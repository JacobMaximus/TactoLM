package com.tactolm

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HapticSequencePlayer — drives sequential haptic playback for TactoActivity.
 *
 * Usage:
 *   val player = HapticSequencePlayer(vibrator, lifecycleScope)
 *   player.startProcessing()         // during API call
 *   player.stopProcessing()          // API responded
 *   player.play(items) { item ->     // fires on UI thread, simultaneously with haptic
 *       addCardToScreen(item)
 *   }
 *   player.replay()                  // replay last sequence without retaking photo
 */
class HapticSequencePlayer(
    private val vibrator: Vibrator,
    private val scope: CoroutineScope
) {

    private companion object {
        const val TAG = "HapticSequencePlayer"
        const val ITEM_GAP_MS = 1000L   // wait between items
    }

    /** Most recently played list, stored for replay(). */
    @Volatile
    private var lastItems: List<SceneItem> = emptyList()

    /** Callback stored for replay(). */
    @Volatile
    private var lastOnItemReady: ((SceneItem) -> Unit)? = null

    // ── Processing loop ───────────────────────────────────────────────────────

    /**
     * Start the looping processing vibration (API waiting indicator).
     * Call [stopProcessing] to cancel it.
     */
    fun startProcessing() {
        if (!vibrator.hasVibrator()) return
        try {
            vibrator.vibrate(TactonLibrary.processingEffect)
        } catch (e: Exception) {
            Log.e(TAG, "startProcessing failed: ${e.message}")
        }
    }

    /**
     * Cancel the processing loop vibration immediately.
     */
    fun stopProcessing() {
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "stopProcessing cancel failed: ${e.message}")
        }
    }

    // ── Item sequence playback ────────────────────────────────────────────────

    /**
     * Play [items] in order.  For each item:
     *   1. Fire its haptic.
     *   2. Call [onItemReady] on the main thread (so the Activity can animate the card).
     *   3. Wait [ITEM_GAP_MS] before the next item.
     *
     * Stores parameters for [replay].
     * This is a suspend function — call from a coroutine.
     */
    suspend fun play(items: List<SceneItem>, onItemReady: (SceneItem) -> Unit) {
        lastItems = items
        lastOnItemReady = onItemReady
        playSequence(items, onItemReady)
    }

    /**
     * Replay the last sequence without re-running the camera / API pipeline.
     * Safe to call from any thread.
     */
    fun replay() {
        val items = lastItems
        val callback = lastOnItemReady ?: return
        if (items.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            playSequence(items, callback)
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun playSequence(
        items: List<SceneItem>,
        onItemReady: (SceneItem) -> Unit
    ) = withContext(Dispatchers.IO) {
        for (item in items) {
            // Fire haptic
            val effect = TactonLibrary.getEffect(item.category)
            if (effect != null && vibrator.hasVibrator()) {
                try {
                    vibrator.cancel()
                    vibrator.vibrate(effect)
                } catch (e: Exception) {
                    Log.e(TAG, "vibrate failed for ${item.category}: ${e.message}")
                }
            }

            // Notify UI on the main thread immediately (same moment as haptic)
            withContext(Dispatchers.Main) {
                onItemReady(item)
            }

            delay(ITEM_GAP_MS)
        }
    }

    // ── Error haptic helper ───────────────────────────────────────────────────

    /**
     * Fire the error condition: three HAZARD haptics with 500ms gap between each.
     * Does NOT replay (error is not stored as a sequence).
     * Call from a coroutine.
     */
    suspend fun playError() = withContext(Dispatchers.IO) {
        val hazardEffect = TactonLibrary.getEffect("HAZARD") ?: return@withContext
        repeat(3) { idx ->
            if (vibrator.hasVibrator()) {
                try {
                    vibrator.cancel()
                    vibrator.vibrate(hazardEffect)
                } catch (e: Exception) {
                    Log.e(TAG, "error haptic $idx failed: ${e.message}")
                }
            }
            if (idx < 2) delay(500L)
        }
    }

    // ── Confirmation tap ──────────────────────────────────────────────────────

    /**
     * Fire the two-pulse confirmation tap that plays when the user presses SCAN.
     */
    fun playConfirmation() {
        if (!vibrator.hasVibrator()) return
        try {
            vibrator.vibrate(TactonLibrary.confirmationEffect)
        } catch (e: Exception) {
            Log.e(TAG, "confirmation failed: ${e.message}")
        }
    }
}
