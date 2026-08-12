package com.clipmaster.floating.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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
import com.clipmaster.floating.data.db.ClipEntry
import com.clipmaster.floating.data.repository.ClipRepository
import com.clipmaster.floating.root.RootExecutor
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

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        serviceScope.cancel()
        bubbleView?.let { windowManager.removeView(it) }
        panelView?.let { windowManager.removeView(it) }
        super.onDestroy()
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
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

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
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(view, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            view.performClick()
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(view, params)
        }
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
                        onPaste = { entry ->
                            serviceScope.launch(Dispatchers.IO) {
                                RootExecutor.setClipboard(entry.content)
                            }
                            hidePanel()
                        },
                        onDelete = { entry ->
                            serviceScope.launch { repository.delete(entry) }
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
}
