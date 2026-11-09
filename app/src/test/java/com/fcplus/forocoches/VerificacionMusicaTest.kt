package com.fcplus.forocoches

import com.fcplus.forocoches.VerificacionMusica.Fin
import com.fcplus.forocoches.VerificacionMusica.Motivo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Todo con falsos que apuntan en [orden] lo que se les pide: lo que importa aquí es el ORDEN (el
 * original se guarda antes de tocar el perfil, y se devuelve pase lo que pase).
 */
class VerificacionMusicaTest {
    private val orden = mutableListOf<String>()
    private val original = "Seat Ibiza 1.9 TDI 2004"
    private val cocheReto = "Audi A3 1.6 TDI Sport 2015"
    private val otroReto = "Kia Ceed 1.0 TGDI Drive 2019"

    // ---- el servidor ----
    private val retos = ArrayDeque<Respuesta>()
    private val comprobaciones = ArrayDeque<Respuesta>()
    private var comprobarLanza = false
    private val api = ApiMusica({ "token-que-no-debe-salir" }) { _, url, _, _ ->
        when {
            url.endsWith("/v1/verificacion/reto") -> { orden += "reto"; retos.removeFirst() }
            url.endsWith("/v1/verificacion/comprobar") -> {
                orden += "comprobar"
                if (comprobarLanza) throw java.io.IOException("se cortó")
                comprobaciones.removeFirst()
            }
            else -> error("ruta inesperada: $url")
        }
    }

    private fun reto(coche: String, id: String = "01JRETO") = Respuesta(200, """{"reto":"$id","coche":"$coche","caduca":1800000600000}""")
    private val verificada = Respuesta(200, """{"token":"tok","nombre":"YoMismo","diasCuenta":162,"puedeVotar":true}""")

    // ---- el perfil de FC ----
    private inner class MotorFalso : MotorCoche {
        var coche = original
        var leerDevuelveNull = false
        var fallaAlPoner: (String) -> Boolean = { false }
        /** Escritura que se quedó sin respuesta (15 s) y llega a FC más tarde, en [aterrizar]. */
        var seAgotaElTiempo: String? = null
        private var enCamino: String? = null
        override var escrituraDudosa = false
        override fun leer(): String? {
            orden += "leer"
            return if (leerDevuelveNull) null else coche
        }
        override fun poner(valor: String): Boolean {
            orden += "poner($valor)"
            if (valor == seAgotaElTiempo) {
                escrituraDudosa = true
                enCamino = valor
                return false
            }
            escrituraDudosa = false
            if (fallaAlPoner(valor)) return false
            coche = valor
            return true
        }
        fun aterrizar() {
            enCamino?.let { coche = it }
            enCamino = null
        }
    }
    private val motor = MotorFalso()

    // ---- lo guardado en el móvil ----
    private inner class GuardadoFalso : GuardadoCoche {
        var p: Pendiente? = null
        var primero: Pendiente? = null
        var fallaAlGuardar = false
        override fun leer(): Pendiente? = p
        override fun guardar(p: Pendiente): Boolean {
            orden += "pendiente(${p.original})"
            if (fallaAlGuardar || this.p != null) return false
            this.p = p
            if (primero == null) primero = p
            return true
        }
        override fun borrar() {
            orden += "pendiente(null)"
            p = null
        }
    }
    private val almacen = GuardadoFalso()
    private val guardado: Pendiente? get() = almacen.p

    // ---- el reloj y la espera ----
    private var ahora = 1_800_000_000_000L
    private var cambiando = false
    /** Lo que pasa "mientras" se espera: aquí aterriza una escritura que se quedó sin respuesta. */
    private var alEsperar: () -> Unit = { }

    private val verif = VerificacionMusica(
        api, motor, almacen,
        reloj = { ahora },
        esperar = { ms -> orden += "esperar($ms)"; ahora += ms; alEsperar() }
    )

    private fun ejecutar(): Fin = verif.ejecutar("123456") { cambiando }

    @Test fun caminoBueno() {
        retos += reto(cocheReto)
        comprobaciones += verificada
        val fin = ejecutar()
        assertEquals(Fin.Ok("YoMismo", "tok", true), fin)
        assertEquals(
            listOf("leer", "reto", "pendiente($original)", "poner($cocheReto)", "comprobar",
                "poner($original)", "leer", "pendiente(null)"),
            orden
        )
        assertEquals(original, motor.coche)
        assertEquals(null, guardado)
    }

