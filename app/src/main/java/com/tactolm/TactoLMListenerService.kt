package com.tactolm

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.tactolm.haptics.LRADispatcher
import com.tactolm.haptics.TactonLibrary
import com.tactolm.pipeline.FastClassifier
import com.tactolm.pipeline.NotificationStore
import com.tactolm.pipeline.NotificationStore.TactoNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TactoLMListenerService — the core sensory pipeline entry point.
 *
 * Lifecycle:
 *  - System binds this service once the user grants Notification Access.
 *  - [onNotificationPosted] fires for every new notification on the device.
 *  - FastClassifier determines the urgency tier in ~1–5ms.
 *  - LRADispatcher fires the corresponding tacton immediately.
 *  - The notification is stored in NotificationStore and broadcast to the UI.
 *
 * MIUI NOTE: The service may be killed by MIUI's aggressive background-app
 * manager. The manifest declares it with the highest priority and adds the
 * autoStart meta-data to mitigate this, but the user may also need to enable
 * "Autostart" for TactoLM in MIUI Settings → Battery → Manage apps.
 */
class TactoLMListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "TactoLMListener"
        const val ACTION_NOTIFICATION_POSTED = "com.tactolm.NOTIFICATION_POSTED"

        // Packages to ignore — system noise that adds no value
        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.miui.securitycenter",
            "com.miui.daemon",
            "com.xiaomi.mircs"
        )

        private val TIME_FORMAT = SimpleDateFormat("hh:mm a", Locale.getDefault())

        /** Symbol icon map for common apps — uses letter initials, no emojis. */
        private val ICON_MAP = mapOf(
            "com.whatsapp"             to "W",
            "com.google.android.gm"   to "M",
            "com.google.android.apps.messaging" to "M",
            "com.phonepe.app"         to "P",
            "in.amazon.mShop.android.shopping" to "A",
            "com.swiggy.android"      to "S",
            "com.zomato.android"      to "Z",
            "com.ola.client"          to "O",
            "com.rapido.passenger"    to "R",
            "com.paytm.android"       to "P",
            "com.google.android.youtube" to "Y",
            "com.instagram.android"   to "I",
            "com.twitter.android"     to "X",
            "com.linkedin.android"    to "L",
            "com.bmtc"                to "B",
            "in.gov.uidai.mAadhaarPlus" to "A",
            "com.google.android.apps.maps" to "G"
        )
    }

    private lateinit var dispatcher: LRADispatcher

    override fun onCreate() {
        super.onCreate()
        dispatcher = LRADispatcher(this)
        Log.i(TAG, "TactoLM Listener Service started")
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val pkg = sbn.packageName ?: return

        // Ignore system & noisy packages
        if (IGNORED_PACKAGES.contains(pkg)) return

        // ── APP FILTER LOGIC ──────────────────────────────────────────────
        val prefs = getSharedPreferences("TactoLM_Prefs", Context.MODE_PRIVATE)
        val selectedApps = prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
        
        // If user hasn't selected any apps, we monitor NO apps (per user request)
        if (selectedApps.isEmpty()) return
        
        // If this app is not in the selected list, ignore it
        if (!selectedApps.contains(pkg)) return

        // Ignore grouped summary notifications (contain child notifications)
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras ?: return
        val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        val body    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val appName = getAppName(pkg)

        // Skip empty notifications
        if (title.isBlank()) return

        Log.d(TAG, "[$pkg] $title — $body")

        // ── Fast classification (runs on binder thread, ~1–5ms) ──────────────
        val result = FastClassifier.classify(title, body, appName)

        // ── Dispatch haptic immediately ────────────────────────────────────
        dispatcher.dispatch(result.tacton)
        Log.i(TAG, "Dispatched: ${result.tacton.id} for [$appName] $title")

        // ── Build UI model ────────────────────────────────────────────────
        val (tierColorRes, rowBgRes) = tierResources(result.tier)
        val tactoNotif = TactoNotification(
            id          = "$pkg-${sbn.postTime}",
            appName     = appName,
            appPackage  = pkg,
            appIcon     = ICON_MAP[pkg] ?: appIconFallback(appName),
            title       = title,
            body        = body,
            tier        = result.tier,
            tierColorRes = tierColorRes,
            tactonId    = result.tacton.id,
            rowBgRes    = rowBgRes,
            timestamp   = sbn.postTime,
            timeLabel   = TIME_FORMAT.format(Date(sbn.postTime))
        )

        // ── Store + broadcast ─────────────────────────────────────────────
        NotificationStore.add(tactoNotif)

        sendBroadcast(Intent(ACTION_NOTIFICATION_POSTED).apply {
            `package` = applicationContext.packageName
        })
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Currently we keep the feed as a history — no removal from store
    }

    override fun onDestroy() {
        super.onDestroy()
        dispatcher.cancel()
        Log.i(TAG, "TactoLM Listener Service destroyed")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun getAppName(packageName: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    } catch (e: Exception) {
        packageName.substringAfterLast(".")
    }

    private fun appIconFallback(appName: String): String {
        val lower = appName.lowercase()
        return when {
            lower.contains("bank") || lower.contains("pay") -> "$"
            lower.contains("food") || lower.contains("eat") -> "~"
            lower.contains("map")  || lower.contains("nav") -> ">"
            lower.contains("health")|| lower.contains("med") -> "+"
            lower.contains("news")                          -> "#"
            lower.contains("mail") || lower.contains("email")-> "@"
            lower.contains("shop") || lower.contains("order")-> "*"
            else -> appName.take(1).uppercase()
        }
    }

    private fun tierResources(tier: String): Pair<Int, Int> = when (tier) {
        "EMERGENCY"  -> R.color.tier_critical      to R.drawable.bg_tacton_row_critical
        "HEALTH"     -> R.color.tier_health         to R.drawable.bg_tacton_row_health
        "TRANSIT"    -> R.color.tier_informational  to R.drawable.bg_tacton_row_info
        "NAVIGATION" -> R.color.tier_social         to R.drawable.bg_tacton_row_social
        "SYSTEM"     -> R.color.accent_secondary    to R.drawable.bg_tacton_row_system
        "PROCESSING" -> R.color.accent_secondary    to R.drawable.bg_tacton_row_system
        else         -> R.color.accent_primary      to R.drawable.bg_tacton_row_system
    }
}
