package com.player.ggo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.player.ggo.R

/**
 * Muestra la caratula de un album a partir de su URI (la que entrega
 * MediaStore via content://media/external/audio/albumart/<albumId>).
 *
 * Si [albumArtUri] es null o la carga falla, se muestra un icono de
 * nota musical sobre un fondo con la forma del tema.
 *
 * @param size tamano del cuadrado en dp.
 * @param corner radio de redondeo en dp (0 = cuadrado perfecto).
 */
@Composable
fun AlbumArt(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    size: Int = 48,
    corner: Int = 8
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(corner.dp)

    Surface(
        modifier = modifier.size(size.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        if (albumArtUri.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clip(shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((size * 0.55f).dp)
                )
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_album_default),
                placeholder = painterResource(R.drawable.ic_album_default),
                modifier = Modifier.clip(shape)
            )
        }
    }
}
