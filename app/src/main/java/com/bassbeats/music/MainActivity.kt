package com.bassbeats.music

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*

private data class Song(val title: String, val artist: String, val uri: Uri)

class MainActivity : Activity() {
    private val blue = Color.rgb(0, 190, 255)
    private val bg = Color.rgb(7, 11, 20)
    private val panel = Color.rgb(16, 24, 39)
    private val white = Color.rgb(240, 247, 255)
    private val muted = Color.rgb(140, 160, 183)
    private val songs = mutableListOf<Song>()
    private var player: MediaPlayer? = null
    private var current = -1
    private var bass: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var visualizer: Visualizer? = null
    private var seeking = false
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var playButton: Button
    private lateinit var seek: SeekBar
    private lateinit var timeText: TextView
    private lateinit var songList: LinearLayout
    private lateinit var visualBars: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        volumeControlStream = AudioManager.STREAM_MUSIC
        buildUi()
        if (hasAudioPermission()) scanSongs() else requestPermissions(
            arrayOf(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE), 100
        )
    }

    private fun hasAudioPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 33) checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) scanSongs()
        else Toast.makeText(this, "Allow audio permission to find songs", Toast.LENGTH_LONG).show()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(TextView(this).apply {
            text = "BASS BEATS"; textSize = 25f; setTextColor(blue)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD); letterSpacing = .12f
        })
        root.addView(TextView(this).apply {
            text = "ELECTRIC BLUE  •  OFFLINE PLAYER"; textSize = 10f
            letterSpacing = .12f; setTextColor(muted)
        })
        root.addView(space(18))
        val playerCard = card()
        root.addView(playerCard, matchWrap())
        playerCard.addView(TextView(this).apply {
            text = "♫"; textSize = 64f; gravity = Gravity.CENTER; setTextColor(blue)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(12, 44, 72), Color.rgb(10, 17, 30))).apply {
                cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.rgb(0, 90, 140))
            }
        }, LinearLayout.LayoutParams(-1, dp(150)))
        titleText = TextView(this).apply {
            text = "Choose a song"; textSize = 21f; setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; maxLines = 1
        }
        playerCard.addView(titleText, matchWrap())
        artistText = TextView(this).apply {
            text = "Your local music library"; textSize = 13f; setTextColor(muted)
            gravity = Gravity.CENTER; maxLines = 1
        }
        playerCard.addView(artistText, matchWrap())
        visualBars = LinearLayout(this).apply {
            gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(8))
        }
        repeat(24) { i ->
            val bar = View(this).apply { background = rounded(blue, 5) }
            visualBars.addView(bar, LinearLayout.LayoutParams(dp(4), dp(10 + (i * 7 % 22))).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        playerCard.addView(visualBars, matchWrap())
        seek = SeekBar(this).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            thumbTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        playerCard.addView(seek, matchWrap())
        timeText = TextView(this).apply {
            text = "00:00 / 00:00"; textSize = 11f; setTextColor(muted); gravity = Gravity.CENTER
        }
        playerCard.addView(timeText, matchWrap())
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        controls.addView(button("⏮", false) { previous() }, LinearLayout.LayoutParams(dp(58), dp(52)))
        playButton = button("▶", true) { togglePlay() }
        controls.addView(playButton, LinearLayout.LayoutParams(dp(76), dp(58)).apply {
            marginStart = dp(14); marginEnd = dp(14)
        })
        controls.addView(button("⏭", false) { next() }, LinearLayout.LayoutParams(dp(58), dp(52)))
        playerCard.addView(controls, matchWrap())
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) timeText.text = "${fmt(progress)} / ${fmt(player?.duration ?: 0)}"
            }
            override fun onStartTrackingTouch(s: SeekBar?) { seeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) {
                try { player?.seekTo(s?.progress ?: 0) } catch (_: Exception) {}
                seeking = false
            }
        })
        root.addView(space(18))
        root.addView(sectionTitle("YOUR MUSIC"))
        songList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(songList, matchWrap())
        root.addView(space(18))
        root.addView(sectionTitle("SOUND LAB  •  EFFECTS"))
        val effectsCard = card()
        root.addView(effectsCard, matchWrap())
        effectsCard.addView(label("BASS BOOST"), matchWrap())
        val bassSeek = SeekBar(this).apply {
            max = 1000; progress = 350
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            thumbTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        effectsCard.addView(bassSeek, matchWrap())
        bassSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                if (user) try { if (bass == null) initEffects(); bass?.setStrength(p.toShort()) } catch (_: Exception) {}
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        effectsCard.addView(label("5-BAND EQUALIZER"), matchWrap())
        val eqArea = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        effectsCard.addView(eqArea, matchWrap())
        listOf("60", "230", "910", "3.6k", "14k").forEachIndexed { index, bandLabel ->
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            val slider = SeekBar(this).apply {
                max = 100; progress = 50; rotation = -90f
                progressTintList = android.content.res.ColorStateList.valueOf(blue)
                thumbTintList = android.content.res.ColorStateList.valueOf(blue)
            }
            col.addView(slider, LinearLayout.LayoutParams(dp(125), dp(40)))
            col.addView(TextView(this).apply {
                text = bandLabel; textSize = 10f; gravity = Gravity.CENTER; setTextColor(muted)
            }, matchWrap())
            eqArea.addView(col, LinearLayout.LayoutParams(0, dp(145), 1f))
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    if (user) try {
                        if (equalizer == null) initEffects()
                        val eq = equalizer ?: return
                        if (index < eq.numberOfBands) {
                            val range = eq.bandLevelRange
                            val level = range[0] + (range[1] - range[0]) * p / 100
                            eq.setBandLevel(index.toShort(), level.toShort())
                        }
                    } catch (_: Exception) {}
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        effectsCard.addView(label("PRESETS"), matchWrap())
        val presets = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Normal", "Bass Boost", "Rock", "Pop", "Classical"))
        }
        effectsCard.addView(presets, matchWrap())
        presets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                try {
                    if (equalizer == null) initEffects()
                    val eq = equalizer ?: return
                    if (position < eq.numberOfPresets) eq.usePreset(position.toShort())
                } catch (_: Exception) {}
            }
        }
    }

    private fun scanSongs() {
        songs.clear()
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        try {
            contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                "${MediaStore.Audio.Media.TITLE} ASC")?.use { c: Cursor ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    songs.add(Song(c.getString(titleCol) ?: "Unknown title",
                        c.getString(artistCol) ?: "Unknown artist", ContentUris.withAppendedId(collection, id)))
                }
            }
        } catch (_: Exception) {}
        renderSongs()
    }

    private fun renderSongs() {
        songList.removeAllViews()
        if (songs.isEmpty()) {
            songList.addView(TextView(this).apply {
                text = "No music found. Add MP3 files and reopen the app."
                setTextColor(muted); setPadding(0, dp(12), 0, dp(12))
            })
            return
        }
        songs.forEachIndexed { index, song ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(panel, 14); isClickable = true; isFocusable = true
            }
            row.addView(TextView(this).apply {
                text = "♫"; textSize = 22f; setTextColor(blue); gravity = Gravity.CENTER
                background = rounded(Color.rgb(10, 43, 66), 10)
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            info.addView(TextView(this).apply {
                text = song.title; textSize = 14f; setTextColor(white); maxLines = 1
            })
            info.addView(TextView(this).apply {
                text = song.artist; textSize = 11f; setTextColor(muted); maxLines = 1
            })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            row.setOnClickListener { play(index) }
            songList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun play(index: Int) {
        if (index !in songs.indices) return
        releaseEffects()
        try { player?.release() } catch (_: Exception) {}
        player = null
        current = index
        try {
            player = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDataSource(this@MainActivity, songs[index].uri)
                setOnPreparedListener { mp ->
                    titleText.text = songs[index].title
                    artistText.text = songs[index].artist
                    seek.max = mp.duration
                    timeText.text = "00:00 / ${fmt(mp.duration)}"
                    initEffects()
                    mp.start()
                    playButton.text = "Ⅱ"
                    startTicker()
                }
                setOnCompletionListener { next() }
                prepareAsync()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Could not play this song", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlay() {
        val p = player
        if (p == null) {
            if (songs.isNotEmpty()) play(if (current >= 0) current else 0)
            return
        }
        try {
            if (p.isPlaying) { p.pause(); playButton.text = "▶" }
            else { p.start(); playButton.text = "Ⅱ"; startTicker() }
        } catch (_: Exception) {}
    }

    private fun next() {
        if (songs.isNotEmpty()) play(if (current + 1 >= songs.size) 0 else current + 1)
    }

    private fun previous() {
        val p = player
        val restart = try { p != null && p.currentPosition > 3000 } catch (_: Exception) { false }
        if (restart) {
            try { p?.seekTo(0); return } catch (_: Exception) {}
        }
        if (songs.isNotEmpty()) play(if (current <= 0) songs.lastIndex else current - 1)
    }

    private fun initEffects() {
        val p = player ?: return
        try { if (bass == null) bass = BassBoost(0, p.audioSessionId).apply { enabled = true } } catch (_: Exception) {}
        try { if (equalizer == null) equalizer = Equalizer(0, p.audioSessionId).apply { enabled = true } } catch (_: Exception) {}
        try {
            if (visualizer == null) visualizer = Visualizer(p.audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        val data = waveform ?: return
                        runOnUiThread {
                            for (i in 0 until visualBars.childCount) {
                                val child = visualBars.getChildAt(i)
                                val h = 8 + (data[i % data.size].toInt() and 0xFF) * 35 / 255
                                child.layoutParams = child.layoutParams.apply { height = dp(h) }
                            }
                        }
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (_: Exception) {}
    }

    private fun startTicker() {
        seek.post(object : Runnable {
            override fun run() {
                val p = player ?: return
                try {
                    if (!seeking && p.isPlaying) {
                        seek.progress = p.currentPosition
                        timeText.text = "${fmt(p.currentPosition)} / ${fmt(p.duration)}"
                    }
                    if (p.isPlaying) seek.postDelayed(this, 500)
                } catch (_: Exception) {}
            }
        })
    }

    private fun releaseEffects() {
        try { visualizer?.release() } catch (_: Exception) {}
        try { bass?.release() } catch (_: Exception) {}
        try { equalizer?.release() } catch (_: Exception) {}
        visualizer = null; bass = null; equalizer = null
    }

    private fun button(textLabel: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = textLabel; textSize = if (primary) 23f else 18f
        setTextColor(if (primary) Color.BLACK else blue)
        background = rounded(if (primary) blue else panel, 18)
        isAllCaps = false; setOnClickListener { action() }
    }

    private fun label(textLabel: String) = TextView(this).apply {
        text = textLabel; setTextColor(white); textSize = 13f
        typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(10), 0, dp(4))
    }

    private fun sectionTitle(textLabel: String) = TextView(this).apply {
        text = textLabel; textSize = 13f; letterSpacing = .08f
        setTextColor(blue); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(10))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(panel, 22)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), Color.rgb(30, 52, 73))
    }

    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun fmt(ms: Int): String = "%02d:%02d".format(ms / 60000, (ms / 1000) % 60)

    override fun onDestroy() {
        releaseEffects()
        try { player?.release() } catch (_: Exception) {}
        player = null
        super.onDestroy()
    }
}
