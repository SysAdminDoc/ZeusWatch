package com.sysadmindoc.nimbus.data.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import org.junit.Assert.assertEquals
import org.junit.Test

class BlitzortungServiceTest {

    @Test
    fun `blitzortungReconnectDelayMs backs off exponentially with a cap`() {
        assertEquals(1_000L, blitzortungReconnectDelayMs(0))
        assertEquals(2_000L, blitzortungReconnectDelayMs(1))
        assertEquals(4_000L, blitzortungReconnectDelayMs(2))
        assertEquals(32_000L, blitzortungReconnectDelayMs(5))
        assertEquals(32_000L, blitzortungReconnectDelayMs(12))
    }

    @Test
    fun `connect shares one socket across multiple clients`() {
        val harness = serviceHarness()

        harness.service.connect()
        harness.service.connect()

        verify(exactly = 1) { harness.wsClient.newWebSocket(any(), any()) }
    }

    @Test
    fun `disconnect only tears down when the last client releases`() {
        val harness = serviceHarness()
        harness.service.connect()
        harness.service.connect()

        harness.service.disconnect()

        // One radar surface leaving must not cut lightning off for the other.
        verify(exactly = 0) { harness.socket.close(any(), any()) }

        harness.service.disconnect()

        verify(exactly = 1) { harness.socket.close(1000, any()) }
    }

    @Test
    fun `unbalanced disconnects do not underflow the client count`() {
        val harness = serviceHarness()

        harness.service.disconnect()
        harness.service.disconnect()
        harness.service.connect()
        harness.service.disconnect()

        verify(exactly = 1) { harness.wsClient.newWebSocket(any(), any()) }
        verify(exactly = 1) { harness.socket.close(1000, any()) }
    }

    private class ServiceHarness(
        val service: BlitzortungService,
        val wsClient: OkHttpClient,
        val socket: WebSocket,
    )

    private fun serviceHarness(): ServiceHarness {
        val socket = mockk<WebSocket>(relaxed = true)
        val wsClient = mockk<OkHttpClient>()
        every { wsClient.newWebSocket(any(), any()) } returns socket
        val builder = mockk<OkHttpClient.Builder>()
        every { builder.retryOnConnectionFailure(any()) } returns builder
        every { builder.build() } returns wsClient
        val baseClient = mockk<OkHttpClient>()
        every { baseClient.newBuilder() } returns builder
        return ServiceHarness(BlitzortungService(baseClient), wsClient, socket)
    }
}
