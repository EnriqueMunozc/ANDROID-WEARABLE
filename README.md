# FocusWear

FocusWear es una aplicación de enfoque con dos módulos Android: `mobile` para el teléfono y `app` para relojes Wear OS. El módulo `routine` contiene componentes compartidos para la configuración y sincronización de las sesiones.

## Requisitos

- Android Studio y Android SDK.
- Android SDK Platform 37 para compilar el proyecto.
- JDK 25 configurado en Gradle.
- Dos emuladores: uno de teléfono y otro de Wear OS.

## Crear y emparejar los emuladores

En Android Studio, abre **Tools > Device Manager > Add a New Device > Create Virtual Device** y crea:

1. **Teléfono:** categoría **Phone**, preferentemente **Pixel 7**, con Android 14 o superior (API 34 o superior). Elige una imagen **Google Play** y arquitectura `x86_64` si está disponible.
2. **Reloj:** categoría **Wear OS**, dispositivo **Wear OS Small Round**, con Wear OS 4 o superior. Elige una imagen **Google Play** y arquitectura `x86_64` si están disponibles.

Estas configuraciones son recomendaciones para reproducir el entorno de prueba; pueden diferir de las utilizadas durante el desarrollo. El proyecto declara `minSdk` 30 y `targetSdk` 37.

Inicia ambos emuladores desde **Device Manager**. En el menú de tres puntos del reloj, selecciona **Pair Wearable**, si está disponible, o utiliza el asistente de emparejamiento de Device Manager. Selecciona el teléfono y completa la vinculación.

## Ejecutar la aplicación

1. Abre el proyecto en Android Studio y espera a que termine la sincronización de Gradle.
2. Selecciona el módulo `mobile` y ejecútalo en el emulador de teléfono.
3. Selecciona el módulo `app` y ejecútalo en el emulador Wear OS.
4. Abre FocusWear en ambos dispositivos, configura una sesión desde el teléfono y envíala al reloj.
5. Comprueba la sincronización, el temporizador, las vibraciones y el registro de distracciones.

## Compilar y probar

Desde la raíz del proyecto, en Windows:

```powershell
.\gradlew.bat :mobile:assembleDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :mobile:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest
```

Las pruebas instrumentadas del módulo de teléfono requieren que haya un dispositivo o emulador conectado:

```powershell
.\gradlew.bat :mobile:connectedDebugAndroidTest
```
