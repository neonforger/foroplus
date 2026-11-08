-- Resolución 16: el coche que mostraba la ficha al dar el reto (dato público, muere con el reto).
-- `comprobar` no acepta que case si el coche del reto ya estaba puesto antes de darlo: un acierto
-- así no prueba que nadie tocara el perfil. NULL = sin coche en la ficha, o reto dado en modo VPS
-- (el Worker no lee FC en ese modo).
ALTER TABLE retos ADD COLUMN coche_antes TEXT;
