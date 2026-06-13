package com.sysadmindoc.nimbus.data.api

import kotlinx.serialization.json.JsonArray
import retrofit2.http.GET

interface NoaaSwpcApi {

    @GET("products/noaa-planetary-k-index.json")
    suspend fun getKpIndex(): JsonArray

    companion object {
        const val BASE_URL = "https://services.swpc.noaa.gov/"
    }
}
