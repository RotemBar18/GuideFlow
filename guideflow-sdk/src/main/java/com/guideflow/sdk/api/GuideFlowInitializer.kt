package com.guideflow.sdk.api

import android.content.Context
import android.content.pm.PackageManager
import androidx.startup.Initializer

/**
 * Optional auto-initialization via AndroidX App Startup.
 *
 * If the host app declares a project key in its manifest, GuideFlow initializes
 * itself at app startup, so the app does not need to call [GuideFlow.initialize].
 * Add to the app's `AndroidManifest.xml`, inside `<application>`:
 *
 * ```xml
 * <meta-data android:name="com.guideflow.PROJECT_KEY"   android:value="gf_your_key" />
 * <!-- optional -->
 * <meta-data android:name="com.guideflow.BASE_URL"      android:value="https://your.backend" />
 * <meta-data android:name="com.guideflow.DEBUG_LOGGING" android:value="true" />
 * ```
 *
 * When no `PROJECT_KEY` meta-data is present this does nothing, so apps that call
 * [GuideFlow.initialize] manually are unaffected.
 */
class GuideFlowInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val metaData = runCatching {
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData
        }.getOrNull() ?: return

        val projectKey = metaData.getString(KEY_PROJECT_KEY)?.trim().orEmpty()
        if (projectKey.isEmpty()) return // no key declared -> app initializes manually (or not at all)

        val baseUrl = metaData.getString(KEY_BASE_URL)?.trim()?.takeIf { it.isNotEmpty() }
            ?: GuideFlowConfig.DEFAULT_BASE_URL
        val debug = metaData.getBoolean(KEY_DEBUG_LOGGING, false)

        GuideFlow.initialize(
            context = context.applicationContext,
            projectKey = projectKey,
            config = GuideFlowConfig(baseUrl = baseUrl, debugLogging = debug),
        )
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    private companion object {
        const val KEY_PROJECT_KEY = "com.guideflow.PROJECT_KEY"
        const val KEY_BASE_URL = "com.guideflow.BASE_URL"
        const val KEY_DEBUG_LOGGING = "com.guideflow.DEBUG_LOGGING"
    }
}
