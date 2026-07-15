package com.sysadmindoc.nimbus.ui.component

import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.VisibilityUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class PwsObservationCardTest {

    private val kmSettings = NimbusSettings(visibilityUnit = VisibilityUnit.KM)
    private val milesSettings = NimbusSettings(visibilityUnit = VisibilityUnit.MILES)

    @Test
    fun lightningDistanceInKmForMetricUsers() {
        assertEquals("5.0 km", formatLightningDistance(5.0, kmSettings))
        assertEquals("12.4 km", formatLightningDistance(12.4, kmSettings))
    }

    @Test
    fun lightningDistanceConvertedToMilesForImperialUsers() {
        assertEquals("1.0 mi", formatLightningDistance(1.609344, milesSettings))
        assertEquals("5.0 mi", formatLightningDistance(8.04672, milesSettings))
        // Tempest reports lightning out to ~40 km; make sure long distances
        // survive the conversion instead of being capped.
        assertEquals("24.9 mi", formatLightningDistance(40.0, milesSettings))
    }

    @Test
    fun lightningDistanceKeepsOneDecimal() {
        assertEquals("3.3 mi", formatLightningDistance(5.3, milesSettings))
        assertEquals("0.6 mi", formatLightningDistance(1.0, milesSettings))
    }
}
