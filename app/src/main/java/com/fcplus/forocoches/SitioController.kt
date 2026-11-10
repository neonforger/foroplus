package com.fcplus.forocoches

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout

/**
 * Elegir sitio desde el selector del título y la pestaña TEMPORAL de la barra. Las decisiones son
 * de [BarraTemporal] (puro); aquí solo se tocan las pestañas.
 *
 * Un subforo que NO está en la barra (escondido en Organizar) y que se abre a propósito (selector,
 * enlace, miga) se recuerda en [fueraDeBarra]; mientras se está en él, la barra le añade al final
 * una pestaña temporal con su nombre, marcada ([tabTemporalFid]). Sin ella `pintarPestanas`
 * creería que se está en un subforo que ya no existe y saltaría al primero visible. La pestaña
 * desaparece al ir a cualquier otro sitio; el fid se recuerda para que el atrás (o volver de un
 * hilo) a esa lista se quede en ella. Organizar lo olvida: si escondes algo estando en ello, sí
 * se salta, como siempre.
 */
class SitioController(
    private val act: AppCompatActivity,
    private val tabs: TabLayout,
    private val lugarActual: () -> Int,
    private val conocidos: () -> List<Sitio>,
    private val abrirDestino: (Int) -> Unit,
    private val pintarPestanas: () -> Unit,
    /** Ejecuta el bloque con `populatingTabs` en alto: tocar pestañas sin disparar el listener. */
    private val sinListener: (() -> Unit) -> Unit,
) {
    var fueraDeBarra: Set<Int> = emptySet()
    var tabTemporalFid = BarraTemporal.NINGUNO

    private fun claves() = (0 until tabs.tabCount).mapNotNull { tabs.getTabAt(it)?.tag as? Int }

    fun quitarTabTemporal() {
        val fid = tabTemporalFid
        if (fid == BarraTemporal.NINGUNO) return
        tabTemporalFid = BarraTemporal.NINGUNO
        sinListener { for (i in 0 until tabs.tabCount) if (tabs.getTabAt(i)?.tag == fid) { tabs.removeTabAt(i); break } }
    }

    /** Se abre [fid] por un enlace: si no es una pestaña, se recuerda (sin esperar a los nombres). */
    fun recordarSiFuera(fid: Int) {
        fueraDeBarra = BarraTemporal.alAbrirPorEnlace(fid, claves(), fueraDeBarra)
    }

    fun alOrganizar() { fueraDeBarra = BarraTemporal.alOrganizar(fueraDeBarra) }

    fun abrirSelector() {
        val pestanas = (0 until tabs.tabCount).mapNotNull { i ->
            val t = tabs.getTabAt(i) ?: return@mapNotNull null
            (t.tag as? Int)?.let { Sitio(it, t.text?.toString().orEmpty()) }
        }
        SelectorSitio.mostrar(act, BarraTemporal.enBarra(pestanas, tabTemporalFid), conocidos(), lugarActual()) { elegir(it) }
    }

    private fun elegir(clave: Int) {
        val claves = claves()
        val marcada = tabs.getTabAt(tabs.selectedTabPosition)?.tag as? Int
        when (val e = BarraTemporal.elegir(clave, lugarActual(), claves, marcada)) {
            BarraTemporal.Eleccion.Nada -> {}
            is BarraTemporal.Eleccion.EnBarra -> {
                // Sin disparar onTabSelected (navega abrirDestino) ni onTabReselected (una pestaña
                // ya marcada no se toca: cargaría dos veces). select() lleva la pestaña a la vista
                // con SU animación: encuadrarla encima la cortaba (ver marcarPestanaMusica).
                if (e.marcar) sinListener { tabs.getTabAt(claves.indexOf(clave))?.select() }
                abrirDestino(clave)
            }
            BarraTemporal.Eleccion.Escondido -> {
                recordarSiFuera(clave)
                abrirDestino(clave)
                pintarPestanas()   // añade la pestaña temporal, marcada y a la vista
            }
        }
    }
}
