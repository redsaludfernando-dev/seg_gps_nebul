package com.redsalud.seggpsnebul.domain.model

import kotlin.test.*

class UserRoleTest {

    @Test
    fun fromString_validValues_returnsCorrectRole() {
        assertEquals(UserRole.JEFE_BRIGADA, UserRole.fromString("jefe_brigada"))
        assertEquals(UserRole.NEBULIZADOR, UserRole.fromString("nebulizador"))
        assertEquals(UserRole.ANOTADOR, UserRole.fromString("anotador"))
        assertEquals(UserRole.CHOFER, UserRole.fromString("chofer"))
    }

    @Test
    fun fromString_invalidValue_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            UserRole.fromString("admin")
        }
        assertFailsWith<IllegalArgumentException> {
            UserRole.fromString("")
        }
        assertFailsWith<IllegalArgumentException> {
            UserRole.fromString("JEFE_BRIGADA") // case-sensitive
        }
    }

    @Test
    fun value_matchesExpectedString() {
        assertEquals("jefe_brigada", UserRole.JEFE_BRIGADA.value)
        assertEquals("nebulizador", UserRole.NEBULIZADOR.value)
        assertEquals("anotador", UserRole.ANOTADOR.value)
        assertEquals("chofer", UserRole.CHOFER.value)
    }

    @Test
    fun displayName_returnsHumanReadableName() {
        assertEquals("Jefe de Brigada", UserRole.JEFE_BRIGADA.displayName)
        assertEquals("Nebulizador", UserRole.NEBULIZADOR.displayName)
        assertEquals("Anotador", UserRole.ANOTADOR.displayName)
        assertEquals("Chofer / Abastecedor", UserRole.CHOFER.displayName)
    }

    @Test
    fun entries_containsAllFourRoles() {
        assertEquals(4, UserRole.entries.size)
    }

    @Test
    fun roundTrip_valueToFromString() {
        for (role in UserRole.entries) {
            assertEquals(role, UserRole.fromString(role.value))
        }
    }
}
