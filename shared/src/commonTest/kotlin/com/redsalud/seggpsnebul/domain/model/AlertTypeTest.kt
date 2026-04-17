package com.redsalud.seggpsnebul.domain.model

import kotlin.test.*

class AlertTypeTest {

    @Test
    fun fromValue_validValues_returnsCorrectType() {
        assertEquals(AlertType.AGUA, AlertType.fromValue("agua"))
        assertEquals(AlertType.GASOLINA, AlertType.fromValue("gasolina"))
        assertEquals(AlertType.INSUMO_QUIMICO, AlertType.fromValue("insumo_quimico"))
        assertEquals(AlertType.AVERIA_MAQUINA, AlertType.fromValue("averia_maquina"))
        assertEquals(AlertType.TRABAJO_FINALIZADO, AlertType.fromValue("trabajo_finalizado"))
        assertEquals(AlertType.BROADCAST_TEXT, AlertType.fromValue("broadcast_text"))
    }

    @Test
    fun fromValue_invalidValue_returnsNull() {
        assertNull(AlertType.fromValue("unknown"))
        assertNull(AlertType.fromValue(""))
        assertNull(AlertType.fromValue("AGUA")) // case-sensitive
    }

    @Test
    fun labelFor_validValue_returnsLabel() {
        assertEquals("Agua mineral", AlertType.labelFor("agua"))
        assertEquals("Gasolina", AlertType.labelFor("gasolina"))
        assertEquals("Insumo Qu\u00edmico", AlertType.labelFor("insumo_quimico"))
        assertEquals("Aver\u00eda de M\u00e1quina", AlertType.labelFor("averia_maquina"))
        assertEquals("Trabajo Finalizado", AlertType.labelFor("trabajo_finalizado"))
        assertEquals("Mensaje", AlertType.labelFor("broadcast_text"))
    }

    @Test
    fun labelFor_unknownValue_returnsValueItself() {
        assertEquals("desconocido", AlertType.labelFor("desconocido"))
    }

    @Test
    fun supplyTypes_containsFourChoferAlerts() {
        val expected = listOf(
            AlertType.AGUA,
            AlertType.GASOLINA,
            AlertType.INSUMO_QUIMICO,
            AlertType.AVERIA_MAQUINA
        )
        assertEquals(expected, AlertType.SUPPLY_TYPES)
    }

    @Test
    fun supplyTypes_allTargetChofer() {
        for (type in AlertType.SUPPLY_TYPES) {
            assertEquals("chofer", type.targetRole)
        }
    }

    @Test
    fun workerAlerts_containsFiveTypes() {
        assertEquals(5, AlertType.WORKER_ALERTS.size)
        assertTrue(AlertType.WORKER_ALERTS.contains(AlertType.TRABAJO_FINALIZADO))
        assertFalse(AlertType.WORKER_ALERTS.contains(AlertType.BROADCAST_TEXT))
    }

    @Test
    fun broadcastTypes_targetAll() {
        assertEquals("all", AlertType.TRABAJO_FINALIZADO.targetRole)
        assertEquals("all", AlertType.BROADCAST_TEXT.targetRole)
    }

    @Test
    fun entries_containsSixTypes() {
        assertEquals(6, AlertType.entries.size)
    }

    @Test
    fun roundTrip_valueToFromValue() {
        for (type in AlertType.entries) {
            assertEquals(type, AlertType.fromValue(type.value))
        }
    }
}
