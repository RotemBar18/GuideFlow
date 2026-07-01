package com.guideflow.backend

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Project keys identify a host application to the SDK. The raw key is shown to the
 * developer once at creation; only its SHA-256 hash is stored (CLAUDE.md → "Project Key").
 */
object ProjectKeys {
    private val random = SecureRandom()

    /** A fresh key like `gf_3f9a...` (16 random bytes, hex). Not a secret. */
    fun generate(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return "gf_" + bytes.toHex()
    }

    fun hash(rawKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(rawKey.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
