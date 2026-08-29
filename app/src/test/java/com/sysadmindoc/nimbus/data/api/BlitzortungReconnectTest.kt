package com.sysadmindoc.nimbus.data.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Test

/**
 * The reconnect path, driven through the real WebSocketListener.
 *
 * onOpen used to write isConnected, reconnectAttempts and reconnectJob straight
 * from OkHttp's reader thread; it now goes through a synchronized
 * onSocketOpened so clearing the attempt counter and cancelling the pending
 * reconnect are one decision. Nothing tested any of it, so the whole thing
 * could be undone without a single failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlitzortungReconnectTest {

    private class Harness(
        val service: BlitzortungService,
        val wsClient: OkHttpClient,
        val socket: WebSocket,
        val listener: () -> WebSocketListener,
    )

    private fun harness(dispatcher: kotlinx.coroutines.CoroutineDispatcher): Harness {
        val socket = mockk<WebSocket>(relaxed = true)
        val wsClient = mockk<OkHttpClient>()
        val captured = slot<WebSocketListener>()
        every { wsClient.newWebSocket(any(), capture(captured)) } returns socket
        val builder = mockk<OkHttpClient.Builder>()
        every { builder.retryOnConnectionFailure(any()) } returns builder
        every { builder.build() } returns wsClient
        val baseClient = mockk<OkHttpClient>()
        every { baseClient.newBuilder() } returns builder
        return Harness(
            service = BlitzortungService(baseClient, dispatcher),
            wsClient = wsClient,
            socket = socket,
            listener = { captured.captured },
        )
    }

    private fun openResponse(): Response = Response.Builder()
        .request(Request.Builder().url("https://ws.blitzortung.org/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(101)
        .message("Switching Protocols")
        .build()

    @Test
    fun `a failure schedules exactly one reconnect`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = harness(dispatcher)
        h.service.connect()

        h.listener().onFailure(h.socket, java.io.IOException("dropped"), null)
        advanceTimeBy(2_000L)
        testScheduler.runCurrent()

        // One for connect, one for the reconnect after the first backoff.
        verify(exactly = 2) { h.wsClient.newWebSocket(any(), any()) }
    }

    @Test
    fun `an open cancels the pending reconnect`() = runTest {
        // This is the fix: without it a socket that came back on its own still
        // had a reconnect queued behind it, which opened a second stream.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = harness(dispatcher)
        h.service.connect()

        h.listener().onFailure(h.socket, java.io.IOException("dropped"), null)
        h.listener().onOpen(h.socket, openResponse())
        advanceTimeBy(60_000L)
        testScheduler.runCurrent()

        verify(exactly = 1) { h.wsClient.newWebSocket(any(), any()) }
    }

    @Test
    fun `an open resets the backoff so the next drop retries quickly`() = runTest {
        // reconnectAttempts is private, so the observable consequence is the
        // delay: after a successful open the next failure waits the first
        // backoff again rather than the escalated one.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = harness(dispatcher)
        h.service.connect()

        repeat(3) {
            h.listener().onFailure(h.socket, java.io.IOException("dropped"), null)
            advanceTimeBy(blitzortungReconnectDelayMs(it) + 100L)
            testScheduler.runCurrent()
        }
        h.listener().onOpen(h.socket, openResponse())
        val socketsSoFar = 4

        h.listener().onFailure(h.socket, java.io.IOException("dropped again"), null)
        advanceTimeBy(blitzortungReconnectDelayMs(0) + 100L)
        testScheduler.runCurrent()

        verify(exactly = socketsSoFar + 1) { h.wsClient.newWebSocket(any(), any()) }
    }

    @Test
    fun `disconnecting stops the reconnect from firing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = harness(dispatcher)
        h.service.connect()

        h.listener().onFailure(h.socket, java.io.IOException("dropped"), null)
        h.service.disconnect()
        advanceTimeBy(60_000L)
        testScheduler.runCurrent()

        verify(exactly = 1) { h.wsClient.newWebSocket(any(), any()) }
    }
}
