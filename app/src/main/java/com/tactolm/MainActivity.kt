package com.tactolm
import android.util.Log
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tactolm.haptics.LRADispatcher
import com.tactolm.haptics.TactonLibrary
import com.tactolm.ui.WaveformView

class MainActivity : BaseActivity() {

    // ── Haptic dispatcher ────────────────────────────────────────────────
    private lateinit var dispatcher: LRADispatcher

    // ── Views ────────────────────────────────────────────────────────────
    private lateinit var tvStatus: TextView
    private lateinit var statusDot: View
    private lateinit var tvFastLatency: TextView
    private lateinit var tvGeminiLatency: TextView
    private lateinit var waveformView: WaveformView
    private lateinit var tvTactonName: TextView
    private lateinit var tvUrgencyTier: TextView
    private lateinit var tvTactonId: TextView
    private lateinit var tvPayload: TextView
    private lateinit var tvRationale: TextView
    private lateinit var tvTrackSource: TextView
    private lateinit var btnScenarioBbmp: LinearLayout
    private lateinit var btnScenarioEmergency: LinearLayout
    private lateinit var btnScenarioSocial: LinearLayout
    // Custom nav bar — managed by BaseActivity

    // ── Demo scenario texts ───────────────────────────────────────────────
    private val scenarioBbmp = "BBMP Health Department: Dengue outbreak reported in Whitefield area. Fumigation drive scheduled tomorrow 6AM–10AM. Residents advised to clear stagnant water. Aarogya Setu alert ID: KA2024-DEN-447"
    private val scenarioEmergency = "Kaveri Water Supply BWSSB: Emergency shutdown in your area today 6AM to 6PM. Store water immediately."
    private val scenarioSocial = "Rahul liked your photo on Instagram"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


            Log.d("TactoLM_TEST", "APP STARTED - LOG SYSTEM WORKING")

        

