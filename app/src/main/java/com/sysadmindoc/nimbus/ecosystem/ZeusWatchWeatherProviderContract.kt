package com.sysadmindoc.nimbus.ecosystem

internal object ZeusWatchWeatherProviderContract {
    const val AUTHORITY_SUFFIX = ".provider.weather"
    const val READ_PERMISSION_SUFFIX = ".READ_PROVIDER"

    const val VERSION_PATH = "version"
    const val LOCATIONS_PATH = "locations"
    const val WEATHER_PATH = "weather"

    const val VERSION_CODE = 1
    const val LOCATIONS_CODE = 2
    const val WEATHER_CODE = 3

    const val SCHEMA_MAJOR = 0
    const val SCHEMA_MINOR = 1

    const val COLUMN_MAJOR = "major"
    const val COLUMN_MINOR = "minor"

    const val COLUMN_ID = "id"
    const val COLUMN_LATITUDE = "latitude"
    const val COLUMN_LONGITUDE = "longitude"
    const val COLUMN_IS_CURRENT_POSITION = "is_current_position"
    const val COLUMN_TIMEZONE = "timezone"
    const val COLUMN_CUSTOM_NAME = "custom_name"
    const val COLUMN_COUNTRY = "country"
    const val COLUMN_COUNTRY_CODE = "country_code"
    const val COLUMN_ADMIN1 = "admin1"
    const val COLUMN_ADMIN1_CODE = "admin1_code"
    const val COLUMN_ADMIN2 = "admin2"
    const val COLUMN_ADMIN2_CODE = "admin2_code"
    const val COLUMN_ADMIN3 = "admin3"
    const val COLUMN_ADMIN3_CODE = "admin3_code"
    const val COLUMN_ADMIN4 = "admin4"
    const val COLUMN_ADMIN4_CODE = "admin4_code"
    const val COLUMN_CITY = "city"
    const val COLUMN_DISTRICT = "district"
    const val COLUMN_WEATHER = "weather"

    val VERSION_COLUMNS = arrayOf(COLUMN_MAJOR, COLUMN_MINOR)

    val LOCATION_COLUMNS = arrayOf(
        COLUMN_ID,
        COLUMN_LATITUDE,
        COLUMN_LONGITUDE,
        COLUMN_IS_CURRENT_POSITION,
        COLUMN_TIMEZONE,
        COLUMN_CUSTOM_NAME,
        COLUMN_COUNTRY,
        COLUMN_COUNTRY_CODE,
        COLUMN_ADMIN1,
        COLUMN_ADMIN1_CODE,
        COLUMN_ADMIN2,
        COLUMN_ADMIN2_CODE,
        COLUMN_ADMIN3,
        COLUMN_ADMIN3_CODE,
        COLUMN_ADMIN4,
        COLUMN_ADMIN4_CODE,
        COLUMN_CITY,
        COLUMN_DISTRICT,
        COLUMN_WEATHER,
    )

    fun authority(packageName: String): String = packageName + AUTHORITY_SUFFIX

    fun readPermission(packageName: String): String = packageName + READ_PERMISSION_SUFFIX
}

