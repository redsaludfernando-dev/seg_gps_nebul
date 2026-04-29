package com.redsalud.seggpsnebul.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinHasherTest {

    @Test
    fun hashIsDeterministic() {
        assertEquals(PinHasher.hash("1234"), PinHasher.hash("1234"))
    }

    @Test
    fun differentPinsDifferentHashes() {
        assertTrue(PinHasher.hash("1234") != PinHasher.hash("4321"))
    }

    @Test
    fun verifyAcceptsCorrectPin() {
        val h = PinHasher.hash("0000")
        assertTrue(PinHasher.verify("0000", h))
    }

    @Test
    fun verifyRejectsWrongPin() {
        val h = PinHasher.hash("0000")
        assertTrue(!PinHasher.verify("9999", h))
    }

    // Vector conocido: SHA-256("seg_gps_nebul_v1:1234") via openssl/sha256sum.
    // Si este test falla, el SHA-256 puro Kotlin no coincide con el estandar
    // y los PINs ya guardados desde Android no validaran al hashear desde Web.
    @Test
    fun knownVectorPin1234() {
        val expected = "bfdc7188579ade1c9e7dc3cb9558ab0dae824ce674c239b753453a0a22de3496"
        assertEquals(expected, PinHasher.hash("1234"))
    }
}
