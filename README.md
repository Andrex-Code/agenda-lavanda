# Agenda Lavanda

Aplicación Android nativa en Kotlin y Jetpack Compose para una agenda personal que funciona sin conexión. El nombre de la agenda y el nombre de la persona se eligen al abrirla por primera vez y se pueden cambiar en Ajustes.

## Incluye

- Inicio con resumen de tareas y avance de agua.
- Calendario mensual, semanal y diario con eventos y tareas fechadas.
- Tareas, notas con escritura manuscrita mediante dedo o lápiz táctil, cuadernos, diario y reflexión, estado de ánimo, rutinas de skincare, contador configurable de agua, hábitos y metas.
- Temas lavanda, rosa, verde y durazno; navegación inferior en pantallas angostas y rail lateral en tabletas.
- Base local Room y preferencias DataStore. No hay cuenta, servidor, SDK de analítica ni permiso de Internet.
- Exportación e importación JSON. La importación valida formato, versión, tamaño, cantidad y secciones; si pasa la validación, sustituye las entradas locales. Haz una copia antes de restaurar.

## Compilar e instalar

Requisitos: Android Studio reciente (incluye JDK 17), Android SDK Platform 35, Android Build Tools 35.x y conexión a Internet la primera vez para descargar Gradle/dependencias. Abre `outputs/AgendaLavanda` en Android Studio, deja que termine la sincronización y elige **Build > Build Bundle(s) / APK(s) > Build APK(s)**. El APK debug se crea en `app/build/outputs/apk/debug/app-debug.apk` y Android Studio muestra el botón **Locate**.

En Windows, desde la carpeta del proyecto, también puedes ejecutar:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

Para instalar manualmente, copia `app-debug.apk` a la tableta, ábrelo desde Archivos y autoriza temporalmente la instalación desde esa fuente si Android lo solicita. También se puede conectar la tableta con depuración USB y ejecutar `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## APK debug y actualizaciones

El APK debug está firmado automáticamente con la clave de depuración local de Gradle. Sirve para instalación manual y pruebas. No debe distribuirse como versión final: la clave debug puede cambiar entre equipos o reinstalaciones, y las instalaciones firmadas con otra clave no se pueden actualizar encima; habrá que desinstalar primero y eso borra los datos de la app. La aplicación mantiene `applicationId` `com.agendalavanda` y `versionCode` para futuras actualizaciones.

## Crear un release y conservar la clave

Genera una clave de publicación una sola vez en un equipo seguro. No subas el archivo ni sus contraseñas al repositorio:

```powershell
keytool -genkeypair -v -keystore agenda-release.jks -alias agenda -keyalg RSA -keysize 4096 -validity 10000
```

Resguarda el keystore y sus contraseñas en dos ubicaciones seguras y separadas. Para cada actualización, firma con **el mismo keystore y alias**, incrementa `versionCode` en `app/build.gradle.kts` y crea un APK release desde Android Studio: **Build > Generate Signed Bundle / APK > APK > Create new/Choose existing**. Comprueba e instala la actualización antes de distribuirla. Si pierdes la clave, Android no aceptará el APK como actualización de la instalación existente.

## Datos y respaldos

La base de datos está en el almacenamiento privado de la app. Exporta regularmente una copia desde **Ajustes > Exportar copia JSON** y guárdala en una ubicación privada adicional (por ejemplo, almacenamiento externo protegido). El JSON puede contener texto personal; no lo publiques ni lo envíes sin cifrar. Para restaurar, usa **Importar copia JSON** y selecciona el archivo. No se hace sincronización en nube.

## Arquitectura y mantenimiento

- `data/Entry.kt`, `EntryDao.kt`, `AppDatabase.kt`: registros locales tipados por sección en Room.
- `data/Backup.kt`: formato versionado y validación de importaciones.
- `MainActivity.kt`: interfaz Compose, preferencias de perfil/tema y navegación adaptable.
- Room usa esquema exportado en `app/schemas`. Al cambiar entidades, incrementa `@Database(version = ...)`, agrega una migración explícita en `AppDatabase` y conserva los esquemas previos. No habilites `fallbackToDestructiveMigration`.
- Ejecuta `gradlew testDebugUnitTest` para las pruebas de validación y serialización. Compila además `assembleDebug` antes de instalar.

La prueba de uso real debe hacerse también en una tableta Android física, especialmente la escritura con lápiz, restauración desde el proveedor de archivos de esa tableta y rotación. Esta entrega no garantiza ausencia total de defectos.
