-- ============================================================
-- RLS (Row Level Security) — Sistema GPS Nebulización
-- ============================================================
-- Trabajadores usan anon key (sin Supabase Auth).
-- Administrador usa Supabase Auth → rol 'authenticated'.
-- ============================================================

-- Habilitar RLS en todas las tablas
ALTER TABLE allowed_users     ENABLE ROW LEVEL SECURITY;
ALTER TABLE users              ENABLE ROW LEVEL SECURITY;
ALTER TABLE sessions           ENABLE ROW LEVEL SECURITY;
ALTER TABLE gps_tracks         ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts             ENABLE ROW LEVEL SECURITY;
ALTER TABLE block_assignments  ENABLE ROW LEVEL SECURITY;
ALTER TABLE vector_layers      ENABLE ROW LEVEL SECURITY;

-- ─── allowed_users ────────────────────────────────────────────
-- Lectura pública (anon): para validar whitelist durante registro
CREATE POLICY "allowed_users_select_anon"
    ON allowed_users FOR SELECT TO anon
    USING (true);

-- Solo admin puede insertar/eliminar (gestión de whitelist)
CREATE POLICY "allowed_users_all_admin"
    ON allowed_users FOR ALL TO authenticated
    USING (true) WITH CHECK (true);

-- ─── users ────────────────────────────────────────────────────
-- Anon puede leer (para login DNI+PIN) e insertar (registro)
CREATE POLICY "users_select_anon"
    ON users FOR SELECT TO anon
    USING (true);

CREATE POLICY "users_insert_anon"
    ON users FOR INSERT TO anon
    WITH CHECK (true);

-- Admin puede hacer todo
CREATE POLICY "users_all_admin"
    ON users FOR ALL TO authenticated
    USING (true) WITH CHECK (true);

-- ─── sessions ─────────────────────────────────────────────────
CREATE POLICY "sessions_select_anon"
    ON sessions FOR SELECT TO anon
    USING (true);

CREATE POLICY "sessions_insert_anon"
    ON sessions FOR INSERT TO anon
    WITH CHECK (true);

CREATE POLICY "sessions_update_anon"
    ON sessions FOR UPDATE TO anon
    USING (true) WITH CHECK (true);

CREATE POLICY "sessions_all_admin"
    ON sessions FOR ALL TO authenticated
    USING (true) WITH CHECK (true);

-- ─── gps_tracks ───────────────────────────────────────────────
CREATE POLICY "gps_tracks_select_anon"
    ON gps_tracks FOR SELECT TO anon
    USING (true);

CREATE POLICY "gps_tracks_insert_anon"
    ON gps_tracks FOR INSERT TO anon
    WITH CHECK (true);

CREATE POLICY "gps_tracks_all_admin"
    ON gps_tracks FOR ALL TO authenticated
    USING (true) WITH CHECK (true);

-- ─── alerts ───────────────────────────────────────────────────
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

-- ─── block_assignments ────────────────────────────────────────
CREATE POLICY "block_assignments_select_anon"
    ON block_assignments FOR SELECT TO anon
    USING (true);

CREATE POLICY "block_assignments_insert_anon"
    ON block_assignments FOR INSERT TO anon
    WITH CHECK (true);

CREATE POLICY "block_assignments_all_admin"
    ON block_assignments FOR ALL TO authenticated
    USING (true) WITH CHECK (true);

-- ─── vector_layers ────────────────────────────────────────────
CREATE POLICY "vector_layers_select_anon"
    ON vector_layers FOR SELECT TO anon
    USING (true);

CREATE POLICY "vector_layers_all_admin"
    ON vector_layers FOR ALL TO authenticated
    USING (true) WITH CHECK (true);
