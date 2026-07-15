package com.sysadmindoc.nimbus.smartspacer

import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sysadmindoc.nimbus.data.repository.TempUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verifies the smartspacer process reads the main process's temperature unit
 * from the preferences proto file WITHOUT opening a DataStore instance —
 * a second `preferencesDataStore("nimbus_prefs")` delegate in one process
 * throws IllegalStateException — and that each query sees the latest value
 * rather than caching the first read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SmartspacerTempUnitFileReadTest {

    private val tempUnitKey = stringPreferencesKey("temp_unit")

    @Test
    fun `defaults to fahrenheit when no preferences file exists`() = runTest {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(TempUnit.FAHRENHEIT, readTempUnitFromPreferencesFile(context))
    }

    @Test
    fun `reads the stored unit and sees later writes instead of caching the first read`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        writePrefsFile(prefsFile(), TempUnit.CELSIUS)

        assertEquals(TempUnit.CELSIUS, readTempUnitFromPreferencesFile(context))

        writePrefsFile(prefsFile(), TempUnit.FAHRENHEIT)

        assertEquals(TempUnit.FAHRENHEIT, readTempUnitFromPreferencesFile(context))
    }

    @Test
    fun `defaults to fahrenheit on a corrupt preferences file`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        prefsFile().apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x00, 0x42, 0x13, 0x37))
        }

        assertEquals(TempUnit.FAHRENHEIT, readTempUnitFromPreferencesFile(context))
    }

    @Test
    fun `defaults to fahrenheit on an unknown stored value`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val file = prefsFile()
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            PreferencesFileSerializer.writeTo(preferencesOf(tempUnitKey to "KELVIN"), output)
        }

        assertEquals(TempUnit.FAHRENHEIT, readTempUnitFromPreferencesFile(context))
    }

    private fun prefsFile(): File =
        File(RuntimeEnvironment.getApplication().filesDir, "datastore/nimbus_prefs.preferences_pb")

    private suspend fun writePrefsFile(file: File, unit: TempUnit) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            PreferencesFileSerializer.writeTo(preferencesOf(tempUnitKey to unit.name), output)
        }
    }
}
