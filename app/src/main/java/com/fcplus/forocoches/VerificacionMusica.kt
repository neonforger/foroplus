package com.fcplus.forocoches

/**
 * El campo "coche" del perfil de FC de la cuenta activa, leído y escrito por el motor
 * (`fcLeerCoche`/`fcPonerCoche` de extractor.js). Bloqueante (≤ 15 s): solo desde un hilo de fondo.
 * - [leer]: el coche que tiene ahora ("" si no tiene), o null si no se pudo (sin sesión, challenge,
 *   otra cuenta en el motor, FC caído).
 * - [poner]: true si FC, leído otra vez después, tiene [valor].
 * - [escrituraDudosa]: la última [poner] se quedó sin respuesta (o se cortó por plazo): su POST pudo
 *   llegar a FC más tarde.
 */
interface MotorCoche {
    fun leer(): String?
    fun poner(valor: String): Boolean
    val escrituraDudosa: Boolean get() = false
}

/**
 * Verificarse en Música con la cuenta de FC (spec del 2026-11-07, "Verificación en la app"): el
 * servidor da un coche, se pone en el perfil, el servidor lo lee en la ficha pública y da un token.
 *
 * Es la DÉCIMA vía que muta algo en FC (gotcha "las vías que mutan algo en FC son nueve"), así que
 * lleva el guard de `cambiandoDeCuenta` DONDE SE ESCRIBE: justo antes de poner el coche de prueba y
 * justo antes de devolver el original, no solo al pulsar. Con un cambio de cuenta a medias el motor
 * puede ser ya de la otra cuenta.
 *
 * Lo que no se negocia: el original se guarda en el móvil ([guardado]) ANTES de tocar el perfil, y
 * se devuelve SIEMPRE (`finally`), salga bien, mal o lance algo. Lo guardado solo se borra cuando
 * FC confirma que vuelve a tener el original; si no, se devuelve al arrancar ([restaurar]). Y nunca
 * se pisa: con uno guardado de antes, primero se devuelve ese, y si no se puede, no se verifica.
 *
 * @param esperar duerme el hilo (inyectable para los tests, con [reloj]).
 */