    @Test fun elPendienteLlevaLaCuentaYLaHora() {
        retos += reto(cocheReto)
        comprobaciones += verificada
        ejecutar()
        assertEquals(Pendiente("123456", original, ahora), almacen.primero)
    }

    // ---- un coche de prueba de una verificación anterior (fix 1, crítico) ----

    @Test fun unPendienteAnteriorSeDevuelveAntesDeVerificar() {
        // El perfil aún tiene el coche de prueba de la vez anterior y el móvil guarda el original.
        // Sin esto, leer() daba el de prueba, se guardaba COMO original pisando el bueno, y al
        // final se "devolvía" el de prueba: el coche de verdad se perdía para siempre.
        motor.coche = otroReto
        almacen.p = Pendiente("123456", original, ahora - 60_000)
        retos += reto(cocheReto)
        comprobaciones += verificada
        assertEquals(Fin.Ok("YoMismo", "tok", true), ejecutar())
        assertEquals(
            listOf("poner($original)", "leer", "pendiente(null)",
                "leer", "reto", "pendiente($original)", "poner($cocheReto)", "comprobar",
                "poner($original)", "leer", "pendiente(null)"),
            orden
        )
        assertFalse("pendiente($otroReto)" in orden)
        assertEquals(original, motor.coche)
    }

    @Test fun unPendienteAnteriorQueNoSeDevuelveImpideVerificar() {
        motor.coche = otroReto
        motor.fallaAlPoner = { it == original }
        val antes = Pendiente("123456", original, ahora - 60_000)
        almacen.p = antes
        assertEquals(Fin.Error(Motivo.COCHE_ANTERIOR), ejecutar())
        // Ni reto ni coche nuevo, y el original sigue guardado tal cual.
        assertEquals(listOf("poner($original)", "leer"), orden)
        assertEquals(antes, guardado)
    }

    @Test fun unPendienteViejoSeOlvidaYSeVerificaConLoQueHay() {
        // Más de 30 días: el usuario ya habrá puesto otro coche, y ese es el "original" de ahora.
        almacen.p = Pendiente("123456", "Opel Corsa 1.2 Joy 2001", ahora - 31L * 24 * 3600 * 1000)
        retos += reto(cocheReto)
        comprobaciones += verificada
        assertTrue(ejecutar() is Fin.Ok)
        assertEquals(listOf("pendiente(null)", "leer", "reto", "pendiente($original)"), orden.take(4))
        assertEquals(original, motor.coche)
    }

    @Test fun siNoSePuedeGuardarElOriginalNoSeTocaElPerfil() {
        retos += reto(cocheReto)
        almacen.fallaAlGuardar = true
        assertEquals(Fin.Error(Motivo.SIN_GUARDAR), ejecutar())
        assertFalse(orden.any { it.startsWith("poner") })
    }

    // ---- una escritura que se queda sin respuesta (fix 1.1) ----

    @Test fun ponerSinRespuestaQueLlegaTardeSeVuelveADevolver() {
        retos += reto(cocheReto)
        motor.seAgotaElTiempo = cocheReto
        alEsperar = { motor.aterrizar() }
        assertEquals(Fin.Error(Motivo.FC), ejecutar())
        // Se devuelve el original, se espera a que lo que quedó en camino haya llegado y se vuelve
        // a leer: ahí sale el de prueba, así que se devuelve otra vez antes de borrar lo guardado.
        assertEquals(
            listOf("poner($cocheReto)", "poner($original)", "leer", "esperar(3000)", "leer",
                "poner($original)", "leer", "pendiente(null)"),
            orden.subList(orden.indexOf("poner($cocheReto)"), orden.size)
        )
        assertEquals(original, motor.coche)
    }

    @Test fun ponerSinRespuestaQueLlegaTardeYNoSeArreglaQuedaGuardado() {
        retos += reto(cocheReto)
        motor.seAgotaElTiempo = cocheReto
        alEsperar = { motor.aterrizar(); motor.fallaAlPoner = { it == original } }
        assertEquals(Fin.Error(Motivo.FC), ejecutar())
        assertFalse("pendiente(null)" in orden)
        assertEquals(Pendiente("123456", original, 1_800_000_000_000L), guardado)
    }

    @Test fun ponerSinRespuestaQueNoLlegaSeDaPorDevuelto() {
        retos += reto(cocheReto)
        motor.seAgotaElTiempo = cocheReto
        assertEquals(Fin.Error(Motivo.FC), ejecutar())
        assertEquals(listOf("poner($original)", "leer", "esperar(3000)", "leer", "pendiente(null)"), orden.takeLast(5))
        assertEquals(original, motor.coche)
    }

