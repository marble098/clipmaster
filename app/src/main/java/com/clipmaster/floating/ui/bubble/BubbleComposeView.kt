package com.clipmaster.floating.ui.bubble

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.Dialog
import com.clipmaster.floating.data.db.ClipEntry

/**
 * The expanded clipboard panel shown when the floating bubble is tapped.
 * Displays recent clip history and provides capture/copy/edit/share actions.
 */
@Composable
fun ClipPanel(
    entries: List<ClipEntry>,
    expanded: Boolean,
    onCollapse: () -> Unit,
    onCapture: () -> Unit,
    onCopy: (ClipEntry) -> Unit,
    onEdit: (ClipEntry, String) -> Unit,
    onShare: (ClipEntry) -> Unit,
    onDelete: (ClipEntry) -> Unit,
) {
    var editingEntry by remember { mutableStateOf<ClipEntry?>(null) }

    editingEntry?.let { entry ->
        EditClipDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { newContent ->
                onEdit(entry, newContent)
                editingEntry = null
            },
        )
    }

    AnimatedVisibility(
        visible = expanded,
        enter = scaleIn(spring(dampingRatio = 0.7f)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
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
                    Text("${entries.size}/50", color = Color.White.copy(0.7f), fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
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
    }
}

@Composable
private fun ClipEntryRow(
    entry: ClipEntry,
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
                Text(
                    text = entry.sourceApp ?: "Unknown",
                    color = Color.White.copy(0.35f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
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
 * Dialog for editing a clip's text before it's saved back to history.
 */
@Composable
private fun EditClipDialog(
    entry: ClipEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(entry.id) { mutableStateOf(entry.content) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
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
