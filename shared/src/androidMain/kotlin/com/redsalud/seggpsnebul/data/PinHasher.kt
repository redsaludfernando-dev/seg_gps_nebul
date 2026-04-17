package com.redsalud.seggpsnebul.data

import java.security.MessageDigest

object PinHasher {
    private const val SALT = "seg_gps_nebul_v1"

    fun hash(pin: String): String {
        val salted = "$SALT:$pin"
        val digest = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, hashedPin: String): Boolean = hash(pin) == hashedPin
}
