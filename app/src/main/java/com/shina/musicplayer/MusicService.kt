package com.shina.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MusicService : Service() {

    companion object {
        const val ACTION_START = "com.shina.musicplayer.START"
        const val ACTION_TOGGLE = "com.shina.musicplayer.TOGGLE"
        const val ACTION_NEXT = "com.shina.musicplayer.NEXT"
        const val ACTION_PREV = "com.shina.musicplayer.PREV"
        const val ACTION_STOP = "com.shina.musicplayer.STOP"
        const val CHANNEL_ID = "shina_music_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                PlayerManager.toggle(applicationContext)
                startForeground(1, buildNotification())
            }
            ACTION_NEXT -> {
                PlayerManager.next(applicationContext)
                startForeground(1, buildNotification())
            }
            ACTION_PREV -> {
                PlayerManager.prev(applicationContext)
                startForeground(1, buildNotification())
            }
            ACTION_STOP -> {
                try { PlayerManager.player?.pause() } catch (e: Exception) {}
                stopForeground(true)
                stopSelf()
            }
            else -> {
                startForeground(1, buildNotification())
            }
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music playback", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun pending(action: String, code: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, code, intent, flags)
    }

    private fun buildNotification(): Notification {
        val song = PlayerManager.currentSong()
        val title = song?.title ?: "Shina Music Player"
        val artist = song?.artist ?: ""
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playing = PlayerManager.isPlaying()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(openPending)
            .setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous, "Prev", pending(ACTION_PREV, 1))
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pause" else "Play", pending(ACTION_TOGGLE, 2))
            .addAction(android.R.drawable.ic_media_next, "Next", pending(ACTION_NEXT, 3))
            .build()
    }
}
