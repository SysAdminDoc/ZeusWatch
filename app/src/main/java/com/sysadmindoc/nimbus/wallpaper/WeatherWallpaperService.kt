package com.sysadmindoc.nimbus.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Live wallpaper service that renders weather-based particle effects
 * over the user's existing wallpaper. The surface is translucent so
 * the system wallpaper shows through underneath the weather effects.
 *
 * Weather condition is read from SharedPreferences (written by the main app
 * whenever a forecast refresh completes) and re-checked every 5 minutes.
 */
class WeatherWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WeatherWallpaperEngine()

    inner class WeatherWallpaperEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val frameIntervalMs = 33L // ~30 fps
        private val weatherRefreshMs = 5 * 60 * 1000L // 5 minutes

        private var visible = false
        private var width = 0
        private var height = 0

        private var weatherCode = 0
        private var particleSystem = WallpaperParticleSystem(WeatherEffect.CLEAR)

        private val prefs: SharedPreferences by lazy {
            applicationContext.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE,
            )
        }

        private val drawRunner = Runnable { drawFrame() }
        private val weatherRefreshRunner = Runnable { refreshWeatherCode() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            surfaceHolder.setFormat(PixelFormat.TRANSLUCENT)
            refreshWeatherCode()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startAnimation()
            scheduleWeatherRefresh()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            w: Int,
            h: Int,
        ) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            particleSystem.reset(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            stopAnimation()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (visible) {
                startAnimation()
                scheduleWeatherRefresh()
            } else {
                stopAnimation()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            stopAnimation()
        }

        // ── Animation loop ──────────────────────────────────────────

        private fun startAnimation() {
            visible = true
            handler.removeCallbacks(drawRunner)
            handler.post(drawRunner)
        }

        private fun stopAnimation() {
            visible = false
            handler.removeCallbacks(drawRunner)
            handler.removeCallbacks(weatherRefreshRunner)
        }

        private fun drawFrame() {
            if (!visible) return

            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    // Clear to transparent so the system wallpaper shows through
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                    particleSystem.update(width, height)
                    particleSystem.draw(canvas, width, height)
                }
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (_: IllegalArgumentException) {
                        // Surface already destroyed
                    }
                }
            }

            handler.removeCallbacks(drawRunner)
            if (visible) {
                handler.postDelayed(drawRunner, frameIntervalMs)
            }
        }

        // ── Weather condition refresh ───────────────────────────────

        private fun scheduleWeatherRefresh() {
            handler.removeCallbacks(weatherRefreshRunner)
            handler.postDelayed(weatherRefreshRunner, weatherRefreshMs)
        }

        private fun refreshWeatherCode() {
            val code = prefs.getInt(KEY_WEATHER_CODE, 0)
            if (code != weatherCode) {
                weatherCode = code
                val effect = WeatherEffect.fromWmoCode(code)
                particleSystem = WallpaperParticleSystem(effect)
                if (width > 0 && height > 0) {
                    particleSystem.reset(width, height)
                }
            }
            if (visible) scheduleWeatherRefresh()
        }
    }

    companion object {
        /** SharedPreferences file used to pass the current weather code to the wallpaper. */
        const val PREFS_NAME = "nimbus_wallpaper"
        const val KEY_WEATHER_CODE = "weather_code"

        /** Helper for the main app to publish the current WMO weather code. */
        fun publishWeatherCode(context: Context, wmoCode: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_WEATHER_CODE, wmoCode)
                .apply()
        }
    }
}

// ── Weather effect classification ───────────────────────────────────

enum class WeatherEffect(val particleCount: Int) {
    CLEAR(50),
    CLOUDY(60),
    FOG(40),
    RAIN(200),
    FREEZING_RAIN(200),
    SNOW(100),
    THUNDERSTORM(200);

    companion object {
        fun fromWmoCode(code: Int): WeatherEffect = when (code) {
            0, 1 -> CLEAR
            2, 3 -> CLOUDY
            45, 48 -> FOG
            in 51..65 -> RAIN
            66, 67 -> FREEZING_RAIN
            in 71..77 -> SNOW
            in 80..82 -> RAIN
            85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> CLEAR
        }
    }
}

// ── Particle data ───────────────────────────────────────────────────

data class Particle(
    var x: Float,
    var y: Float,
    var speedX: Float,
    var speedY: Float,
    var size: Float,
    var alpha: Float,
    var lifetime: Int = 0,
)

// ── Particle system ─────────────────────────────────────────────────

class WallpaperParticleSystem(private val effect: WeatherEffect) {

