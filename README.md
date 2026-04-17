# GPS Nebulizacion -- Sistema de Seguimiento para Brigadas

Sistema de seguimiento GPS en tiempo real para las brigadas de nebulizacion contra el dengue en la provincia de Rioja, San Martin, Peru. Construido con **Kotlin Multiplatform** (Android + Desktop/Windows).

---

## Contexto

La Unidad de Gestion Territorial (UNGET) de la Red de Salud Rioja coordina jornadas de nebulizacion con brigadas de campo que operan en zonas urbanas y periurbanas. Cada brigada esta compuesta por trabajadores con roles especificos que necesitan comunicarse y ser rastreados en tiempo real, incluso en zonas con conectividad limitada.

Este sistema reemplaza el seguimiento manual por hojas de calculo, permitiendo:

- Saber donde esta cada miembro de la brigada en todo momento
- Enviar alertas de abastecimiento (agua, gasolina, insumos) al chofer
- Asignar manzanas/bloques de trabajo a nebulizadores y anotadores
- Recibir notificaciones push de solicitudes en tiempo real
- Exportar recorridos GPS y alertas a CSV para reportes de cobertura

---

## Roles del sistema

| Rol | Funcion | Capacidades en la app |
|-----|---------|----------------------|
| **Jefe de Brigada** | Coordina la brigada en campo | Ve el mapa completo con posiciones de todos, envia broadcasts, asigna manzanas, monitorea y atiende alertas (3 pestanas: Mapa, Alertas, Brigada) |
| **Nebulizador** | Opera la maquina de nebulizacion | Ve el mapa, solicita agua/gasolina/insumos/reporta averias, marca trabajo finalizado |
| **Anotador** | Registra datos casa por casa | Ve su manzana asignada, historial de alertas, solicita insumos |
| **Chofer / Abastecedor** | Transporta insumos y personal | Recibe solicitudes ordenadas por distancia (Haversine), marca como atendidas (2 pestanas: Solicitudes, Mapa) |
| **Administrador** | Supervisa desde oficina (Desktop) | Gestiona trabajadores, exporta jornadas a CSV, elimina GPS del servidor, monitorea estado de sync (3 pestanas: Trabajadores, Jornadas, Sync) |

---

## Arquitectura

```
+-------------------------------------------------------+
|                      composeApp                        |
|  +------------+  +--------------+  +-----------------+ |
|  |  Android   |  |  commonMain  |  |    Desktop      | |
|  | MapLibre   |  |  Screens/UI  |  | WebView Map     | |
|  | GPS Svc    |  |  ViewModels  |  | (MapLibre GL JS)| |
|  | IMU Fusion |  |  Navigation  |  |                 | |
|  | Notif.     |  |              |  |                 | |
|  +------------+  +--------------+  +-----------------+ |
+-------------------------------------------------------+
|                       shared                           |
|  +--------------------------------------------------+ |
|  | commonMain                                        | |
|  |  Domain Models -- Repositories -- SQLDelight      | |
|  |  Location Fusion (EKF + Complementario)           | |
|  |  Supabase Client -- Sync Manager -- PmTiles       | |
|  |  Connectivity Observer -- PIN Hasher              | |
|  +------------------------+--------------------------+ |
|  | androidMain            |  desktopMain             | |
|  | DB Driver (Android)    |  DB Driver (JdbcSqlite)  | |
|  | IMU Sensors            |  Connectivity (java.net) | |
|  | Device ID (SharedPrefs)|  Device ID (UUID file)   | |
|  | Connectivity (CM)      |  App Files Dir           | |
|  +------------------------+--------------------------+ |
+-------------------------------------------------------+
|                Supabase (remoto)                        |
|  Auth -- Postgrest -- Realtime (WebSocket) -- Storage  |
+-------------------------------------------------------+
```

### Navegacion de la app

```
App.kt
  |
  +-- AuthScreen (Login / Registro / Admin Login)
  |     |
  |     +-- [Worker] --> HomeScreen --> segun UserRole:
  |     |                   +-- JefeScreen      (Mapa | Alertas | Brigada)
  |     |                   +-- NebulizadorScreen (Mapa + AlertButtons)
  |     |                   +-- AnotadorScreen   (Mapa + Manzana + Alertas)
  |     |                   +-- ChoferScreen     (Solicitudes | Mapa)
  |     |
  |     +-- [Admin]  --> AdminScreen (Trabajadores | Jornadas | Sync)
```

---

