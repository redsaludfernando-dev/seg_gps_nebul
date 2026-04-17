package com.redsalud.seggpsnebul.domain.model

import kotlin.test.*

class UserTest {

    @Test
    fun dataClass_equalityByValues() {
        val user1 = User("id1", "12345678", "999888777", "Juan", UserRole.NEBULIZADOR, "hash", "dev1", true)
        val user2 = User("id1", "12345678", "999888777", "Juan", UserRole.NEBULIZADOR, "hash", "dev1", true)
        assertEquals(user1, user2)
    }

    @Test
    fun dataClass_inequalityOnDifferentField() {
        val user1 = User("id1", "12345678", "999888777", "Juan", UserRole.NEBULIZADOR, "hash", "dev1", true)
        val user2 = user1.copy(isActive = false)
        assertNotEquals(user1, user2)
    }

    @Test
    fun deviceId_canBeNull() {
        val user = User("id1", "12345678", "999888777", "Juan", UserRole.CHOFER, "hash", null, true)
        assertNull(user.deviceId)
    }

    @Test
    fun copy_changesOnlySpecifiedFields() {
        val original = User("id1", "12345678", "999888777", "Juan", UserRole.JEFE_BRIGADA, "hash", "dev1", true)
        val updated = original.copy(role = UserRole.ANOTADOR, fullName = "Juan Carlos")
        assertEquals("id1", updated.id)
        assertEquals("12345678", updated.dni)
        assertEquals(UserRole.ANOTADOR, updated.role)
        assertEquals("Juan Carlos", updated.fullName)
    }
}
