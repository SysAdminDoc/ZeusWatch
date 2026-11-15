package com.sysadmindoc.nimbus.ui.screen.radar

import com.sysadmindoc.nimbus.data.model.ReportCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Report markers are images. A text label needs glyphs, and the basemaps
 * have neither colour emoji nor the default font stack.
 */
class ReportIconsTest {

    @Test
    fun `every condition has its own icon`() {
        val ids = ReportCondition.entries.map(::reportIconId)
        assertEquals(ReportCondition.entries.size, ids.toSet().size)
        ids.forEach { assertTrue(it, Regex("report-icon-[a-z_]+").matches(it)) }
    }

    @Test
    fun `the report layer draws icons and asks for no glyphs`() {
        val source = radarMapViewSource()
        val reports = source.substringAfter("private fun updateCommunityReports(")
            .substringBefore("\nprivate fun ")
        assertTrue("report features must name their icon", reports.contains("reportIconId(report.condition)"))
        assertTrue("the report layer must draw icons", reports.contains("PropertyFactory.iconImage("))
        assertTrue("icons must be on the style before the layer uses them", reports.contains("ensureReportIcons()"))
        assertFalse("a text label needs glyphs the basemaps don't serve", source.contains("textField("))
    }

    private fun radarMapViewSource(): String {
        val cwd = Path.of("").toAbsolutePath()
        val app = listOfNotNull(cwd, cwd.resolve("app"), cwd.parent?.resolve("app"))
            .first { Files.exists(it.resolve("src/main/AndroidManifest.xml")) }
        return app.resolve("src/main/java/com/sysadmindoc/nimbus/ui/screen/radar/RadarMapView.kt").readText()
    }
}
