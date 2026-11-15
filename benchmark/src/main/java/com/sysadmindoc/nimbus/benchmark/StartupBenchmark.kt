package com.sysadmindoc.nimbus.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private var firstRunDone = false

    @Test
    fun startupBaselineProfile() = startup(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
        )
    )

    private fun startup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = {
                // Measure the launch returning users get (the weather screen),
                // not the one-time onboarding a fresh install opens on.
                if (!firstRunDone) {
                    grantLocation()
                    pressHome()
                    startActivityAndWait()
                    finishFirstRunIfShown()
                    awaitForecast()
                    killProcess()
                    firstRunDone = true
                }
                pressHome()
            },
        ) {
            startActivityAndWait()
        }
    }
}
