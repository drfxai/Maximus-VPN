package com.drfxai.maximusvpn.ui.addserver

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drfxai.maximusvpn.core.AppResult
import com.drfxai.maximusvpn.data.model.VlessProfile
import com.drfxai.maximusvpn.ui.theme.AppTheme
import com.drfxai.maximusvpn.vless.VlessParser

@Composable
fun QrImportDialog(
    onDismiss: () -> Unit,
    onImportSuccess: (VlessProfile) -> Unit,
    onBatchImport: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var inputText by remember { mutableStateOf("") }
    var detectedCount by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("QR / Batch Import", color = AppTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Live camera scanner — decodes and fills the text field
                var showCamera by remember { mutableStateOf(false) }
                if (showCamera) {
                    com.drfxai.maximusvpn.ui.qr.QrCameraScanner(
                        modifier = Modifier.fillMaxWidth(),
                        onQrScanned = { payload ->
                            inputText = payload
                            showCamera = false
                            val singleRes = VlessParser.parse(payload.trim())
                            if (singleRes is AppResult.Success) {
                                onImportSuccess(singleRes.data)
                                onDismiss()
                            }
                        },
                        onError = { /* surfaced via input field; non-fatal */ }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = { showCamera = !showCamera },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceElevated),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showCamera) "Hide camera" else "Scan QR with camera", color = AppTheme.colors.primary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Or paste a QR code payload, subscription text, or multiple line-separated VLESS URIs:",
                    color = AppTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        val lines = it.lines().filter { line -> line.trim().startsWith("vless://") }
                        detectedCount = lines.size
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = { Text("vless://...\nvless://...", color = AppTheme.colors.textMuted, fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppTheme.colors.surfaceElevated,
                        unfocusedContainerColor = AppTheme.colors.surfaceElevated,
                        focusedBorderColor = AppTheme.colors.primary,
                        unfocusedBorderColor = AppTheme.colors.borderSubtle,
                        focusedTextColor = AppTheme.colors.textPrimary,
                        unfocusedTextColor = AppTheme.colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val clip = clipboardManager.getText()?.text ?: ""
                            inputText = clip
                            val lines = clip.lines().filter { line -> line.trim().startsWith("vless://") }
                            detectedCount = lines.size
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceElevated),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste Clipboard", color = AppTheme.colors.primary, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (detectedCount > 0) {
                        Text(
                            text = "$detectedCount VLESS found",
                            color = AppTheme.colors.statusConnected,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val singleRes = VlessParser.parse(inputText.trim())
                        if (singleRes is AppResult.Success) {
                            onImportSuccess(singleRes.data)
                        } else {
                            onBatchImport(inputText)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Import", color = AppTheme.colors.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppTheme.colors.textSecondary)
            }
        },
        containerColor = AppTheme.colors.surfaceCard,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(20.dp))
    )
}
