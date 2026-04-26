package com.tactolm

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.tactolm.haptics.LRADispatcher
import com.tactolm.haptics.TactonLibrary
import com.tactolm.pipeline.NotificationStore
import com.tactolm.pipeline.NotificationStore.TactoNotification

class NotificationsActivity : BaseActivity() {

    private lateinit var dispatcher: LRADispatcher
    private lateinit var feedContainer: LinearLayout
    private lateinit var bannerGrant: View
    private lateinit var btnGrantAccess: LinearLayout
    private lateinit var bannerNoApps: View
    private lateinit var btnFilterApps: LinearLayout

    // ── Live update receiver ──────────────────────────────────────────────────
    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val latest = NotificationStore.notifications.firstOrNull() ?: return
            prependCard(latest, animate = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        dispatcher = LRADispatcher(this)
        feedContainer  = findViewById(R.id.feed_container)
        bannerGrant    = findViewById(R.id.banner_grant)
        btnGrantAccess = findViewById(R.id.btn_grant_access)
        bannerNoApps   = findViewById(R.id.banner_no_apps)
        btnFilterApps  = findViewById(R.id.btn_filter_apps)

        setupNavBar(NAV_FEED)

        findViewById<LinearLayout>(R.id.btn_back).setOnClickListener { finish() }

        btnGrantAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnFilterApps.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()

        // Show/hide permission banner
        val hasAccess = isNotificationAccessGranted()
        bannerGrant.visibility = if (hasAccess) View.GONE else View.VISIBLE

        // Check if apps are selected
        val prefs = getSharedPreferences("TactoLM_Prefs", Context.MODE_PRIVATE)
        val selectedApps = prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
        val hasSelectedApps = selectedApps.isNotEmpty()

        // Show/hide no-apps banner
        bannerNoApps.visibility = if (hasAccess && !hasSelectedApps) View.VISIBLE else View.GONE

        // Rebuild the feed from the live store
        feedContainer.removeAllViews()
        val notifications = NotificationStore.notifications
        if (notifications.isEmpty() && hasAccess) {
            showEmptyState()
        } else {
            notifications.forEachIndexed { index, item ->
                val card = buildCard(item)
                card.alpha = 0f
                card.translationY = 20f
                feedContainer.addView(card)
                card.postDelayed({
                    card.animate()
                        .alpha(1f).translationY(0f)
                        .setDuration(220).setInterpolator(DecelerateInterpolator())
                        .start()
                }, index * 40L)
            }
        }

        // Register live broadcast receiver
        registerReceiver(
            notifReceiver,
            IntentFilter(TactoLMListenerService.ACTION_NOTIFICATION_POSTED),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(notifReceiver) } catch (_: Exception) {}
    }

    override fun isPageLockSupported(): Boolean = false

    override fun onDestroy() {
        super.onDestroy()
        dispatcher.cancel()
    }

    // ── Prepend a new card to the top of the feed ─────────────────────────────

