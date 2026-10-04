package com.fcplus.forocoches

/**
 * Cuándo mover a la cuenta activa los datos que estaban sin dueño.
 *
 * Antes de la 40 no había cuentas: las marcas de leído, las citas vistas y la firma vivían en
 * claves **sin sufijo**. Al actualizar hay que adoptarlas, y hay que hacerlo bien **para los
 * 144 usuarios**, no solo para quien use multicuenta: si se pierden, el foro entero les sale en
 * negrita de golpe.
 *
 * Quien ejecuta la migración (ver MainActivity) tiene una regla que no está aquí porque no es
 * una decisión sino una precaución: **el origen no se borra hasta confirmar el destino**. Si el
 * proceso muere a medias, al siguiente arranque se reintenta con los datos intactos.
 */
object MigracionCuentas {

    /**
     * @param yaHecha marca persistente `cuentas_migradas`.
     * @param uidActivo uid de la sesión que hay ahora, o "" si no hay sesión.
     * @param hayCuentas ya existe un índice de cuentas (instalación que nació en la 40 o posterior).
     */
    fun hayQueMigrar(yaHecha: Boolean, uidActivo: String, hayCuentas: Boolean): Boolean =
        !yaHecha && !hayCuentas && uidActivo.isNotEmpty()
}
