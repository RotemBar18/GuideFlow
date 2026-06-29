package com.guideflow.sdk.api

/**
 * Host-app configuration passed to [GuideFlow.initialize].
 *
 * [baseUrl] must point at the Ktor backend. On a physical device use the dev
 * machine's LAN IP (e.g. `http://192.168.1.20:8080`), not `localhost`.
 */
data class GuideFlowConfig(
    val baseUrl: String,
    val enableAnalytics: Boolean = true,
    val enableOfflineCache: Boolean = true,
    val debugLogging: Boolean = false,
)
