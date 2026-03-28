# Plan de Desarrollo — Sistema GPS de Seguimiento de Brigadas de Nebulización
**Proyecto:** seg_gps_nebul
**Fecha:** 2026-03-27
**Stack:** Kotlin Multiplatform · Compose Multiplatform · Supabase · SQLDelight

---

## 1. Visión General

App multiplataforma (Android + Windows Desktop) para el seguimiento en tiempo real de brigadas de nebulización espacial en la **Provincia de Rioja, San Martín, Perú**. Opera en condiciones de baja o nula conectividad (*offline-first*): SQLDelight como fuente de verdad local, sincronización con Supabase al recuperar señal.

**Herramientas 100% gratuitas:** Supabase Free Tier · Firebase FCM · MapLibre · Protomaps/PMTiles · OSRM público · KMP/Compose Multiplatform · SQLDelight · GitHub.

---

## 2. Roles y Permisos

| Rol | Plataforma | Descripción |
|---|---|---|
| **Administrador** | Desktop (Windows) + Android | Control total: ve todo, gestiona usuarios, capas, sesiones, exporta CSV. UI idéntica adaptada por pantalla |
| **Jefe de Brigada** | Android | Coordina brigada, asigna manzanas, envía broadcast de texto |
| **Nebulizador** | Android | Inicia jornada, GPS en segundo plano, ruta capturada |
| **Anotador** | Android | Emite alertas de insumos/estado |
| **Chofer / Abastecedor** | Android | Recibe solicitudes de insumos, ve ruta optimizada hacia solicitantes |

> **Admin Android:** la carga de KML/GPKG y CSV de whitelist se hace desde el almacenamiento local del dispositivo. La exportación CSV se guarda en Downloads.

---

## 3. Stack Tecnológico

### Frontend
- **Kotlin Multiplatform (KMP)** — lógica de negocio compartida (Android + Desktop)
- **Compose Multiplatform** — UI unificada Android + Windows Desktop
- **MapLibre Native (Android)** — mapa con soporte offline PMTiles
- **MapLibre GL JS en WebView Compose (Desktop)** — mapa en panel de administrador

### Backend
- **Supabase** (Free Tier)
  - PostgreSQL — base de datos central
  - Supabase Realtime — suscripciones WebSocket para GPS y alertas en vivo
  - Supabase Storage — almacenamiento de capas vectoriales (KML, GPKG) y PMTiles regionales
  - Row Level Security (RLS) — control de acceso por rol
  - Supabase Auth — exclusivo para el administrador (email + contraseña)

### Notificaciones Push
- **Firebase FCM (Android)** — alertas en segundo plano para: chofer (solicitudes de insumos), todos los roles (broadcast del jefe), admin móvil
- **Desktop (Windows)** — sin notificaciones push; el admin ve el estado haciendo clic en el panel (Supabase Realtime mantiene todo actualizado mientras el panel está abierto)

### Base de Datos Local
- **SQLDelight** — SQLite multiplataforma (Android + Desktop), fuente de verdad offline

### Mapas Offline
- **PMTiles (Protomaps)** — formato moderno, un solo archivo, compatible con MapLibre
- Extracto OSM de la Provincia de Rioja (~50–150 MB, zoom 10–17)
- **Descarga automática en el primer arranque:** al primer login exitoso, la app descarga el archivo PMTiles desde Supabase Storage con barra de progreso. Se almacena localmente y no se vuelve a descargar salvo actualización
- Zoom útil: 10 (visión provincial) → 17 (detalle de manzana)

### Ruteo para Chofer
- **Con señal:** OSRM API pública — ruta real multiparada por calles
- **Sin señal:** Haversine + nearest neighbor — ordenamiento por distancia en línea recta
- La app indica claramente el modo activo ("Ruta real" / "Estimación sin señal")

---

## 4. Arquitectura del Sistema

```
┌──────────────────────────────────────────────────────────────────┐
│                        SUPABASE (Cloud)                          │
│   PostgreSQL · Realtime · Storage (PMTiles + capas) · Auth       │
└──────────────────┬───────────────────────────────────────────────┘
                   │ sync bidireccional (cuando hay señal)
        ┌──────────┴──────────┐
        │                     │
┌───────▼──────────┐  ┌───────▼──────────────────────────────────┐
│  ANDROID         │  │  WINDOWS DESKTOP (Admin)                  │
│  Todos los roles │  │                                           │
│  incl. Admin     │  │  Compose Desktop UI                       │
│                  │  │  SQLDelight local                         │
│  Compose UI      │  │  Coroutine SyncManager                    │
│  SQLDelight      │  │  MapLibre GL JS (WebView)                 │
│  WorkManager     │  │  Sin notificaciones push                  │
│  MapLibre+PMTiles│  │  (Realtime mantiene estado live)          │
│  GPS Foreground  │  └───────────────────────────────────────────┘
│  FCM (Firebase)  │
└──────────────────┘
        │
┌───────▼──────────┐
│  Firebase FCM    │
│  Push Android    │
└──────────────────┘
```

