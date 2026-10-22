package dev.raamiz.assistantdiagnostic

import android.app.*
import android.content.Intent
import android.os.IBinder

class DiagnosticMicService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("capture", "Diagnostic microphone", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "capture").setContentTitle("Diagnostic microphone test")
            .setContentText("Audio discarded; capture stops before assistant dispatch.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build())
        if (intent == null || DiagnosticAccessibilityService.instance == null) stopSelf()
        else DiagnosticAccessibilityService.instance!!.arm(intent)
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        DiagnosticAccessibilityService.instance?.micServiceStopped()
        super.onDestroy()
    }
}
