package com.sysadmindoc.nimbus.util

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.workDataOf
import com.sysadmindoc.nimbus.data.repository.NimbusSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether a background fetch may use the network it's on.
 *
 * Checked when the worker runs rather than set as a WorkManager constraint,
 * so a run held back for mobile data is written down as a skip the delivery
 * panel can show, not a job that silently never fires.
 */
@Singleton
class BackgroundNetworkPolicy @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isMetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.isActiveNetworkMetered
    }

    /**
     * True when this run should wait for an unmetered network: the user asked
     * for background updates on Wi-Fi only, nobody started this run by hand,
     * and the active network is metered.
     */
    fun holdsBack(settings: NimbusSettings, userInitiated: Boolean): Boolean =
        settings.backgroundUnmeteredOnly && !userInitiated && isMetered()
}

/** Constraints and input shared by every background worker's requests. */
object BackgroundWork {
    /** Set on a run the user started (Run now, a widget tap); it skips the battery and Wi-Fi holds. */
    const val KEY_USER_INITIATED = "user_initiated"

    /** A scheduled run: any network, and not while the battery is low. */
    fun periodicConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Severe weather alerts keep checking at low battery. A phone at 14% in a
     * tornado warning is the one that most needs to hear about it.
     */
    fun alertConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * A run the user asked for. It still needs a network (offline, every
     * provider call fails and Run now manufactures the failure it exists to
     * diagnose), but nothing else holds it back.
     */
    fun userInitiatedConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun userInitiatedInput(): Data = workDataOf(KEY_USER_INITIATED to true)

    fun isUserInitiated(inputData: Data): Boolean = inputData.getBoolean(KEY_USER_INITIATED, false)
}