## Pantallas y funcionalidades

### Autenticacion (`AuthScreen`)

Tres modos de acceso:

1. **Login trabajador**: DNI + PIN de 4 digitos --> validacion contra SQLite local (PIN hasheado con SHA-256 + salt)
2. **Registro trabajador**: DNI + nombre + rol + PIN --> verifica DNI en whitelist `allowed_users` (local, con fallback a Supabase)
3. **Login administrador**: Email + contrasena --> Supabase Auth (JWT)

### Jefe de Brigada (`JefeScreen`)

Interfaz de 3 pestanas:

| Pestana | Contenido |
|---------|-----------|
| **Mapa** | Posiciones de todos los miembros en tiempo real. Click en marcador = popup con nombre, rol, ultimo GPS, alerta activa y manzana asignada. Botones: "Iniciar Jornada" / "Finalizar" / "Broadcast" |
| **Alertas** | Lista de alertas pendientes con boton "Atender". Se cambia automaticamente a esta pestana cuando llega una alerta nueva por Realtime |
| **Brigada** | Lista de trabajadores con sus manzanas asignadas. Boton "Asignar" abre dialogo para asignar nueva manzana |

### Nebulizador (`NebulizadorScreen`)

- Mapa siempre visible con su posicion y la de companeros
- Cuadricula 2x2 de botones de alerta:
  - Agua mineral (agua azul), Gasolina (bomba), Insumo Quimico (tubo), Averia de Maquina (llave)
  - Boton central: Trabajo Finalizado (check verde)
- Cada boton pide confirmacion antes de enviar
- Indicador de estado de sync

### Anotador (`AnotadorScreen`)

- Mapa con posiciones del equipo
- Tarjeta "Manzana asignada" con nombre del bloque y notas
- Historial de ultimas 10 alertas de la sesion
- Mismos botones de alerta que el Nebulizador

### Chofer / Abastecedor (`ChoferScreen`)

Interfaz de 2 pestanas:

| Pestana | Contenido |
|---------|-----------|
| **Solicitudes** | Lista de solicitudes de suministro pendientes, ordenadas por distancia al chofer (formula de Haversine). Muestra distancia en km o metros. Boton "Atendido" para marcar completada |
| **Mapa** | Mapa con posiciones de todos los miembros |

### Panel de Administracion (`AdminScreen`) -- Solo Desktop

| Pestana | Contenido |
|---------|-----------|
| **Trabajadores** | Lista de usuarios desde Supabase: nombre, DNI, telefono, rol. Badges Activo/Inactivo con boton toggle. Filtro por estado |
| **Jornadas** | Todas las sesiones (mas recientes primero). Stats por sesion: puntos GPS, alertas, manzanas. Badges: "En curso" / "Cerrada" / "Exportada". Acciones: exportar CSV, eliminar GPS del servidor |
| **Sync** | Dashboard en tiempo real: conectividad (Online/Offline), WebSocket (Conectado/Polling), conteos pendientes (GPS, alertas, manzanas), ultima sincronizacion, errores. Boton "Sincronizar ahora" |

---

## Offline-first

Toda la informacion se guarda primero en **SQLite local** y se sincroniza a Supabase cuando hay conexion:

1. El trabajador opera normalmente sin internet
2. GPS tracks, alertas y asignaciones se acumulan localmente con `sync_status = 'pending'`
3. Al recuperar conexion, `SyncManager` sube todo en orden FK-safe:
   - Sesiones (primero, por dependencias de foreign key)
   - GPS tracks
   - Alertas
   - Asignaciones de manzanas
4. Retry automatico con backoff exponencial (max 5 intentos, 2s --> 4s --> 8s --> 16s --> 32s --> 60s)
5. Si falla un paso, se aborta el ciclo y se reintenta todo en el siguiente

### Deteccion de conectividad

| Plataforma | Mecanismo |
|-----------|-----------|
| Android | `ConnectivityManager` + `NetworkCallback` |
| Desktop | `InetAddress.isReachable()` con check DNS periodico |

Al detectar conexion:
1. Se conecta el WebSocket de Realtime
2. Se suscribe a la sesion activa
3. Se dispara `SyncManager.syncAll()`

---

## Fusion de sensores GPS + IMU

En Android, el sistema combina GPS con acelerometro para mejorar la precision del posicionamiento:

### Extended Kalman Filter (EKF) -- `KalmanLocationFilter`

