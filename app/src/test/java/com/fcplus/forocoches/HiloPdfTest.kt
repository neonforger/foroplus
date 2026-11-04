package com.fcplus.forocoches

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exportar una copia descargada a PDF (Juan, 2026-10-30). El HTML de los mensajes imita el de
 * una copia real medida el 2026-11-04: cita con `max-height` inline, foto `imgpost`, emoticono
 * `inlineimg` y la marca U+E002 n U+E003 de un reproductor.
 */
class HiloPdfTest {

    private val cita = """<div class="squote"><blockquote><a href="https://forocoches.com/foro/showthread.php?p=1"><b>uno dijo:</b></a><br>
        <div style="word-wrap:break-word; font-size: 0.813rem; margin-bottom: 38px"><div style="max-height: 410px; overflow-y: auto">texto citado</div></div>
        </blockquote></div>respuesta"""

    private fun post(pid: String, autor: String, html: String, embeds: JSONArray = JSONArray(),
                     editBy: String = "", editDate: String = "") = JSONObject()
        .put("pid", pid).put("author", autor).put("date", "30-oct-2026 22:26").put("html", html)
        .put("embeds", embeds).put("editBy", editBy).put("editDate", editDate)

    private fun copia(vararg paginas: List<JSONObject>) = JSONObject()
        .put("tid", "10852585").put("titulo", "Un hilo <con> \"comillas\"").put("paginas", paginas.size)
        .put("guardadoEn", 1793810000000L)
        .put("contenido", JSONArray().apply {
            paginas.forEachIndexed { i, posts ->
                put(JSONObject().put("page", i + 1).put("posts", JSONArray().apply { posts.forEach { put(it) } }))
            }
        }).toString()

    private val nada: (String) -> Boolean = { false }

    @Test
    fun `numera los mensajes seguidos a traves de las paginas`() {
        val d = HiloPdf.documento(copia(listOf(post("1", "ana", "hola"), post("2", "bea", "adios")),
            listOf(post("3", "carla", "otra"))), nada)!!
        assertTrue(d.html.contains("#1</span> <b>@ana</b>"))
        assertTrue(d.html.contains("#3</span> <b>@carla</b>"))
        assertTrue(d.html.contains("2 páginas · 3 mensajes"))
    }

    @Test
    fun `el titulo y los autores se escapan`() {
        val d = HiloPdf.documento(copia(listOf(post("1", "<b>malo</b>", "x"))), nada)!!
        assertTrue(d.html.contains("<h1>Un hilo &lt;con&gt; &quot;comillas&quot;</h1>"))
        assertTrue(d.html.contains("@&lt;b&gt;malo&lt;/b&gt;"))
    }

    @Test
    fun `las citas no se cortan en papel`() {
        val d = HiloPdf.documento(copia(listOf(post("1", "ana", cita))), nada)!!
        assertTrue(d.html.contains("texto citado"))
        assertTrue(d.html.contains("max-height: none !important"))
    }

    @Test
    fun `una foto guardada se sirve desde la copia y una que no, como enlace`() {
        val html = """<img class="imgpost" src="https://i.imgur.com/a.gif" border="0" alt="">
            <img class="imgpost" src="https://images.forocoches.com?url=x&amp;y=1" alt="">"""
        val d = HiloPdf.documento(copia(listOf(post("1", "ana", html))), { it == "https://i.imgur.com/a.gif" })!!
        assertEquals(listOf("https://i.imgur.com/a.gif"), d.imagenes)
        assertTrue(d.html.contains("src=\"${HiloPdf.URL_IMAGEN}0\""))
        assertFalse(d.html.contains("src=\"https://i.imgur.com/a.gif\""))
        assertTrue(d.html.contains("[foto] https://images.forocoches.com?url=x&amp;y=1"))
    }

