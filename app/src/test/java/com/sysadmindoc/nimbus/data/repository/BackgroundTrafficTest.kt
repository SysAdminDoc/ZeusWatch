package com.sysadmindoc.nimbus.data.repository

import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.http.GET
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Real sockets, real Retrofit suspend calls: the surface is set on the
 * caller's thread and the call ends on OkHttp's, so only the whole path
 * shows whether the surface survives the hop.
 */
class BackgroundTrafficTest {
    private interface Api {
        @GET("/")
        suspend fun fetch(): ResponseBody
    }

    private lateinit var server: ServerSocket
    private val wireBytes = AtomicLong()
    private val workers = CopyOnWriteArrayList<Thread>()
    private lateinit var api: Api
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        BackgroundTraffic.drainAll()
        server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                workers += thread(isDaemon = true) { serve(socket) }
            }
        }
        client = OkHttpClient.Builder()
            .eventListenerFactory(BackgroundTraffic.eventListenerFactory)
            .build()
        api = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:${server.localPort}/")
            .client(client)
            .build()
            .create(Api::class.java)
    }

    @After
    fun tearDown() {
        server.close()
        BackgroundTraffic.drainAll()
    }

    /** One keep-alive connection: answers every request on it with [BODY]. */
    private fun serve(socket: java.net.Socket) = socket.use {
        val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val output = it.getOutputStream()
        while (true) {
            var requestBytes = 0L
            while (true) {
                val line = input.readLine() ?: return
                requestBytes += line.length + 2
                if (line.isEmpty()) break
            }
            val response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${BODY.length}\r\n\r\n$BODY"
            output.write(response.toByteArray(Charsets.ISO_8859_1))
            output.flush()
            wireBytes.addAndGet(requestBytes + response.length)
        }
    }

    @Test
    fun `a metered run records its requests and the bytes that crossed the socket`() = runBlocking {
        val health = mockk<DeliveryHealthRepository>(relaxed = true)
        val sample = slot<TrafficSample>()

        BackgroundTraffic.meter(DeliverySurface.WIDGETS, health) {
            repeat(3) { api.fetch().string() }
        }

        coVerify { health.recordTraffic(DeliverySurface.WIDGETS, capture(sample), any()) }
        assertEquals(3, sample.captured.requests)
        // Headers are counted as OkHttp sizes them (name, value, separators),
        // without the request and status lines, so the count sits between the
        // bodies alone and everything the server saw and sent.
        assertTrue("bytes ${sample.captured.bytes}", sample.captured.bytes > 3L * BODY.length)
        assertTrue("bytes ${sample.captured.bytes} > wire ${wireBytes.get()}", sample.captured.bytes <= wireBytes.get())
    }

    @Test
    fun `a plain OkHttp call made on another dispatcher inside a metered run is counted`() = runBlocking {
        val health = mockk<DeliveryHealthRepository>(relaxed = true)
        val sample = slot<TrafficSample>()
        val url = "http://127.0.0.1:${server.localPort}/"

        BackgroundTraffic.meter(DeliverySurface.HEALTH_ALERTS, health) {
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }
            }
        }

        coVerify { health.recordTraffic(DeliverySurface.HEALTH_ALERTS, capture(sample), any()) }
        assertEquals(1, sample.captured.requests)
    }

    @Test
    fun `a call made outside any metered run is not counted`() = runBlocking {
        withContext(Dispatchers.IO) { api.fetch().string() }

        DeliverySurface.entries.forEach { assertEquals(TrafficSample.NONE, BackgroundTraffic.drain(it)) }
    }

    @Test
    fun `two surfaces running at once are counted apart`() = runBlocking {
        val health = mockk<DeliveryHealthRepository>(relaxed = true)
        val alerts = slot<TrafficSample>()
        val widgets = slot<TrafficSample>()

        listOf(
            async(Dispatchers.IO) { BackgroundTraffic.meter(DeliverySurface.WEATHER_ALERTS, health) { repeat(2) { api.fetch().string() } } },
            async(Dispatchers.IO) { BackgroundTraffic.meter(DeliverySurface.WIDGETS, health) { repeat(5) { api.fetch().string() } } },
        ).awaitAll()

        coVerify { health.recordTraffic(DeliverySurface.WEATHER_ALERTS, capture(alerts), any()) }
        coVerify { health.recordTraffic(DeliverySurface.WIDGETS, capture(widgets), any()) }
        assertEquals(2, alerts.captured.requests)
        assertEquals(5, widgets.captured.requests)
    }

    @Test
    fun `a run that throws still records what it used`() = runBlocking {
        val health = mockk<DeliveryHealthRepository>(relaxed = true)
        val sample = slot<TrafficSample>()

        runCatching {
            BackgroundTraffic.meter(DeliverySurface.NOWCAST_ALERTS, health) {
                api.fetch().string()
                error("parse failed")
            }
        }

        coVerify { health.recordTraffic(DeliverySurface.NOWCAST_ALERTS, capture(sample), any()) }
        assertEquals(1, sample.captured.requests)
    }

    private companion object {
        const val BODY = "{\"temperature\":21.5}"
    }
}
