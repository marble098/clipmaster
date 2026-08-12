package com.clipmaster.floating.ui.bubble

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipmaster.floating.data.db.ClipEntry

/** The four corners the floating bubble can be explicitly moved to. */
enum class BubbleCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * The expanded clipboard panel shown when the floating bubble is tapped.
 * Displays recent clip history and provides capture/copy/edit/share actions.
 */
@Composable
fun ClipPanel(
    entries: List<ClipEntry>,
    historyLimit: Int,
    showSourceApp: Boolean,
    expanded: Boolean,
    onCollapse: () -> Unit,
    onCapture: () -> Unit,
    onCopy: (ClipEntry) -> Unit,
    onEdit: (ClipEntry, String) -> Unit,
    onShare: (ClipEntry) -> Unit,
    onDelete: (ClipEntry) -> Unit,
    onMove: (BubbleCorner) -> Unit,
    onClearAll: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var editingEntry by remember { mutableStateOf<ClipEntry?>(null) }
    var moveMenuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = expanded,
        enter = scaleIn(spring(dampingRatio = 0.7f)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        // A plain Box, not a platform Dialog/AlertDialog: this whole panel is
        // itself hosted in a WindowManager overlay window owned by a Service
        // (no Activity), and android.app.Dialog (which Compose's Dialog/
        // AlertDialog are built on) needs an Activity-derived window token —
        // showing one here throws WindowManager.BadTokenException and crashes
        // the app. Edit/clear-confirm are rendered as an in-tree scrim+card
        // overlay instead, sized to match the panel via matchParentSize().
        Box {
            Card(
                modifier = Modifier
                    .width(300.dp)
                    .heightIn(max = 420.dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ContentPaste, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clipboard", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${entries.size}/$historyLimit", color = Color.White.copy(0.7f), fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Settings, "Settings", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCollapse, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // Capture button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onCapture),
                    color = Color(0xFF818CF8).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CenterFocusStrong, null, tint = Color(0xFF818CF8), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Capture screen text", color = Color(0xFF818CF8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Move bubble / clear all
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ActionChip(
                            icon = Icons.Rounded.OpenWith,
                            label = "Move",
                            onClick = { moveMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = moveMenuExpanded,
                            onDismissRequest = { moveMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Top left") },
                                onClick = { moveMenuExpanded = false; onMove(BubbleCorner.TOP_LEFT) },
                            )
                            DropdownMenuItem(
                                text = { Text("Top right") },
                                onClick = { moveMenuExpanded = false; onMove(BubbleCorner.TOP_RIGHT) },
                            )
                            DropdownMenuItem(
                                text = { Text("Bottom left") },
                                onClick = { moveMenuExpanded = false; onMove(BubbleCorner.BOTTOM_LEFT) },
                            )
                            DropdownMenuItem(
                                text = { Text("Bottom right") },
                                onClick = { moveMenuExpanded = false; onMove(BubbleCorner.BOTTOM_RIGHT) },
                            )
                        }
                    }
                    ActionChip(
                        icon = Icons.Rounded.DeleteSweep,
                        label = "Clear all",
                        enabled = entries.isNotEmpty(),
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Clip list
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No clips yet.\nCapture some text to get started.",
                            color = Color.White.copy(0.4f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            ClipEntryRow(
                                entry = entry,
                                showSourceApp = showSourceApp,
                                onCopy = { onCopy(entry) },
                                onEdit = { editingEntry = entry },
                                onShare = { onShare(entry) },
                                onDelete = { onDelete(entry) },
                            )
                        }
                    }
                }
            }
            }

            editingEntry?.let { entry ->
                Box(modifier = Modifier.matchParentSize()) {
                    ScrimOverlay(onDismissRequest = { editingEntry = null }) {
                        EditClipCard(
                            entry = entry,
                            onDismiss = { editingEntry = null },
                            onSave = { newContent ->
                                onEdit(entry, newContent)
                                editingEntry = null
                            },
                        )
                    }
                }
            }

            if (showClearConfirm) {
                Box(modifier = Modifier.matchParentSize()) {
                    ScrimOverlay(onDismissRequest = { showClearConfirm = false }) {
                        ClearAllConfirmCard(
                            count = entries.size,
                            onDismiss = { showClearConfirm = false },
                            onConfirm = {
                                onClearAll()
                                showClearConfirm = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tint = Color.White.copy(if (enabled) 0.6f else 0.25f)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = tint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ClipEntryRow(
    entry: ClipEntry,
    showSourceApp: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onCopy)
                .padding(12.dp),
        ) {
            Text(
                text = entry.content,
                color = Color.White.copy(0.9f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showSourceApp) {
                    Text(
                        text = entry.sourceApp ?: "Unknown",
                        color = Color.White.copy(0.35f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                RowIconButton(Icons.Rounded.ContentCopy, "Copy to clipboard", onCopy)
                RowIconButton(Icons.Rounded.Edit, "Edit", onEdit)
                RowIconButton(Icons.Rounded.Share, "Share", onShare)
                RowIconButton(Icons.Rounded.DeleteOutline, "Delete", onDelete)
            }
        }
    }
}

@Composable
private fun RowIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(26.dp)) {
        Icon(icon, contentDescription, tint = Color.White.copy(0.4f), modifier = Modifier.size(15.dp))
    }
}

/**
 * Full-bleed scrim + centered card, standing in for a platform Dialog.
 * Renders in-tree (inside the panel's own overlay window) instead of
 * spawning a new android.app.Dialog window, which crashes when the host
 * window belongs to a Service rather than an Activity.
 */
@Composable
private fun ScrimOverlay(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismissRequest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(20.dp)
                // Consumes taps on the card itself so they don't fall through
                // to the scrim's dismiss handler above.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            content()
        }
    }
}

/** Card content for editing a clip's text before it's saved back to history. */
@Composable
private fun EditClipCard(
    entry: ClipEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(entry.id) { mutableStateOf(entry.content) }

    Card(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Edit clip",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 240.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF818CF8),
                    unfocusedBorderColor = Color.White.copy(0.2f),
                    cursorColor = Color(0xFF818CF8),
                ),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White.copy(0.6f))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                    Text("Save", color = Color(0xFF818CF8))
                }
            }
        }
    }
}

/** Card content confirming the "Clear all clips" action. */
@Composable
private fun ClearAllConfirmCard(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Card(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Clear all clips?",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This permanently deletes all $count saved clips.",
                color = Color.White.copy(0.7f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White.copy(0.6f))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onConfirm) {
                    Text("Clear all", color = Color(0xFFEF4444))
                }
            }
        }
    }
}

/**
 * The small floating bubble circle.
 */
@Composable
fun FloatingBubble(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ContentPaste,
            contentDescription = "Open ClipMaster",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}
