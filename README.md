# Agenda Lavanda 1.3.0

Agenda Android local, sin cuentas, anuncios, analítica ni permiso de internet. Perfil y temas configurables, navegación para teléfono/tablet, calendario mensual/semanal/diario, eventos recurrentes, tareas, notas, cuadernos con páginas, diario, skincare, agua, hábitos con rachas, metas con progreso, borradores y papelera.

## Prueba en Android

Descarga el APK de la versión publicada en https://github.com/Andrex-Code/agenda-lavanda/releases . Android 8.0 o posterior. Exporta una copia antes de actualizar. El APK debug usa la misma firma de las versiones anteriores de este equipo y tiene un versionCode superior. La instalación se hace abriendo el APK desde Descargas y autorizando la fuente si Android lo pide.

## Escritura y recuperación

Notas → Nueva página → Escribir con lápiz. Colores, grosores, presión proporcionada por el dispositivo, borrador de trazos completos, deshacer/rehacer, zoom con dos dedos y modo Mover. Solo lápiz ignora eventos de dedo; no es garantía de rechazo de palma en todos los dispositivos. Hoja A4 virtual con plantillas. Inicio o Notas → Nueva hoja manuscrita abre directamente el lienzo. Lápiz, borrador, mover, deshacer y rehacer permanecen visibles; el botón de opciones reúne color, grosor y papel. Los cambios se guardan automáticamente como páginas en la agenda, también al cerrar. Guardar permite confirmar y salir. Borradores recupera sesiones anteriores o guardados interrumpidos. Un error se muestra en el editor y permite reintentar sin cerrarlo. El autoguardado tiene un breve retardo; un corte instantáneo puede perder los últimos cambios no confirmados.

Cuadernos permite crear y agrupar páginas. Una nota puede moverse de cuaderno desde su editor. La papelera conserva elementos hasta restaurarlos o eliminarlos definitivamente. Eliminar definitivamente un cuaderno elimina sus páginas asociadas. PDF exporta texto paginado y, si existe, una hoja adicional con los trazos. PNG exporta la hoja manuscrita.

## Copias y privacidad

Ajustes → Exportar copia completa. JSON v2 incluye registros, vínculos de cuadernos, historial, papelera, borradores, perfil, tema, visibilidad de agua/skincare y preferencia de recordatorio de copia; admite importar copias v1. Límite 15 MB. Se valida antes de reemplazar la base y se conserva un archivo local previo a la importación para recuperación. El JSON no está cifrado: guárdalo en una ubicación privada. Las copias locales se pierden al desinstalar: exporta una copia fuera de la app. No hay sincronización ni copias automáticas en nube.

## Compilar

JDK 17 o 21, SDK Android Platform 35 y Gradle Wrapper 8.9. En Windows:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

El APK está en app/build/outputs/apk/debug/app-debug.apk. connectedDebugAndroidTest requiere emulador o dispositivo conectado. Android Studio abre la raíz del proyecto.

## Firma para años de mantenimiento

El APK debug sirve para pruebas. La misma firma es obligatoria para actualizaciones compatibles. Para publicación, crea una clave propia con Android Studio → Generate Signed Bundle / APK, guarda el keystore y contraseñas en dos lugares seguros y no los subas al repo. Incrementa versionCode en cada actualización y conserva applicationId. Para cambiar de la firma debug a otra firma, exporta datos, desinstala la prueba, instala release e importa. No pierdas la clave: Android no admite actualizar con otra firma.

## Arquitectura y migraciones

Room versión 2 con migración explícita desde versión 1; esquemas en app/schemas. Entry agrupa registros con índice de cuaderno; Draft conserva borradores. DraftSession serializa autoguardado y confirmación final para impedir que una escritura pendiente resucite un borrador confirmado. EntryCodec y Backup validan y conservan referencias. InkDocument guarda trazos versionados y lee los manuscritos anteriores. La UI está en MainActivity, Screens, EditorScreen y el paquete ink. No usar migraciones destructivas.

## Límites conocidos

No incluye alarmas/notificaciones, reconocimiento de escritura, selección/movimiento de trazos individuales, colaboración ni actualización automática. La sensibilidad y rechazo de palma deben validarse en tabletas físicas. Las tareas tienen fecha/hora pero no disparan recordatorios. La recurrencia de eventos no equivale a un sistema de alertas.

## Mejoras UX 1.3.0

Navegación lateral más amplia y sin duplicar Ajustes en la cabecera de tablet. Calendario y agenda del día en paneles a partir de 900 dp disponibles; Hoy y contador de eventos independientes. Listados en dos columnas desde 720 dp y formularios con ancho de lectura limitado. Estado y orden separados. Fechas legibles, estados vacíos por sección y ánimo etiquetado. Reflexión antes del autocuidado, con agua/skincare ocultables sin borrar historial. Guía opcional en Inicio, recuperable desde Ajustes. Recordatorio semanal de copia dentro de Inicio, desactivable; no es una notificación del sistema ni crea copias automáticamente.

Las preferencias de visibilidad y recordatorio se guardan de inmediato; los campos de nombre/tema/meta requieren Guardar preferencias. Las copias anteriores usan los valores predeterminados al no incluir estas nuevas preferencias. La guía es propia del dispositivo. El ánimo sigue siendo parte del texto; no se anuncian estadísticas de ánimo.
