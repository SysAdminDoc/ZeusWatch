package com.sysadmindoc.nimbus.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until

private const val UI_TIMEOUT_MS = 10_000L
private const val WEATHER_TIMEOUT_MS = 30_000L
private const val POLL_MS = 250L
private const val ONBOARDING_STEPS = 3

// English labels: the gate runs on a phone set to English.
private const val CONTINUE = "Continue"
private const val FINISH = "Start ZeusWatch"
private const val TODAY_TAB = "Today"
private const val WHATS_NEW_DONE = "Got it"
private const val FEELS_LIKE = "Feels like"

// The bottom bar merges each tab's label into its description, so the tab
// has no text node to match.
private fun todayTab(): BySelector = By.desc(TODAY_TAB)

/**
 * Grants location the way a returning user already has. Launch never asks for
 * it (NX-26), so without the grant the weather screen stops at the location
 * card and no forecast code runs.
 */
fun MacrobenchmarkScope.grantLocation() {
    listOf("ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION").forEach { permission ->
        device.executeShellCommand("pm grant $packageName android.permission.$permission")
    }
}

/**
 * Walks a fresh install through onboarding and closes the What's New sheet that
 * follows it, so every later launch opens on the weather screen. Does nothing
 * when the app already opens there.
 */
fun MacrobenchmarkScope.finishFirstRunIfShown() {
    val landed = awaitAny(By.text(CONTINUE), todayTab())
    check(landed != null) { "Neither onboarding nor the weather screen showed after launch" }
    if (landed == 0) {
        for (step in 1 until ONBOARDING_STEPS) {
            clickText(CONTINUE)
            awaitText("${step + 1} of $ONBOARDING_STEPS")
        }
        clickText(FINISH)
        // The sheet is its own window and UiAutomator only reads the top one,
        // so the tab bar under it stays hidden until it closes.
        check(awaitAny(todayTab(), By.text(WHATS_NEW_DONE)) != null) {
            "Neither the weather screen nor What's New showed after onboarding"
        }
    }
    if (device.wait(Until.hasObject(By.text(WHATS_NEW_DONE)), UI_TIMEOUT_MS) == true) {
        clickText(WHATS_NEW_DONE)
        check(device.wait(Until.gone(By.text(WHATS_NEW_DONE)), UI_TIMEOUT_MS) == true) {
            "What's New sheet did not close"
        }
    }
    check(device.wait(Until.hasObject(todayTab()), UI_TIMEOUT_MS) == true) {
        "The $TODAY_TAB tab did not show within ${UI_TIMEOUT_MS / 1000} s"
    }
}

/** Waits for the current conditions header, which only renders once a forecast loaded. */
fun MacrobenchmarkScope.awaitForecast() {
    check(device.wait(Until.hasObject(By.textStartsWith(FEELS_LIKE)), WEATHER_TIMEOUT_MS) == true) {
        "No forecast on the weather screen after ${WEATHER_TIMEOUT_MS / 1000} s (location or network)"
    }
}

/** Index of the first of [selectors] on screen within the timeout, or null if none showed. */
private fun MacrobenchmarkScope.awaitAny(vararg selectors: BySelector): Int? {
    val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS
    while (true) {
        val shown = selectors.indexOfFirst { device.hasObject(it) }
        if (shown >= 0) return shown
        if (SystemClock.uptimeMillis() >= deadline) return null
        SystemClock.sleep(POLL_MS)
    }
}

private fun MacrobenchmarkScope.awaitText(text: String) {
    check(device.wait(Until.hasObject(By.text(text)), UI_TIMEOUT_MS) == true) {
        "\"$text\" did not show within ${UI_TIMEOUT_MS / 1000} s"
    }
}

private fun MacrobenchmarkScope.clickText(text: String) {
    val node = device.wait(Until.findObject(By.text(text).enabled(true)), UI_TIMEOUT_MS)
        ?: error("No enabled \"$text\" within ${UI_TIMEOUT_MS / 1000} s")
    node.click()
}
