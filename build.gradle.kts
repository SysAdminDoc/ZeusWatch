// Nimbus Weather v0.1.0 - Phase 1
// Open-source Android weather app targeting TWC parity
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
    id("com.google.gms.google-services") version "4.4.2" apply false
}

detekt {
    toolVersion = libs.versions.detekt.get()
    // Config lives at the repo root so both modules share the same rules.
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    // Baseline captures the current backlog so we fail-fast on *new* issues
    // without requiring a one-shot cleanup sweep.
    baseline = file("$rootDir/config/detekt/baseline.xml")
    parallel = true
    // Every module that applies the plugin.
    source.setFrom(
        files(
            "$rootDir/app/src/main/java",
            "$rootDir/app/src/standard/java",
            "$rootDir/app/src/freenet/java",
            "$rootDir/app/src/test/java",
            "$rootDir/benchmark/src/main/java",
            "$rootDir/wear/src/main/java",
            "$rootDir/wear/src/test/java",
        ),
    )
    autoCorrect = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        md.required.set(false)
        txt.required.set(false)
    }
}

tasks.register("accessibilityGate") {
    group = "verification"
    description = "Runs WCAG contrast unit tests and Compose accessibility checks on Android."
    dependsOn(":app:testStandardDebugUnitTest", ":app:connectedStandardDebugAndroidTest")
}

tasks.register("startupGate") {
    group = "verification"
    description = "Runs the standard benchmark startup gate and fails when cold-start p95 exceeds the configured budget."
    dependsOn(":benchmark:checkStandardStartupP95")
}