    @Test fun restauraAunqueFalleComprobar() {
        retos += reto(cocheReto)
        comprobarLanza = true
        assertEquals(Fin.Error(Motivo.RED), ejecutar())
        assertEquals(listOf("comprobar", "poner($original)", "leer", "pendiente(null)"), orden.takeLast(4))
        assertEquals(original, motor.coche)
    }

    @Test fun restauraAunqueAlgoLanceDentro() {
        retos += reto(cocheReto)
        // El motor lanza DESPUÉS de haber escrito el coche de prueba (el POST llegó a FC).
        motor.fallaAlPoner = { v -> if (v == cocheReto) { motor.coche = v; throw IllegalStateException("x") } else false }
        assertEquals(Fin.Error(Motivo.RED), ejecutar())
        assertEquals(original, motor.coche)
        assertEquals(null, guardado)
    }

    @Test fun challengeDelServidor() {
        retos += reto(cocheReto)
        comprobaciones += Respuesta(503, """{"error":"challenge"}""")
        assertEquals(Fin.Error(Motivo.CHALLENGE), ejecutar())
        assertTrue("poner($original)" in orden)
        assertEquals(original, motor.coche)
        assertEquals(null, guardado)
    }

    @Test fun challengeAlPedirElRetoNoTocaElPerfil() {
        retos += Respuesta(503, """{"error":"challenge"}""")
        assertEquals(Fin.Error(Motivo.CHALLENGE), ejecutar())
        assertEquals(listOf("leer", "reto"), orden)
    }

    @Test fun ponerFalla() {
        retos += reto(cocheReto)
        motor.fallaAlPoner = { it == cocheReto }
        assertEquals(Fin.Error(Motivo.FC), ejecutar())
        assertFalse("comprobar" in orden)
        // Aunque fallara, el POST pudo llegar a FC: se intenta devolver el original igual.
        assertEquals(listOf("poner($cocheReto)", "poner($original)", "leer", "pendiente(null)"), orden.takeLast(4))
    }

    @Test fun restaurarFalla() {
        retos += reto(cocheReto)
        comprobaciones += verificada
        motor.fallaAlPoner = { it == original }
        val fin = ejecutar()
        // El token vale (el servidor ya ha verificado), pero el coche de prueba sigue puesto:
        // lo guardado se queda para devolverlo al arrancar.
        assertEquals(Fin.Ok("YoMismo", "tok", true), fin)
        assertFalse("pendiente(null)" in orden)
        assertEquals(Pendiente("123456", original, 1_800_000_000_000L), guardado)
    }

    @Test fun retoIgualAlOriginal() {
        // Con otras mayúsculas y espacios sigue siendo el mismo coche (FC puede recolocarlos).
        retos += reto("  seat ibiza 1.9   TDI 2004", "01JUNO")
        retos += reto(otroReto, "01JDOS")
        comprobaciones += verificada
        assertTrue(ejecutar() is Fin.Ok)
        assertEquals(2, orden.count { it == "reto" })
        assertTrue("poner($otroReto)" in orden)
    }

    @Test fun tresRetosIgualesYSeRinde() {
        repeat(3) { retos += reto(original) }
        assertEquals(Fin.Error(Motivo.FC), ejecutar())
        assertEquals(3, orden.count { it == "reto" })
        assertFalse(orden.any { it.startsWith("poner") })
    }

    @Test fun pendienteEn202() {
        retos += reto(cocheReto)
        comprobaciones += Respuesta(202, """{"estado":"pendiente"}""")
        comprobaciones += Respuesta(202, """{"estado":"pendiente"}""")
        comprobaciones += verificada
        assertEquals(Fin.Ok("YoMismo", "tok", true), ejecutar())
        assertEquals(
            listOf("comprobar", "esperar(2000)", "comprobar", "esperar(2000)", "comprobar", "poner($original)"),
            orden.subList(orden.indexOf("comprobar"), orden.indexOf("poner($original)") + 1)
        )
    }

    @Test fun pendienteMasDe30SegundosSeRinde() {
        retos += reto(cocheReto)
        repeat(40) { comprobaciones += Respuesta(202, """{"estado":"pendiente"}""") }
        assertEquals(Fin.Error(Motivo.FC), ejecutar())
        assertTrue(orden.count { it == "comprobar" } in 15..16)
        assertEquals(original, motor.coche)
    }

