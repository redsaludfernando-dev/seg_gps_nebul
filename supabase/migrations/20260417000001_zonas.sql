-- Tabla de manzanas/zonas a nebulizar (polígonos GeoJSON)
CREATE TABLE IF NOT EXISTS zonas (
    id          TEXT        PRIMARY KEY,
    nombre      TEXT        NOT NULL,
    color       TEXT        NOT NULL DEFAULT '#e74c3c',
    geojson     JSONB       NOT NULL,  -- GeoJSON Polygon geometry
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE zonas ENABLE ROW LEVEL SECURITY;

-- Lectura pública (trabajadores usan anon key)
CREATE POLICY "zonas_select_all" ON zonas
    FOR SELECT USING (true);

-- Escritura solo para admin autenticado
CREATE POLICY "zonas_write_admin" ON zonas
    FOR ALL USING (auth.role() = 'authenticated')
    WITH CHECK (auth.role() = 'authenticated');
