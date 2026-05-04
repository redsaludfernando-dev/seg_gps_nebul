---
name: seg-gps-nebul
description: Reglas vinculantes para asistir en seg_gps_nebul — KMP Android + Web (wasmJs), Supabase offline-first, panel admin GPS de brigadas de nebulización (Rioja, Perú). Activar siempre que se trabaje en este repo.
type: descriptive
version: 1.0.0
scope: project
targets: [android, wasmJs]
package: com.redsalud.seggpsnebul
languages: [kotlin]
keywords: [kotlin-multiplatform, kmp, compose-multiplatform, supabase, sqldelight, ktor, maplibre, gps, offline-first]
license: internal-use
---

# seg-gps-nebul — Skill descriptiva

Esta skill condensa las reglas operativas del proyecto. Si una regla aquí entra en conflicto con `CLAUDE.md`, **gana `CLAUDE.md`** (fuente humana editable). Si entra en conflicto con un mensaje del usuario, **gana el usuario**.

## 0. Identidad

- Producto: app de seguimiento GPS para brigadas de nebulización contra el dengue, Red de Salud Rioja (UNGET, San Martín, Perú).
- Targets activos: **Android** (campo) y **Web wasmJs** (panel admin en GitHub Pages). **NO hay Desktop.** Cualquier referencia a `desktopMain`, `jvm()`, MSI/EXE, Swing, `JdbcSqliteDriver` o `compose-webview-multiplatform` debe ser eliminada al encontrarla.
- Repo: `redsaludfernando-dev/seg_gps_nebul` en GitHub. Backend: Supabase proyecto `mltudqhjsqmnospewgxa` (`seg-gps-nebul`).

## 1. Layout obligatorio

```
composeApp/src/{commonMain,androidMain,wasmJsMain}/kotlin/com/redsalud/seggpsnebul/
shared/src/{commonMain,androidMain,wasmJsMain,commonTest}/kotlin/com/redsalud/seggpsnebul/
shared/src/androidMain/sqldelight/com/redsalud/seggpsnebul/data/local/SegGpsDatabase.sq
supabase/migrations/YYYYMMDDhhmmss_<nombre>.sql
gradle/libs.versions.toml      # ÚNICA fuente de versiones
.github/workflows/             # deploy-web.yml + supabase-keepalive.yml
```

Paquete raíz: `com.redsalud.seggpsnebul`. Mismo `applicationId` Android.

## 2. Stack autoritativo (no inventar versiones)

Kotlin 2.1.20 · Compose Multiplatform 1.7.0 · AGP 8.9.1 · Gradle 8.11.1 · JDK 17 · Android compileSdk 35 / minSdk 24 · SQLDelight 2.3.2 (sólo Android) · Supabase 3.4.1 · Ktor 3.4.0 · coroutines 1.8.1 · serialization-json 1.7.3 · datetime 0.6.1→0.7.1 · MapLibre Android 11.8.0 · MapLibre GL JS 4.x (Web) · play-services-location 21.3.0.

## 3. Reglas duras

1. **Targets**: sólo Android y wasmJs. No reactivar Desktop.
2. **Versiones**: editar **siempre** `gradle/libs.versions.toml`, nunca coordinates literales en `build.gradle.kts`.
3. **DI**: Service Locator (`AppContainer` `expect`/`actual`). Prohibido Hilt/Koin.
4. **Persistencia local**: SQLDelight, sólo en Android. Web sólo lee/escribe contra Supabase.
5. **Offline-first (Android)**: insertar local con `sync_status='pending'` y dejar que `SyncManager` suba. Nunca bloquear UI esperando red.
6. **Realtime**: `RealtimeRepository` → `SharedFlow<AlertEvent>`. Polling fallback 10 s → 5 s si WS cae.
7. **expect/actual** para todo lo dependiente de plataforma. No ramificar con `Platform.current` cuando se puede declarar `expect`.
8. **Coroutines**: prohibido `GlobalScope`, prohibido `runBlocking` (excepto tests). En wasmJs **no usar** `Dispatchers.IO`.
9. **kotlinx-datetime**: usar `kotlin.time.Clock` con `@OptIn(ExperimentalTime::class)`. No bajar Supabase para evitar el upgrade.
10. **Migraciones**: nunca editar una ya aplicada. Crear nueva con timestamp posterior.
11. **APK release**: lo compila el usuario manualmente desde Android Studio. **No** ejecutar `assembleRelease` ni manipular el keystore.
12. **Push a `main`**: dispara deploy de Pages. No pushear código que no compile en wasmJs.
13. **Secrets**: `local.properties`, `*.jks`, tokens, contraseñas — nunca al repo. Si se ven commiteados, alertar al usuario.
14. **Binarios grandes** en `scripts/`: ya gitignorados; no forzar add con `-f`.
15. **No agregar** comentarios obvios, `Plan.md`, `NOTES.md`, ni doc files no pedidos.

