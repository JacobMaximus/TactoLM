package com.tactolm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.tactolm.R
import kotlin.math.sin

/**
 * WaveformView — renders an animated bar-graph visualizer representing
 * what the LRA is currently outputting. In idle state shows flat dimmed
 * bars. When animating, bars pulse in a pattern matching the active tacton.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BAR_COUNT = 24
        private const val BAR_CORNER_RADIUS = 3f
        private const val ANIMATION_SPEED_MS = 16L   // ~60fps
    }

    // Amplitude data (0..255) representing the current tacton waveform
    private var waveformAmps: IntArray = IntArray(BAR_COUNT) { 30 }

    // Whether the waveform is actively animating
    private var isPlaying = false

    // Phase offset for the sweep animation
    private var animPhase = 0f

    private val paintActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.waveform_bar)
    }

    private val paintDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.waveform_bar_dim)
    }

    private val rect = RectF()

    private val animRunnable = object : Runnable {
        override fun run() {
            animPhase += 0.12f
            if (animPhase > (2 * Math.PI).toFloat()) animPhase = 0f
            invalidate()
            if (isPlaying) postDelayed(this, ANIMATION_SPEED_MS)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val barWidth = (w / BAR_COUNT) * 0.62f
        val spacing = w / BAR_COUNT

        for (i in 0 until BAR_COUNT) {
            val x = i * spacing + spacing / 2f

            val normalizedAmp = if (isPlaying) {
                // Animate: overlay a travelling sine wave on the waveform shape
                val baseAmp = if (i < waveformAmps.size) waveformAmps[i] / 255f else 0.15f
                val wave = (sin((animPhase + i * 0.4f).toDouble()) * 0.4f + 0.6f).toFloat()
                (baseAmp * wave).coerceIn(0.05f, 1f)
            } else {
                0.12f  // idle: flat dim bars
            }

            val barH = (h * normalizedAmp).coerceAtLeast(4f)
            val top = h - barH
            val left = x - barWidth / 2f
            val right = x + barWidth / 2f

            rect.set(left, top, right, h)
            canvas.drawRoundRect(rect, BAR_CORNER_RADIUS, BAR_CORNER_RADIUS,
                if (isPlaying) paintActive else paintDim)
        }
    }

    /**
     * Start the waveform animation with the given amplitude array.
     * @param amps IntArray from the tacton (mapped/resampled to BAR_COUNT bars)
     */
    fun play(amps: IntArray) {
        waveformAmps = resampleAmps(amps, BAR_COUNT)
        if (!isPlaying) {
            isPlaying = true
            post(animRunnable)
        }
    }

    /**
     * Stop animation and return to idle (dim flat bars).
     */
    fun stop() {
        isPlaying = false
        waveformAmps = IntArray(BAR_COUNT) { 30 }
        animPhase = 0f
        removeCallbacks(animRunnable)
        invalidate()
    }

    private fun resampleAmps(src: IntArray, targetSize: Int): IntArray {
        if (src.isEmpty()) return IntArray(targetSize) { 60 }
        return IntArray(targetSize) { i ->
            val srcIdx = (i.toFloat() / targetSize * src.size).toInt().coerceIn(0, src.lastIndex)
            src[srcIdx]
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(animRunnable)
    }
}
