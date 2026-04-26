package com.tactolm.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tactolm.R
import com.tactolm.haptics.LRADispatcher

class AudioRecognitionService : Service() {

    private lateinit var audioRecognitionManager: AudioRecognitionManager
    private lateinit var dispatcher: LRADispatcher
    private val CHANNEL_ID = "AudioRecognitionChannel"
    private val NOTIFICATION_ID = 2

    companion object {
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        dispatcher = LRADispatcher(this)
        audioRecognitionManager = AudioRecognitionManager(this, dispatcher)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Doorbell Detection Active")
            .setContentText("Listening for doorbells...")
            .setSmallIcon(R.drawable.ic_nav_doorbell) // Using standard Android icon as fallback
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start listening
        audioRecognitionManager.startListening()

        // Return START_STICKY so the system restarts it if it's killed due to memory pressure
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        audioRecognitionManager.stopListening()
        dispatcher.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't provide binding
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "TactoLM Background Listening",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
