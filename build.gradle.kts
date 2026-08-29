// Nimbus Weather v0.1.0 - Phase 1
// Open-source Android weather app targeting TWC parity
plugins {
    alias(libs.plugins.android.application) apply false
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
    description = "Runs WCAG contrast tests and Compose accessibility checks on the JVM."
    // The Compose accessibility suite moved from androidTest to Robolectric:
    // the local on-device harness fails tree-wide with "No compose hierarchies
    // found", so the gate depended on a task that could never pass and was
    // effectively off. It now runs where it actually executes.
    dependsOn(":app:testStandardDebugUnitTest")
}

val docsGate = tasks.register<Exec>("docsGate") {
    group = "verification"
    description = "Fails when documentation, fastlane metadata, or version headers drift from the code."
    // Declared inputs so the task is not permanently UP-TO-DATE: the script
    // reads sources and metadata that Gradle otherwise knows nothing about.
    inputs.file("$rootDir/tools/check_docs_consistency.py")
    inputs.files(
        "$rootDir/README.md",
        "$rootDir/ROADMAP.md",
        "$rootDir/app/build.gradle.kts",
        "$rootDir/wear/build.gradle.kts",
        "$rootDir/gradle/libs.versions.toml",
        "$rootDir/app/src/main/AndroidManifest.xml",
    )
    inputs.dir("$rootDir/fastlane/metadata/android/en-US")
    inputs.dir("$rootDir/app/src/main/java/com/sysadmindoc/nimbus/data/repository")
    outputs.upToDateWhen { false }
    // py launcher on Windows, python3 elsewhere.
    val launcher = if (System.getProperty("os.name").startsWith("Windows")) {
        listOf("py", "-3.13")
    } else {
        listOf("python3")
    }
    commandLine(launcher + listOf("tools/check_docs_consistency.py"))
    workingDir = rootDir
}

/**
 * The freenet flavor exists to be free of proprietary dependencies, and a
 * single unflavored `debugImplementation` was enough to pull Firebase and
 * Play Services into freenetDebug. Resolving the classpath is the only way to
 * see that; a source-level check cannot.
 */
val freenetPurityGate = tasks.register("freenetPurityGate") {
    group = "verification"
    description = "Fails when a proprietary artifact reaches any freenet configuration."
    val banned = listOf("com.google.firebase", "com.google.android.gms", "com.google.mlkit")
    val configurationNames = listOf(
        "freenetDebugRuntimeClasspath",
        "freenetReleaseRuntimeClasspath",
    )
    val appProject = project(":app")
    dependsOn(appProject.tasks.matching { it.name == "preBuild" })
    doLast {
        val offenders = configurationNames.flatMap { name ->
            val configuration = appProject.configurations.findByName(name)
                ?: error("Configuration $name not found; the freenet flavor may have been renamed.")
            configuration.incoming.resolutionResult.allDependencies
                .map { it.requested.displayName }
                .filter { requested -> banned.any { requested.startsWith(it) } }
                .map { "$name -> $it" }
        }.distinct().sorted()
        if (offenders.isNotEmpty()) {
            error(
                "Proprietary artifacts reached the freenet flavor: " +
                    offenders.joinToString("; "),
            )
        }
    }
}

val ossNoticesGate = tasks.register<Exec>("ossNoticesGate") {
    group = "verification"
    description = "Fails when the packaged open-source notices no longer match the version catalog."
    inputs.file("$rootDir/tools/generate_oss_notices.py")
    inputs.file("$rootDir/config/oss-licenses.json")
    inputs.file("$rootDir/gradle/libs.versions.toml")
    inputs.file("$rootDir/app/src/main/assets/oss_notices.json")
    inputs.file("$rootDir/app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSource.kt")
    outputs.upToDateWhen { false }
    val launcher = if (System.getProperty("os.name").startsWith("Windows")) {
        listOf("py", "-3.13")
    } else {
        listOf("python3")
    }
    commandLine(launcher + listOf("tools/generate_oss_notices.py", "--check"))
    workingDir = rootDir
}

tasks.register("localQualityGate") {
    group = "verification"
    description = "Runs every JVM-verifiable check: docs, notices, detekt, phone + wear lint, phone + wear unit tests."
    // :wear:lintDebug is here because it was silently red for releases — no
    // aggregate task ran it, so a RestrictedApi error sat unnoticed.
    dependsOn(
        docsGate,
        ossNoticesGate,
        freenetPurityGate,
        ":detekt",
        ":app:lintStandardDebug",
        ":wear:lintDebug",
        ":app:testStandardDebugUnitTest",
        ":wear:testDebugUnitTest",
    )
}

tasks.register("startupGate") {
    group = "verification"
    description = "Runs the standard benchmark startup gate and fails when cold-start p95 exceeds the configured budget."
    dependsOn(":benchmark:checkStandardStartupP95")
}
