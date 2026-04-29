package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.data.remote.AssignmentDto
import com.redsalud.seggpsnebul.data.remote.SessionAdminDto
import com.redsalud.seggpsnebul.data.remote.UserAdminDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsignacionesTab(vm: AdminViewModel) {
    val sessions    by vm.sessions.collectAsState()
    val users       by vm.users.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val zonas       by vm.zonas.collectAsState()

    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var sessionMenuOpen   by remember { mutableStateOf(false) }
    var creating          by remember { mutableStateOf(false) }
    var editing           by remember { mutableStateOf<AssignmentDto?>(null) }
    var deleting          by remember { mutableStateOf<AssignmentDto?>(null) }

    LaunchedEffect(sessions) {
        if (selectedSessionId == null && sessions.isNotEmpty()) {
            selectedSessionId = sessions.firstOrNull { it.is_active }?.id ?: sessions.first().id
        }
    }
    LaunchedEffect(selectedSessionId) {
        selectedSessionId?.let { vm.loadAssignments(it) }
    }

    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId }
    val nebulizadores   = users.filter { it.role == "nebulizador" || it.role == "anotador" }
    val usersById       = remember(users) { users.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        // Selector de jornada
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded         = sessionMenuOpen,
                onExpandedChange = { sessionMenuOpen = !sessionMenuOpen },
                modifier         = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value         = selectedSession?.let { "${if (it.is_active) "🟢 " else ""}${it.name}" } ?: "Selecciona jornada",
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Jornada") },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(sessionMenuOpen) },
                    modifier      = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded         = sessionMenuOpen,
                    onDismissRequest = { sessionMenuOpen = false }
                ) {
                    sessions.forEach { s ->
                        DropdownMenuItem(
                            text    = { Text("${if (s.is_active) "🟢 " else ""}${s.name}") },
                            onClick = {
                                selectedSessionId = s.id
                                sessionMenuOpen = false
                            }
                        )
                    }
                }
            }
            Button(
                onClick = { creating = true },
                enabled = selectedSessionId != null && nebulizadores.isNotEmpty()
            ) { Text("+ Asignar") }
        }

        // Resumen
        Text(
            "${assignments.size} manzanas asignadas en esta jornada",
            style    = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (selectedSessionId == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Selecciona una jornada para ver/crear asignaciones.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else if (nebulizadores.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Crea trabajadores con rol nebulizador o anotador para asignar manzanas.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else if (assignments.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Sin asignaciones. Pulsa + Asignar.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(assignments, key = { it.id }) { a ->
                    AssignmentCard(
                        a         = a,
                        usersById = usersById,
                        onEdit    = { editing = a },
                        onDelete  = { deleting = a }
                    )
                }
            }
        }
    }

    if (creating && selectedSessionId != null) {
        AssignmentDialog(
            title       = "Nueva asignación",
            initialUser = nebulizadores.firstOrNull()?.id,
            initialBlock = "",
            initialNotes = "",
            users       = nebulizadores,
            zonaNames   = zonas.map { it.nombre },
            onDismiss   = { creating = false },
            onSubmit    = { userId, blockName, notes ->
                // assignedBy: usar el primer user disponible (no hay un user.id de admin real)
                val adminProxy = users.firstOrNull()?.id ?: userId
                vm.createAssignment(selectedSessionId!!, userId, adminProxy, blockName, notes)
                creating = false
            }
        )
    }

    editing?.let { a ->
        AssignmentDialog(
            title       = "Editar asignación",
            initialUser = a.assigned_to,
            initialBlock = a.block_name,
            initialNotes = a.notes ?: "",
            users       = nebulizadores,
            zonaNames   = zonas.map { it.nombre },
            onDismiss   = { editing = null },
            onSubmit    = { userId, blockName, notes ->
                vm.updateAssignment(a.id, a.session_id, userId, blockName, notes)
                editing = null
            }
        )
    }

    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar asignación") },
            text  = { Text("¿Eliminar la asignación de '${a.block_name}'?") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteAssignment(a.id, a.session_id); deleting = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AssignmentCard(
    a: AssignmentDto,
    usersById: Map<String, UserAdminDto>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val worker = usersById[a.assigned_to]
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.block_name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        worker?.let { "${it.full_name} · ${roleLabel(it.role)}" } ?: a.assigned_to.take(8),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(fmtTs(a.assigned_at), style = MaterialTheme.typography.labelSmall)
                }
            }
            a.notes?.takeIf { it.isNotBlank() }?.let { n ->
                Spacer(Modifier.height(4.dp))
                Text(n, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Editar", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick        = onDelete,
                    colors         = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) { Text("Eliminar", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentDialog(
    title: String,
    initialUser: String?,
    initialBlock: String,
    initialNotes: String,
    users: List<UserAdminDto>,
    zonaNames: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (userId: String, blockName: String, notes: String?) -> Unit
) {
    var userId    by remember { mutableStateOf(initialUser ?: users.firstOrNull()?.id ?: "") }
    var blockName by remember { mutableStateOf(initialBlock) }
    var notes     by remember { mutableStateOf(initialNotes) }
    var userMenuOpen by remember { mutableStateOf(false) }
    var blockMenuOpen by remember { mutableStateOf(false) }

    val selectedName = users.firstOrNull { it.id == userId }?.full_name ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded         = userMenuOpen,
                    onExpandedChange = { userMenuOpen = !userMenuOpen }
                ) {
                    OutlinedTextField(
                        value         = selectedName,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Trabajador") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(userMenuOpen) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded         = userMenuOpen,
                        onDismissRequest = { userMenuOpen = false }
                    ) {
                        users.forEach { u ->
                            DropdownMenuItem(
                                text    = { Text("${u.full_name} · ${roleLabel(u.role)}") },
                                onClick = { userId = u.id; userMenuOpen = false }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded         = blockMenuOpen,
                    onExpandedChange = { blockMenuOpen = !blockMenuOpen }
                ) {
                    OutlinedTextField(
                        value         = blockName,
                        onValueChange = { blockName = it },
                        label         = { Text("Manzana") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(blockMenuOpen) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth().menuAnchor()
                    )
                    if (zonaNames.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded         = blockMenuOpen,
                            onDismissRequest = { blockMenuOpen = false }
                        ) {
                            val q = blockName.trim()
                            zonaNames
                                .filter { q.isBlank() || it.contains(q, ignoreCase = true) }
                                .take(15)
                                .forEach { z ->
                                    DropdownMenuItem(
                                        text    = { Text(z) },
                                        onClick = { blockName = z; blockMenuOpen = false }
                                    )
                                }
                        }
                    }
                }

                OutlinedTextField(
                    value         = notes,
                    onValueChange = { notes = it },
                    label         = { Text("Notas (opcional)") },
                    minLines      = 2,
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(userId, blockName, notes.takeIf { it.isNotBlank() }) },
                enabled = userId.isNotBlank() && blockName.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
