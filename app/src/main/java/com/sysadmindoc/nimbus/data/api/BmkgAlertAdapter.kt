package com.sysadmindoc.nimbus.data.api

import com.sysadmindoc.nimbus.data.model.AlertCoordinate
import com.sysadmindoc.nimbus.data.model.AlertGeometry
import com.sysadmindoc.nimbus.data.model.AlertPolygon
import com.sysadmindoc.nimbus.data.model.AlertSeverity
import com.sysadmindoc.nimbus.data.model.AlertUrgency
import com.sysadmindoc.nimbus.data.model.WeatherAlert
import com.sysadmindoc.nimbus.data.util.SourceLocaleText
import kotlinx.coroutines.CancellationException
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [AlertSourceAdapter] for BMKG public nowcast CAP warnings.
 */
@Singleton
class BmkgAlertAdapter @Inject constructor(
    private val api: BmkgAlertApi,
) : AlertSourceAdapter {

    override val sourceId = "bmkg"
    override val displayName = "BMKG"
    override val supportedRegions = setOf("ID")

    override suspend fun getAlerts(lat: Double, lon: Double): Result<List<WeatherAlert>> {
        if (!isIndonesiaCoordinate(lat, lon)) return Result.success(emptyList())

        return try {
            val feedXml = api.getNowcastFeed().use { it.string() }
            val detailUrls = parseFeedDetailUrls(feedXml)
                .take(MAX_DETAIL_FETCHES)
            var failureCount = 0
            var lastFailure: Throwable? = null
            val alerts = detailUrls.mapNotNull { url ->
                runCatching {
                    api.getAlertDetail(url).use { body ->
                        parseCapAlert(body.string(), sourceUrl = url, latitude = lat, longitude = lon)
                    }
                }.onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    failureCount += 1
                    lastFailure = failure
                }.getOrNull()
            }.filter { alert ->
                alert.coversRequestedLocation != false
            }

            if (detailUrls.isNotEmpty() && alerts.isEmpty() && failureCount == detailUrls.size) {
                Result.failure(lastFailure ?: IllegalStateException("BMKG alert detail fetch failed"))
            } else {
                Result.success(alerts)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFeedDetailUrls(xml: String): List<String> {
        val document = parseXml(xml)
        return document.getElementsByTagName("item")
            .asElements()
            .mapNotNull { item -> item.childText("link") }
            .map { link -> URI(BmkgAlertApi.BASE_URL).resolve(link).toString() }
            .distinct()
    }

    private fun parseCapAlert(
        xml: String,
        sourceUrl: String,
        latitude: Double,
        longitude: Double,
    ): WeatherAlert? {
        val root = parseXml(xml).documentElement
        val infoBlocks = root.childElements("info")
        val info = SourceLocaleText.filterByLocale(infoBlocks, languageTag = { it.childText("language") })
            .firstOrNull()
            ?: return null
        val event = info.childText("event") ?: return null
        val geometry = info.toAlertGeometry()
        val coversLocation = geometry?.contains(latitude, longitude)
        val identifier = root.childText("identifier")

        return WeatherAlert(
            id = identifier ?: sourceUrl,
            event = event,
            headline = info.childText("headline") ?: event,
            description = info.childText("description") ?: "",
            instruction = null,
            severity = AlertSeverity.from(info.childText("severity")),
            urgency = AlertUrgency.from(info.childText("urgency")),
            certainty = info.childText("certainty") ?: "Unknown",
            senderName = info.childText("senderName") ?: "BMKG",
            areaDescription = info.areaDescriptions().ifEmpty { "Indonesia" },
            effective = info.childText("effective") ?: root.childText("sent"),
            expires = info.childText("expires"),
            response = null,
            geometry = geometry,
            coversRequestedLocation = coversLocation,
        )
    }

    private fun Element.toAlertGeometry(): AlertGeometry? {
        val polygons = childElements("area")
            .flatMap { area -> area.childElements("polygon") }
            .mapNotNull { polygon -> parseCapPolygon(polygon.textContent.orEmpty()) }
        return polygons.takeIf { it.isNotEmpty() }?.let(::AlertGeometry)
    }

    private fun Element.areaDescriptions(): String =
        childElements("area")
            .mapNotNull { it.childText("areaDesc") }
            .distinct()
            .joinToString()

    private fun parseCapPolygon(value: String): AlertPolygon? {
        val points = value.trim()
            .split(Regex("\\s+"))
            .mapNotNull { pair ->
                val latitude = pair.substringBefore(',', missingDelimiterValue = "").toDoubleOrNull()
                val longitude = pair.substringAfter(',', missingDelimiterValue = "").toDoubleOrNull()
                if (latitude == null || longitude == null) null else AlertCoordinate(latitude, longitude)
            }
        return AlertPolygon(points).takeIf { points.size >= 3 }
    }

    private fun parseXml(xml: String) = DocumentBuilderFactory.newInstance()
        .apply {
            isNamespaceAware = true
            configureSecureXml()
        }
        .newDocumentBuilder()
        .parse(InputSource(StringReader(xml)))

    private fun DocumentBuilderFactory.configureSecureXml() {
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setAttribute(ACCESS_EXTERNAL_DTD, "") }
        runCatching { setAttribute(ACCESS_EXTERNAL_SCHEMA, "") }
    }

    private fun Element.childText(localName: String): String? =
        childElements(localName).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }

    private fun Element.childElements(localName: String): List<Element> =
        (getElementsByTagNameNS("*", localName).asElements() + getElementsByTagName(localName).asElements())
            .distinct()
            .filter { it.parentNode == this }

    private fun NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { index -> item(index) as? Element }

    companion object {
        private const val MAX_DETAIL_FETCHES = 40
        private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        private const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
    }
}

private fun isIndonesiaCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude in -11.2..6.2 && longitude in 94.5..141.1
