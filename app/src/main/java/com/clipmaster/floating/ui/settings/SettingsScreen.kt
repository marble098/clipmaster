package com.clipmaster.floating.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipmaster.floating.settings.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onResetBubblePosition: () -> Unit,
    onClearAllClips: () -> Unit,
    onBack: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = Color(0xFF1E1E2E),
            title = { Text("Clear all clips?", color = Color.White) },
            text = { Text("This permanently deletes your entire clip history.", color = Color.White.copy(0.7f)) },
            confirmButton = {
                TextButton(onClick = { onClearAllClips(); showClearConfirm = false }) {
                    Text("Clear all", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = Color.White.copy(0.6f))
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F0F1A), Color(0xFF1A1A2E), Color(0xFF16213E))
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    "Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                SectionLabel("Bubble")
                SettingsCard {
                    SwitchRow(
                        title = "Snap to screen edges",
                        description = "Releasing a drag glides the bubble to the nearest edge. Turn off to drop it exactly where you release it.",
                        checked = settings.snapBubbleToEdges,
                        onCheckedChange = { v -> onSettingsChange { it.copy(snapBubbleToEdges = v) } },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Auto-hide when empty",
                        description = "Hide the bubble while there are no saved clips; it reappears the moment something is copied.",
                        checked = settings.autoHideBubbleWhenEmpty,
                        onCheckedChange = { v -> onSettingsChange { it.copy(autoHideBubbleWhenEmpty = v) } },
                    )
                    RowDivider()
                    ActionRow(
                        title = "Reset bubble position",
                        description = "Move the bubble back to its default spot.",
                        icon = Icons.Rounded.RestartAlt,
                        onClick = onResetBubblePosition,
                    )
                }

                Spacer(Modifier.height(24.dp))

                SectionLabel("Clipboard")
                SettingsCard {
                    SwitchRow(
                        title = "Auto-capture from clipboard",
                        description = "Automatically save anything copied anywhere on the device. Turn off to only save via manual screen capture.",
                        checked = settings.autoCaptureFromClipboard,
                        onCheckedChange = { v -> onSettingsChange { it.copy(autoCaptureFromClipboard = v) } },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Show source app",
                        description = "Display which app a clip came from underneath its text.",
                        checked = settings.showSourceApp,
                        onCheckedChange = { v -> onSettingsChange { it.copy(showSourceApp = v) } },
                    )
                    RowDivider()
                    HistoryLimitRow(
                        selected = settings.historyLimit,
                        onSelect = { limit -> onSettingsChange { it.copy(historyLimit = limit) } },
                    )
                }

                Spacer(Modifier.height(24.dp))

                SectionLabel("Data")
                SettingsCard {
                    ActionRow(
                        title = "Clear all clips",
                        description = "Permanently delete your entire clip history.",
                        icon = Icons.Rounded.DeleteSweep,
                        destructive = true,
                        onClick = { showClearConfirm = true },
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Color.White.copy(0.4f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
    ) {
        Column(content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(description, color = Color.White.copy(0.5f), fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF818CF8)),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) Color(0xFFEF4444) else Color(0xFF818CF8)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(description, color = Color.White.copy(0.5f), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun HistoryLimitRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Clip history limit", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(
            "Oldest clips are dropped once this many are saved.",
            color = Color.White.copy(0.5f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettings.HISTORY_LIMIT_OPTIONS.forEach { option ->
                val isSelected = option == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(option) },
                    color = if (isSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.toString(),
                            color = if (isSelected) Color.Black else Color.White.copy(0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
