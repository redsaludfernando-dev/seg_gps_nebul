-- ============================================================
-- RLS v2 — Restricciones de seguridad reforzadas
-- ============================================================
-- Reemplaza las politicas permisivas de 20260328000001_rls.sql.
-- Trabajadores (anon): solo INSERT/UPDATE para sync + SELECT donde
-- Realtime lo requiere.  Admin (authenticated): acceso completo.
-- ============================================================

-- ─── Eliminar politicas permisivas anteriores ────────────────────────

-- allowed_users
DROP POLICY IF EXISTS "allowed_users_select_anon"  ON allowed_users;
DROP POLICY IF EXISTS "allowed_users_all_admin"     ON allowed_users;

-- users
DROP POLICY IF EXISTS "users_select_anon"  ON users;
DROP POLICY IF EXISTS "users_insert_anon"  ON users;
DROP POLICY IF EXISTS "users_all_admin"    ON users;

-- sessions
DROP POLICY IF EXISTS "sessions_select_anon"  ON sessions;
DROP POLICY IF EXISTS "sessions_insert_anon"  ON sessions;
DROP POLICY IF EXISTS "sessions_update_anon"  ON sessions;
DROP POLICY IF EXISTS "sessions_all_admin"    ON sessions;

-- gps_tracks
DROP POLICY IF EXISTS "gps_tracks_select_anon"  ON gps_tracks;
DROP POLICY IF EXISTS "gps_tracks_insert_anon"  ON gps_tracks;
DROP POLICY IF EXISTS "gps_tracks_all_admin"    ON gps_tracks;

-- alerts
DROP POLICY IF EXISTS "alerts_select_anon"  ON alerts;
DROP POLICY IF EXISTS "alerts_insert_anon"  ON alerts;
DROP POLICY IF EXISTS "alerts_update_anon"  ON alerts;
DROP POLICY IF EXISTS "alerts_all_admin"    ON alerts;

-- block_assignments
DROP POLICY IF EXISTS "block_assignments_select_anon"  ON block_assignments;
DROP POLICY IF EXISTS "block_assignments_insert_anon"  ON block_assignments;
DROP POLICY IF EXISTS "block_assignments_all_admin"    ON block_assignments;

-- vector_layers
DROP POLICY IF EXISTS "vector_layers_select_anon"  ON vector_layers;
DROP POLICY IF EXISTS "vector_layers_all_admin"    ON vector_layers;


-- ═════════════════════════════════════════════════════════════
--  NUEVAS POLITICAS RESTRICTIVAS
-- ═════════════════════════════════════════════════════════════

-- ─── allowed_users ───────────────────────────────────────────
-- Anon: solo lectura (validar whitelist durante registro)
CREATE POLICY "allowed_users_select_anon"
    ON allowed_users FOR SELECT TO anon
    USING (true);

CREATE POLICY "allowed_users_all_admin"
    ON allowed_users FOR ALL TO authenticated
    USING (true) WITH CHECK (true);


-- ─── users ───────────────────────────────────────────────────
-- Anon: solo INSERT (registrar nuevo trabajador desde dispositivo).
-- NO puede SELECT (login es local; evita exposicion de PINs hasheados).
CREATE POLICY "users_insert_anon"
    ON users FOR INSERT TO anon
    WITH CHECK (true);

CREATE POLICY "users_all_admin"
    ON users FOR ALL TO authenticated
    USING (true) WITH CHECK (true);


-- ─── sessions ────────────────────────────────────────────────
-- Anon: INSERT + UPDATE (upsert desde sync).
-- No SELECT — las sesiones se gestionan localmente.
CREATE POLICY "sessions_insert_anon"
    ON sessions FOR INSERT TO anon
    WITH CHECK (true);

CREATE POLICY "sessions_update_anon"
    ON sessions FOR UPDATE TO anon
    USING (true) WITH CHECK (true);

CREATE POLICY "sessions_all_admin"
    ON sessions FOR ALL TO authenticated
    USING (true) WITH CHECK (true);


-- ─── gps_tracks ──────────────────────────────────────────────
-- Anon: INSERT + UPDATE (upsert desde sync).
-- No SELECT — los tracks se leen de SQLite local.
CREATE POLICY "gps_tracks_insert_anon"
    ON gps_tracks FOR INSERT TO anon
    WITH CHECK (true);

CREATE POLICY "gps_tracks_update_anon"
    ON gps_tracks FOR UPDATE TO anon
    USING (true) WITH CHECK (true);

CREATE POLICY "gps_tracks_all_admin"
    ON gps_tracks FOR ALL TO authenticated
    USING (true) WITH CHECK (true);


-- ─── alerts ──────────────────────────────────────────────────
-- Anon: INSERT + UPDATE (upsert + marcar atendidas) + SELECT
-- (SELECT requerido por Realtime subscriptions).
CREATE POLICY "alerts_select_anon"
    ON alerts FOR SELECT TO anon
    USING (true);

CREATE POLICY "alerts_insert_anon"
    ON alerts FOR INSERT TO anon
    WITH CHECK (true);

CREATE POLICY "alerts_update_anon"
    ON alerts FOR UPDATE TO anon
    USING (true) WITH CHECK (true);

CREATE POLICY "alerts_all_admin"
    ON alerts FOR ALL TO authenticated
    USING (true) WITH CHECK (true);


-- ─── block_assignments ───────────────────────────────────────
-- Anon: INSERT + UPDATE (upsert desde sync).
-- No SELECT — las asignaciones se leen localmente.
CREATE POLICY "block_assignments_insert_anon"
    ON block_assignments FOR INSERT TO anon
    WITH CHECK (true);

CREATE POLICY "block_assignments_update_anon"
    ON block_assignments FOR UPDATE TO anon
    USING (true) WITH CHECK (true);

CREATE POLICY "block_assignments_all_admin"
    ON block_assignments FOR ALL TO authenticated
    USING (true) WITH CHECK (true);


-- ─── vector_layers ───────────────────────────────────────────
-- Solo admin puede gestionar capas vectoriales.
CREATE POLICY "vector_layers_all_admin"
    ON vector_layers FOR ALL TO authenticated
    USING (true) WITH CHECK (true);
