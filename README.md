# Véritas AI — Android APK

App nativa Android para **Véritas AI**, orquestador multi-rol OSINT.

## Stack

- **Lenguaje**: Kotlin
- **UI**: Jetpack Compose + WebView
- **Build**: Gradle 8.11.1 (Kotlin DSL), SDK API 34, minSdk 24, OpenJDK 21
- **Auth**: PBKDF2-SHA256 + AES-256-GCM (Android Keystore / EncryptedSharedPreferences)
- **Optimización**: R8 + shrinkResources (APK ~3.2 MB)

## Arquitectura

```
app/src/main/java/com/veritas/ai/
├── auth/AuthManager.kt          # Singleton auth (login, register, session, password change)
├── camera/CameraHelper.kt        # ActivityResultContract para cámara y galería
├── deep/DeepLinkRouter.kt        # Router de deep links (veritas://...)
├── notifications/
│   ├── NotificationHelper.kt     # Canales y mostrador de notificaciones
│   └── NotificationPollWorker.kt # WorkManager polling (sin Firebase)
├── offline/OfflineManager.kt     # Detector de conectividad
├── ui/theme/VeritasTheme.kt      # Tema Compose
├── LoginActivity.kt              # Login/registro con Compose
├── MainActivity.kt               # WebView + JS Bridge + fetch interceptor
├── RouteActivity.kt              # Router invisible para deep links
├── SettingsActivity.kt           # Ajustes con Compose
├── ShareReceiverActivity.kt      # Receptor de ACTION_SEND
└── SplashActivity.kt             # Splash con animaciones
```

## Backend

La app se conecta a `https://veritas-ai.pages.dev` (Cloudflare Workers + D1 + R2).
Repo del backend: [veritas-ai](https://github.com/maxrivero783-pixel/veritas-ai)

## Compilar

```bash
# Desde la raíz del proyecto
./gradlew assembleRelease
```

El APK firmado se genera en `app/build/outputs/apk/release/`.

## Features implementadas

- WebView con auth gate y token injection
- Fetch interceptor (Authorization: Bearer automático)
- JS Bridge bidireccional (AndroidBridge)
- Login/registro nativo (sin WebView para auth)
- Splash screen con animaciones
- Ajustes nativos (cambio contraseña, limpiar cache, logout)
- Cámara y galería (FAB + FileProvider)
- Deep links (`veritas://new-chat`, `veritas://settings`, `veritas://tool/<name>`, `veritas://chat/<id>`)
- Share intent receiver
- Offline banner con ConnectivityManager
- Pull-to-refresh
- Immersive mode
- File chooser
- Push notifications vía polling (sin Firebase/Google)

## Licencia

MIT
