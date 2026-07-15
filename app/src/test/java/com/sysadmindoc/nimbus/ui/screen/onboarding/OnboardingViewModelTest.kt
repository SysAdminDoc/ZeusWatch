package com.sysadmindoc.nimbus.ui.screen.onboarding

import androidx.datastore.preferences.core.emptyPreferences
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import com.sysadmindoc.nimbus.data.repository.StarterCardSet
import com.sysadmindoc.nimbus.data.repository.TempUnit
import com.sysadmindoc.nimbus.data.repository.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private lateinit var prefs: UserPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        prefs = mockk()
        every { prefs.settings } returns flowOf(NimbusSettings())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful completion saves once and coalesces duplicate taps`() {
        coEvery {
            prefs.completeOnboarding(TempUnit.CELSIUS, StarterCardSet.MINIMAL)
        } returns emptyPreferences()
        val viewModel = OnboardingViewModel(prefs)
        var completionCount = 0

        viewModel.complete(TempUnit.CELSIUS, StarterCardSet.MINIMAL) { completionCount += 1 }
        viewModel.complete(TempUnit.CELSIUS, StarterCardSet.MINIMAL) { completionCount += 1 }

        assertTrue(viewModel.saveState.value.isSaving)
        scheduler.advanceUntilIdle()

        assertFalse(viewModel.saveState.value.isSaving)
        assertFalse(viewModel.saveState.value.saveFailed)
        assertEquals(1, completionCount)
        coVerify(exactly = 1) {
            prefs.completeOnboarding(TempUnit.CELSIUS, StarterCardSet.MINIMAL)
        }
    }

    @Test
    fun `failed completion clears saving and retry preserves selected values`() {
        coEvery {
            prefs.completeOnboarding(TempUnit.FAHRENHEIT, StarterCardSet.EVERYTHING)
        } throws IllegalStateException("disk unavailable")
        val viewModel = OnboardingViewModel(prefs)
        var completionCount = 0

        viewModel.complete(TempUnit.FAHRENHEIT, StarterCardSet.EVERYTHING) { completionCount += 1 }
        scheduler.advanceUntilIdle()

        assertFalse(viewModel.saveState.value.isSaving)
        assertTrue(viewModel.saveState.value.saveFailed)
        assertEquals(0, completionCount)

        coEvery {
            prefs.completeOnboarding(TempUnit.FAHRENHEIT, StarterCardSet.EVERYTHING)
        } returns emptyPreferences()
        viewModel.complete(TempUnit.FAHRENHEIT, StarterCardSet.EVERYTHING) { completionCount += 1 }
        scheduler.advanceUntilIdle()

        assertFalse(viewModel.saveState.value.isSaving)
        assertFalse(viewModel.saveState.value.saveFailed)
        assertEquals(1, completionCount)
        coVerify(exactly = 2) {
            prefs.completeOnboarding(TempUnit.FAHRENHEIT, StarterCardSet.EVERYTHING)
        }
    }

    @Test
    fun `cancellation clears saving without presenting a retry error`() {
        coEvery {
            prefs.completeOnboarding(TempUnit.CELSIUS, StarterCardSet.STANDARD)
        } throws CancellationException("screen closed")
        val viewModel = OnboardingViewModel(prefs)

        viewModel.complete(TempUnit.CELSIUS, StarterCardSet.STANDARD) {
            throw AssertionError("completion callback must not run")
        }
        scheduler.advanceUntilIdle()

        assertFalse(viewModel.saveState.value.isSaving)
        assertFalse(viewModel.saveState.value.saveFailed)
    }
}
