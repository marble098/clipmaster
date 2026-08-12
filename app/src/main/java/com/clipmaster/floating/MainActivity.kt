package com.clipmaster.floating

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipmaster.floating.service.FloatingBubbleService
import com.clipmaster.floating.ui.onboarding.AlreadySetupScreen
import com.clipmaster.floating.ui.onboarding.OnboardingScreen
import com.clipmaster.floating.ui.onboarding.OnboardingViewModel
import com.clipmaster.floating.ui.settings.SettingsActivity
import com.clipmaster.floating.ui.theme.ClipMasterTheme
import com.clipmaster.floating.util.PermissionHelper

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClipMasterTheme {
                val vm: OnboardingViewModel = viewModel()
                val state by vm.state.collectAsState()

                // Covers cold start: onResume's refresh also fires, but that's
                // after the first frame, so this avoids a flash of the wizard
                // when everything was already granted in a previous session.
                LaunchedEffect(Unit) { vm.refreshPermissions() }

                if (state.allGranted) {
                    // Nothing left to grant — never re-show the permission
                    // wizard once the user has already granted everything.
                    LaunchedEffect(Unit) { startBubbleService() }
                    AlreadySetupScreen(
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                    )
                } else {
                    OnboardingScreen(
                        state = state,
                        onRequestRoot = { vm.checkRoot() },
                        onRequestOverlay = {
                            startActivity(PermissionHelper.overlaySettingsIntent(this))
                        },
                        onRequestAccessibility = {
                            startActivity(PermissionHelper.accessibilitySettingsIntent())
                        },
                        onFinish = {
                            startBubbleService()
                            finish()
                        },
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning from settings
        val vm = androidx.lifecycle.ViewModelProvider(this)[OnboardingViewModel::class.java]
        vm.refreshPermissions()
    }

    private fun startBubbleService() {
        val intent = Intent(this, FloatingBubbleService::class.java)
        startForegroundService(intent)
    }
}
