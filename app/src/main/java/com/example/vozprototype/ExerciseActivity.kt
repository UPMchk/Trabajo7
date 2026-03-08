package com.example.vozprototype

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExerciseActivity : AppCompatActivity() {

    private lateinit var tvExerciseTitle: TextView
    private lateinit var tvUserId: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvScore: TextView
    private lateinit var pbVolume: ProgressBar
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    private val meterHandler = Handler(Looper.getMainLooper())

    private var recordingStartTime = 0L
    private var observedMaxAmplitude = 0

    private lateinit var exerciseCode: String
    private lateinit var exerciseTitle: String
    private lateinit var instructions: String
    private lateinit var userId: String

    companion object {
        private const val MAX_DURATION_MS = 10_000L
        private const val LOW_THRESHOLD = 2000
        private const val CLIP_THRESHOLD = 30000
    }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            tvStatus.text = "Microphone permission granted. Ready."
            btnStart.isEnabled = true
        } else {
            tvStatus.text = "Microphone permission denied."
            btnStart.isEnabled = false
        }
    }

    private val meterRunnable = object : Runnable {
        override fun run() {
            val amplitude = recorder?.maxAmplitude ?: 0

            if (amplitude > observedMaxAmplitude) {
                observedMaxAmplitude = amplitude
            }

            pbVolume.progress = amplitude
            tvVolume.text = "Current level: $amplitude / 32767"

            val elapsed = System.currentTimeMillis() - recordingStartTime
            val seconds = elapsed / 1000f
            tvTimer.text = String.format(Locale.getDefault(), "Time: %.1f / 10.0 s", seconds)

            if (elapsed >= MAX_DURATION_MS) {
                tvStatus.text = "Maximum time reached. Stopping recording..."
                stopRecording()
            } else {
                meterHandler.postDelayed(this, 200)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise)

        tvExerciseTitle = findViewById(R.id.tvExerciseTitle)
        tvUserId = findViewById(R.id.tvUserId)
        tvInstructions = findViewById(R.id.tvInstructions)
        tvTimer = findViewById(R.id.tvTimer)
        tvVolume = findViewById(R.id.tvVolume)
        tvStatus = findViewById(R.id.tvStatus)
        tvScore = findViewById(R.id.tvScore)
        pbVolume = findViewById(R.id.pbVolume)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        exerciseCode = intent.getStringExtra("exercise_code") ?: "UNKNOWN"
        exerciseTitle = intent.getStringExtra("exercise_title") ?: "Exercise"
        instructions = intent.getStringExtra("instructions") ?: ""
        userId = intent.getStringExtra("user_id") ?: "USR_001"

        tvExerciseTitle.text = exerciseTitle
        tvUserId.text = "User: $userId"
        tvInstructions.text = instructions
        tvScore.text = "Result: -"

        ensureMicPermission()

        btnStart.setOnClickListener {
            startRecording()
        }

        btnStop.setOnClickListener {
            stopRecording()
        }
    }

    private fun ensureMicPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            tvStatus.text = "Ready to record"
            btnStart.isEnabled = true
        } else {
            btnStart.isEnabled = false
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        val recordingsDir = File(filesDir, "recordings")
        recordingsDir.mkdirs()

        val safeUserId = userId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${safeUserId}_${timestamp}_${exerciseCode}.m4a"

        outputFile = File(recordingsDir, fileName)
        observedMaxAmplitude = 0
        recordingStartTime = System.currentTimeMillis()

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
            start()
        }

        pbVolume.progress = 0
        tvVolume.text = "Current level: 0 / 32767"
        tvTimer.text = "Time: 0.0 / 10.0 s"
        tvScore.text = "Result: -"
        tvStatus.text = "Recording..."

        btnStart.isEnabled = false
        btnStop.isEnabled = true

        meterHandler.post(meterRunnable)
    }

    private fun stopRecording() {
        if (recorder == null) return

        meterHandler.removeCallbacks(meterRunnable)

        try {
            recorder?.stop()
        } catch (e: Exception) {
            Toast.makeText(this, "Recording stop error", Toast.LENGTH_SHORT).show()
        } finally {
            recorder?.release()
            recorder = null
        }

        btnStart.isEnabled = true
        btnStop.isEnabled = false

        val file = outputFile
        if (file == null || !file.exists()) {
            tvStatus.text = "Recording file not found."
            return
        }

        val durationMs = System.currentTimeMillis() - recordingStartTime
        val tooShort = durationMs < 1000
        val tooLow = observedMaxAmplitude < LOW_THRESHOLD
        val maybeClipped = observedMaxAmplitude > CLIP_THRESHOLD

        if (tooShort) {
            file.delete()
            tvStatus.text = "Recording too short. Please repeat."
            tvScore.text = "Result: -"
            return
        }

        if (tooLow) {
            file.delete()
            tvStatus.text = "Sound level too low. Please repeat the recording."
            tvScore.text = "Result: -"
            return
        }

        if (maybeClipped) {
            file.delete()
            tvStatus.text = "Audio may be saturated. Please repeat the recording."
            tvScore.text = "Result: -"
            return
        }

        val result = analyzeRecording(file, exerciseCode)

        tvStatus.text = "Recording saved:\n${file.name}"
        tvScore.text = "Result: $result / 32"
    }

    private fun analyzeRecording(audioFile: File, exerciseCode: String): Int {
        return (0..32).random()
    }

    override fun onDestroy() {
        super.onDestroy()
        meterHandler.removeCallbacks(meterRunnable)
        recorder?.release()
        recorder = null
    }
}