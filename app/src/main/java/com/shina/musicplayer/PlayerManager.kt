package com.shina.musicplayer

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlin.random.Random

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri,
    val albumId: Long
)

object PlayerManager {
    var player: MediaPlayer? = null
    var playlist: List<Song> = emptyList()
    var index: Int = -1
    var shuffle: Boolean = false
    var repeatMode: Int = 0 // 0 off, 1 all, 2 one
    @JvmField var speed: Float = 1.0f
    var onSongChanged: ((Song?) -> Unit)? = null
    var onIsPlayingChanged: ((Boolean) -> Unit)? = null

    fun currentSong(): Song? = playlist.getOrNull(index)

    fun isPlaying(): Boolean = try { player?.isPlaying == true } catch (e: Exception) { false }

    fun play(context: Context, list: List<Song>, position: Int) {
        if (list.isEmpty() || position < 0 || position >= list.size) return
        playlist = list
        index = position
        playCurrent(context)
    }

    private fun playCurrent(context: Context) {
        val song = currentSong() ?: return
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(context, song.uri)
                setOnPreparedListener { mp ->
                    try { mp.playbackParams = mp.playbackParams.setSpeed(speed) } catch (e: Exception) {}
                    mp.start()
                    onIsPlayingChanged?.invoke(true)
                }
                setOnCompletionListener {
                    when (repeatMode) {
                        2 -> playCurrent(context)
                        1 -> next(context, auto = true)
                        else -> {
                            if (index < playlist.size - 1) next(context, auto = true)
                            else onIsPlayingChanged?.invoke(false)
                        }
                    }
                }
                prepareAsync()
            }
            onSongChanged?.invoke(song)
        } catch (e: Exception) {
            onSongChanged?.invoke(song)
        }
    }

    fun toggle(context: Context) {
        val p = player
        if (p == null) {
            if (playlist.isNotEmpty() && index >= 0) playCurrent(context)
            return
        }
        try {
            if (p.isPlaying) {
                p.pause()
                onIsPlayingChanged?.invoke(false)
            } else {
                p.start()
                onIsPlayingChanged?.invoke(true)
            }
        } catch (e: Exception) {}
    }

    fun next(context: Context, auto: Boolean = false) {
        if (playlist.isEmpty()) return
        index = if (shuffle && playlist.size > 1) {
            var n = Random.nextInt(playlist.size)
            if (n == index) n = (n + 1) % playlist.size
            n
        } else {
            (index + 1) % playlist.size
        }
        playCurrent(context)
    }

    fun prev(context: Context) {
        if (playlist.isEmpty()) return
        index = if (index - 1 < 0) playlist.size - 1 else index - 1
        playCurrent(context)
    }

    fun seekTo(ms: Int) {
        try { player?.seekTo(ms) } catch (e: Exception) {}
    }

    fun setSpeed(value: Float) {
        speed = value
        try {
            val p = player
            if (p != null) p.playbackParams = p.playbackParams.setSpeed(value)
        } catch (e: Exception) {}
    }

    fun duration(): Int = try { player?.duration ?: 0 } catch (e: Exception) { 0 }
    fun position(): Int = try { player?.currentPosition ?: 0 } catch (e: Exception) { 0 }
    fun audioSessionId(): Int = try { player?.audioSessionId ?: 0 } catch (e: Exception) { 0 }
}
