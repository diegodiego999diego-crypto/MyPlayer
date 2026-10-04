package com.player.ggo.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Servicio de reproduccion en segundo plano con MediaSession.
 * Muestra la notificacion multimedia estandar del sistema.
 *
 * Los incrementos de seek (10 s hacia atras/adelante) se configuran
 * aqui, en el ExoPlayer.Builder, porque Media3 1.4.x no expone
 * setSeekBackIncrementMs / setSeekForwardIncrementMs en la interfaz
 * Player (que es lo que implementa MediaController en el cliente).
 */
class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
