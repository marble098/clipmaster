package com.clipmaster.floating.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.clipmaster.floating.ClipMasterApp
import com.clipmaster.floating.MainActivity
import com.clipmaster.floating.R
import com.clipmaster.floating.clipboard.ClipboardHelper
import com.clipmaster.floating.data.db.ClipEntry
import com.clipmaster.floating.data.repository.ClipRepository
import com.clipmaster.floating.ui.bubble.BubbleCorner
import com.clipmaster.floating.ui.bubble.ClipPanel
import com.clipmaster.floating.ui.bubble.FloatingBubble
import com.clipmaster.floating.ui.theme.ClipMasterTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import androidx.savedstate.setViewTreeSavedStateRegistryOwner


class FloatingBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    // Lifecycle plumbing so ComposeView works in a Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private lateinit var repository: ClipRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var clipboardManager: ClipboardManager
    @Volatile private var lastSeenClip: String? = null
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        onSystemClipboardChanged()
    }

    private var bubbleParams: WindowManager.LayoutParams? = null
    private val bubblePrefs: SharedPreferences by lazy {
        getSharedPreferences("clipmaster_bubble", Context.MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val app = application as ClipMasterApp
        repository = ClipRepository(app.database.clipDao())

        startForegroundNotification()
        showBubble()

        // Full two-way link to the phone's system clipboard: pick up anything
        // copied anywhere on the device (plus whatever's on it right now),
        // and write clips back to it via ClipboardHelper elsewhere below.
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        onSystemClipboardChanged() // capture whatever's already on the clipboard, not just future changes

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        serviceScope.cancel()
        bubbleView?.let { windowManager.removeView(it) }
        panelView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    private fun onSystemClipboardChanged() {
        val text = ClipboardHelper.readPrimaryClip(this)?.trim() ?: return
        if (text.isBlank() || text == lastSeenClip) return
        lastSeenClip = text
        serviceScope.launch(Dispatchers.IO) {
            repository.addClip(text, sourceApp = "System Clipboard")
        }
    }

    private fun startForegroundNotification() {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ClipMasterApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clipboard)
            .setContentTitle("ClipMaster active")
            .setContentText("Tap to configure")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val bubbleSizePx = dpToPx(BUBBLE_SIZE_DP)
        val screen = screenSize()
        val savedIsLeft = bubblePrefs.getBoolean(KEY_BUBBLE_LEFT, true)
        val savedY = bubblePrefs.getInt(KEY_BUBBLE_Y, screen.y / 3)
            .coerceIn(0, (screen.y - bubbleSizePx).coerceAtLeast(0))
        val startX = if (savedIsLeft) 0 else (screen.x - bubbleSizePx).coerceAtLeast(0)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = savedY
        }
        bubbleParams = params

        bubbleView = ComposeView(this).also { view ->
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
            view.setContent {
                ClipMasterTheme {
                    FloatingBubble(onClick = { togglePanel() })
                }
            }

            // Drag support
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var moved = false

            view.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (abs(dx) > 5 || abs(dy) > 5) moved = true
                        val bounds = screenSize()
                        params.x = initialX + dx.toInt()
                        params.y = (initialY + dy.toInt())
                            .coerceIn(0, (bounds.y - bubbleSizePx).coerceAtLeast(0))
                        windowManager.updateViewLayout(view, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            view.performClick()
                        } else {
                            snapToNearestEdge(view, params, bubbleSizePx)
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(view, params)
        }
    }

    /**
     * Chat-head-style "smart" placement: after a drag, glide the bubble to
     * whichever screen edge (left/right) it's closer to, clamp it vertically
     * so it never lands under the status bar or nav bar, and remember the
     * resting spot so it's restored next time the service starts.
     */
    private fun snapToNearestEdge(view: View, params: WindowManager.LayoutParams, bubbleSizePx: Int) {
        val screen = screenSize()
        val clampedY = params.y.coerceIn(0, (screen.y - bubbleSizePx).coerceAtLeast(0))
        val targetX = nearestEdgeX(params.x, bubbleSizePx, screen.x)

        animateBubbleTo(view, params, targetX, clampedY)
        saveBubblePosition(targetX, clampedY, screen.x, bubbleSizePx)
    }

    /** Explicit placement requested from the clip panel's "Move" menu. */
    private fun moveBubbleToCorner(corner: BubbleCorner) {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val bubbleSizePx = dpToPx(BUBBLE_SIZE_DP)
        val screen = screenSize()
        val margin = dpToPx(24)

        val targetX = when (corner) {
            BubbleCorner.TOP_LEFT, BubbleCorner.BOTTOM_LEFT -> 0
            BubbleCorner.TOP_RIGHT, BubbleCorner.BOTTOM_RIGHT -> (screen.x - bubbleSizePx).coerceAtLeast(0)
        }
        val targetY = when (corner) {
            BubbleCorner.TOP_LEFT, BubbleCorner.TOP_RIGHT -> margin
            BubbleCorner.BOTTOM_LEFT, BubbleCorner.BOTTOM_RIGHT ->
                (screen.y - bubbleSizePx - margin).coerceAtLeast(0)
        }

        animateBubbleTo(view, params, targetX, targetY)
        saveBubblePosition(targetX, targetY, screen.x, bubbleSizePx)
    }

    private fun nearestEdgeX(x: Int, bubbleSizePx: Int, screenWidth: Int): Int =
        if (x + bubbleSizePx / 2 < screenWidth / 2) 0 else (screenWidth - bubbleSizePx).coerceAtLeast(0)

    private fun animateBubbleTo(view: View, params: WindowManager.LayoutParams, targetX: Int, targetY: Int) {
        val startX = params.x
        val startY = params.y
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                params.x = (startX + (targetX - startX) * fraction).toInt()
                params.y = (startY + (targetY - startY) * fraction).toInt()
                try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
            }
            start()
        }
    }

    private fun saveBubblePosition(x: Int, y: Int, screenWidth: Int, bubbleSizePx: Int) {
        val isLeft = x < (screenWidth - bubbleSizePx) / 2
        bubblePrefs.edit()
            .putBoolean(KEY_BUBBLE_LEFT, isLeft)
            .putInt(KEY_BUBBLE_Y, y)
            .apply()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun screenSize(): Point {
        val point = Point()
        windowManager.defaultDisplay.getRealSize(point)
        return point
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val bubbleSizePx = dpToPx(BUBBLE_SIZE_DP)
        val screen = screenSize()

        params.x = nearestEdgeX(params.x, bubbleSizePx, screen.x)
        params.y = params.y.coerceIn(0, (screen.y - bubbleSizePx).coerceAtLeast(0))

        try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
        saveBubblePosition(params.x, params.y, screen.x, bubbleSizePx)
    }

    private var panelShowing = false

    private fun togglePanel() {
        if (panelShowing) {
            hidePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        panelShowing = true
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }

        panelView = ComposeView(this).also { view ->
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
            view.setContent {
                val entries = remember { mutableStateListOf<ClipEntry>() }
                LaunchedEffect(Unit) {
                    repository.recentClips().collectLatest { list ->
                        entries.clear()
                        entries.addAll(list)
                    }
                }

                ClipMasterTheme {
                    ClipPanel(
                        entries = entries,
                        expanded = true,
                        onCollapse = { hidePanel() },
                        onCapture = {
                            ClipAccessibilityService.instance?.captureScreenText()
                            hidePanel()
                        },
                        onCopy = { entry ->
                            serviceScope.launch(Dispatchers.IO) {
                                lastSeenClip = entry.content
                                ClipboardHelper.copyWithRootFallback(this@FloatingBubbleService, entry.content)
                            }
                            hidePanel()
                        },
                        onEdit = { entry, newContent ->
                            serviceScope.launch { repository.updateClip(entry, newContent) }
                        },
                        onShare = { entry ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, entry.content)
                            }
                            val chooser = Intent.createChooser(shareIntent, "Share clip").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(chooser)
                            hidePanel()
                        },
                        onDelete = { entry ->
                            serviceScope.launch { repository.delete(entry) }
                        },
                        onMove = { corner -> moveBubbleToCorner(corner) },
                        onClearAll = {
                            // Panel stays open so the list visibly updates to
                            // the empty state, confirming the clear happened.
                            serviceScope.launch { repository.clearAll() }
                        },
                    )
                }
            }

            // Close panel on outside touch
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hidePanel()
                    true
                } else false
            }

            windowManager.addView(view, params)
        }
    }

    private fun hidePanel() {
        panelShowing = false
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
    }

    companion object {
        // Must match FloatingBubble's Box(.size(52.dp)) in BubbleComposeView.kt
        private const val BUBBLE_SIZE_DP = 52
        private const val KEY_BUBBLE_LEFT = "bubble_edge_left"
        private const val KEY_BUBBLE_Y = "bubble_y"
    }
}