    @Test fun noCoincideSeRepiteUnaVez() {
        retos += reto(cocheReto)
        comprobaciones += Respuesta(409, """{"error":"no_coincide","intentosRestantes":2}""")
        comprobaciones += verificada
        assertTrue(ejecutar() is Fin.Ok)
        assertEquals(listOf("comprobar", "esperar(3000)", "comprobar"), orden.filter { it == "comprobar" || it.startsWith("esperar") })
    }

    @Test fun noCoincideDosVecesSeRinde() {
        retos += reto(cocheReto)
        comprobaciones += Respuesta(409, """{"error":"no_coincide","intentosRestantes":2}""")
        comprobaciones += Respuesta(409, """{"error":"no_coincide","intentosRestantes":1}""")
        assertEquals(Fin.Error(Motivo.NO_COINCIDE), ejecutar())
        assertEquals(2, orden.count { it == "comprobar" })
        assertEquals(original, motor.coche)
    }

    @Test fun noCoincideSinIntentosNoSeRepite() {
        retos += reto(cocheReto)
        comprobaciones += Respuesta(409, """{"error":"no_coincide","intentosRestantes":0}""")
        assertEquals(Fin.Error(Motivo.NO_COINCIDE), ejecutar())
        assertEquals(1, orden.count { it == "comprobar" })
    }

    @Test fun motivosDelServidor() {
        fun conReto(r: Respuesta): Fin {
            orden.clear(); retos.clear(); retos += r
            return ejecutar()
        }
        fun conComprobar(r: Respuesta): Fin {
            orden.clear(); retos.clear(); comprobaciones.clear(); retos += reto(cocheReto); comprobaciones += r
            return ejecutar()
        }
        assertEquals(Fin.Error(Motivo.BANEADA), conReto(Respuesta(403, """{"error":"baneada"}""")))
        assertEquals(Fin.Error(Motivo.LIMITE), conReto(Respuesta(429, """{"error":"limite"}""")))
        assertEquals(Fin.Error(Motivo.FC), conReto(Respuesta(404, """{"error":"no_existe"}""")))
        assertEquals(Fin.Error(Motivo.BANEADA), conComprobar(Respuesta(403, """{"error":"baneada"}""")))
        assertEquals(Fin.Error(Motivo.CADUCADO), conComprobar(Respuesta(410, """{"error":"caducado"}""")))
        assertEquals(Fin.Error(Motivo.CADUCADO), conComprobar(Respuesta(404, """{"error":"reto"}""")))
        assertEquals(Fin.Error(Motivo.LIMITE), conComprobar(Respuesta(429, """{"error":"intentos"}""")))
        assertEquals(Fin.Error(Motivo.FC), conComprobar(Respuesta(500, "no es json")))
        assertEquals(original, motor.coche)
    }

    @Test fun sinSesionNoPideReto() {
        motor.leerDevuelveNull = true
        assertEquals(Fin.Error(Motivo.SIN_SESION), ejecutar())
        assertEquals(listOf("leer"), orden)
    }

    @Test fun noPoneCocheCambiandoDeCuenta() {
        cambiando = true
        assertEquals(Fin.Error(Motivo.CAMBIANDO_DE_CUENTA), ejecutar())
        assertEquals(emptyList<String>(), orden)
    }

    @Test fun unCambioDeCuentaAMitadNoEscribe() {
        // El cambio empieza mientras se pide el reto: el guard va DONDE SE ESCRIBE.
        retos += reto(cocheReto)
        val v = VerificacionMusica(api, motor, almacen, { ahora }, { })
        var llamadas = 0
        assertEquals(Fin.Error(Motivo.CAMBIANDO_DE_CUENTA), v.ejecutar("123456") { llamadas++ > 0 })
        assertEquals(listOf("leer", "reto"), orden)
    }

    @Test fun conUnCambioDeCuentaNoSeRestauraYQuedaPendiente() {
        // Si el cambio empieza mientras se comprueba, el motor ya puede ser de la otra cuenta:
        // devolver el coche ahí escribiría en el perfil equivocado. Lo guardado se queda.
        retos += reto(cocheReto)
        comprobaciones += verificada
        var llamadas = 0
        val fin = verif.ejecutar("123456") { llamadas++ >= 2 }
        assertEquals(Fin.Ok("YoMismo", "tok", true), fin)
        assertFalse("poner($original)" in orden)
        assertEquals(Pendiente("123456", original, ahora), guardado)
    }