class VerificacionMusica(
    private val api: ApiMusica,
    private val motor: MotorCoche,
    private val guardado: GuardadoCoche,
    private val reloj: () -> Long,
    private val esperar: (Long) -> Unit
) {
    enum class Motivo { SIN_SESION, CAMBIANDO_DE_CUENTA, CHALLENGE, NO_COINCIDE, CADUCADO, BANEADA, LIMITE, RED, FC, COCHE_ANTERIOR, SIN_GUARDAR }

    sealed class Fin {
        data class Ok(val nombre: String, val token: String, val puedeVotar: Boolean) : Fin()
        data class Error(val motivo: Motivo) : Fin()
    }

    /** Qué se le dice al usuario, y si hay que llevarle al login. Ver [aviso]. */
    data class Aviso(val texto: String, val entrar: Boolean)

    fun ejecutar(uid: String, cambiandoDeCuenta: () -> Boolean): Fin {
        if (cambiandoDeCuenta()) return Fin.Error(Motivo.CAMBIANDO_DE_CUENTA)

        // Un coche de prueba de una vez anterior puede seguir puesto, con el original guardado (la
        // app se cerró a medias y aún no se pudo devolver). Leer ahora daría el de PRUEBA, y
        // guardarlo como "original" perdía el de verdad para siempre: primero se devuelve lo
        // guardado, y si FC no confirma que lo tiene, no se verifica.
        if (guardado.leer() != null) {
            restaurar(motor, guardado, uid, reloj(), cambiandoDeCuenta)
            if (guardado.leer() != null) {
                return Fin.Error(if (cambiandoDeCuenta()) Motivo.CAMBIANDO_DE_CUENTA else Motivo.COCHE_ANTERIOR)
            }
        }
        val original = motor.leer() ?: return Fin.Error(Motivo.SIN_SESION)

        // El servidor ya no da nunca el coche que tiene la ficha (lo lee al dar el reto), pero si lo
        // diera no probaría nada: el perfil ya lo tenía. Se pide otro, como mucho tres en total.
        var reto: Reto? = null
        for (i in 0 until MAX_RETOS) {
            when (val r = api.reto(uid)) {
                is ApiMusica.R.Error -> return Fin.Error(motivo(r))
                is ApiMusica.R.Ok -> if (!cochesIguales(r.v.coche, original)) { reto = r.v; break }
            }
        }
        if (reto == null) return Fin.Error(Motivo.FC)

        // Entre leer y aquí ha habido red: un cambio de cuenta ha podido empezar.
        if (cambiandoDeCuenta()) return Fin.Error(Motivo.CAMBIANDO_DE_CUENTA)
        // Sin el original en disco no se toca el perfil: si no, una app cerrada a medias dejaría el
        // coche de prueba sin nada con lo que devolverlo.
        if (!guardado.guardar(Pendiente(uid, original, reloj()))) return Fin.Error(Motivo.SIN_GUARDAR)
        var dudosa = false
        return try {
            val puesto = motor.poner(reto.coche)
            dudosa = !puesto && motor.escrituraDudosa
            if (puesto) comprobar(reto) else Fin.Error(Motivo.FC)
        } catch (e: Exception) {
            Fin.Error(Motivo.RED)
        } finally {
            // Aunque poner() diga que no, el POST pudo llegar a FC: se devuelve igual. Y si se quedó
            // sin respuesta, pudo llegar DESPUÉS de devolverlo: se espera y se vuelve a mirar.
            if (!cambiandoDeCuenta()) devolver(motor, original, guardado, if (dudosa) esperar else null)
        }
    }

    /** 202: el servidor aún no ha leído la ficha (lector del VPS). 409 con intentos: FC tarda en enseñarlo. */
    private fun comprobar(reto: Reto): Fin {
        val limite = reloj() + ESPERA_PENDIENTE_MS
        var repetido = false
        while (true) {
            when (val r = api.comprobar(reto.id)) {
                is ApiMusica.R.Ok -> {
                    val c = r.v
                    if (!c.pendiente) {
                        val token = c.token ?: return Fin.Error(Motivo.FC)
                        return Fin.Ok(c.nombre.orEmpty(), token, c.puedeVotar)
                    }
                    if (reloj() + SONDEO_MS > limite) return Fin.Error(Motivo.FC)
                    esperar(SONDEO_MS)
                }
                is ApiMusica.R.Error -> {
                    val quedan = r.datos?.optInt("intentosRestantes", 0) ?: 0
                    if (r.codigo == "no_coincide" && quedan > 0 && !repetido) {
                        repetido = true
                        esperar(REPETIR_MS)
                    } else return Fin.Error(motivo(r))
                }
            }
        }
    }

    companion object {
        const val MAX_RETOS = 3
        const val SONDEO_MS = 2_000L
        const val ESPERA_PENDIENTE_MS = 30_000L
        const val REPETIR_MS = 3_000L
        /**
         * Lo que se espera, tras devolver el original, si la escritura del coche de prueba se quedó
         * sin respuesta: el JS corta su cadena a los 10 s y Kotlin espera 15, así que cuando se da
         * por perdida ya no sale nada más; esto cubre un POST que FC aún estuviera procesando.
         */
        const val ESPERA_TARDIA_MS = 3_000L

        /** Igual que `cochesIguales` del servidor: sin mirar mayúsculas ni espacios de más. */
        fun cochesIguales(a: String, b: String): Boolean = normalizar(a) == normalizar(b)

        private fun normalizar(s: String) = s.trim().replace(Regex("\\s+"), " ").lowercase()

        /**
         * `baneada` va ANTES que cualquier otra lectura del error: en estas rutas un 403 `baneada`
         * también da `tokenInservible`, y la app tiene que decir "cuenta bloqueada", no tratarlo
         * como un token que caducó.
         */
        internal fun motivo(e: ApiMusica.R.Error): Motivo = when {
            e.codigo == "baneada" -> Motivo.BANEADA
            e.esChallenge -> Motivo.CHALLENGE
            e.retoCaducado -> Motivo.CADUCADO
            e.codigo == "no_coincide" -> Motivo.NO_COINCIDE
            e.status == 429 -> Motivo.LIMITE
            e.codigo == "red" -> Motivo.RED
            else -> Motivo.FC
        }

        /** Texto fijo del spec: no se reescribe. */
        const val TEXTO_CHALLENGE = "ForoCoches no deja comprobarlo ahora, prueba en un rato"

        /**
         * [errorMotor] es el `ultimoError` del motor: "sin sesión" sale cuando no se pudo LEER el
         * perfil, y eso también lo da un challenge o FC caído. Solo con "login" se manda a entrar.
         */
        fun aviso(m: Motivo, errorMotor: String?): Aviso = when (m) {
            Motivo.SIN_SESION -> when (errorMotor) {
                "login" -> Aviso("Tu sesión de ForoCoches se ha cerrado. Entra otra vez para verificarte.", true)
                "challenge" -> Aviso(TEXTO_CHALLENGE, false)
                "cuenta" -> Aviso("ForoCoches tiene abierta otra de tus cuentas. Espera un momento y vuelve a probar.", false)
                else -> Aviso("No se ha podido leer tu perfil de ForoCoches. Prueba en un rato.", false)
            }
            Motivo.CAMBIANDO_DE_CUENTA -> Aviso("Espera a que termine el cambio de cuenta", false)
            Motivo.CHALLENGE -> Aviso(TEXTO_CHALLENGE, false)
            Motivo.NO_COINCIDE -> Aviso("El servidor no ha visto el coche en tu ficha. Vuelve a probar en un momento.", false)
            Motivo.CADUCADO -> Aviso("La comprobación ha caducado. Vuelve a probar.", false)
            Motivo.BANEADA -> Aviso("Esta cuenta de ForoCoches está bloqueada en Música.", false)
            Motivo.LIMITE -> Aviso("Demasiados intentos seguidos. Prueba dentro de una hora.", false)
            Motivo.RED -> Aviso("No se ha podido hablar con el servidor de Música. Mira la conexión y vuelve a probar.", false)
            Motivo.FC -> Aviso("ForoCoches no ha dejado completar la comprobación. Prueba en un rato.", false)
            Motivo.COCHE_ANTERIOR -> Aviso("No hemos podido devolver tu coche anterior; prueba en un rato.", false)
            Motivo.SIN_GUARDAR -> Aviso("No se ha podido guardar tu coche en el móvil, así que no se ha tocado tu perfil. Prueba en un rato.", false)
        }

        /**
         * Al arrancar (tras `onQuienSoy`): si con [uidActivo] quedó un coche de prueba puesto, se
         * devuelve el original. Con otra cuenta o con un cambio en curso no se toca nada; uno de
         * hace más de un mes se olvida sin tocar el perfil. True si el perfil vuelve a tener el
         * original.
         */
        fun restaurar(
            motor: MotorCoche, guardado: GuardadoCoche, uidActivo: String?, ahora: Long,
            cambiandoDeCuenta: () -> Boolean
        ): Boolean {
            val p = guardado.leer()
            if (p == null || uidActivo.isNullOrEmpty() || p.uid != uidActivo) return false
            if (cambiandoDeCuenta()) return false
            if (!CochePendiente.hayQueRestaurar(p, uidActivo, ahora)) {
                guardado.borrar()
                return false
            }
            return devolver(motor, p.original, guardado)
        }

        /**
         * Pone [original] y, si FC lo confirma al leerlo, borra lo guardado. Nunca lanza. Con
         * [esperarTardia] (la escritura del de prueba se quedó sin respuesta) espera a que lo que
         * quedara en camino haya llegado, vuelve a mirar y, si ahí sale otro, lo devuelve otra vez.
         */
        private fun devolver(
            motor: MotorCoche, original: String, guardado: GuardadoCoche, esperarTardia: ((Long) -> Unit)? = null
        ): Boolean = try {
            motor.poner(original)
            var leido = motor.leer()
            if (esperarTardia != null) {
                esperarTardia(ESPERA_TARDIA_MS)
                leido = motor.leer()
                if (leido != null && !cochesIguales(leido, original)) {
                    motor.poner(original)
                    leido = motor.leer()
                }
            }
            if (leido != null && cochesIguales(leido, original)) {
                guardado.borrar()
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
