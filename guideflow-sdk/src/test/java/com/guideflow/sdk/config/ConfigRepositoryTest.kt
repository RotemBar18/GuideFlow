package com.guideflow.sdk.config

import com.guideflow.shared.FlowStatus
import com.guideflow.shared.TutorialConfig
import com.guideflow.shared.TutorialFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRepositoryTest {

    private fun config(version: Int, vararg flowKeys: String) = TutorialConfig(
        projectId = "p",
        configVersion = version,
        flows = flowKeys.map { TutorialFlow("id_$it", it, it, FlowStatus.PUBLISHED) },
    )

    private class FakeSource(var result: ConfigResult) : ConfigSource {
        var lastVersion: Int? = null
        override suspend fun fetch(projectKey: String, currentVersion: Int?): ConfigResult {
            lastVersion = currentVersion
            return result
        }
    }

    private class FakeCache(var stored: TutorialConfig? = null) : ConfigCache {
        var saveCount = 0
        override suspend fun load(): TutorialConfig? = stored
        override suspend fun save(config: TutorialConfig) {
            stored = config
            saveCount++
        }
    }

    @Test
    fun refreshSuccess_updatesConfigAndCaches() = runBlocking {
        val source = FakeSource(ConfigResult.Success(config(2, "tour")))
        val cache = FakeCache()
        val repo = ConfigRepository(source, cache, enableOfflineCache = true)

        val result = repo.refresh("key")

        assertTrue(result.isSuccess)
        assertEquals(2, repo.config.value?.configVersion)
        assertEquals("tour", repo.currentFlows().single().flowKey)
        assertEquals(config(2, "tour"), cache.stored)
    }

    @Test
    fun refreshFailure_keepsPreviousConfig() = runBlocking {
        val cache = FakeCache(stored = config(1, "tour"))
        val source = FakeSource(ConfigResult.Success(config(1, "tour")))
        val repo = ConfigRepository(source, cache, enableOfflineCache = true)
        repo.loadCached() // seed v1 from cache

        source.result = ConfigResult.Failure(com.guideflow.sdk.api.GuideFlowError.NetworkError("offline"))
        val result = repo.refresh("key")

        assertTrue(result.isFailure)
        assertEquals(1, repo.config.value?.configVersion) // unchanged
    }

    @Test
    fun refreshNotModified_keepsCurrentAndDoesNotSave() = runBlocking {
        val cache = FakeCache(stored = config(3, "tour"))
        val source = FakeSource(ConfigResult.NotModified)
        val repo = ConfigRepository(source, cache, enableOfflineCache = true)
        repo.loadCached()
        cache.saveCount = 0

        val result = repo.refresh("key")

        assertTrue(result.isSuccess)
        assertEquals(3, source.lastVersion) // sent current version for comparison
        assertEquals(3, repo.config.value?.configVersion)
        assertEquals(0, cache.saveCount)
    }

    @Test
    fun loadCached_disabled_doesNotLoad() = runBlocking {
        val cache = FakeCache(stored = config(1, "tour"))
        val repo = ConfigRepository(FakeSource(ConfigResult.NotModified), cache, enableOfflineCache = false)

        repo.loadCached()

        assertNull(repo.config.value)
    }
}
