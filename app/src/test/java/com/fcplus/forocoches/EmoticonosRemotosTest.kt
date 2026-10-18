package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class EmoticonosRemotosTest {

    @Test
    fun `la version sale del nombre guardado`() {
        assertEquals(3, EmoticonosRemotos.versionDe("v3_roto.gif", "roto.gif"))
        assertEquals(12, EmoticonosRemotos.versionDe("v12_roto.gif", "roto.gif"))
    }

    @Test
    fun `un nombre que no es nuestro da cero`() {
        assertEquals(0, EmoticonosRemotos.versionDe("roto.gif", "roto.gif"))
        assertEquals(0, EmoticonosRemotos.versionDe("v0_roto.gif", "roto.gif"))
        assertEquals(0, EmoticonosRemotos.versionDe("vx_roto.gif", "roto.gif"))
        assertEquals(0, EmoticonosRemotos.versionDe("v3_otro.gif", "roto.gif"))
        assertEquals(0, EmoticonosRemotos.versionDe("roto.gif.tmp", "roto.gif"))
        // Termina igual pero es de otro emoticono: no puede contar (ni borrarse) como versión de roto.gif.
        assertEquals(0, EmoticonosRemotos.versionDe("v3_xroto.gif", "roto.gif"))
        assertEquals(0, EmoticonosRemotos.versionDe("v1_x_roto.gif", "roto.gif"))
    }
}
