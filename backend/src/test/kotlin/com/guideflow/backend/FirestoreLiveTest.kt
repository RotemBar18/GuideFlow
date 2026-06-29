package com.guideflow.backend

import com.guideflow.backend.auth.FirebaseAuthProvider
import com.guideflow.shared.CreateStepRequest
import com.guideflow.shared.StepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

/**
 * Live round-trip against the real Firestore. Skipped unless Firebase credentials
 * are configured (GUIDEFLOW_FIREBASE_CREDENTIALS / GOOGLE_APPLICATION_CREDENTIALS),
 * so normal test runs are unaffected.
 *
 * Run it with:
 *   GUIDEFLOW_FIREBASE_CREDENTIALS="C:/.../guideflow_key.json" ./gradlew :backend:test --no-daemon
 */
class FirestoreLiveTest {

    @Test
    fun firestore_createPublishFetch_roundTrip() {
        val auth = FirebaseAuthProvider.initIfConfigured()
        assumeNotNull(auth) // no creds -> skip

        val store = FirestoreStore()

        val (project, rawKey) = store.createProject("live-test-owner", "Live Test Project")
        println("[FirestoreLiveTest] created ${project.projectId} (key $rawKey) — check the 'projects' collection")

        val fetched = store.getProject(project.projectId)
        assertNotNull("project should be readable back from Firestore", fetched)
        assertEquals("live-test-owner", fetched!!.ownerUid)
        assertTrue(rawKey.startsWith("gf_"))

        // Exercise the full publish -> compiled config path.
        val flow = store.createFlow(project.projectId, "live_tour", "Live Tour")
        store.addStep(flow.flowId, CreateStepRequest(StepType.MODAL, title = "Hi", body = "From Firestore"))
        val published = store.publishFlow(flow.flowId)
        assertEquals(com.guideflow.shared.FlowStatus.PUBLISHED, published.status)

        val config = store.getPublishedConfig(project.projectId)
        assertNotNull("published config should exist", config)
        assertEquals(1, config!!.configVersion)
        assertEquals("live_tour", config.flows.single().flowKey)
        println("[FirestoreLiveTest] OK — published config v${config.configVersion} with flow '${config.flows.single().flowKey}'")
    }
}
