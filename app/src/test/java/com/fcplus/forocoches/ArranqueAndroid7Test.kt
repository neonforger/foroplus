package com.fcplus.forocoches

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La app dice soportar desde Android 7 (minSdk 24), y el resto de tests corren en la 34: una
 * llamada a una API de Android 8 sin comprobar la versión pasa todos los tests y revienta en
 * un Android 7 de verdad. El arranque es lo único que corre siempre, así que se prueba ahí.
 *
 * Ojo: un `NoSuchMethodError` es un Error, no una Exception, así que un
 * `catch (_: Exception)` alrededor NO lo para.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ArranqueAndroid7Test {

    @Test
    fun `la app arranca en Android 7`() {
        // Robolectric ya ha ejecutado ForocochesApp.onCreate para darnos el contexto.
        assertNotNull(ApplicationProvider.getApplicationContext<ForocochesApp>())
    }
}
