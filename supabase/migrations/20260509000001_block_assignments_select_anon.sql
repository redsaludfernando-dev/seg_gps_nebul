-- ============================================================
-- block_assignments: permitir SELECT a anon
-- ============================================================
-- Causa raiz: la migracion 20260330000001_tighten_rls.sql dejo
-- block_assignments SIN politica SELECT para anon. Los trabajadores
-- (que usan la anon key) no pueden hacer pull de las manzanas que
-- el admin les asigna desde la web; tampoco les llegan los eventos
-- de Realtime sobre esa tabla, porque Realtime hace un SELECT
-- interno para enviar el payload y RLS lo bloquea.
--
-- Efecto observado: desde el celular, las manzanas asignadas por el
-- admin no se ven, ni en pull periodico ni via Realtime.
-- ============================================================

CREATE POLICY "block_assignments_select_anon"
    ON block_assignments FOR SELECT TO anon
    USING (true);
