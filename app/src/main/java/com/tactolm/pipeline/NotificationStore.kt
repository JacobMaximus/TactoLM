package com.tactolm.pipeline

import java.util.concurrent.CopyOnWriteArrayList

/**
 * NotificationStore — thread-safe in-memory store for live notifications.
 *
 * Holds the last [MAX_SIZE] notifications. New items are prepended (index 0
 * = most recent). Observers are notified via [OnChangeListener].
 */
object NotificationStore {

    const val MAX_SIZE = 50

    data class TactoNotification(
        val id: String,           // Unique key: "${packageName}-${postTime}"
        val appName: String,
        val appPackage: String,
        val appIcon: String,      // Emoji fallback icon
        val title: String,
        val body: String,
        val tier: String,
        val tierColorRes: Int,
        val tactonId: String,
        val rowBgRes: Int,
        val timestamp: Long,
        val timeLabel: String
    )

    private val _notifications = CopyOnWriteArrayList<TactoNotification>()

    val notifications: List<TactoNotification>
        get() = _notifications.toList()

    private val listeners = CopyOnWriteArrayList<OnChangeListener>()

    fun interface OnChangeListener {
        fun onChanged(notification: TactoNotification)
    }

    fun addListener(l: OnChangeListener) = listeners.add(l)
    fun removeListener(l: OnChangeListener) = listeners.remove(l)

    /**
     * Prepend a new notification. Trims the list to [MAX_SIZE].
     * Notifies all registered listeners on the calling thread.
     */
    fun add(n: TactoNotification) {
        _notifications.add(0, n)
        if (_notifications.size > MAX_SIZE) {
            _notifications.removeAt(_notifications.size - 1)
        }
        listeners.forEach { it.onChanged(n) }
    }

    fun clear() {
        _notifications.clear()
    }
}
