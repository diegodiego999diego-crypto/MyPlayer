package com.player.ggo.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore

/**
 * ContentObserver sobre MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
 * con notifyForDescendants=true. Actua como respaldo del
 * RecursiveFileObserver: cubre archivos nuevos que el FileObserver
 * no llegue a ver (por ejemplo, cuando otra app inserta audio via
 * MediaStore sin tocar el sistema de archivos observado, o cuando se
 * agotan los watchers de inotify).
 *
 * IMPORTANTE: debe liberarse con [unregister] para no filtrar el
 * observer ni el HandlerThread asociado.
 */
class MediaStoreObserver(
    context: Context,
    private val onChange: () -> Unit
) {
    private val appContext = context.applicationContext
    private val thread = HandlerThread("MediaStoreObserver").apply { start() }
    private val handler = Handler(thread.looper)

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            onChange()
        }
    }

    fun register() {
        appContext.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer
        )
    }

    fun unregister() {
        appContext.contentResolver.unregisterContentObserver(observer)
        thread.quitSafely()
    }
}
