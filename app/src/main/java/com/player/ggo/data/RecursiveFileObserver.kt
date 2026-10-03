package com.player.ggo.data

import android.os.FileObserver
import java.io.File

/**
 * FileObserver recursivo: registra un observador en la raiz y en TODAS
 * las subcarpetas existentes, y anade observadores cuando aparecen
 * carpetas nuevas. Ante cualquier cambio (crear, borrar, mover archivos
 * o carpetas) invoca [onChange], que debe re-escanear con MediaStore.
 *
 * Asi el reproductor nunca depende de un "escaneo" manual: si una
 * cancion aparece o desaparece del almacenamiento, la app se entera.
 */
class RecursiveFileObserver(
    private val root: File,
    private val onChange: () -> Unit
) {
    private val observers = mutableMapOf<String, SingleObserver>()

    private val mask = CREATE or DELETE or MOVED_FROM or MOVED_TO or
        DELETE_SELF or MOVE_SELF or ATTRIB

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

    private fun addRecursive(dir: File) {
        if (!dir.isDirectory) return
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
            val type = event and ALL_EVENTS

            if (type == DELETE_SELF || type == MOVE_SELF) {
                // La carpeta observada desaparecio: quitar su observador
                synchronized(observers) {
                    observers.remove(dir.absolutePath)
                    stopWatching()
                }
            }

            if (path != null && (type == CREATE || type == MOVED_TO)) {
                // Si nacio una carpeta nueva, observarla tambien (recursivo)
                val child = File(dir, path)
                if (child.isDirectory) {
                    synchronized(observers) { addRecursive(child) }
                }
            }

            // Cualquier evento relevante dispara re-escaneo
            onChange()
        }
    }
}
