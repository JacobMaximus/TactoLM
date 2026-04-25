package com.tactolm.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * LRADispatcher — controls the Linear Resonant Actuator via Android's
 * [VibrationEffect.createWaveform] API (API 26+).
 *
 * MIUI/HyperOS PREEMPTION NOTE:
 * On Poco X7 Pro / MIUI / HyperOS, calling Vibrator.cancel() causes the
 * hardware driver to enforce a 15–20ms silence before it accepts a new
 * waveform. If a new effect is dispatched immediately after cancel(), it
 * is silently dropped. The [MIUI_CANCEL_SLEEP_MS] sleep is not optional.
 *
 * Usage:
 *   val dispatcher = LRADispatcher(context)
 *   dispatcher.dispatch(TactonLibrary.PULSE_BURST)
 *   dispatcher.dispatch(TactonLibrary.HEALTH_RAMP, intensityModifier = 1.2f)
 *   dispatcher.cancel()
 */
class LRADispatcher(context: Context) {

    companion object {
        private const val TAG = "LRADispatcher"

        /**
         * Mandatory sleep after Vibrator.cancel() on MIUI/HyperOS.
         * Without this, the next waveform is silently dropped by the driver.
         */
        private const val MIUI_CANCEL_SLEEP_MS = 15L
    }

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /** Currently playing tacton ID — used to gate re-dispatch logic. */
    @Volatile
    private var currentTactonId: String? = null

    /**
     * Dispatch a tacton, preempting anything currently playing.
     *
     * @param tacton           The [Tacton] to play.
     * @param intensityModifier Scale amplitudes (clamped to 0.7–1.3). Applied
     *                          per-segment, then clamped to [0, 255].
     */
    fun dispatch(tacton: Tacton, intensityModifier: Float = 1.0f) {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "Device has no vibrator — skipping dispatch")
            return
        }

        val clampedModifier = intensityModifier.coerceIn(0.7f, 1.3f)

        // Cancel current waveform and sleep to let MIUI driver recover
        if (currentTactonId != null) {
            cancel(skipLog = true)
            Thread.sleep(MIUI_CANCEL_SLEEP_MS)
        }

        val scaledAmps = applyIntensityModifier(tacton.amps, clampedModifier)

        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(tacton.timings, scaledAmps, tacton.repeatIdx)
        } else {
            Log.w(TAG, "API < 26 — waveform not supported, playing legacy vibration")
            @Suppress("DEPRECATION")
            vibrator.vibrate(tacton.timings.sum())
            currentTactonId = tacton.id
            return
        }

        currentTactonId = tacton.id
        vibrator.vibrate(effect)
        Log.d(TAG, "Dispatched: ${tacton.id} | modifier=$clampedModifier")
    }

    /**
     * Cancel the currently playing waveform.
     * Includes the MIUI recovery sleep unless [skipLog] is true (internal use).
     */
    fun cancel(skipLog: Boolean = false) {
        vibrator.cancel()
        currentTactonId = null
        if (!skipLog) Log.d(TAG, "Cancelled vibration")
    }

    val isPlaying: Boolean get() = currentTactonId != null

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scale each amplitude value by [modifier], clamp to [0, 255].
     * Silence segments (amp == 0) are preserved as-is.
     */
    private fun applyIntensityModifier(amps: IntArray, modifier: Float): IntArray {
        return IntArray(amps.size) { i ->
            if (amps[i] == 0) 0
            else (amps[i] * modifier).toInt().coerceIn(0, 255)
        }
    }
}
