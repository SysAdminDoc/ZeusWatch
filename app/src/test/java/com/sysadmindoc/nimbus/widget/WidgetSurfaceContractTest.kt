package com.sysadmindoc.nimbus.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class WidgetSurfaceContractTest {

    @Test
    fun `all widget receivers use the shared freshness and refresh contract`() {
        val manifest = readAppSource("src/main/AndroidManifest.xml")
        val worker = readAppSource("src/main/java/com/sysadmindoc/nimbus/widget/WidgetRefreshWorker.kt")
        val dataProvider = readAppSource("src/main/java/com/sysadmindoc/nimbus/widget/WidgetDataProvider.kt")

        assertTrue(dataProvider.contains("val updatedAt: Long = 0L"))
        assertTrue(dataProvider.contains("val observedAt: Long = 0L"))
        assertTrue(dataProvider.contains("val sourceProvider: String? = null"))
        assertTrue(worker.contains("sourceProvider = weatherData.sourceProvider"))
        assertTrue(worker.contains("observedAt = weatherData.current.observationTime"))

        widgetSurfaces.forEach { surface ->
            assertTrue(
                "${surface.receiverClass} is not registered in AndroidManifest.xml",
                manifest.contains("""android:name=".widget.${surface.receiverClass}""""),
            )

            val source = readAppSource("src/main/java/com/sysadmindoc/nimbus/widget/${surface.fileName}")
            assertTrue(
                "${surface.receiverClass} must inherit widget lifecycle cleanup",
                source.contains("class ${surface.receiverClass} : NimbusWidgetReceiverBase()"),
            )
            assertTrue(
                "${surface.widgetClass} must read the shared widget cache",
                source.contains(surface.loaderCall),
            )
            assertTrue(
                "${surface.widgetClass} must show cache age from updatedAt",
                source.contains("updatedLabel("),
            )
            assertTrue(
                "${surface.widgetClass} freshness badge must be tappable refresh",
                source.contains("widgetRefreshBadgeAction()") ||
                    source.contains("actionRunCallback<WidgetRefreshAction>()"),
            )
            assertTrue(
                "${surface.widgetClass} must render a visible freshness badge",
                source.contains("WidgetStatusBadge(") ||
                    source.contains("WidgetMiniStatusBadge("),
            )
            assertTrue(
                "${surface.receiverClass} must be included in worker widget enumeration",
                worker.contains("${surface.receiverClass}::class.java"),
            )
            assertTrue(
                "${surface.widgetClass} must be updated after refresh",
                worker.contains("${surface.widgetClass}().updateAll(applicationContext)"),
            )
        }
    }

    private fun readAppSource(relativePath: String): String =
        appDir().resolve(relativePath).toFile().readText()

    private fun appDir(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return listOf(
            cwd,
            cwd.resolve("app"),
            cwd.parent?.resolve("app"),
        ).filterNotNull().first { candidate ->
            Files.exists(candidate.resolve("src/main/AndroidManifest.xml"))
        }
    }

    private data class WidgetSurface(
        val fileName: String,
        val widgetClass: String,
        val receiverClass: String,
        val loaderCall: String,
    )

    private companion object {
        private val widgetSurfaces = listOf(
            WidgetSurface(
                fileName = "NimbusSmallWidget.kt",
                widgetClass = "NimbusSmallWidget",
                receiverClass = "NimbusSmallWidgetReceiver",
                loaderCall = "WidgetDataProvider.load(context, appWidgetId)",
            ),
            WidgetSurface(
                fileName = "NimbusMediumWidget.kt",
                widgetClass = "NimbusMediumWidget",
                receiverClass = "NimbusMediumWidgetReceiver",
                loaderCall = "WidgetDataProvider.load(context, appWidgetId)",
            ),
            WidgetSurface(
                fileName = "NimbusLargeWidget.kt",
                widgetClass = "NimbusLargeWidget",
                receiverClass = "NimbusLargeWidgetReceiver",
                loaderCall = "WidgetDataProvider.load(context, appWidgetId)",
            ),
            WidgetSurface(
                fileName = "NimbusForecastStripWidget.kt",
                widgetClass = "NimbusForecastStripWidget",
                receiverClass = "NimbusForecastStripWidgetReceiver",
                loaderCall = "WidgetDataProvider.load(context, appWidgetId)",
            ),
            WidgetSurface(
                fileName = "NimbusSavedCitiesWidget.kt",
                widgetClass = "NimbusSavedCitiesWidget",
                receiverClass = "NimbusSavedCitiesWidgetReceiver",
                loaderCall = "WidgetDataProvider.loadSavedCities(context)",
            ),
            WidgetSurface(
                fileName = "NimbusTempWidget.kt",
                widgetClass = "NimbusTempWidget",
                receiverClass = "NimbusTempWidgetReceiver",
                loaderCall = "WidgetDataProvider.load(context, appWidgetId)",
            ),
            WidgetSurface(
                fileName = "NimbusCompactWidget.kt",
                widgetClass = "NimbusCompactWidget",
                receiverClass = "NimbusCompactWidgetReceiver",
                loaderCall = "WidgetDataProvider.load(context, appWidgetId)",
            ),
            WidgetSurface(
                fileName = "NimbusDailyWidget.kt",
                widgetClass = "NimbusDailyWidget",
                receiverClass = "NimbusDailyWidgetReceiver",
                loaderCall = "WidgetDataProvider.load(context, appWidgetId)",
            ),
        )
    }
}
