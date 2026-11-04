
package com.zaymbes.voicelab

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.content.Context

class MainActivity : Activity() {

    private var serviceActive = false

    private var depthValue = 55
    private var gateValue = 35
    private var resonanceValue = 25
    private var layerBlendValue = 50

    private lateinit var status: TextView
    private lateinit var startButton: Button

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

        val scroll = ScrollView(this).apply {
            addView(root)
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

        root.addView(
            Space(this),
            LinearLayout.LayoutParams(1, 30)
        )

        root.addView(
            label("PRESET", 13f, Color.rgb(170, 130, 220))
        )

        root.addView(
            label("PHANTOM GOD / v4", 22f)
        )

        root.addView(
            label(
                "Deep • Echoing • Ominous • Pitch Shifted",
                14f,
                Color.LTGRAY
            )
        )

        val info = label(
            "Foreground Audio Service • Phantom Effects",
            13f,
            Color.rgb(190, 180, 205)
        )

        info.setPadding(0, 24, 0, 20)
        root.addView(info)

        startButton = Button(this).apply {
            text = "START MIC MONITOR"
            setOnClickListener {
                toggleService()
            }
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
            label(
                "PHANTOM VOICE CONTROLS",
                15f,
                Color.rgb(190, 130, 255)
            )
        )

        addSlider(root, "Depth", 55)
        addSlider(root, "Phantom resonance", 25)
        addSlider(root, "Layer blend", 50)
        addSlider(root, "Noise Gate", 35)

        root.addView(
            label(
                "Real-Time Pitch Engine • Layered Echo",
                12f,
                Color.LTGRAY
            )
        )

        root.addView(
            label(
                "Resonance Feedback • Noise Suppression",
                12f,
                Color.LTGRAY
            )
        )

        root.addView(
            label(
                "Bluetooth headphones recommended",
                12f,
                Color.LTGRAY
            )
        )

        setContentView(scroll)
    }

    private fun addSlider(
        root: LinearLayout,
        name: String,
        initial: Int
    ) {

        val title = TextView(this).apply {
            text = "$name: $initial%"
            setTextColor(Color.WHITE)
        }

        root.addView(title)

        val seek = SeekBar(this).apply {
            max = 100
            progress = initial
        }

        seek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    title.text = "$name: $progress%"

                    when (name) {
                        "Depth" -> depthValue = progress

                        "Phantom resonance" ->
                            resonanceValue = progress

                        "Layer blend" ->
                            layerBlendValue = progress

                        "Noise Gate" ->
                            gateValue = progress
                    }

                    if (serviceActive && fromUser) {
                        sendAudioSettings()
                    }
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {}

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {}
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

    private fun toggleService() {

        if (serviceActive) {
            stopAudioService()
            return
        }

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                10
            )

            return
        }

        startAudioService()
    }

    private fun startAudioService() {

        try {

            val intent = Intent(
                this,
                AudioService::class.java
            ).apply {
                action = AudioService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            serviceActive = true

            sendAudioSettings()

            startButton.text = "STOP MIC MONITOR"
            status.text = "Phantom God V4 starting..."

        } catch (e: Exception) {

            serviceActive = false

            status.text = "Service error: ${e.message}"
            startButton.text = "START MIC MONITOR"
        }
    }

    private fun sendAudioSettings() {

        if (!serviceActive) return

        val intent = Intent(
            this,
            AudioService::class.java
        ).apply {

            action = AudioService.ACTION_UPDATE

            putExtra(
                AudioService.EXTRA_DEPTH,
                depthValue
            )

            putExtra(
                AudioService.EXTRA_GATE,
                gateValue
            )

            putExtra(
                AudioService.EXTRA_RESONANCE,
                resonanceValue
            )

            putExtra(
                AudioService.EXTRA_BLEND,
                layerBlendValue
            )
        }

        try {
            startService(intent)
        } catch (e: Exception) {
            android.util.Log.e(
                "ZaymbesVoiceLab",
                "Settings update failed",
                e
            )
        }
    }

    private fun stopAudioService() {

        val intent = Intent(
            this,
            AudioService::class.java
        ).apply {
            action = AudioService.ACTION_STOP
        }

        try {
            startService(intent)
        } catch (e: Exception) {
            android.util.Log.e(
                "ZaymbesVoiceLab",
                "Service stop failed",
                e
            )
        }

        serviceActive = false

        startButton.text = "START MIC MONITOR"
        status.text = "Microphone stopped"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == 10 &&
            grantResults.firstOrNull() ==
            PackageManager.PERMISSION_GRANTED
        ) {

            startAudioService()

        } else {

            status.text = "Microphone permission denied"
        }
    }
}
