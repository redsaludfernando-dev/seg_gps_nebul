# CLAUDE.md — Guía operativa para Claude Code

> Este archivo es la guía vinculante para cualquier asistente (Claude / agentes) que trabaje en este repositorio. El `README.md` describe el producto; este documento describe **cómo se trabaja sobre el código**. El `SKILL.md` describe **qué skills cargar** para asistir mejor en el trabajo.
>
> Si una instrucción de aquí entra en conflicto con un mensaje del usuario, **prevalece el usuario**. Si entra en conflicto con el `README.md`, **prevalece este archivo** (el README puede quedar desactualizado).

---

## 1. Propósito del proyecto

`seg_gps_nebul` es un sistema de seguimiento GPS en tiempo real para brigadas de nebulización contra el dengue de la Red de Salud Rioja (San Martín, Perú). Es **Kotlin Multiplatform** con **dos targets activos** y exclusivos:

- **Android** (`composeApp/androidMain` + `shared/androidMain`): app de campo para Jefe de Brigada, Nebulizador, Anotador y Chofer. Captura GPS + IMU, alertas, asignaciones, sync offline-first contra Supabase.
- **Web (wasmJs)** (`composeApp/wasmJsMain` + `shared/wasmJsMain`): Panel Admin (sólo Administrador), publicado en GitHub Pages. CRUD de trabajadores, jornadas, manzanas, zonas (KML), geovisor con tracks históricos y export CSV.

**No hay target Desktop.** Cualquier referencia residual a `desktopMain`, `jvm()`, MSI/EXE/Swing, `JdbcSqliteDriver`, `compose-webview-multiplatform`, etc. debe ser eliminada en cualquier PR que la encuentre.

### Onboarding rápido (lo que un asistente debe leer primero)

1. `README.md` — producto, roles, arquitectura conceptual.
2. Este archivo (`CLAUDE.md`) — convenciones, comandos, reglas duras.
3. `SKILL.md` — skills externas y la skill propia del proyecto.
4. `gradle/libs.versions.toml` — versiones autoritativas (no inventar otras).
5. `shared/src/commonMain/kotlin/com/redsalud/seggpsnebul/AppContainer.kt` — punto de entrada del DI (es `expect`; los `actual` están en `androidMain` y `wasmJsMain`).
6. `composeApp/src/commonMain/kotlin/com/redsalud/seggpsnebul/App.kt` — navegación raíz.
7. `supabase/migrations/` — esquema autoritativo de la base de datos remota.

### Estado del proyecto

**Listo para producción**, en mantenimiento y evolución. Cada cambio debe asumir que ya hay usuarios usando la app: no romper el esquema en producción, no introducir migraciones destructivas sin coordinación, no subir secretos.

---

## 2. Stack y estructura

### Stack autoritativo

Las versiones se leen de `gradle/libs.versions.toml`. **Nunca** introducir una versión distinta sin actualizar el catálogo.

| Componente | Versión | Notas |
|---|---|---|
| Kotlin | 2.1.20 | Multiplatform, K2 |
| Compose Multiplatform | 1.7.0 | UI común (Android + wasmJs) |
| AGP | 8.9.1 | Android Gradle Plugin |
| Gradle | 8.11.1 | wrapper |
| JDK | 17 | obligatorio (build y CI) |
| Android compileSdk / minSdk | 35 / 24 | |
| SQLDelight | 2.3.2 | sólo Android |
| Supabase Kotlin | 3.4.1 | auth + postgrest + realtime + storage |
| Ktor | 3.4.0 | client (Android, wasmJs) + server CIO (Android) |
| kotlinx-coroutines | 1.8.1 | core, android, test |
| kotlinx-serialization-json | 1.7.3 | |
| kotlinx-datetime | 0.6.1 declarado, **0.7.1 resuelto** por Supabase | ver §6 |
| MapLibre Android SDK | 11.8.0 | Android nativo |
| MapLibre GL JS | 4.x | Web (wasmJs vía DOM/JS interop) |
| play-services-location | 21.3.0 | sólo Android |

### Estructura de módulos

