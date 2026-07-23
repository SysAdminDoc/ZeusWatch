package com.sysadmindoc.nimbus.di

import com.sysadmindoc.nimbus.data.repository.WeatherDataType
import com.sysadmindoc.nimbus.data.repository.WeatherSourceProvider
import dagger.multibindings.IntoMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture guard for the `@IntoMap` provider registry in
 * [WeatherSourceAdapterModule]. Complements the compile-time
 * `dagger.mapMultibindingDuplicateDetectionFix=ENABLED` flag (KSP, both flavors):
 * the flag fails the build on a duplicate map key, and this test proves the
 * registry is complete for every implemented [WeatherSourceProvider] and that
 * each declared key is unique.
 */
class WeatherSourceAdapterRegistryTest {

    /** Provider keys declared by every `@Provides @IntoMap @WeatherSourceKey` binding. */
    private fun registeredKeys(): List<WeatherSourceProvider> =
        WeatherSourceAdapterModule::class.java.declaredMethods
            .filter { it.isAnnotationPresent(IntoMap::class.java) }
            .mapNotNull { it.getAnnotation(WeatherSourceKey::class.java)?.value }

    @Test
    fun `every binding key is unique`() {
        val keys = registeredKeys()
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(
            "Duplicate @WeatherSourceKey bindings detected: $duplicates. " +
                "Each provider must be bound exactly once in WeatherSourceAdapterModule.",
            duplicates.isEmpty(),
        )
        assertEquals("Registered key count should match the distinct set", keys.size, keys.toSet().size)
    }

    @Test
    fun `every implemented provider has a registry binding`() {
        val registered = registeredKeys().toSet()
        val implemented = WeatherSourceProvider.entries.filter { provider ->
            WeatherDataType.entries.any { provider.isSelectableFor(it) }
        }
        val missing = implemented.filterNot { it in registered }
        assertTrue(
            "Implemented providers with no @IntoMap binding: $missing. " +
                "Add a WeatherSourceAdapterModule binding or drop the provider from the enum.",
            missing.isEmpty(),
        )
    }

    @Test
    fun `registry has no binding for an unknown provider`() {
        // Every registered key must be a real enum entry (guards against a stale
        // binding left behind after a provider is removed from the enum).
        val validProviders = WeatherSourceProvider.entries.toSet()
        val unknown = registeredKeys().filterNot { it in validProviders }
        assertTrue("Registry binds unknown providers: $unknown", unknown.isEmpty())
    }

    @Test
    fun `forecast and alert providers are each fully backed by the registry`() {
        val registered = registeredKeys().toSet()
        for (type in listOf(WeatherDataType.FORECAST, WeatherDataType.ALERTS)) {
            val backing = WeatherSourceProvider.forType(type)
            val missing = backing.filterNot { it in registered }
            assertTrue(
                "Selectable $type providers missing a registry binding: $missing",
                missing.isEmpty(),
            )
            assertTrue("$type must have at least one registry-backed provider", backing.isNotEmpty())
        }
    }
}