    @Test
    fun `la misma foto dos veces usa un solo indice`() {
        val img = """<img class="imgpost" src="https://i.imgur.com/a.gif">"""
        val d = HiloPdf.documento(copia(listOf(post("1", "ana", img), post("2", "bea", img))), { true })!!
        assertEquals(1, d.imagenes.size)
    }

    @Test
    fun `los emoticonos se quedan como estan`() {
        val sm = """<img src="https://forocoches.com/foro/images/smilies/meparto.gif" border="0" alt="" title="qmeparto" class="inlineimg">"""
        val d = HiloPdf.documento(copia(listOf(post("1", "ana", "jaja $sm"))), nada)!!
        assertTrue(d.html.contains(sm))
        assertTrue(d.imagenes.isEmpty())
    }

    @Test
    fun `un reproductor se cambia por su enlace en su sitio`() {
        val embeds = JSONArray().put(JSONObject().put("kind", "instagram").put("url", "https://www.instagram.com/p/X/"))
        val d = HiloPdf.documento(copia(listOf(post("1", "ana",
            "antes ${TramosEmbed.ABRE}0${TramosEmbed.CIERRA} despues", embeds))), nada)!!
        val i = d.html.indexOf("Publicación de Instagram")
        assertTrue(i > d.html.indexOf("antes") && i < d.html.indexOf("despues"))
        assertFalse(d.html.contains(TramosEmbed.ABRE))
    }

    @Test
    fun `un reproductor sin marca sale al final del mensaje`() {
        val embeds = JSONArray().put(JSONObject().put("kind", "youtube").put("url", "https://youtu.be/abc"))
        val d = HiloPdf.documento(copia(listOf(post("1", "ana", "texto", embeds))), nada)!!
        assertTrue(d.html.indexOf("Vídeo de YouTube") > d.html.indexOf("texto"))
    }

    @Test
    fun `sin scripts ni iframes`() {
        val d = HiloPdf.documento(copia(listOf(post("1", "ana",
            "a<script>alert(1)</script>b<iframe src=\"https://x\"></iframe>c"))), nada)!!
        assertFalse(d.html.contains("<script"))
        assertFalse(d.html.contains("<iframe"))
        assertTrue(d.html.contains("abc"))
    }

    @Test
    fun `la edicion se dice`() {
        val d = HiloPdf.documento(copia(listOf(post("1", "ana", "x", editBy = "ana", editDate = "31-oct-2026 10:00"))), nada)!!
        assertTrue(d.html.contains("Editado por ana · 31-oct-2026 10:00"))
    }

    @Test
    fun `los spoilers salen visibles`() {
        val d = HiloPdf.documento(copia(listOf(post("1", "ana", "<div class=\"spoiler\">sorpresa</div>"))), nada)!!
        assertTrue(d.html.contains(".spoiler { background: #ececec !important; color: #111 !important;"))
    }

    @Test
    fun `el nombre del fichero no lleva caracteres prohibidos ni se pasa de largo`() {
        assertEquals("ForoPlus - Hilo 1 2 o no", HiloPdf.nombreArchivo("Hilo 1/2: ¿o no?".replace("¿", "")))
        assertEquals("ForoPlus - hilo", HiloPdf.nombreArchivo("  /// "))
        // Medido: un título acabado en punto salía guardado como "….PDF".
        assertEquals("ForoPlus - Dedícale unas palabras", HiloPdf.nombreArchivo("Dedícale unas palabras."))
        assertEquals("ForoPlus - Qué", HiloPdf.nombreArchivo("Qué... "))
        assertTrue(HiloPdf.nombreArchivo("x".repeat(300)).length <= "ForoPlus - ".length + 80)
    }

    @Test
    fun `una copia rota o vacia no da documento`() {
        assertNull(HiloPdf.documento("no es json", nada))
        assertNull(HiloPdf.documento(copia(emptyList()), nada))
        assertNotNull(HiloPdf.documento(copia(listOf(post("1", "ana", "x"))), nada))
    }
}
