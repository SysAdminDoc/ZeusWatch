package com.sysadmindoc.nimbus.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun parseAlertInstant(value: String?, zone: ZoneId = ZoneId.systemDefault()): Instant? {
    val trimmed = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    runCatching { return OffsetDateTime.parse(trimmed).toInstant() }
    runCatching { return Instant.parse(trimmed) }

    val normalized = trimmed.replace(' ', 'T')
    return runCatching {
        LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(zone)
            .toInstant()
    }.getOrNull()
}

fun isAlertExpired(expires: String?, now: Instant = Instant.now()): Boolean {
    val expiresAt = parseAlertInstant(expires) ?: return false
    return expiresAt.isBefore(now)
}