    private fun prependCard(item: TactoNotification, animate: Boolean) {
        // Remove empty-state placeholder if it exists
        feedContainer.findViewWithTag<View>("empty_state")?.let { feedContainer.removeView(it) }

        val card = buildCard(item)
        if (animate) {
            card.alpha = 0f
            card.translationY = -30f
        }
        feedContainer.addView(card, 0)
        if (animate) {
            card.animate()
                .alpha(1f).translationY(0f)
                .setDuration(300).setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    // ── Build a single notification card ─────────────────────────────────────

    private fun buildCard(item: TactoNotification): CardView {
        val card = CardView(this).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(20), dp(4), dp(20), dp(4)) }
            radius = dp(14).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(this@NotificationsActivity, R.color.bg_card))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dispatcher.dispatch(getTactonForId(item.tactonId))
                animateCardPress(this)
            }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            gravity = android.view.Gravity.TOP
        }

        // Left accent bar
        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT).also {
                it.marginEnd = dp(14)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(2).toFloat()
                setColor(ContextCompat.getColor(this@NotificationsActivity, item.tierColorRes))
            }
        })

        // Category vibration icon
        val iconResId = when (item.tactonId) {
            "pulse_burst"  -> R.drawable.ic_emergency
            "health_ramp"  -> R.drawable.ic_health
            "slow_ramp"    -> R.drawable.ic_transit
            "nav_slide"    -> R.drawable.ic_navigation
            "confirm_tap"  -> R.drawable.ic_system
            "wait_hold"    -> R.drawable.ic_processing
            else           -> R.drawable.ic_bell
        }
        
        inner.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).also { it.marginEnd = dp(12) }
            setImageResource(iconResId)
            imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@NotificationsActivity, item.tierColorRes))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@NotificationsActivity, R.color.bg_card_elevated))
            }
        })

        // Text content
        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        textBlock.addView(TextView(this).apply {
            text = item.appName
            textSize = 10f
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, item.tierColorRes))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.06f
        })

        textBlock.addView(TextView(this).apply {
            text = item.title
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.text_primary))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(0, dp(2), 0, 0)
        })

        if (item.body.isNotBlank()) {
            textBlock.addView(TextView(this).apply {
                text = item.body
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.text_secondary))
                setLineSpacing(0f, 1.4f)
                setPadding(0, dp(4), 0, 0)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }

        // Meta row: time + category badge + haptic hint
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        metaRow.addView(TextView(this).apply {
            text = item.timeLabel
            textSize = 10f
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.text_disabled))
        })

        metaRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        metaRow.addView(TextView(this).apply {
            text = item.tier
            textSize = 9f
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, item.tierColorRes))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.10f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(ContextCompat.getColor(this@NotificationsActivity, R.color.bg_card_elevated))
            }
            setPadding(dp(8), dp(3), dp(8), dp(3))
        })

        metaRow.addView(TextView(this).apply {
            text = "  ◎ ${item.tactonId}"
            textSize = 9f
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.text_disabled))
            typeface = android.graphics.Typeface.create("sans-serif-mono", android.graphics.Typeface.NORMAL)
        })

        textBlock.addView(metaRow)
        inner.addView(textBlock)
        card.addView(inner)

        return card
    }

    // ── Empty state placeholder ───────────────────────────────────────────────

    private fun showEmptyState() {
        val placeholder = LinearLayout(this).apply {
            tag = "empty_state"
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200)
            )
        }
        placeholder.addView(TextView(this).apply {
            text = "\u25cb"
            textSize = 40f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.text_disabled))
        })
        placeholder.addView(TextView(this).apply {
            text = "Waiting for notifications…"
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.text_secondary))
            setPadding(0, dp(12), 0, 0)
        })
        placeholder.addView(TextView(this).apply {
            text = "They will appear here as they arrive"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@NotificationsActivity, R.color.text_tertiary))
            setPadding(0, dp(6), 0, 0)
        })
        feedContainer.addView(placeholder)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────


    private fun getTactonForId(id: String) = when (id) {
        "pulse_burst"  -> TactonLibrary.PULSE_BURST
        "health_ramp"  -> TactonLibrary.HEALTH_RAMP
        "slow_ramp"    -> TactonLibrary.SLOW_RAMP
        "nav_slide"    -> TactonLibrary.NAV_SLIDE
        "confirm_tap"  -> TactonLibrary.CONFIRM_TAP
        "wait_hold"    -> TactonLibrary.WAIT_HOLD
        else           -> TactonLibrary.CONFIRM_TAP
    }

    private fun animateCardPress(card: CardView) {
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, View.SCALE_X, 1f, 0.97f),
                ObjectAnimator.ofFloat(card, View.SCALE_Y, 1f, 0.97f)
            )
            duration = 70; interpolator = AccelerateInterpolator()
        }
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, View.SCALE_X, 0.97f, 1f),
                ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.97f, 1f)
            )
            duration = 160; interpolator = DecelerateInterpolator()
        }
        AnimatorSet().apply { playSequentially(scaleDown, scaleUp); start() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
