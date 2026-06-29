package com.guideflow.backend

import com.guideflow.backend.auth.AuthProvider
import com.guideflow.backend.auth.DevAuthProvider
import com.guideflow.backend.auth.FirebaseAuthProvider
import com.guideflow.shared.ApiError
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

fun main() {
    // Cloud Run provides $PORT; locally defaults to 8080. host 0.0.0.0 for LAN/container access.
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

/**
 * Wires plugins and routes. [store]/[auth] are injectable for tests; otherwise they
 * are resolved from the environment: if Firebase credentials are configured we use
 * Firebase auth + Firestore, else dev auth + an in-memory store.
 */
fun Application.module(
    store: GuideFlowStore? = null,
    auth: AuthProvider? = null,
) {
    val firebaseAuth = FirebaseAuthProvider.initIfConfigured()
    val resolvedAuth = auth ?: firebaseAuth ?: DevAuthProvider()
    val resolvedStore = store ?: if (firebaseAuth != null) FirestoreStore() else InMemoryStore()

    log.info(
        "GuideFlow backend starting — auth: {}, store: {}",
        resolvedAuth::class.simpleName,
        resolvedStore::class.simpleName,
    )
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ApiError(cause.code, cause.message ?: cause.code))
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError("internal_error", cause.message ?: "Unexpected error"),
            )
        }
    }
    configureRouting(resolvedStore, resolvedAuth)
}
