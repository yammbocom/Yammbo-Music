<div align="center">

  <img src="./assets/branding/yammbo-music-app-icon.png" width="140" height="140" alt="Yammbo Music"/>

  # Yammbo Music

  **Tu musica, tu estilo, tu experiencia.**

  Una aplicacion de musica potente, personalizable y de codigo abierto basada en [RiPlay](https://github.com/fast4x/RiPlay).

  <a href="https://github.com/yammbocom/Yammbo-Music/releases/latest"><img src="https://img.shields.io/github/v/release/yammbocom/Yammbo-Music?style=for-the-badge&color=2ECC71&label=Version"></a>
  <a href="https://github.com/yammbocom/Yammbo-Music/releases"><img src="https://img.shields.io/github/downloads/yammbocom/Yammbo-Music/total?style=for-the-badge&color=3498DB&label=Descargas"></a>
  <a href="https://github.com/yammbocom/Yammbo-Music/blob/main/LICENSE"><img src="https://img.shields.io/github/license/yammbocom/Yammbo-Music?style=for-the-badge&color=9B59B6&label=Licencia"></a>

</div>

---

## Capturas de pantalla

<div align="center">
  <img src="./assets/screenshots/01-home-dark.png" width="200" alt="Inicio - Tema oscuro"/>
  &nbsp;&nbsp;
  <img src="./assets/screenshots/02-home-light.png" width="200" alt="Inicio - Tema claro"/>
  &nbsp;&nbsp;
  <img src="./assets/screenshots/03-player.png" width="200" alt="Reproductor"/>
  &nbsp;&nbsp;
  <img src="./assets/screenshots/05-artist.png" width="200" alt="Vista de artista"/>
</div>

---

## Que es Yammbo Music?

Yammbo Music es un reproductor de musica moderno construido sobre **RiPlay/RiMusic**. Ofrece una experiencia musical completa con soporte para contenido online y offline, suscripciones Premium, integracion con Android TV y Android Auto, letras sincronizadas, y una interfaz cuidada disponible en tema oscuro y claro.

> **Privacidad primero:** No recopilamos datos personales. Tu musica y preferencias se quedan en tu dispositivo.

---

## Caracteristicas principales

### Reproduccion
- Canciones, videos, artistas, albumes, playlists y podcasts
- Colas inteligentes (audio/video) con radio automatica
- Letras sincronizadas por defecto: buscar, mostrar, editar y traducir
- Estadisticas de escucha, ranking de nivel y resumen anual
- Lista negra para artistas, albumes, canciones o carpetas
- Temporizador de suspension persistente (sobrevive reinicios)
- Reconocimiento de musica (titulo y artista)
- Visualizador de audio

### Yammbo Premium
- **Sin anuncios** en toda la app
- **Saltos ilimitados** (usuarios gratis: 6 saltos por hora)
- **Descargas** de canciones para escuchar sin conexion
- **Playlists personalizadas** ilimitadas
- **Letras sincronizadas** siempre disponibles
- **Configuracion avanzada** (calidad de audio, ajustes de reproduccion)
- Suscripcion mensual o anual via **Stripe o PayPal**
- Administra tu plan desde `Mi Cuenta → Gestionar suscripcion` (cancelar, reanudar, cambiar)
- Cancelaciones respetan el periodo pagado (grace period)
- La app detecta automaticamente cambios en tu suscripcion

### Android TV
- Soporte completo para Android TV (Leanback launcher)
- Sidebar vertical de navegacion con focus states optimizados para control remoto
- Vinculacion por codigo QR desde la TV: escanea, inicia sesion en el movil, listo
- Controles de reproductor adaptados (skip/play/next con focus + escalado)
- Enlace profundo: abrir `music.yammbo.com/tv-link` desde la app vincula tu cuenta al instante

### Android Auto y reproduccion en movimiento
- Integracion nativa con Android Auto
- Controles de media session (auriculares, smartwatch, Bluetooth)
- Recuperacion automatica tras errores de streaming
- Temporizador de suspension que se cancela al conectar con Android Auto
- Opcion de reanudar/pausar al conectar/desconectar dispositivos

### Funciones sociales y de compartir
- Compartir canciones con imagen generada (1080x1920) con portada y paleta de colores automatica
- URLs cortas de Yammbo Music con redireccion inteligente
- Notificaciones push de nuevos lanzamientos (Firebase)
- Popups de novedades y anuncios (Firebase Remote Config)

### Personalizacion
- Tema oscuro y claro con paletas personalizables
- Controles de audio: volumen, velocidad, tono, normalizacion, saltar silencios, refuerzo de bajos
- Navegacion personalizable (Inicio, Top 50, Mi Musica, Buscar, Mi Cuenta)
- Tabs de ajustes rediseniados (General, UI, Apariencia, Inicio, Datos, Cuentas, Otros)
- Soporte para 50+ idiomas

### Privacidad y seguridad
- Autenticacion con Laravel Sanctum (tokens firmados)
- Sin trackers de terceros fuera de los necesarios (Firebase FCM, AdMob en tier gratis)
- Navegador embebido en modo oscuro sin barra de URL al scrollear
- Keystores y credenciales locales nunca salen de tu dispositivo

---

## Descargar

<div align="center">
  <a href="https://github.com/yammbocom/Yammbo-Music/releases/latest">
    <img src="./assets/getItGithub.png" alt="Descargar desde GitHub" height="80">
  </a>
</div>

---

## Construido con

| Tecnologia | Uso |
|-----------|-----|
| **Kotlin** | Lenguaje principal |
| **Jetpack Compose** | UI moderna y reactiva |
| **Kotlin Multiplatform** | Arquitectura multiplataforma |
| **Firebase** | Notificaciones push y Remote Config |
| **Google AdMob** | Monetizacion del tier gratuito |
| **Stripe y PayPal** | Procesadores de pago para suscripciones |
| **Chrome Custom Tabs** | Navegador embebido con branding propio |
| **Laravel Sanctum** | Autenticacion del backend `music.yammbo.com` |
| **Media3 ExoPlayer** | Motor de reproduccion |

---

## Creditos y agradecimientos

Yammbo Music es un fork de [RiPlay](https://github.com/fast4x/RiPlay), que a su vez esta basado en [RiMusic](https://github.com/fast4x/RiMusic) y [ViMusic](https://github.com/vfsfitvnm/ViMusic). Agradecemos a todos los contribuidores originales por su increible trabajo.

| Proyecto | Contribucion |
|----------|-------------|
| [RiPlay](https://github.com/fast4x/RiPlay) | Proyecto base |
| [ViMusic](https://github.com/vfsfitvnm/ViMusic) | Fundacion original |
| [Android YouTube Player](https://github.com/PierfrancescoSoffritti/android-youtube-player) | Wrapper de YouTube Player |
| [KuGou](https://www.kugou.com) y [LrcLib](https://lrclib.net) | Proveedores de letras |
| [AudioTag.info](https://audiotag.info) | API de reconocimiento musical |
| [qrose](https://github.com/alexzhirkevich/qrose) | Generacion de codigos QR para TV |

---

## Licencia

Este proyecto esta licenciado bajo **GPL-3.0**. Consulta el archivo [LICENSE](LICENSE) para mas detalles.

---

<div align="center">

  **Yammbo Music** · by [yammbo.com](https://link.yammbo.com/services)

</div>
