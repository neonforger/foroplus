package com.fcplus.forocoches

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import org.json.JSONObject

data class Smiley(val code: String, val src: String)

fun parseSmilies(json: String): List<Smiley> = try {
    val arr = JSONObject(json).getJSONArray("smilies")
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        val code = o.optString("code").trim()
        val src = o.optString("src").trim()
        if (code.isEmpty() || src.isEmpty()) null else Smiley(code, src)
    }
} catch (_: Exception) { emptyList() }

/**
 * Editor BBCode del panel de respuesta (Bloque A): envuelve la selección del
 * EditText con tags de vBulletin y ofrece los diálogos de color/tamaño/embeds
 * y el modal de smilies. Solo manipula texto; el envío sigue en MainActivity.
 */
class BbcodeEditor(private val context: Context, private val input: EditText) {

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    /** Envuelve la selección (o el cursor) con open/close y deja el cursor bien puesto. */
    fun wrap(open: String, close: String) {
        val s = input.selectionStart.coerceAtLeast(0)
        val e = input.selectionEnd.coerceAtLeast(0)
        val a = minOf(s, e)
        val b = maxOf(s, e)
        input.text.insert(b, close)
        input.text.insert(a, open)
        input.setSelection(if (a == b) a + open.length else b + open.length + close.length)
        input.requestFocus()
    }

    fun insert(text: String) {
        val pos = input.selectionStart.coerceAtLeast(0)
        input.text.insert(pos, text)
        input.requestFocus()
    }

    // ── Diálogos de formato ──────────────────────────────────────────────────

    private val palette = listOf(
        "#C8102E", "#FF0000", "#FF8C00", "#FFD700",
        "#00A650", "#00FF00", "#00BFFF", "#0000FF",
        "#800080", "#FF69B4", "#8B4513", "#000000"
    )

