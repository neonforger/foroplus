# ForoPlus

Cliente **no oficial** de ForoCoches para Android. Gratis, sin anuncios y con interfaz nativa.

**[Descargar en Google Play](https://play.google.com/store/apps/details?id=com.foroplus.app)** · [Grupo de Telegram](https://t.me/foroplus)

<p>
  <img src="docs/capturas/lista.jpg" width="200" alt="Lista de hilos">
  <img src="docs/capturas/hilo.jpg" width="200" alt="Hilo">
  <img src="docs/capturas/avisos.jpg" width="200" alt="Avisos">
  <img src="docs/capturas/hilo_oscuro.jpg" width="200" alt="Hilo en modo oscuro">
</p>

> Proyecto personal, sin relación con ForoCoches. Se desarrolló en privado desde marzo de 2026;
> el código se publica a partir de la versión 1.11.0.

## Qué hace

- **Ignorados de verdad**: el usuario ignorado desaparece, sus hilos de la lista y las citas que le
  hacen. Se sincronizan con tu cuenta del foro en los dos sentidos.
- **Filtro de palabras**: los hilos con esas palabras en el título dejan de salir. También se puede
  callar un hilo concreto.
- **Último que ha escrito**: debajo de la hora de cada hilo, sin tener que entrar.
- **Avisos**: citas, menciones y privados con su número en la barra.
- **Perfiles completos**: fecha de alta, mensajes al día, firma, coche y ubicación.
- **Modo oscuro** en toda la app, también dentro de los hilos.
- Pestañas de subforos, varios subforos en una sola, hilos del momento, ver solo los mensajes del
  OP, hilos para leer sin conexión, varias cuentas, compartir un mensaje como imagen.
- Tuits, Instagram, TikTok, YouTube y Vocaroo se ven dentro del hilo, con vídeo flotante.
- Responder, citar y multicitar, crear y editar hilos, privados, suscripciones, buscador y encuestas.

## Privacidad

- **No hay servidor propio.** La app habla directamente con forocoches.com desde tu móvil, con tu
  cuenta de siempre. Nadie más ve lo que lees ni lo que escribes.
- La sesión se guarda **solo en tu móvil**.
- Lo único que la app consulta fuera del foro: las imágenes y los contenidos incrustados de los
  mensajes (en sus propias webs) y un fichero público de configuración en GitHub
  ([`RemoteConfig.kt`](app/src/main/java/com/fcplus/forocoches/RemoteConfig.kt)) que sirve para
  avisar de actualizaciones o de problemas. Ese fichero no recibe ningún dato tuyo.
- [Política de privacidad](docs/privacy-policy.html).

## Cómo funciona

Por dentro hay un WebView invisible que carga el foro y hace de **motor**: todas las peticiones
salen de ahí, con tus cookies, como si fuera el navegador. Un script inyectado
([`extractor.js`](app/src/main/assets/extractor.js)) lee el HTML y lo pasa a JSON, y la interfaz
que ves está hecha entera en Kotlin. La web del foro no se enseña nunca.

Para escribir (responder, editar, privados…) la app trae el formulario real del foro, copia todos
sus campos y solo cambia el texto, así que no reimplementa ningún protocolo.

| Fichero | Qué hace |
|---|---|
| `app/src/main/assets/extractor.js` | Lee el HTML del foro y lo devuelve como JSON |
| `app/src/main/java/.../MainActivity.kt` | Pantallas, navegación y el puente con el motor |
| `app/src/main/java/.../ShellBridge.kt` | Las llamadas del motor a Kotlin |
| `app/src/main/java/.../PostAdapter.kt` | Cómo se pinta cada mensaje |
| `app/src/main/java/.../EmbedView.kt` | Tuits, vídeos y demás contenido incrustado |
| `tools/oracle/` | Pruebas de los lectores de HTML contra páginas guardadas |

## Compilar

Hace falta el JDK 17 y el SDK de Android.

```bash
./gradlew assembleDebug        # APK de pruebas (com.foroplus.app.v2, convive con la de Play)
./gradlew testDebugUnitTest    # tests
```

La versión de release necesita un `keystore.properties` con tu propia firma; sin él, `bundleRelease`
falla a propósito.

## Fallos e ideas

En el [grupo de Telegram](https://t.me/foroplus) o abriendo una *issue* aquí.
