package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppTheme

/**
 * Premium interactive Theme Toggle Switch featuring Sun (Light) and Moon (Dark) indicators.
 * - Swiping or dragging towards the moon activates the Dark theme.
 * - Swiping, dragging, or tapping towards the sun activates the Light theme.
 * - Tapping anywhere seamlessly switches to the selected theme side.
 */
@Composable
fun ThemeToggleSwitch(
    isDark: Boolean,
    onThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    val trackBgColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF161822) else Color(0xFFE2E8F0),
        animationSpec = tween(300),
        label = "trackBgColor"
    )

    val trackBorderColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF2C2E3E) else Color(0xFFCBD5E1),
        animationSpec = tween(300),
        label = "trackBorderColor"
    )

    val thumbBgColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF272938) else Color(0xFFFFFFFF),
        animationSpec = tween(300),
        label = "thumbBgColor"
    )

    val thumbBorderColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF8B5CF6).copy(alpha = 0.5f) else Color(0xFFFBBF24).copy(alpha = 0.6f),
        animationSpec = tween(300),
        label = "thumbBorderColor"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (isDark) 44.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumbOffset"
    )

    val iconRotation by animateFloatAsState(
        targetValue = if (isDark) 360f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "iconRotation"
    )

    val sunColor by animateColorAsState(
        targetValue = if (!isDark) Color(0xFFD97706) else Color(0xFF64748B),
        animationSpec = tween(250),
        label = "sunColor"
    )

    val moonColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFFD0BCFF) else Color(0xFF94A3B8),
        animationSpec = tween(250),
        label = "moonColor"
    )

    Box(
        modifier = modifier
            .semantics {
                role = Role.Switch
                contentDescription = if (isDark) "Dark mode active. Swipe left or tap sun for light mode." else "Light mode active. Swipe right or tap moon for dark mode."
            }
            .testTag("theme_toggle_switch")
            .width(84.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(trackBgColor)
            .border(1.2.dp, trackBorderColor, RoundedCornerShape(20.dp))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    accumulatedDrag += delta
                    if (accumulatedDrag > 12f) {
                        // Dragged right -> Dark Theme
                        if (!isDark) onThemeChange(true)
                        accumulatedDrag = 0f
                    } else if (accumulatedDrag < -12f) {
                        // Dragged left -> Light Theme
                        if (isDark) onThemeChange(false)
                        accumulatedDrag = 0f
                    }
                },
                onDragStopped = {
                    accumulatedDrag = 0f
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Background stationary icons for Sun (left) and Moon (right)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sun side touch target
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onThemeChange(false) }
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = "Light Theme",
                    tint = sunColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Moon side touch target
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onThemeChange(true) }
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.DarkMode,
                    contentDescription = "Dark Theme",
                    tint = moonColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Sliding Thumb with Active Theme Indicator
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(36.dp)
                .height(30.dp)
                .shadow(elevation = if (isDark) 2.dp else 4.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(thumbBgColor)
                .border(1.dp, thumbBorderColor, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = if (isDark) Color(0xFFD0BCFF) else Color(0xFFF59E0B)),
                    onClick = { onThemeChange(!isDark) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDark) Icons.Filled.DarkMode else Icons.Filled.WbSunny,
                contentDescription = null,
                tint = if (isDark) Color(0xFFD0BCFF) else Color(0xFFD97706),
                modifier = Modifier
                    .size(17.dp)
                    .rotate(iconRotation)
            )
        }
    }
}
