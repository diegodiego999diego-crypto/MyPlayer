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
     * escucha.
     */
    private val mask = CREATE or DELETE or MOVED_FROM or MOVED_TO or
        DELETE_SELF or MOVE_SELF or ATTRIB or MODIFY or CLOSE_WRITE

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

    private inner class SingleObserver(val dir: File) : FileObserver(dir, mask) {
        override fun onEvent(event: Int, path: String?) {
            val type = event and 0xfff // ALL_EVENTS mask low bits

            if (type == DELETE_SELF || type == MOVE_SELF) {
                synchronized(observers) {
                    observers.remove(dir.absolutePath)
                    stopWatching()
                }
            }

            val absolute: String? = path?.let { File(dir, it).absolutePath }

            if (path != null && (type == CREATE || type == MOVED_TO)) {
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
