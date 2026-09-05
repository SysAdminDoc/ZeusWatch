// ZeusWatch v1.29.3
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
    // Derived from the tree rather than hand-listed: the hand-listed version
    // named 7 of the 13 source sets, so everything in standardDebug,
    // standardRelease, freenetRelease, standardBenchmark,
    // standardNonMinifiedRelease and testStandard was never linted at all.
    source.setFrom(
        files(
            listOf("app", "wear", "benchmark").flatMap { module ->
                (file("$rootDir/$module/src").listFiles() ?: emptyArray()).flatMap { sourceSet ->
                    listOf("java", "kotlin")
                        .map { sourceSet.resolve(it) }
                        .filter { it.isDirectory }
                }
            },
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
val appProject = project(":app")
val wearProject = project(":wear")

val verifyAppFreenetPurity = appProject.tasks.register("verifyFreenetPurityForRoot") {
    group = "verification"
    description = "Fails when a proprietary artifact reaches any freenet configuration."
    val banned = listOf("com.google.firebase", "com.google.android.gms", "com.google.mlkit")
    val configurationNames = listOf(
        "freenetDebugRuntimeClasspath",
        "freenetReleaseRuntimeClasspath",
    )
    dependsOn(tasks.matching { it.name == "preBuild" })
    doLast {
        val offenders = configurationNames.flatMap { name ->
            val configuration = project.configurations.findByName(name)
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

val freenetPurityGate = tasks.register("freenetPurityGate") {
    group = "verification"
    description = "Runs the freenet dependency check in the app module that owns those configurations."
    dependsOn(verifyAppFreenetPurity)
}

/**
 * Dumps the resolved runtime classpath of each shipped variant.
 *
 * The notices generator used to read the version catalog, which is neither
 * complete nor accurate: dependencies declared as string literals (all of
 * Firebase, all of androidx.wear) never appear there, BOM-managed artifacts
 * carry no version, and test-only entries do appear. Only the resolved
 * runtime classpath knows what actually ships.
 */
fun org.gradle.api.artifacts.Configuration.resolvedModuleCoordinates(): List<String> =
    incoming.resolutionResult.allDependencies
        .mapNotNull { (it as? org.gradle.api.artifacts.result.ResolvedDependencyResult)?.selected?.moduleVersion }
        .filter { it.group.isNotBlank() }
        .map { "${it.group}:${it.name}:${it.version}" }
        .distinct()
        .sorted()

val appRuntimeDependenciesOutput = appProject.layout.buildDirectory.file("reports/runtime-dependencies.tsv")
val exportAppRuntimeDependencies = appProject.tasks.register("exportRuntimeDependenciesForRoot") {
    group = "verification"
    description = "Resolves shipped phone dependencies inside the app project."
    outputs.file(appRuntimeDependenciesOutput)
    outputs.upToDateWhen { false }
    val variants = linkedMapOf(
        "standard" to "standardReleaseRuntimeClasspath",
        "freenet" to "freenetReleaseRuntimeClasspath",
    )
    doLast {
        val lines = variants.flatMap { (variant, configurationName) ->
            val configuration = project.configurations.findByName(configurationName)
                ?: error("Configuration $configurationName not found in ${project.path}")
            configuration.resolvedModuleCoordinates().map { "$variant\t$it" }
        }
        val file = appRuntimeDependenciesOutput.get().asFile
        file.parentFile.mkdirs()
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }
}

val wearRuntimeDependenciesOutput = wearProject.layout.buildDirectory.file("reports/runtime-dependencies.tsv")
val exportWearRuntimeDependencies = wearProject.tasks.register("exportRuntimeDependenciesForRoot") {
    group = "verification"
    description = "Resolves shipped watch dependencies inside the wear project."
    outputs.file(wearRuntimeDependenciesOutput)
    outputs.upToDateWhen { false }
    doLast {
        val configurationName = "releaseRuntimeClasspath"
        val configuration = project.configurations.findByName(configurationName)
            ?: error("Configuration $configurationName not found in ${project.path}")
        val lines = configuration.resolvedModuleCoordinates().map { "wear\t$it" }
        val file = wearRuntimeDependenciesOutput.get().asFile
        file.parentFile.mkdirs()
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }
}

val exportRuntimeDependencies = tasks.register("exportRuntimeDependencies") {
    group = "verification"
    description = "Writes the resolved runtime classpath of every shipped variant to JSON."
    val output = layout.buildDirectory.file("reports/runtime-dependencies.json")
    dependsOn(exportAppRuntimeDependencies, exportWearRuntimeDependencies)
    inputs.files(appRuntimeDependenciesOutput, wearRuntimeDependenciesOutput)
    outputs.file(output)
    outputs.upToDateWhen { false }
    doLast {
        val entries = linkedMapOf(
            "standard" to mutableListOf<String>(),
            "freenet" to mutableListOf(),
            "wear" to mutableListOf(),
        )
        listOf(appRuntimeDependenciesOutput.get().asFile, wearRuntimeDependenciesOutput.get().asFile)
            .flatMap { it.readLines() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val (variant, coordinate) = line.split('\t', limit = 2)
                entries.getValue(variant).add(coordinate)
            }
        entries.values.forEach { modules ->
            val sorted = modules.distinct().sorted()
            modules.clear()
            modules.addAll(sorted)
        }
        val builder = StringBuilder("{").appendLine()
        entries.entries.forEachIndexed { variantIndex, (variant, modules) ->
            builder.append("  ").append('"').append(variant).append('"').append(": [").appendLine()
            modules.forEachIndexed { index, module ->
                builder.append("    ").append('"').append(module).append('"')
                if (index != modules.lastIndex) builder.append(",")
                builder.appendLine()
            }
            builder.append("  ]")
            if (variantIndex != entries.size - 1) builder.append(",")
            builder.appendLine()
        }
        builder.append("}").appendLine()
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(builder.toString())
        logger.lifecycle("Wrote " + file.relativeTo(rootDir) + ": " + entries.entries.joinToString { it.key + "=" + it.value.size })
    }
}

val ossNoticesGate = tasks.register<Exec>("ossNoticesGate") {
    group = "verification"
    description = "Fails when the packaged open-source notices no longer match the resolved runtime classpath."
    inputs.file("$rootDir/tools/generate_oss_notices.py")
    inputs.file("$rootDir/config/oss-licenses.json")
    inputs.file("$rootDir/app/src/main/assets/oss_notices.json")
    dependsOn(exportRuntimeDependencies)
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

/**
 * The repository's Python tooling had four test files and nothing that ran
 * them. One of them was red: BMKG is a user-selectable alert source with no
 * contract coverage, and the test that says so had been failing unnoticed.
 */
val toolTests = tasks.register<Exec>("toolTests") {
    group = "verification"
    description = "Runs the unit tests for the Python tooling under tools/."
    inputs.dir("$rootDir/tools")
    outputs.upToDateWhen { false }
    val launcher = if (System.getProperty("os.name").startsWith("Windows")) {
        listOf("py", "-3.13")
    } else {
        listOf("python3")
    }
    // Discovery needs the tools directory on sys.path: the test modules import
    // the scripts by name, and the directory is not an importable package.
    commandLine(launcher + listOf("-m", "unittest", "discover", "-s", ".", "-p", "*_test.py"))
    workingDir = file("$rootDir/tools")
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
        toolTests,
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
