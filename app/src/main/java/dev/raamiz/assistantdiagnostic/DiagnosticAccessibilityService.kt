package dev.raamiz.assistantdiagnostic

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.os.*
import android.view.accessibility.AccessibilityEvent
import java.util.UUID

/** This is the dispatch service to retain if the listener gate ever passes. */
class DiagnosticAccessibilityService : AccessibilityService() {
    companion object { var instance: DiagnosticAccessibilityService? = null; private set }
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var busy = false
    private var cleanupPending = false
    private var capture: Capture? = null
    private var wake: PowerManager.WakeLock? = null
    private var id = ""
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* No screen inspection. */ }
    override fun onInterrupt() { cancel() }
    override fun onDestroy() { cancel(); instance = null; super.onDestroy() }
    fun micServiceStopped() { if (capture != null) cancel() }
    fun arm(config: Intent) {
        if (busy || cleanupPending) { Trace.status = "Test already active or microphone cleanup pending."; return }
        busy = true
        val token = ++generation
        id = UUID.randomUUID().toString(); Trace.latestId = id
        val attempt = id
        val seconds = config.getIntExtra("seconds", 15).coerceIn(5, 120)
        val mic = config.getBooleanExtra("mic", false)
        Trace.event(this, attempt, "armed", "route" to config.getStringExtra("route").orEmpty(),
            "requested_state" to config.getStringExtra("state").orEmpty(), "mic" to mic,
            "countdown_seconds" to seconds, "mapping_uid" to config.getStringExtra("uid").orEmpty(),
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}", "sdk" to Build.VERSION.SDK_INT,
            "fingerprint" to Build.FINGERPRINT)
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:diagnostic").apply { acquire((seconds + 5L) * 1000) }
        try {
            if (mic) { capture = Capture(this); Trace.event(this, attempt, "capture_started") }
        } catch (e: Throwable) { fail(attempt, "Capture initialization failed: $e"); return }
        handler.postDelayed({
            if (token != generation) return@postDelayed
            val recorder = capture
            if (recorder == null) dispatch(config, attempt)
            else {
                recorder.disable()
                Trace.event(this, attempt, "release_started")
                val deadline = SystemClock.elapsedRealtime() + 2000
                handler.postDelayed({ if (token == generation && busy) fail(attempt, "Microphone handoff failed.") }, 2000)
                Thread({
                    val ok = try { recorder.stopAndJoin() } catch (_: Throwable) { false }
                    handler.post {
                        if (token != generation || !busy) return@post
                        if (!ok || SystemClock.elapsedRealtime() >= deadline) fail(attempt, "Microphone handoff failed.")
                        else {
                            capture = null
                            Trace.event(this, attempt, "capture_released_worker_terminated")
                            dispatch(config, attempt)
                        }
                    }
                }, "microphone-handoff").start()
            }
        }, seconds * 1000L)
    }
    private fun dispatch(config: Intent, attempt: String) {
        if (MainActivity.visible) { fail(attempt, "Activity still foregrounded; dispatch suppressed"); return }
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val observed = if (!locked) "other_app" else if (interactive) "locked_on" else "locked_off"
        Trace.event(this, attempt, "dispatch_started", "observed_state" to observed, "locked" to locked, "interactive" to interactive)
        try {
            when (config.getStringExtra("route")) {
                "VOICE_COMMAND" -> startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "ASSIST" -> startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "KEY_MAPPER" -> {
                    val uid = config.getStringExtra("uid").orEmpty()
                    UUID.fromString(uid)
                    sendBroadcast(Intent("io.github.sds100.keymapper.ACTION_TRIGGER_KEYMAP_BY_UID")
                        .setComponent(ComponentName("io.github.sds100.keymapper", "io.github.sds100.keymapper.api.TriggerKeyMapsBroadcastReceiver"))
                        .putExtra("io.github.sds100.keymapper.EXTRA_KEYMAP_UID", uid))
                }
                else -> error("Unknown route")
            }
            Trace.event(this, attempt, "dispatched", "acknowledgement" to "none; human observation required")
        } catch (e: Exception) { Trace.event(this, attempt, "dispatch_error", "detail" to e.toString()) }
        finishAttempt()
    }
    private fun fail(attempt: String, message: String) {
        Trace.event(this, attempt, "error", "detail" to message)
        cancel()
        Trace.status = message
    }
    fun cancel() {
        ++generation
        val old = capture; capture = null
        old?.disable()
        if (old != null) {
            cleanupPending = true
            Thread {
                try { old.stopAndJoin() } catch (_: Throwable) {}
                handler.post {
                    cleanupPending = !old.released
                    if (cleanupPending) Trace.status = "Microphone handoff failed. Disable service and reopen app before retrying."
                }
            }.start()
        }
        finishAttempt()
    }
    private fun finishAttempt() {
        busy = false
        if (wake?.isHeld == true) wake?.release()
        wake = null
        stopService(Intent(this, DiagnosticMicService::class.java))
    }
}
