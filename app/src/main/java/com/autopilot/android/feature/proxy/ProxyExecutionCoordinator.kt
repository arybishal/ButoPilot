package com.autopilot.android.feature.proxy

import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.Executor
import kotlin.coroutines.resume

class ProxyConflictException(message: String) : IllegalStateException(message)

class ProxyExecutionCoordinator {

    private val mutex = Mutex()
    private val mainExecutor = Executor { command ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            command.run()
        } else {
            Handler(Looper.getMainLooper()).post(command)
        }
    }

    private val _activeProxy = MutableStateFlow<ProxyConfiguration?>(null)
    val activeProxy: StateFlow<ProxyConfiguration?> = _activeProxy.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isProxyActive = MutableStateFlow(false)
    val isProxyActive: StateFlow<Boolean> = _isProxyActive.asStateFlow()

    /**
     * Executes the given suspending block with exclusive serialized proxy access.
     * Restores default network configuration and releases lock unconditionally on exit.
     */
    suspend fun <T> executeWithProxy(
        proxy: ProxyConfiguration?,
        block: suspend () -> T
    ): T {
        acquireAndApply(proxy)
        return try {
            block()
        } finally {
            resetAndRelease()
        }
    }

    /**
     * Acquires exclusive lock and applies proxy configuration process-wide to WebView.
     */
    suspend fun acquireAndApply(proxy: ProxyConfiguration?) {
        mutex.lock()
        _isLocked.value = true

        try {
            if (proxy != null) {
                val applied = applyProxyInternal(proxy)
                _isProxyActive.value = applied
                _activeProxy.value = if (applied) proxy else null
            } else {
                clearProxyInternal()
                _isProxyActive.value = false
                _activeProxy.value = null
            }
        } catch (e: Exception) {
            // If applying fails, unlock and propagate
            resetAndRelease()
            throw e
        }
    }

    /**
     * Clears proxy override, restores default network routing, and releases exclusive lock.
     * Guaranteed safe to call multiple times.
     */
    suspend fun resetAndRelease() {
        try {
            clearProxyInternal()
            _isProxyActive.value = false
            _activeProxy.value = null
        } catch (e: Exception) {
            // Log or ignore during teardown
        } finally {
            _isLocked.value = false
            if (mutex.isLocked) {
                mutex.unlock()
            }
        }
    }

    /**
     * Updates the active proxy override dynamically while maintaining current lock ownership.
     */
    suspend fun updateProxy(proxy: ProxyConfiguration?) {
        try {
            if (proxy != null) {
                val applied = applyProxyInternal(proxy)
                _isProxyActive.value = applied
                _activeProxy.value = if (applied) proxy else null
            } else {
                clearProxyInternal()
                _isProxyActive.value = false
                _activeProxy.value = null
            }
        } catch (e: Exception) {
            // Fall back or keep existing proxy on update error
        }
    }

    private suspend fun applyProxyInternal(proxy: ProxyConfiguration): Boolean {
        if (!ProxyCompatibilityChecker.isProxyOverrideSupported()) {
            // If device WebView does not support PROXY_OVERRIDE, report inactive instead of pretending routing works
            return false
        }

        val hostPort = "${proxy.host}:${proxy.port}"
        val builder = ProxyConfig.Builder()

        when (proxy.protocol) {
            ProxyProtocol.SOCKS5 -> {
                builder.addProxyRule("socks://$hostPort")
                builder.addProxyRule("socks5://$hostPort")
            }
            ProxyProtocol.HTTPS -> {
                builder.addProxyRule("https://$hostPort")
                builder.addProxyRule(hostPort)
            }
            ProxyProtocol.HTTP -> {
                // In AndroidX WebKit / Chromium:
                // hostPort without scheme prefix matches ALL URL schemes (HTTP and HTTPS).
                // Adding both hostPort and http://$hostPort ensures universal compatibility across all Android WebView versions.
                builder.addProxyRule(hostPort)
                builder.addProxyRule("http://$hostPort")
            }
        }

        // Bypass local and loopback addresses
        builder.addBypassRule("<local>")
        builder.addBypassRule("localhost")
        builder.addBypassRule("127.0.0.1")

        // No addDirect(): a failed proxy must fail closed instead of leaking the device IP

        val proxyConfig = builder.build()

        return suspendCancellableCoroutine<Boolean> { cont ->
            try {
                ProxyController.getInstance().setProxyOverride(
                    proxyConfig,
                    mainExecutor,
                    Runnable {
                        if (cont.isActive) {
                            cont.resume(true)
                        }
                    }
                )
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(false)
                }
            }
        }
    }

    private suspend fun clearProxyInternal() {
        if (!ProxyCompatibilityChecker.isProxyOverrideSupported()) {
            return
        }

        suspendCancellableCoroutine<Unit> { cont ->
            try {
                ProxyController.getInstance().clearProxyOverride(
                    mainExecutor,
                    Runnable {
                        if (cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                )
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(Unit)
                }
            }
        }
    }
}
