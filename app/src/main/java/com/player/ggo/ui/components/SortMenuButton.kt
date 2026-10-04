package com.player.ggo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.player.ggo.SortMode

/**
 * Boton de ordenacion con menu desplegable. Muestra solo las opciones
 * relevantes para la lista actual ([options]) y marca la activa.
 */
@Composable
fun SortMenuButton(
    current: SortMode,
    options: List<SortMode>,
    onSelect: (SortMode) -> Unit
) {
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    trailingIcon = {
                        if (mode == current) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(mode)
                    }
                )
            }
        }
    }
}

private fun SortMode.label(): String = when (this) {
    SortMode.NAME -> "Nombre (A-Z)"
    SortMode.ARTIST -> "Artista (A-Z)"
    SortMode.DATE -> "Más recientes"
    SortMode.DURATION -> "Duración"
    SortMode.COUNT -> "Número de canciones"
}
