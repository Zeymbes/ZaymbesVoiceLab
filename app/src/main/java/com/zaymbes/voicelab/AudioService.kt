
package com.zaymbes.voicelab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.IBinder
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class AudioService : Service() {

    companion object {
        const val ACTION_START =
            "com.zaymbes.voicelab.START_AUDIO"

        const val ACTION_STOP =
            "com.zaymbes.voicelab.STOP_AUDIO"

        const val ACTION_UPDATE =
            "com.zaymbes.voicelab.UPDATE_AUDIO"

        const val EXTRA_DEPTH = "depth"
        const val EXTRA_GATE = "gate"
        const val EXTRA_RESONANCE = "resonance"
        const val EXTRA_BLEND = "blend"

        private const val CHANNEL_ID =
            "zaymbes_audio_channel"

        private const val NOTIFICATION_ID = 1001
    }

    @Volatile private var running = false

    @Volatile private var depthValue = 55
    @Volatile private var gateValue = 35
    @Volatile private var resonanceValue = 25
    @Volatile private var layerBlendValue = 50

    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var worker: Thread? = null

    private lateinit var audioManager: AudioManager

    private val rate = 44100

    private val pitchBufferSize = 8192
    private val pitchWindow = 4096
    private val pitchMinimumDelay = 2

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

    override fun onCreate() {
        super.onCreate()

        audioManager =
            getSystemService(Context.AUDIO_SERVICE)
                as AudioManager

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                if (!running) {
                    startForegroundNotification()
                    startAudioEngine()
                }
            }

            ACTION_UPDATE -> {
                depthValue = intent.getIntExtra(
                    EXTRA_DEPTH, depthValue
                )

                gateValue = intent.getIntExtra(
                    EXTRA_GATE, gateValue
                )

                resonanceValue = intent.getIntExtra(
                    EXTRA_RESONANCE, resonanceValue
                )

                layerBlendValue = intent.getIntExtra(
                    EXTRA_BLEND, layerBlendValue
                )
            }

            ACTION_STOP -> {
                stopAudioEngine()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {

        val notification =
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Zaymbes Voice Lab")
                .setContentText(
                    "Phantom God V4 audio engine is active"
                )
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zaymbes Audio Service",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(channel)
        }
    }

    private fun startAudioEngine() {

        try {

            if (
                checkSelfPermission(
                    android.Manifest.permission.RECORD_AUDIO
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException(
                    "Microphone permission is missing"
                )
            }

            audioManager.mode =
                AudioManager.MODE_IN_COMMUNICATION

            if (
                Build.VERSION.SDK_INT < 31 ||
                checkSelfPermission(
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()

                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
            }

            val outBuffer = max(
                AudioTrack.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ),
                4096
            )

            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            player = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                rate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                outBuffer,
                AudioTrack.MODE_STREAM
            )

            if (
                recorder?.state != AudioRecord.STATE_INITIALIZED ||
                player?.state != AudioTrack.STATE_INITIALIZED
            ) {
                throw IllegalStateException(
                    "Audio initialization failed"
                )
            }

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

            recorder!!.startRecording()
            player!!.play()

            running = true

            worker = thread(
                name = "ZaymbesAudioEngine"
            ) {
                processMicrophone()
            }

        } catch (e: Exception) {

            android.util.Log.e(
                "ZaymbesAudio",
                "Audio startup failed",
                e
            )

            stopAudioEngine()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun readPitchBuffer(
        buffer: FloatArray,
        position: Float
    ): Float {

        val size = buffer.size
        var pos = position

        while (pos < 0f) pos += size
        while (pos >= size) pos -= size

        val index = pos.toInt()
        val next = (index + 1) % size
        val fraction = pos - index

        return buffer[index] * (1f - fraction) +
            buffer[next] * fraction
    }

    private fun pitchShift(
        input: Float,
        buffer: FloatArray,
        writePosition: Int,
        phase: Float,
        ratio: Float
    ): Float {

        buffer[writePosition] = input

        val safeRatio = ratio.coerceIn(0.65f, 1f)

        val delayRange =
            (1f - safeRatio) * pitchWindow

        val phaseOne = phase

        val phaseTwo =
            if (phase + 0.5f >= 1f) {
                phase - 0.5f
            } else {
                phase + 0.5f
            }

        val delayOne =
            pitchMinimumDelay + phaseOne * delayRange

        val delayTwo =
            pitchMinimumDelay + phaseTwo * delayRange

        val readOne =
            writePosition.toFloat() - delayOne

        val readTwo =
            writePosition.toFloat() - delayTwo

        val weightOne =
            sin(Math.PI.toFloat() * phaseOne).let {
                it * it
            }

        val weightTwo =
            sin(Math.PI.toFloat() * phaseTwo).let {
                it * it
            }

        val sampleOne =
            readPitchBuffer(buffer, readOne)

        val sampleTwo =
            readPitchBuffer(buffer, readTwo)

        val totalWeight = weightOne + weightTwo

        return if (totalWeight > 0f) {
            (
                sampleOne * weightOne +
                sampleTwo * weightTwo
            ) / totalWeight
        } else {
            sampleOne
        }
    }

    private fun processMicrophone() {

        val data = ShortArray(bufferSize / 2)

        var envelope = 0f
        var gateGain = 1f

        val pitchBuffer =
            FloatArray(pitchBufferSize)

        var pitchWritePosition = 0
        var pitchPhase = 0f

        val delayOne = FloatArray(6000)
        val delayTwo = FloatArray(12000)

        var positionOne = 0
        var positionTwo = 0

        while (running) {

            val count = try {
                recorder?.read(
                    data, 0, data.size
                ) ?: -1
            } catch (_: Exception) {
                -1
            }

            if (count <= 0) {
                if (!running) break
                continue
            }

            var sumSquares = 0.0

            for (i in 0 until count) {
                val sample =
                    data[i].toDouble() / 32768.0

                sumSquares += sample * sample
            }

            val rms =
                sqrt(sumSquares / count).toFloat()

            envelope =
                envelope * 0.85f + rms * 0.15f

            val gate = gateValue / 100f

            val threshold =
                0.002f + gate * 0.035f

            val targetGain =
                if (envelope > threshold) 1f else 0f

            gateGain +=
                if (targetGain > gateGain) {
                    (targetGain - gateGain) * 0.35f
                } else {
                    (targetGain - gateGain) * 0.04f
                }

            val depth = depthValue / 100f
            val resonance = resonanceValue / 100f
            val blend = layerBlendValue / 100f

            val pitchRatio =
                1f - depth * 0.35f

            val feedback =
                resonance * 0.58f

            val wetMix =
                blend * 0.65f

            for (i in 0 until count) {

                val dry =
                    (data[i] / 32768f) * gateGain

                val pitched = pitchShift(
                    dry,
                    pitchBuffer,
                    pitchWritePosition,
                    pitchPhase,
                    pitchRatio
                )

                pitchWritePosition++

                if (
                    pitchWritePosition >= pitchBufferSize
                ) {
                    pitchWritePosition = 0
                }

                pitchPhase += 1f / pitchWindow

                if (pitchPhase >= 1f) {
                    pitchPhase -= 1f
                }

                val echoOne =
                    delayOne[positionOne]

                val echoTwo =
                    delayTwo[positionTwo]

                val combinedEcho =
                    (echoOne + echoTwo) * 0.5f

                val output =
                    pitched * (1f - wetMix) +
                    combinedEcho * wetMix

                delayOne[positionOne] =
                    pitched +
                    echoTwo * feedback * 0.25f

                delayTwo[positionTwo] =
                    pitched +
                    echoOne * feedback * 0.25f

                positionOne++
                positionTwo++

                if (positionOne >= delayOne.size) {
                    positionOne = 0
                }

                if (positionTwo >= delayTwo.size) {
                    positionTwo = 0
                }

                data[i] =
                    (output * 32767f)
                        .toInt()
                        .coerceIn(-32768, 32767)
                        .toShort()
            }

            try {
                player?.write(data, 0, count)
            } catch (e: Exception) {
                android.util.Log.e(
                    "ZaymbesAudio",
                    "Audio output failed",
                    e
                )
                break
            }
        }
    }

    private fun stopAudioEngine() {

        running = false

        try {
            recorder?.stop()
        } catch (_: Exception) {}

        try {
            player?.stop()
        } catch (_: Exception) {}

        try {
            worker?.join(500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}

        noiseSuppressor = null

        try {
            recorder?.release()
        } catch (_: Exception) {}

        try {
            player?.release()
        } catch (_: Exception) {}

        recorder = null
        player = null
        worker = null

        try {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()

            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false

            audioManager.mode = AudioManager.MODE_NORMAL

        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        stopAudioEngine()
        super.onDestroy()
    }
}
