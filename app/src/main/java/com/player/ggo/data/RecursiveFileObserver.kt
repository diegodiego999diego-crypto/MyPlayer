package com.player.ggo.data

import android.os.FileObserver
import java.io.File

/**
 * FileObserver recursivo: registra un observador en la raiz y en TODAS
 * las subcarpetas existentes, y anade observadores cuando aparecen
 * carpetas nuevas. Ante cualquier cambio (crear, borrar, mover archivos
 * o carpetas) invoca [onChange] con la ruta absoluta del archivo o
 * carpeta afectada (o null si el evento no lleva path), para que la
 * app pueda disparar MediaScannerConnection.scanFile() sobre esa ruta
 * antes de re-consultar MediaStore.
 *
 * Se aplica un limite superior [MAX_OBSERVERS] para no agotar el
 * limite de inotify del kernel (~1000 watchers por proceso).
 */
class RecursiveFileObserver(
    private val root: File,
    private val onChange: (absolutePath: String?) -> Unit
) {
    private val observers = mutableMapOf<String, SingleObserver>()

    /**
     * Mascara explicita en lugar de ALL_EVENTS (que esta deprecated
     * desde API 29) para evitar warnings y ser claros con lo que se
     * escucha. Las constantes son estaticas de FileObserver (Java);
     * en Kotlin hay que cualificarlas con FileObserver.<NAME>.
     */
    private val mask: Int =
        FileObserver.CREATE or FileObserver.DELETE or
        FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
        FileObserver.DELETE_SELF or FileObserver.MOVE_SELF or
        FileObserver.ATTRIB or FileObserver.MODIFY or FileObserver.CLOSE_WRITE

    fun startWatching() {
        synchronized(observers) {
            addRecursive(root)
        }
    }

    fun stopWatching() {
        synchronized(observers) {
            observers.values.forEach { it.stopWatching() }
            observers.clear()
        }
    }

    /** Cantidad de observers activos (para diagnostico y tests). */
    fun observerCount(): Int = synchronized(observers) { observers.size }

    private fun addRecursive(dir: File) {
        if (!dir.isDirectory) return
        if (observers.size >= MAX_OBSERVERS) return
        val path = dir.absolutePath
        if (!observers.containsKey(path)) {
            val obs = SingleObserver(dir)
            observers[path] = obs
            obs.startWatching()
        }
        dir.listFiles()?.filter { it.isDirectory }?.forEach { addRecursive(it) }
    }

    /**
     * FileObserver(File, Int) es API 31+. Como minSdk = 29, usamos el
     * constructor FileObserver(String, Int) con dir.absolutePath, que
     * existe desde API 1 y es seguro en todas las versiones soportadas.
     */
    private inner class SingleObserver(val dir: File) :
        FileObserver(dir.absolutePath, mask) {

        override fun onEvent(event: Int, path: String?) {
            val type = event and 0xfff // ALL_EVENTS mask low bits

            if (type == FileObserver.DELETE_SELF || type == FileObserver.MOVE_SELF) {
                // La carpeta observada desaparecio: quitar su observador
                synchronized(observers) {
                    observers.remove(dir.absolutePath)
                    stopWatching()
                }
            }

            val absolute: String? = path?.let { File(dir, it).absolutePath }

            if (path != null && (type == FileObserver.CREATE || type == FileObserver.MOVED_TO)) {
                // Si nacio una carpeta nueva, observarla tambien (recursivo)
                val child = File(dir, path)
                if (child.isDirectory) {
                    synchronized(observers) { addRecursive(child) }
                }
            }

            // Cualquier evento relevante dispara re-escaneo; pasamos la
            // ruta absoluta para que el caller pueda scanFile() primero.
            onChange(absolute)
        }
    }

    companion object {
        /** Limite blando para no saturar inotify. */
        private const val MAX_OBSERVERS = 500
    }
}
