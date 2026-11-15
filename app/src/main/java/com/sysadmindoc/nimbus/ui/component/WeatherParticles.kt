package com.sysadmindoc.nimbus.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.data.model.WeatherCode
import com.sysadmindoc.nimbus.util.isReducedMotionEnabled
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One frame of the weather particles behind the header, for
 * [particlesBehind]: null while the app is in the background or the system
 * has animations off.
 * Rain: angled droplets falling. Snow: drifting flakes. Clear: sun rays / star twinkle.
 * Gravity sensor support: particles respond to device tilt for parallax effect.
 * Inspired by breezy-weather's MaterialPainterView + DelayRotateController.
 */
@Composable
fun rememberWeatherParticles(
    weatherCode: WeatherCode,
    isDay: Boolean,
): (DrawScope.() -> Unit)? {
    // Stop animations when app is backgrounded to save battery.
    // currentStateAsState() observes lifecycle transitions; deriving from the
    // raw (non-snapshot) currentState computed once and never updated.
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) return null

    // Respect system reduced motion / animation off setting
    if (isReducedMotionEnabled()) return null

    // Partly cloudy and overcast draw nothing, so they get no sensor, clock
    // or erase layers to spend on drawing nothing.
    val kind = weatherCode.particleKind() ?: return null

    // Gravity sensor for gyroscopic parallax. Keep the State unwrapped here and
    // read .value inside the draw lambda so sensor updates only
    // invalidate the draw phase instead of recomposing at sensor rate.
    val gravityTilt = rememberGravityTilt()

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

    return remember(weatherCode, isDay, particles) {
        {
            // Gravity-based pixel offset for parallax (max ~20px shift) — read in
            // the draw phase, see note above.
            val gravity = gravityTilt.value
            val offsetX = gravity.x * 20f
            val offsetY = gravity.y * 12f

            when (kind) {
                ParticleKind.RAIN -> drawRain(particles, phase, offsetX, offsetY)
                ParticleKind.SNOW -> drawSnow(particles, slowPhase, offsetX, offsetY)
                ParticleKind.STORM -> {
                    drawRain(particles, phase, offsetX, offsetY)
                    if (phase in 0.48f..0.52f) {
                        drawRect(Color.White.copy(alpha = 0.03f))
                    }
                }
                ParticleKind.CLEAR -> {
                    if (isDay) drawSunRays(rotationPhase, offsetX, offsetY)
                    else drawStars(particles, slowPhase, offsetX, offsetY)
                }
                ParticleKind.FOG -> drawFog(slowPhase, offsetY)
            }
        }
    }
}

internal enum class ParticleKind { RAIN, SNOW, STORM, CLEAR, FOG }

/**
 * What [rememberWeatherParticles] draws for this sky, or null for nothing.
 * One answer for both the early return and the draw, so they can't disagree.
 */
internal fun WeatherCode.particleKind(): ParticleKind? = when {
    isRainy -> ParticleKind.RAIN
    isSnowy -> ParticleKind.SNOW
    isStormy -> ParticleKind.STORM
    this == WeatherCode.CLEAR_SKY || this == WeatherCode.MAINLY_CLEAR -> ParticleKind.CLEAR
    isFoggy -> ParticleKind.FOG
    else -> null
}

/**
 * Draws [particles] behind this content, erased within [clearance] of
 * everything the content draws. Rain peaks at 0.45 alpha and snow at 0.36,
 * enough to drop white text under 4.5:1 where one crosses a letter, so the
 * hero's text and icons always sit on the bare sky that `skyTint` reads
 * against, and the particles show between and around them.
 *
 * The particles move every frame and the content almost never does, so the
 * two live in separate nodes. The inner one sits behind its own layer and
 * builds the erase mask only when the content redraws. The outer one reads
 * the animation, and each frame costs it one particle layer and one blit of
 * the finished mask.
 *
 * Remember the result, keyed on [particles]: every call builds new caches,
 * so calling it on each recomposition rebuilds the mask each time.
 */
