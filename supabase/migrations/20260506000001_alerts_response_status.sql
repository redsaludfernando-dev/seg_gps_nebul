-- Estado de respuesta a una alerta. Permite que cualquier miembro de la brigada
-- (chofer, jefe, anotador, nebulizador o el admin) "capture" una alerta marcando
-- "Ya voy" antes de cerrarla con "Atendida".
--
-- Convenciones:
--   response_status = NULL          -> nadie ha respondido todavia (pendiente)
--   response_status = 'on_way'      -> alguien dijo "Ya voy"
--   response_status = 'attended'    -> resuelta (equivalente a is_attended = true)
--
-- attended_by ya guarda quien la cerro. Anadimos response_by para saber quien
-- la "capturo" como "ya voy" (puede ser distinto de quien la cierra finalmente).

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS response_status TEXT
        CHECK (response_status IS NULL OR response_status IN ('on_way','attended')),
    ADD COLUMN IF NOT EXISTS response_by    UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS responded_at   TIMESTAMPTZ;

-- Indice para que las suscripciones realtime / queries del mapa filtren
-- pendientes rapidamente.
CREATE INDEX IF NOT EXISTS alerts_response_status_idx
    ON alerts(response_status);
