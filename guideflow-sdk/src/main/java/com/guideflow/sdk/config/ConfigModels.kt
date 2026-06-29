package com.guideflow.sdk.config

import com.guideflow.sdk.api.GuideFlowError
import com.guideflow.shared.TutorialConfig

/** Outcome of a config fetch. */
internal sealed class ConfigResult {
    data class Success(val config: TutorialConfig) : ConfigResult()
    data object NotModified : ConfigResult()
    data class Failure(val error: GuideFlowError) : ConfigResult()
}

/** Source of remote config (the backend). Abstracted so the repository is testable. */
internal interface ConfigSource {
    suspend fun fetch(projectKey: String, currentVersion: Int?): ConfigResult
}

/** Local persistence for the last-known-good config. */
internal interface ConfigCache {
    suspend fun load(): TutorialConfig?
    suspend fun save(config: TutorialConfig)
}