### Flujo Offline-First
1. Toda acción (GPS, alerta, asignación) se escribe primero en **SQLDelight local** con `sync_status = PENDING`
2. `ConnectivityObserver` detecta recuperación de señal → dispara `SyncManager`
3. `SyncManager` envía registros pendientes en orden cronológico (`captured_at`)
4. Confirmados en Supabase → `sync_status = SYNCED`
5. Supabase Realtime propaga a todos los clientes conectados en tiempo real

---

## 5. Esquema de Base de Datos

### `allowed_users` — whitelist pre-cargada por admin
```sql
dni             TEXT PRIMARY KEY
phone_number    TEXT NOT NULL
loaded_at       TIMESTAMP
```

### `users` — trabajadores registrados
```sql
id              UUID PRIMARY KEY
dni             TEXT UNIQUE NOT NULL  -- debe existir en allowed_users
phone_number    TEXT NOT NULL         -- del allowed_users
full_name       TEXT NOT NULL
role            ENUM(jefe_brigada, nebulizador, anotador, chofer)
pin             TEXT NOT NULL         -- 4 dígitos, texto plano
device_id       TEXT                  -- ANDROID_ID o equivalente
is_active       BOOLEAN DEFAULT true
created_at      TIMESTAMP
```

### `admin` — credenciales separadas (Supabase Auth)
```
Gestionado por Supabase Auth: email + contraseña
Un único registro de administrador
```

### `sessions` — jornadas de trabajo
```sql
id              UUID PRIMARY KEY
name            TEXT
brigade_code    TEXT NULL             -- opcional, definido por jefe
started_by      UUID REFERENCES users -- o admin_id
started_at      TIMESTAMP
ended_at        TIMESTAMP NULL
is_active       BOOLEAN DEFAULT true
export_done     BOOLEAN DEFAULT false
```

### `gps_tracks` — puntos GPS cada 15 segundos
```sql
id              UUID PRIMARY KEY
user_id         UUID REFERENCES users
session_id      UUID REFERENCES sessions
latitude        DOUBLE PRECISION
longitude       DOUBLE PRECISION
accuracy        FLOAT
captured_at     TIMESTAMP            -- timestamp real del dispositivo
sync_status     ENUM(pending, synced)
```

### `alerts` — alertas y mensajes
```sql
id              UUID PRIMARY KEY
sender_id       UUID REFERENCES users
session_id      UUID REFERENCES sessions
alert_type      ENUM(
                  agua,
                  gasolina,
                  insumo_quimico,
                  averia_maquina,
                  trabajo_finalizado,
                  broadcast_text
                )
message         TEXT NULL            -- para broadcast_text
target_role     ENUM(all, chofer)    -- insumos → chofer; broadcast → all
latitude        DOUBLE PRECISION NULL
longitude       DOUBLE PRECISION NULL
is_attended     BOOLEAN DEFAULT false
attended_by     UUID NULL REFERENCES users
created_at      TIMESTAMP
sync_status     ENUM(pending, synced)
```

### `block_assignments` — asignación de manzanas
```sql
id              UUID PRIMARY KEY
session_id      UUID REFERENCES sessions
assigned_to     UUID REFERENCES users
assigned_by     UUID REFERENCES users -- jefe o admin
block_name      TEXT                  -- libre: "Mz. 14", "Sector B"
notes           TEXT NULL
assigned_at     TIMESTAMP
sync_status     ENUM(pending, synced)
```

### `vector_layers` — capas admin (KML/GPKG)
```sql
id              UUID PRIMARY KEY
session_id      UUID NULL REFERENCES sessions
name            TEXT
file_type       ENUM(kml, gpkg, geojson)
storage_path    TEXT                  -- path en Supabase Storage
uploaded_at     TIMESTAMP
```

---

## 6. Funcionalidades por Rol

### 6.1 Registro y Autenticación

**Trabajadores (todos excepto admin):**
- Pantalla de registro: **DNI + Nombre Completo + Cargo + PIN (4 dígitos)**
- Validación: el DNI debe existir en `allowed_users`
- Al registrarse: se captura `device_id`, se crea cuenta, se guarda localmente
- Login posterior: DNI + PIN (sin re-registro)

