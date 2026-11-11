package com.fcplus.forocoches

/** Cuándo el botón "Nuevo hilo" va extendido (con texto) o encogido (solo el +). Puro, con test. */
object FabNuevoHilo {
    enum class Accion { EXTENDER, ENCOGER, NADA }

    /** Se encoge en cuanto la lista se mueve lejos del tope; se extiende al quedar quieta arriba. */
    fun decidir(moviendose: Boolean, arriba: Boolean): Accion = when {
        !moviendose && arriba -> Accion.EXTENDER
        moviendose && !arriba -> Accion.ENCOGER
        else -> Accion.NADA
    }
}
