package com.bassbeats.music

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.res.ColorStateList

data class Song(val id: Long, val title: String, val artist: String, val uri: Uri)

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
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var playButton: Button
    private lateinit var seek: SeekBar
    private lateinit var timeText: TextView
    private lateinit var list: LinearLayout
    private lateinit var visualBars: LinearLayout
    private var seeking = false

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 100 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) scanSongs()
        else Toast.makeText(this, "Audio permission is needed to find your songs", Toast.LENGTH_LONG).show()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(24)); setBackgroundColor(bg) }
        scroll.addView(root); setContentView(scroll)
        root.addView(TextView(this).apply { text = "BASS BEATS"; textSize = 24f; setTextColor(blue); typeface = Typeface.create("sans-serif-black", Typeface.BOLD); letterSpacing = .12f })
        root.addView(TextView(this).apply { text = "ELECTRIC BLUE  •  OFFLINE PLAYER"; textSize = 10f; letterSpacing = .16f; setTextColor(muted) })
        root.addView(space(18))
        val playerCard = card(); root.addView(playerCard, matchWrap())
        playerCard.addView(TextView(this).apply {
            text = "♫"; textSize = 64f; gravity = Gravity.CENTER; setTextColor(blue)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(12,44,72), Color.rgb(10,17,30))).apply { cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.rgb(0,90,140)) }
        }, LinearLayout.LayoutParams(-1, dp(160)))
        titleText = TextView(this).apply { text = "Choose a song"; textSize = 21f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; maxLines = 1 }
        playerCard.addView(titleText, matchWrap())
        artistText = TextView(this).apply { text = "Your local music library"; textSize = 13f; setTextColor(muted); gravity = Gravity.CENTER; maxLines = 1 }
        playerCard.addView(artistText, matchWrap())
        visualBars = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(8)) }
        repeat(28) { i ->
            val bar = View(this).apply { background = rounded(blue, 8f) }
            visualBars.addView(bar, LinearLayout.LayoutParams(dp(4), dp(10 + ((i * 7) % 25))).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        playerCard.addView(visualBars, matchWrap())
        seek = SeekBar(this).apply { progressTintList = ColorStateList.valueOf(blue); thumbTintList = ColorStateList.valueOf(blue); progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(50,65,82)) }
        playerCard.addView(seek, matchWrap())
        timeText = TextView(this).apply { text = "00:00  /  00:00"; textSize = 11f; setTextColor(muted); gravity = Gravity.CENTER }
        playerCard.addView(timeText, matchWrap())
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        controls.addView(button("⏮", false) { previous() }, LinearLayout.LayoutParams(dp(58), dp(52)))
        playButton = button("▶", true) { togglePlay() }
        controls.addView(playButton, LinearLayout.LayoutParams(dp(76), dp(60)).apply { marginStart = dp(14); marginEnd = dp(14) })
        controls.addView(button("⏭", false) { next() }, LinearLayout.LayoutParams(dp(58), dp(52)))
        playerCard.addView(controls, matchWrap())
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) timeText.text = "${fmt(progress)}  /  ${fmt(player?.duration ?: 0)}" }
            override fun onStartTrackingTouch(s: SeekBar?) { seeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) { player?.seekTo(s?.progress ?: 0); seeking = false }
        })
        root.addView(space(18)); root.addView(sectionTitle("YOUR MUSIC"))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(list, matchWrap())
        root.addView(space(18)); root.addView(sectionTitle("SOUND LAB  •  EFFECTS"))
        val effectsCard = card(); root.addView(effectsCard, matchWrap())
        effectsCard.addView(TextView(this).apply { text = "BASS BOOST"; setTextColor(white); textSize = 13f; typeface = Typeface.DEFAULT_BOLD }, matchWrap())
        val bassSeek = SeekBar(this).apply { max = 1000; progress = 350; progressTintList = ColorStateList.valueOf(blue); thumbTintList = ColorStateList.valueOf(blue) }
        effectsCard.addView(bassSeek, matchWrap())
        bassSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { if (user) try { if (bass == null) initEffects(); bass?.setStrength(p.toShort()) } catch (_: Exception) {} }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        effectsCard.addView(TextView(this).apply { text = "5-BAND EQUALIZER"; setTextColor(white); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(10), 0, dp(4)) }, matchWrap())
        val eqArea = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }; effectsCard.addView(eqArea, matchWrap())
        listOf("60", "230", "910", "3.6k", "14k").forEachIndexed { index, label ->
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, dp(145), 1f) }
            val slider = SeekBar(this).apply { max = 100; progress = 50; rotation = -90f; progressTintList = ColorStateList.valueOf(blue); thumbTintList = ColorStateList.valueOf(blue) }
            col.addView(slider, LinearLayout.LayoutParams(dp(130), dp(42)))
            col.addView(TextView(this).apply { text = label; textSize = 10f; gravity = Gravity.CENTER; setTextColor(muted) }, matchWrap())
            eqArea.addView(col)
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    if (user) try { if (equalizer == null) initEffects(); val eq = equalizer ?: return
                        if (index < eq.numberOfBands) { val r = eq.bandLevelRange; eq.setBandLevel(index.toShort(), (r[0] + (r[1] - r[0]) * p / 100).toShort()) }
                    } catch (_: Exception) {}
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        effectsCard.addView(TextView(this).apply { text = "Presets"; setTextColor(muted); textSize = 12f; setPadding(0, dp(10), 0, dp(4)) }, matchWrap())
        val presets = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Normal", "Bass Boost", "Rock", "Pop", "Classical")) }
        effectsCard.addView(presets, matchWrap())
        presets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                try { if (equalizer == null) initEffects(); val eq = equalizer ?: return
                    val preset = when (position) { 1 -> 1; 2 -> 2; 3 -> 3; 4 -> 4; else -> 0 }
                    if (preset < eq.numberOfPresets) eq.usePreset(preset.toShort())
                } catch (_: Exception) {}
            }
        }
    }

    private fun scanSongs() {
        songs.clear()
        val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        try {
            contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c: Cursor ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                while (c.moveToNext()) { val id = c.getLong(idCol); songs.add(Song(id, c.getString(titleCol) ?: "Unknown title", c.getString(artistCol) ?: "Unknown artist", ContentUris.withAppendedId(collection, id))) }
            }
        } catch (_: Exception) {}
        renderSongs()
    }

    private fun renderSongs() {
        list.removeAllViews()
        if (songs.isEmpty()) { list.addView(TextView(this).apply { text = "No music found. Add MP3 files to your phone and reopen."; setTextColor(muted); setPadding(0, dp(12), 0, dp(12)) }); return }
        songs.forEachIndexed { index, song ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(panel, 14f); isClickable = true; isFocusable = true }
            row.addView(TextView(this).apply { text = "♫"; textSize = 22f; setTextColor(blue); gravity = Gravity.CENTER; background = rounded(Color.rgb(10,43,66), 10f) }, LinearLayout.LayoutParams(dp(42), dp(42)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            info.addView(TextView(this).apply { text = song.title; textSize = 14f; setTextColor(white); maxLines = 1 })
            info.addView(TextView(this).apply { text = song.artist; textSize = 11f; setTextColor(muted); maxLines = 1 })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); row.setOnClickListener { play(index) }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun play(index: Int) {
        if (index !in songs.indices) return
        releasePlayer(); current = index
        try {
            player = MediaPlayer().apply {
                setAudioStreamType(AudioManager.STREAM_MUSIC); setDataSource(this@MainActivity, songs[index].uri)
                setOnPreparedListener { it.start(); titleText.text = songs[index].title; artistText.text = songs[index].artist; playButton.text = "Ⅱ"; seek.max = it.duration; timeText.text = "00:00  /  ${fmt(it.duration)}"; initEffects(); startTicker() }
                setOnCompletionListener { next() }; prepareAsync()
            }
        } catch (_: Exception) { Toast.makeText(this, "Could not play this song", Toast.LENGTH_SHORT).show() }
    }
    private fun togglePlay() {
        val p = player
        if (p == null) { if (songs.isNotEmpty()) play(if (current >= 0) current else 0); return }
        try { if (p.isPlaying) { p.pause(); playButton.text = "▶" } else { p.start(); playButton.text = "Ⅱ"; startTicker() } } catch (_: Exception) {}
    }
    private fun next() { if (songs.isNotEmpty()) play(if (current + 1 >= songs.size) 0 else current + 1) }
    private fun previous() {
        val p = player
        if (p != null && (try { p.currentPosition > 3000 } catch (_: Exception) { false })) {
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
                        waveform ?: return
                        runOnUiThread { for (i in 0 until visualBars.childCount) { val child = visualBars.getChildAt(i); val h = 8 + (waveform[i % waveform.size].toInt() and 0xFF) * 35 / 255; child.layoutParams = child.layoutParams.apply { height = dp(h) } } }
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false); enabled = true
            }
        } catch (_: Exception) {}
    }
    private fun startTicker() {
        seek.post(object : Runnable {
            override fun run() { val p = player ?: return
                try { if (!seeking && p.isPlaying) { seek.progress = p.currentPosition; timeText.text = "${fmt(p.currentPosition)}  /  ${fmt(p.duration)}" }; if (p.isPlaying) seek.postDelayed(this, 500) } catch (_: Exception) {}
            }
        })
    }
    private fun releasePlayer() {
        try { visualizer?.release() } catch (_: Exception) {}; try { bass?.release() } catch (_: Exception) {}; try { equalizer?.release() } catch (_: Exception) {}
        visualizer = null; bass = null; equalizer = null; try { player?.release() } catch (_: Exception) {}; player = null
    }
    private fun button(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label; textSize = if (primary) 23f else 18f; setTextColor(if (primary) Color.BLACK else blue); background = rounded(if (primary) blue else panel, 18f); isAllCaps = false; setOnClickListener { action() }
    }
    private fun sectionTitle(s: String) = TextView(this).apply { text = s; textSize = 13f; letterSpacing = .08f; setTextColor(blue); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(10)) }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); background = rounded(panel, 22f) }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat(); setStroke(dp(1), Color.rgb(30, 52, 73)) }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun fmt(ms: Int): String = "%02d:%02d".format(ms / 60000, (ms / 1000) % 60)
    override fun onDestroy() { releasePlayer(); super.onDestroy() }
}