fun Modifier.particlesBehind(
    particles: (DrawScope.() -> Unit)?,
    clearance: Dp = PARTICLE_CLEARANCE,
): Modifier {
    if (particles == null) return this
    val eraser = EraseMask()
    return graphicsLayer()
        .drawWithCache {
            val hero = obtainGraphicsLayer()
            val bounds = size.toRect()
            val ownLayer = Paint()
            val erase = Paint().apply { blendMode = BlendMode.DstOut }
            onDrawWithContent {
                // Recorded first: drawing the inner node is what brings the
                // mask up to date when the content has changed.
                hero.record { this@onDrawWithContent.drawContent() }
                val mask = eraser.mask
                if (mask != null) {
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(bounds, ownLayer)
                        particles()
                        canvas.saveLayer(bounds, erase)
                        drawLayer(mask)
                        canvas.restore()
                        canvas.restore()
                    }
                }
                drawLayer(hero)
            }
        }
        .graphicsLayer()
        .drawWithCache {
            val content = obtainGraphicsLayer()
            // The content smeared sideways, then that stamped up and down: a
            // solid square of [clearance] around every pixel it draws, in
            // 2 × 13 draws. Rings of stamps left holes beside small marks like
            // the dot on an i. Both are offscreen so the finished mask is a
            // cached bitmap until the content changes.
            val widened = obtainGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
            val mask = obtainGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
            val shifts = clearanceShifts(clearance.toPx(), CLEARANCE_STEP.toPx())
            onDrawWithContent {
                content.record { this@onDrawWithContent.drawContent() }
                widened.record { shifts.forEach { dx -> translate(left = dx) { drawLayer(content) } } }
                mask.record { shifts.forEach { dy -> translate(top = dy) { drawLayer(widened) } } }
                eraser.mask = mask
                drawLayer(content)
            }
        }
}

/** Hands the inner node's finished mask to the outer node that erases with it. */
private class EraseMask {
    var mask: GraphicsLayer? = null
}

/** Room left around every letter and icon, wide enough to read as a halo. */
internal val PARTICLE_CLEARANCE = 3.dp

/** Under the thinnest stroke or smallest dot the hero draws, so no gap opens between stamps. */
private val CLEARANCE_STEP = 0.5.dp

/** Even shifts from -[radius] to [radius], no further apart than [step]. */
internal fun clearanceShifts(radius: Float, step: Float): List<Float> {
    val gaps = ceil(2 * radius / step).toInt().coerceAtLeast(1)
    return List(gaps + 1) { i -> -radius + 2 * radius * i / gaps }
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

private fun DrawScope.drawRain(particles: List<Particle>, phase: Float, gx: Float = 0f, gy: Float = 0f) {
    val w = size.width
    val h = size.height
    val rainColor = Color(0xFF64B5F6)

    particles.forEach { p ->
        val y = ((p.y + phase * p.speed) % 1f) * h
        val x = (p.x + phase * 0.05f) % 1f * w + gx * p.speed
        val length = 12.dp.toPx() + p.size * 16.dp.toPx()
        val angle = 0.15f + gx * 0.03f

        drawLine(
            color = rainColor.copy(alpha = p.alpha),
            start = Offset(x, y + gy),
            end = Offset(x + length * angle, y + length + gy),
            strokeWidth = 1.5.dp.toPx() + p.size * 0.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnow(particles: List<Particle>, phase: Float, gx: Float = 0f, gy: Float = 0f) {
    val w = size.width
    val h = size.height
    val snowColor = Color(0xFFE8EAF6)

    particles.forEach { p ->
        val y = ((p.y + phase * p.speed * 0.5f) % 1f) * h + gy
        val drift = sin((phase + p.x) * 2 * PI.toFloat()) * 20f * p.drift * 10f
        val x = (p.x * w) + drift + gx * p.speed * 2f

        val radius = 1.5.dp.toPx() + p.size * 2.5.dp.toPx()
        drawCircle(
            color = snowColor.copy(alpha = p.alpha * 0.8f),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawSunRays(rotation: Float, gx: Float = 0f, gy: Float = 0f) {
    val cx = size.width * 0.75f + gx * 2f
    val cy = size.height * 0.2f + gy * 1.5f
    val rayCount = 12
    val innerRadius = 30.dp.toPx()
    val outerRadius = 80.dp.toPx()

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
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawStars(particles: List<Particle>, phase: Float, gx: Float = 0f, gy: Float = 0f) {
    val w = size.width
    val h = size.height

    particles.take(20).forEach { p ->
        val twinkle = (sin((phase + p.x * 3f) * 2 * PI.toFloat()) + 1f) / 2f
        val alpha = p.alpha * 0.5f * twinkle + 0.05f
        val radius = 1.dp.toPx() + p.size * 1.5.dp.toPx()

        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = radius,
            center = Offset(p.x * w + gx * 1.5f, p.y * h * 0.6f + gy),
        )
    }
}

private fun DrawScope.drawFog(phase: Float, gy: Float = 0f) {
    val w = size.width
    val h = size.height

    for (i in 0..3) {
        val y = h * (0.3f + i * 0.15f) + sin(phase * 2 * PI.toFloat() + i) * 10f + gy
        val alpha = 0.04f + (sin(phase * PI.toFloat() + i * 0.5f) + 1f) * 0.02f

        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(-20f, y),
            end = Offset(w + 20f, y),
            strokeWidth = 40.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
