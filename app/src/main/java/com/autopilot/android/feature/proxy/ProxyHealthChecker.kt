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
        return Socket().use { socket ->
            socket.soTimeout = readTimeoutMs.toInt()
            socket.connect(InetSocketAddress(proxy.host, proxy.port), connectTimeoutMs.toInt())

            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            val username = proxy.credentials?.username
            val password = proxy.credentials?.password
            val hasAuth = !username.isNullOrBlank() && !password.isNullOrBlank()

            // 1. SOCKS5 Client Greeting
            if (hasAuth) {
                // Methods: 0x00 (NO AUTH), 0x02 (USER/PASS)
                output.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                // Method: 0x00 (NO AUTH)
                output.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            output.flush()

            // 2. Read Server Method Selection
            val serverVer = input.read()
            if (serverVer == -1) throw IOException("SOCKS5 server closed connection during greeting")
            if (serverVer != 0x05) throw IOException("Invalid SOCKS version in server greeting: $serverVer (expected 0x05)")

            val selectedMethod = input.read()
            if (selectedMethod == -1) throw IOException("SOCKS5 server closed connection reading auth method")
            if (selectedMethod == 0xFF) {
                return ProxyTestResult(
                    proxyId = proxy.id,
                    status = ProxyHealthStatus.AUTH_FAILURE,
                    latencyMs = System.currentTimeMillis() - startTime,
                    timestamp = System.currentTimeMillis(),
                    errorMessage = "SOCKS5 server rejected authentication methods (0xFF: NO ACCEPTABLE METHODS)"
                )
            }

            // 3. Username/Password Subnegotiation (RFC 1929)
            if (selectedMethod == 0x02) {
                if (!hasAuth) {
                    return ProxyTestResult(
                        proxyId = proxy.id,
                        status = ProxyHealthStatus.AUTH_FAILURE,
                        latencyMs = System.currentTimeMillis() - startTime,
                        timestamp = System.currentTimeMillis(),
                        errorMessage = "SOCKS5 server requires authentication (method 0x02) but no credentials were provided."
                    )
                }

                val uBytes = username!!.toByteArray(Charsets.UTF_8)
                val pBytes = password!!.toByteArray(Charsets.UTF_8)
                val authPacket = ByteArray(3 + uBytes.size + pBytes.size)
                authPacket[0] = 0x01 // Subnegotiation version
                authPacket[1] = uBytes.size.toByte()
                System.arraycopy(uBytes, 0, authPacket, 2, uBytes.size)
                authPacket[2 + uBytes.size] = pBytes.size.toByte()
                System.arraycopy(pBytes, 0, authPacket, 3 + uBytes.size, pBytes.size)

                output.write(authPacket)
                output.flush()

                val authVer = input.read()
                val authStatus = input.read()
                if (authStatus != 0x00) {
                    return ProxyTestResult(
                        proxyId = proxy.id,
                        status = ProxyHealthStatus.AUTH_FAILURE,
                        latencyMs = System.currentTimeMillis() - startTime,
                        timestamp = System.currentTimeMillis(),
                        errorMessage = "SOCKS5 authentication failed with status code $authStatus"
                    )
                }
            }

            // 4. SOCKS5 CONNECT probe command to verify tunneling (probe www.google.com:80)
            val probeHost = "www.google.com"
            val probePort = 80
            val hostBytes = probeHost.toByteArray(Charsets.UTF_8)

            val connectCmd = ByteArray(4 + 1 + hostBytes.size + 2)
            connectCmd[0] = 0x05 // SOCKS version 5
            connectCmd[1] = 0x01 // CMD: CONNECT
            connectCmd[2] = 0x00 // RSV: reserved
            connectCmd[3] = 0x03 // ATYP: DOMAINNAME
            connectCmd[4] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, connectCmd, 5, hostBytes.size)
            connectCmd[5 + hostBytes.size] = ((probePort ushr 8) and 0xFF).toByte()
            connectCmd[5 + hostBytes.size + 1] = (probePort and 0xFF).toByte()

            output.write(connectCmd)
            output.flush()

            // 5. Read SOCKS5 Response
            val respVer = input.read()
            val respCode = input.read()
            val respRsv = input.read()
            val respAtyp = input.read()

            if (respVer == -1 || respCode == -1) {
                throw IOException("SOCKS5 server closed connection during CONNECT response")
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (respCode == 0x00) {
                ProxyTestResult(
                    proxyId = proxy.id,
                    status = ProxyHealthStatus.HEALTHY,
                    latencyMs = elapsed,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                val repDesc = when (respCode) {
                    0x01 -> "General SOCKS server failure (0x01)"
                    0x02 -> "Connection not allowed by ruleset (0x02)"
                    0x03 -> "Network unreachable (0x03)"
                    0x04 -> "Host unreachable (0x04)"
                    0x05 -> "Connection refused (0x05)"
                    0x06 -> "TTL expired (0x06)"
                    0x07 -> "Command not supported (0x07)"
                    0x08 -> "Address type not supported (0x08)"
                    else -> "SOCKS error code ($respCode)"
                }
                ProxyTestResult(
                    proxyId = proxy.id,
                    status = ProxyHealthStatus.UNHEALTHY,
                    latencyMs = elapsed,
                    timestamp = System.currentTimeMillis(),
                    errorMessage = "SOCKS5 CONNECT failed: $repDesc"
                )
            }
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
