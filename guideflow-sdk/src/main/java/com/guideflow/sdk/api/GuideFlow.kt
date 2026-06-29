package com.guideflow.sdk.api

import android.content.Context
import com.guideflow.sdk.analytics.AnalyticsManager
import com.guideflow.sdk.anchor.AnchorManager
import com.guideflow.sdk.config.ConfigClient
import com.guideflow.sdk.config.ConfigRepository
import com.guideflow.sdk.config.ConfigStorage
import com.guideflow.sdk.flow.FlowCoordinator
import com.guideflow.sdk.flow.FlowValidator
import com.guideflow.shared.TutorialFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Public entry point for the GuideFlow SDK.
 *
 * Typical use: [initialize] with a project key + [GuideFlowConfig], optionally
 * [setUser], then [startFlow]. Flows come from remote config once loaded; if the
 * SDK is not initialized (or remote config is empty/unreachable) it falls back to
 * flows supplied via [loadLocalFlows].
 */
object GuideFlow {
    const val SDK_VERSION: String = "1.0.0"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var listener: GuideFlowListener? = null
    private var localFlows: List<TutorialFlow> = emptyList()

    private var projectKey: String? = null
    private var userHash: String? = null
    private var configClient: ConfigClient? = null
    private var storage: ConfigStorage? = null
    private var repository: ConfigRepository? = null
    private var analytics: AnalyticsManager? = null

    // Engine internals shared by the Compose layer (host, anchor modifier, overlays).
    internal val anchors: AnchorManager = AnchorManager()
    internal val coordinator: FlowCoordinator = FlowCoordinator(
        listenerProvider = { listener },
        recordEvent = { type, flowId, stepId -> analytics?.record(flowId, stepId, type) },
    )

    /**
     * Initialize the SDK: load any cached config immediately, then refresh from the
     * backend in the background (keeping the cache if the refresh fails).
     */
    fun initialize(context: Context, projectKey: String, config: GuideFlowConfig) {
        this.projectKey = projectKey

        configClient?.close()
        val client = ConfigClient(config.baseUrl)
        val cache = ConfigStorage(context)
        val repo = ConfigRepository(client, cache, config.enableOfflineCache)

        configClient = client
        storage = cache
        repository = repo
        analytics = if (config.enableAnalytics) {
            AnalyticsManager(context, config.baseUrl, projectKey) { userHash }
        } else {
            null
        }

        scope.launch {
            repo.loadCached()
            repo.refresh(projectKey)
        }
    }

    /** Associate tutorial state/analytics with a host-app user. Hashed before use. */
    fun setUser(userId: String?) {
        val hash = userId?.let(::hashUserId)
        userHash = hash
        storage?.let { cache -> scope.launch { cache.saveUserHash(hash) } }
    }

    /** Register a host-app listener for tutorial lifecycle events. */
    fun setListener(listener: GuideFlowListener?) {
        this.listener = listener
    }

    /**
     * Load flows locally instead of from the backend. Useful for tests and for the
     * offline fallback in the demo; superseded by remote config once it loads.
     */
    fun loadLocalFlows(flows: List<TutorialFlow>) {
        this.localFlows = flows
    }

    /** Fetch the latest published config from the backend. */
    suspend fun refreshConfig(): Result<Unit> {
        val repo = repository ?: return Result.failure(GuideFlowException(GuideFlowError.NotInitialized))
        val key = projectKey ?: return Result.failure(GuideFlowException(GuideFlowError.NotInitialized))
        return repo.refresh(key)
    }

    /**
     * Start the flow with the given [flowKey]. Prefers remote config flows, falling
     * back to [loadLocalFlows]. Errors are also reported via [setListener].
     */
    fun startFlow(flowKey: String): Result<Unit> {
        val remote = repository?.currentFlows().orEmpty()
        val flows = remote.ifEmpty { localFlows }

        val flow = flows.firstOrNull { it.flowKey == flowKey }
        if (flow == null) {
            val error = GuideFlowError.FlowNotFound(flowKey)
            listener?.onError(error)
            return Result.failure(GuideFlowException(error))
        }
        FlowValidator.validate(flow)?.let { error ->
            listener?.onError(error)
            return Result.failure(GuideFlowException(error))
        }
        return coordinator.start(flow)
    }

    /** Stop the active flow, if any. */
    fun stopFlow(reason: StopReason = StopReason.MANUAL) {
        coordinator.stop(reason)
    }

    /** Upload queued analytics now. Returns the number of events the server accepted. */
    suspend fun flush(): Result<Int> = analytics?.flush() ?: Result.success(0)

    private fun hashUserId(userId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(userId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
