-- ============================================================
-- Migración inicial — Sistema GPS Nebulización
-- Proyecto: seg_gps_nebul | Rioja, San Martín, Perú
-- Fecha: 2026-03-28
-- ============================================================

-- Extensiones
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- allowed_users: whitelist DNIs cargada por admin
-- ============================================================
CREATE TABLE IF NOT EXISTS allowed_users (
    dni             TEXT PRIMARY KEY,
    phone_number    TEXT NOT NULL,
    loaded_at       TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- users: trabajadores registrados
-- ============================================================
CREATE TYPE user_role AS ENUM ('jefe_brigada', 'nebulizador', 'anotador', 'chofer');

CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dni             TEXT UNIQUE NOT NULL REFERENCES allowed_users(dni),
    phone_number    TEXT NOT NULL,
    full_name       TEXT NOT NULL,
    role            user_role NOT NULL,
    pin             TEXT NOT NULL,
    device_id       TEXT,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- sessions: jornadas de trabajo
-- ============================================================
CREATE TABLE IF NOT EXISTS sessions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            TEXT NOT NULL,
    brigade_code    TEXT,
    started_by      UUID REFERENCES users(id),
    started_at      TIMESTAMPTZ DEFAULT NOW(),
    ended_at        TIMESTAMPTZ,
    is_active       BOOLEAN DEFAULT true,
    export_done     BOOLEAN DEFAULT false
);

-- ============================================================
-- gps_tracks: puntos GPS cada 15 segundos
-- ============================================================
CREATE TYPE sync_status AS ENUM ('pending', 'synced');

CREATE TABLE IF NOT EXISTS gps_tracks (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id),
    session_id      UUID NOT NULL REFERENCES sessions(id),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    accuracy        FLOAT,
    captured_at     TIMESTAMPTZ NOT NULL,
    sync_status     sync_status DEFAULT 'pending'
);

-- ============================================================
-- alerts: alertas y mensajes
-- ============================================================
CREATE TYPE alert_type AS ENUM (
    'agua', 'gasolina', 'insumo_quimico',
    'averia_maquina', 'trabajo_finalizado', 'broadcast_text'
);
CREATE TYPE target_role AS ENUM ('all', 'chofer');

CREATE TABLE IF NOT EXISTS alerts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id       UUID NOT NULL REFERENCES users(id),
    session_id      UUID NOT NULL REFERENCES sessions(id),
    alert_type      alert_type NOT NULL,
    message         TEXT,
    target_role     target_role DEFAULT 'all',
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    is_attended     BOOLEAN DEFAULT false,
    attended_by     UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    sync_status     sync_status DEFAULT 'pending'
);

-- ============================================================
-- block_assignments: asignación de manzanas
-- ============================================================
CREATE TABLE IF NOT EXISTS block_assignments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id      UUID NOT NULL REFERENCES sessions(id),
    assigned_to     UUID NOT NULL REFERENCES users(id),
    assigned_by     UUID NOT NULL REFERENCES users(id),
    block_name      TEXT NOT NULL,
    notes           TEXT,
    assigned_at     TIMESTAMPTZ DEFAULT NOW(),
    sync_status     sync_status DEFAULT 'pending'
);

-- ============================================================
-- vector_layers: capas vectoriales cargadas por admin
-- ============================================================
CREATE TYPE layer_file_type AS ENUM ('kml', 'gpkg', 'geojson');

CREATE TABLE IF NOT EXISTS vector_layers (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id      UUID REFERENCES sessions(id),
    name            TEXT NOT NULL,
    file_type       layer_file_type NOT NULL,
    storage_path    TEXT NOT NULL,
    uploaded_at     TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- Índices de performance
-- ============================================================
CREATE INDEX idx_gps_tracks_user_session ON gps_tracks(user_id, session_id);
CREATE INDEX idx_gps_tracks_captured_at ON gps_tracks(captured_at);
CREATE INDEX idx_gps_tracks_sync_status ON gps_tracks(sync_status);
CREATE INDEX idx_alerts_session ON alerts(session_id);
CREATE INDEX idx_alerts_sync_status ON alerts(sync_status);
CREATE INDEX idx_sessions_active ON sessions(is_active);

-- ============================================================
-- Realtime: habilitar para tablas clave
-- ============================================================
ALTER PUBLICATION supabase_realtime ADD TABLE gps_tracks;
ALTER PUBLICATION supabase_realtime ADD TABLE alerts;
ALTER PUBLICATION supabase_realtime ADD TABLE sessions;
ALTER PUBLICATION supabase_realtime ADD TABLE block_assignments;

-- ============================================================
-- Storage buckets (ejecutar en Supabase Dashboard o via CLI)
-- INSERT INTO storage.buckets (id, name, public) VALUES ('map-tiles', 'map-tiles', false);
-- INSERT INTO storage.buckets (id, name, public) VALUES ('vector-layers', 'vector-layers', false);
-- ============================================================