```
seg_gps_nebul/
├── composeApp/                 # Aplicaciones (UI por target)
│   └── src/
│       ├── commonMain/         # UI compartida: AdminScreen, AuthScreen, MapLibreView (expect), …
│       ├── androidMain/        # MainActivity, GpsTrackingService, MapLibreView nativo, HomeScreen
│       └── wasmJsMain/         # main.kt (wasm entry), MapLibreView via JS, SaveCsv (browser blob)
│
├── shared/                     # Lógica sin UI (KMP library)
│   └── src/
│       ├── commonMain/kotlin/  # Domain models, AppContainer (expect), repos remotos comunes
│       ├── commonMain/sqldelight/  # vacío (los .sq sólo se generan para Android)
│       ├── androidMain/        # AppContainer.actual completo (SQLDelight, Auth, Sync, Export, PMTiles)
│       ├── androidMain/sqldelight/com/redsalud/seggpsnebul/data/local/SegGpsDatabase.sq
│       ├── wasmJsMain/         # AppContainer.actual web (sólo loginAdmin + repos remotos)
│       └── commonTest/         # 106 tests multiplataforma (KMP-test + coroutines-test)
│
├── supabase/
│   ├── config.toml
│   └── migrations/             # 6 migraciones, fechadas YYYYMMDDhhmmss_*.sql
│
├── scripts/                    # generate_pmtiles.sh + binarios IGNORADOS por git
├── .claude/
│   ├── settings.local.json     # permisos de Claude Code (no commitear secretos aquí)
│   └── skills/                 # skills locales del proyecto (ver SKILL.md)
├── .github/workflows/          # deploy-web.yml + supabase-keepalive.yml
├── gradle/libs.versions.toml   # único catálogo de versiones
├── settings.gradle.kts         # incluye :composeApp y :shared, TYPESAFE_PROJECT_ACCESSORS
├── CLAUDE.md                   # este archivo
└── SKILL.md                    # guía de skills
```

### Paquete raíz

`com.redsalud.seggpsnebul` — todo el código va bajo este namespace. El `applicationId` Android coincide.

### Asimetría de targets

- **Android tiene SQLDelight**, web no. SQLDelight se configura en `shared/build.gradle.kts` con `srcDirs.setFrom("src/androidMain/sqldelight")`. **No mover** los `.sq` a `commonMain` sin reconfigurar.
- **Web sólo tiene Supabase remoto**. No hay caché local en wasmJs: lectura/escritura directa contra Supabase con sesión de admin (JWT).
- **Sólo Android tiene GPS + IMU + foreground service + PMTiles + LocalTileServer.**
- Web usa MapLibre GL JS embebido vía DOM/JS interop, no `compose-webview-multiplatform`.

---

## 3. Convenciones de código

### Estilo Kotlin

- `kotlin.code.style=official` (ya en `gradle.properties`). 4 espacios, sin tabs.
- Nombres en inglés para identificadores (`UserRole`, `SyncManager`). Strings de UI y comentarios pueden ir en español.
- Archivos `.kt` con un tipo público por archivo cuando el archivo lleva el nombre del tipo. Excepción: data classes pequeñas relacionadas pueden ir juntas (`GeovisorDtos.kt`).
- `data class` para modelos inmutables. `enum class` con `fromString`/`fromValue` para mapear strings de Supabase (ver `UserRole`, `AlertType`).
- `expect`/`actual` para todo lo que dependa de plataforma (drivers, sensores, FS, conectividad). Nunca usar `Platform.current` para ramificar lógica si se puede declarar `expect`.
- `@OptIn(ExperimentalTime::class)` en archivos que usen `kotlin.time.Clock` (ver §6).

### Funciones y composables

- Funciones cortas, una responsabilidad. Evitar `Unit`-returning con efectos múltiples cuando se puede partir.
- `@Composable` con verbo en imperativo si renderizan UI (`AdminScreen`, `AlertButtons`). `remember`/`mutableStateOf` para estado local; ViewModels (clase plana con `CoroutineScope`, no Android lifecycle) para estado compartido entre composables.
- Pasar callbacks (`onClick`, `onConfirm`) en lugar de inyectar el ViewModel a composables hijos cuando sea factible.

### Coroutines

- Scopes:
  - `MainScope()` o `CoroutineScope(SupervisorJob() + Dispatchers.Main)` para ViewModels en composeApp.
  - `Dispatchers.IO` para SQLDelight y Ktor en Android. **En wasmJs no existe `IO` real** — usar `Dispatchers.Default` o el dispatcher por defecto del cliente Supabase.
  - El `GpsTrackingService` Android usa su propio `CoroutineScope` + cancelación en `onDestroy`.
- **Nunca** `GlobalScope`. **Nunca** `runBlocking` fuera de tests.
- `Flow` y `StateFlow` para streams reactivos. `SharedFlow` con `replay = 0` para eventos one-shot (`AlertEvent`).
- Cancelación cooperativa: respetar `isActive` en bucles largos; usar `withTimeout`/`withTimeoutOrNull` para llamadas de red críticas.

