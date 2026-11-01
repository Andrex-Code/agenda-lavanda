# Verificación de 1.2.0

- Compilación APK debug y APK de instrumentación: correcta.
- Pruebas JVM: 11 aprobadas (BackupTest y DomainTest). Incluyen compatibilidad de manuscritos, preservación de estilos, borrador con fecha incompleta, rachas, recurrencia mensual y relaciones de cuadernos en respaldos.
- Firma APK: verificada; misma clave debug de la versión 1.1.0.
- Pruebas de Android: intentadas, sin resultado válido. El AVD API 35 presentó reinicios de servicios y el diálogo del sistema «Process system isn’t responding». Las ejecuciones de instrumentación finalizaron antes de ejecutar pruebas; no se consideran aprobadas.
- Pendiente de validación real: migración Room 1→2, restauración completa, creación/edición de páginas y borradores, PDF/PNG, rotación, presión del lápiz y rechazo de palma en tablet.

La publicación es prerelease para pruebas. Exportar copia antes de actualizar. No tratar esta entrega como una versión comercial validada.
