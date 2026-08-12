package com.clipmaster.floating.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipmaster.floating.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val rootGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val rootChecking: Boolean = false,
    val currentPage: Int = 0,
) {
    val allGranted get() = rootGranted && overlayGranted && accessibilityGranted
}

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    /**
     * Re-checks every permission, including root — so once root/overlay/
     * accessibility have been granted, reopening the app reflects that
     * automatically instead of asking the user to grant them again.
     */
    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _state.update {
            it.copy(
                overlayGranted = PermissionHelper.hasOverlayPermission(ctx),
                accessibilityGranted = PermissionHelper.hasAccessibilityEnabled(ctx),
            )
        }
        viewModelScope.launch {
            val granted = PermissionHelper.hasRootAccess()
            _state.update { it.copy(rootGranted = granted) }
        }
    }

    fun checkRoot() {
        viewModelScope.launch {
            _state.update { it.copy(rootChecking = true) }
            val granted = PermissionHelper.hasRootAccess()
            _state.update { it.copy(rootGranted = granted, rootChecking = false) }
        }
    }

    fun setPage(page: Int) {
        _state.update { it.copy(currentPage = page) }
    }
}
