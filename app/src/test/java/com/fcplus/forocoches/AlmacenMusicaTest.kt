package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AlmacenMusicaTest {
    @get:Rule val carpeta = TemporaryFolder()

    private var hora = 1000L
    private fun almacen(dir: File = carpeta.root) = AlmacenMusica(dir) { hora++ }

    private fun cancion(n: Int) = Cancion(
        RefCancion(FuenteMusica.YOUTUBE, "id%08d".format(n)),
        "Tema $n", "Artista", null, 200, null, 0L
    )

    @Test fun primeraCancionCreaFavoritas() {
        val a = almacen()
        assertTrue(a.guardar(cancion(1), emptyList()))
        val e = a.estado()
        assertEquals(1, e.playlists.size)
        assertEquals(Musica.FAVORITAS, e.playlists[0].nombre)
        assertEquals(listOf(cancion(1).ref.clave), e.playlists[0].claves)
        assertEquals("Tema 1", e.canciones[cancion(1).ref.clave]?.titulo)
    }

    @Test fun sinIdsVaAFavoritasAunqueHayaOtras() {
        val a = almacen()
        val otra = a.crearPlaylist("Rock")!!
        a.guardar(cancion(1), emptyList())
        val e = a.estado()
        assertEquals(listOf("Rock", Musica.FAVORITAS), e.playlists.map { it.nombre })
        assertTrue(e.playlists.first { it.id == otra.id }.claves.isEmpty())
        assertEquals(1, e.playlists.first { it.nombre == Musica.FAVORITAS }.claves.size)
    }

    @Test fun conIdsVaSoloAEsasYIgnoraDesconocidos() {
        val a = almacen()
        val p = a.crearPlaylist("Rock")!!
        assertTrue(a.guardar(cancion(1), listOf(p.id, "no-existe")))
        val e = a.estado()
        assertEquals(1, e.playlists.size)
        assertEquals(1, e.playlists[0].claves.size)
    }

    @Test fun idaYVuelta() {
        val a = almacen()
        val p = a.crearPlaylist("Rock")!!
        a.guardar(Cancion(cancion(1).ref, "Ñu", "Ar", "Al", 99, "http://x/p.jpg", 5L), listOf(p.id))
        val sp = Cancion(RefCancion(FuenteMusica.SPOTIFY, "4uLU6hMCjMI75M1A2tKUQC"), "Sp", null, null, null, null, 0L)
        a.guardar(sp, listOf(p.id))
        a.marcarPublicada(p.id, "pub1")
        assertEquals(a.estado(), almacen().estado())
        val c = almacen().estado().canciones.getValue(cancion(1).ref.clave)
        assertEquals("Al", c.album)
        assertEquals(99, c.duracionSeg)
        assertEquals("Ñu", c.titulo)
        assertEquals(FuenteMusica.SPOTIFY, almacen().estado().canciones.getValue(sp.ref.clave).ref.fuente)
        assertEquals("pub1", almacen().estado().playlists[0].publicadaId)
    }

    @Test fun noRepiteEnLaMisma() {
        val a = almacen()
        val p = a.crearPlaylist("Rock")!!
        assertTrue(a.guardar(cancion(1), listOf(p.id)))
        assertTrue(a.guardar(cancion(1), listOf(p.id)))
        assertEquals(1, a.estado().playlists[0].claves.size)
    }

    @Test fun topeDe200() {
        val a = almacen()
        val p = a.crearPlaylist("Rock")!!
        for (i in 1..Musica.MAX_CANCIONES) assertTrue(a.guardar(cancion(i), listOf(p.id)))
        assertFalse(a.guardar(cancion(201), listOf(p.id)))
        val e = a.estado()
        assertEquals(200, e.playlists[0].claves.size)
        assertFalse(e.canciones.containsKey(cancion(201).ref.clave))
    }

    @Test fun topeLlenoEnUnDestinoNoCambiaNada() {
        val a = almacen()
        val llena = a.crearPlaylist("Llena")!!
        val libre = a.crearPlaylist("Libre")!!
        for (i in 1..200) a.guardar(cancion(i), listOf(llena.id))
        assertFalse(a.guardar(cancion(300), listOf(libre.id, llena.id)))
        assertTrue(a.estado().playlists.first { it.id == libre.id }.claves.isEmpty())
    }

    @Test fun topeDe50Playlists() {
        val a = almacen()
        for (i in 1..Musica.MAX_PLAYLISTS) assertNotNull(a.crearPlaylist("P$i"))
        assertNull(a.crearPlaylist("Una mas"))
        assertNull(a.importar("Otra", listOf(cancion(1))))
        assertEquals(50, a.estado().playlists.size)
    }

    @Test fun crearConNombreInvalidoORepetido() {
        val a = almacen()
        assertNull(a.crearPlaylist("   "))
        assertEquals("Rock", a.crearPlaylist("Rock")!!.nombre)
        assertEquals("Rock (2)", a.crearPlaylist(" Rock ")!!.nombre)
    }

    @Test fun renombrar() {
        val a = almacen()
        val p = a.crearPlaylist("Rock")!!
        a.crearPlaylist("Pop")
        assertTrue(a.renombrar(p.id, "  Rock   duro "))
        assertEquals("Rock duro", a.estado().playlists[0].nombre)
        assertFalse(a.renombrar(p.id, "Pop"))
        assertFalse(a.renombrar(p.id, ""))
        assertFalse(a.renombrar("nope", "X"))
        assertTrue(a.renombrar(p.id, "Rock duro"))
    }

    @Test fun borrarQuitaHuerfanas() {
        val a = almacen()
        val p1 = a.crearPlaylist("Uno")!!
        val p2 = a.crearPlaylist("Dos")!!
        a.guardar(cancion(1), listOf(p1.id))
        a.guardar(cancion(2), listOf(p1.id, p2.id))
        assertTrue(a.borrarPlaylist(p1.id))
        val e = a.estado()
        assertEquals(listOf(p2.id), e.playlists.map { it.id })
        assertFalse(e.canciones.containsKey(cancion(1).ref.clave))
        assertTrue(e.canciones.containsKey(cancion(2).ref.clave))
        assertFalse(a.borrarPlaylist(p1.id))
    }

    @Test fun quitarCancionYHuerfana() {
        val a = almacen()
        val p = a.crearPlaylist("Uno")!!
        a.guardar(cancion(1), listOf(p.id))
        assertFalse(a.quitar(p.id, "youtube:otra"))
        assertTrue(a.quitar(p.id, cancion(1).ref.clave))
        assertTrue(a.estado().canciones.isEmpty())
        assertTrue(a.estado().playlists[0].claves.isEmpty())
    }

    @Test fun moverReordena() {
        val a = almacen()
        val p = a.crearPlaylist("Uno")!!
        for (i in 1..4) a.guardar(cancion(i), listOf(p.id))
        val c = { i: Int -> cancion(i).ref.clave }
        assertTrue(a.mover(p.id, 0, 2))
        assertEquals(listOf(c(2), c(3), c(1), c(4)), a.estado().playlists[0].claves)
        assertTrue(a.mover(p.id, 3, 0))
        assertEquals(listOf(c(4), c(2), c(3), c(1)), a.estado().playlists[0].claves)
        assertFalse(a.mover(p.id, 0, 4))
        assertFalse(a.mover(p.id, -1, 1))
        assertFalse(a.mover("nope", 0, 1))
    }

    @Test fun importarConNombreRepetidoYCorta() {
        val a = almacen()
        a.crearPlaylist("X")
        val p = a.importar("X", (1..250).map { cancion(it) })!!
        assertEquals("X (2)", p.nombre)
        assertEquals(200, p.claves.size)
        val e = a.estado()
        assertEquals(200, e.canciones.size)
        assertEquals(p, e.playlists.first { it.id == p.id })
        assertNull(a.importar("  ", listOf(cancion(1))))
    }

    @Test fun marcarPublicada() {
        val a = almacen()
        val p = a.crearPlaylist("X")!!
        assertTrue(a.marcarPublicada(p.id, "abc"))
        assertEquals("abc", a.estado().playlists[0].publicadaId)
        assertTrue(a.marcarPublicada(p.id, null))
        assertNull(a.estado().playlists[0].publicadaId)
        assertFalse(a.marcarPublicada("nope", "z"))
    }

    @Test fun lamarcaGuardaLaCuentaQuePublicaYSeBorraConEllaYLeeFicherosAntiguos() {
        val a = almacen()
        val p = a.crearPlaylist("X")!!
        assertTrue(a.marcarPublicada(p.id, "abc", "111"))
        assertEquals("111", almacen().estado().playlists[0].publicadaPor)
        assertTrue(a.marcarPublicada(p.id, null))
        assertNull(a.estado().playlists[0].publicadaPor)
        // Fichero escrito antes de existir `publicadaPor`: carga, y la marca queda sin dueña.
        File(carpeta.root, "musica.json").writeText(
            """{"v":1,"canciones":[],"playlists":[{"id":"i","nombre":"Vieja","claves":[],"creada":1,"modificada":1,"publicadaId":"pubV"}]}"""
        )
        val v = almacen().estado().playlists.single()
        assertEquals("pubV", v.publicadaId)
        assertNull(v.publicadaPor)
    }

    @Test fun fechasDelReloj() {
        hora = 5000
        val a = almacen()
        val p = a.crearPlaylist("X")!!
        assertEquals(5000L, p.creada)
        a.guardar(cancion(1), listOf(p.id))
        val e = a.estado()
        assertTrue(e.playlists[0].modificada > p.creada)
        assertTrue(e.canciones.getValue(cancion(1).ref.clave).anadida >= 5000L)
    }

    @Test fun ficheroRotoNoPierdeNada() {
        File(carpeta.root, "musica.json").writeText("esto no es json {{{")
        val a = almacen()
        val e = a.estado()
        assertTrue(e.playlists.isEmpty() && e.canciones.isEmpty())
        assertEquals("esto no es json {{{", File(carpeta.root, "musica.json.roto").readText())
        assertTrue(a.guardar(cancion(1), emptyList()))
        assertEquals(1, almacen().estado().playlists.size)
    }

    @Test fun sinFicheroEstadoVacio() {
        val e = almacen(File(carpeta.root, "nueva/sub")).estado()
        assertTrue(e.playlists.isEmpty())
    }

    @Test fun escrituraAtomica() {
        val a = almacen()
        a.guardar(cancion(1), emptyList())
        a.guardar(cancion(2), emptyList())
        assertFalse(File(carpeta.root, "musica.json.tmp").exists())
        assertTrue(File(carpeta.root, "musica.json").isFile)
        assertTrue(File(carpeta.root, "musica.json").readText().contains("\"v\""))
    }

    private fun jsonDe(a: AlmacenMusica): String {
        return File(carpeta.root, "musica.json").readText()
    }

    @Test fun soloTmpValidoSeRecuperaYSePromueve() {
        val a = almacen()
        a.guardar(cancion(1), emptyList())
        val f = File(carpeta.root, "musica.json")
        val tmp = File(carpeta.root, "musica.json.tmp")
        tmp.writeText(jsonDe(a))
        assertTrue(f.delete())
        val e = almacen().estado()
        assertEquals(1, e.playlists.size)
        assertEquals(1, e.canciones.size)
        assertTrue(f.isFile)
        assertFalse(tmp.exists())
    }

    @Test fun tmpBasuraConMainAusenteDaVacio() {
        File(carpeta.root, "musica.json.tmp").writeText("{{{")
        assertTrue(almacen().estado().playlists.isEmpty())
    }

    @Test fun guardarSobreExistenteNoDejaTmp() {
        val a = almacen()
        repeat(3) { a.guardar(cancion(it + 1), emptyList()) }
        assertFalse(File(carpeta.root, "musica.json.tmp").exists())
        assertEquals(3, almacen().estado().canciones.size)
    }

    @Test fun hilosConcurrentesNoPierdenCanciones() {
        val p = almacen().crearPlaylist("Rock")!!
        val errores = java.util.concurrent.atomic.AtomicInteger()
        val hilos = (0 until 4).map { h ->
            Thread {
                // Una instancia propia por hilo: el cerrojo es por carpeta, no por objeto.
                val a = almacen()
                for (i in 0 until 25) if (!a.guardar(cancion(h * 25 + i + 1), listOf(p.id))) errores.incrementAndGet()
            }
        }
        hilos.forEach { it.start() }
        hilos.forEach { it.join() }
        assertEquals(0, errores.get())
        val e = almacen().estado()
        assertEquals(100, e.playlists[0].claves.size)
        assertEquals(100, e.canciones.size)
    }

    @Test fun ficheroRotoQueNoSePuedeApartarNoRecursa() {
        File(carpeta.root, "musica.json").writeText("basura")
        // .roto es un directorio no vacío: ni delete ni renameTo pueden sustituirlo.
        val roto = File(carpeta.root, "musica.json.roto")
        assertTrue(roto.mkdirs())
        File(roto, "x").writeText("x")
        val e = almacen().estado()
        assertTrue(e.playlists.isEmpty() && e.canciones.isEmpty())
        assertEquals("basura", File(carpeta.root, "musica.json").readText())
    }

    @Test fun ficheroRotoQueNoSePuedeApartarNoSePisaAlEscribir() {
        val main = File(carpeta.root, "musica.json")
        main.writeText("basura")
        val roto = File(carpeta.root, "musica.json.roto")
        assertTrue(roto.mkdirs())
        File(roto, "x").writeText("x")
        val a = almacen()
        assertFalse(a.guardar(cancion(1), emptyList()))
        assertNull(a.crearPlaylist("X"))
        assertNull(a.importar("Y", listOf(cancion(2))))
        assertEquals("basura", main.readText())
        assertFalse(File(carpeta.root, "musica.json.tmp").exists())
    }
}
