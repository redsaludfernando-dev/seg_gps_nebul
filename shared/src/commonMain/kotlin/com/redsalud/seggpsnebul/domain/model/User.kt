package com.redsalud.seggpsnebul.domain.model

data class User(
    val id: String,
    val dni: String,
    val phoneNumber: String,
    val fullName: String,
    val role: UserRole,
    val pin: String,
    val deviceId: String?,
    val isActive: Boolean
)

enum class UserRole(val value: String) {
    JEFE_BRIGADA("jefe_brigada"),
    NEBULIZADOR("nebulizador"),
    ANOTADOR("anotador"),
    CHOFER("chofer");

    val displayName: String
        get() = when (this) {
            JEFE_BRIGADA -> "Jefe de Brigada"
            NEBULIZADOR  -> "Nebulizador"
            ANOTADOR     -> "Anotador"
            CHOFER       -> "Chofer / Abastecedor"
        }

    companion object {
        fun fromString(value: String): UserRole =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Rol desconocido: $value")
    }
}