### Patrones de arquitectura

- **Service Locator (no DI framework)**: `AppContainer` (`expect`) construye y retiene singletons. No introducir Koin/Hilt sin discutirlo primero.
- **Repository pattern**: cada agregado tiene un repo (`AuthRepository`, `GpsSyncRepository`, `AlertSyncRepository`, `AdminRepository`, `GeovisorRepository`, `ZonasRepository`, etc.). Los repos son la frontera entre UI y datos.
- **Offline-first (sólo Android)**: escribir a SQLite con `sync_status = 'pending'`, dejar que `SyncManager` resuelva el upload. Nunca bloquear UI esperando red.
- **Realtime**: `RealtimeRepository` emite `SharedFlow<AlertEvent>`; los ViewModels se suscriben. Polling fallback acelera de 10 s a 5 s si el WebSocket cae.
- **expect/actual** como mecanismo principal de adaptación de plataforma.

### Formato y herramientas

- No hay linter configurado en CI por ahora. Aun así: corregir warnings antes de commitear, evitar `@Suppress` salvo justificado en una línea de comentario.
- No agregar comentarios obvios. Comentarios sólo para invariantes no obvios o citar referencias (papers de Kalman, edge cases de Supabase).

---

## 4. Guía de trabajo

### Antes de empezar cualquier cambio

1. `git status` y `git branch` — estar al día con `main`.
2. Confirmar el target objetivo: ¿Android, Web, o `commonMain`?
3. Si toca el esquema → necesita una migración nueva en `supabase/migrations/` (timestamp incremental, nunca editar una migración ya aplicada).
4. Si toca dependencias → editar `gradle/libs.versions.toml`, no los `build.gradle.kts` directamente.

### Verificación obligatoria antes de declarar "listo"

| Cambio toca... | Comando mínimo de verificación |
|---|---|
| `commonMain` (shared o composeApp) | `./gradlew :shared:compileDebugKotlinAndroid :composeApp:compileKotlinWasmJs` |
| `androidMain` | `./gradlew :composeApp:assembleDebug` |
| `wasmJsMain` | `./gradlew :composeApp:wasmJsBrowserDistribution` |
| Lógica de dominio o filtros | `./gradlew :shared:allTests` |
| Migración SQL | `supabase db push --dry-run` antes de aplicar |

**No marques una tarea como completada sin haber compilado los targets afectados.** Si no se puede compilar localmente (ej. falta el SDK Android), decirlo explícitamente al usuario, no asumir que funciona.

### Tests

- Suite en `shared/src/commonTest`: 106 tests (Mat4, Kalman, Complementary, Fusion, AlertType, UserRole, User, PinHasher).
- Comando: `./gradlew :shared:allTests` (corre los tests de `commonTest` en cada target compatible).
- No hay suite de DB con SQLite in-memory hoy (era parte del antiguo `desktopTest`, eliminado). Si se necesita, plantear al usuario crear un `androidUnitTest` con Robolectric — no introducirlo silenciosamente.

### Dependencias

- **Añadir una dep**: agregar entrada en `[libraries]` del catálogo + `implementation(libs.x)` en el `build.gradle.kts` correcto. **No usar coordinates literales** (`"io.foo:bar:1.2.3"`).
- **Subir versión**: cambiar sólo en `[versions]`. Compilar Android, wasmJs y `shared:allTests`.
- Antes de upgrade mayor de Supabase o kotlinx-datetime: leer §6 (rompen API silenciosamente).
- No introducir Hilt, Koin, Retrofit, OkHttp, Room ni librerías que dupliquen lo existente.

### Git y commits

- `main` es la rama de producción. **Cada push a `main` dispara el deploy de Pages** (`deploy-web.yml`). No pushear a `main` código que no compile en wasmJs.
- Para features grandes: rama `feat/<nombre-corto>`, PR a `main` con `gh pr create`.
- Commit messages: conventional commits en español funcionan bien para este repo (`feat:`, `fix:`, `chore:`, `refactor:`). El historial reciente usa scope opcional (`feat(admin):`, `fix(geovisor):`).
- **Nunca** `git push --force` a `main`. Nunca `--no-verify`. Nunca commits con secretos.
- Crear commits sólo cuando el usuario lo pida explícitamente.

### CLI requeridas (verificar al inicio si dudas)

