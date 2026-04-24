package com.sysadmindoc.nimbus.util

import android.app.Application
import com.sysadmindoc.nimbus.BuildConfig
import org.acra.ACRA
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

/**
 * GMS-free crash reporting. Runs in both `standard` and `freenet` flavors.
 *
 * Crashes are *not* auto-uploaded; ACRA opens a dialog that asks the user
 * whether to email the sanitized report (no PII, no API keys, no location).
 * This keeps the freenet flavor F-Droid-compliant (no network call without
 * user consent) and respects the roadmap constraint that Crashlytics was
 * deliberately removed in v1.5.0.
 *
 * Logs are skipped in debug builds to keep local iteration clean — rely on
 * logcat there.
 */
object CrashReporting {
    private const val REPORT_EMAIL = "snafumatthew+zeuswatch@gmail.com"

    fun install(application: Application) {
        if (BuildConfig.DEBUG) return
        if (ACRA.isACRASenderServiceProcess()) return

        application.initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.KEY_VALUE_LIST
            // Redact anything that might carry a supplied API key or coarse
            // location back to the developer. ACRA already strips request
            // bodies by default; this locks the URL parameter form too.
            excludeMatchingSharedPreferencesKeys = listOf(
                ".*apikey.*",
                ".*api_key.*",
                ".*owm_key.*",
                ".*pirate_key.*",
                ".*last_location.*",
            )

            dialog {
                title = "ZeusWatch crashed"
                text = "Send a sanitized crash log by email? No API keys or " +
                    "coordinates are included."
                positiveButtonText = "Send"
                negativeButtonText = "No thanks"
                resIcon = android.R.drawable.ic_dialog_alert
            }

            mailSender {
                mailTo = REPORT_EMAIL
                reportAsFile = true
                reportFileName = "zeuswatch-crash.txt"
                subject = "ZeusWatch crash report"
            }
        }
    }
}
