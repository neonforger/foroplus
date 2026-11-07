package com.fcplus.forocoches

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Las canciones y playlists del usuario: un solo `musica.json` en [dir] (`"v": 1`).
 *
 * Mismo criterio que [AlmacenDescargas]: vive en `filesDir` (lo da quien lo construye), se
 * escribe en un temporal y se renombra, para que morir a mitad no deje un fichero a medias.
 * Todo método que cambia algo lee, modifica y escribe entero; devuelve false/null sin tocar
 * el disco si la regla no se cumple. Sin sincronización: se usa desde un solo hilo.
 *
 * Un fichero que no se puede leer NO se pisa: se aparta como `musica.json.roto` y se parte de
 * vacío, así una versión futura o un fallo de disco no se llevan por delante las playlists.
 */
class AlmacenMusica(
    private val dir: File,
    private val reloj: () -> Long = System::currentTimeMillis
) {
    private val fichero get() = File(dir, "musica.json")

    fun estado(): EstadoMusica = leer()

    /**
     * Guarda [c] en las playlists [playlistIds] (los ids desconocidos se ignoran). Con la lista
     * vacía va a "Mis favoritas", que se crea si hace falta. False, sin cambiar nada, si algún
     * destino ya está en [Musica.MAX_CANCIONES]; si ya la tenía, esa playlist se deja como está.
     */
    fun guardar(c: Cancion, playlistIds: List<String>): Boolean {
        val e = leer()
        val ahora = reloj()
        var listas = e.playlists
        val destinos: List<String> = if (playlistIds.isEmpty()) {
            val fav = listas.firstOrNull { it.nombre == Musica.FAVORITAS }
            if (fav != null) listOf(fav.id) else {
                if (listas.size >= Musica.MAX_PLAYLISTS) return false
                val nueva = Playlist(nuevoId(), Musica.FAVORITAS, emptyList(), ahora, ahora)
                listas = listas + nueva
                listOf(nueva.id)
            }
        } else playlistIds.filter { id -> listas.any { it.id == id } }.distinct()
        val clave = c.ref.clave
        val llenas = listas.any {
            it.id in destinos && clave !in it.claves && it.claves.size >= Musica.MAX_CANCIONES
        }
        if (llenas) return false
        val canciones = e.canciones + (clave to c.copy(anadida = e.canciones[clave]?.anadida ?: ahora))
        listas = listas.map {
            if (it.id in destinos && clave !in it.claves) it.copy(claves = it.claves + clave, modificada = ahora)
            else it
        }
        return escribir(EstadoMusica(canciones, listas))
    }

    fun crearPlaylist(nombre: String): Playlist? {
        val limpio = Musica.nombreLimpio(nombre) ?: return null
        val e = leer()
        if (e.playlists.size >= Musica.MAX_PLAYLISTS) return null
        val ahora = reloj()
        val p = Playlist(
            nuevoId(), Musica.nombreLibre(limpio, e.playlists.map { it.nombre }),
            emptyList(), ahora, ahora
        )
        return if (escribir(e.copy(playlists = e.playlists + p))) p else null
    }

    /** False si el nombre no vale o ya lo lleva OTRA playlist. */
    fun renombrar(id: String, nombre: String): Boolean {
        val limpio = Musica.nombreLimpio(nombre) ?: return false
        val e = leer()
        if (e.playlists.none { it.id == id }) return false
        if (e.playlists.any { it.id != id && it.nombre == limpio }) return false
        val ahora = reloj()
        return escribir(e.copy(playlists = e.playlists.map {
            if (it.id == id) it.copy(nombre = limpio, modificada = ahora) else it
        }))
    }

    fun borrarPlaylist(id: String): Boolean {
        val e = leer()
        if (e.playlists.none { it.id == id }) return false
        return escribir(sinHuerfanas(e.copy(playlists = e.playlists.filter { it.id != id })))
    }

    fun quitar(id: String, clave: String): Boolean {
        val e = leer()
        val p = e.playlists.firstOrNull { it.id == id } ?: return false
        if (clave !in p.claves) return false
        val ahora = reloj()
        return escribir(sinHuerfanas(e.copy(playlists = e.playlists.map {
            if (it.id == id) it.copy(claves = it.claves - clave, modificada = ahora) else it
        })))
    }

    /** Saca la canción de la posición [desde] y la deja en [hasta] (ambas índices válidos). */
    fun mover(id: String, desde: Int, hasta: Int): Boolean {
        val e = leer()
        val p = e.playlists.firstOrNull { it.id == id } ?: return false
        if (desde !in p.claves.indices || hasta !in p.claves.indices) return false
        val claves = p.claves.toMutableList()
        claves.add(hasta, claves.removeAt(desde))
        val ahora = reloj()
        return escribir(e.copy(playlists = e.playlists.map {
            if (it.id == id) it.copy(claves = claves, modificada = ahora) else it
        }))
    }

    /** Playlist nueva con las primeras [Musica.MAX_CANCIONES] de [canciones]; null si no cabe o el nombre no vale. */
    fun importar(nombre: String, canciones: List<Cancion>): Playlist? {
        val limpio = Musica.nombreLimpio(nombre) ?: return null
        val e = leer()
        if (e.playlists.size >= Musica.MAX_PLAYLISTS) return null
        val ahora = reloj()
        val elegidas = canciones.distinctBy { it.ref.clave }.take(Musica.MAX_CANCIONES)
        val p = Playlist(
            nuevoId(), Musica.nombreLibre(limpio, e.playlists.map { it.nombre }),
            elegidas.map { it.ref.clave }, ahora, ahora
        )
        val todas = e.canciones + elegidas.filter { it.ref.clave !in e.canciones }
            .associate { it.ref.clave to it.copy(anadida = ahora) }
        return if (escribir(EstadoMusica(todas, e.playlists + p))) p else null
    }

    fun marcarPublicada(id: String, publicadaId: String?): Boolean {
        val e = leer()
        if (e.playlists.none { it.id == id }) return false
        return escribir(e.copy(playlists = e.playlists.map {
            if (it.id == id) it.copy(publicadaId = publicadaId) else it
        }))
    }

    private fun nuevoId() = UUID.randomUUID().toString()

    private fun sinHuerfanas(e: EstadoMusica): EstadoMusica {
        val usadas = e.playlists.flatMap { it.claves }.toSet()
        return e.copy(canciones = e.canciones.filterKeys { it in usadas })
    }

    private fun leer(): EstadoMusica {
        val f = fichero
        if (!f.isFile) return EstadoMusica(emptyMap(), emptyList())
        return try {
            val raiz = JSONObject(f.readText())
            val canciones = LinkedHashMap<String, Cancion>()
            val arrC = raiz.optJSONArray("canciones") ?: JSONArray()
            for (i in 0 until arrC.length()) {
                val o = arrC.getJSONObject(i)
                val ref = RefCancion.deClave(o.getString("clave")) ?: continue
                canciones[ref.clave] = Cancion(
                    ref, o.getString("titulo"), texto(o, "artista"), texto(o, "album"),
                    if (o.has("duracionSeg") && !o.isNull("duracionSeg")) o.getInt("duracionSeg") else null,
                    texto(o, "portada"), o.optLong("anadida")
                )
            }
            val playlists = ArrayList<Playlist>()
            val arrP = raiz.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until arrP.length()) {
                val o = arrP.getJSONObject(i)
                val cl = o.getJSONArray("claves")
                // Una clave sin su canción no se puede pintar: se descarta al leer.
                val claves = (0 until cl.length()).map { cl.getString(it) }.filter { it in canciones }
                playlists.add(
                    Playlist(
                        o.getString("id"), o.getString("nombre"), claves,
                        o.optLong("creada"), o.optLong("modificada"), texto(o, "publicadaId")
                    )
                )
            }
            EstadoMusica(canciones, playlists)
        } catch (_: Exception) {
            apartarRoto(f)
            EstadoMusica(emptyMap(), emptyList())
        }
    }

    private fun texto(o: JSONObject, k: String): String? =
        if (o.has(k) && !o.isNull(k)) o.getString(k) else null

    private fun apartarRoto(f: File) {
        try {
            val roto = File(dir, "musica.json.roto")
            if (roto.exists()) roto.delete()
            f.renameTo(roto)
        } catch (_: Exception) {
        }
    }

    private fun escribir(e: EstadoMusica): Boolean {
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return false
            val canciones = JSONArray()
            for (c in e.canciones.values) {
                canciones.put(
                    JSONObject()
                        .put("clave", c.ref.clave).put("titulo", c.titulo)
                        .put("artista", c.artista ?: JSONObject.NULL)
                        .put("album", c.album ?: JSONObject.NULL)
                        .put("duracionSeg", c.duracionSeg ?: JSONObject.NULL)
                        .put("portada", c.portada ?: JSONObject.NULL)
                        .put("anadida", c.anadida)
                )
            }
            val playlists = JSONArray()
            for (p in e.playlists) {
                playlists.put(
                    JSONObject()
                        .put("id", p.id).put("nombre", p.nombre)
                        .put("claves", JSONArray(p.claves))
                        .put("creada", p.creada).put("modificada", p.modificada)
                        .put("publicadaId", p.publicadaId ?: JSONObject.NULL)
                )
            }
            val raiz = JSONObject().put("v", 1).put("canciones", canciones).put("playlists", playlists)
            val tmp = File(dir, "musica.json.tmp")
            tmp.writeText(raiz.toString())
            // renameTo sobre un fichero existente falla en Windows y en algunos Android.
            if (fichero.exists()) fichero.delete()
            tmp.renameTo(fichero)
        } catch (_: Exception) {
            false
        }
    }
}
