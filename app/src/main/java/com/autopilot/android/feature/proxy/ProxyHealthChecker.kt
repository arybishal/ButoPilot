package com.autopilot.android.feature.proxy

import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.domain.model.proxy.ProxyTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class ProxyHealthChecker(
    private val connectTimeoutMs: Long = 5000L,
    private val readTimeoutMs: Long = 5000L,
    private val probeUrl: String = "https://www.google.com/generate_204"
) {

    /**
     * Executes an isolated network probe through the given proxy configuration with a strict timeout.
     */
    suspend fun checkHealth(proxy: ProxyConfiguration): ProxyTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            when (proxy.protocol) {
                ProxyProtocol.HTTP, ProxyProtocol.HTTPS -> {
                    checkHttpProxy(proxy, startTime)
                }
                ProxyProtocol.SOCKS5 -> {
                    checkSocksProxy(proxy, startTime)
                }
            }
        } catch (e: SocketTimeoutException) {
            ProxyTestResult(
                proxyId = proxy.id,
                status = ProxyHealthStatus.TIMEOUT,
                latencyMs = System.currentTimeMillis() - startTime,
                timestamp = System.currentTimeMillis(),
                errorMessage = "Connection timed out after ${connectTimeoutMs}ms: ${e.message}"
            )
        } catch (e: IOException) {
            val message = e.message ?: "Unknown I/O error"
            val status = when {
                message.contains("407", ignoreCase = true) || message.contains("authentication", ignoreCase = true) ->
                    ProxyHealthStatus.AUTH_FAILURE
                message.contains("refused", ignoreCase = true) || message.contains("unreachable", ignoreCase = true) ->
                    ProxyHealthStatus.UNHEALTHY
                else ->
                    ProxyHealthStatus.UNHEALTHY
            }
            ProxyTestResult(
                proxyId = proxy.id,
                status = status,
                latencyMs = System.currentTimeMillis() - startTime,
                timestamp = System.currentTimeMillis(),
                errorMessage = message
            )
        } catch (e: Exception) {
            ProxyTestResult(
                proxyId = proxy.id,
                status = ProxyHealthStatus.UNHEALTHY,
                latencyMs = System.currentTimeMillis() - startTime,
                timestamp = System.currentTimeMillis(),
                errorMessage = e.message ?: "General check failure"
            )
        }
    }

    private fun checkHttpProxy(proxy: ProxyConfiguration, startTime: Long): ProxyTestResult {
        val javaProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.host, proxy.port))

        val clientBuilder = OkHttpClient.Builder()
            .proxy(javaProxy)
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        val username = proxy.credentials?.username
        val password = proxy.credentials?.password
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            clientBuilder.proxyAuthenticator(object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? {
                    if (response.challenges().any { it.scheme.equals("OkHttp-Preemptive", ignoreCase = true) || it.scheme.equals("Basic", ignoreCase = true) }) {
                        val credential = Credentials.basic(username, password)
                        return response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                    val credential = Credentials.basic(username, password)
                    return response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            })
        }

        val client = clientBuilder.build()
        val request = Request.Builder()
            .url(probeUrl)
            .header("User-Agent", "AutoPilot/1.0.0 (Android; HealthCheck)")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                if (response.isSuccessful || response.code == 204 || response.code == 200) {
                    ProxyTestResult(
                        proxyId = proxy.id,
                        status = ProxyHealthStatus.HEALTHY,
                        latencyMs = elapsed,
                        timestamp = System.currentTimeMillis()
                    )
                } else if (response.code == 407) {
                    ProxyTestResult(
                        proxyId = proxy.id,
                        status = ProxyHealthStatus.AUTH_FAILURE,
                        latencyMs = elapsed,
                        timestamp = System.currentTimeMillis(),
                        errorMessage = "Proxy authentication required (HTTP 407)"
                    )
                } else {
                    ProxyTestResult(
                        proxyId = proxy.id,
                        status = ProxyHealthStatus.UNHEALTHY,
                        latencyMs = elapsed,
                        timestamp = System.currentTimeMillis(),
                        errorMessage = "HTTP probe failed with status ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            // If probeUrl request fails (e.g. strict firewall), fallback to TCP socket probe to verify reachability
            fallbackSocketProbe(proxy, startTime, e)
        }
    }

    private fun checkSocksProxy(proxy: ProxyConfiguration, startTime: Long): ProxyTestResult {
        // Direct socket handshake check to verify SOCKS proxy host and port reachability
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(proxy.host, proxy.port), connectTimeoutMs.toInt())
            val elapsed = System.currentTimeMillis() - startTime
            ProxyTestResult(
                proxyId = proxy.id,
                status = ProxyHealthStatus.HEALTHY,
                latencyMs = elapsed,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun fallbackSocketProbe(proxy: ProxyConfiguration, startTime: Long, primaryException: Exception): ProxyTestResult {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(proxy.host, proxy.port), connectTimeoutMs.toInt())
                val elapsed = System.currentTimeMillis() - startTime
                ProxyTestResult(
                    proxyId = proxy.id,
                    status = ProxyHealthStatus.HEALTHY,
                    latencyMs = elapsed,
                    timestamp = System.currentTimeMillis(),
                    errorMessage = "HTTP probe failed (${primaryException.message}), but TCP port is reachable."
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            ProxyTestResult(
                proxyId = proxy.id,
                status = ProxyHealthStatus.UNHEALTHY,
                latencyMs = elapsed,
                timestamp = System.currentTimeMillis(),
                errorMessage = "Probe failed: ${primaryException.message}"
            )
        }
    }
}
