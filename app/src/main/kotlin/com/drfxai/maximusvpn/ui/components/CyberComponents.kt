package com.drfxai.maximusvpn.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drfxai.maximusvpn.data.model.ConnectionStatus
import com.drfxai.maximusvpn.ui.theme.AppTheme

@Composable
fun ConnectionButton(
    status: ConnectionStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = status == ConnectionStatus.CONNECTED
    val isBusy = status == ConnectionStatus.CONNECTING || status == ConnectionStatus.PREPARING || status == ConnectionStatus.RECONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isBusy || isConnected) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isBusy || isConnected) 0.04f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val accentColor = when {
        isConnected -> AppTheme.colors.statusConnected
        isBusy -> AppTheme.colors.statusWarning
        status == ConnectionStatus.FAILED -> AppTheme.colors.statusError
        else -> AppTheme.colors.primary
    }

    Box(
        modifier = modifier.size(230.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Pulsing Ambient Aura
        if (isConnected || isBusy) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = accentColor.copy(alpha = pulseAlpha),
                    radius = (size.minDimension / 2f) * pulseScale
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.25f),
                    radius = size.minDimension / 2.25f,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Outer Ring
        Box(
            modifier = Modifier
                .size(180.dp)
                .shadow(
                    elevation = if (AppTheme.colors.isDark) 4.dp else 10.dp,
                    shape = CircleShape,
                    ambientColor = accentColor.copy(alpha = 0.2f),
                    spotColor = accentColor.copy(alpha = 0.2f)
                )
                .clip(CircleShape)
                .background(AppTheme.colors.surfaceCard)
                .border(
                    width = 2.dp,
                    color = if (isConnected || isBusy) accentColor.copy(alpha = 0.6f) else AppTheme.colors.borderMedium,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Main Core Button Body
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (AppTheme.colors.isDark) {
                                listOf(AppTheme.colors.surfaceElevated, Color(0xFF14151D))
                            } else {
                                listOf(Color(0xFFFFFFFF), AppTheme.colors.surfaceElevated)
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isConnected) AppTheme.colors.statusConnected.copy(alpha = 0.5f) else AppTheme.colors.borderSubtle,
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = accentColor),
                        onClick = onClick
                    )
                    .testTag("vpn_connect_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "VPN Toggle",
                        tint = accentColor,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (status) {
                            ConnectionStatus.CONNECTED -> "CONNECTED"
                            ConnectionStatus.CONNECTING -> "CONNECTING"
                            ConnectionStatus.PREPARING -> "STARTING"
                            ConnectionStatus.RECONNECTING -> "RECONNECT"
                            ConnectionStatus.DISCONNECTING -> "STOPPING"
                            ConnectionStatus.FAILED -> "RETRY"
                            ConnectionStatus.DISCONNECTED -> "CONNECT"
                        },
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (dotColor, text) = when (status) {
        ConnectionStatus.CONNECTED -> Pair(AppTheme.colors.statusConnected, "CONNECTED")
        ConnectionStatus.CONNECTING, ConnectionStatus.PREPARING -> Pair(AppTheme.colors.statusWarning, "CONNECTING...")
        ConnectionStatus.RECONNECTING -> Pair(AppTheme.colors.statusWarning, "RECONNECTING...")
        ConnectionStatus.DISCONNECTING -> Pair(AppTheme.colors.textMuted, "DISCONNECTING...")
        ConnectionStatus.FAILED -> Pair(AppTheme.colors.statusError, "FAILED")
        ConnectionStatus.DISCONNECTED -> Pair(AppTheme.colors.textMuted, "DISCONNECTED")
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(AppTheme.colors.surfaceCard)
            .border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = AppTheme.colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun LatencyPill(
    latencyMs: Long?,
    modifier: Modifier = Modifier
) {
    if (latencyMs == null) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AppTheme.colors.surfaceElevated)
                .border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("--- ms", color = AppTheme.colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        return
    }

    val (color, label) = when {
        latencyMs < 120 -> Pair(AppTheme.colors.statusConnected, "${latencyMs}ms")
        latencyMs < 300 -> Pair(AppTheme.colors.statusWarning, "${latencyMs}ms")
        else -> Pair(AppTheme.colors.statusError, "${latencyMs}ms")
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppTheme.colors.surfaceElevated)
            .border(1.dp, AppTheme.colors.borderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = AppTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
