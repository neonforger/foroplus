-- Esquema inicial de Música. Cuentas: solo el HMAC del uid de FC, nunca el uid.
CREATE TABLE cuentas (id TEXT PRIMARY KEY, uid_hmac TEXT UNIQUE NOT NULL, nombre TEXT NOT NULL, registro_fc TEXT, baneada INTEGER DEFAULT 0, creada INTEGER, ultima_verif INTEGER);
CREATE TABLE tokens (hash TEXT PRIMARY KEY, cuenta TEXT NOT NULL, creado INTEGER, caduca INTEGER);
-- El uid va en claro SOLO mientras vive el reto (<= 10 min): hace falta para leer la ficha.
CREATE TABLE retos (id TEXT PRIMARY KEY, uid TEXT NOT NULL, uid_hmac TEXT NOT NULL, coche TEXT NOT NULL, caduca INTEGER, intentos INTEGER DEFAULT 0, estado TEXT DEFAULT 'nuevo', ficha TEXT);
CREATE TABLE canciones (id TEXT PRIMARY KEY, clave TEXT UNIQUE NOT NULL, titulo TEXT, artista TEXT, portada TEXT, isrc TEXT, enlaces TEXT NOT NULL);
CREATE TABLE enlaces_cancion (ref TEXT PRIMARY KEY, cancion TEXT NOT NULL);
CREATE TABLE semanas (id TEXT PRIMARY KEY, cerrada INTEGER DEFAULT 0, top TEXT, cancion_semana TEXT);
CREATE TABLE propuestas (semana TEXT, cancion TEXT, cuenta TEXT, creada INTEGER, PRIMARY KEY (semana, cancion));
CREATE TABLE votos (semana TEXT, cancion TEXT, cuenta TEXT, creado INTEGER, PRIMARY KEY (semana, cancion, cuenta));
CREATE TABLE playlists_pub (id TEXT PRIMARY KEY, cuenta TEXT, nombre TEXT, canciones TEXT, publicada INTEGER, guardados INTEGER DEFAULT 0, oculta INTEGER DEFAULT 0);
CREATE TABLE guardados (playlist TEXT, cuenta TEXT, PRIMARY KEY (playlist, cuenta));
CREATE TABLE denuncias (id TEXT PRIMARY KEY, tipo TEXT, objetivo TEXT, cuenta TEXT, motivo TEXT, creada INTEGER, resuelta INTEGER DEFAULT 0);
CREATE TABLE limites (clave TEXT, ventana INTEGER, n INTEGER, PRIMARY KEY (clave, ventana));
CREATE INDEX idx_votos_semana_cancion ON votos (semana, cancion);
CREATE INDEX idx_propuestas_cuenta_semana ON propuestas (cuenta, semana);
CREATE INDEX idx_playlists_publicada ON playlists_pub (publicada);
