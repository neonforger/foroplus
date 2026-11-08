-- Canciones ocultas por el dueño (rutas de administración, B8): no salen en la semana en curso, ni en
-- el detalle de una playlist, ni se pueden proponer ni votar. Los tops ya cerrados se quedan como se
-- guardaron.
ALTER TABLE canciones ADD COLUMN oculta INTEGER DEFAULT 0;
