package com.guideflow.backend

import io.ktor.http.HttpStatusCode

/**
 * An expected, client-facing error. Mapped to an [com.guideflow.shared.ApiError]
 * JSON body with the given HTTP [status] by the StatusPages plugin.
 */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    message: String,
) : RuntimeException(message)