    fun pickColor() {
        val grid = GridLayout(context).apply {
            columnCount = 4
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        lateinit var dlg: AlertDialog
        for (hex in palette) {
            val sw = View(context).apply {
                setBackgroundColor(Color.parseColor(hex))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(52); height = dp(44)
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
                setOnClickListener {
                    wrap("[COLOR=${hex.lowercase()}]", "[/COLOR]")
                    dlg.dismiss()
                }
            }
            grid.addView(sw)
        }
        dlg = AlertDialog.Builder(context)
            .setTitle("Color del texto")
            .setView(grid)
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun pickSize() {
        val sizes = arrayOf(
            "1 · Muy pequeña", "2 · Pequeña", "3 · Normal",
            "4 · Grande", "5 · Muy grande", "6 · Gigante", "7 · Enorme"
        )
        AlertDialog.Builder(context)
            .setTitle("Tamaño de la fuente")
            .setItems(sizes) { _, i -> wrap("[SIZE=${i + 1}]", "[/SIZE]") }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun pickAlign() {
        val opts = arrayOf("Centrar", "Alinear a la izquierda", "Alinear a la derecha")
        val tags = arrayOf("CENTER", "LEFT", "RIGHT")
        AlertDialog.Builder(context)
            .setTitle("Alineación")
            .setItems(opts) { _, i -> wrap("[${tags[i]}]", "[/${tags[i]}]") }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun pickList() {
        AlertDialog.Builder(context)
            .setTitle("Lista")
            .setItems(arrayOf("Con viñetas", "Numerada")) { _, i ->
                val open = if (i == 1) "[LIST=1]" else "[LIST]"
                insert("\n$open\n[*]\n[*]\n[/LIST]\n")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun askImg() = inputDialog("Insertar imagen", "URL de la imagen") { url ->
        insert("[IMG]$url[/IMG]")
    }

    fun askUrl() {
        val urlEt = EditText(context).apply { hint = "URL (https://…)" }
        val txtEt = EditText(context).apply { hint = "Texto visible (opcional)" }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(urlEt); addView(txtEt)
        }
        AlertDialog.Builder(context)
            .setTitle("Insertar enlace")
            .setView(box)
            .setPositiveButton("Insertar") { _, _ ->
                val url = urlEt.text.toString().trim()
                if (url.isEmpty()) return@setPositiveButton
                val txt = txtEt.text.toString().trim().ifEmpty { url }
                insert("[URL=\"$url\"]$txt[/URL]")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun pickEmbed() {
        val opts = arrayOf("YouTube", "Tweet / X", "Instagram", "Facebook", "TikTok", "Vocaroo")
        val tags = arrayOf("YOUTUBE", "TWEET", "IG", "FB", "TIKTOK", "VOCAROO")
        AlertDialog.Builder(context)
            .setTitle("Insertar contenido")
            .setItems(opts) { _, i ->
                inputDialog("Insertar ${opts[i]}", "Pega la URL") { raw ->
                    // YouTube quiere el ID del vídeo; el resto tragan la URL completa.
                    val v = if (tags[i] == "YOUTUBE") youtubeId(raw) else raw
                    insert("[${tags[i]}]$v[/${tags[i]}]")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun youtubeId(u: String): String {
        val m = Regex("(?:v=|youtu\\.be/|shorts/|embed/)([A-Za-z0-9_-]{6,})").find(u)
        return m?.groupValues?.get(1) ?: u
    }

    private fun inputDialog(title: String, hint: String, cb: (String) -> Unit) {
        val et = EditText(context).apply { this.hint = hint }
        val fr = FrameLayout(context).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(et)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(fr)
            .setPositiveButton("Insertar") { _, _ ->
                val v = et.text.toString().trim()
                if (v.isNotEmpty()) cb(v)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ── Modal de smilies ─────────────────────────────────────────────────────
    // Bottom sheet scrolleable poco invasivo, en pestañas como los stickers de WhatsApp:
    // "Forocoches" (los reales de FC) y "ForoPlus" (los propios, ver [EmoticonosForoPlus]).
    // Tocar uno inserta su código (se pueden meter varios sin cerrar).

    /** La pestaña que se dejó abierta, para volver a ella la próxima vez. */
    private var pestanaSmileys = 0

    fun showSmilies(items: List<Smiley>) {
        val dlg = BottomSheetDialog(context)
        val vista = android.view.LayoutInflater.from(context).inflate(R.layout.sheet_smileys, null)
        val tabs = vista.findViewById<TabLayout>(R.id.smiley_tabs)
        val nota = vista.findViewById<View>(R.id.smiley_nota)
        val rv = vista.findViewById<RecyclerView>(R.id.smiley_rejilla)
        val deFc = SmileyAdapter(items) { code -> insert("$code ") }
        val propios = ForoPlusAdapter(EmoticonosForoPlus.TODOS) { e -> insert("${e.bbcode} ") }
        fun mostrar(pestana: Int) {
            pestanaSmileys = pestana
            val foroPlus = pestana == 1
            nota.visibility = if (foroPlus) View.VISIBLE else View.GONE
            // Los de ForoPlus son anchos (88x20): a 7 por fila no se verían.
            rv.layoutManager = GridLayoutManager(context, if (foroPlus) 3 else 7)
            rv.adapter = if (foroPlus) propios else deFc
        }
        tabs.addTab(tabs.newTab().setText("Forocoches"), false)
        tabs.addTab(tabs.newTab().setText("ForoPlus"), false)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = mostrar(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        tabs.getTabAt(pestanaSmileys)?.select()
        mostrar(pestanaSmileys)
        dlg.setContentView(vista)
        dlg.show()
    }

    /**
     * La rejilla de los de ForoPlus. Salen del propio APK y animados (en Android 9+), no de
     * [PostImages]: el selector no tiene por qué esperar a la red para algo que ya llevamos.
     */
    private inner class ForoPlusAdapter(
        private val items: List<EmoticonosForoPlus.Emoticono>,
        private val onPick: (EmoticonosForoPlus.Emoticono) -> Unit
    ) : RecyclerView.Adapter<ForoPlusAdapter.Holder>() {

        inner class Holder(val img: ImageView) : RecyclerView.ViewHolder(img)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val img = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(10), dp(14), dp(10), dp(14))
                isClickable = true
                isFocusable = true
                val onda = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, onda, true)
                setBackgroundResource(onda.resourceId)
            }
            return Holder(img)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, pos: Int) {
            val e = items[pos]
            h.img.contentDescription = e.nombre
            val bytes = EmoticonosForoPlus.bytesDe(e.url)
            val animado = bytes?.let { GifAnimado.decodificar(it) }
            if (animado != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                animado.repeatCount = android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                h.img.setImageDrawable(animado)
                animado.start()
            } else {
                h.img.setImageBitmap(bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) })
            }
            h.img.setOnClickListener { onPick(e) }
        }
    }

    private inner class SmileyAdapter(
        private val items: List<Smiley>,
        private val onPick: (String) -> Unit
    ) : RecyclerView.Adapter<SmileyAdapter.Holder>() {

        inner class Holder(val img: ImageView) : RecyclerView.ViewHolder(img) {
            var boundSrc = ""
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val img = ImageView(parent.context).apply {
                // GridLayoutManager fija el ancho a la columna; alto fijo. FIT_CENTER
                // AGRANDA los gifs pequeños de FC (CENTER_INSIDE solo encogía → enanos).
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = false
                setPadding(dp(7), dp(7), dp(7), dp(7))
            }
            return Holder(img)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, pos: Int) {
            val s = items[pos]
            h.boundSrc = s.src
            h.img.contentDescription = s.code
            val cached = PostImages.get(s.src)
            if (cached != null) {
                h.img.setImageBitmap(cached)
            } else {
                h.img.setImageBitmap(null)
                PostImages.load(s.src) {
                    if (h.boundSrc == s.src) PostImages.get(s.src)?.let { h.img.setImageBitmap(it) }
                }
            }
            h.img.setOnClickListener { onPick(s.code) }
        }
    }
}
