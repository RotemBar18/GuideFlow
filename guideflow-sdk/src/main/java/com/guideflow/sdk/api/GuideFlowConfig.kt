package com.guideflow.sdk.api

/**
 * Host-app configuration passed to [GuideFlow.initialize].
 *
 * [baseUrl] defaults to the hosted GuideFlow backend, so most apps never set it.
 * Override it only to run your own backend; on a physical device use the dev
 * machine's LAN IP (e.g. `http://192.168.1.20:8080`), not `localhost`.
 */
data class GuideFlowConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val enableAnalytics: Boolean = true,
    val enableOfflineCache: Boolean = true,
    val debugLogging: Boolean = false,
) {
    companion object {
        /** The hosted backend on Google Cloud Run. */
        const val DEFAULT_BASE_URL = "https://guideflow-backend-794711970205.me-west1.run.app"
    }
}