**Administrador:**
- Login separado: **email + contraseña** (Supabase Auth)
- No aparece en la whitelist de trabajadores
- Panel distinguible visualmente del resto de roles

### 6.2 Mapa — Lo que ve cada rol

**Todos los roles ven en el mapa:**
- Posición en tiempo real de **todos los usuarios activos** (íconos diferenciados por cargo)
- Capa toggleable: rutas históricas de nebulizadores (línea de track por usuario, color único por persona)
- Capas vectoriales cargadas por el admin (KML/GPKG)

**Solo el Admin y Jefe de Brigada ven además:**
- Panel de capas completo con toggles
- Asignaciones de manzanas sobre el mapa

**Solo el Chofer ve además:**
- Solicitudes de insumos activas con ubicación

**Solo el Admin ve además:**
- Todas las alertas de todas las brigadas
- Panel de gestión superpuesto al mapa

### 6.3 Nebulizador
- Botón grande **"INICIAR JORNADA"** / **"FINALIZAR JORNADA"**
- Al iniciar → servicio foreground GPS (captura cada 15 seg), notificación persistente
- Las 5 alertas también disponibles como botones secundarios
- Indicador de sync: verde (synced) / naranja (pendiente)
- Mapa con todos los usuarios visibles

### 6.4 Anotador
- **5 botones de alerta** prominentes:
  1. Agua mineral
  2. Gasolina
  3. Insumo químico
  4. Avería de máquina
  5. Trabajo finalizado
- Al emitir alerta → se geolocaliza en ese momento
- Ve su manzana asignada actual
- Historial de alertas del día
- Mapa con todos los usuarios visibles

### 6.5 Jefe de Brigada
- Todo lo del Anotador +
- **Panel de brigada:** lista de miembros activos
- **Asignar manzana:** selecciona usuario → escribe nombre (texto libre, opcional)
- **Broadcast:** texto libre → notificación push a todos en la brigada
- Puede poner código de brigada (opcional)
- Mapa con todos los usuarios visibles + panel de asignaciones

### 6.6 Chofer / Abastecedor
- **Notificación push (FCM)** al recibir solicitud de insumo
- Lista de solicitudes pendientes: tipo, solicitante, tiempo transcurrido, distancia
- Al haber múltiples:
  - **Con señal:** OSRM multiparada → secuencia óptima real
  - **Sin señal:** Haversine → ordenamiento por cercanía desde su posición
  - Mapa con secuencia numerada de paradas
- Puede marcar solicitud como "atendida"
- Mapa con todos los usuarios visibles

### 6.7 Administrador (Desktop + Android)

**Autenticación:** email + contraseña (Supabase Auth), independiente del sistema de trabajadores.

**Mapa central:**
- Ve todos los usuarios activos con íconos por cargo
- Panel de capas completo (toggles por rol, rutas históricas, capas vectoriales)
- Desktop: Realtime mantiene todo actualizado mientras el panel está abierto
- Android: FCM para alertas críticas en background

**Gestión de usuarios:**
- Carga CSV de whitelist (DNI + teléfono)
- Ve todos los registros: nombre, DNI, cargo, PIN en texto plano, device_id
- Edita PIN directamente
- Activa / desactiva usuarios

**Gestión de sesiones:**
- Inicia y cierra sesiones: por brigada individual o cierre global de todas
- Asigna manzanas a cualquier usuario
- Envía broadcast a brigada específica o a todos
- Ve todas las alertas en panel lateral (Desktop) / pestaña dedicada (Android)

**Capas vectoriales:**
- Carga KML o GPKG → sube a Supabase Storage → se comparte a todos los clientes
- Desktop: selector de archivo Windows; Android: selector desde almacenamiento local

**Exportar y cerrar jornada:**
1. Selecciona sesión(es) a cerrar: brigada individual o todas
2. Preview de registros a exportar
3. Confirma → descarga CSV (Desktop: carpeta elegida; Android: Downloads)
4. Post-confirmación → borrado en Supabase de los registros de esa sesión
5. Data local en dispositivos permanece hasta limpieza manual

---

## 7. Mapa — Íconos y Capas

### Íconos por Cargo
| Cargo | Ícono | Color |
|---|---|---|
| Nebulizador | Nube / spray | Azul |
| Anotador | Clipboard / lápiz | Verde |
| Jefe de Brigada | Escudo / estrella | Naranja |
| Chofer / Abastecedor | Camión / caja | Morado |
| Admin (referencia) | Ojo | Gris oscuro |

- Al tocar un ícono → popup: nombre, cargo, última actualización, alerta activa si la hay, manzana asignada si aplica

