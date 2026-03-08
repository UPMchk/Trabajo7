package com.example.vozprototype

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etUserId: EditText
    private val prefs by lazy { getSharedPreferences("voz_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUserId = findViewById(R.id.etUserId)
        val btnSaveUserId = findViewById<Button>(R.id.btnSaveUserId)
        val btnExerciseA = findViewById<Button>(R.id.btnExerciseA)
        val btnExerciseDDK = findViewById<Button>(R.id.btnExerciseDDK)
        val btnExercisePhrase = findViewById<Button>(R.id.btnExercisePhrase)

        val currentUserId = prefs.getString("user_id", null) ?: buildDefaultUserId()
        etUserId.setText(currentUserId)
        saveUserId(currentUserId)

        btnSaveUserId.setOnClickListener {
            val newId = etUserId.text.toString().trim()
            if (newId.isEmpty()) {
                Toast.makeText(this, "User ID cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                saveUserId(newId)
                Toast.makeText(this, "User ID saved", Toast.LENGTH_SHORT).show()
            }
        }

        btnExerciseA.setOnClickListener {
            openExercise(
                exerciseCode = "A",
                exerciseTitle = "Sustained vowel /a/",
                instructions = "Take a normal breath and pronounce the vowel /a/ continuously for a few seconds. Keep a steady volume. Maximum duration: 10 seconds."
            )
        }

        btnExerciseDDK.setOnClickListener {
            openExercise(
                exerciseCode = "DDK",
                exerciseTitle = "Diadochokinetic /pa-ta-ka/",
                instructions = "Repeat /pa-ta-ka/ several times at a comfortable rhythm and volume. Maximum duration: 10 seconds."
            )
        }

        btnExercisePhrase.setOnClickListener {
            openExercise(
                exerciseCode = "PHRASE",
                exerciseTitle = "Fixed phrase",
                instructions = "Pronounce the predefined phrase clearly and at a normal volume. Maximum duration: 10 seconds."
            )
        }
    }

    private fun buildDefaultUserId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrBlank()) {
            "USR_${androidId.takeLast(6).uppercase()}"
        } else {
            "USR_001"
        }
    }

    private fun saveUserId(userId: String) {
        prefs.edit().putString("user_id", userId).apply()
    }

    private fun openExercise(
        exerciseCode: String,
        exerciseTitle: String,
        instructions: String
    ) {
        val userId = etUserId.text.toString().trim()
        if (userId.isEmpty()) {
            Toast.makeText(this, "Please enter a user ID first", Toast.LENGTH_SHORT).show()
            return
        }

        saveUserId(userId)

        val intent = Intent(this, ExerciseActivity::class.java).apply {
            putExtra("exercise_code", exerciseCode)
            putExtra("exercise_title", exerciseTitle)
            putExtra("instructions", instructions)
            putExtra("user_id", userId)
        }
        startActivity(intent)
    }
}