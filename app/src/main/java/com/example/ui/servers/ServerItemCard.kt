package com.example.ui.servers

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerTestStatus
import com.example.data.model.VlessProfile
import com.example.ui.components.LatencyPill
import com.example.ui.theme.AppTheme

@Composable
fun ServerItemCard(
    profile: VlessProfile,
    isSelected: Boolean,
    isConnected: Boolean,
    testingStatus: ServerTestStatus?,
    onSelect: () -> Unit,
    onConnectDirect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTestPing: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExportUri: (VlessProfile) -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val borderColor = when {
        isSelected && isConnected -> AppTheme.colors.statusConnected
        isSelected -> AppTheme.colors.primary
        else -> AppTheme.colors.borderSubtle
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (isSelected) 4.dp else 1.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect)
            .testTag("server_card_${profile.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AppTheme.colors.surfaceElevated else AppTheme.colors.surfaceCard
        ),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = if (isConnected) AppTheme.colors.statusConnected else AppTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = profile.name,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Favorite Button & Overflow Menu
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (profile.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (profile.isFavorite) AppTheme.colors.statusWarning else AppTheme.colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = AppTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .background(AppTheme.colors.surfaceCard)
                                .border(1.dp, AppTheme.colors.borderSubtle)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Test Connectivity", color = AppTheme.colors.textPrimary) },
                                leadingIcon = {
                                    Icon(Icons.Default.NetworkPing, contentDescription = null, tint = AppTheme.colors.primary)
                                },
                                onClick = {
                                    showMenu = false
                                    onTestPing()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate Profile", color = AppTheme.colors.textPrimary) },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AppTheme.colors.metricDownload)
                                },
                                onClick = {
                                    showMenu = false
                                    onDuplicate()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy VLESS Link", color = AppTheme.colors.textPrimary) },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AppTheme.colors.primary)
                                },
                                onClick = {
                                    showMenu = false
                                    val uri = onExportUri(profile)
                                    clipboardManager.setText(AnnotatedString(uri))
                                    Toast.makeText(context, "VLESS Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Profile", color = AppTheme.colors.statusError) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = AppTheme.colors.statusError)
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle Address & Port
            Text(
                text = "${profile.address}:${profile.port}",
                color = AppTheme.colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Protocol & Security Badges + Latency Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BadgeChip(text = "VLESS", color = AppTheme.colors.primary)
                    BadgeChip(text = profile.transport.uppercase(), color = AppTheme.colors.metricDownload)
                    if (profile.security != "none") {
                        BadgeChip(
                            text = profile.securityBadge,
                            color = if (profile.security.equals("reality", true)) AppTheme.colors.statusConnected else AppTheme.colors.statusWarning
                        )
                    }
                }

                // Latency Status
                if (testingStatus == ServerTestStatus.Testing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = AppTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                    }
                } else if (testingStatus is ServerTestStatus.Unavailable) {
                    Text("Unavailable", color = AppTheme.colors.statusError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else if (testingStatus is ServerTestStatus.Available) {
                    LatencyPill(latencyMs = testingStatus.latencyMs)
                } else if (testingStatus is ServerTestStatus.Slow) {
                    LatencyPill(latencyMs = testingStatus.latencyMs)
                } else {
                    LatencyPill(latencyMs = profile.lastLatencyMs)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Server Profile", color = AppTheme.colors.textPrimary) },
            text = { Text("Are you sure you want to remove '${profile.name}'? This action cannot be undone.", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = AppTheme.colors.statusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.surfaceCard
        )
    }
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
