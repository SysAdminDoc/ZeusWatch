package com.sysadmindoc.nimbus.data.repository

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NimbusSettingsUsageContractTest {
    @Test
    fun `every persisted setting has a production reader beyond settings plumbing`() {
        val sourceRoot = repositoryRoot().resolve("app/src/main")
        val userPreferences = sourceRoot
            .resolve("java/com/sysadmindoc/nimbus/data/repository/UserPreferences.kt")
            .readText()
        val constructor = requireNotNull(
            Regex("data class NimbusSettings\\((?<fields>[\\s\\S]*?)\\n\\) \\{")
                .find(userPreferences),
        )
        val fields = Regex("(?m)^\\s*val\\s+(\\w+)\\s*:")
            .findAll(constructor.groups["fields"]!!.value)
            .map { it.groupValues[1] }
            .toList()
        val retiredCardToggles = setOf(
            "showSnowfall",
            "showCape",
            "showSunshineDuration",
            "showGoldenHour",
            "showOutdoorScore",
        )

        val productionReaders = buildString {
            append(userPreferences.substring(constructor.range.last + 1))
            Files.walk(sourceRoot).use { paths ->
                paths.filter { path ->
                    path.extension == "kt" && path.invariantSeparatorsPathString.let { normalized ->
                        !normalized.endsWith("/UserPreferences.kt") &&
                            !normalized.endsWith("/SettingsTransfer.kt") &&
                            !normalized.contains("/ui/screen/settings/")
                    }
                }.forEach { append('\n').append(it.readText()) }
            }
        }

        retiredCardToggles.forEach { retired -> assertFalse(retired in fields) }
        fields.forEach { field ->
            assertTrue(
                "NimbusSettings.$field has no production reader outside settings plumbing",
                Regex("\\b${Regex.escape(field)}\\b").containsMatchIn(productionReaders),
            )
        }
    }

    private fun repositoryRoot(): Path {
        var candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (!Files.isDirectory(candidate.resolve("app/src/main"))) {
            candidate = candidate.parent
                ?: error("Could not locate repository root from ${System.getProperty("user.dir")}")
        }
        return candidate
    }
}
