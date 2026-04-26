package com.tactolm

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts

import androidx.core.content.ContextCompat
import com.tactolm.pipeline.AudioRecognitionService

class DoorbellActivity : BaseActivity() {

    private lateinit var cardToggle: LinearLayout
    private lateinit var viewPulseRing: View
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var bannerMicPermission: LinearLayout
    private lateinit var btnGrantMic: LinearLayout

    private var isListening = false
    private var pulseAnimator: AnimatorSet? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                bannerMicPermission.visibility = View.GONE
                toggleListening()
            } else {
                bannerMicPermission.visibility = View.VISIBLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doorbell)

        bindViews()
        setupNavBar(NAV_DOORBELL)
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        
        // Sync UI state with actual service state
        isListening = AudioRecognitionService.isRunning
        updateUI()
        if (isListening) {
            startPulseAnimation()
        } else {
            stopPulseAnimation()
        }
        
        checkPermissionBanner()
    }

    override fun getNavigableViews(): List<View> {
        val views = mutableListOf<View>()
        if (bannerMicPermission.visibility == View.VISIBLE) {
            views.add(btnGrantMic)
        }
        views.add(cardToggle)
        return views
    }

    private fun bindViews() {
        cardToggle = findViewById(R.id.card_doorbell_toggle)
        viewPulseRing = findViewById(R.id.view_pulse_ring)
        ivStatusIcon = findViewById(R.id.iv_status_icon)
        tvStatusTitle = findViewById(R.id.tv_status_title)
        tvStatusDesc = findViewById(R.id.tv_status_desc)
        bannerMicPermission = findViewById(R.id.banner_mic_permission)
        btnGrantMic = findViewById(R.id.btn_grant_mic)
    }

    private fun setupListeners() {
        cardToggle.setOnClickListener {
            if (hasMicPermission()) {
                toggleListening()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        btnGrantMic.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun checkPermissionBanner() {
        if (hasMicPermission()) {
            bannerMicPermission.visibility = View.GONE
        } else {
            bannerMicPermission.visibility = View.VISIBLE
            // If we're listening but lost permission, stop
            if (isListening) toggleListening()
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun toggleListening() {
        isListening = !isListening
        updateUI()

        val serviceIntent = Intent(this, AudioRecognitionService::class.java)
        if (isListening) {
            ContextCompat.startForegroundService(this, serviceIntent)
            startPulseAnimation()
        } else {
            stopService(serviceIntent)
            stopPulseAnimation()
        }
    }

    private fun updateUI() {
        val toggleCard = findViewById<View>(R.id.card_doorbell_toggle)
        if (isListening) {
            tvStatusTitle.text = "Listening for Doorbells..."
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_neon))
            tvStatusDesc.text = "Tap to stop"
            ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_neon))
            viewPulseRing.visibility = View.VISIBLE
            toggleCard.setBackgroundResource(R.drawable.bg_card_glowing)
        } else {
            tvStatusTitle.text = "Doorbell is Off"
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            tvStatusDesc.text = "Tap to start listening"
            ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_disabled))
            viewPulseRing.visibility = View.GONE
            toggleCard.setBackgroundResource(R.drawable.bg_glass_card)
        }
    }

    private fun startPulseAnimation() {
        if (pulseAnimator != null) return

        viewPulseRing.alpha = 0.6f
        viewPulseRing.scaleX = 0.8f
        viewPulseRing.scaleY = 0.8f

        pulseAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(viewPulseRing, View.SCALE_X, 0.8f, 1.5f),
                ObjectAnimator.ofFloat(viewPulseRing, View.SCALE_Y, 0.8f, 1.5f),
                ObjectAnimator.ofFloat(viewPulseRing, View.ALPHA, 0.6f, 0f)
            )
            duration = 1500
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isListening) {
                        animation.start()
                    }
                }
            })
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPulseAnimation()
    }
}
