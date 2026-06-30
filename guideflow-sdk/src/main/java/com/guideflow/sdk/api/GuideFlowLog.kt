package com.guideflow.sdk.api

import android.util.Log

/**
 * Lightweight Logcat logger gated by [GuideFlowConfig.debugLogging]. Off by default,
 * so a release host app stays quiet. Messages aim to be actionable (they name the
 * flow key / anchor key and what to check).
 */
internal object GuideFlowLog {
    const val TAG = "GuideFlow"

    @Volatile
    var enabled = false

    fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun w(message: String) {
        if (enabled) Log.w(TAG, message)
    }
}
