
package com.zaymbes.voicelab

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.AudioManager
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.widget.*
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sqrt

class MainActivity : Activity() {

    @Volatile private var running = false
    @Volatile private var depthValue = 55
    @Volatile private var gateValue = 35

    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var worker: Thread? = null

    private lateinit var status: TextView
    private lateinit var startButton: Button

    private val rate = 44100

    private val bufferSize by lazy {
        max(
            AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ),
            4096
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(15, 8, 27)
        window.navigationBarColor = Color.rgb(15, 8, 27)

        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 34, 28, 24)
            setBackgroundColor(Color.rgb(15, 8, 27))
        }

        fun label(
            text: String,
            size: Float,
            color: Int = Color.WHITE
        ) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
        }

        root.addView(
            label("ZAYMBES", 30f, Color.rgb(190, 130, 255))
        )

        root.addView(
            label("VOICE LAB  •  PHANTOM GOD", 14f, Color.LTGRAY)
        )

        root.addView(Space(this), LinearLayout.LayoutParams(1, 30))

        root.addView(
            label("PRESET", 13f, Color.rgb(170, 130, 220))
        )

        root.addView(label("PHANTOM GOD  /  v1", 22f))
        root.addView(label("Deep • synchronized • ominous", 14f, Color.LTGRAY))

        val info = label(
            "Live microphone monitor • noise gate active",
            13f,
            Color.rgb(190, 180, 205)
        )

        info.setPadding(0, 24, 0, 20)
        root.addView(info)

        startButton = Button(this).apply {
            text = "START MIC MONITOR"
            setOnClickListener { toggle() }
        }

        root.addView(
            startButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                58
            )
        )

        status = label(
            "Microphone stopped",
            14f,
            Color.rgb(190, 130, 255)
        )

        status.setPadding(0, 20, 0, 20)
        root.addView(status)

        root.addView(
            label("VOICE CONTROLS", 15f, Color.rgb(190, 130, 255))
        )

        addSlider(root, "Depth", 0, 100, 55)
        addSlider(root, "Phantom resonance", 0, 100, 25)
        addSlider(root, "Layer blend", 0, 100, 50)
        addSlider(root, "Noise Gate", 0, 100, 35)

        root.addView(
            label(
                "Headphones recommended • avoid speaker feedback",
                12f,
                Color.LTGRAY
            )
        )

        setContentView(root)
    }

    private fun addSlider(
        root: LinearLayout,
        name: String,
        min: Int,
        max: Int,
        initial: Int
    ) {
        val title = TextView(this).apply {
            text = "$name: $initial%"
            setTextColor(Color.WHITE)
        }

        root.addView(title)

        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
        }

        seek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    s: SeekBar?,
                    p: Int,
                    user: Boolean
                ) {
                    val value = p + min
                    title.text = "$name: $value%"

                    when (name) {
                        "Depth" -> {
                            depthValue = value
                            updatePlaybackRate()
                        }

                        "Noise Gate" -> {
                            gateValue = value
                        }
                    }
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}

                override fun onStopTrackingTouch(s: SeekBar?) {}
            }
        )

        root.addView(
            seek,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                48
            )
        )
    }

    private fun updatePlaybackRate() {
        try {
            val depth = depthValue / 100f
            val playbackRate = (rate * (1f - depth * 0.35f)).toInt()

            player?.playbackRate = playbackRate.coerceAtLeast(8000)
        } catch (_: Exception) {
        }
    }

    private fun toggle() {
        if (running) {
            stopAudio()
        } else {
            if (
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    10
                )
                return
            }

            startAudio()
        }
    }

    private fun startAudio() {
        try {
            val outBuffer = max(
                AudioTrack.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ),
                4096
            )

            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            player = AudioTrack(
                AudioManager.STREAM_MUSIC,
                rate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                outBuffer,
                AudioTrack.MODE_STREAM
            )

            if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException(
                    "Microphone initialization failed"
                )
            }

            if (player?.state != AudioTrack.STATE_INITIALIZED) {
                throw IllegalStateException(
                    "Audio output initialization failed"
                )
            }

            noiseSuppressor?.release()

            noiseSuppressor =
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(
                        recorder!!.audioSessionId
                    )?.apply {
                        enabled = true
                    }
                } else {
                    null
                }

            running = true

            recorder?.startRecording()
            player?.play()

            updatePlaybackRate()

            startButton.text = "STOP MIC MONITOR"
            status.text = "Mic active • Noise Gate running"

            worker = thread(name = "ZaymbesAudio") {
                processMicrophone()
            }

        } catch (e: Exception) {
            status.text = "Audio error: ${e.message}"
            stopAudio()
        }
    }

    private fun processMicrophone() {
        val data = ShortArray(bufferSize / 2)

        var envelope = 0f
        var gateGain = 1f

        while (running) {
            val count = recorder?.read(
                data,
                0,
                data.size
            ) ?: -1

            if (count <= 0 || !running) {
                continue
            }

            var sumSquares = 0.0

            for (i in 0 until count) {
                val sample = data[i].toDouble() / 32768.0
                sumSquares += sample * sample
            }

            val rms = sqrt(sumSquares / count).toFloat()

            envelope = envelope * 0.85f + rms * 0.15f

            val threshold =
                0.002f + (gateValue / 100f) * 0.035f

            val targetGain =
                if (envelope > threshold) {
                    1f
                } else {
                    0f
                }

            val attack = 0.35f
            val release = 0.04f

            gateGain +=
                if (targetGain > gateGain) {
                    (targetGain - gateGain) * attack
                } else {
                    (targetGain - gateGain) * release
                }

            for (i in 0 until count) {
                val sample = data[i] * gateGain

                data[i] = sample
                    .toInt()
                    .coerceIn(-32768, 32767)
                    .toShort()
            }

            player?.write(data, 0, count)
        }
    }

    private fun stopAudio() {
        running = false

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        try {
            player?.stop()
        } catch (_: Exception) {
        }

        try {
            worker?.join(300)
        } catch (_: InterruptedException) {
        }

        noiseSuppressor?.release()
        noiseSuppressor = null

        recorder?.release()
        player?.release()

        recorder = null
        player = null
        worker = null

        startButton.text = "START MIC MONITOR"
        status.text = "Microphone stopped"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            results
        )

        if (
            requestCode == 10 &&
            results.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startAudio()
        } else {
            status.text = "Microphone permission denied"
        }
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}
