package com.sysadmindoc.nimbus.data.api

import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.CertificatePinner
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Test double for [Interceptor.Chain]. Implements every non-behavioural member so
 * subclasses only supply [request] and [proceed].
 *
 * OkHttp 5.4.0 widened `Interceptor.Chain` to expose the full effective client
 * configuration (the `val` properties below) plus `withX(...)` reconfiguration
 * methods. The config properties delegate to a default [OkHttpClient] so they
 * stay consistent with real OkHttp defaults, and the `withX` methods are no-ops
 * returning `this` (the interceptors under test never reconfigure the chain).
 * Keeping this in one place means future OkHttp additions are fixed here, not in
 * every test double.
 */
abstract class FakeInterceptorChain : Interceptor.Chain {
    private val defaults = OkHttpClient()

    override fun connection(): Connection? = null
    override fun call(): Call = error("unused")
    override fun connectTimeoutMillis(): Int = 15_000
    override fun readTimeoutMillis(): Int = 15_000
    override fun writeTimeoutMillis(): Int = 15_000

    override val followRedirects: Boolean get() = defaults.followRedirects
    override val followSslRedirects: Boolean get() = defaults.followSslRedirects
    override val retryOnConnectionFailure: Boolean get() = defaults.retryOnConnectionFailure
    override val dns: Dns get() = defaults.dns
    override val socketFactory: SocketFactory get() = defaults.socketFactory
    override val authenticator: Authenticator get() = defaults.authenticator
    override val proxyAuthenticator: Authenticator get() = defaults.proxyAuthenticator
    override val cookieJar: CookieJar get() = defaults.cookieJar
    override val cache: Cache? get() = defaults.cache
    override val proxy: Proxy? get() = defaults.proxy
    override val proxySelector: ProxySelector get() = defaults.proxySelector
    override val sslSocketFactoryOrNull: SSLSocketFactory? get() = null
    override val x509TrustManagerOrNull: X509TrustManager? get() = null
    override val hostnameVerifier: HostnameVerifier get() = defaults.hostnameVerifier
    override val certificatePinner: CertificatePinner get() = defaults.certificatePinner
    override val connectionPool: ConnectionPool get() = defaults.connectionPool
    override val eventListener: EventListener get() = EventListener.NONE

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun withAuthenticator(authenticator: Authenticator): Interceptor.Chain = this
    override fun withCache(cache: Cache?): Interceptor.Chain = this
    override fun withCertificatePinner(certificatePinner: CertificatePinner): Interceptor.Chain = this
    override fun withConnectionPool(connectionPool: ConnectionPool): Interceptor.Chain = this
    override fun withCookieJar(cookieJar: CookieJar): Interceptor.Chain = this
    override fun withDns(dns: Dns): Interceptor.Chain = this
    override fun withHostnameVerifier(hostnameVerifier: HostnameVerifier): Interceptor.Chain = this
    override fun withProxy(proxy: Proxy?): Interceptor.Chain = this
    override fun withProxyAuthenticator(proxyAuthenticator: Authenticator): Interceptor.Chain = this
    override fun withProxySelector(proxySelector: ProxySelector): Interceptor.Chain = this
    override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean): Interceptor.Chain = this
    override fun withSocketFactory(socketFactory: SocketFactory): Interceptor.Chain = this
    override fun withSslSocketFactory(
        sslSocketFactory: SSLSocketFactory?,
        x509TrustManager: X509TrustManager?,
    ): Interceptor.Chain = this
}