    private val particles = mutableListOf<Particle>()
    private val rng = Random(System.nanoTime())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Frame counter for time-based effects (lightning, fog pulse, sun rotation)
    private var frame = 0

    fun reset(width: Int, height: Int) {
        particles.clear()
        repeat(effect.particleCount) {
            particles.add(spawnParticle(width.toFloat(), height.toFloat()))
        }
    }

    fun update(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        frame++
        val w = width.toFloat()
        val h = height.toFloat()

        for (p in particles) {
            p.x += p.speedX
            p.y += p.speedY
            p.lifetime++

            // Snow: add horizontal wobble
            if (effect == WeatherEffect.SNOW) {
                p.x += sin(p.lifetime * 0.05f) * 0.5f
            }

            // Reset if off-screen
            if (p.y > h + 20 || p.y < -20 || p.x > w + 20 || p.x < -20) {
                resetParticle(p, w, h)
            }
        }
    }

    fun draw(canvas: Canvas, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()

        when (effect) {
            WeatherEffect.RAIN -> drawRain(canvas, w, h)
            WeatherEffect.FREEZING_RAIN -> drawFreezingRain(canvas, w, h)
            WeatherEffect.SNOW -> drawSnow(canvas, w, h)
            WeatherEffect.THUNDERSTORM -> drawThunderstorm(canvas, w, h)
            WeatherEffect.CLEAR -> drawSunRays(canvas, w, h)
            WeatherEffect.CLOUDY -> drawCloudy(canvas, w, h)
            WeatherEffect.FOG -> drawFog(canvas, w, h)
        }
    }

    // ── Rain ────────────────────────────────────────────────────

    private fun drawRain(canvas: Canvas, w: Float, h: Float) {
        val rainColor = Color.rgb(100, 181, 246) // Blue rain
        for (p in particles) {
            paint.color = rainColor
            paint.alpha = (p.alpha * 255).toInt()
            paint.strokeWidth = 1.5f + p.size * 0.5f
            paint.strokeCap = Paint.Cap.ROUND

            val length = 12f + p.size * 18f
            val angle = 0.15f // slight diagonal
            canvas.drawLine(
                p.x, p.y,
                p.x + length * angle, p.y + length,
                paint,
            )
        }
    }

    // ── Freezing rain (rain + ice shimmer) ──────────────────────

    private fun drawFreezingRain(canvas: Canvas, w: Float, h: Float) {
        drawRain(canvas, w, h)
        // Ice shimmer: occasional bright white-blue sparkles
        val shimmerColor = Color.rgb(200, 230, 255)
        for (p in particles) {
            if (p.lifetime % 7 == 0) {
                paint.color = shimmerColor
                paint.alpha = (p.alpha * 180).toInt()
                canvas.drawCircle(p.x, p.y, 2f + p.size, paint)
            }
        }
    }

    // ── Snow ────────────────────────────────────────────────────

