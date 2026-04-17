package com.redsalud.seggpsnebul.screens.role

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.AppContainer

@Composable
fun SyncStatusIndicator(modifier: Modifier = Modifier) {
    val isOnline   by AppContainer.connectivityObserver.isOnline.collectAsState()
    val realtimeUp by AppContainer.realtimeRepository.isConnected.collectAsState()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            !isOnline   -> SyncChip("Sin red",
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer)
            !realtimeUp -> SyncChip("RT off",
                Color(0xFFFFF9C4), Color(0xFF5D4037))
        }
    }
}

@Composable
private fun SyncChip(label: String, containerColor: Color, contentColor: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = containerColor, contentColor = contentColor) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}
