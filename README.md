<div align="center">
  <img src="docs/assets/lawnchair-round.webp" width="96" alt="Lawnchair One UI Next" />

  <h1>Lawnchair One UI Next</h1>
  <h3>La libertad de Lawnchair, rediseñada con una experiencia inspirada en One UI.</h3>

  <p>
    Un launcher para Android centrado en <strong>organización, personalización y superficies visuales avanzadas</strong>:
    carpetas grandes, iconos grandes, pilas de widgets, Samsung Finder por gesto y un motor de Blur / Crystal / Frosty construido dentro del propio launcher.
  </p>

  <p>
    <a href="https://github.com/Jorgeprdz/lawnchair/releases/tag/oneui-next-2026.09.28"><img alt="Descargar APK" src="https://img.shields.io/badge/↓%20DESCARGAR%20APK-6C5CE7?style=for-the-badge&labelColor=15151B"></a>
    &nbsp;
    <a href="https://github.com/Jorgeprdz/lawnchair/releases/"><img alt="Ver releases" src="https://img.shields.io/badge/VER%20RELEASES-22232A?style=for-the-badge"></a>
  </p>

  <p>
    <a href="https://github.com/Jorgeprdz/lawnchair/actions/workflows/oneui-build.yml?query=branch%3Afeature%2Foneui-next"><img alt="Build One UI Next" src="https://github.com/Jorgeprdz/lawnchair/actions/workflows/oneui-build.yml/badge.svg?branch=feature%2Foneui-next"></a>
    <a href="LICENSE.txt"><img alt="Licencia Apache 2.0" src="https://img.shields.io/badge/licencia-Apache--2.0-blue.svg"></a>
    <img alt="Android 8.0 o superior" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
    <img alt="Base Launcher3 Android 16" src="https://img.shields.io/badge/Base-Launcher3%20Android%2016-5C6BC0">
  </p>
</div>

<p align="center">
  <a href="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/01-s25-home-large-folder-frosty-dock.png"><img src="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/01-s25-home-large-folder-frosty-dock.png" width="245" alt="Escritorio con carpeta grande y dock Frosty"></a>
  &nbsp;&nbsp;
  <a href="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/02-s25-open-folder-icons.png"><img src="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/02-s25-open-folder-icons.png" width="245" alt="Carpeta abierta en Lawnchair One UI Next"></a>
</p>

<p align="center"><em>Capturas reales en Galaxy S25 · toca una imagen para verla completa</em></p>

---

## Más que un tema: una variante funcional de Lawnchair

**Lawnchair One UI Next** parte de Lawnchair 16 / Launcher3 y conserva su flexibilidad, pero añade una capa propia de interacción y diseño pensada para que la pantalla de inicio se sienta más útil, más visual y más cercana a la filosofía de One UI.

No se limita a cambiar colores o radios. Este fork modifica cómo se organizan carpetas, widgets e iconos; añade integración específica para Samsung; incorpora superficies de vidrio renderizadas dentro del launcher; y mantiene una ruta de sincronización con el desarrollo de Lawnchair.

### Lo que implementamos en este fork