    // ---- al arrancar ----

    @Test fun restaurarAlArrancar() {
        motor.coche = cocheReto
        almacen.p = Pendiente("123456", original, ahora - 60_000)
        assertTrue(VerificacionMusica.restaurar(motor, almacen, "123456", ahora) { false })
        assertEquals(listOf("poner($original)", "leer", "pendiente(null)"), orden)
        assertEquals(original, motor.coche)
    }

    @Test fun alArrancarSinNadaGuardadoNoSeToca() {
        assertFalse(VerificacionMusica.restaurar(motor, almacen, "123456", ahora) { false })
        assertEquals(emptyList<String>(), orden)
    }

    @Test fun alArrancarConOtraCuentaNoSeToca() {
        motor.coche = cocheReto
        almacen.p = Pendiente("123456", original, ahora)
        assertFalse(VerificacionMusica.restaurar(motor, almacen, "654321", ahora) { false })
        assertEquals(emptyList<String>(), orden)
    }

    @Test fun alArrancarCambiandoDeCuentaNoSeToca() {
        almacen.p = Pendiente("123456", original, ahora)
        assertFalse(VerificacionMusica.restaurar(motor, almacen, "123456", ahora) { true })
        assertEquals(emptyList<String>(), orden)
    }

    @Test fun alArrancarUnoViejoSeOlvidaSinTocarElPerfil() {
        // Más de 30 días: el usuario ya habrá puesto lo que quiera; devolver el viejo lo pisaría.
        almacen.p = Pendiente("123456", original, ahora - 31L * 24 * 3600 * 1000)
        assertFalse(VerificacionMusica.restaurar(motor, almacen, "123456", ahora) { false })
        assertEquals(listOf("pendiente(null)"), orden)
    }

    @Test fun alArrancarSiNoQuedaPuestoSeGuardaParaLaProxima() {
        motor.coche = cocheReto
        motor.fallaAlPoner = { true }
        almacen.p = Pendiente("123456", original, ahora)
        assertFalse(VerificacionMusica.restaurar(motor, almacen, "123456", ahora) { false })
        assertFalse(orden.any { it.startsWith("pendiente") })
        assertEquals(Pendiente("123456", original, ahora), guardado)
    }

    // ---- qué se le dice al usuario ----

    @Test fun elChallengeDiceElTextoFijo() {
        val challenge = "ForoCoches no deja comprobarlo ahora, prueba en un rato"
        assertEquals(VerificacionMusica.Aviso(challenge, false), VerificacionMusica.aviso(Motivo.CHALLENGE, null))
        // Si el challenge salta al leer el perfil, el motivo es "sin sesión" pero no lo es.
        assertEquals(VerificacionMusica.Aviso(challenge, false), VerificacionMusica.aviso(Motivo.SIN_SESION, "challenge"))
    }

    @Test fun sinSesionLlevaAEntrarSoloSiEsLaSesion() {
        assertTrue(VerificacionMusica.aviso(Motivo.SIN_SESION, "login").entrar)
        assertFalse(VerificacionMusica.aviso(Motivo.SIN_SESION, "tiempo").entrar)
        assertFalse(VerificacionMusica.aviso(Motivo.SIN_SESION, "cuenta").entrar)
        assertFalse(VerificacionMusica.aviso(Motivo.FC, null).entrar)
    }

    @Test fun cadaMotivoTieneSuTexto() {
        val textos = Motivo.values().map { VerificacionMusica.aviso(it, null).texto }
        assertTrue(textos.all { it.isNotBlank() })
        assertTrue(VerificacionMusica.aviso(Motivo.BANEADA, null).texto.contains("bloqueada"))
        assertEquals("Espera a que termine el cambio de cuenta", VerificacionMusica.aviso(Motivo.CAMBIANDO_DE_CUENTA, null).texto)
        assertEquals("No hemos podido devolver tu coche anterior; prueba en un rato.",
            VerificacionMusica.aviso(Motivo.COCHE_ANTERIOR, null).texto)
    }

    @Test fun cochesIgualesSinMirarMayusculasNiEspacios() {
        assertTrue(VerificacionMusica.cochesIguales("Seat Ibiza 1.9 TDI 2004", "  seat  ibiza 1.9 tdi 2004 "))
        assertFalse(VerificacionMusica.cochesIguales("Seat Ibiza 1.9 TDI 2004", "Seat Ibiza 1.9 TDI 2005"))
        assertTrue(VerificacionMusica.cochesIguales("", "  "))
    }
}
