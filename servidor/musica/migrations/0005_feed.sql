-- Feed de canciones de 24 h (sustituye a la semana y a las playlists públicas).
-- Una publicación = una canción + un texto corto de una cuenta; vive VIDA_MS desde `creada` y luego se borra.
CREATE TABLE publicaciones (
  id TEXT PRIMARY KEY,
  cuenta TEXT NOT NULL,
  cancion TEXT NOT NULL,
  texto TEXT NOT NULL,
  creada INTEGER NOT NULL,
  caduca INTEGER NOT NULL,
  oculta INTEGER NOT NULL DEFAULT 0,
  corazones INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_publicaciones_caduca ON publicaciones (caduca);
-- El tope de 6 en 24 h y el borrado de cuenta leen por cuenta.
CREATE INDEX idx_publicaciones_cuenta ON publicaciones (cuenta, creada);

-- Un corazón por cuenta y publicación; `publicaciones.corazones` se recuenta, no se suma.
CREATE TABLE corazones (
  publicacion TEXT NOT NULL,
  cuenta TEXT NOT NULL,
  creado INTEGER NOT NULL,
  PRIMARY KEY (publicacion, cuenta)
);
-- "Los corazones que dio esta cuenta" (borrar cuenta).
CREATE INDEX idx_corazones_cuenta ON corazones (cuenta);

-- La publicación más querida de cada semana, guardada al caducar (hueco para S7).
CREATE TABLE mas_querida (
  semana TEXT PRIMARY KEY,
  cancion TEXT,
  autor TEXT,
  texto TEXT,
  corazones INTEGER
);

-- Latido del conversor del VPS (clave/valor con la hora en ms).
CREATE TABLE conversor (
  clave TEXT PRIMARY KEY,
  valor TEXT,
  en INTEGER
);

-- Canciones: ids de Deezer/Spotify y estado de la conversión a YouTube (la hace el VPS).
-- Las filas que ya existen están convertidas ('hecha'): vienen de enlaces de YouTube/Spotify ya resueltos.
ALTER TABLE canciones ADD COLUMN deezer INTEGER;
ALTER TABLE canciones ADD COLUMN spotify TEXT;
ALTER TABLE canciones ADD COLUMN conversion TEXT NOT NULL DEFAULT 'hecha';
ALTER TABLE canciones ADD COLUMN conversion_en INTEGER;
ALTER TABLE canciones ADD COLUMN conversion_intentos INTEGER NOT NULL DEFAULT 0;

DROP TABLE semanas;
DROP TABLE propuestas;
DROP TABLE votos;
DROP TABLE playlists_pub;
DROP TABLE guardados;
