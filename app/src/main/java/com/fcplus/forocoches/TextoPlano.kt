package com.fcplus.forocoches

/**
 * Limpieza del texto que sale de convertir el HTML de un mensaje a texto plano.
 *
 * Existe por el **gotcha 21**: `HtmlCompat.fromHtml(...).toString()` sustituye cada `<img>` por
 * el carácter *object replacement* (`U+FFFC`). Dentro de la app no se nota —ahí va la foto de
 * verdad—, pero en cuanto ese texto se pinta o se ENVÍA a otro sitio, el sistema lo dibuja como
 * una **cajita punteada con "OBJ" dentro**.
 *
 * Se arregló el 2026-08-19 para la tarjeta de compartir y **se olvidó el camino de citar**, que
 * es mucho peor: ahí el texto no se pinta, se **publica en ForoCoches**. Un tester citó el
 * 2026-08-28 un mensaje con fotos y su mensaje quedó publicado con las cajitas dentro
 * (comprobado en el foro: 3 caracteres `U+FFFC` guardados en `p=518306773`).
 *
 * Por eso la limpieza vive aquí y no copiada en cada sitio: olvidarla no rompe nada que se note
 * al compilar, y lo que se ensucia es la cuenta del usuario.
 */
object TextoPlano {

    /**
     * Quita los huecos de imagen y el doble espacio que dejan al desaparecer.
     *
     * No toca los saltos de línea: de eso se encarga quien llama, que es quien sabe si quiere
     * párrafos (la cita) o un bloque compacto (la tarjeta de compartir).
     */
    /**
     * Solo quita los huecos de imagen, **sin tocar los espacios**.
     *
     * Es lo que hace falta en el COMPOSER. Desde que se puede seleccionar y copiar texto de un
     * mensaje (2026-09-11), lo que se lleva el portapapeles incluye un `U+FFFC` por cada foto
     * que hubiera en el tramo seleccionado — comprobado pegando en el buscador: *"No es que
     * estén siendo muy sutiles que digamos: ￼"*. Si eso se pega en una respuesta y se envía,
     * se publica la cajita "OBJ", que es exactamente el gotcha 21 por otra puerta.
     *
     * Aquí NO se colapsan los espacios dobles como en [sinHuecosDeImagen]: ese texto lo ha
     * escrito una persona y no es nuestro sitio recolocarle nada.
     */
    fun sinCajitas(texto: String): String = texto.replace("￼", "")

    fun sinHuecosDeImagen(texto: String): String =
        texto.replace("\uFFFC", "").replace(Regex("[ \t]{2,}"), " ")
}
