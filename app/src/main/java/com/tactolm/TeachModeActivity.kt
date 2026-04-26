package com.tactolm

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import androidx.core.content.ContextCompat
import com.tactolm.haptics.LRADispatcher
import com.tactolm.haptics.Tacton
import com.tactolm.haptics.TactonLibrary
import com.tactolm.haptics.UrgencyTier

class TeachModeActivity : BaseActivity() {

    private lateinit var dispatcher: LRADispatcher

    private lateinit var btnBack: LinearLayout
    private lateinit var tvActiveName: TextView
    private lateinit var tvActiveDesc: TextView
    private lateinit var tvActiveTier: TextView
    private lateinit var viewPulseRing: View
    private lateinit var cardActiveIndicator: LinearLayout

    // Tacton buttons
    private lateinit var btnPulseBurst: LinearLayout
    private lateinit var btnHealthRamp: LinearLayout
    private lateinit var btnSlowRamp: LinearLayout
    private lateinit var btnConfirmTap: LinearLayout
    private lateinit var btnWaitHold: LinearLayout
    private lateinit var btnNavSlide: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teach_mode)

        dispatcher = LRADispatcher(this)

        bindViews()
        setupNavBar(NAV_TEACH)
        setupListeners()
    }

    private fun bindViews() {
        btnBack              = findViewById(R.id.btn_back)
        tvActiveName         = findViewById(R.id.tv_active_tacton_name)
        tvActiveDesc         = findViewById(R.id.tv_active_tacton_desc)
        tvActiveTier         = findViewById(R.id.tv_active_tier)
        viewPulseRing        = findViewById(R.id.view_pulse_ring)
        cardActiveIndicator  = findViewById(R.id.card_active_indicator)

        btnPulseBurst  = findViewById(R.id.btn_pulse_burst)
        btnHealthRamp  = findViewById(R.id.btn_health_ramp)
        btnSlowRamp    = findViewById(R.id.btn_slow_ramp)
        btnNavSlide    = findViewById(R.id.btn_nav_slide)
        btnConfirmTap  = findViewById(R.id.btn_confirm_tap)
        btnWaitHold    = findViewById(R.id.btn_wait_hold)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnPulseBurst.setOnClickListener  { fire(TactonLibrary.PULSE_BURST,  btnPulseBurst) }
        btnHealthRamp.setOnClickListener  { fire(TactonLibrary.HEALTH_RAMP,  btnHealthRamp) }
        btnSlowRamp.setOnClickListener    { fire(TactonLibrary.SLOW_RAMP,    btnSlowRamp) }
        btnNavSlide.setOnClickListener    { fire(TactonLibrary.NAV_SLIDE,    btnNavSlide) }
        btnConfirmTap.setOnClickListener  { fire(TactonLibrary.CONFIRM_TAP,  btnConfirmTap) }
        btnWaitHold.setOnClickListener    { fire(TactonLibrary.WAIT_HOLD,    btnWaitHold) }
    }

    override fun getNavigableViews(): List<View> = listOf(
        btnPulseBurst,
        btnHealthRamp,
        btnSlowRamp,
        btnNavSlide,
        btnConfirmTap,
        btnWaitHold
    )

    private fun fire(tacton: Tacton, sourceButton: View) {
        // 1. Dispatch to hardware — this is the fast path, happens first
        dispatcher.dispatch(tacton)

        // 2. Update active indicator card
        updateIndicator(tacton)

        // 3. Button press animation (quick scale bounce)
        animateButtonPress(sourceButton)

        // 4. Pulse ring animation on indicator
        animatePulseRing(tacton)
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun updateIndicator(tacton: Tacton) {
        tvActiveName.text = tacton.displayName
        tvActiveDesc.text = tacton.description

        val (tierLabel, tierColor) = when (tacton.urgencyTier) {
            UrgencyTier.CRITICAL      -> "CRITICAL"      to R.color.tier_critical
            UrgencyTier.HEALTH        -> "HEALTH"        to R.color.tier_health
            UrgencyTier.SOCIAL        -> "SOCIAL"        to R.color.tier_social
            UrgencyTier.AMBIENT       -> "AMBIENT"       to R.color.tier_ambient
            UrgencyTier.INFORMATIONAL -> "INFORMATIONAL" to R.color.tier_informational
            UrgencyTier.SYSTEM        -> "SYSTEM"        to R.color.accent_secondary
        }

        tvActiveTier.text = tierLabel
        tvActiveTier.setTextColor(ContextCompat.getColor(this, tierColor))

        // Briefly pulse the indicator card
        pulseCard(cardActiveIndicator)
    }

    private fun pulseCard(card: View) {
        val sx = ObjectAnimator.ofFloat(card, View.SCALE_X, 1f, 1.016f, 1f)
        val sy = ObjectAnimator.ofFloat(card, View.SCALE_Y, 1f, 1.016f, 1f)
        AnimatorSet().apply {
            playTogether(sx, sy)
            duration = 400
            interpolator = android.view.animation.OvershootInterpolator(2f)
            start()
        }
    }

    private fun animateButtonPress(view: View) {
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.96f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.96f)
            )
            duration = 80
            interpolator = AccelerateInterpolator()
        }
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.96f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.96f, 1f)
            )
            duration = 180
            interpolator = DecelerateInterpolator()
        }
        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp)
            start()
        }
    }

    private fun animatePulseRing(tacton: Tacton) {
        val ringColor = when (tacton.urgencyTier) {
            UrgencyTier.CRITICAL      -> R.color.tier_critical
            UrgencyTier.HEALTH        -> R.color.tier_health
            UrgencyTier.SOCIAL        -> R.color.tier_social
            UrgencyTier.INFORMATIONAL -> R.color.tier_informational
            else                      -> R.color.accent_primary
        }
        viewPulseRing.setBackgroundResource(R.drawable.bg_pulse_ring)
        viewPulseRing.background.setTint(ContextCompat.getColor(this, ringColor))

        viewPulseRing.clearAnimation()
        val expand = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(viewPulseRing, View.SCALE_X, 1f, 1.6f),
                ObjectAnimator.ofFloat(viewPulseRing, View.SCALE_Y, 1f, 1.6f),
                ObjectAnimator.ofFloat(viewPulseRing, View.ALPHA,   0.8f, 0f)
            )
            duration = 500
            interpolator = DecelerateInterpolator()
        }
        expand.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                viewPulseRing.alpha = 0.8f
                viewPulseRing.scaleX = 1f
                viewPulseRing.scaleY = 1f
            }
        })
        expand.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        dispatcher.cancel()
    }
}
