package com.tactolm

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tactolm.haptics.LRADispatcher
import com.tactolm.haptics.TactonLibrary

/**
 * BaseActivity — all screens extend this to get the shared floating nav bar
 * and hardware volume-button navigation.
 *
 * ## Volume button behaviour
 *
 * ### Unlocked mode (default)
 *   VOL UP   → navigate to the NEXT tab  (cycles forward)
 *   VOL DOWN → navigate to the PREV tab  (cycles backward)
 *
 * ### Simultaneous VOL UP + VOL DOWN
 *   Toggles PAGE LOCK mode.
 *   • Lock confirmed  : PULSE_BURST haptic + "Page Locked" toast
 *   • Unlock confirmed: CONFIRM_TAP haptic + "Page Unlocked" toast
 *
 * ### Locked mode
 *   VOL UP   → move focus to the NEXT focusable view in the page
 *   VOL DOWN → move focus to the PREV focusable view in the page
 *   Views are provided by each subclass via [getNavigableViews].
 *
 * Simultaneous detection: the first key is deferred by SIMULTANEOUS_WINDOW_MS.
 * If the second key arrives before the window expires the single-key action
 * is cancelled and the lock is toggled instead.
 */
abstract class BaseActivity : AppCompatActivity() {

    companion object {
        const val NAV_HOME     = 0
        const val NAV_TEACH    = 1
        const val NAV_FEED     = 2
        const val NAV_VISION   = 3
        const val NAV_DOORBELL = 4

        /** Ordered list of tabs for volume-button cycling. */
        private val NAV_ORDER = listOf(NAV_HOME, NAV_TEACH, NAV_FEED, NAV_VISION, NAV_DOORBELL)

        /** Window in ms to decide if two volume presses are "simultaneous". */
        private const val SIMULTANEOUS_WINDOW_MS = 90L
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Set by setupNavBar() — used for volume-key navigation. */
    private var currentTab: Int = NAV_HOME
    private var volumeDispatcher: LRADispatcher? = null

    // Page-lock state
    private var isPageLocked = false
    private var focusedNavIndex = 0
    private var originalBackgrounds = mutableMapOf<View, android.graphics.drawable.Drawable?>()

    // Simultaneous-press and long-press detection
    private var volUpHeld   = false
    private var volDownHeld = false
    private var bothHandled = false
    private var navActionFired = false
    private var pendingNavAction: Runnable? = null
    private var pendingBothLongPress: Runnable? = null
    private val keyHandler  = Handler(Looper.getMainLooper())

    // ── Nav bar views ─────────────────────────────────────────────────────────

    private lateinit var navBtnHome: LinearLayout
    private lateinit var navBtnTeach: LinearLayout
    private lateinit var navBtnFeed: LinearLayout
    private lateinit var navBtnVision: LinearLayout
    private lateinit var navBtnDoorbell: LinearLayout
    private lateinit var navIconHome: ImageView
    private lateinit var navIconTeach: ImageView
    private lateinit var navIconFeed: ImageView
    private lateinit var navIconVision: ImageView
    private lateinit var navIconDoorbell: ImageView
    private lateinit var navLabelHome: TextView
    private lateinit var navLabelTeach: TextView
    private lateinit var navLabelFeed: TextView
    private lateinit var navLabelVision: TextView
    private lateinit var navLabelDoorbell: TextView

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        volumeDispatcher = LRADispatcher(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        keyHandler.removeCallbacksAndMessages(null)
        volumeDispatcher?.cancel()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    // ── Subclass hook ─────────────────────────────────────────────────────────

    /**
     * Return the ordered list of interactive views that volume Up/Down should
     * cycle through when the page is locked.
     * Override in each Activity to declare its buttons.
     */
    open fun getNavigableViews(): List<View> = emptyList()

    /**
     * Whether the current page supports being locked for button navigation.
     */
    open fun isPageLockSupported(): Boolean = true

    // ── Nav bar setup ─────────────────────────────────────────────────────────

    protected fun setupNavBar(activeTab: Int) {
        currentTab = activeTab
        navBtnHome     = findViewById(R.id.nav_btn_home)     ?: return
        navBtnTeach    = findViewById(R.id.nav_btn_teach)    ?: return
        navBtnFeed     = findViewById(R.id.nav_btn_feed)     ?: return
        navBtnVision   = findViewById(R.id.nav_btn_vision)   ?: return
        navBtnDoorbell = findViewById(R.id.nav_btn_doorbell) ?: return
        navIconHome     = findViewById(R.id.nav_icon_home)
        navIconTeach    = findViewById(R.id.nav_icon_teach)
        navIconFeed     = findViewById(R.id.nav_icon_feed)
        navIconVision   = findViewById(R.id.nav_icon_vision)
        navIconDoorbell = findViewById(R.id.nav_icon_doorbell)
        navLabelHome     = findViewById(R.id.nav_label_home)
        navLabelTeach    = findViewById(R.id.nav_label_teach)
        navLabelFeed     = findViewById(R.id.nav_label_feed)
        navLabelVision   = findViewById(R.id.nav_label_vision)
        navLabelDoorbell = findViewById(R.id.nav_label_doorbell)

        applyActiveState(activeTab)

        navBtnHome.setOnClickListener {
            if (activeTab != NAV_HOME) {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                overridePendingTransition(0, 0)
            }
        }
        navBtnTeach.setOnClickListener {
            if (activeTab != NAV_TEACH) {
                startActivity(Intent(this, TeachModeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                overridePendingTransition(0, 0)
            }
        }
        navBtnFeed.setOnClickListener {
            if (activeTab != NAV_FEED) {
                startActivity(Intent(this, NotificationsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                overridePendingTransition(0, 0)
            }
        }
        navBtnVision.setOnClickListener {
            if (activeTab != NAV_VISION) {
                startActivity(Intent(this, TactoActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                overridePendingTransition(0, 0)
            }
        }
        navBtnDoorbell.setOnClickListener {
            if (activeTab != NAV_DOORBELL) {
                startActivity(Intent(this, DoorbellActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                overridePendingTransition(0, 0)
            }
        }
    }

    // ── Volume key handling ───────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                          event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (!isVolumeKey) return super.dispatchKeyEvent(event)

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return true // Consume system repeats
                
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)   volUpHeld   = true
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) volDownHeld = true

                if (volUpHeld && volDownHeld) {
                    cancelPendingNav()
                    if (!navActionFired && !bothHandled) {
                        pendingBothLongPress = Runnable {
                            if (volUpHeld && volDownHeld) {
                                bothHandled = true
                                if (isPageLocked) selectFocusedView()
                            }
                        }
                        keyHandler.postDelayed(pendingBothLongPress!!, 500)
                    }
                    return true
                }

                // First key pressed
                if (!navActionFired && !bothHandled) {
                    cancelPendingNav()
                    pendingNavAction = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        Runnable { 
                            navActionFired = true
                            handleSingleVolUp() 
                        }
                    } else {
                        Runnable { 
                            navActionFired = true
                            handleSingleVolDown() 
                        }
                    }
                    keyHandler.postDelayed(pendingNavAction!!, SIMULTANEOUS_WINDOW_MS)
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                // If both were held, and we haven't handled the long press yet, it's a short press of both (Lock/Unlock)
                if (volUpHeld && volDownHeld && !bothHandled && !navActionFired) {
                    cancelPendingBothLongPress()
                    bothHandled = true
                    togglePageLock()
                }

                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)   volUpHeld   = false
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) volDownHeld = false

                if (!volUpHeld && !volDownHeld) {
                    bothHandled = false
                    navActionFired = false
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun cancelPendingNav() {
        pendingNavAction?.let { keyHandler.removeCallbacks(it) }
        pendingNavAction = null
    }

    private fun cancelPendingBothLongPress() {
        pendingBothLongPress?.let { keyHandler.removeCallbacks(it) }
        pendingBothLongPress = null
    }

    // ── Single-key actions ────────────────────────────────────────────────────

    private fun handleSingleVolUp() {
        if (isPageLocked) {
            shiftFocus(+1)
        } else {
            val idx = NAV_ORDER.indexOf(currentTab)
            volumeDispatcher?.dispatch(TactonLibrary.CONFIRM_TAP)
            navigateTo(NAV_ORDER[(idx + 1) % NAV_ORDER.size])
        }
    }

    private fun handleSingleVolDown() {
        if (isPageLocked) {
            shiftFocus(-1)
        } else {
            val idx = NAV_ORDER.indexOf(currentTab)
            volumeDispatcher?.dispatch(TactonLibrary.CONFIRM_TAP)
            navigateTo(NAV_ORDER[(idx - 1 + NAV_ORDER.size) % NAV_ORDER.size])
        }
    }

    // ── Focus cycling (locked mode) ───────────────────────────────────────────

    private fun shiftFocus(delta: Int) {
        val views = getNavigableViews()
        if (views.isEmpty()) return
        
        // Remove focus from old view
        if (views.indices.contains(focusedNavIndex)) {
            val oldView = views[focusedNavIndex]
            originalBackgrounds[oldView]?.let { oldView.background = it }
        }

        focusedNavIndex = (focusedNavIndex + delta + views.size) % views.size
        
        // Add focus to new view
        val newView = views[focusedNavIndex]
        if (!originalBackgrounds.containsKey(newView)) {
            originalBackgrounds[newView] = newView.background
        }
        newView.setBackgroundResource(R.drawable.bg_card_focused)

        newView.apply {
            requestFocus()
            // Scale-pulse so even sighted users see which button is active
            animate().scaleX(1.10f).scaleY(1.10f).setDuration(80)
                .withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                .start()
        }
        // Short haptic tick per step
        volumeDispatcher?.dispatch(TactonLibrary.CONFIRM_TAP)
    }

    private fun selectFocusedView() {
        val views = getNavigableViews()
        if (views.indices.contains(focusedNavIndex)) {
            volumeDispatcher?.dispatch(TactonLibrary.PULSE_BURST) // distinctly acknowledge click
            views[focusedNavIndex].performClick()
        }
    }

    // Tap anywhere on the screen to select the focused button while locked
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (isPageLocked && ev?.action == android.view.MotionEvent.ACTION_DOWN) {
            selectFocusedView()
            return true // consume the touch! entire screen is a button
        }
        return super.dispatchTouchEvent(ev)
    }

    // ── Page lock toggle ──────────────────────────────────────────────────────

    private fun togglePageLock() {
        if (!isPageLockSupported()) {
            volumeDispatcher?.dispatch(TactonLibrary.CONFIRM_TAP)
            Toast.makeText(this, "Page Lock not available on this screen", Toast.LENGTH_SHORT).show()
            return
        }

        isPageLocked = !isPageLocked
        // Reset held state so next individual press is treated fresh
        volUpHeld   = false
        volDownHeld = false

        val views = getNavigableViews()

        if (isPageLocked) {
            // Distinct double-pulse = "locked"
            volumeDispatcher?.dispatch(TactonLibrary.PULSE_BURST)
            Toast.makeText(this, "\uD83D\uDD12 Page Locked — Vol Up/Down navigate buttons", Toast.LENGTH_SHORT).show()
            focusedNavIndex = 0
            if (views.isNotEmpty()) {
                val firstView = views[0]
                if (!originalBackgrounds.containsKey(firstView)) {
                    originalBackgrounds[firstView] = firstView.background
                }
                firstView.setBackgroundResource(R.drawable.bg_card_focused)
                firstView.requestFocus()
            }
        } else {
            // Unlock: clear backgrounds
            views.forEach { view ->
                originalBackgrounds[view]?.let { view.background = it }
            }
            originalBackgrounds.clear()

            // Single confirm tap = "unlocked"
            volumeDispatcher?.dispatch(TactonLibrary.CONFIRM_TAP)
            Toast.makeText(this, "\uD83D\uDD13 Page Unlocked — Vol Up/Down switch pages", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun navigateTo(tab: Int) {
        if (tab == currentTab) return
        val intent = when (tab) {
            NAV_HOME     -> Intent(this, MainActivity::class.java)
            NAV_TEACH    -> Intent(this, TeachModeActivity::class.java)
            NAV_FEED     -> Intent(this, NotificationsActivity::class.java)
            NAV_VISION   -> Intent(this, TactoActivity::class.java)
            NAV_DOORBELL -> Intent(this, DoorbellActivity::class.java)
            else -> return
        }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        overridePendingTransition(0, 0)
    }

    private fun applyActiveState(activeTab: Int) {
        val allBtns   = listOf(navBtnHome, navBtnTeach, navBtnFeed, navBtnVision, navBtnDoorbell)
        val allIcons  = listOf(navIconHome, navIconTeach, navIconFeed, navIconVision, navIconDoorbell)
        val allLabels = listOf(navLabelHome, navLabelTeach, navLabelFeed, navLabelVision, navLabelDoorbell)
        val activeColor   = ContextCompat.getColor(this, R.color.accent_primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)

        for (i in allBtns.indices) {
            val isActive = i == activeTab
            val color = if (isActive) activeColor else inactiveColor
            allIcons[i].setColorFilter(color)
            allLabels[i].setTextColor(color)
            allBtns[i].background = null
        }
    }

    protected fun isNotificationAccessGranted(): Boolean {
        val cn = "${packageName}/${TactoLMListenerService::class.java.name}"
        val flat = android.provider.Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(cn)
    }
}
