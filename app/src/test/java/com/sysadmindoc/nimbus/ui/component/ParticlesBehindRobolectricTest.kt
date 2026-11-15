package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.nimbus.data.model.WeatherCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The hero's weather particles sit behind its text, and rain at 0.45 alpha
 * drops even white text under 4.5:1 where a streak crosses a letter. The
 * particles here are one opaque magenta sheet, the worst particle there is:
 * every pixel near a letter must still be the bare sky, and the sheet must
 * still show away from the text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ParticlesBehindRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun particlesKeepClearOfEveryLetterAndStillShowAroundThem() {
        var showParticles by mutableStateOf(false)
        composeTestRule.setContent {
            Box(Modifier.background(SKY).testTag(TAG)) {
                Text(
                    text = "Wind 12 mph  Humidity 81%",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .particlesBehind(if (showParticles) ({ drawRect(PARTICLE) }) else null)
                        .padding(16.dp),
                )
            }
        }
        val bare = composeTestRule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        showParticles = true
        composeTestRule.waitForIdle()
        val drawn = composeTestRule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        assertEquals(bare.width, drawn.width)
        assertEquals(bare.height, drawn.height)

        val density = composeTestRule.density.density
        val letters = pixels(bare) { it != SKY.toArgb() }
        assertTrue("the text drew nothing to measure", letters.size > 500)

        // Within half a dp of the full clearance around any letter pixel, the
        // sky shows as if no particle were drawn, so a halo that shrinks fails.
        val halo = ((PARTICLE_CLEARANCE.value - 0.5f) * density).toInt()
        var nearChecked = 0
        within(letters, halo, bare) { x, y ->
            nearChecked++
            assertTrue(
                "particle at ($x, $y), within $halo px of a letter: ${hex(drawn.argb(x, y))} where the bare sky is ${hex(bare.argb(x, y))}",
                close(drawn.argb(x, y), bare.argb(x, y)),
            )
        }
        assertTrue(nearChecked > letters.size)

        // Past the clearance and a margin, the particles draw untouched.
        val reach = ((PARTICLE_CLEARANCE.value + 2) * density).toInt()
        val near = BooleanArray(bare.width * bare.height)
        within(letters, reach, bare) { x, y -> near[y * bare.width + x] = true }
        var farChecked = 0
        for (y in 0 until bare.height) for (x in 0 until bare.width) {
            if (near[y * bare.width + x]) continue
            farChecked++
            assertEquals("no particle at ($x, $y), far from the text", hex(PARTICLE.toArgb()), hex(drawn.argb(x, y)))
        }
        assertTrue("nothing was far enough from the text to check", farChecked > 1000)
    }

    @Test
    fun skiesWithNothingToDrawGetNoParticleLayers() {
        val drawn = mutableMapOf<WeatherCode, Boolean>()
        composeTestRule.setContent {
            WeatherCode.entries.forEach { code ->
                drawn[code] = rememberWeatherParticles(code, isDay = true) != null
            }
        }
        composeTestRule.waitForIdle()

        // Written out rather than derived from particleKind(), so dropping fog
        // or snow from the guard fails here.
        val bare = setOf(WeatherCode.PARTLY_CLOUDY, WeatherCode.OVERCAST, WeatherCode.UNKNOWN)
        assertEquals(
            WeatherCode.entries.associateWith { it !in bare },
            drawn.toMap(),
        )
    }

    private fun PixelMap.argb(x: Int, y: Int): Int = this[x, y].toArgb()

    private fun pixels(map: PixelMap, keep: (Int) -> Boolean): List<Pair<Int, Int>> = buildList {
        for (y in 0 until map.height) for (x in 0 until map.width) if (keep(map.argb(x, y))) add(x to y)
    }

    private inline fun within(points: List<Pair<Int, Int>>, radius: Int, map: PixelMap, visit: (Int, Int) -> Unit) {
        val seen = BooleanArray(map.width * map.height)
        for ((px, py) in points) {
            for (dy in -radius..radius) for (dx in -radius..radius) {
                if (dx * dx + dy * dy > radius * radius) continue
                val x = px + dx
                val y = py + dy
                if (x !in 0 until map.width || y !in 0 until map.height) continue
                if (seen[y * map.width + x]) continue
                seen[y * map.width + x] = true
                visit(x, y)
            }
        }
    }

    private fun close(a: Int, b: Int): Boolean =
        (0..16 step 8).all { shift -> abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) <= 2 }

    private fun hex(argb: Int): String = "#%08X".format(argb)

    private companion object {
        const val TAG = "hero"
        val SKY = Color(0xFF34689D)
        val PARTICLE = Color(0xFFFF00FF)
    }
}