- **Vector de estado**: `[N, E, vN, vE]` -- posicion (metros) y velocidad (m/s) en coordenadas ENU desde el origen de referencia
- **Predict** (~50 Hz): integra aceleraciones del IMU para estimar posicion entre fixes GPS
- **Update** (~0.2 Hz): corrige con cada fix GPS usando ganancia de Kalman (K = P*Ht*S^-1)
- **Covarianza**: matriz P de 4x4 (`Mat4`) con ruido de proceso Q y ruido de medicion R = sigma^2
- **Conversion**: coordenadas planas ENU <--> lat/lon via aproximacion flat-earth

### Filtro Complementario -- `ComplementaryLocationFilter`

- Alternativa mas liviana como warm-standby
- Blend alpha automatico segun precision GPS:
  - Precision <= 5m --> alpha = 0.90 (confiar en GPS)
  - Precision = 20m --> alpha = 0.67
  - Precision >= 60m --> alpha = 0.30 (confiar en dead reckoning)
- Clamp de aceleracion a +/-6 m/s^2 (rechaza picos de sensor)
- Clamp de velocidad a 15 m/s (~54 km/h, apropiado para moto en trocha)

### Motor de Fusion -- `LocationFusionEngine`

- Mantiene ambos filtros en paralelo (warm-standby)
- Solo el filtro seleccionado (`Mode.EKF` o `Mode.COMPLEMENTARY`) genera la salida `lastFused`
- Se puede cambiar de filtro en caliente sin cold start

### Servicio GPS Android -- `GpsTrackingService`

- Foreground service con notificacion persistente "GPS activo"
- Solicita fixes GPS cada **5 segundos** (`PRIORITY_HIGH_ACCURACY`)
- `ImuProvider` entrega muestras de acelerometro a ~50 Hz (main looper)
- El fix GPS fusionado se guarda en SQLite local con `sync_status = 'pending'`
- Sync automatico si hay conexion (en background via coroutine)

---

## Alertas y notificaciones

### Tipos de alerta

| Tipo | Emoji | Descripcion | Destinatario |
|------|-------|-------------|-------------|
| `agua` | agua azul | Solicitud de agua mineral | Chofer |
| `gasolina` | bomba gasolina | Solicitud de gasolina | Chofer |
| `insumo_quimico` | tubo ensayo | Solicitud de insumo quimico | Chofer |
| `averia_maquina` | llave | Reporte de averia de maquina | Chofer |
| `trabajo_finalizado` | check verde | Trabajo completado | Todos |
| `broadcast_text` | megafono | Mensaje de texto libre | Todos |

### Flujo de alertas

1. Trabajador toca boton de alerta --> confirmacion --> se inserta en SQLite local (`sync_status = 'pending'`)
2. `SyncManager` sube la alerta a Supabase (upsert)
3. Supabase Realtime emite evento INSERT a todos los suscriptores de la sesion
4. `RealtimeRepository` recibe el evento y emite `AlertEvent` via `SharedFlow`
5. **En campo**: `RoleViewModel` refresca datos al recibir el evento
6. **Notificacion Android**: `GpsTrackingService` muestra notificacion heads-up para:
   - Jefe de Brigada: todas las alertas
   - Chofer: solo alertas dirigidas a "chofer" o "all"
   - El emisor no recibe notificacion de su propia alerta

### Fallback sin Realtime

Si el WebSocket se desconecta, `RoleViewModel` aumenta la frecuencia de polling:
- Realtime activo: refresco cada 10 segundos
- Realtime caido: refresco cada 5 segundos

---

## Mapa offline con PMTiles

### Cadena de datos

1. **Generacion** (una vez): `scripts/generate_pmtiles.sh` usa Planetiler para extraer tiles de OpenStreetMap y `pmtiles` CLI para empaquetar
2. **Almacenamiento**: archivo `rioja.pmtiles` (~11 MB comprimido) en Supabase Storage bucket `map-tiles`
3. **Descarga**: `PmTilesManager` descarga al primer inicio con streaming a disco (evita cargar 50-150 MB en memoria). Descarga atomica: escribe a `.tmp` y renombra al completar
4. **Servicio local**: `LocalTileServer` (Ktor CIO en puerto aleatorio) sirve el archivo PMTiles con soporte HTTP Range y CORS habilitado

### Renderizado

| Plataforma | Motor | Implementacion |
|-----------|-------|----------------|
| Android | MapLibre Android SDK 11.8.0 | `AndroidView` con `MapView` nativo. GeoJSON source + CircleLayer con colores por rol |
| Desktop | MapLibre GL JS 4.7.1 | WebView con HTML/JS generado. Popups en HTML. Misma paleta de colores |