| CLI | Uso | Verificación |
|---|---|---|
| `git` | control de versiones | `git --version` |
| `gh` | GitHub (PRs, secrets, runs de Actions) | `gh auth status` debe mostrar `redsaludfernando-dev` logueado |
| `supabase` | migraciones, info del proyecto | `supabase projects list` debe listar `seg-gps-nebul` linkeado (`mltudqhjsqmnospewgxa`) |
| `adb` (opcional) | debug Android | `adb devices` |
| `./gradlew` | build (wrapper, **no** `gradle` global) | `./gradlew --version` |
| `java -version` | debe reportar 17 | |

Si una CLI falla autenticación, **no intentar reautenticar automáticamente** — pedir al usuario que ejecute `! gh auth login` o `! supabase login` en el prompt.

### Compilación del APK Android — manual desde Android Studio

**El usuario compila el APK manualmente desde Android Studio.** Claude no debe ejecutar `assembleRelease` ni intentar firmar APKs en local: el keystore (`composeApp/keystore/release.jks`) y sus passwords (`KEYSTORE_PASS`, `KEY_ALIAS`, `KEY_PASS`) son secretos y no están en el repo. Claude puede:

- Correr `./gradlew :composeApp:assembleDebug` para verificar que compila (sin firmar).
- Sugerir al usuario abrir Android Studio y ejecutar **Build → Generate Signed Bundle / APK** cuando se quiera un release.
- Nunca pedir, intentar adivinar, ni almacenar secretos de firma.
- Nunca añadir un workflow de CI que firme APKs.

### CI/CD activo

- `deploy-web.yml`: en push a `main`, build wasmJs (`wasmJsBrowserDistribution`) y deploy a Pages. Usa secrets `SUPABASE_URL` y `SUPABASE_ANON_KEY` del repo.
- `supabase-keepalive.yml`: cron diario 06:00 UTC, ping a la API REST para evitar pausa del plan free.
- Sólo el deploy web está automatizado. Android es siempre manual.

---

## 5. Reglas que no se tocan

- **No subir secretos**: `local.properties`, `*.jks`, `*.keystore`, `google-services.json`, tokens de Supabase, contraseñas. Ya están en `.gitignore`. Si encuentras uno commiteado, alertar al usuario inmediatamente.
- **No subir binarios grandes**: `scripts/*.jar`, `scripts/*.zip`, `scripts/*.exe`, `scripts/*.pmtiles`, `scripts/data/` están en `.gitignore` (algunos pesan >50 MB). Si necesitas regenerar `rioja.pmtiles`, hacerlo localmente y subirlo al bucket `map-tiles` de Supabase Storage.
- **No editar migraciones aplicadas**. Crear una nueva con timestamp posterior. Las migraciones son el contrato con producción.
- **No bajar versiones del catálogo** sin razón documentada. Subirlas requiere validar Android + wasmJs + tests.
- **No introducir un DI framework** (Hilt/Koin) ni un ORM (Room/Exposed). El proyecto usa service locator + SQLDelight a propósito.
- **No agregar permisos Android** sin justificación + actualizar `AndroidManifest.xml` + revisar Play Store policies.
- **No reactivar Desktop**. El target fue eliminado. Si se necesita un panel de escritorio, va por la web.
- **No usar emojis en código** salvo que estén ya en literales de UI (los hay en algunos labels de alerta).
- **No crear archivos de planning, notas o docs** (`Plan.md`, `NOTES.md`, etc.) salvo que el usuario lo pida.

### `.gitignore` — qué debe quedar fuera

El `.gitignore` actual cubre:

```
.gradle/, build/, *.iml, .idea/, .kotlin/
local.properties
*.class, *.jks, *.keystore, google-services.json
.DS_Store
/composeApp/build/, /shared/build/
scripts/data/, scripts/*.jar, scripts/*.zip, scripts/*.exe, scripts/*.pmtiles
!gradle/wrapper/gradle-wrapper.jar
composeApp/release/*.apk, composeApp/release/baselineProfiles/
**/*.js.map
```

Antes de commitear, verifica que ningún `.kt`/`.kts`/`.toml` nuevo haya quedado fuera, y que ningún binario o `.env`/`.properties` con secretos haya entrado.

---

## 6. Edge cases conocidos

