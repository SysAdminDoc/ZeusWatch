package com.sysadmindoc.nimbus.data.api

import okhttp3.CertificatePinner

/**
 * Certificate pin registry for ZeusWatch's weather-provider endpoints.
 *
 * Pinning is scoped to endpoints that carry a **user secret or a privacy
 * signal**. A MITM proxy intercepting a request to `openweathermap.org` can
 * steal the user's `?appid=<key>` query parameter; a MITM of the Open-Meteo
 * geocoding host can read the user's typed place-name search text — a privacy
 * leak in a zero-telemetry app. Keyless *forecast* APIs (Open-Meteo forecast,
 * NWS, RainViewer, MeteoAlarm, JMA, ECCC, DWD Bright Sky) are NOT pinned:
 * pinning them adds a brittleness cost (app breaks on cert rotation)
 * without any corresponding security benefit — a MITM of a public forecast
 * endpoint just serves wrong weather data, which is a nuisance, not a
 * compromise. The geocoding host is the one keyless exception because the
 * request body (the search query) is itself sensitive.
 *
 * **Rotation-safe by design**: each entry must list *both* the leaf SPKI
 * and at least one intermediate SPKI. When the leaf rotates (typical
 * 90-day Let's Encrypt cadence), the intermediate pin still validates;
 * when the intermediate rotates (much rarer, typically ~yearly), the
 * leaf pin still validates. Both would have to rotate in the same
 * deployment for a full break.
 *
 * **Enabling pinning for a new host:**
 *   1. Run `tools/capture_api_pins.sh` or `tools/capture_api_pins.ps1`
 *      against the host.
 *   2. Copy the `sha256/…` values into the [hostPins] map below.
 *   3. Ship. The [build] function wires pins into [NetworkModule]'s
 *      OkHttp client automatically.
 *
 * **Current status:** OpenWeatherMap and Pirate Weather pins were captured
 * from live TLS chains on 2026-05-17. OpenWeatherMap's forecast and air
 * pollution APIs share `api.openweathermap.org`, so one host entry covers
 * both keyed OWM clients.
 */
object ApiCertificatePins {

    /**
     * Hostname → ordered list of `sha256/…` SPKI pins (leaf + intermediate(s)).
     * Populate via `tools/capture_api_pins.sh` before enabling in production.
     *
     * Captured 2026-05-17 via both capture scripts and verified against the
     * live chains. Re-run the capture script before every release and update
     * these values if either host rotates leaf or intermediate certificates.
     */
    val hostPins: Map<String, List<String>> = mapOf(
        "api.openweathermap.org" to listOf(
            "sha256/2rABlvP8a/45fRdYlmvSYEWrgBZyNampT8AqVpcPMtk=", // leaf: *.openweathermap.org
            "sha256/KqkYYX5LYAYP7XGemqzbtPPIA8x7BS/BbOIcAXf3j2k=", // Sectigo Public Server Authentication CA OV R36
            "sha256/Douxi77vs4G+Ib/BogbTFymEYq0QSFXwSgVCaZcI09Q=", // Sectigo Public Server Authentication Root R46
        ),
        "api.pirateweather.net" to listOf(
            "sha256/95LHu8iEMa32s8PFk1JCca+ww8wU+Oay940so3a6Iek=", // leaf: *.pirateweather.net
            "sha256/G9LNNAql897egYsabashkzUCTEJkWBzgoEtk8X/678c=", // Amazon RSA 2048 M04
            "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI=", // Amazon Root CA 1
        ),
        // Keyless, but the query carries the user's typed place name (privacy).
        // Captured 2026-07-23 from the live Let's Encrypt chain; the long-lived
        // ISRG root pin keeps this rotation-safe across the 90-day leaf cadence.
        "geocoding-api.open-meteo.com" to listOf(
            "sha256/reVU4Wtm3aCYigAHoOasPjxb8Ns9uXYf+yULCbcaErw=", // leaf: CN=geocoding-api.open-meteo.com
            "sha256/nWN7PSep5XDQdge5zK24CnCRXHr3KvzhKEGxsdqCX9E=", // intermediate: Let's Encrypt YR2
            "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88=", // root: ISRG Root YR
        ),
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