| Feature | Qué hace | Por qué importa |
|---|---|---|
| **Large Folders / Carpetas grandes 2×2** | Convierte una carpeta en un elemento grande del escritorio y muestra hasta **9 apps** en la vista previa. | Permite abrir apps frecuentes directamente desde la carpeta sin entrar primero a ella. |
| **Forma de carpeta grande** | Selector independiente entre **cuadrado redondeado** y **círculo** para la vista previa 2×2. | Mantiene el look One UI o permite un estilo más neutro sin afectar la forma de las carpetas normales. |
| **Max Icons / Iconos grandes** | Permite dar más presencia a accesos directos concretos sin tener que agrandar toda la cuadrícula. | Crea jerarquía visual y accesos destacados en la pantalla de inicio. |
| **Widget Stacks / Pilas de widgets** | Reúne varios widgets en el mismo espacio del escritorio, con navegación entre páginas y editor propio. | Multiplica la información disponible sin ocupar más celdas. |
| **Editor de pilas** | Gestiona los widgets de una pila desde una interfaz dedicada: selección, orden y mantenimiento del contenido. | Hace que las pilas sean una función utilizable a diario y no sólo una demostración visual. |
| **Persistencia de pilas** | Conserva la página activa y el estado de la pila al reconstruir el launcher. | Evita que el stack “olvide” dónde estabas después de recargas o reinicios del proceso. |
| **Widget Grid Snap** | El redimensionado de widgets se ajusta a la cuadrícula y respeta restricciones reales del proveedor. | Reduce layouts rotos y hace más predecible el ajuste de widgets. |
| **Samsung Finder por gesto** | En dispositivos Samsung con Finder disponible, un gesto configurable puede abrir directamente la búsqueda de Samsung. | Integra una de las funciones más útiles de One UI sin reemplazar el sistema de búsqueda de Lawnchair. |
| **Refresco automático de iconos temáticos** | Fuerza la actualización visual de iconos cuando cambia el estado **claro / oscuro**. | Evita iconos con tintes atrasados después de cambiar de tema. |
| **Motor de vidrio para el dock** | Añade modos **Off, Solid, Blur, Crystal y Frosty**. | El dock deja de ser un simple rectángulo translúcido y se convierte en una superficie visual configurable. |
| **Blur ajustable** | Intensidad controlable en tiempo real para el acabado difuminado. | Permite pasar de un efecto muy sutil a uno claramente esmerilado. |
| **Crystal** | Superficie más limpia y refractiva que el blur tradicional, con tratamiento propio dentro del launcher. | Da un acabado más “vidrio” y menos “niebla”. |
| **Frosty** | Variante de vidrio más lechosa y difusa, separada del Blur normal. | Ofrece un look One UI más marcado sin perder por completo el color del wallpaper. |
| **Geometría avanzada del dock** | Ajustes de radio de esquinas, opacidad e insets independientes por lado. | Permite adaptar el dock al wallpaper, a la cuadrícula y al tamaño del dispositivo. |
| **Glass para carpetas** | Las carpetas usan la misma familia visual de superficies del dock con control de intensidad y color. | Mantiene coherencia visual entre el escritorio, el dock y las carpetas. |
| **Render dentro del launcher** | Blur / Crystal / Frosty se dibujan en la propia UI de Lawnchair. | **No requiere root, Shizuku ni modificar SystemUI** para estos efectos. |
| **Sincronización con Lawnchair 16** | Workflow dedicado para integrar cambios upstream y validar el fork mediante GitHub Actions. | Reduce la distancia con Lawnchair mientras protege las funciones One UI añadidas al fork. |

## Carpetas grandes: información sin abrir la carpeta

La implementación de **Large Folders** convierte la carpeta en una pieza real del layout, no en una miniatura agrandada.

- Ocupa un área **2×2** del escritorio.
- Muestra hasta **nueve aplicaciones** en la vista previa.
- Mantiene interacción con el sistema de drag & drop y ocupación de celdas.
- Permite elegir entre **rounded square** y **circle** para la vista previa grande.
- Puede combinarse con las superficies visuales One UI del fork.

El resultado es una carpeta que funciona como acceso rápido y, al mismo tiempo, como elemento visual del home screen.

## Widget Stacks: varios widgets, un solo espacio

Las **pilas de widgets** permiten colocar varios widgets sobre una misma región del escritorio y cambiar entre ellos sin consumir nuevas celdas.

La implementación incluye:

- host de widgets integrado en Lawnchair;
- creación y mantenimiento de la pila;
- navegación entre páginas;
- editor dedicado mediante long press;
- reordenamiento y gestión de miembros;
- persistencia de la página activa;
- restauración segura cuando el launcher reconstruye el host del widget.

Esto permite tener, por ejemplo, clima, calendario, música y tareas en el espacio que normalmente ocuparía un solo widget.

## Iconos grandes y cuadrícula inteligente

El fork incorpora **Max Icons** para destacar aplicaciones concretas y una lógica de **Widget Grid Snap** para que el escritorio siga siendo consistente incluso cuando empiezas a mezclar elementos de distintos tamaños.

Los widgets respetan las celdas reales del workspace y sus tamaños mínimos antes de aceptar cambios. La intención es evitar configuraciones visualmente atractivas pero frágiles que después se rompan al recargar el launcher.

## Samsung Finder, integrado como gesto

En equipos Samsung compatibles puedes asignar un gesto de Lawnchair para abrir **Samsung Finder** directamente.

La integración usa el componente disponible en el dispositivo y está protegida para no romper la experiencia cuando Finder no existe. Así puedes mantener Lawnchair como launcher principal y seguir usando la búsqueda propia de Samsung cuando te convenga.

## One UI Glass Engine

Uno de los cambios más visibles del proyecto es la nueva capa de superficies para **dock y carpetas**.

### Dock

El selector de fondo incluye cinco estilos:

**Off · Solid · Blur · Crystal · Frosty**

Además puedes ajustar:

- intensidad del efecto;
- color;
- opacidad;
- radio de esquinas;
- inset izquierdo;
- inset derecho;
- inset superior;
- inset inferior.

**Blur** y **Crystal** conservan ajustes de intensidad independientes. **Frosty** mantiene un tratamiento más difuso y lechoso para distinguirse visualmente del Blur normal.

### Carpetas

Las carpetas incorporan su propio estilo de fondo, intensidad, color y opacidad. Las carpetas grandes también pueden elegir su forma independientemente de las carpetas normales.

Todo el pipeline se ejecuta **dentro de Lawnchair**: el launcher no necesita tocar SystemUI para producir el efecto.

