package com.sysadmindoc.nimbus.ui.screen.locations

import android.app.Application
import android.location.Address
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** A dropped pin is named like the current location, then by country, then by its coordinates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MapLocationPickerPlaceNameTest {

    @Test
    fun `a pin in a town takes the town's name over the country's`() {
        assertEquals(
            "Boulder",
            name(address(locality = "Boulder", subAdminArea = "Boulder County", countryName = "United States")),
        )
    }

    @Test
    fun `a blank town name falls through to the county`() {
        assertEquals("Boulder County", name(address(locality = "", subAdminArea = "Boulder County")))
    }

    @Test
    fun `a pin with only a country takes the country's name`() {
        assertEquals("Iceland", name(address(locality = " ", adminArea = "", countryName = "Iceland")))
    }

    @Test
    fun `a pin with no usable name shows its coordinates`() {
        assertEquals("64.1466, -21.9426", name(address(locality = " ", countryName = "  ")))
    }

    @Test
    fun `a pin the geocoder can't place shows its coordinates`() {
        assertEquals("-12.5000, 130.8400", pickerPlaceName(null, -12.5, 130.84))
    }

    private fun name(address: Address) = pickerPlaceName(address, 64.1466, -21.9426)

    private fun address(
        locality: String? = null,
        subAdminArea: String? = null,
        adminArea: String? = null,
        countryName: String? = null,
    ) = Address(Locale.US).apply {
        this.locality = locality
        this.subAdminArea = subAdminArea
        this.adminArea = adminArea
        this.countryName = countryName
    }
}