- **kotlinx-datetime 0.6.1 declarado, 0.7.1 resuelto** por la fuerza de Supabase 3.4.x. Esto migra `Clock`/`Instant` a `kotlin.time`. Usar `kotlin.time.Clock` con `@OptIn(ExperimentalTime::class)`. No bajar Supabase para "arreglar" esto.
- **Supabase 3.4.x API**:
  - `select(count = Count.EXACT)` → `select { count(Count.EXACT) }`.
  - `update()`/`delete()` retornan `PostgrestResult` (no `Unit`) — añadir `Unit` explícito en `runCatching {}`.
  - `postgresChangeFlow` filter: `filter("col", FilterOperator.EQ, value)`, no string crudo.
  - Importar `io.github.jan.supabase.realtime.realtime` explícitamente.
- **SQLDelight + K2**: propiedades nullable de cross-module no smart-castean. Usar `?.let {}` o variables locales.
- **wasmJs**: `Dispatchers.IO` no existe; ningún acceso a filesystem; el "device id" es un UUID generado en `localStorage`; CSV se descarga vía `URL.createObjectURL`.
- **PMTiles** descarga atómica (`.tmp` + rename). No hacer la descarga en el hilo principal.

---

## 7. Subagentes y división de roles

Para tareas grandes, usar agentes especializados como subprocesos del razonamiento — no como personas distintas. Roles sugeridos para *este* repo:

| Rol | Cuándo invocar | Alcance |
|---|---|---|
| **backend** (Supabase + repos) | esquema, RLS, migraciones, repos en `shared/data/remote/`, `SyncManager`, `RealtimeRepository` | sólo `shared/`, `supabase/migrations/`. No toca UI. |
| **android** | `composeApp/androidMain`, `shared/androidMain`, `GpsTrackingService`, MapLibre nativo, notificaciones, sensores | sólo lo que vive en androidMain o requiere SDK Android. |
| **web** | `composeApp/wasmJsMain`, `shared/wasmJsMain`, MapLibre GL JS embebido, panel admin | sólo lo que vive en wasmJsMain o `commonMain` con foco en navegador. |
| **ui-common** | `composeApp/commonMain`, screens compartidas (Auth, Admin), navegación raíz | UI Compose Multiplatform que sirve a Android y Web. |
| **infra / CI** | `.github/workflows/`, `build.gradle.kts`, `libs.versions.toml`, ProGuard, gitignore, deploy a Pages | nunca tocar lógica de negocio. |
| **test** | `shared/commonTest`, futuros tests instrumentados de Android | sólo tests; si falta cobertura, proponer pero no forzar refactor de prod para "facilitar tests". |

Para una **feature** que cruza targets: planear en `commonMain` primero (ui-common + backend), luego implementar adapters en `android` y `web` en paralelo. Para **refactor**: backend + test antes que ui-common. Para **CI/CD**: infra solo, sin tocar código de producción en el mismo PR.

Reglas para cualquier subagente:

- Recibe contexto explícito (path, líneas, qué cambiar) en el prompt — no se le delega "entender" el problema.
- Devuelve un reporte breve, no un mensaje al usuario final.
- Si necesita decidir entre alternativas con tradeoffs reales, devuelve la pregunta al hilo principal en lugar de elegir.

---

## 8. Comandos CLI rápidos

```bash
# Compilación / verificación
./gradlew :shared:allTests                           # tests comunes
./gradlew :composeApp:compileKotlinWasmJs            # compila web sin empaquetar
./gradlew :composeApp:wasmJsBrowserDistribution      # build web producción → composeApp/build/dist/wasmJs/productionExecutable
./gradlew :composeApp:assembleDebug                  # APK debug (sin firmar)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun    # servidor de desarrollo web

# Supabase
supabase projects list                               # confirmar link
supabase db diff                                     # ver diff con remoto
supabase db push                                     # aplicar migraciones nuevas
supabase migration new <nombre>                      # crear archivo nuevo

# GitHub
gh auth status
gh pr create --base main --head <rama> --title "..." --body "..."
gh run list --workflow=deploy-web.yml --limit 5
gh secret list                                       # secrets del repo

# Android (manual)
adb devices
adb install composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

---

## 9. Cuando dudes

- Versión de una librería → `gradle/libs.versions.toml`.
- Esquema de tabla → `supabase/migrations/` (la última que la toque).
- Cómo se inicializa algo → `AppContainer` del target correspondiente.
- Cómo se navega → `composeApp/.../App.kt` y `screens/HomeScreen.kt`.
- Skills externas o de proyecto → `SKILL.md`.
- Si una decisión cambia el contrato con producción (esquema, deploy, secrets) → **preguntar al usuario antes de actuar**.

---

*Última revisión tras purga de target Desktop. Si la realidad del repo difiere de este documento, gana el repo — y este archivo necesita actualización.*
