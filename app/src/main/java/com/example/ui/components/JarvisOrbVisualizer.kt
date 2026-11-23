package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.ConnectionStateCode
import com.example.domain.model.JarvisState
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusViolet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun JarvisOrbVisualizer(
    jarvisState: JarvisState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    size: Dp = 156.dp,
    onClick: (() -> Unit)? = null
) {
    val transition = rememberInfiniteTransition(label = "jarvis_orb")
    val outerRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (jarvisState) {
                    JarvisState.THINKING, JarvisState.PROCESSING -> 2600
                    JarvisState.LISTENING, JarvisState.SPEAKING -> 3800
                    JarvisState.EXECUTING -> 2200
                    else -> 9500
                },
                easing = LinearEasing
            )
        ),
        label = "outer_rot"
    )
    val innerRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (jarvisState) {
                    JarvisState.THINKING, JarvisState.PROCESSING -> 2000
                    JarvisState.LISTENING, JarvisState.SPEAKING -> 3000
                    else -> 7200
                },
                easing = LinearEasing
            )
        ),
        label = "inner_rot"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (jarvisState) {
                    JarvisState.LISTENING, JarvisState.SPEAKING -> 650
                    JarvisState.THINKING, JarvisState.PROCESSING -> 900
                    else -> 2100
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val (primaryColor, secondaryColor) = when (jarvisState) {
        JarvisState.IDLE -> ElectricCyan to ArcBlue
        JarvisState.LISTENING -> StatusEmerald to CyberTeal
        JarvisState.PROCESSING -> ArcBlue to StatusViolet
        JarvisState.THINKING -> StatusViolet to ElectricCyan
        JarvisState.SPEAKING -> ElectricCyan to StatusEmerald
        JarvisState.EXECUTING -> StatusAmber to ElectricCyan
        JarvisState.OFFLINE -> Color(0xFF64748B) to Color(0xFF475569)
        JarvisState.ERROR -> StatusCrimson to StatusAmber
    }

    val liveBoost = if (jarvisState == JarvisState.LISTENING || jarvisState == JarvisState.SPEAKING) {
        (audioLevel * 0.22f).coerceIn(0f, 0.24f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = "PK JARVIS neural core orb, state ${jarvisState.label}" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 2f

            // Ambient radial energy halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.32f + liveBoost),
                        secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * (0.98f * pulse)
                ),
                radius = baseRadius,
                center = center
            )

            // Outer segmented energy arcs
            val outerR = baseRadius * 0.84f
            val outerStroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
            for (segment in 0..2) {
                drawArc(
                    color = primaryColor.copy(alpha = 0.85f),
                    startAngle = outerRotation + segment * 120f,
                    sweepAngle = 72f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerR, center.y - outerR),
                    size = Size(outerR * 2f, outerR * 2f),
                    style = outerStroke
                )
            }

            // Counter-rotating telemetry ring
            val midR = baseRadius * 0.67f
            val midStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            for (segment in 0..3) {
                drawArc(
                    color = secondaryColor.copy(alpha = 0.72f),
                    startAngle = innerRotation + segment * 90f,
                    sweepAngle = 44f,
                    useCenter = false,
                    topLeft = Offset(center.x - midR, center.y - midR),
                    size = Size(midR * 2f, midR * 2f),
                    style = midStroke
                )
            }

            // Core plasma sphere
            val coreR = baseRadius * (0.44f * pulse + liveBoost * 0.45f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        primaryColor.copy(alpha = 0.88f),
                        secondaryColor.copy(alpha = 0.35f)
                    ),
                    center = center,
                    radius = coreR
                ),
                radius = coreR,
                center = center
            )

            // Subtle crisp ring framing core
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = coreR * 0.72f,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = jarvisState.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF06101B)
            )
        }
    }
}

@Composable
fun LiveAudioWaveform(
    jarvisState: JarvisState,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "wave_phase"
    )

    val barColor = when (jarvisState) {
        JarvisState.LISTENING -> StatusEmerald
        JarvisState.SPEAKING -> ElectricCyan
        JarvisState.THINKING, JarvisState.PROCESSING -> StatusViolet
        JarvisState.EXECUTING -> StatusAmber
        JarvisState.ERROR -> StatusCrimson
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    }

    val isActive = jarvisState in setOf(
        JarvisState.LISTENING,
        JarvisState.SPEAKING,
        JarvisState.THINKING,
        JarvisState.PROCESSING,
        JarvisState.EXECUTING
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        val barCount = 28
        val spacing = size.width / barCount
        val barWidth = (spacing * 0.46f).coerceAtLeast(3f)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val normalizedIndex = (i.toFloat() / (barCount - 1)) * 2f - 1f
            val envelope = 1f - abs(normalizedIndex) * 0.72f
            val wave = if (isActive) {
                val boost = if (audioLevel > 0.05f) audioLevel else 0.55f
                val s = abs(sin(phase + i * 0.48f))
                (0.18f + s * 0.82f * boost) * envelope
            } else {
                0.14f * envelope
            }
            val barH = (size.height * wave).coerceIn(4f, size.height)
            val x = i * spacing + (spacing - barWidth) / 2f
            drawRoundRect(
                color = barColor.copy(alpha = if (isActive) 0.92f else 0.4f),
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun ConnectionStatusBadge(
    status: ConnectionStateCode,
    modifier: Modifier = Modifier
) {
    val (accent, bg) = when (status) {
        ConnectionStateCode.CONNECTED -> StatusEmerald to StatusEmerald.copy(alpha = 0.14f)
        ConnectionStateCode.TESTING_CONNECTION -> StatusAmber to StatusAmber.copy(alpha = 0.14f)
        ConnectionStateCode.SAVED_SECURELY -> ArcBlue to ArcBlue.copy(alpha = 0.14f)
        ConnectionStateCode.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
        ConnectionStateCode.RATE_LIMITED -> StatusAmber to StatusAmber.copy(alpha = 0.14f)
        else -> StatusCrimson to StatusCrimson.copy(alpha = 0.14f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
    }
}
