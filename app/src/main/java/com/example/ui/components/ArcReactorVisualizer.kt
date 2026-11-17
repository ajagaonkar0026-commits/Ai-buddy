package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.network.OperatingMode
import com.example.system.PendingSensitiveAction
import com.example.system.VoiceEngineStatus
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CardBorderSlate
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.ReactorBlue
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorOrb(
    assistantName: String,
    voiceStatus: VoiceEngineStatus,
    isThinking: Boolean,
    rmsLevel: Float,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "arc_reactor")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (voiceStatus == VoiceEngineStatus.LISTENING) 2600 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val coreColor = when {
        voiceStatus == VoiceEngineStatus.LISTENING -> ArcCyan
        voiceStatus == VoiceEngineStatus.SPEAKING -> OnlineEmerald
        isThinking || voiceStatus == VoiceEngineStatus.PROCESSING -> AlertAmber
        else -> ReactorBlue
    }

    Box(
        modifier = modifier
            .size(168.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggleVoice)
            .testTag("arc_reactor_voice_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.42f
            val dynamicBoost = if (voiceStatus == VoiceEngineStatus.LISTENING) rmsLevel * 14f else 0f

            // Outer ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor.copy(alpha = 0.30f), Color.Transparent),
                    center = center,
                    radius = baseRadius * 1.35f * pulse
                ),
                radius = baseRadius * 1.35f * pulse,
                center = center
            )

            // Segmented outer arc ring
            for (i in 0 until 6) {
                val startAngle = rotation + i * 60f
                drawArc(
                    color = coreColor.copy(alpha = 0.85f),
                    startAngle = startAngle,
                    sweepAngle = 38f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Inner counter-rotating nodes
            val innerR = baseRadius * 0.72f + dynamicBoost
            drawCircle(
                color = coreColor.copy(alpha = 0.45f),
                radius = innerR,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            val tickCount = 12
            for (i in 0 until tickCount) {
                val angleRad = Math.toRadians((-(rotation * 0.6) + i * (360.0 / tickCount)))
                val p1 = Offset(
                    x = center.x + (innerR * 0.85f * cos(angleRad)).toFloat(),
                    y = center.y + (innerR * 0.85f * sin(angleRad)).toFloat()
                )
                val p2 = Offset(
                    x = center.x + (innerR * 1.05f * cos(angleRad)).toFloat(),
                    y = center.y + (innerR * 1.05f * sin(angleRad)).toFloat()
                )
                drawLine(coreColor, p1, p2, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when (voiceStatus) {
                    VoiceEngineStatus.LISTENING -> Icons.Filled.Stop
                    VoiceEngineStatus.SPEAKING -> Icons.Filled.GraphicEq
                    else -> Icons.Filled.Mic
                },
                contentDescription = "Activate $assistantName Voice",
                tint = coreColor,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = when {
                    voiceStatus == VoiceEngineStatus.LISTENING -> "LISTENING"
                    voiceStatus == VoiceEngineStatus.SPEAKING -> "SPEAKING"
                    isThinking || voiceStatus == VoiceEngineStatus.PROCESSING -> "THINKING"
                    else -> "TAP SPEAK"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun OperatingModeChip(mode: OperatingMode, modifier: Modifier = Modifier) {
    val (color, icon) = when (mode) {
        OperatingMode.ONLINE -> OnlineEmerald to Icons.Filled.Wifi
        OperatingMode.OFFLINE -> AlertAmber to Icons.Filled.WifiOff
        OperatingMode.CONNECTING, OperatingMode.SWITCHING -> ArcCyan to Icons.Filled.Wifi
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = mode.label, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = mode.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
fun FirebaseAiNoticeBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = ElevatedSlate,
        border = BorderStroke(1.dp, AlertAmber.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Firebase AI Notice",
                tint = AlertAmber,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.firebase_ai_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SensitiveActionDialog(
    action: PendingSensitiveAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = AlertAmber)
        },
        title = {
            Text(action.title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(action.description, style = MaterialTheme.typography.bodyLarge)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.testTag("confirm_sensitive_action")
            ) {
                Text("Confirm & Execute")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, CardBorderSlate),
                modifier = Modifier.testTag("cancel_sensitive_action")
            ) {
                Text("Cancel", color = ErrorCoral)
            }
        }
    )
}
