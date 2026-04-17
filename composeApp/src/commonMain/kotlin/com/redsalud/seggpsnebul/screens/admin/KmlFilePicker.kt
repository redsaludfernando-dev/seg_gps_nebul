package com.redsalud.seggpsnebul.screens.admin

/** Abre el selector de archivos del sistema para elegir un KML. */
expect fun triggerKmlFilePicker()

/**
 * Devuelve el contenido del KML seleccionado y lo elimina del buffer.
 * Devuelve null si aún no hay archivo disponible.
 */
expect fun consumePendingKml(): String?
