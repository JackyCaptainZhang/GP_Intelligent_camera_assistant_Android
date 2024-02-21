package com.example.gp_intelligent_camera_assistant

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// This class will call the google speech recognition API
class VoiceRecognizer(private val activity: Activity) {
    companion object {
        const val REQUEST_CODE_SPEECH_INPUT = 1000
    }

    fun askSpeechInput() {
        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_CODE_SPEECH_INPUT)
        } else {
            startVoiceRecognitionActivity()
        }
    }

    private fun startVoiceRecognitionActivity() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(activity)) {
            Toast.makeText(activity, "Speech recognition is not available on this device.", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the item you want to find: ")
            }
            activity.startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
        }
    }
}
