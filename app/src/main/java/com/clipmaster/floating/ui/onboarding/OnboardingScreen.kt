@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.clipmaster.floating.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class PermStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val buttonLabel: String,
    val granted: Boolean,
    val loading: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onRequestRoot: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onFinish: () -> Unit,
) {
    val steps = listOf(
        PermStep(
            icon = Icons.Rounded.AdminPanelSettings,
            title = "Root Access",
            description = "ClipMaster needs superuser privileges to inject text into system clipboard and paste into secured fields across all apps.",
            buttonLabel = if (state.rootChecking) "Checking…" else "Grant Root",
            granted = state.rootGranted,
            loading = state.rootChecking,
        ),
        PermStep(
            icon = Icons.Rounded.Layers,
            title = "Draw Over Apps",
            description = "The floating bubble needs permission to appear on top of other applications so you can access your clipboard anywhere.",
            buttonLabel = "Open Settings",
            granted = state.overlayGranted,
        ),
        PermStep(
            icon = Icons.Rounded.Accessibility,
            title = "Accessibility Service",
            description = "Text capture works through Android's accessibility framework. This lets ClipMaster read on-screen text and paste into active fields.",
            buttonLabel = "Enable Service",
            granted = state.accessibilityGranted,
        ),
    )

    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F1A),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            Text(
                text = "ClipMaster",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "Setup your clipboard manager",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(16.dp))

            // Page indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { i, step ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == i) 28.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (step.granted) Color(0xFF4ADE80)
                                else if (pagerState.currentPage == i) Color(0xFF818CF8)
                                else Color.White.copy(alpha = 0.25f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                PermissionCard(
                    step = steps[page],
                    onAction = {
                        when (page) {
                            0 -> onRequestRoot()
                            1 -> onRequestOverlay()
                            2 -> onRequestAccessibility()
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) { Text("Back") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (state.allGranted) {
                    Button(
                        onClick = onFinish,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Launch ClipMaster", color = Color.Black)
                    }
                } else if (pagerState.currentPage < steps.lastIndex) {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF818CF8)),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Next") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(step: PermStep, onAction: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.07f)
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        if (step.granted) Color(0xFF4ADE80).copy(alpha = 0.2f)
                        else Color(0xFF818CF8).copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (step.granted) Icons.Rounded.CheckCircle else step.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (step.granted) Color(0xFF4ADE80) else Color(0xFF818CF8),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = step.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = step.description,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(32.dp))

            if (step.granted) {
                Text(
                    text = "✓ Granted",
                    color = Color(0xFF4ADE80),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
            } else {
                Button(
                    onClick = onAction,
                    enabled = !step.loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF818CF8)),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    if (step.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(step.buttonLabel, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