## Iconos que reaccionan correctamente al tema

Este fork corrige un detalle especialmente visible con iconos temáticos: al alternar entre **modo claro y oscuro**, los iconos pueden refrescarse para usar el tinte correcto sin esperar a que otra acción obligue a reconstruirlos.

Se mantiene además la compatibilidad de Lawnchair con:

- paquetes de iconos;
- themed icons;
- escalado;
- formas de icono;
- colores dinámicos derivados del wallpaper.

## Todo lo bueno de Lawnchair sigue aquí

Las funciones anteriores son las extensiones propias de **One UI Next**. Debajo seguimos aprovechando la base de Lawnchair 16 y Launcher3, incluyendo Material 3 Expressive, búsqueda global, opciones avanzadas de cuadrícula, personalización de iconos y fuentes, Smartspace / At a Glance, gestos, app drawer, copias de seguridad y las opciones de personalización habituales de Lawnchair.

> **Importante:** algunas funciones generales de Lawnchair, como determinadas integraciones de QuickSwitch / Recents, pueden tener requisitos distintos. Los efectos One UI descritos en este README no requieren root.

## Galería

<table>
  <tr>
    <td align="center"><a href="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/03-s25-widgets-and-large-icon.png"><img src="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/03-s25-widgets-and-large-icon.png" width="190" alt="Widgets e icono grande"></a><br><strong>Widgets + Max Icon</strong></td>
    <td align="center"><a href="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/04-s25-samsung-finder-gesture.png"><img src="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/04-s25-samsung-finder-gesture.png" width="190" alt="Samsung Finder mediante gesto"></a><br><strong>Samsung Finder</strong></td>
    <td align="center"><a href="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/08-widget-stack-editor.png"><img src="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/08-widget-stack-editor.png" width="190" alt="Editor de pilas de widgets"></a><br><strong>Widget Stacks</strong></td>
  </tr>
  <tr>
    <td align="center"><a href="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/05-widget-grid.png"><img src="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/05-widget-grid.png" width="190" alt="Widget Grid Snap"></a><br><strong>Widget Grid Snap</strong></td>
    <td align="center"><a href="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/10-large-folder-nine-app-preview.png"><img src="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/10-large-folder-nine-app-preview.png" width="190" alt="Carpeta grande con nueve aplicaciones"></a><br><strong>Large Folders</strong></td>
    <td align="center"><a href="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/15-dock-frosty-s25.png"><img src="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/15-dock-frosty-s25.png" width="190" alt="Dock Frosty"></a><br><strong>Frosty Dock</strong></td>
  </tr>
</table>

<p align="center"><a href="https://github.com/Jorgeprdz/lawnchair/releases/download/oneui-next-2026.09.28/Lawnchair-OneUI-Next-Screenshots-2026-09-28.zip"><strong>↓ Descargar las 16 capturas del release</strong></a></p>

## Instalación

1. Descarga el APK desde **[Releases](https://github.com/Jorgeprdz/lawnchair/releases/)**.
2. Abre el archivo y permite la instalación desde esa fuente si Android lo solicita.
3. Selecciona Lawnchair como aplicación de inicio.
4. Mantén pulsado el escritorio o abre **Ajustes de Lawnchair** para personalizar el launcher.

> **Build comunitaria de prueba.** Esta variante no es una publicación oficial del proyecto Lawnchair ni está distribuida por el equipo oficial en Google Play. Haz una copia de seguridad de tus ajustes antes de actualizar entre builds experimentales.

## Estado del proyecto

- **Base:** Lawnchair 16 / Launcher3 de Android 16.
- **Rama principal del fork:** `16-dev`.
- **Desarrollo One UI:** `feature/oneui-next`.
- **Builds:** GitHub Actions.
- **Distribución:** GitHub Releases.
- **Licencia:** Apache 2.0.

El proyecto mantiene automatización para sincronizar cambios upstream y validar que las extensiones One UI sigan compilando y conservando su comportamiento.

## Código abierto y créditos

Lawnchair es un proyecto comunitario de código abierto basado en Launcher3. **Lawnchair One UI Next es una personalización independiente** construida sobre esa base.

Este fork conserva la licencia y los créditos correspondientes al proyecto original y a los componentes de terceros utilizados.

- [Código del fork](https://github.com/Jorgeprdz/lawnchair)
- [Releases](https://github.com/Jorgeprdz/lawnchair/releases/)
- [Issues](https://github.com/Jorgeprdz/lawnchair/issues)
- [Rama One UI Next](https://github.com/Jorgeprdz/lawnchair/tree/feature/oneui-next)
- [Licencia Apache 2.0](LICENSE.txt)

---

<div align="center">
  <strong>Lawnchair One UI Next</strong><br>
  Personalización profunda sin convertir Android en otra cosa.
</div>