### Panel de Capas (toggleable)
```
[x] Nebulizadores — posición actual
[x] Anotadores — posición actual
[x] Jefes de Brigada — posición actual
[x] Choferes — posición actual
[ ] Rutas históricas — Nebulizadores (línea por usuario, color único)
[ ] Rutas históricas — Choferes
[ ] Alertas activas
[ ] Capas vectoriales (KML/GPKG cargadas por admin)
[ ] Asignaciones de manzanas
```

---

## 8. Estrategia Offline

### Primer Arranque (automatizado)
1. Usuario instala app → pantalla de login
2. Login exitoso (trabajador: DNI+PIN / admin: email+contraseña)
3. App detecta que no hay PMTiles local → inicia descarga automática desde Supabase Storage
4. Barra de progreso visible: "Descargando mapa de Rioja... X MB / Y MB"
5. La app es usable durante la descarga (mapa online como fallback temporal)
6. PMTiles guardado localmente → mapa offline disponible para siempre

### Durante Trabajo Sin Señal
- SQLDelight es la fuente de verdad
- GPS → escrito local con `sync_status = PENDING`
- Alertas, asignaciones → ídem
- Mapa usa PMTiles local
- Indicador global visible: "Sin señal — X registros pendientes de sync"

### Al Recuperar Señal
- `ConnectivityObserver` dispara `SyncManager`
- Envía pendientes en orden cronológico
- Manejo de conflictos: GPS es append-only (no hay conflictos reales)
- Supabase Realtime notifica a todos los clientes → mapa se actualiza

### Realtime
- Supabase Realtime vía WebSocket
- Reconexión automática con exponential backoff
- Fallback: polling cada 30 seg si WebSocket no disponible

---

## 9. Notificaciones Push (Android — FCM)

| Evento | Destinatario | Contenido |
|---|---|---|
| Solicitud de insumo (agua/gasolina/químico) | Chofer(es) activos | Tipo + nombre solicitante + distancia estimada |
| Avería de máquina | Chofer + Jefe de Brigada | Nombre + ubicación |
| Trabajo finalizado | Jefe de Brigada + Admin | Nombre del nebulizador |
| Broadcast del jefe | Todos en la brigada | Texto del mensaje |
| Broadcast del admin | Todos o brigada específica | Texto del mensaje |

- FCM token se registra al hacer login y se actualiza por sesión
- Desktop: sin notificaciones push — admin ve estado en panel Realtime

---

## 10. Exportación y Cierre de Sesión

### Formato CSV
```
session_id, session_name, brigade_code,
user_dni, user_name, role,
event_type, latitude, longitude, captured_at,
alert_type, alert_message, is_attended,
block_name, device_id
```
> `event_type`: `gps_track` | `alert` | `block_assignment`

### Proceso de Cierre
1. Admin selecciona: brigada específica o cierre global
2. Preview con contador de registros
3. Confirma exportación → archivo CSV descargado
4. Confirmado → `DELETE` en Supabase de los registros de esa sesión
5. Sesión marcada: `is_active = false`, `ended_at = now()`, `export_done = true`
6. Data local en dispositivos permanece (no se borra automáticamente)

---

## 11. Seguridad

- **Whitelist:** solo DNIs pre-cargados por el admin pueden registrarse como trabajadores
- **PIN texto plano:** guardado en Supabase y visible para el admin en su panel
- **Device ID:** `ANDROID_ID` (Android) / WMI UUID (Windows) como referencia de dispositivo
- **Admin separado:** Supabase Auth (email + contraseña), un único administrador
- **RLS Supabase:** cada rol solo accede a los datos que le corresponden
- **Sin exposición pública:** sistema cerrado, no hay registro libre

---

## 12. Setup Inicial del Proyecto

### Herramientas requeridas
```
- Supabase CLI
- GitHub CLI
- Android Studio (KMP plugin)
- JDK 17+
- Node.js (para scripts de PMTiles si aplica)
```

### Pasos de inicialización
1. `gh repo create seg_gps_nebul --private` — repositorio GitHub
2. `supabase init` + `supabase login` — proyecto Supabase nuevo
3. Aplicar migraciones SQL (tablas + RLS)
4. Crear bucket en Supabase Storage: `map-tiles` (para PMTiles) y `vector-layers`
5. Descargar extracto OSM de Rioja → generar PMTiles con Protomaps CLI → subir a Storage
6. Configurar Firebase proyecto → habilitar FCM → descargar `google-services.json`
7. Setup KMP con Compose Multiplatform + SQLDelight + Supabase Kotlin SDK

---

## 13. Fases de Implementación

