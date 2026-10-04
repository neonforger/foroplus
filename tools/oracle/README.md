# Oráculo / golden test del filtrado (`content.js`)

Test de **regresión** del filtrado de `app/src/main/assets/content.js`. Carga snapshots
**congelados** de páginas reales de ForoCoches, inyecta el mismo puente `Android` que usa la
app (con inputs fijos de test), **ejecuta el `content.js` real** en un DOM (jsdom) y comprueba
que oculta **exactamente** lo esperado.

Si alguien rompe el filtrado al tocar `content.js`, este test falla en el mismo PR.

> No comprueba cambios de HTML de FC ("drift") — eso lo detecta el canario en el móvil del
> usuario, con su propia sesión. Aquí el HTML está congelado a propósito.

## Qué cubre

| Fixture | Superficie | Casos |
|---|---|---|
| `thread_new` / `thread_old` | hilo (`filterPosts`) | post de ignorado, **cita** a ignorado (skin nuevo), post normal (negativo) |
| `listing_new` / `listing_old` | listado (`filterThreads`) | hilo de **creador ignorado**, hilo con **keyword**, hilos normales (negativos) |

Inputs y salida esperada están en `golden.json` (ignorados: `GTX`, `Ibai_S`, `Jorge_Lega`;
keyword: `lexus`). En el listado del skin viejo móvil el creador no aparece en el HTML, así
que se mockea `getCachedCreator` con el mapa `creators` del golden (sacado del skin nuevo).

## Harness de mutaciones (`mutations.test.js`)

Substrato del futuro **self-healing**. Coge un fixture limpio, le aplica una mutación
realista que rompe un selector (como haría FC al cambiar su HTML: renombrar una clase,
cambiar un prefijo de id, cambiar un tag, cambiar el esquema de URL…) y verifica:
1. que la mutación **rompe de verdad** el selector objetivo (es significativa), y
2. que el **fail-safe aguanta**: content.js degrada a casi no-op, nunca oculta MÁS que en
   limpio ni blanquea la página (ningún contenedor "gordo" con varios posts/hilos).

Cuando exista el healer, este HTML mutado será su entrada ("repara y que el oráculo vuelva
a verde"). De momento solo verifica robustez.

## Self-healing (`healer.js` + `healing.js` + `heal.test.js`)

Reparación **determinista** (sin LLM) de selectores cuando FC cambia su HTML:

1. **fingerprint** del elemento objetivo desde el HTML limpio, *respetando el scope* con que
   content.js usa el selector (p.ej. `postAuthorNew` se usa como `li.postbit`.querySelector).
2. **findBest**: busca en el HTML roto el elemento más parecido (scoring de similitud:
   tag, needle de href derivado, tag del padre, texto/imagen, Jaccard de clases y ancestros),
   con un **umbral de confianza** mínimo.
3. **generateSelectors** (ROBULA+-lite): candidatos relativos al contenedor, más robusto
   primero (tag del padre y clases estables antes que prefijos de id dinámicos).
4. **Aceptación por equivalencia de comportamiento**: se acepta el primer candidato que hace
   que content.js oculte **exactamente las mismas unidades** que en limpio. Si ninguno lo
   logra (o no hay candidato fiable), **NO cura** y se escala a humano. *Mejor no curar que
   curar mal.*

Soporta dos **formas de uso**: `descendant` (`container.querySelector`, p.ej. el autor dentro
del post) y `ancestor` (`container.closest`, p.ej. el wrapper que envuelve el post).

### Cobertura por selector de CFG (analizado a fondo)
| Selector | Forma | Estado |
|---|---|---|
| `postAuthorNew` | descendant | ✅ curado (prefijo id, id eliminado, esquema href) |
| `postAuthorOld` | descendant | ✅ curado (renombrado de clase) |
| `postContainerNew` | ancestor | ✅ curado (renombrado de clase del wrapper). *Ojo: NO es fail-safe — al romperse se pierde el ocultado de las CITAS, porque la cita vive fuera del `li.postbit`.* |
| `newSkinMarker` | presencia global | 🛡️ endurecido con redundancia: varias señales solo-skin-nuevo en OR (`.menu-item, .user-notifications-count-wrapper, .header-container, .forocoches-logo, .user-profile-menu-container`). FC tendría que renombrarlas todas. *(Crítico: si falla, se pierde TODO el filtrado por autor.)* |
| `oldThreadTitle` | global, styleid **5** | ⚪ N/A: la app usa **UA móvil** → FC nunca le sirve el skin de escritorio (styleid 5). Rama muerta en este contexto. |
| `forumAuthorOld` | `row.querySelector`, styleid **5** | ⚪ N/A: idem (escritorio). El creador del listado móvil viejo lo resuelve `ThreadCreatorFetcher` (Kotlin), no este selector. |
| `threadLink` | global + regex de id | 🟡 atributo-robusto; la rotura realista (cambio de esquema de URL) rompe también `threadIdOf` (código), así que no se cura solo con config. |
| `ignoredPlaceholder` | **texto**, no selector | 🟡 fuera de alcance del healer (es un needle de texto, no similitud de elementos). |

### Límite de alcance (importante)
El self-healing solo repara roturas **con forma de selector** (datos de `CFG`/`fc_config.json`).
**NO** repara roturas **algorítmicas** que viven en el código de content.js:
- la heurística del `@usuario` en `getAuthorFromRow` (listado skin nuevo),
- el texto del placeholder `'oculto porque'`,
- el regex `threadIdOf` y el algoritmo `rowForAnchor`.

Si FC cambia esas convenciones, ningún `fc_config.json` lo arregla: requiere tocar código y
publicar versión. El canario lo detectará, pero la reparación es manual.

### Cobertura completa para la app móvil
Como la app va con UA móvil, los únicos skins reales son **móvil viejo (7)** y **nuevo (8/9)**.
Para esos, todos los selectores **con forma de selector y alcanzables** están cubiertos
(curados o endurecidos). Lo que queda sin auto-reparar es **algorítmico** (código, no config):
la heurística `@usuario` del listado nuevo, el regex `threadIdOf`, el texto `ignoredPlaceholder`.
El skin de escritorio (styleid 5) no aplica.

## Ficheros

| Fichero | Qué es |
|---|---|
| `lib.js` | Helpers compartidos (carga jsdom, ejecuta el content.js real, selectores) |
| `golden.json` | Inputs fijos + salida esperada del oráculo |
| `oracle.test.js` | Test de regresión (golden) |
| `mutations.test.js` | Harness de mutaciones (fail-safe bajo rotura) |
| `healer.js` | Motor de self-healing (fingerprint, scoring, generación de selectores) |
| `healing.js` | Orquestador (gate por equivalencia de comportamiento, umbral, "no curar") |
| `heal.test.js` | Matriz mutación × selector + caso "no curar" |
| `fixtures/` | Snapshots congelados |

## Uso

```bash
cd tools/oracle
npm install      # solo la primera vez (instala jsdom)
npm test         # corre oráculo + mutaciones (node --test)
```

## Actualizar los fixtures

Si FC cambia su HTML y hay que recapturar:
1. Captura las páginas (hilo + listado) en skin **nuevo** y **antiguo móvil** con sesión real
   (vía el proxy), y guarda el HTML crudo en `fixtures/` con los mismos nombres.
2. Reajusta `golden.json` si cambian los hilos/usuarios visibles (ids de hilo, autores, etc.).
3. `npm test` hasta verde.

El `content.js` se lee **en su sitio** (`app/src/main/assets/`), no se copia — por eso el
test valida siempre la versión actual del código.
