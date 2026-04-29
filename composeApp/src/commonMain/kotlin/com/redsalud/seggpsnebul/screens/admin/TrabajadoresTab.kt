package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.data.remote.AllowedUserDto
import com.redsalud.seggpsnebul.data.remote.UserAdminDto
import com.redsalud.seggpsnebul.domain.model.UserRole

private enum class WorkersSubTab { TRABAJADORES, WHITELIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrabajadoresTab(vm: AdminViewModel) {
    var sub by remember { mutableStateOf(WorkersSubTab.TRABAJADORES) }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = sub.ordinal) {
            Tab(
                selected = sub == WorkersSubTab.TRABAJADORES,
                onClick  = { sub = WorkersSubTab.TRABAJADORES },
                text     = { Text("Trabajadores") }
            )
            Tab(
                selected = sub == WorkersSubTab.WHITELIST,
                onClick  = { sub = WorkersSubTab.WHITELIST },
                text     = { Text("Whitelist DNIs") }
            )
        }
        when (sub) {
            WorkersSubTab.TRABAJADORES -> WorkersList(vm)
            WorkersSubTab.WHITELIST    -> WhitelistList(vm)
        }
    }
}

// ─── Subtab: Trabajadores ────────────────────────────────────────────────────

@Composable
private fun WorkersList(vm: AdminViewModel) {
    val users     by vm.users.collectAsState()
    val allowed   by vm.allowedUsers.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    var query    by remember { mutableStateOf("") }
    var showNew  by remember { mutableStateOf(false) }
    var editing  by remember { mutableStateOf<UserAdminDto?>(null) }
    var resetting by remember { mutableStateOf<UserAdminDto?>(null) }
    var deleting by remember { mutableStateOf<UserAdminDto?>(null) }

    val filtered = remember(users, query) {
        if (query.isBlank()) users
        else users.filter {
            it.full_name.contains(query, ignoreCase = true) ||
            it.dni.contains(query) ||
            it.role.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Cabecera con buscador y botón nuevo
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("Buscar por nombre, DNI o rol") },
                singleLine    = true,
                modifier      = Modifier.weight(1f)
            )
            Button(onClick = { showNew = true }, enabled = !isLoading) { Text("+ Nuevo") }
        }

        Text(
            "${filtered.size} de ${users.size} trabajadores",
            style    = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (users.isEmpty()) "Sin trabajadores. Crea uno con + Nuevo." else "Sin coincidencias.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { user ->
                    WorkerCardFull(
                        user      = user,
                        onToggle  = { vm.toggleUserActive(user.id, user.isActive) },
                        onEdit    = { editing = user },
                        onResetPin = { resetting = user },
                        onDelete  = { deleting = user }
                    )
                }
            }
        }
    }

    // ─── Diálogos ──
    if (showNew) {
        WorkerFormDialog(
            title       = "Nuevo trabajador",
            initialDni  = "",
            initialName = "",
            initialPhone = "",
            initialRole = UserRole.NEBULIZADOR.value,
            allowed     = allowed,
            showPin     = true,
            onDismiss   = { showNew = false },
            onSubmit    = { dni, phone, name, role, pin ->
                vm.createUser(dni, phone, name, role, pin!!)
                showNew = false
            }
        )
    }
    editing?.let { u ->
        WorkerFormDialog(
            title       = "Editar trabajador",
            initialDni  = u.dni,
            initialName = u.full_name,
            initialPhone = u.phone_number,
            initialRole = u.role,
            allowed     = allowed,
            showPin     = false,
            dniReadOnly = true,
            onDismiss   = { editing = null },
            onSubmit    = { _, phone, name, role, _ ->
                vm.updateUser(u.id, name, role, phone)
                editing = null
            }
        )
    }
    resetting?.let { u ->
        ResetPinDialog(
            user      = u,
            onDismiss = { resetting = null },
            onSubmit  = { newPin -> vm.resetUserPin(u.id, newPin); resetting = null }
        )
    }
    deleting?.let { u ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar trabajador") },
            text  = { Text("¿Eliminar definitivamente a ${u.full_name}? Esto fallará si tiene datos asociados (jornadas, GPS, alertas). Si así fuera, usa 'Desactivar' en su lugar.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteUser(u.id); deleting = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun WorkerCardFull(
    user: UserAdminDto,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onResetPin: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(user.full_name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        roleLabel(user.role),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "DNI ${user.dni} · ${user.phone_number}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Badge(
                    containerColor = if (user.isActive) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        if (user.isActive) "Activo" else "Inactivo",
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Editar", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onResetPin, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Reset PIN", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(
                        if (user.isActive) "Desactivar" else "Activar",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onDelete,
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("Eliminar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ─── Subtab: Whitelist DNIs ──────────────────────────────────────────────────

@Composable
private fun WhitelistList(vm: AdminViewModel) {
    val allowed by vm.allowedUsers.collectAsState()

    var dni     by remember { mutableStateOf("") }
    var phone   by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<AllowedUserDto?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value           = dni,
                onValueChange   = { dni = it.filter(Char::isDigit).take(8) },
                placeholder     = { Text("DNI (8 dígitos)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine      = true,
                modifier        = Modifier.weight(1f)
            )
            OutlinedTextField(
                value           = phone,
                onValueChange   = { phone = it.filter(Char::isDigit).take(11) },
                placeholder     = { Text("Teléfono") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine      = true,
                modifier        = Modifier.weight(1.2f)
            )
            Button(
                onClick = {
                    if (dni.length in 8..8 && phone.length in 9..11) {
                        vm.addAllowedUser(dni, phone)
                        dni = ""; phone = ""
                    }
                },
                enabled = dni.length == 8 && phone.length >= 9
            ) { Text("Añadir") }
        }

        Text(
            "${allowed.size} DNIs autorizados (whitelist)",
            style    = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (allowed.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Sin DNIs en la whitelist.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(allowed, key = { it.dni }) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("DNI ${row.dni}", style = MaterialTheme.typography.bodyMedium)
                                Text(row.phone_number, style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = { deleting = row },
                                colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Quitar", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Quitar de whitelist") },
            text  = { Text("¿Quitar DNI ${row.dni} de la whitelist? Si ya hay un trabajador con ese DNI registrado, fallará por integridad referencial.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteAllowedUser(row.dni); deleting = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

// ─── Diálogos compartidos ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkerFormDialog(
    title: String,
    initialDni: String,
    initialName: String,
    initialPhone: String,
    initialRole: String,
    allowed: List<AllowedUserDto>,
    showPin: Boolean,
    dniReadOnly: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (dni: String, phone: String, name: String, role: String, pin: String?) -> Unit
) {
    var dni   by remember { mutableStateOf(initialDni) }
    var phone by remember { mutableStateOf(initialPhone) }
    var name  by remember { mutableStateOf(initialName) }
    var role  by remember { mutableStateOf(initialRole) }
    var pin   by remember { mutableStateOf("") }
    var roleMenuOpen by remember { mutableStateOf(false) }

    // Auto-completar phone si el DNI está en allowed_users
    LaunchedEffect(dni) {
        if (!dniReadOnly && dni.length == 8) {
            allowed.firstOrNull { it.dni == dni }?.let {
                if (phone.isBlank()) phone = it.phone_number
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value           = dni,
                    onValueChange   = { dni = it.filter(Char::isDigit).take(8) },
                    label           = { Text("DNI") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    enabled         = !dniReadOnly
                )
                OutlinedTextField(
                    value           = name,
                    onValueChange   = { name = it },
                    label           = { Text("Nombre completo") },
                    singleLine      = true
                )
                OutlinedTextField(
                    value           = phone,
                    onValueChange   = { phone = it.filter(Char::isDigit).take(11) },
                    label           = { Text("Teléfono") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine      = true
                )

                ExposedDropdownMenuBox(
                    expanded         = roleMenuOpen,
                    onExpandedChange = { roleMenuOpen = !roleMenuOpen }
                ) {
                    OutlinedTextField(
                        value         = roleLabel(role),
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Rol") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(roleMenuOpen) },
                        modifier      = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded         = roleMenuOpen,
                        onDismissRequest = { roleMenuOpen = false }
                    ) {
                        UserRole.entries.forEach { r ->
                            DropdownMenuItem(
                                text    = { Text(r.displayName) },
                                onClick = { role = r.value; roleMenuOpen = false }
                            )
                        }
                    }
                }

                if (showPin) {
                    OutlinedTextField(
                        value           = pin,
                        onValueChange   = { pin = it.filter(Char::isDigit).take(4) },
                        label           = { Text("PIN inicial (4 dígitos)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine      = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ok = dni.length == 8 && name.isNotBlank() && phone.length >= 9 &&
                             (!showPin || pin.length == 4)
                    if (ok) onSubmit(dni, phone, name, role, if (showPin) pin else null)
                },
                enabled = dni.length == 8 && name.isNotBlank() && phone.length >= 9 &&
                          (!showPin || pin.length == 4)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ResetPinDialog(
    user: UserAdminDto,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restablecer PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Trabajador: ${user.full_name}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "El nuevo PIN se entregará al trabajador. Su dispositivo asociado se desvinculará y deberá volver a registrarse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value           = pin,
                    onValueChange   = { pin = it.filter(Char::isDigit).take(4) },
                    label           = { Text("Nuevo PIN (4 dígitos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine      = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(pin) },
                enabled = pin.length == 4
            ) { Text("Restablecer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

internal fun roleLabel(role: String) = when (role) {
    "jefe_brigada" -> "Jefe de Brigada"
    "nebulizador"  -> "Nebulizador"
    "anotador"     -> "Anotador"
    "chofer"       -> "Chofer / Abastecedor"
    else           -> role
}
