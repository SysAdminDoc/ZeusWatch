package com.sysadmindoc.nimbus.data.model

/**
 * Represents an installable weather icon pack.
 *
 * Icon packs can be bundled in the app's assets or provided by external APKs
 * that declare the `com.sysadmindoc.nimbus.ICON_PACK` intent action.
 *
 * Each pack maps WMO weather codes to day/night icon filenames within the pack.
 */
data class IconPack(
    val id: String,
    val name: String,
    val author: String = "",
    val format: String = "png",
    val source: IconPackSource,
    val mappings: Map<Int, IconMapping>,
)

/**
 * Day/night icon filenames for a single WMO weather code.
 */
data class IconMapping(
    val dayIcon: String,
    val nightIcon: String,
)

/**
 * Where an icon pack's assets are located.
 */
sealed class IconPackSource {
    /** Icons bundled in `assets/iconpacks/{assetPath}/`. */
    data class Bundled(val assetPath: String) : IconPackSource()

    /** Icons provided by an external APK with the given package name. */
    data class External(val packageName: String) : IconPackSource()
}
