package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.sysadmindoc.nimbus.data.model.WeatherCode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Canvas-drawn weather particle effects overlaid on the header.
 * Rain: angled droplets falling. Snow: drifting flakes. Clear: sun rays / star twinkle.
 */
@Composable
fun WeatherParticles(
    weatherCode: WeatherCode,
    isDay: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "particles")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val slowPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "slowPhase",
    )

    val rotationPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    val particles = remember(weatherCode) {
        generateParticles(weatherCode, count = 40)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        when {
            weatherCode.isRainy -> drawRain(particles, phase)
            weatherCode.isSnowy -> drawSnow(particles, slowPhase)
            weatherCode.isStormy -> {
                drawRain(particles, phase)
                // Storm flash effect on certain phase values
                if (phase in 0.48f..0.52f) {
                    drawRect(Color.White.copy(alpha = 0.03f))
                }
            }
            weatherCode == WeatherCode.CLEAR_SKY || weatherCode == WeatherCode.MAINLY_CLEAR -> {
                if (isDay) drawSunRays(rotationPhase)
                else drawStars(particles, slowPhase)
            }
            weatherCode.isFoggy -> drawFog(slowPhase)
            // Cloudy/partly cloudy - no particles
        }
    }
}

private data class Particle(
    val x: Float,      // 0..1 normalized position
    val y: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float,
    val drift: Float,  // horizontal drift for snow
)

private fun generateParticles(code: WeatherCode, count: Int): List<Particle> {
    val rng = Random(code.code.toLong() + 42)
    return List(count) {
        Particle(
            x = rng.nextFloat(),
            y = rng.nextFloat(),
            size = rng.nextFloat() * 0.5f + 0.5f,
            speed = rng.nextFloat() * 0.4f + 0.6f,
            alpha = rng.nextFloat() * 0.3f + 0.15f,
            drift = (rng.nextFloat() - 0.5f) * 0.02f,
        )
    }
}

private fun DrawScope.drawRain(particles: List<Particle>, phase: Float) {
    val w = size.width
    val h = size.height
    val rainColor = Color(0xFF64B5F6)

    particles.forEach { p ->
        val y = ((p.y + phase * p.speed) % 1f) * h
        val x = (p.x + phase * 0.05f) % 1f * w
        val length = 12f + p.size * 16f
        val angle = 0.15f // slight angle

        drawLine(
            color = rainColor.copy(alpha = p.alpha),
            start = Offset(x, y),
            end = Offset(x + length * angle, y + length),
            strokeWidth = 1.5f + p.size * 0.5f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnow(particles: List<Particle>, phase: Float) {
    val w = size.width
    val h = size.height
    val snowColor = Color(0xFFE8EAF6)

    particles.forEach { p ->
        val y = ((p.y + phase * p.speed * 0.5f) % 1f) * h
        val drift = sin((phase + p.x) * 2 * PI.toFloat()) * 20f * p.drift * 10f
        val x = (p.x * w) + drift

        val radius = 1.5f + p.size * 2.5f
        drawCircle(
            color = snowColor.copy(alpha = p.alpha * 0.8f),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawSunRays(rotation: Float) {
    val cx = size.width * 0.75f
    val cy = size.height * 0.2f
    val rayCount = 12
    val innerRadius = 30f
    val outerRadius = 80f

    rotate(rotation, pivot = Offset(cx, cy)) {
        for (i in 0 until rayCount) {
            val angle = (i * 360f / rayCount) * (PI.toFloat() / 180f)
            val startX = cx + cos(angle) * innerRadius
            val startY = cy + sin(angle) * innerRadius
            val endX = cx + cos(angle) * outerRadius
            val endY = cy + sin(angle) * outerRadius

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x33FFD54F),
                        Color(0x00FFD54F),
                    ),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawStars(particles: List<Particle>, phase: Float) {
    val w = size.width
    val h = size.height

    particles.take(20).forEach { p ->
        val twinkle = (sin((phase + p.x * 3f) * 2 * PI.toFloat()) + 1f) / 2f
        val alpha = p.alpha * 0.5f * twinkle + 0.05f
        val radius = 1f + p.size * 1.5f

        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = radius,
            center = Offset(p.x * w, p.y * h * 0.6f),
        )
    }
}

private fun DrawScope.drawFog(phase: Float) {
    val w = size.width
    val h = size.height

    for (i in 0..3) {
        val y = h * (0.3f + i * 0.15f) + sin(phase * 2 * PI.toFloat() + i) * 10f
        val alpha = 0.04f + (sin(phase * PI.toFloat() + i * 0.5f) + 1f) * 0.02f

        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(-20f, y),
            end = Offset(w + 20f, y),
            strokeWidth = 40f,
            cap = StrokeCap.Round,
        )
    }
}
