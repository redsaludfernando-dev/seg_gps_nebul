-- ============================================================
-- Seed: whitelist de trabajadores autorizados
-- ============================================================
-- IMPORTANTE: Reemplazar estos datos de ejemplo con los DNIs
-- y telefonos reales antes del despliegue en produccion.
-- Usar: supabase db execute < seed_real.sql (no commitear PII)
-- ============================================================

INSERT INTO allowed_users (dni, phone_number, loaded_at) VALUES
('00000001', '900000001',  NOW()),
('00000002', '900000002',  NOW()),
('00000003', '900000003',  NOW()),
('00000004', '900000004',  NOW()),
('00000005', '900000005',  NOW())
ON CONFLICT (dni) DO NOTHING;
