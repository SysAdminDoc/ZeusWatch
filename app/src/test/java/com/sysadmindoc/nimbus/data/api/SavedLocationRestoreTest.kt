package com.sysadmindoc.nimbus.data.api

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.nimbus.data.model.SavedLocationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Undo after deleting a saved location goes through the DAO, and the first
 * version of it called `restoreAll` — a whole-table replace written for
 * settings import. Restoring one row that way deleted every other saved
 * place, including the GPS current-location row that background workers read.
 * These pin the difference between the two.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SavedLocationRestoreTest {

    private lateinit var database: NimbusDatabase
    private lateinit var dao: SavedLocationDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NimbusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.savedLocationDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun restore_putsOneRowBackAndLeavesTheRestAlone() = runTest {
        seed()
        val removed = dao.getAll().first { it.name == "New York" }
        dao.deleteById(removed.id)
        assertEquals(3, dao.getAll().size)

        dao.restore(removed)

        val names = dao.getAll().map { it.name }.toSet()
        assertEquals(setOf("My Location", "Denver", "New York", "Tokyo"), names)
    }

    @Test
    fun restore_keepsTheIdSortOrderAndSourceOverrides() = runTest {
        seed()
        val removed = dao.getAll().first { it.name == "New York" }
        dao.deleteById(removed.id)

        dao.restore(removed)

        // autoGenerate does not reassign a non-zero id, so the row comes back
        // in its original position with its per-location sources intact.
        assertEquals(removed, dao.getAll().first { it.name == "New York" })
    }

    @Test
    fun restoreAll_isAReplaceAndMustNotBeUsedForUndo() = runTest {
        seed()
        val removed = dao.getAll().first { it.name == "New York" }
        dao.deleteById(removed.id)

        dao.restoreAll(listOf(removed))

        // This is the data loss the undo path used to cause: everything else,
        // including the GPS current-location row, is gone.
        assertEquals(listOf("New York"), dao.getAll().map { it.name })
    }

    @Test
    fun theCurrentLocationRowCannotBeDeleted() = runTest {
        // Enforced in the repository, not just the screen: deletion is
        // immediate now, and every background surface falls back to this row.
        val repository = com.sysadmindoc.nimbus.data.repository.LocationRepository(
            geocodingApi = io.mockk.mockk(relaxed = true),
            dao = dao,
        )
        seed()
        val current = dao.getAll().first { it.isCurrentLocation }

        val removed = repository.removeLocation(current.id)

        assertEquals(null, removed)
        assertEquals(4, dao.getAll().size)
    }

    private suspend fun seed() {
        listOf(
            SavedLocationEntity(
                name = "My Location",
                latitude = 39.7,
                longitude = -105.0,
                sortOrder = -1,
                isCurrentLocation = true,
            ),
            SavedLocationEntity(name = "Denver", latitude = 39.74, longitude = -104.98, sortOrder = 0),
            SavedLocationEntity(
                name = "New York",
                latitude = 40.71,
                longitude = -74.01,
                sortOrder = 1,
                forecastSource = "MET_NORWAY",
                alertSource = "NWS",
            ),
            SavedLocationEntity(name = "Tokyo", latitude = 35.68, longitude = 139.69, sortOrder = 2),
        ).forEach { dao.insert(it) }
    }
}
