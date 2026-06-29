package com.guideflow.backend.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.guideflow.backend.ApiException
import io.ktor.http.HttpStatusCode
import java.io.FileInputStream

/**
 * Verifies a Firebase ID token from `Authorization: Bearer <token>` and returns the
 * Firebase UID. The host app initializes Firebase once via [initIfConfigured].
 */
class FirebaseAuthProvider private constructor() : AuthProvider {

    override fun requireUid(authorizationHeader: String?): String {
        val header = authorizationHeader
            ?: unauthorized("missing_token", "Missing Authorization header")
        if (!header.startsWith(BEARER_PREFIX)) {
            unauthorized("missing_token", "Authorization header must be a Bearer token")
        }
        val token = header.removePrefix(BEARER_PREFIX).trim()
        if (token.isEmpty()) unauthorized("missing_token", "Empty Bearer token")

        return try {
            FirebaseAuth.getInstance().verifyIdToken(token).uid
        } catch (e: Exception) {
            unauthorized("invalid_token", "Invalid or expired Firebase ID token")
        }
    }

    private fun unauthorized(code: String, message: String): Nothing =
        throw ApiException(HttpStatusCode.Unauthorized, code, message)

    companion object {
        private const val BEARER_PREFIX = "Bearer "

        /**
         * Initialize Firebase from a service-account JSON whose path is given by
         * `GUIDEFLOW_FIREBASE_CREDENTIALS` (or `GOOGLE_APPLICATION_CREDENTIALS`).
         * Returns null when neither is set, so the caller can fall back to dev auth.
         */
        fun initIfConfigured(): FirebaseAuthProvider? {
            val path = System.getenv("GUIDEFLOW_FIREBASE_CREDENTIALS")
                ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
            // On Cloud Run (K_SERVICE is set) use Application Default Credentials — no key file.
            val onCloudRun = System.getenv("K_SERVICE") != null
            if (path == null && !onCloudRun) return null

            if (FirebaseApp.getApps().isEmpty()) {
                val builder = FirebaseOptions.builder()
                if (path != null) {
                    // Key file carries its own project id.
                    builder.setCredentials(FileInputStream(path).use { GoogleCredentials.fromStream(it) })
                } else {
                    // ADC has no project id attached — Firestore needs it set explicitly.
                    builder.setCredentials(GoogleCredentials.getApplicationDefault())
                        .setProjectId(System.getenv("GOOGLE_CLOUD_PROJECT") ?: "guideflow-af26c")
                }
                FirebaseApp.initializeApp(builder.build())
            }
            return FirebaseAuthProvider()
        }
    }
}
