# 1.2.1 — Revisión de estabilidad y dibujo

- Cierre seguro de borradores: si falla la escritura, el editor informa y conserva la página abierta.
- No se guarda una página vacía mientras se está cargando su contenido.
- Copias coherentes de registros y borradores; validación de referencias y recuperación de la copia local previa.
- Restauración de una página sin recuperar otras páginas eliminadas del cuaderno.
- Contadores y rutinas protegidos frente a toques rápidos simultáneos.
- El modo Solo lápiz conserva el trazo cuando aparece un contacto de palma.
- Caché de la hoja manuscrita para evitar repintar todos los trazos con cada movimiento.
- Recorte del lienzo y las vistas previas para no tapar la barra de herramientas.
- Cierre del teclado al guardar/cerrar y navegación adaptada al espacio del teclado.
- Etiquetas de hábitos y rutinas pulsables, icono propio y exclusión explícita de copias automáticas de Android.
- Pruebas automáticas en teléfono Android 11 y tablet Android 15; ver VALIDATION.md para los resultados exactos.

APK de prueba firmado con la misma clave de las versiones anteriores. Exporta una copia antes de actualizar. Sigue pendiente la validación con lápiz físico; no incluye alarmas ni reconocimiento de escritura.
- Los avisos de guardado permanecen fuera de la navegación inferior y lateral.