        // Edge-to-edge, forced dark
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_main)

        dispatcher = LRADispatcher(this)

        bindViews()
        setupNavBar(NAV_HOME)
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updateListenerStatus()
    }

    /** Buttons that Vol Up/Down cycle through when page is locked. */
    override fun getNavigableViews() = listOf(
        btnScenarioBbmp,
        btnScenarioEmergency,
        btnScenarioSocial
    )

    private fun bindViews() {
        tvStatus         = findViewById(R.id.tv_status)
        statusDot        = findViewById(R.id.status_dot)
        tvFastLatency    = findViewById(R.id.tv_fast_latency)
        tvGeminiLatency  = findViewById(R.id.tv_gemini_latency)
        waveformView     = findViewById(R.id.waveform_view)
        tvTactonName     = findViewById(R.id.tv_tacton_name)
        tvUrgencyTier    = findViewById(R.id.tv_urgency_tier)
        tvTactonId       = findViewById(R.id.tv_tacton_id)
        tvPayload        = findViewById(R.id.tv_payload)
        tvRationale      = findViewById(R.id.tv_rationale)
        tvTrackSource    = findViewById(R.id.tv_track_source)
        btnScenarioBbmp      = findViewById(R.id.btn_scenario_bbmp)
        btnScenarioEmergency = findViewById(R.id.btn_scenario_emergency)
        btnScenarioSocial    = findViewById(R.id.btn_scenario_social)
        val btnReset         = findViewById<android.view.View>(R.id.btn_reset_scenario)
        btnReset.setOnClickListener { resetToIdle() }
    }

    private fun setupClickListeners() {

        // ── Demo scenario shortcuts ──────────────────────────────────────
        btnScenarioBbmp.setOnClickListener {
            classifyAndDisplay(scenarioBbmp)
        }
        btnScenarioEmergency.setOnClickListener {
            classifyAndDisplay(scenarioEmergency)
        }
        btnScenarioSocial.setOnClickListener {
            classifyAndDisplay(scenarioSocial)
        }
    }


    // ── Notification access status indicator ─────────────────────────────────
    private fun updateListenerStatus() {
        val granted = isNotificationAccessGranted()
        val color = if (granted) R.color.status_active else R.color.tier_critical
        val dotColor = ContextCompat.getColor(this, color)

        statusDot.background.setTint(dotColor)

        tvStatus.text = if (granted) "Listening" else "No Access — Tap to fix"
        tvStatus.setTextColor(dotColor)

        if (!granted) {
            tvStatus.setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        } else {
            tvStatus.setOnClickListener(null)
        }
    }



    // ─────────────────────────────────────────────────────────────────────────
    // Stub classification — will be replaced by TactoLMPipeline
    // ─────────────────────────────────────────────────────────────────────────
    private fun classifyAndDisplay(text: String) {
        val lower = text.lowercase()
        val result: ClassificationResult = when {
            lower.contains("emergency") || lower.contains("otp") ||
            lower.contains("expires in") || lower.contains("ambulance") -> {
                dispatcher.dispatch(TactonLibrary.PULSE_BURST)
                ClassificationResult("CRITICAL", "pulse_burst",
                    "Emergency signal detected", "18ms", "—", "FAST",
                    TactonLibrary.PULSE_BURST.amps)
            }
            lower.contains("bbmp") || lower.contains("dengue") ||
            lower.contains("health") || lower.contains("aarogya") -> {
                dispatcher.dispatch(TactonLibrary.HEALTH_RAMP)
                ClassificationResult("HEALTH", "health_ramp",
                    "Dengue alert Whitefield. Clear stagnant water. Fumigation 6AM.",
                    "22ms", "284ms", "GEMINI", TactonLibrary.HEALTH_RAMP.amps)
            }
            lower.contains("liked") || lower.contains("instagram") ||
            lower.contains("message from") -> {
                dispatcher.dispatch(TactonLibrary.GRIT_TEXTURE)
                ClassificationResult("SOCIAL", "grit_texture",
                    "Social notification", "14ms", "—", "FAST",
                    TactonLibrary.GRIT_TEXTURE.amps)
            }
            else -> {
                dispatcher.dispatch(TactonLibrary.NAV_SLIDE)
                ClassificationResult("INFORMATIONAL", "nav_slide",
                    text.take(60), "19ms", "310ms", "GEMINI",
                    TactonLibrary.NAV_SLIDE.amps)
            }
        }
        applyResult(result)
    }

    private fun applyResult(r: ClassificationResult) {
        animateTextChange(tvFastLatency, r.fastMs)
        animateTextChange(tvGeminiLatency, r.geminiMs)

        tvUrgencyTier.text = r.urgency
        tvUrgencyTier.setTextColor(urgencyColor(r.urgency))
        tvUrgencyTier.background = urgencyBadgeBg(r.urgency)

        tvTactonId.text = r.tacton
        tvTactonId.setTextColor(urgencyColor(r.urgency))

        tvPayload.text = r.payload

        tvTrackSource.text = r.trackSrc
        tvTrackSource.visibility = View.VISIBLE

        tvTactonName.text = r.tacton
        waveformView.play(r.amps)

        pulseCard(findViewById(R.id.card_output))
    }

    private fun resetOutput() {
        tvFastLatency.text = "— ms"
        tvGeminiLatency.text = "— ms"
        tvUrgencyTier.text = "—"
        tvUrgencyTier.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvUrgencyTier.setBackgroundResource(R.drawable.bg_urgency_badge)
        tvTactonId.text = "—"
        tvTactonId.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tvPayload.text = getString(R.string.output_idle)
        tvTrackSource.visibility = View.INVISIBLE
        tvTactonName.text = "idle"
        waveformView.stop()
        dispatcher.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun urgencyColor(urgency: String): Int {
        val colorRes = when (urgency) {
            "CRITICAL"      -> R.color.tier_critical
            "HEALTH"        -> R.color.tier_health
            "SOCIAL"        -> R.color.tier_social
            "AMBIENT"       -> R.color.tier_ambient
            "INFORMATIONAL" -> R.color.tier_informational
            else            -> R.color.text_secondary
        }
        return ContextCompat.getColor(this, colorRes)
    }

    private fun urgencyBadgeBg(urgency: String): android.graphics.drawable.Drawable? {
        val bgRes = when (urgency) {
            "CRITICAL"      -> R.drawable.bg_scenario_critical
            "HEALTH"        -> R.drawable.bg_scenario_health
            "SOCIAL"        -> R.drawable.bg_scenario_social
            else            -> R.drawable.bg_urgency_badge
        }
        return ContextCompat.getDrawable(this, bgRes)
    }

    private fun animateTextChange(tv: TextView, newText: String) {
        tv.animate().alpha(0f).setDuration(100).withEndAction {
            tv.text = newText
            tv.animate().alpha(1f).setDuration(150).start()
        }.start()
    }

    private fun pulseCard(card: View) {
        val sx = ObjectAnimator.ofFloat(card, View.SCALE_X, 1f, 1.016f, 1f)
        val sy = ObjectAnimator.ofFloat(card, View.SCALE_Y, 1f, 1.016f, 1f)
        AnimatorSet().apply {
            playTogether(sx, sy)
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dispatcher.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    data class ClassificationResult(
        val urgency: String,
        val tacton: String,
        val payload: String,
        val fastMs: String,
        val geminiMs: String,
        val trackSrc: String,
        val amps: IntArray
    )
    private fun resetToIdle() {
        tvTactonName.text = "idle"
        tvTactonName.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
        
        tvUrgencyTier.text = "—"
        tvUrgencyTier.background.setTint(ContextCompat.getColor(this, R.color.stroke_subtle))
        tvUrgencyTier.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        
        tvTactonId.text = "—"
        tvPayload.text = getString(R.string.output_idle)
        tvTrackSource.visibility = android.view.View.INVISIBLE
        tvRationale.visibility = android.view.View.GONE
        
        tvFastLatency.text = "0 ms"
        tvGeminiLatency.text = "0 ms"
        
        waveformView.stop()
    }

}