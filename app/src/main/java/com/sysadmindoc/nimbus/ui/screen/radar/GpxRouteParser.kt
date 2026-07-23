package com.sysadmindoc.nimbus.ui.screen.radar

import com.sysadmindoc.nimbus.data.repository.DrivingRouteEstimateKind
import com.sysadmindoc.nimbus.data.repository.DrivingRouteGeometry
import com.sysadmindoc.nimbus.data.repository.DrivingRoutePoint
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

internal const val MAX_GPX_BYTES = 5 * 1024 * 1024
internal const val MAX_GPX_POINTS = 10_000

internal class GpxRouteParseException(
    val reason: GpxRouteParseFailure,
    cause: Throwable? = null,
) : IllegalArgumentException(reason.name, cause)

enum class GpxRouteParseFailure {
    FILE_TOO_LARGE,
    TOO_MANY_POINTS,
    UNSAFE_XML,
    INVALID_GPX,
}

/** Parses bounded GPX 1.1 route or track geometry without resolving entities. */
internal class GpxRouteParser {
    fun parse(input: InputStream): DrivingRouteGeometry {
        val bytes = input.readBoundedBytes()
        // Decode using the BOM-declared charset so a UTF-16 document (which the
        // XML spec requires to carry a BOM) can't smuggle a DOCTYPE/ENTITY past
        // this marker scan as UTF-8 mojibake — this scan is the load-bearing XXE
        // guard on Android, where the Xerces disallow-doctype-decl flag is unsupported.
        val xmlPrefix = bytes.decodeByBom().uppercase()
        if ("<!DOCTYPE" in xmlPrefix || "<!ENTITY" in xmlPrefix) {
            throw GpxRouteParseException(GpxRouteParseFailure.UNSAFE_XML)
        }

        val document = try {
            secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(InputSource(ByteArrayInputStream(bytes)))
        } catch (error: GpxRouteParseException) {
            throw error
        } catch (error: Exception) {
            throw GpxRouteParseException(GpxRouteParseFailure.INVALID_GPX, error)
        }
        val root = document.documentElement
            ?: throw GpxRouteParseException(GpxRouteParseFailure.INVALID_GPX)
        if (root.localNameOrNodeName() != "gpx" || root.getAttribute("version") != "1.1") {
            throw GpxRouteParseException(GpxRouteParseFailure.INVALID_GPX)
        }
        val namespace = root.namespaceURI.orEmpty()
        if (namespace.isNotBlank() && namespace != GPX_1_1_NAMESPACE) {
            throw GpxRouteParseException(GpxRouteParseFailure.INVALID_GPX)
        }

        val routeNodes = root.elementsByLocalName("rtept")
        val (nodes, estimateKind) = if (routeNodes.isNotEmpty()) {
            routeNodes to DrivingRouteEstimateKind.GPX_ROUTE
        } else {
            root.elementsByLocalName("trkpt") to DrivingRouteEstimateKind.GPX_TRACK
        }
        if (nodes.size > MAX_GPX_POINTS) {
            throw GpxRouteParseException(GpxRouteParseFailure.TOO_MANY_POINTS)
        }
        val points = nodes.map { element ->
            val latitude = element.getAttribute("lat").toDoubleOrNull()
            val longitude = element.getAttribute("lon").toDoubleOrNull()
            try {
                DrivingRoutePoint(
                    latitude = latitude ?: throw IllegalArgumentException(),
                    longitude = longitude ?: throw IllegalArgumentException(),
                )
            } catch (error: IllegalArgumentException) {
                throw GpxRouteParseException(GpxRouteParseFailure.INVALID_GPX, error)
            }
        }
        if (points.size < 2) {
            throw GpxRouteParseException(GpxRouteParseFailure.INVALID_GPX)
        }
        return DrivingRouteGeometry(points = points, estimateKind = estimateKind)
    }

    private fun InputStream.readBoundedBytes(): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = java.io.ByteArrayOutputStream()
        var totalBytes = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (count == 0) continue
            totalBytes += count
            if (totalBytes > MAX_GPX_BYTES) {
                throw GpxRouteParseException(GpxRouteParseFailure.FILE_TOO_LARGE)
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    /**
     * Decodes bytes for the DOCTYPE/ENTITY marker scan using the leading byte-order
     * mark to pick the charset (the XML spec requires a UTF-16 document to carry a
     * BOM). Absent a BOM, XML defaults to UTF-8. Prevents a UTF-16 payload from
     * hiding the markers as UTF-8 mojibake.
     */
    private fun ByteArray.decodeByBom(): String {
        val charset = when {
            size >= 2 && this[0] == 0xFE.toByte() && this[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            else -> Charsets.UTF_8
        }
        return toString(charset)
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            // Best-effort hardening only: Android's platform DocumentBuilderFactory
            // recognises just the namespace/validation features and throws
            // ParserConfigurationException for these Xerces flags, which would
            // otherwise fail every on-device import as INVALID_GPX. The DOCTYPE/
            // ENTITY prefix scan above plus isExpandEntityReferences=false keep
            // the XXE defence when a flag is unsupported.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute(ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(ACCESS_EXTERNAL_SCHEMA, "") }
        }

    private fun Element.elementsByLocalName(name: String): List<Element> {
        val namespaceMatches = getElementsByTagNameNS("*", name).asElements()
        return if (namespaceMatches.isNotEmpty()) {
            namespaceMatches
        } else {
            getElementsByTagName(name).asElements()
        }
    }

    private fun Element.localNameOrNodeName(): String = localName ?: nodeName.substringAfter(':')

    private fun NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { index -> item(index) as? Element }

    private companion object {
        const val GPX_1_1_NAMESPACE = "http://www.topografix.com/GPX/1/1"
        const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
    }
}
