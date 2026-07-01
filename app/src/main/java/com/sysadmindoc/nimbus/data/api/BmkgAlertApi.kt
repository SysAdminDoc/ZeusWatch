package com.sysadmindoc.nimbus.data.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * BMKG public nowcast warning feed.
 *
 * API docs: https://data.bmkg.go.id/peringatan-dini-cuaca/
 */
interface BmkgAlertApi {

    @GET("alerts/nowcast/en")
    suspend fun getNowcastFeed(): ResponseBody

    @GET
    suspend fun getAlertDetail(@Url url: String): ResponseBody

    companion object {
        const val BASE_URL = "https://www.bmkg.go.id/"
    }
}