## 4. Patrones a aplicar

- `data class` inmutable + `enum class` con `fromString`/`fromValue` para mapear strings de Supabase.
- Repos como frontera UI/datos. Un repo por agregado (`AuthRepository`, `GpsSyncRepository`, `AdminRepository`, `GeovisorRepository`, `ZonasRepository`…).
- ViewModels = clases planas con `CoroutineScope(SupervisorJob() + Dispatchers.Main)`. No `androidx.lifecycle.ViewModel`.
- Composables reciben callbacks; sólo el screen-root tiene el ViewModel.
- `Flow`/`StateFlow` para streams; `SharedFlow(replay=0)` para eventos one-shot.
- `runCatching {}` para llamadas Supabase; recordar que `update()`/`delete()` retornan `PostgrestResult` (añadir `Unit` explícito si se ignora).

## 5. Cómo verificar antes de declarar "listo"

| Cambio | Comando |
|---|---|
| `commonMain` (shared/composeApp) | `./gradlew :shared:compileDebugKotlinAndroid :composeApp:compileKotlinWasmJs` |
| `androidMain` | `./gradlew :composeApp:assembleDebug` |
| `wasmJsMain` | `./gradlew :composeApp:wasmJsBrowserDistribution` |
| Lógica de dominio | `./gradlew :shared:allTests` |
| Migración SQL | `supabase db push --dry-run` antes de aplicar |

No marcar tarea completa sin haber compilado los targets afectados. Si no se puede compilar localmente, decirlo explícitamente.

## 6. CLI esperadas en la sesión

`git`, `gh` (logueado como `redsaludfernando-dev`), `supabase` (linkeado a `mltudqhjsqmnospewgxa`), `./gradlew`, `java 17`, `adb` (opcional). Si una falla, **pedir al usuario** que reautentique con `! gh auth login` o `! supabase login`. Nunca intentar autenticación automática.

## 7. Edge cases que ya costaron tiempo

- Supabase 3.4.x: `select { count(Count.EXACT) }`, `filter("col", FilterOperator.EQ, v)`, importar `io.github.jan.supabase.realtime.realtime` explícito.
- SQLDelight + K2: nullable cross-module no smart-castea — usar `?.let {}` o variable local.
- wasmJs: sin filesystem; device id en `localStorage`; CSV vía `URL.createObjectURL`.
- PMTiles: descarga atómica `.tmp` + rename, fuera del hilo principal.
- GPS Android: 5 s `PRIORITY_HIGH_ACCURACY`. IMU 50 Hz. EKF + Complementario en warm-standby (`LocationFusionEngine.Mode`).

## 8. Subagentes / roles para tareas grandes

| Rol | Alcance |
|---|---|
| **backend** | `shared/data/remote/`, `supabase/migrations/`, RLS, repos, `SyncManager`. |
| **android** | `composeApp/androidMain`, `shared/androidMain`, foreground service, MapLibre nativo, sensores. |
| **web** | `composeApp/wasmJsMain`, `shared/wasmJsMain`, MapLibre GL JS, panel admin. |
| **ui-common** | `composeApp/commonMain` (Auth, Admin, navegación raíz). |
| **infra/CI** | `.github/workflows/`, `build.gradle.kts`, `libs.versions.toml`, ProGuard, gitignore. |
| **test** | `shared/commonTest` y futuros tests instrumentados. |

Para feature cross-target: planear en `commonMain` (ui-common + backend), implementar adapters `android` y `web` en paralelo. Para refactor: backend + test antes que ui-common. Para CI/CD: infra solo, sin tocar lógica de negocio en el mismo PR.

## 9. Cuando dudar, preguntar

Antes de:
- Cambiar el esquema en producción (Supabase remoto).
- Añadir un workflow de CI.
- Subir versión mayor de Supabase, Kotlin, AGP o Compose.
- Introducir una librería que no esté en el catálogo.
- Tocar el keystore o cualquier secret.

→ **Pedir confirmación explícita al usuario.**

---

*Skill alineada con `CLAUDE.md` y con el estado del repo tras la purga del target Desktop.*
