package com.shina.musicplayer

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var songList: ListView
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var playBtn: Button
    private lateinit var shuffleBtn: Button
    private lateinit var repeatBtn: Button
    private lateinit var speedBtn: Button
    private lateinit var favBtn: Button
    private lateinit var favoritesBtn: Button
    private lateinit var seekBar: SeekBar
    private lateinit var albumArt: ImageView

    private var allSongs: List<Song> = emptyList()
    private var displayed: List<Song> = emptyList()
    private var showFavoritesOnly = false
    private var query = ""
    private var sortMode = 0
    private var equalizer: Equalizer? = null
    private var sleepTimer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        loadSongs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        songList = findViewById(R.id.songList)
        titleText = findViewById(R.id.titleText)
        artistText = findViewById(R.id.artistText)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        playBtn = findViewById(R.id.playBtn)
        shuffleBtn = findViewById(R.id.shuffleBtn)
        repeatBtn = findViewById(R.id.repeatBtn)
        speedBtn = findViewById(R.id.speedBtn)
        favBtn = findViewById(R.id.favBtn)
        favoritesBtn = findViewById(R.id.favoritesBtn)
        seekBar = findViewById(R.id.seekBar)
        albumArt = findViewById(R.id.albumArt)

        val searchInput: EditText = findViewById(R.id.searchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; refreshList() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        findViewById<Button>(R.id.libraryBtn).setOnClickListener { showFavoritesOnly = false; refreshList() }
        favoritesBtn.setOnClickListener { showFavoritesOnly = true; refreshList() }
        findViewById<Button>(R.id.sortBtn).setOnClickListener {
            sortMode = (sortMode + 1) % 3
            Toast.makeText(this, when (sortMode) { 0 -> "Sort: Judul"; 1 -> "Sort: Artis"; else -> "Sort: Durasi" }, Toast.LENGTH_SHORT).show()
            refreshList()
        }
        findViewById<Button>(R.id.timerBtn).setOnClickListener { showSleepTimerDialog() }
        findViewById<Button>(R.id.equalizerBtn).setOnClickListener { showEqualizerDialog() }

        playBtn.setOnClickListener {
            PlayerManager.toggle(this)
            startMusicService()
        }
        findViewById<Button>(R.id.nextBtn).setOnClickListener { PlayerManager.next(this); startMusicService() }
        findViewById<Button>(R.id.prevBtn).setOnClickListener { PlayerManager.prev(this); startMusicService() }

        shuffleBtn.setOnClickListener {
            PlayerManager.shuffle = !PlayerManager.shuffle
            shuffleBtn.text = if (PlayerManager.shuffle) "🔀 On" else "🔀 Off"
        }
        repeatBtn.setOnClickListener {
            PlayerManager.repeatMode = (PlayerManager.repeatMode + 1) % 3
            repeatBtn.text = when (PlayerManager.repeatMode) { 1 -> "🔁 All"; 2 -> "🔂 One"; else -> "🔁 Off" }
        }
        speedBtn.setOnClickListener {
            val next = when (PlayerManager.speed) { 1.0f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 0.75f; else -> 1.0f }
            PlayerManager.setSpeed(next)
            speedBtn.text = "Speed ${next}x"
        }
        favBtn.setOnClickListener {
            val song = PlayerManager.currentSong() ?: return@setOnClickListener
            toggleFavorite(song.id)
            updateFavButton()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) PlayerManager.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        songList.setOnItemClickListener { _, _, position, _ ->
            val list = displayed
            if (position >= 0 && position < list.size) {
                PlayerManager.play(this, list, position)
                startMusicService()
            }
        }

        PlayerManager.onSongChanged = { song -> runOnUiThread { updateNowPlaying(song) } }
        PlayerManager.onIsPlayingChanged = { playing -> runOnUiThread { playBtn.text = if (playing) "⏸" else "▶️" } }

        requestPermissionsIfNeeded()
        handler.post(updateSeekRunnable)
    }

    private val updateSeekRunnable = object : Runnable {
        override fun run() {
            try {
                val dur = PlayerManager.duration()
                val pos = PlayerManager.position()
                if (dur > 0) {
                    seekBar.max = dur
                    seekBar.progress = pos
                    currentTime.text = formatTime(pos.toLong())
                    totalTime.text = formatTime(dur.toLong())
                }
            } catch (e: Exception) {}
            handler.postDelayed(this, 500)
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (needed.isEmpty()) loadSongs() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun loadSongs() {
        val songs = mutableListOf<Song>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC + "!=0", null, MediaStore.Audio.Media.TITLE + " ASC")
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    songs.add(Song(id, it.getString(titleCol) ?: "Unknown", it.getString(artistCol) ?: "Unknown", it.getLong(durCol), uri, it.getLong(albumCol)))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca library musik", Toast.LENGTH_SHORT).show()
        }
        allSongs = songs
        if (allSongs.isEmpty()) Toast.makeText(this, "Tidak ada lagu ditemukan. Simpan MP3 di folder Music.", Toast.LENGTH_LONG).show()
        refreshList()
    }

    private fun favorites(): MutableSet<String> {
        val prefs = getSharedPreferences("favorites", MODE_PRIVATE)
        return prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun isFavorite(id: Long): Boolean = favorites().contains(id.toString())

    private fun toggleFavorite(id: Long) {
        val favs = favorites()
        if (favs.contains(id.toString())) favs.remove(id.toString()) else favs.add(id.toString())
        getSharedPreferences("favorites", MODE_PRIVATE).edit().putStringSet("ids", favs).apply()
        if (showFavoritesOnly) refreshList()
    }

    private fun refreshList() {
        var list = if (showFavoritesOnly) allSongs.filter { isFavorite(it.id) } else allSongs
        if (query.isNotEmpty()) {
            val q = query.lowercase()
            list = list.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
        list = when (sortMode) {
            1 -> list.sortedBy { it.artist.lowercase() }
            2 -> list.sortedBy { it.duration }
            else -> list.sortedBy { it.title.lowercase() }
        }
        displayed = list
        songList.adapter = SongAdapter(list)
    }

    inner class SongAdapter(private val items: List<Song>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val song = items[position]
            view.findViewById<TextView>(android.R.id.text1).apply {
                text = (if (isFavorite(song.id)) "❤️ " else "") + song.title
                setTextColor(0xFFFFFFFF.toInt())
            }
            view.findViewById<TextView>(android.R.id.text2).apply {
                text = song.artist + " • " + formatTime(song.duration)
                setTextColor(0x99FFFFFF.toInt())
            }
            return view
        }
    }

    private fun updateNowPlaying(song: Song?) {
        if (song == null) return
        titleText.text = song.title
        artistText.text = song.artist
        try {
            val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
            albumArt.setImageURI(artUri)
        } catch (e: Exception) {
            albumArt.setImageDrawable(null)
        }
        updateFavButton()
        playBtn.text = "⏸"
    }

    private fun updateFavButton() {
        val song = PlayerManager.currentSong() ?: return
        favBtn.text = if (isFavorite(song.id)) "❤️" else "🤍"
    }

    private fun startMusicService() {
        try {
            val intent = Intent(this, MusicService::class.java).setAction(MusicService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (e: Exception) {}
    }

    private fun showSleepTimerDialog() {
        val options = arrayOf("5 menit", "10 menit", "15 menit", "30 menit", "60 menit", "Matikan timer")
        AlertDialog.Builder(this).setTitle("Sleep timer").setItems(options) { _, which ->
            sleepTimer?.cancel()
            if (which == 5) {
                Toast.makeText(this, "Sleep timer dimatikan", Toast.LENGTH_SHORT).show()
                return@setItems
            }
            val minutes = listOf(5, 10, 15, 30, 60)[which]
            sleepTimer = object : CountDownTimer(minutes * 60 * 1000L, 1000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() {
                    try { PlayerManager.player?.pause() } catch (e: Exception) {}
                    playBtn.text = "▶️"
                }
            }.start()
            Toast.makeText(this, "Lagu berhenti dalam $minutes menit", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun showEqualizerDialog() {
        try {
            val sessionId = PlayerManager.audioSessionId()
            if (sessionId == 0) {
                Toast.makeText(this, "Putar lagu dulu untuk membuka equalizer", Toast.LENGTH_SHORT).show()
                return
            }
            if (equalizer == null) equalizer = Equalizer(0, sessionId)
            val eq = equalizer!!
            eq.enabled = true
            val bands = eq.numberOfBands.toInt()
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
            for (b in 0 until bands) {
                val band = b.toShort()
                val label = TextView(this).apply { text = "Band ${b + 1} - ${eq.getCenterFreq(band) / 1000} Hz"; setTextColor(0xFF000000.toInt()) }
                layout.addView(label)
                val range = eq.getBandLevelRange()
                val seek = SeekBar(this).apply {
                    max = (range[1] - range[0]).toInt()
                    progress = (eq.getBandLevel(band) - range[0]).toInt()
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) try { eq.setBandLevel(band, (progress + range[0]).toShort()) } catch (e: Exception) {}
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                }
                layout.addView(seek)
            }
            AlertDialog.Builder(this).setTitle("Equalizer").setView(layout).setPositiveButton("Tutup", null).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Equalizer tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
