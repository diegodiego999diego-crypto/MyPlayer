# MyPlayer

Reproductor de música para Android construido con **Kotlin**, **Jetpack Compose** (Material You) y **Media3 / ExoPlayer**. Lee la música del almacenamiento compartido vía `MediaStore`, la cachea en **Room** para que la lista cargue al instante, y reproduce en segundo plano con una sesión multimedia del sistema.

> Repositorio: https://github.com/diegodiego999diego-crypto/MyPlayer
> Paquete: `com.player.ggo`

## Características

- **Lista de carpetas plana**: todas las carpetas con música dentro de `Music/` del almacenamiento compartido aparecen al mismo nivel, sin importar su profundidad.
- **Carga instantánea desde caché Room**: la primera vez se consulta MediaStore y se persiste; en aperturas siguientes la lista se muestra al instante desde la BD y se refresca en segundo plano.
- **Detección robusta de cambios**:
  - `RecursiveFileObserver` sobre `Music/` y todos sus subdirectorios.
  - `ContentObserver` sobre `MediaStore.Audio.Media` como respaldo.
  - Cuando `FileObserver` detecta un archivo nuevo o modificado, se llama a `MediaScannerConnection.scanFile()` sobre esa ruta antes de re-consultar, para que MediaStore indexe el archivo.
  - Re-escaneo completo al abrir la app (cubre cambios con la app cerrada).
  - Límite de observers (500) para no saturar inotify; liberados en `onCleared`/`onStop(isFinishing)`.
- **Mini-reproductor persistente** al pie de la lista: carátula, título, artista, play/pausa y siguiente. Al tocarlo abre el reproductor completo.
- **Reproductor completo**: carátula grande, slider de posición, retroceder/adelantar 10 s, anterior/siguiente, ciclo de repetición (off → carpeta → una).
- **Carátulas de álbum** en lista, búsqueda y reproductor (vía Coil), con icono por defecto cuando no hay carátula.
- **Búsqueda por título, artista, álbum y carpeta**: filtra en vivo desde el cache.
- **Material You**: colores dinámicos del sistema en Android 12+.
- **Reproducción en segundo plano** con notificación multimedia del sistema (MediaSession).

## Stack

| Capa | Tecnología |
|------|------------|
| Lenguaje | Kotlin 2.0.20 |
| UI | Jetpack Compose (BOM 2024.09.00) + Material 3 |
| Tema | Material You (color dinámico en Android 12+) |
| Reproductor | Media3 ExoPlayer 1.4.1 + MediaSession |
| Audio | MediaStore (MediaStore.Audio.Media) |
| Caché | Room 2.6.1 (runtime + ktx, compilador via KSP) |
| Imágenes | Coil 2.7.0 |
| Arquitectura | Single-activity + ViewModel + StateFlow |
| minSdk | 29 (Android 10) |
| targetSdk / compileSdk | 34 |

## Permisos

| Permiso | Uso |
|---------|-----|
| `READ_MEDIA_AUDIO` (Android 13+) | Leer el audio del almacenamiento compartido vía MediaStore. |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Mismo uso en Android 12 y anteriores. |
| `FOREGROUND_SERVICE` | Mantener el servicio de reproducción activo. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Tipo del servicio en primer plano (reproducción de media). |
| `POST_NOTIFICATIONS` (Android 13+) | Publicar la notificación multimedia del sistema. |

## Cómo compilar

Requisitos:

- Android Studio Hedgehog o superior (o Gradle 8.x por CLI).
- JDK 17.
- Android SDK con `platform-34` y `build-tools-34`.

Pasos:

```bash
# 1. Clonar
git clone https://github.com/diegodiego999diego-crypto/MyPlayer.git
cd MyPlayer

# 2. (Opcional) crear un archivo local.properties con la ruta del SDK:
#    echo "sdk.dir=/ruta/a/Android/Sdk" > local.properties

# 3. Build de debug
./gradlew assembleDebug

# 4. Instalar en un dispositivo/emulador conectado
./gradlew installDebug
```

El APK de debug queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Estructura del proyecto

```
app/src/main/java/com/player/ggo/
├── MainActivity.kt              # Entry point + lifecycle hooks de observers
├── MainViewModel.kt             # Estado UI + MediaController + observers
├── data/
│   ├── AppDatabase.kt           # Room database singleton
│   ├── MusicRepository.kt       # MediaStore query + Flow desde Room
│   ├── MediaStoreObserver.kt    # ContentObserver de respaldo
│   ├── RecursiveFileObserver.kt # FileObserver recursivo con límite
│   ├── SongDao.kt               # DAO con observeAll() y replaceAll()
│   └── SongEntity.kt            # Entidad Room
├── service/
│   └── MusicPlayerService.kt    # MediaSessionService con ExoPlayer
└── ui/
    ├── FolderListScreen.kt      # Lista de carpetas + búsqueda
    ├── SongListScreen.kt        # Lista de canciones de una carpeta
    ├── PlayerScreen.kt          # Reproductor a pantalla completa
    ├── theme/Theme.kt           # Material You
    └── components/
        ├── AlbumArt.kt          # Carátula con Coil + placeholder
        └── MiniPlayer.kt        # Mini-reproductor al pie
```

## Capturas

<!-- Marca para capturas -->

![Captura 1: lista de carpetas](docs/screenshot_folders.png)
![Captura 2: lista de canciones](docs/screenshot_songs.png)
![Captura 3: reproductor](docs/screenshot_player.png)
![Captura 4: búsqueda](docs/screenshot_search.png)

> Las capturas se colocan en `docs/` y se referencian aquí.

## Licencia

MIT — ver [LICENSE](LICENSE).
