package com.clipmaster.floating

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipmaster.floating.service.FloatingBubbleService
import com.clipmaster.floating.ui.onboarding.OnboardingScreen
import com.clipmaster.floating.ui.onboarding.OnboardingViewModel
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
                )
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
