package com.guideflow.sdk.config

import com.guideflow.sdk.api.GuideFlowException
import com.guideflow.shared.TutorialConfig
import com.guideflow.shared.TutorialFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the active config. Refreshes from the [source],
 * persists valid results to the [cache], and — crucially — keeps the previous
 * config when a refresh fails (CLAUDE.md → "Error Handling").
 */
internal class ConfigRepository(
    private val source: ConfigSource,
    private val cache: ConfigCache,
    private val enableOfflineCache: Boolean,
) {
    private val _config = MutableStateFlow<TutorialConfig?>(null)
    val config: StateFlow<TutorialConfig?> = _config

    fun currentFlows(): List<TutorialFlow> = _config.value?.flows ?: emptyList()

    /** Seed from cache once, if offline cache is enabled and nothing is loaded yet. */
    suspend fun loadCached() {
        if (!enableOfflineCache) return
        if (_config.value == null) {
            _config.value = cache.load()
        }
    }

    suspend fun refresh(projectKey: String): Result<Unit> {
        return when (val result = source.fetch(projectKey, _config.value?.configVersion)) {
            is ConfigResult.Success -> {
                _config.value = result.config
                if (enableOfflineCache) cache.save(result.config)
                Result.success(Unit)
            }
            // Server says nothing changed — keep what we have.
            ConfigResult.NotModified -> Result.success(Unit)
            // Refresh failed — keep the previous (cached) config, report the error.
            is ConfigResult.Failure -> Result.failure(GuideFlowException(result.error))
        }
    }
}