### Fase 1 — Fundamentos (semana 1–2)
- [ ] Setup KMP: Android + Desktop (Compose Multiplatform)
- [ ] Integración Supabase: tablas, RLS, Storage buckets
- [ ] SQLDelight: esquema local completo
- [ ] Autenticación: whitelist + registro DNI+PIN (trabajadores) + email/pass (admin)
- [ ] Captura device_id en Android y Desktop

### Fase 2 — GPS y Mapa Base (semana 3–4)
- [ ] Servicio foreground GPS Android (cada 15 seg)
- [ ] Descarga automática PMTiles en primer arranque
- [ ] Integración MapLibre + PMTiles en Android
- [ ] Integración MapLibre GL JS (WebView) en Desktop
- [ ] Íconos por cargo como Symbol Layers en MapLibre
- [ ] Popup al tocar ícono

### Fase 3 — Roles y Alertas (semana 5–6)
- [ ] UI Nebulizador: iniciar/finalizar jornada + servicio background
- [ ] UI Anotador: 5 botones de alerta
- [ ] UI Jefe de Brigada: asignación de manzanas + broadcast
- [ ] UI Chofer: lista de solicitudes + ruteo (OSRM + Haversine fallback)
- [ ] FCM: integración y envío de notificaciones por tipo de alerta

### Fase 4 — Mapa Completo y Capas (semana 7–8)
- [ ] Todos los roles ven a todos en el mapa (posición en tiempo real)
- [ ] Capas de rutas históricas de nebulizadores (toggle)
- [ ] Carga y visualización de KML/GPKG como capas vectoriales
- [ ] Panel de capas toggleable

### Fase 5 — Panel Admin y Sync (semana 9–10)
- [ ] Panel admin completo (Desktop + Android): gestión usuarios, sesiones, alertas
- [ ] SyncManager robusto: cola PENDING, reconexión, append-only GPS
- [ ] Exportación CSV + borrado servidor por brigada o global
- [ ] ConnectivityObserver + indicadores de estado en UI
- [ ] Supabase Realtime: reconexión automática + fallback polling

### Fase 6 — QA y Pulido (semana 11–12)
- [ ] Testing en condiciones de señal intermitente
- [ ] Testing multi-brigada simultánea
- [ ] Performance: mapa con muchos puntos GPS simultáneos
- [ ] UI/UX: notificaciones persistentes, estados de sync, feedback visual
- [ ] Build de release Android (APK) + Desktop (EXE/MSI Windows)

---

## 14. Decisiones Confirmadas

| # | Decisión | Resolución |
|---|---|---|
| 1 | PIN almacenado | **Texto plano** — visible para admin |
| 2 | MBTiles/PMTiles fuente | **Protomaps PMTiles** — descarga automática primer arranque |
| 3 | Ruteo chofer offline | **Haversine** offline + **OSRM** online |
| 4 | Auth admin | **Supabase Auth** (email + contraseña), único admin |
| 5 | Zoom tiles Rioja | **10–17** (balance calidad / peso ~100 MB) |
| 6 | Notificaciones Desktop | **Sin push** — Supabase Realtime en panel abierto |
| 7 | Visibilidad de ubicaciones | **Todos ven a todos** (posición + ruta nebulizadores) |
| 8 | Cierre de sesión | **Por brigada** o **global**, a elección del admin |
| 9 | Número de admins | **Uno único** |

---

## 15. Estructura de Carpetas KMP

```
seg_gps_nebul/
├── composeApp/
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/
│       │       └── screens/
│       │           ├── admin/          # UI admin (compartida Android + Desktop)
│       │           ├── nebulizador/
│       │           ├── anotador/
│       │           ├── jefe/
│       │           └── chofer/
│       ├── androidMain/                # GPS ForegroundService, FCM, file picker Android
│       └── desktopMain/                # WebView mapa, file picker Windows
├── shared/
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/
│       │       ├── data/
│       │       │   ├── local/          # SQLDelight DAOs
│       │       │   ├── remote/         # Supabase repos
│       │       │   └── sync/           # SyncManager, SyncQueue
│       │       ├── domain/
│       │       │   ├── model/          # User, GpsTrack, Alert, Session...
│       │       │   └── usecase/
│       │       └── location/           # expect/actual GPS abstraction
│       ├── androidMain/                # FusedLocationProvider impl
│       └── desktopMain/                # Windows GPS / mock
├── supabase/
│   └── migrations/                     # SQL de tablas y RLS
├── scripts/
│   └── generate_pmtiles.sh             # Descarga OSM Rioja + genera PMTiles
└── build.gradle.kts
```

---

*Plan cerrado. Listo para iniciar Fase 1.*
