package com.guideflow.backend.auth

/**
 * Resolves the authenticated portal-user id (Firebase UID) for a request.
 *
 * Two implementations: [DevAuthProvider] (local dev, no Firebase) and
 * [FirebaseAuthProvider] (verifies a real Firebase ID token).
 */
interface AuthProvider {
    /**
     * Returns the owner uid for the given `Authorization` header value, or throws
     * [com.guideflow.backend.ApiException] (401) if it cannot be authenticated.
     */
    fun requireUid(authorizationHeader: String?): String
}

/** Accepts any request and returns a fixed dev uid. Used when Firebase isn't configured. */
class DevAuthProvider(private val devUid: String = "dev-owner") : AuthProvider {
    override fun requireUid(authorizationHeader: String?): String = devUid
}
