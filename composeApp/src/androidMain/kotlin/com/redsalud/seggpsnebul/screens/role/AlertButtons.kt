package com.redsalud.seggpsnebul.screens.role

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.domain.model.AlertType

/**
 * 2×2 grid of supply alert buttons + 1 centered "Trabajo Finalizado" button.
 * Each button shows a confirmation dialog before sending.
 */
@Composable
fun AlertButtons(
    modifier: Modifier = Modifier,
    types: List<AlertType> = AlertType.WORKER_ALERTS,
    onSend: (AlertType) -> Unit
) {
    var pending by remember { mutableStateOf<AlertType?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val grid  = types.filter { it != AlertType.TRABAJO_FINALIZADO }
        val extra = types.filter { it == AlertType.TRABAJO_FINALIZADO }

        // 2-column grid for supply alerts
        grid.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { type ->
                    AlertChip(type, Modifier.weight(1f)) { pending = type }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // "Trabajo Finalizado" centered below
        extra.forEach { type ->
            AlertChip(
                type     = type,
                modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.6f),
                onClick  = { pending = type }
            )
        }
    }

    pending?.let { type ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title   = { Text("Confirmar alerta") },
            text    = { Text("¿Enviar \"${type.emoji} ${type.label}\"?") },
            confirmButton = {
                TextButton(onClick = { onSend(type); pending = null }) { Text("Enviar") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun AlertChip(type: AlertType, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text("${type.emoji} ${type.label}", maxLines = 1)
    }
}
