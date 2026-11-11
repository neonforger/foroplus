-- Índices del feed y de la cola de conversión, y la cuenta de la más querida (revisión final del feed).

-- La cola del conversor: el VPS la consulta cada 5 s. Sin índice, cada consulta recorría TODAS las
-- canciones (casi todas 'hecha'); el índice parcial solo guarda las que aún pueden entregarse, así que
-- la consulta lee unas pocas filas aunque el catálogo crezca. RECLAMAR repite la condición del WHERE
-- para que SQLite pueda usarlo.
CREATE INDEX idx_canciones_conv ON canciones (conversion, conversion_en)
  WHERE conversion IN ('pendiente', 'en_curso', 'error');

-- Las dos ordenaciones del feed (Recientes y Top) leen la página en el orden del índice, sin ordenar
-- todas las publicaciones vivas en cada petición.
CREATE INDEX idx_publicaciones_creada ON publicaciones (creada DESC, id DESC);
CREATE INDEX idx_publicaciones_corazones ON publicaciones (corazones DESC, creada DESC, id DESC);

-- De quién era la más querida: borrar la cuenta (o banearla) la quita del cartel. Las filas que ya
-- existen quedan con NULL y se encuentran por el nombre del autor.
ALTER TABLE mas_querida ADD COLUMN cuenta TEXT;
