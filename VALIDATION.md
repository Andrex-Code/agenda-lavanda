# Mis Días 1.3.0 · Revisión UX

## Verificación del 1 de noviembre de 2026

La ronda Android final de los cambios funcionales es https://github.com/Andrex-Code/agenda-lavanda/actions/runs/41341326319 (commit f4260bbaff3919ec1c54ceb923e6594b7cdbf69b): aprobada en Android 30 celular (720×1280, 240 dpi) y Android 35 tablet (1600×2560, 240 dpi). En cada emulador se ejecutaron 14 pruebas instrumentadas sin fallos. También se aprobaron 13 pruebas JVM de lógica y backups. Después de esa ronda solo se corrigió el texto de la fecha de copia y se aplicó el nombre comercial Mis Días, sin cambiar applicationId, base de datos o formato de backups. La compilación local final comprueba esos textos y recursos.

Cobertura añadida: autoguardado continuo con identidad estable y sin duplicados; cierre de manuscrito sin título; borrador recuperable ante fallo de guardado; clave compatible con backups al elegir cuaderno antes de la primera confirmación; preferencia de diario y recordatorio en copias nuevas y valores por defecto en copias antiguas; conservación de renglones al reducir el lienzo a ancho de celular. Se mantienen migración 1→2, papelera, hábitos, agua, lápiz/presión simulados, rechazo de dedo en modo lápiz, borrado de trazos y exportación.

## Comprobación visual

Se inspeccionaron capturas reales de calendario horizontal y metas vertical en tablet, y escritura en celular. El menú ya muestra las palabras completas; el calendario separa mes y agenda diaria cuando hay espacio y muestra Hoy junto al número de eventos; las herramientas principales de tinta están visibles; filtros y orden están separados. Las capturas contienen datos de QA y un nombre de agenda configurable, Mi Agenda; no son imágenes de una tablet física.

Se midió el contraste sRGB de los colores principales en los cuatro temas: mínimo 4,62:1 sobre contenedor seleccionado, 5,19:1 sobre fondo, 5,56:1 sobre superficie y 5,75:1 de blanco sobre botones primarios. Esta medición parcial no sustituye una auditoría completa de accesibilidad.

## Límites de la revisión

Pendientes: tablet y lápiz físicos, respuesta percibida y rechazo de palma de cada fabricante, TalkBack, letra al 200 %, pantalla dividida y sesiones con usuarias. El ánimo permanece en el texto y no hay tendencias de ánimo. Los listados usan tarjetas en dos columnas, no un editor de detalle permanente. El recordatorio de copia aparece en Inicio, no es una notificación ni una copia automática. El autoguardado tiene un retardo de 400 ms; un cierre instantáneo del proceso puede perder cambios aún no escritos. No se promete ausencia total de bugs.

El APK sigue siendo debug para instalación manual. La firma se compara con 1.2.1 antes de publicar; no hay actualización automática. La documentación de firma release, resguardo del keystore y backups está en README.md.

---

## Historial de verificación anterior

# Verificación de Agenda Lavanda 1.2.1

Revisión funcional: e65dadd1795855d3e91f48682811017c803873aa.
Ejecución: https://github.com/Andrex-Code/agenda-lavanda/actions/runs/41336425098

- Compilación local y firma de APK: correctas. Misma firma debug de las versiones anteriores.
- 12 pruebas JVM aprobadas.
- Teléfono Android 11, 720×1280: 11 pruebas Android aprobadas.
- Tablet Android 15, 1600×2560: 11 pruebas Android aprobadas.
- Análisis estático Android: sin errores; quedan avisos de actualización de dependencias y asignaciones de renderizado.
- Capturas revisadas: editor manuscrito y calendario; se corrigió el lienzo que tapaba herramientas.

Cobertura: migración Room 1→2 conservando notas, borradores y guardado final, fallo de escritura simulado, papelera y restauración de páginas, operaciones concurrentes de agua/rutinas, referencias de cuadernos en copias, compatibilidad de manuscritos, PDF multipágina/PNG, lápiz/borrrador/presión/palma simulados, caché y recorte del lienzo, creación de cuadernos/páginas, diario, metas, navegación y rotación.

La prueba con lápiz físico y sus particularidades de presión/palma sigue siendo necesaria. No se garantiza ausencia total de defectos. La app no incluye alarmas, OCR ni sincronización; las copias JSON tienen un límite de 15 MB. El APK conserva firma debug para pruebas y actualizaciones manuales. Exporta una copia antes de actualizar.

SHA-256 APK:
Se incluye el hash calculado del archivo publicado en la entrega.
1D818068CD13CC525370C3D609358BA7433071C904BFCE5109C8625DEF21C512
