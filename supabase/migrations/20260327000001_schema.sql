-- ============================================================
-- Schema principal — GPS Nebulización Rioja
-- ============================================================

CREATE TABLE IF NOT EXISTS allowed_users (
    dni TEXT NOT NULL PRIMARY KEY,
    phone_number TEXT NOT NULL,
    loaded_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)
);

CREATE TABLE IF NOT EXISTS users (
    id UUID NOT NULL PRIMARY KEY,
    dni TEXT NOT NULL UNIQUE,
    phone_number TEXT NOT NULL,
    full_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('jefe_brigada','nebulizador','anotador','chofer')),
    pin TEXT NOT NULL,
    device_id TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)
);

CREATE TABLE IF NOT EXISTS sessions (
    id UUID NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    brigade_code TEXT,
    started_by UUID NOT NULL,
    started_at BIGINT NOT NULL,
    ended_at BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    export_done BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS gps_tracks (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy DOUBLE PRECISION,
    captured_at BIGINT NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'pending' CHECK (sync_status IN ('pending','synced'))
);

CREATE TABLE IF NOT EXISTS alerts (
    id UUID NOT NULL PRIMARY KEY,
    sender_id UUID NOT NULL,
    session_id UUID NOT NULL,
    alert_type TEXT NOT NULL,
    message TEXT,
    target_role TEXT NOT NULL DEFAULT 'all',
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    is_attended BOOLEAN NOT NULL DEFAULT FALSE,
    attended_by UUID,
    created_at BIGINT NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'pending'
);

CREATE TABLE IF NOT EXISTS block_assignments (
    id UUID NOT NULL PRIMARY KEY,
    session_id UUID NOT NULL,
    assigned_to UUID NOT NULL,
    assigned_by UUID NOT NULL,
    block_name TEXT NOT NULL,
    notes TEXT,
    assigned_at BIGINT NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'pending'
);

CREATE TABLE IF NOT EXISTS vector_layers (
    id UUID NOT NULL PRIMARY KEY,
    session_id UUID,
    name TEXT NOT NULL,
    file_type TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    uploaded_at BIGINT NOT NULL
);

-- Indexes for frequent queries
CREATE INDEX IF NOT EXISTS idx_gps_tracks_sync ON gps_tracks(sync_status) WHERE sync_status = 'pending';
CREATE INDEX IF NOT EXISTS idx_gps_tracks_user_session ON gps_tracks(user_id, session_id);
CREATE INDEX IF NOT EXISTS idx_sessions_active ON sessions(is_active) WHERE is_active = TRUE;
