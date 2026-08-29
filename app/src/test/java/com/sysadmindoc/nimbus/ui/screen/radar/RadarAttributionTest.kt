package com.sysadmindoc.nimbus.ui.screen.radar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The attribution strip carries the "informational only, not for protection of
 * life or property" qualifier whenever lightning is drawn.
 *
 * It used to be gated on the Radar layer alone, so the dedicated Lightning
 * view, the one surface showing nothing but strikes, carried no qualifier at
 * all. That shipped and no test noticed, which is what this is for.
 */
class RadarAttributionTest {

    @Test
    fun `the lightning layer shows the attribution`() {
        assertTrue(radarLayerShowsAttribution(RadarLayer.LIGHTNING))
    }

    @Test
    fun `the radar layer shows the attribution`() {
        assertTrue(radarLayerShowsAttribution(RadarLayer.RADAR))
    }

    @Test
    fun `every layer that draws strikes carries the qualifier`() {
        // Derived rather than listed, so a layer added later that draws
        // lightning cannot ship without the qualifier.
        RadarLayer.entries.filter { radarLayerShowsLightning(it) }.forEach { layer ->
            assertTrue("$layer draws strikes with no attribution", radarLayerShowsAttribution(layer))
        }
    }

    @Test
    fun `a layer that draws no strikes does not claim to`() {
        assertFalse(radarLayerShowsLightning(RadarLayer.SATELLITE))
    }
}
