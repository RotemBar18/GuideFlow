package com.guideflow.portal

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Sign-In via Credential Manager, exchanged for a Firebase session.
 * [idToken] returns the Firebase ID token the backend verifies as a Bearer.
 */
class PortalAuth {

    private val auth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun signInWithGoogle(activityContext: Context): Result<Unit> = try {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val credential = response.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            auth.signInWithCredential(firebaseCredential).await()
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Unexpected credential type"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Current Firebase ID token (refreshed if expired), or null if signed out. */
    suspend fun idToken(): String? =
        auth.currentUser?.getIdToken(false)?.await()?.token

    fun signOut() = auth.signOut()

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }

    companion object {
        // Web client ID (OAuth) for project guideflow-af26c — public, not a secret.
        const val WEB_CLIENT_ID =
            "794711970205-nhbnf6f3p3s3lh58fmhnims07am81r71.apps.googleusercontent.com"
    }
}