    private fun drawSnow(canvas: Canvas, w: Float, h: Float) {
        for (p in particles) {
            // Alternate between white and light blue
            val snowColor = if (p.lifetime % 3 == 0) {
                Color.rgb(232, 234, 246) // light blue
            } else {
                Color.WHITE
            }
            paint.color = snowColor
            paint.alpha = (p.alpha * 255).toInt()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, 2f + p.size * 3f, paint)
        }
    }

    // ── Thunderstorm (rain + lightning flashes) ─────────────────

    private fun drawThunderstorm(canvas: Canvas, w: Float, h: Float) {
        drawRain(canvas, w, h)

        // Lightning flash: ~2 frames every ~90 frames (~3 seconds)
        val flashCycle = frame % 90
        if (flashCycle in 0..1) {
            paint.color = Color.WHITE
            paint.alpha = 40 + rng.nextInt(30)
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w, h, paint)
        }
    }

    // ── Sun / Clear: golden rays from upper area ────────────────

    private fun drawSunRays(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.75f
        val cy = h * 0.12f
        val rayCount = 14
        val innerRadius = 40f
        val outerRadius = h * 0.35f
        val rotation = (frame * 0.3f) % 360f
        val rotRad = rotation * (PI.toFloat() / 180f)

        for (i in 0 until rayCount) {
            val baseAngle = (i * 360f / rayCount) * (PI.toFloat() / 180f)
            val angle = baseAngle + rotRad
            val startX = cx + cos(angle) * innerRadius
            val startY = cy + sin(angle) * innerRadius
            val endX = cx + cos(angle) * outerRadius
            val endY = cy + sin(angle) * outerRadius

            paint.shader = LinearGradient(
                startX, startY, endX, endY,
                Color.argb(50, 255, 213, 79),
                Color.argb(0, 255, 213, 79),
                Shader.TileMode.CLAMP,
            )
            paint.strokeWidth = 4f
            paint.strokeCap = Paint.Cap.ROUND
            paint.style = Paint.Style.STROKE
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
        paint.shader = null
    }

    // ── Cloudy: subtle gray horizontal drift ────────────────────

    private fun drawCloudy(canvas: Canvas, w: Float, h: Float) {
        for (p in particles) {
            val cx = p.x
            val cy = p.y
            paint.color = Color.rgb(180, 180, 190)
            paint.alpha = (p.alpha * 100).toInt()
            paint.style = Paint.Style.FILL
            // Elongated elliptical cloud wisps
            canvas.save()
            canvas.scale(3f, 1f, cx, cy)
            canvas.drawCircle(cx, cy, 8f + p.size * 6f, paint)
            canvas.restore()
        }
    }

    // ── Fog / Mist: pulsing semi-transparent overlay ────────────

    private fun drawFog(canvas: Canvas, w: Float, h: Float) {
        val pulse = (sin(frame * 0.02f) + 1f) / 2f // 0..1 pulsing
        val baseAlpha = 15 + (pulse * 20).toInt()

        // Several horizontal bands at different heights
        for (i in 0..4) {
            val bandY = h * (0.2f + i * 0.15f) + sin(frame * 0.015f + i) * 15f
            paint.color = Color.WHITE
            paint.alpha = baseAlpha
            paint.strokeWidth = 60f
            paint.strokeCap = Paint.Cap.ROUND
            paint.style = Paint.Style.STROKE
            canvas.drawLine(-30f, bandY, w + 30f, bandY, paint)
        }
    }

    // ── Particle spawn / reset helpers ──────────────────────────

    private fun spawnParticle(w: Float, h: Float): Particle {
        return when (effect) {
            WeatherEffect.RAIN, WeatherEffect.FREEZING_RAIN, WeatherEffect.THUNDERSTORM -> Particle(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                speedX = 0.5f + rng.nextFloat() * 0.5f,
                speedY = 8f + rng.nextFloat() * 10f,
                size = rng.nextFloat(),
                alpha = 0.15f + rng.nextFloat() * 0.35f,
            )
            WeatherEffect.SNOW -> Particle(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                speedX = (rng.nextFloat() - 0.5f) * 0.8f,
                speedY = 1f + rng.nextFloat() * 2.5f,
                size = rng.nextFloat(),
                alpha = 0.2f + rng.nextFloat() * 0.4f,
            )
            WeatherEffect.CLOUDY -> Particle(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                speedX = 0.2f + rng.nextFloat() * 0.3f,
                speedY = 0f,
                size = rng.nextFloat(),
                alpha = 0.05f + rng.nextFloat() * 0.1f,
            )
            WeatherEffect.FOG -> Particle(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                speedX = 0.1f + rng.nextFloat() * 0.15f,
                speedY = 0f,
                size = rng.nextFloat(),
                alpha = 0.03f + rng.nextFloat() * 0.05f,
            )
            WeatherEffect.CLEAR -> Particle(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h * 0.5f,
                speedX = 0f,
                speedY = 0f,
                size = rng.nextFloat(),
                alpha = 0.1f + rng.nextFloat() * 0.15f,
            )
        }
    }

    private fun resetParticle(p: Particle, w: Float, h: Float) {
        when (effect) {
            WeatherEffect.RAIN, WeatherEffect.FREEZING_RAIN, WeatherEffect.THUNDERSTORM -> {
                p.x = rng.nextFloat() * w
                p.y = -rng.nextFloat() * 40f
                p.speedY = 8f + rng.nextFloat() * 10f
                p.lifetime = 0
            }
            WeatherEffect.SNOW -> {
                p.x = rng.nextFloat() * w
                p.y = -rng.nextFloat() * 20f
                p.speedY = 1f + rng.nextFloat() * 2.5f
                p.lifetime = 0
            }
            WeatherEffect.CLOUDY, WeatherEffect.FOG -> {
                p.x = -20f
                p.y = rng.nextFloat() * h
                p.lifetime = 0
            }
            WeatherEffect.CLEAR -> {
                // Sun rays don't really need particle reset
                p.x = rng.nextFloat() * w
                p.y = rng.nextFloat() * h * 0.5f
                p.lifetime = 0
            }
        }
    }
}
