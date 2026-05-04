# SKILL.md — Skills para `seg_gps_nebul`

Este documento lista las **skills externas** que Claude Code (u otro agente compatible) debe cargar al trabajar en este repositorio, y la **skill propia** del proyecto que vive en `.claude/skills/seg-gps-nebul/`.

> Sobre nomenclatura: el usuario solicitó originalmente skills llamadas `kotlin-multiplatform-architecture`, `kotlin-expert` y `lang-kotlin-enterprise`. **Esos nombres no existen como tal** en el ecosistema oficial de Anthropic ni en repositorios reconocidos. Lo que sigue son los **mejores equivalentes verificables** publicados por la comunidad y, en su defecto, agentes/skills cuya cobertura solapa la intención. Verifica disponibilidad antes de instalar.

---

## 1. Skills externas recomendadas

### 1.1 KMP Evolution Suite — `ethanzhongch/Agent-Skills`

Repo: <https://github.com/ethanzhongch/Agent-Skills>

Cubre la intención **"kotlin-multiplatform-architecture / Kotlin Multiplatform skills"**. Son tres skills separadas que funcionan mejor juntas:

| Skill | Función |
|---|---|
| `kmp-architecture-skill` | Scaffolding de módulos feature MVI/MVVM con Clean Architecture sobre KMP. |
| `kmp-dependency-init-skill` | Inyecta dependencias esenciales KMP (Koin, Ktor, Coroutines, etc.). |
| `kmp-quality-standards-skill` | Estándares de logging unificado y safety en async. |

**Instalación (Claude Code, scope usuario):**

```bash
git clone https://github.com/ethanzhongch/Agent-Skills.git /tmp/agent-skills
mkdir -p ~/.claude/skills
cp -r /tmp/agent-skills/kmp-architecture-skill ~/.claude/skills/
cp -r /tmp/agent-skills/kmp-dependency-init-skill ~/.claude/skills/
cp -r /tmp/agent-skills/kmp-quality-standards-skill ~/.claude/skills/
```

**Adaptación necesaria para este repo** (importante — la skill genérica empuja patrones que **no usamos**):

- Esta app usa **Service Locator (`AppContainer`)**, NO Koin/Hilt. Si la skill propone Koin, ignorar.
- Esta app NO usa Clean Architecture estricta — sólo Repository pattern + ViewModels planos.
- `kmp-dependency-init-skill` tenderá a sugerir Koin, kotlinx-datetime, Ktor: ya están. **No duplicar entradas en `libs.versions.toml`.**

### 1.2 Compose Multiplatform — `Meet-Miyani/compose-skill`

Repo: <https://github.com/Meet-Miyani/compose-skill>

Skill genérica de Compose Multiplatform (KMP/CMP) con MVI, Navigation 3, Koin/Hilt, Ktor, Room, DataStore, Paging 3, Coil, coroutines/Flow, animaciones, performance, accesibilidad, testing y patrones cross-platform.

**Instalación:**

```bash
git clone https://github.com/Meet-Miyani/compose-skill.git ~/.claude/skills/compose-skill
```

**Adaptación necesaria:**

- Targets activos = **Android + wasmJs**, no iOS/Desktop. Ignorar consejos de SKIE / iOS interop / `packageMsi`.
- No usamos Room, DataStore ni Paging 3 — el caché local es **SQLDelight**.
- Navigation 3: **no implementado**. Hoy la navegación es manual con `when` sobre estado en `App.kt`.

### 1.3 Kotlin Specialist — `VoltAgent/awesome-claude-code-subagents`

Archivo: <https://github.com/VoltAgent/awesome-claude-code-subagents/blob/main/categories/02-language-specialists/kotlin-specialist.md>

Cubre la intención **"kotlin-expert / lang-kotlin-enterprise"**. Es un **subagent** (no una skill estándar), con frontmatter:

```yaml
name: kotlin-specialist
description: Use when building Kotlin applications requiring advanced coroutine patterns, multiplatform code sharing, or Android/server-side development with functional programming principles.
tools: Read, Write, Edit, Bash, Glob, Grep
model: sonnet
```

Cubre coroutines avanzadas, expect/actual, multiplataforma (JVM, Android, JS/WASM), Compose Multiplatform, structured concurrency, testing (JUnit5 + property-based), Detekt + ktlint, Ktor server-side.

**Instalación (como subagent, no skill):**

```bash
mkdir -p ~/.claude/agents
curl -fsSL https://raw.githubusercontent.com/VoltAgent/awesome-claude-code-subagents/main/categories/02-language-specialists/kotlin-specialist.md \
  -o ~/.claude/agents/kotlin-specialist.md
```

**Adaptación:**

- No tenemos Detekt ni ktlint configurados en CI. Si el agente intenta correrlos, decirle que falla y pedir validación humana antes de añadirlos al CI.
- No usamos JUnit 5 en commonTest — usamos `kotlin.test` multiplatform.

### 1.4 Skills oficiales de Anthropic (uso transversal)

Repo: <https://github.com/anthropics/skills>

No hay skills oficiales específicas de Kotlin, pero sí hay útiles transversales:

- `claude-api` — sólo si se decide integrar Claude API en el backoffice (no es el caso hoy).
- Las del marketplace para Markdown/PDF/CSV son irrelevantes a este repo.

---

## 2. Skill propia del proyecto

La skill propia del proyecto vive en:

```
.claude/skills/seg-gps-nebul/SKILL.md
```

Es de **tipo descriptivo** (no ejecuta scripts). Contiene las reglas operativas y de arquitectura que un asistente debe aplicar al trabajar sobre `seg_gps_nebul`. Se carga automáticamente cuando Claude Code arranca en este directorio.

### Cómo se invoca

- Carga automática: Claude Code escanea `.claude/skills/*/SKILL.md` al iniciar.
- Invocación explícita: `/seg-gps-nebul` (o el nombre que corresponda según la versión del cliente).
- Verificación: en una sesión nueva, preguntar "¿está cargada la skill seg-gps-nebul?"; el asistente debe poder citar al menos una regla específica del proyecto (ej. "Android tiene SQLDelight, web no").

### Qué cubre vs. CLAUDE.md

- `CLAUDE.md` → instrucciones humanas (onboarding largo, tablas, comandos).
- `SKILL.md` (propio) → reglas accionables, condensadas, en el formato que un agente recuerda al ejecutar tareas.

Si CLAUDE.md y la skill divergen, **gana CLAUDE.md** (es la fuente humana editable). Mantener ambos sincronizados es responsabilidad de cualquier PR que toque convenciones.

---

## 3. Orden de carga sugerido para una sesión productiva

1. La skill propia (`.claude/skills/seg-gps-nebul/`) — siempre.
2. `kotlin-specialist` (subagent) — para razonar sobre coroutines, expect/actual, refactors.
3. `kmp-architecture-skill` — sólo si se está creando un módulo feature nuevo.
4. `compose-skill` — sólo si se está rediseñando UI compartida.

Cargar las cuatro a la vez es ruidoso. Cargar sólo lo necesario para la tarea.

---

## 4. Política de actualización

- Si una skill externa rompe convenciones del proyecto, **no adoptar el cambio** — comentar en el PR por qué.
- Si una skill externa propone una mejora real (ej. una nueva versión de Kotlin con migración guiada), **abrir un issue** antes de aplicar, no modificar `main` directamente.
- Las skills externas NO se commitean al repo — son configuración de cada desarrollador (`~/.claude/...`). Sólo la skill propia (`.claude/skills/seg-gps-nebul/`) vive en el repo.
