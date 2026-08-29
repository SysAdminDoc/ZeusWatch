package com.sysadmindoc.nimbus.sync

/**
 * What happened when the app tried to push weather to a paired watch.
 *
 * Distinguishes "there is no watch" from "there is a watch and it did not
 * work". The first is not a fault and must not be reported as one; the second
 * is exactly what the delivery diagnostics exist to show. Both were previously
 * swallowed inside the sync manager, so the caller recorded a successful
 * delivery every single time.
 */
enum class WearSyncOutcome {
    SYNCED,
    UNAVAILABLE,
    FAILED,
}
