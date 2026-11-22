package com.autopilot.android.feature.proxy

import android.content.Context
import androidx.webkit.WebViewFeature
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyProtocol

data class ProxyCompatibilityReport(
    val isProxyOverrideSupported: Boolean,
    val isProxyBypassSupported: Boolean,
    val isProtocolSupported: Boolean,
    val supportedProtocols: List<ProxyProtocol>,
    val warningMessage: String? = null
)

object ProxyCompatibilityChecker {

    /**
     * Checks if the device WebView supports AndroidX WebKit Proxy Override.
     */
    fun isProxyOverrideSupported(): Boolean {
        return try {
            WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Checks if the device WebView supports reverse bypass rules.
     */
    fun isProxyBypassSupported(): Boolean {
        return try {
            WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS)
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Returns the list of protocols supported by the active WebView implementation.
     */
    fun getSupportedProtocols(): List<ProxyProtocol> {
        return listOf(ProxyProtocol.HTTP, ProxyProtocol.HTTPS, ProxyProtocol.SOCKS5)
    }

    /**
     * Evaluates compatibility for a specific proxy configuration.
     */
    fun checkCompatibility(proxy: ProxyConfiguration?): ProxyCompatibilityReport {
        val overrideSupported = isProxyOverrideSupported()
        val bypassSupported = isProxyBypassSupported()
        val supportedProtocols = getSupportedProtocols()

        if (proxy == null) {
            return ProxyCompatibilityReport(
                isProxyOverrideSupported = overrideSupported,
                isProxyBypassSupported = bypassSupported,
                isProtocolSupported = true,
                supportedProtocols = supportedProtocols,
                warningMessage = null
            )
        }

        if (!overrideSupported) {
            return ProxyCompatibilityReport(
                isProxyOverrideSupported = false,
                isProxyBypassSupported = false,
                isProtocolSupported = false,
                supportedProtocols = emptyList(),
                warningMessage = "System WebView does not support PROXY_OVERRIDE. Android System WebView update may be required."
            )
        }

        val protocolSupported = supportedProtocols.contains(proxy.protocol)
        val warning = if (!protocolSupported) {
            "Protocol ${proxy.protocol.name} is not supported on this device's WebView."
        } else if (proxy.protocol == ProxyProtocol.SOCKS5) {
            "SOCKS5 support is dependent on Chromium engine version. Bypassing DNS over proxy may vary by OEM."
        } else {
            null
        }

        return ProxyCompatibilityReport(
            isProxyOverrideSupported = overrideSupported,
            isProxyBypassSupported = bypassSupported,
            isProtocolSupported = protocolSupported,
            supportedProtocols = supportedProtocols,
            warningMessage = warning
        )
    }
}
