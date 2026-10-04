package com.player.ggo.ui

/**
 * Normaliza el nombre del artista para mostrarlo en la UI.
 *
 * MediaStore (y muchos tags ID3) devuelven "<unknown>" o cadenas
 * vacias cuando el artista no esta etiquetado. En la UI queremos un
 * texto legible ("Artista desconocido") o nada, segun el contexto.
 *
 * @param raw valor crudo que llega de Song.artist.
 * @return texto a mostrar, nunca null.
 */
fun displayArtist(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "Artista desconocido"
    if (trimmed.equals("<unknown>", ignoreCase = true)) return "Artista desconocido"
    return trimmed
}

/**
 * True si el artista crudo vale la pena mostrarlo como segundo titulo
 * (es decir, no es desconocido). Util para layouts que quieren ocultar
 * la linea de artista cuando no aporta informacion.
 */
fun hasArtist(raw: String): Boolean =
    raw.trim().let { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }
