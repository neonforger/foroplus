package com.fcplus.forocoches

/**
 * Lo que Música necesita de MainActivity para hablar con su servidor en nombre de la cuenta de FC
 * activa. Lambdas y no valores: la cuenta, el cambio de cuenta y la config cambian con la app
 * abierta, y cada llamada tiene que ver los de ESE momento.
 *
 * @param servidorActivo el interruptor de `fc_config.json` ([ConfigMusica.servidorActivo]).
 * @param uidActivo la cuenta de FC activa, o "" sin sesión.
 * @param cambiandoDeCuenta hay un cambio de cuenta a medias: no se escribe nada en FC.
 * @param tokens perezoso: abrir las preferencias cifradas cuesta el keystore, y al arrancar solo
 *   hace falta mirar si quedó un coche pendiente ([restaurarCoche]).
 * @param entrar abre el login nativo.
 */
class EnganchesMusica(
    val servidorActivo: () -> Boolean,
    val uidActivo: () -> String,
    val cambiandoDeCuenta: () -> Boolean,
    val motorCoche: MotorCocheFC,
    tokens: Lazy<TokensMusica>,
    val pendientes: PendientesCoche,
    val entrar: () -> Unit
) {
    val tokens: TokensMusica by tokens

    /** El cliente del servidor con el token de la cuenta activa en cada llamada. */
    fun api(): ApiMusica = ApiMusica({ tokens.de(uidActivo()) })

    /**
     * Tras `onQuienSoy` con [uid] confirmado: si una verificación se quedó a medias (la app se
     * cerró con el coche de prueba puesto), se devuelve el original. En el hilo del coche, y lo
     * pendiente se lee ALLÍ, no aquí: si hay una verificación en marcha, esta espera a que acabe y
     * para entonces ya habrá devuelto el coche ella misma.
     */
    fun restaurarCoche(uid: String) {
        if (uid.isEmpty() || pendientes.de(uid) == null) return
        MotorCocheFC.hilo.execute {
            val hecho = VerificacionMusica.restaurar(
                motorCoche.para(uid), pendientes.de(uid), uid, System.currentTimeMillis(),
                { cambiandoDeCuenta() || uidActivo() != uid }, pendientes.para(uid)
            )
            android.util.Log.i("Musica", "coche de una verificación a medias devuelto: $hecho")
        }
    }
}
