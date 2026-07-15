package com.sysadmindoc.nimbus.ui.screen.radar

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal data class SharedRouteFields(
    val origin: String? = null,
    val destination: String? = null,
    val unreadable: Boolean = false,
)

/**
 * Upper bound for externally supplied route text (ACTION_SEND shares and
 * zeuswatch://radar deep links). Uncapped text would ride the nav back stack's
 * saved state (TransactionTooLargeException on backgrounding) and feed the
 * share-parsing regexes on the main thread.
 */
internal const val MAX_SHARED_ROUTE_TEXT_CHARS = 2_000

/** Trim and cap external route text at the intent boundary; null when blank. */
internal fun capSharedRouteText(rawText: String?): String? =
    rawText?.take(MAX_SHARED_ROUTE_TEXT_CHARS)?.trim()?.takeIf { it.isNotBlank() }

internal fun parseSharedRouteText(rawText: String): SharedRouteFields {
    val text = rawText.trim()
    if (text.isBlank()) return SharedRouteFields(unreadable = true)

    parseQueryParameters(text)?.let { return it }
    parseMapsDirPath(text)?.let { return it }
    parseLabeledLines(text)?.let { return it }
    parseInlineRoute(text)?.let { return it }

    val looksLikeUrl = text.startsWith("http://", ignoreCase = true) ||
        text.startsWith("https://", ignoreCase = true)
    return if (looksLikeUrl) {
        SharedRouteFields(unreadable = true)
    } else {
        SharedRouteFields(destination = text)
    }
}

private fun parseQueryParameters(text: String): SharedRouteFields? {
    val query = text.substringAfter('?', missingDelimiterValue = "")
        .substringBefore('#')
        .takeIf { it.isNotBlank() }
        ?: return null
    val params = query.split('&')
        .mapNotNull { pair ->
            val key = pair.substringBefore('=', missingDelimiterValue = "").ifBlank { return@mapNotNull null }
            val value = pair.substringAfter('=', missingDelimiterValue = "").ifBlank { return@mapNotNull null }
            decodeUrlPart(key).lowercase() to decodeUrlPart(value)
        }
        .toMap()
    val origin = params["origin"] ?: params["saddr"] ?: params["from"]
    val destination = params["destination"] ?: params["daddr"] ?: params["to"]
    return when {
        !origin.isNullOrBlank() || !destination.isNullOrBlank() ->
            SharedRouteFields(origin = origin.cleanRouteToken(), destination = destination.cleanRouteToken())
        else -> null
    }
}

private fun parseMapsDirPath(text: String): SharedRouteFields? {
    val path = text.substringBefore('?')
        .substringBefore('#')
        .replace("%2F", "/", ignoreCase = true)
    val parts = path.split('/')
        .map { decodeUrlPart(it).trim() }
        .filter { it.isNotBlank() }
    val dirIndex = parts.indexOfFirst { it.equals("dir", ignoreCase = true) }
    if (dirIndex < 0) return null
    val routeParts = parts.drop(dirIndex + 1)
        .filterNot { it.startsWith("@") || it.startsWith("data=", ignoreCase = true) }
    if (routeParts.size < 2) return null
    return SharedRouteFields(
        origin = routeParts[0].cleanRouteToken(),
        destination = routeParts[1].cleanRouteToken(),
    )
}

private fun parseLabeledLines(text: String): SharedRouteFields? {
    val labeled = text.lineSequence()
        .mapNotNull { line ->
            val key = line.substringBefore(':', missingDelimiterValue = "").trim().lowercase()
            val value = line.substringAfter(':', missingDelimiterValue = "").trim()
            if (key.isBlank() || value.isBlank()) return@mapNotNull null
            key to value
        }
        .toMap()
    val origin = labeled["origin"] ?: labeled["from"] ?: labeled["start"]
    val destination = labeled["destination"] ?: labeled["to"] ?: labeled["end"]
    return if (!origin.isNullOrBlank() || !destination.isNullOrBlank()) {
        SharedRouteFields(origin = origin.cleanRouteToken(), destination = destination.cleanRouteToken())
    } else {
        null
    }
}

private fun parseInlineRoute(text: String): SharedRouteFields? {
    val fromTo = Regex(
        pattern = """(?i)\bfrom\s+(.+?)\s+\bto\s+(.+)$""",
        options = setOf(RegexOption.DOT_MATCHES_ALL),
    ).find(text)
    if (fromTo != null) {
        return SharedRouteFields(
            origin = fromTo.groupValues[1].cleanRouteToken(),
            destination = fromTo.groupValues[2].cleanRouteToken(),
        )
    }

    val arrow = Regex("""(.+?)\s*(?:->|\bto\b)\s*(.+)""").find(text)
    if (arrow != null) {
        return SharedRouteFields(
            origin = arrow.groupValues[1].cleanRouteToken(),
            destination = arrow.groupValues[2].cleanRouteToken(),
        )
    }
    return null
}

private fun String?.cleanRouteToken(): String? {
    return this
        ?.replace('+', ' ')
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun decodeUrlPart(value: String): String =
    runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