### Colores de marcadores por rol

| Rol | Color |
|-----|-------|
| Nebulizador | Verde (#2ecc71) |
| Jefe de Brigada | Azul (#3498db) |
| Anotador | Morado (#9b59b6) |
| Chofer | Naranja (#f39c12) |

---

## Exportacion de datos

### CSV (`ExportRepository`)

Al exportar una sesion desde el panel admin:

1. Consulta Supabase para obtener datos completos (sesion, GPS, alertas, manzanas, usuarios)
2. Genera CSV con 4 secciones:
   - `## SESION`: metadatos (nombre, inicio, fin, duracion, conteos)
   - `## PUNTOS_GPS`: id, usuario, cargo, lat, lon, precision, timestamp
   - `## ALERTAS`: id, tipo, descripcion, emisor, mensaje, coords, atendida, atendida_por
   - `## MANZANAS`: id, asignado_a, cargo, asignado_por, nombre_manzana, notas, timestamp
3. Guarda en `{appFilesDir}/exports/{nombre_sesion}_{id8chars}.csv`
4. Marca la sesion como exportada (`export_done = true`)

### Eliminacion de GPS del servidor

Despues de exportar, el admin puede eliminar todos los GPS tracks de una sesion en Supabase para liberar almacenamiento. Esta accion es **irreversible**.

---

## Stack tecnologico

| Componente | Tecnologia | Version |
|-----------|------------|---------|
| Lenguaje | Kotlin (Multiplatform) | 2.1.20 |
| UI | Compose Multiplatform | 1.7.0 |
| Base de datos local | SQLDelight | 2.3.2 |
| Backend | Supabase (Auth, Postgrest, Realtime, Storage) | 3.4.1 |
| HTTP Client/Server | Ktor | 3.4.0 |
| GPS Android | Google Play Services Location | 21.3.0 |
| Mapas Android | MapLibre Android SDK | 11.8.0 |
| Mapas Desktop | MapLibre GL JS (via WebView) | 4.7.1 |
| WebView Desktop | compose-webview-multiplatform | 1.9.40 |
| Tile server local | Ktor Server CIO | 3.4.0 |
| Serialization | kotlinx-serialization-json | 1.7.3 |
| Date/Time | kotlinx-datetime | 0.6.1 |
| Coroutines | kotlinx-coroutines | 1.8.1 |
| Build | Gradle + AGP | 8.11.1 / 8.9.1 |
| JDK | Java | 17 |

---

## Estructura del proyecto

```
seg_gps_nebul/
|
+-- composeApp/                          # Modulo de aplicacion (UI)
|   +-- src/
|   |   +-- commonMain/kotlin/.../
|   |   |   +-- App.kt                  # Navegacion raiz: Auth -> Worker/Admin
|   |   |   +-- screens/
|   |   |       +-- HomeScreen.kt       # Router por rol -> JefeScreen, etc.
|   |   |       +-- auth/
|   |   |       |   +-- AuthScreen.kt   # UI login/registro/admin
|   |   |       |   +-- AuthViewModel.kt # Estado y validacion de auth
|   |   |       +-- admin/
|   |   |       |   +-- AdminScreen.kt  # Panel admin 3 pestanas
|   |   |       |   +-- AdminViewModel.kt
|   |   |       +-- role/
|   |   |       |   +-- RoleViewModel.kt  # ViewModel compartido (sesion, alertas, sync, map)
|   |   |       |   +-- JefeScreen.kt
|   |   |       |   +-- NebulizadorScreen.kt
|   |   |       |   +-- AnotadorScreen.kt
|   |   |       |   +-- ChoferScreen.kt
|   |   |       |   +-- AlertButtons.kt       # Grid de botones de alerta
|   |   |       |   +-- MapSharedComponents.kt # Componentes de mapa reutilizables
|   |   |       |   +-- SyncStatusIndicator.kt # Badge de pendientes/sync
|   |   |       +-- map/
|   |   |           +-- MapScreen.kt           # Contenedor del mapa
|   |   |           +-- MapViewModel.kt        # Estado del mapa (posiciones, tiles)
|   |   |           +-- MapLibreView.kt        # expect (multiplataforma)
|   |   |
|   |   +-- androidMain/kotlin/.../
|   |   |   +-- MainActivity.kt        # Permisos, lifecycle, inicia GPS service
|   |   |   +-- GpsTrackingService.kt  # Foreground service GPS+IMU fusion
|   |   |   +-- notifications/
|   |   |   |   +-- AlertNotificationHelper.kt  # Notificaciones heads-up
|   |   |   +-- screens/map/
|   |   |   |   +-- MapLibreView.android.kt     # actual: MapLibre nativo
|   |   |   +-- map/
|   |   |       +-- LocalTileServer.kt          # Ktor server para PMTiles
|   |   |
|   |   +-- desktopMain/kotlin/.../
|   |       +-- main.kt               # Entry point: Window + AppContainer.init
|   |       +-- screens/map/
|   |       |   +-- MapLibreView.desktop.kt     # actual: WebView + HTML/JS
|   |       +-- map/
|   |           +-- LocalTileServer.kt          # Ktor server para PMTiles
|   |
|   +-- keystore/                      # Keystore para firma de release APK
|   +-- proguard-rules.pro             # ProGuard: preserva serialization, Supabase, Ktor
|   +-- build.gradle.kts
|
+-- shared/                            # Modulo de logica compartida (sin UI)
|   +-- src/
|   |   +-- commonMain/kotlin/.../
|   |   |   +-- AppContainer.kt        # Service locator / DI (singleton)
|   |   |   +-- Platform.kt            # expect: deteccion de plataforma
|   |   |   +-- SupabaseConfig.kt      # URL + anon key de Supabase
|   |   |   +-- domain/model/
|   |   |   |   +-- User.kt            # Data class User + enum UserRole (4 roles)
|   |   |   |   +-- AlertType.kt       # Enum AlertType (6 tipos) + SUPPLY_TYPES, WORKER_ALERTS
|   |   |   +-- data/
|   |   |   |   +-- PinHasher.kt       # SHA-256 con salt para PINs
|   |   |   |   +-- local/
|   |   |   |   |   +-- DatabaseDriverFactory.kt  # expect: crea SqlDriver
|   |   |   |   |   +-- LocalDataSource.kt        # Wrapper sobre queries SQLDelight
|   |   |   |   +-- remote/
|   |   |   |       +-- SupabaseClient.kt          # Lazy init del cliente Supabase
|   |   |   |       +-- AuthRepository.kt          # Login worker (local), admin (Supabase), registro
|   |   |   |       +-- GpsSyncRepository.kt       # Sync GPS tracks a Supabase
|   |   |   |       +-- AlertSyncRepository.kt     # Sync sesiones, alertas, manzanas
|   |   |   |       +-- SyncManager.kt             # Orquestador de sync con retry/backoff
|   |   |   |       +-- RealtimeRepository.kt      # WebSocket: suscripcion a INSERT en alerts
|   |   |   |       +-- AdminRepository.kt         # CRUD admin: fetch users/sessions, stats, toggle active
|   |   |   |       +-- ExportRepository.kt        # Genera CSV desde Supabase, guarda en disco
|   |   |   +-- location/
|   |   |   |   +-- LocationPoint.kt               # Data class (lat, lon, accuracy, timestamp)
|   |   |   |   +-- ImuSample.kt                   # Data class (timestamp, aNorth, aEast, dt)
|   |   |   |   +-- KalmanLocationFilter.kt        # EKF 4-estado + Mat4 (matriz 4x4)
|   |   |   |   +-- ComplementaryLocationFilter.kt # Filtro complementario GPS+IMU
|   |   |   |   +-- LocationFusionEngine.kt        # Orquesta EKF + Complementario
|   |   |   |   +-- DeviceIdProvider.kt            # expect: ID unico del dispositivo
|   |   |   +-- connectivity/
|   |   |   |   +-- ConnectivityObserver.kt        # expect: monitoreo de red
|   |   |   +-- map/
|   |   |       +-- PmTilesManager.kt              # Descarga PMTiles desde Supabase Storage
|   |   |       +-- AppFilesDir.kt                 # expect: directorio de archivos de la app
|   |   |
|   |   +-- androidMain/kotlin/.../
|   |   |   +-- Platform.android.kt
|   |   |   +-- data/local/DatabaseDriverFactory.android.kt  # Android SQLite driver
|   |   |   +-- location/DeviceIdProvider.android.kt         # SharedPreferences UUID
|   |   |   +-- location/ImuProvider.android.kt              # SensorManager accelerometer
|   |   |   +-- connectivity/ConnectivityObserver.android.kt # ConnectivityManager
|   |   |   +-- map/AppFilesDir.android.kt                   # context.filesDir
|   |   |
|   |   +-- desktopMain/kotlin/.../
|   |   |   +-- Platform.desktop.kt
|   |   |   +-- data/local/DatabaseDriverFactory.desktop.kt  # JdbcSqliteDriver (~/.seggps/)
|   |   |   +-- location/DeviceIdProvider.desktop.kt         # UUID persistido en archivo
|   |   |   +-- connectivity/ConnectivityObserver.desktop.kt # java.net DNS check
|   |   |   +-- map/AppFilesDir.desktop.kt                   # ~/.seggps/
|   |   |
|   |   +-- commonTest/kotlin/.../                # Tests multiplataforma
|   |   |   +-- domain/model/
|   |   |   |   +-- UserRoleTest.kt       # fromString, displayName, entries
|   |   |   |   +-- AlertTypeTest.kt      # fromValue, labelFor, SUPPLY_TYPES, WORKER_ALERTS
|   |   |   |   +-- UserTest.kt           # Equality, copy, nullable fields
|   |   |   +-- location/
|   |   |       +-- Mat4Test.kt           # Diagonal, multiplicacion, transpose, EKF propagation
|   |   |       +-- KalmanLocationFilterTest.kt       # Init, predict, update, reset, convergencia
|   |   |       +-- ComplementaryLocationFilterTest.kt # Blend alpha, clamp, damping
|   |   |       +-- LocationFusionEngineTest.kt       # Modos EKF/Complementario, reset, accuracy
|   |   |
|   |   +-- desktopTest/kotlin/.../               # Tests JVM (SQLite, crypto)
|   |       +-- data/
|   |           +-- PinHasherTest.kt      # Hash SHA-256, verify, salt
|   |           +-- LocalDataSourceTest.kt # CRUD completo de 5 tablas con SQLite in-memory
|   |
|   +-- build.gradle.kts
|
+-- supabase/
|   +-- config.toml                    # Configuracion del proyecto Supabase
|   +-- migrations/
|       +-- 20260327000001_schema.sql  # Tablas preliminares
|       +-- 20260328000000_initial.sql # Schema completo: tablas, tipos, indices, Realtime
|       +-- 20260328000001_rls.sql     # Politicas RLS iniciales
|       +-- 20260328000002_seed_users.sql  # Datos de ejemplo para whitelist
|       +-- 20260330000001_tighten_rls.sql # RLS reforzado (v2)
|
+-- scripts/
|   +-- generate_pmtiles.sh            # Script para generar tiles de Rioja
|   +-- planetiler-j17.jar             # Planetiler (generador de tiles)
|   +-- pmtiles.exe                    # CLI PMTiles (Windows)
|   +-- rioja.pmtiles                  # Archivo de tiles generado (~11 MB)
|   +-- data/                          # Archivos fuente OSM
|
+-- Imagenes/
|   +-- icono.ico                      # Icono de la app
|   +-- logo app.jpg                   # Logo
|
+-- gradle/
|   +-- libs.versions.toml            # Catalogo de versiones centralizado
|   +-- wrapper/
|       +-- gradle-wrapper.properties  # Gradle 8.11.1
|
+-- build.gradle.kts                   # Root build (plugins)
+-- settings.gradle.kts                # Modulos: composeApp, shared
+-- gradle.properties                  # JVM args, parallel, KMP config
```

---

## Base de datos

### Esquema local (SQLDelight)

7 tablas definidas en `SegGpsDatabase.sq`:

| Tabla | PK | Descripcion | sync_status |
|-------|----|-------------|-------------|
| `allowed_users` | `dni` | Whitelist de DNIs autorizados para registro | -- |
| `users` | `id` (UUID) | Trabajadores registrados. PIN hasheado con SHA-256 + salt. `is_active` para activar/desactivar | -- |
| `sessions` | `id` (UUID) | Jornadas de trabajo. `is_active`, `ended_at`, `export_done` | -- |
| `gps_tracks` | `id` (UUID) | Puntos GPS capturados cada 5 seg. lat, lon, accuracy, captured_at | Si |
| `alerts` | `id` (UUID) | Alertas de suministro y mensajes. `alert_type`, `is_attended`, `attended_by` | Si |
| `block_assignments` | `id` (UUID) | Asignacion de manzanas a trabajadores. `block_name`, `notes` | Si |
| `vector_layers` | `id` (UUID) | Capas GIS subidas por admin (KML, GeoJSON, GPKG). Uso futuro | -- |

### Esquema remoto (Supabase/PostgreSQL)

Mismo esquema con tipos PostgreSQL (`user_role`, `alert_type`, `target_role`, `sync_status`, `layer_file_type`) e indices de performance:

```sql
CREATE INDEX idx_gps_tracks_user_session ON gps_tracks(user_id, session_id);
CREATE INDEX idx_gps_tracks_captured_at  ON gps_tracks(captured_at);
CREATE INDEX idx_gps_tracks_sync_status  ON gps_tracks(sync_status);
CREATE INDEX idx_alerts_session          ON alerts(session_id);
CREATE INDEX idx_alerts_sync_status      ON alerts(sync_status);
CREATE INDEX idx_sessions_active         ON sessions(is_active);
```

Realtime habilitado en: `gps_tracks`, `alerts`, `sessions`, `block_assignments`.

### Seguridad (RLS v2)

| Tabla | `anon` (trabajadores) | `authenticated` (admin) |
|-------|-----------------------|------------------------|
| `allowed_users` | SELECT (validar whitelist) | ALL |
| `users` | INSERT (registro) | ALL |
| `sessions` | INSERT + UPDATE (sync) | ALL |
| `gps_tracks` | INSERT + UPDATE (sync) | ALL |
| `alerts` | SELECT + INSERT + UPDATE (Realtime + sync) | ALL |
| `block_assignments` | INSERT + UPDATE (sync) | ALL |
| `vector_layers` | -- | ALL |

Principio: los trabajadores solo pueden **escribir** datos para sync y **leer** alertas (necesario para Realtime). No pueden leer usuarios, GPS tracks ni sesiones desde el servidor -- todo se lee localmente.

---

## Tests

106 pruebas unitarias en el modulo `shared`:

| Suite | Tests | Cobertura |
|-------|-------|-----------|
| `LocalDataSourceTest` | 30 | CRUD completo de 5 tablas con SQLite in-memory |
| `KalmanLocationFilterTest` | 15 | Init, predict, update, reset, convergencia, precision |
| `ComplementaryLocationFilterTest` | 12 | Blend alpha, clamp, damping, reset |
| `LocationFusionEngineTest` | 12 | Modos EKF/Complementario, IMU feed, accuracy |
| `AlertTypeTest` | 10 | fromValue, labelFor, SUPPLY_TYPES, WORKER_ALERTS |
| `PinHasherTest` | 9 | Hash SHA-256, verify, salt, determinismo |
| `Mat4Test` | 8 | Diagonal, multiplicacion, transpose, propagacion EKF |
| `UserRoleTest` | 6 | fromString, displayName, case-sensitivity |
| `UserTest` | 4 | Data class equality, copy, nullable fields |

Ejecutar tests:

```bash
./gradlew :shared:desktopTest
```

---

## Requisitos previos

- **JDK 17+**
- **Android SDK 35** (compileSdk) con minSdk 24
- **Gradle 8.11.1** (incluido via wrapper)
- Cuenta en [Supabase](https://supabase.com) con proyecto configurado
- (Opcional) [Supabase CLI](https://supabase.com/docs/guides/cli) para gestionar migraciones

---

## Configuracion

### 1. Supabase

Crear un proyecto en Supabase y ejecutar las migraciones en orden:

```bash
supabase db push --project-ref <tu-project-ref>
```

Esto aplica:
- `20260327000001_schema.sql` -- tablas preliminares
- `20260328000000_initial.sql` -- schema completo con tipos, indices y Realtime
- `20260328000001_rls.sql` -- politicas RLS iniciales
- `20260328000002_seed_users.sql` -- datos de ejemplo para whitelist
- `20260330000001_tighten_rls.sql` -- RLS reforzado (reemplaza las politicas iniciales)

Crear los buckets de Storage manualmente en el Dashboard de Supabase:
- `map-tiles` -- para el archivo PMTiles
- `vector-layers` -- para capas GIS (uso futuro)

### 2. Credenciales

Editar `shared/src/commonMain/.../SupabaseConfig.kt` con la URL y anon key de tu proyecto:

```kotlin
object SupabaseConfig {
    const val URL = "https://<tu-ref>.supabase.co"
    const val ANON_KEY = "<tu-anon-key>"
}
```

### 3. Admin

Crear un usuario administrador en Supabase Auth (Dashboard > Authentication > Users > Add User) con email y contrasena. Este usuario accede al panel de administracion en Desktop.

### 4. Whitelist de trabajadores

Insertar los DNIs autorizados en la tabla `allowed_users`:

```sql
INSERT INTO allowed_users (dni, phone_number) VALUES
  ('12345678', '912345678'),
  ('87654321', '987654321')
ON CONFLICT (dni) DO NOTHING;
```

Solo los trabajadores cuyo DNI este en esta tabla podran registrarse en la app.

### 5. Mapa PMTiles

Subir el archivo `rioja.pmtiles` al bucket `map-tiles` en Supabase Storage. La app lo descargara automaticamente al primer inicio.

Para generar un nuevo archivo PMTiles de otra zona:

```bash
cd scripts/
./generate_pmtiles.sh   # Requiere Java 17+
```

---

## Build

```bash
# Android debug APK
./gradlew assembleDebug

# Android release APK (requiere variables de entorno para signing)
KEYSTORE_PATH=composeApp/keystore/release.jks \
KEYSTORE_PASS=... \
KEY_ALIAS=seggps \
KEY_PASS=... \
./gradlew assembleRelease

# Desktop (Windows MSI)
./gradlew packageMsi

# Desktop (Windows EXE)
./gradlew packageExe

# Ejecutar desktop en modo desarrollo
./gradlew :composeApp:run

# Tests
./gradlew :shared:desktopTest
```

### Configuracion de release

- **Android**: firma con keystore en `composeApp/keystore/release.jks`. Alias: `seggps`
- **ProGuard**: activado en release. Reglas en `composeApp/proguard-rules.pro` preservan serialization, Supabase, Ktor, SQLDelight
- **ABIs**: debug incluye x86_64 (emulador) + arm64-v8a; release solo arm64-v8a + armeabi-v7a
- **Desktop**: distribucion MSI/EXE con icono, menu Start, upgrade UUID fijo

---

## Flujo de uso

### Registro de trabajador (primera vez)

1. El trabajador abre la app y selecciona "Registrarse"
2. Ingresa su **DNI**, nombre completo, selecciona su **rol** y crea un **PIN de 4 digitos**
3. La app verifica el DNI contra la whitelist `allowed_users` (local primero, luego Supabase si hay red)
4. Si esta autorizado, se crea la cuenta local (PIN hasheado con SHA-256 + salt `seg_gps_nebul_v1`)
5. El ID del dispositivo se asocia al usuario

### Jornada de trabajo tipica

1. El **Jefe de Brigada** inicia la app, hace login con DNI + PIN
2. Inicia una **jornada** (sesion) con nombre descriptivo
3. El **GPS comienza a registrar** posiciones cada 5 segundos (foreground service con fusion GPS+IMU)
4. Todos los trabajadores ven el **mapa** con las posiciones de los demas miembros en tiempo real
5. El **Nebulizador** solicita insumos tocando los botones de alerta --> el **Chofer** recibe la solicitud ordenada por distancia
6. El **Jefe** asigna manzanas a los trabajadores y envia mensajes broadcast
7. El **Anotador** ve su manzana asignada y registra datos
8. Al finalizar, el Jefe cierra la jornada --> se sincroniza todo a Supabase

### Administrador (Desktop)

1. Login con email/contrasena de Supabase Auth
2. Pestana **Trabajadores**: ver lista, activar/desactivar cuentas
3. Pestana **Jornadas**: ver estadisticas, exportar CSV, limpiar GPS del servidor
4. Pestana **Sync**: estado de conectividad, conteos pendientes, ultima sincronizacion, sync manual

---

## Dependencia entre servicios (`AppContainer`)

`AppContainer` es el service locator global que inicializa y conecta todos los componentes:

```
DatabaseDriverFactory
  |
  +--> SegGpsDatabase --> LocalDataSource
          |                    |
          |    +---------------+-------------------+
          |    |               |                   |
          v    v               v                   v
     AuthRepository    GpsSyncRepository    AlertSyncRepository
                            |                      |
                            +----------+-----------+
                                       |
                                       v
                                  SyncManager
                                       |
     ConnectivityObserver ------------>+
          |                            |
          v                            v
     RealtimeRepository         syncAll() on connect
```

Al detectar conexion (`isOnline = true`):
1. Conecta Realtime WebSocket
2. Suscribe a la sesion activa
3. Ejecuta `SyncManager.syncAll()`

---

## Licencia

Uso interno -- Red de Salud Rioja / UNGET, San Martin, Peru.
