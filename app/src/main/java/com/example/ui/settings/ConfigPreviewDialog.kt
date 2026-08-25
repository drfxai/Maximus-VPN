package com.example.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppTheme

@Composable
fun ConfigPreviewDialog(
    configJson: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Xray Configuration Preview", color = AppTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Generated JSON sent to Xray-core (UUIDs and Keys sanitized):",
                    color = AppTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.consoleBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.borderSubtle),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = configJson,
                            color = AppTheme.colors.primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(configJson))
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AppTheme.colors.onPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy JSON", color = AppTheme.colors.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AppTheme.colors.textSecondary)
            }
        },
        containerColor = AppTheme.colors.surfaceCard,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(20.dp))
    )
}
