package com.redsalud.seggpsnebul.domain.model

enum class AlertType(
    val value: String,
    val label: String,
    val emoji: String,
    val targetRole: String
) {
    AGUA(
        value      = "agua",
        label      = "Agua mineral",
        emoji      = "💧",
        targetRole = "chofer"
    ),
    GASOLINA(
        value      = "gasolina",
        label      = "Gasolina",
        emoji      = "⛽",
        targetRole = "chofer"
    ),
    INSUMO_QUIMICO(
        value      = "insumo_quimico",
        label      = "Insumo Químico",
        emoji      = "🧪",
        targetRole = "chofer"
    ),
    AVERIA_MAQUINA(
        value      = "averia_maquina",
        label      = "Avería de Máquina",
        emoji      = "🔧",
        targetRole = "chofer"
    ),
    TRABAJO_FINALIZADO(
        value      = "trabajo_finalizado",
        label      = "Trabajo Finalizado",
        emoji      = "✅",
        targetRole = "all"
    ),
    BROADCAST_TEXT(
        value      = "broadcast_text",
        label      = "Mensaje",
        emoji      = "📢",
        targetRole = "all"
    );

    companion object {
        /** Alert types that trigger a supply request to the Chofer. */
        val SUPPLY_TYPES = listOf(AGUA, GASOLINA, INSUMO_QUIMICO, AVERIA_MAQUINA)

        /** Alert types available to all worker roles (except Chofer sending). */
        val WORKER_ALERTS = listOf(AGUA, GASOLINA, INSUMO_QUIMICO, AVERIA_MAQUINA, TRABAJO_FINALIZADO)

        fun fromValue(value: String): AlertType? = entries.find { it.value == value }

        fun labelFor(value: String): String = fromValue(value)?.label ?: value
    }
}
