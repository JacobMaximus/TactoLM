package com.tactolm.pipeline

import android.content.Context
import android.media.AudioRecord
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tactolm.haptics.LRADispatcher
import com.tactolm.haptics.TactonLibrary
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.audio.classifier.Classifications
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.scheduleAtFixedRate

class AudioRecognitionManager(
    private val context: Context,
    private val dispatcher: LRADispatcher
) {
    private val TAG = "AudioRecognitionMgr"

    // This model must be placed in app/src/main/assets/
    private val MODEL_FILE = "yamnet.tflite"

    private var classifier: AudioClassifier? = null
    private var tensorAudio: org.tensorflow.lite.support.audio.TensorAudio? = null
    private var audioRecord: AudioRecord? = null
    private var timerTask: TimerTask? = null

    // Minimum probability score to trigger on a doorbell detection
    // 0.2f = fairly sensitive; raise to 0.35f if false positives occur
    private val PROBABILITY_THRESHOLD = 0.2f
    private val CLASSIFICATION_INTERVAL_MS = 500L

    fun startListening() {
        try {
            // Initialize the Audio Classifier
            classifier = AudioClassifier.createFromFile(context, MODEL_FILE)
            
            // Create the AudioRecord and TensorAudio
            tensorAudio = classifier?.createInputTensorAudio()
            audioRecord = classifier?.createAudioRecord()

            // Start recording audio from the microphone
            audioRecord?.startRecording()
            Log.i(TAG, "Microphone started. Listening for alarms and doorbells...")

            // Run classification every 500ms
            timerTask = Timer().scheduleAtFixedRate(0, CLASSIFICATION_INTERVAL_MS) {
                classifyAudio()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Audio Recognition. Make sure yamnet.tflite is in the assets folder!", e)
        }
    }

    private fun classifyAudio() {
        val record = audioRecord ?: return
        val audio = tensorAudio ?: return
        val clf = classifier ?: return

        try {
            audio.load(record)

            // AI classification - ONLY doorbell sounds trigger a vibration
            val output: List<Classifications>? = clf.classify(audio)
            var doorbellDetected = false

            output?.forEach { classification ->
                if (!doorbellDetected) {
                    classification.categories.forEach { category ->
                        if (!doorbellDetected &&
                            category.score > PROBABILITY_THRESHOLD &&
                            isDoorbellSound(category.label.lowercase())
                        ) {
                            Log.i(TAG, "DOORBELL DETECTED: ${category.label} (Score: ${category.score})")
                            doorbellDetected = true
                        }
                    }
                }
            }

            if (doorbellDetected) {
                triggerVibrationAlert()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio classification", e)
        }
    }

    /**
     * Matches ONLY doorbell and bell ring sounds.
     * We deliberately exclude alarms, sirens, smoke detectors etc.
     * to keep this as a focused doorbell-only listener.
     */
    private fun isDoorbellSound(label: String): Boolean {
        val doorbellKeywords = listOf(
            "doorbell",
            "door bell",
            "bell ring",
            "chime",
            "ding dong"
        )
        return doorbellKeywords.any { keyword -> label.contains(keyword) }
    }

    private fun triggerVibrationAlert() {
        // Trigger on main thread — Custom "Ding-Dong" doorbell vibration
        Handler(Looper.getMainLooper()).post {
            dispatcher.dispatch(TactonLibrary.DOORBELL_CHIME)
        }
    }

    fun stopListening() {
        timerTask?.cancel()
        timerTask = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        classifier?.close()
        classifier = null
        Log.i(TAG, "Audio listening stopped.")
    }
}
