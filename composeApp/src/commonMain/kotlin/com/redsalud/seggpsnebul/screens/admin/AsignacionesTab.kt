package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.redsalud.seggpsnebul.data.remote.AssignmentDto
import com.redsalud.seggpsnebul.data.remote.UserAdminDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsignacionesTab(vm: AdminViewModel) {
    val users       by vm.users.collectAsState()
    val assignments by vm.allAssignments.collectAsState()
    val zonas       by vm.zonas.collectAsState()

    var creating by remember { mutableStateOf(false) }
    var editing  by remember { mutableStateOf<AssignmentDto?>(null) }
    var deleting by remember { mutableStateOf<AssignmentDto?>(null) }

    LaunchedEffect(Unit) { vm.loadAllAssignments() }

    val usersById = remember(users) { users.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "${assignments.size} asignaciones",
                style = MaterialTheme.typography.labelMedium
            )
            Button(
                onClick  = { creating = true },
                enabled  = users.isNotEmpty()
            ) { Text("+ Asignar") }
        }

        if (users.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Crea trabajadores para poder asignar manzanas.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (assignments.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Sin asignaciones. Pulsa + Asignar para asignar manzanas a trabajadores.",
                    style = MaterialTheme.typography.bodyMedium
                )
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

    if (creating) {
        BatchAssignmentDialog(
            users     = users,
            zonaNames = zonas.map { it.nombre },
            onDismiss = { creating = false },
            onSubmit  = { workerIds, manzanas, notes ->
                vm.createAssignmentsBatch(workerIds, manzanas, notes)
                creating = false
            }
        )
    }

    editing?.let { a ->
        SingleAssignmentDialog(
            title        = "Editar asignación",
            initialUser  = a.assigned_to,
            initialBlock = a.block_name,
            initialNotes = a.notes ?: "",
            users        = users,
            zonaNames    = zonas.map { it.nombre },
            onDismiss    = { editing = null },
            onSubmit     = { userId, blockName, notes ->
                vm.updateAssignment(a.id, userId, blockName, notes)
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
                    onClick = { vm.deleteAssignment(a.id); deleting = null },
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

@Composable
private fun BatchAssignmentDialog(
    users: List<UserAdminDto>,
    zonaNames: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (workerIds: List<String>, manzanas: List<String>, notes: String?) -> Unit
) {
    var selectedWorkers  by remember { mutableStateOf(setOf<String>()) }
    var selectedManzanas by remember { mutableStateOf(setOf<String>()) }
    var manzanaQuery     by remember { mutableStateOf("") }
    var notes            by remember { mutableStateOf("") }

    val filteredZonas = remember(manzanaQuery, zonaNames) {
        zonaNames.filter { manzanaQuery.isBlank() || it.contains(manzanaQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape    = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Nueva asignación", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                // Workers
                Text("Trabajadores *", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                ) {
                    items(users) { u ->
                        val checked = u.id in selectedWorkers
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedWorkers = if (checked) selectedWorkers - u.id
                                    else selectedWorkers + u.id
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked         = checked,
                                onCheckedChange = {
                                    selectedWorkers = if (it) selectedWorkers + u.id
                                    else selectedWorkers - u.id
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(u.full_name, style = MaterialTheme.typography.bodyMedium)
                                Text(roleLabel(u.role), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Manzanas
                Text("Manzanas *", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value         = manzanaQuery,
                    onValueChange = { manzanaQuery = it },
                    placeholder   = { Text("Buscar manzana…") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                ) {
                    items(filteredZonas) { z ->
                        val checked = z in selectedManzanas
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedManzanas = if (checked) selectedManzanas - z
                                    else selectedManzanas + z
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked         = checked,
                                onCheckedChange = {
                                    selectedManzanas = if (it) selectedManzanas + z
                                    else selectedManzanas - z
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(z, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value         = notes,
                    onValueChange = { notes = it },
                    label         = { Text("Notas (opcional)") },
                    minLines      = 2,
                    modifier      = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    val count = selectedWorkers.size * selectedManzanas.size
                    Button(
                        onClick  = {
                            onSubmit(
                                selectedWorkers.toList(),
                                selectedManzanas.toList(),
                                notes.takeIf { it.isNotBlank() }
                            )
                        },
                        enabled  = selectedWorkers.isNotEmpty() && selectedManzanas.isNotEmpty()
                    ) {
                        Text(if (count > 0) "Asignar ($count)" else "Asignar")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleAssignmentDialog(
    title: String,
    initialUser: String?,
    initialBlock: String,
    initialNotes: String,
    users: List<UserAdminDto>,
    zonaNames: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (userId: String, blockName: String, notes: String?) -> Unit
) {
    var userId       by remember { mutableStateOf(initialUser ?: users.firstOrNull()?.id ?: "") }
    var blockName    by remember { mutableStateOf(initialBlock) }
    var notes        by remember { mutableStateOf(initialNotes) }
    var userMenuOpen  by remember { mutableStateOf(false) }
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
                onClick  = { onSubmit(userId, blockName, notes.takeIf { it.isNotBlank() }) },
                enabled  = userId.isNotBlank() && blockName.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
