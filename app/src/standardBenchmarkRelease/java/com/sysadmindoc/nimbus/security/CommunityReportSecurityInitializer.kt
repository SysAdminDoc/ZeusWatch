package com.sysadmindoc.nimbus.security

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityReportSecurityInitializer @Inject constructor() {
    fun install(context: Context) {
        if (!ensureFirebaseApp(context)) return
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }

    private fun ensureFirebaseApp(context: Context): Boolean {
        FirebaseApp.initializeApp(context)
        val available = FirebaseApp.getApps(context).isNotEmpty()
        if (!available) {
            Log.w(TAG, "Firebase config missing; community report App Check Play Integrity provider not installed.")
        }
        return available
    }

    companion object {
        private const val TAG = "ReportSecurityInit"
    }
}
