package com.sysadmindoc.nimbus.util

import com.sysadmindoc.nimbus.data.repository.SkinType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This estimate tells someone how long they can stand in the sun. The
 * direction of every error matters more than its size: opting into a skin
 * type must never shorten the number the app already showed, and no type may
 * ever be told it has longer than the published dose ratio allows.
 */
class UvExposureCalculatorTest {

    @Test
    fun `the unset default is unchanged from the previous estimate`() {
        // The old formula, inlined in two places before this existed.
        listOf(1.0, 3.0, 5.5, 8.0, 11.0).forEach { uv ->
            val previous = (200.0 / (uv * 3.0)).toInt().coerceIn(5, 120)

            assertEquals(previous, UvExposureCalculator.safeMinutes(uv))
        }
    }

    @Test
    fun `type I lands exactly on the cautious default`() {
        listOf(2.0, 6.0, 9.0).forEach { uv ->
            assertEquals(
                UvExposureCalculator.safeMinutes(uv, SkinType.NOT_SET),
                UvExposureCalculator.safeMinutes(uv, SkinType.TYPE_I),
            )
        }
    }

    @Test
    fun `no skin type ever gets less time than the default`() {
        SkinType.entries.forEach { type ->
            listOf(1.0, 4.0, 7.0, 12.0).forEach { uv ->
                val default = UvExposureCalculator.safeMinutes(uv)!!
                val forType = UvExposureCalculator.safeMinutes(uv, type)!!

                assertTrue(
                    "$type at UV $uv gave $forType, less than the default $default",
                    forType >= default,
                )
            }
        }
    }

    @Test
    fun `exposure lengthens monotonically across the Fitzpatrick scale`() {
        val uv = 3.0
        val minutes = listOf(
            SkinType.TYPE_I,
            SkinType.TYPE_II,
            SkinType.TYPE_III,
            SkinType.TYPE_IV,
            SkinType.TYPE_V,
            SkinType.TYPE_VI,
        ).map { UvExposureCalculator.safeMinutes(uv, it)!! }

        assertEquals(minutes.sorted(), minutes)
        assertTrue("expected distinct values, got $minutes", minutes.distinct().size > 1)
    }

    @Test
    fun `the multipliers follow the published minimal erythemal dose ratios`() {
        // MED table: I 200, II 250, III 300, IV 450, V 600, VI 1000 J/m2,
        // expressed relative to type I.
        assertEquals(1.0, SkinType.TYPE_I.exposureMultiplier, 0.0001)
        assertEquals(250.0 / 200.0, SkinType.TYPE_II.exposureMultiplier, 0.0001)
        assertEquals(300.0 / 200.0, SkinType.TYPE_III.exposureMultiplier, 0.0001)
        assertEquals(450.0 / 200.0, SkinType.TYPE_IV.exposureMultiplier, 0.0001)
        assertEquals(600.0 / 200.0, SkinType.TYPE_V.exposureMultiplier, 0.0001)
        assertEquals(1000.0 / 200.0, SkinType.TYPE_VI.exposureMultiplier, 0.0001)
    }

    @Test
    fun `below UV 1 there is no estimate to give`() {
        assertNull(UvExposureCalculator.safeMinutes(0.0, SkinType.TYPE_VI))
        assertNull(UvExposureCalculator.safeMinutes(0.9))
    }

    @Test
    fun `the estimate stays inside the five to one hundred twenty minute bounds`() {
        // Type VI at UV 1 would otherwise read over five hours, which is not a
        // number anyone should act on.
        assertEquals(120, UvExposureCalculator.safeMinutes(1.0, SkinType.TYPE_VI))
        assertEquals(5, UvExposureCalculator.safeMinutes(20.0, SkinType.TYPE_I))
    }
}
