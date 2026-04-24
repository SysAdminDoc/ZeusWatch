package com.sysadmindoc.nimbus.data.api

import okhttp3.CertificatePinner

/**
 * Certificate pin registry for ZeusWatch's weather-provider endpoints.
 *
 * Pinning is scoped to **API-key-bearing endpoints only** — a MITM proxy
 * intercepting a request to `openweathermap.org` can steal the user's
 * `?appid=<key>` query parameter. Keyless public APIs (Open-Meteo, NWS,
 * RainViewer, MeteoAlarm, JMA, ECCC, DWD Bright Sky) are NOT pinned:
 * pinning them adds a brittleness cost (app breaks on cert rotation)
 * without any corresponding security benefit — a MITM of a public
 * endpoint just serves wrong weather data, which is a nuisance, not a
 * compromise.
 *
 * **Rotation-safe by design**: each entry must list *both* the leaf SPKI
 * and at least one intermediate SPKI. When the leaf rotates (typical
 * 90-day Let's Encrypt cadence), the intermediate pin still validates;
 * when the intermediate rotates (much rarer, typically ~yearly), the
 * leaf pin still validates. Both would have to rotate in the same
 * deployment for a full break.
 *
 * **Enabling pinning for a new host:**
 *   1. Run `tools/capture_api_pins.sh` against the host.
 *   2. Copy the `sha256/…` values into the [hostPins] map below.
 *   3. Ship. The [build] function wires pins into [NetworkModule]'s
 *      OkHttp client automatically.
 *
 * **Current status (v1.17.0):** the map is intentionally empty. Pins are
 * captured per-release in the release workflow, not hardcoded here —
 * this avoids the "Claude wrote a fake SHA" failure mode and lets CI
 * re-capture pins when certificates rotate. Until pins are populated,
 * [build] returns a no-op [CertificatePinner], preserving runtime
 * behavior while the scaffolding ships.
 */
object ApiCertificatePins {

    /**
     * Hostname → ordered list of `sha256/…` SPKI pins (leaf + intermediate(s)).
     * Populate via `tools/capture_api_pins.sh` before enabling in production.
     *
     * Empty map = pinning disabled but scaffolding intact.
     */
    val hostPins: Map<String, List<String>> = mapOf(
        // "api.openweathermap.org" to listOf(
        //     "sha256/<leaf-spki>",
        //     "sha256/<intermediate-spki>",
        //     "sha256/<backup-intermediate-spki>",
        // ),
        // "api.pirateweather.net" to listOf(
        //     "sha256/<leaf-spki>",
        //     "sha256/<intermediate-spki>",
        // ),
    )

    /**
     * Build a [CertificatePinner] containing every pin in [hostPins].
     * Returns [CertificatePinner.DEFAULT] (no-op) when the map is empty.
     */
    fun build(): CertificatePinner {
        if (hostPins.isEmpty()) return CertificatePinner.DEFAULT
        val builder = CertificatePinner.Builder()
        for ((host, pins) in hostPins) {
            for (pin in pins) builder.add(host, pin)
        }
        return builder.build()
    }

    /** True when at least one host has pins configured. For wiring tests. */
    val isActive: Boolean get() = hostPins.isNotEmpty()
}
