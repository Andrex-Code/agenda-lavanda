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
