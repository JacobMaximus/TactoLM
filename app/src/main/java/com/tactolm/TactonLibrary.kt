package com.tactolm

import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect

/**
 * TactonLibrary — haptic waveform definitions for the TactoActivity camera
 * vision feature.  Separate from [com.tactolm.haptics.TactonLibrary] which
 * handles notification tactons.
 *
 * All effects use VibrationEffect.createWaveform(timings, amplitudes, -1)
 * (no repeat) unless noted.  The processing loop uses repeat index 0.
 */
object TactonLibrary {

    // ── Confirmation tap (fires on SCAN button press) ─────────────────────────
    val confirmationEffect: VibrationEffect
        get() = VibrationEffect.createWaveform(
            longArrayOf(50, 80, 50, 0),
            intArrayOf(200,  0, 200, 0),
            -1
        )

    // ── Slow pulsing loop while waiting for API ───────────────────────────────
    // repeat index 0 → loops from the very start
    val processingEffect: VibrationEffect
        get() = VibrationEffect.createWaveform(
            longArrayOf(300, 50),
            intArrayOf(100,   0),
            0
        )

    // ── Category waveforms ────────────────────────────────────────────────────

    private val personEffect: VibrationEffect
        get() = VibrationEffect.createWaveform(
            longArrayOf(80, 40, 80, 40, 80, 200),
            intArrayOf(255,  0,255,  0,255,   0),
            -1
        )

    private val hazardEffect: VibrationEffect
        get() = VibrationEffect.createWaveform(
            longArrayOf(40, 20, 40, 20, 40, 20, 40, 200),
            intArrayOf(255,  0,255,  0,255,  0,255,   0),
            -1
        )

    private val furnitureEffect: VibrationEffect
        get() = VibrationEffect.createWaveform(
            longArrayOf(50, 10, 50, 10, 50, 10, 50, 10, 100, 80, 60, 40, 60,  0),
            intArrayOf( 60,  0,100,  0,150,  0,200,  0,  220,  0,200,  0,200,  0),
            -1
        )

    private val objectEffect: VibrationEffect
        get() = VibrationEffect.createWaveform(
            longArrayOf(15, 20, 12, 25, 18, 15, 10, 30, 14, 20, 16, 18, 11, 25, 13, 20, 15, 22, 12,  0),
            intArrayOf( 70,  0, 80,  0, 65,  0, 75,  0, 85,  0, 70,  0, 80,  0, 65,  0, 75,  0, 70,  0),
            -1
        )

    private val foodEffect: VibrationEffect
        get() = VibrationEffect.createWaveform(
            longArrayOf(50, 80, 50,  0),
            intArrayOf(200,  0,200,  0),
            -1
        )

    private val animalEffect: VibrationEffect
        get() = VibrationEffect.createWaveform(
            longArrayOf(300, 50, 300,  0),
            intArrayOf( 100,  0,  80,  0),
            -1
        )

    /**
     * Return the VibrationEffect for a given category string.
     * Returns null for unknown / ERROR categories.
     */
    fun getEffect(category: String): VibrationEffect? = when (category.uppercase()) {
        "PERSON"    -> personEffect
        "HAZARD"    -> hazardEffect
        "FURNITURE" -> furnitureEffect
        "OBJECT"    -> objectEffect
        "FOOD"      -> foodEffect
        "ANIMAL"    -> animalEffect
        else        -> null
    }

    /**
     * Approximate playback duration in milliseconds for delay-after-haptic
     * calculations.  For the error condition we use the HAZARD duration.
     */
    fun getDuration(category: String): Long = when (category.uppercase()) {
        "PERSON"    -> 80L + 40 + 80 + 40 + 80 + 200
        "HAZARD"    -> 40L + 20 + 40 + 20 + 40 + 20 + 40 + 200
        "FURNITURE" -> 50L + 10 + 50 + 10 + 50 + 10 + 50 + 10 + 100 + 80 + 60 + 40 + 60
        "OBJECT"    -> 15L + 20 + 12 + 25 + 18 + 15 + 10 + 30 + 14 + 20 + 16 + 18 + 11 + 25 + 13 + 20 + 15 + 22 + 12
        "FOOD"      -> 50L + 80 + 50
        "ANIMAL"    -> 300L + 50 + 300
        else        -> 200L
    }

    /**
     * Return the display colour for a given Gemini category name.
     * Uses [Color.parseColor] so we don't need a resources reference.
     */
    fun getCategoryColor(category: String): Int = when (category.uppercase()) {
        "PERSON"    -> Color.parseColor("#FF6B6B")
        "HAZARD"    -> Color.parseColor("#FF0000")
        "FURNITURE" -> Color.parseColor("#4ECDC4")
        "OBJECT"    -> Color.parseColor("#45B7D1")
        "FOOD"      -> Color.parseColor("#96CEB4")
        "ANIMAL"    -> Color.parseColor("#FFEAA7")
        else        -> Color.WHITE
    }
}
