-- Canción de la semana (B6). D1 cobra por filas LEÍDAS, no por consultas:
-- 1) El tope de 10 votos cuenta los votos de UNA cuenta en la semana (en cada voto y en /v1/yo). Sin
--    este índice, cada voto recorre todos los votos de la semana de todo el mundo.
CREATE INDEX idx_votos_cuenta_semana ON votos (cuenta, semana);
-- 2) El cierre perezoso (`asegurarCierres`) corre en cada GET /v1/semana, proponer y votar, y busca
--    semanas pasadas aún abiertas. Con este índice parcial lee solo las abiertas (la en curso y, un
--    lunes, la que toca cerrar), no todas las semanas de la historia.
CREATE INDEX idx_semanas_abiertas ON semanas (id) WHERE cerrada = 0;
