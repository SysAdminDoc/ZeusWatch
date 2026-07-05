import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.sysadmindoc.nimbus.benchmark"
    compileSdk = 36
    targetProjectPath = ":app"

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            matchingFallbacks += listOf("release")
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
        }
        create("freenet") {
            dimension = "distribution"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.junit.ext)
    implementation(libs.espresso.core)
    implementation(libs.uiautomator)
}

val STARTUP_P95_BUDGET_MS = 1200.0

tasks.register("checkStandardStartupP95") {
    group = "verification"
    description = "Runs standard benchmark startup measurements and fails when cold-start p95 exceeds ${STARTUP_P95_BUDGET_MS.toInt()}ms."
    dependsOn("connectedStandardBenchmarkBenchmarkAndroidTest")

    doLast {
        val outputDir = layout.buildDirectory.dir("outputs/connected_android_test_additional_output").get().asFile
        val resultFiles = outputDir
            .takeIf { it.exists() }
            ?.walkTopDown()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.toList()
            .orEmpty()

        val startupP95Values = resultFiles.flatMap { file ->
            extractStartupP95Values(file.readText())
        }

        if (startupP95Values.isEmpty()) {
            throw GradleException(
                "No startup p95 values found under ${outputDir.path}. Run connectedStandardBenchmarkBenchmarkAndroidTest on a connected device first."
            )
        }

        val worstP95 = startupP95Values.maxOrNull() ?: error("missing startup p95")
        if (worstP95 > STARTUP_P95_BUDGET_MS) {
            throw GradleException(
                "Cold-start p95 ${"%.1f".format(worstP95)}ms exceeds ${STARTUP_P95_BUDGET_MS.toInt()}ms budget."
            )
        }
        logger.lifecycle("Cold-start p95 ${"%.1f".format(worstP95)}ms is within ${STARTUP_P95_BUDGET_MS.toInt()}ms budget.")
    }
}

fun extractStartupP95Values(jsonText: String): List<Double> {
    val startupMetricBlock = Regex(
        """"(?:timeToInitialDisplayMs|timeToFullDisplayMs|startupMs)"\s*:\s*\{([\s\S]*?)\n\s*\}""",
        RegexOption.IGNORE_CASE,
    )
    val p95Value = Regex(
        """"p95"\s*:\s*([0-9]+(?:\.[0-9]+)?)""",
        RegexOption.IGNORE_CASE,
    )
    val runsBlock = Regex(
        """"runs"\s*:\s*\[([\s\S]*?)]""",
        RegexOption.IGNORE_CASE,
    )
    val numberValue = Regex("""[0-9]+(?:\.[0-9]+)?""")
    return startupMetricBlock.findAll(jsonText)
        .mapNotNull { block ->
            val metricJson = block.groupValues[1]
            p95Value.find(metricJson)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: runsBlock.find(metricJson)
                    ?.groupValues
                    ?.get(1)
                    ?.let { runsJson ->
                        numberValue.findAll(runsJson).mapNotNull { it.value.toDoubleOrNull() }.toList()
                    }
                    ?.percentileNearestRank(95.0)
        }
        .toList()
}

fun List<Double>.percentileNearestRank(percentile: Double): Double? {
    if (isEmpty()) return null
    val rank = Math.ceil((percentile / 100.0) * size).toInt()
    return sorted()[rank.coerceIn(1, size) - 1]
}
