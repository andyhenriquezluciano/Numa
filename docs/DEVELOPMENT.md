# Compilación

## Preparación

```sh
git submodule update --init --recursive
npm ci --prefix providers/youtube
```

## Android

Requiere JDK 21, Node.js 24, Rust, cargo-ndk, Android SDK 37 y NDK 28.2.13676358.
Configura `ANDROID_HOME` o `mobile/android/local.properties` con la ruta del SDK.

```sh
cd mobile/android
./gradlew testDebugUnitTest assembleDebug
```

En Windows usa `gradlew.bat`. Para la distribución, configura
`ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`
y `ANDROID_KEY_PASSWORD`, y ejecuta `assembleRelease`.
Conserva la misma clave para que los usuarios puedan actualizar sin desinstalar.

## Windows

Requiere MSVC 2022, Windows SDK, Qt 6.11.2 para MSVC x64 con WebEngine y WebView,
Meson 1.12 o posterior, CMake, Ninja, Rust y Node.js.
Ejecuta desde una consola de desarrollo de MSVC con Qt en `PATH`:

Si ya tienes `ffmpeg.exe` y `ffprobe.exe`, define `NUMA_FFMPEG_DIR` con su carpeta
para reutilizarlos durante la compilación y evitar descargar una distribución antigua.

```sh
meson setup build/desktop --buildtype=release
meson compile -C build/desktop
meson test -C build/desktop --print-errorlogs
```

`scripts/package-windows-native.py` prepara la aplicación con sus bibliotecas y modelos.
Publica únicamente el APK de Android y el setup de Windows. Los archivos de comprobación y los registros se conservan localmente.
El formato del instalador está documentado en [BOOTSTRAPPER.md](BOOTSTRAPPER.md).

## Servicios

Consulta `services/account/README.md` y `services/lastfm/README.md` para configurar
los servicios de Cloudflare. Usa tus propias credenciales; los archivos locales de
configuración y las claves están excluidos de Git.

## Verificación

Android CI ejecuta las pruebas unitarias del teléfono. Las pruebas de escritorio
se ejecutan con Meson. Las pruebas de los servicios se ejecutan con `npm test`
desde su directorio. No añadas binarios generados, registros ni sesiones al código fuente.
