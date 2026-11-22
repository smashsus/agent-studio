package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.SwissGeometry
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreenOverlay(
    visible: Boolean,
    modelLabel: String,
    onDismiss: () -> Unit
) {
    val tokens = LocalSwissTokens.current
    val emblemScale = remember { Animatable(0.88f) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        emblemScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 780, easing = FastOutSlowInEasing)
        )
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 220))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.background)
                .safeDrawingPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = tokens.surface1,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(SwissGeometry.borderWidth, tokens.border.copy(alpha = 0.7f)),
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .size(84.dp)
                        .graphicsLayer {
                            scaleX = emblemScale.value
                            scaleY = emblemScale.value
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.minDimension / 2f
                        val hexPath = Path()
                        for (i in 0 until 6) {
                            val angle = Math.toRadians((60 * i - 90).toDouble())
                            val x = cx + (r * cos(angle)).toFloat()
                            val y = cy + (r * sin(angle)).toFloat()
                            if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
                        }
                        hexPath.close()
                        drawPath(
                            path = hexPath,
                            color = tokens.primaryText,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        val innerR = r * 0.44f
                        val diamond = Path().apply {
                            moveTo(cx, cy - innerR)
                            lineTo(cx + innerR, cy)
                            lineTo(cx, cy + innerR)
                            lineTo(cx - innerR, cy)
                            close()
                        }
                        drawPath(path = diamond, color = tokens.primaryAccent)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Agent Studio",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = SpaceGroteskFamily,
                            fontSize = 28.sp
                        ),
                        color = tokens.primaryText
                    )
                    Text(
                        text = "Autonomous Android Workspace",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText
                    )
                }

                Surface(
                    color = tokens.primaryAccent.copy(alpha = 0.12f),
                    shape = CircleShape
                ) {
                    Text(
                        text = modelLabel.ifBlank { "OpenAI-Compatible Runtime" },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp
                        ),
                        color = tokens.primaryAccent,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .width(156.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(tokens.surface3)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(tokens.primaryAccent)
                    )
                }
            }
        }
    }
}
