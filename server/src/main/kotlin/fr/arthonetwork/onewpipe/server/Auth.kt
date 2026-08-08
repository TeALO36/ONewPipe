package fr.arthonetwork.onewpipe.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Password hashing: PBKDF2-like salted SHA-256 iterations.
 * Good enough for a self-hosted MVP; swap for bcrypt/argon2 in a real deployment.
 */
object Passwords {
    private const val ITERATIONS = 10_000

    fun hash(password: String, salt: String): String {
        var hash = salt + password
        repeat(ITERATIONS) {
            hash = MessageDigest.getInstance("SHA-256")
                .digest(hash.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
        return hash
    }

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/** Minimal HMAC-SHA256 JWT (header.payload.signature), no external dependency. */
object Jwt {
    private val json = Json { ignoreUnknownKeys = true }
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    @Serializable
    data class Claims(
        val sub: String,
        val exp: Long
    )

    fun sign(secret: String, username: String, ttlMillis: Long = 30L * 24 * 3600 * 1000): String {
        val header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".toByteArray())
        val payload = encoder.encodeToString(
            json.encodeToString(Claims(username, System.currentTimeMillis() + ttlMillis)).toByteArray()
        )
        val signature = hmac(secret, "$header.$payload")
        return "$header.$payload.$signature"
    }

    fun verify(secret: String, token: String): String? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        val expected = hmac(secret, "${parts[0]}.${parts[1]}")
        if (!MessageDigest.isEqual(expected.toByteArray(), parts[2].toByteArray())) return null
        return try {
            val claims = json.decodeFromString<Claims>(String(decoder.decode(parts[1])))
            if (claims.exp < System.currentTimeMillis()) null else claims.sub
        } catch (e: Exception) {
            null
        }
    }

    private fun hmac(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return encoder.encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }
}
