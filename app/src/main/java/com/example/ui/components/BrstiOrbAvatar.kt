package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.state.AssistantMood
import com.example.ui.state.EmotionTone
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.WarmGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BrstiOrbAvatar(
    mood: AssistantMood,
    emotionTone: EmotionTone,
    userAudioLevel: Float,
    assistantAudioLevel: Float,
    onAvatarTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "brsti_orb")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    AssistantMood.THINKING -> 2200
                    AssistantMood.SPEAKING -> 4200
                    AssistantMood.LISTENING -> 6000
                    AssistantMood.SLEEPING -> 14000
                    AssistantMood.WAITING -> 10000
                },
                easing = LinearEasing
            )
        ),
        label = "orbit"
    )
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == AssistantMood.SLEEPING) 3200 else 1700,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val reactiveLevel = when (mood) {
        AssistantMood.SPEAKING -> assistantAudioLevel
        AssistantMood.LISTENING -> userAudioLevel
        else -> 0.05f
    }
    val smoothAudio by animateFloatAsState(
        targetValue = reactiveLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "audio_smooth"
    )

    val primaryGlow = when (mood) {
        AssistantMood.LISTENING -> NeonCyan
        AssistantMood.SPEAKING -> NeonMagenta
        AssistantMood.THINKING -> WarmGold
        AssistantMood.SLEEPING -> ElectricBlue.copy(alpha = 0.45f)
        AssistantMood.WAITING -> NeonViolet
    }
    val secondaryGlow = when (mood) {
        AssistantMood.LISTENING -> ElectricBlue
        AssistantMood.SPEAKING -> NeonCyan
        AssistantMood.THINKING -> NeonViolet
        AssistantMood.SLEEPING -> NeonViolet.copy(alpha = 0.35f)
        AssistantMood.WAITING -> ElectricBlue
    }

    val cdText = stringResource(R.string.cd_brsti_avatar)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(288.dp)
                .semantics { contentDescription = cdText }
                .testTag("brsti_avatar_orb")
                .clip(CircleShape)
                .clickable(onClick = onAvatarTap)
        ) {
            // Outer reactive Canvas rings & harmonic wave petals
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension * 0.31f
                val pulseRadius = baseRadius * (breathe + smoothAudio * 0.24f)

                // Ambient radial aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = if (mood == AssistantMood.SLEEPING) 0.18f else 0.42f),
                            secondaryGlow.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.49f
                    ),
                    radius = size.minDimension * 0.49f,
                    center = center
                )

                // Voice-reactive harmonic wave ring
                val ringPoints = 48
                val ringR = pulseRadius + 16.dp.toPx()
                for (i in 0 until ringPoints) {
                    val angleRad = (2.0 * PI * i / ringPoints) + Math.toRadians(orbitAngle.toDouble())
                    val harmonic = when (mood) {
                        AssistantMood.SPEAKING -> sin(angleRad * 6.0) * (10.dp.toPx() + smoothAudio * 22.dp.toPx())
                        AssistantMood.LISTENING -> cos(angleRad * 4.0) * (4.dp.toPx() + smoothAudio * 18.dp.toPx())
                        AssistantMood.THINKING -> sin(angleRad * 3.0) * 8.dp.toPx()
                        AssistantMood.SLEEPING -> 0.0
                        AssistantMood.WAITING -> sin(angleRad * 2.0) * 3.dp.toPx()
                    }
                    val r = (ringR + harmonic).toFloat()
                    val dotPos = Offset(
                        x = center.x + cos(angleRad).toFloat() * r,
                        y = center.y + sin(angleRad).toFloat() * r
                    )
                    drawCircle(
                        color = if (i % 2 == 0) primaryGlow.copy(alpha = 0.85f) else secondaryGlow.copy(alpha = 0.75f),
                        radius = if (mood == AssistantMood.SPEAKING) (2.5f + smoothAudio * 3f).dp.toPx() else 2.dp.toPx(),
                        center = dotPos
                    )
                }

                // Rotating dual neon arcs
                rotate(degrees = orbitAngle, pivot = center) {
                    val arcRadius = baseRadius + 28.dp.toPx() + smoothAudio * 14.dp.toPx()
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color.Transparent, primaryGlow, secondaryGlow, Color.Transparent)
                        ),
                        startAngle = 0f,
                        sweepAngle = if (mood == AssistantMood.THINKING) 220f else 135f,
                        useCenter = false,
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color.Transparent, secondaryGlow, primaryGlow, Color.Transparent)
                        ),
                        startAngle = 180f,
                        sweepAngle = if (mood == AssistantMood.THINKING) 200f else 120f,
                        useCenter = false,
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Inner glowing holographic orb core with generated neural aura artwork
            val coreSize = (158f + smoothAudio * 18f).dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(coreSize)
                    .clip(CircleShape)
                    .background(ObsidianDeep)
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(listOf(primaryGlow, secondaryGlow)),
                        shape = CircleShape
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_brsti_aura_1794935013982),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (mood == AssistantMood.SLEEPING) 0.35f else 0.88f)
                )

                // Central state emblem & BRSTI monogram
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(
                            brush = Brush.radialGradient(
                                listOf(ObsidianDeep.copy(alpha = 0.72f), Color.Transparent)
                            )
                        )
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = when (mood) {
                            AssistantMood.LISTENING -> Icons.Rounded.Hearing
                            AssistantMood.SPEAKING -> Icons.Rounded.GraphicEq
                            AssistantMood.THINKING -> Icons.Rounded.Psychology
                            AssistantMood.SLEEPING -> Icons.Rounded.Bedtime
                            AssistantMood.WAITING -> Icons.Rounded.AutoAwesome
                        },
                        contentDescription = null,
                        tint = primaryGlow,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "BRSTI",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mood & Expressive Emotion Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = ObsidianCard.copy(alpha = 0.85f),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryGlow.copy(alpha = 0.6f))
            ) {
                Text(
                    text = mood.label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryGlow,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Surface(
                color = ObsidianCard.copy(alpha = 0.75f),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, secondaryGlow.copy(alpha = 0.45f))
            ) {
                Text(
                    text = emotionTone.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
