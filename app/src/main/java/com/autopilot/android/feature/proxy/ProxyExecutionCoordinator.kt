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
                applyProxyInternal(proxy)
                _activeProxy.value = proxy
            } else {
                clearProxyInternal()
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
                applyProxyInternal(proxy)
                _activeProxy.value = proxy
            } else {
                clearProxyInternal()
                _activeProxy.value = null
            }
        } catch (e: Exception) {
            // Fall back or keep existing proxy on update error
        }
    }

    private suspend fun applyProxyInternal(proxy: ProxyConfiguration) {
        if (!ProxyCompatibilityChecker.isProxyOverrideSupported()) {
            // If device WebView does not support PROXY_OVERRIDE, do not crash; record state
            return
        }

        val rule = when (proxy.protocol) {
            ProxyProtocol.SOCKS5 -> "socks://${proxy.host}:${proxy.port}"
            ProxyProtocol.HTTP -> "http://${proxy.host}:${proxy.port}"
            ProxyProtocol.HTTPS -> "https://${proxy.host}:${proxy.port}"
        }

        val proxyConfig = ProxyConfig.Builder()
            .addProxyRule(rule)
            .build()

        suspendCancellableCoroutine<Unit> { cont ->
            try {
                ProxyController.getInstance().setProxyOverride(
                    proxyConfig,
                    mainExecutor,
                    Runnable {
                        if (cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                )
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(Unit) // Continue even if listener invocation had warning
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
