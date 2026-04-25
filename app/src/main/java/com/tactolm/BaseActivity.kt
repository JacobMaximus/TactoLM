package com.tactolm

import android.content.Intent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * BaseActivity — all screens extend this to get the shared floating nav bar.
 *
 * After calling setContentView(), subclasses should call setupNavBar(currentTab)
 * where currentTab is one of NAV_TEACH, NAV_FEED, NAV_VISION, NAV_DOORBELL.
 */
abstract class BaseActivity : AppCompatActivity() {

    companion object {
        const val NAV_HOME     = 0
        const val NAV_TEACH    = 1
        const val NAV_FEED     = 2
        const val NAV_VISION   = 3
        const val NAV_DOORBELL = 4
    }

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

    protected fun setupNavBar(activeTab: Int) {
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
            }
        }
        navBtnTeach.setOnClickListener {
            if (activeTab != NAV_TEACH) {
                startActivity(Intent(this, TeachModeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
        navBtnFeed.setOnClickListener {
            if (activeTab != NAV_FEED) {
                startActivity(Intent(this, NotificationsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
        navBtnVision.setOnClickListener {
            Toast.makeText(this, "Vision: Not implemented yet", Toast.LENGTH_SHORT).show()
        }
        navBtnDoorbell.setOnClickListener {
            Toast.makeText(this, "Door Bell: Not implemented yet", Toast.LENGTH_SHORT).show()
        }
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
            allBtns[i].background = null // no background, color only
        }
    }
}
