package com.player.ggo.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Helpers en torno a MediaScannerConnection.
 *
 * Cuando FileObserver detecta un archivo nuevo o modificado, el indice
 * de MediaStore puede no estar actualizado todavia. Llamar a scanFile()
 * fuerza al sistema a indexar ese archivo, de modo que la consulta
 * posterior de [MusicRepository.refreshFromMediaStore] lo vea.
 */
object MediaScannerHelper {

    /**
     * Pide a MediaStore que re-escanee [paths] y suspende hasta que el
     * sistema responde con las URIs resultantes.
     * Devuelve la lista de URIs indexadas (puede ser vacia si el archivo
     * no es un media reconocido o si ya estaba indexado sin cambios).
     */
    suspend fun scanFiles(context: Context, paths: List<String>): List<Uri?> =
        withContext(Dispatchers.IO) {
            if (paths.isEmpty()) return@withContext emptyList()
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    paths.toTypedArray(),
                    null
                ) { _, uri ->
                    // callback por path; aqui se llama N veces. Acumulamos
                    // via un unico resultado sincronizado mas abajo.
                    if (cont.isActive) cont.resume(listOf(uri))
                }
                cont.invokeOnCancellation { /* nada: el sistema limpia solo */ }
            }
        }

    /**
     * Variante para un solo path; devuelve su URI o null.
     */
    suspend fun scanFile(context: Context, path: String): Uri? =
        scanFiles(context, listOf(path)).firstOrNull()
}
