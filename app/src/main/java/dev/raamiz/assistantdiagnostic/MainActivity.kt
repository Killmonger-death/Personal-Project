package dev.raamiz.assistantdiagnostic

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    companion object { var visible = false; private set }
    private lateinit var status: TextView
    override fun onResume() { super.onResume(); visible = true; if (::status.isInitialized) status.text = Trace.status }
    override fun onPause() { visible = false; super.onPause() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        setContentView(ScrollView(this).apply { addView(body) })
        fun text(value: String) { body.addView(TextView(this).apply { text = value }) }
        fun button(label: String, action: () -> Unit) { body.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
        fun field(hintText: String, initial: String = ""): EditText = EditText(this).apply { hint = hintText; setText(initial); body.addView(this) }
        fun choices(values: List<String>): Spinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values); body.addView(this) }
        text("Assistant route diagnostic — gate PENDING")
        text("Enable the diagnostic accessibility service. Select a route and state, arm, then switch to another app or lock the phone. No automatic taps, unlock, or screen-content reading. Audio is discarded. A dispatched call is NOT success.")
        button("Accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button("Grant microphone permission") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        val route = choices(listOf("VOICE_COMMAND", "ASSIST", "KEY_MAPPER"))
        val target = choices(listOf("other_app", "locked_on", "locked_off"))
        val uid = field("Key Mapper mapping UUID")
        val seconds = field("Countdown seconds (5–120)", "15").apply { inputType = 2 }
        val mic = CheckBox(this).apply { text = "Capture microphone during countdown, then release"; body.addView(this) }
        status = TextView(this).also { body.addView(it) }
        button("Arm one test") {
            val service = DiagnosticAccessibilityService.instance
            when {
                service == null -> status.text = "Enable accessibility service first."
                mic.isChecked && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> status.text = "Grant microphone permission first."
                route.selectedItem == "KEY_MAPPER" && !validUid(uid.text.toString()) -> status.text = "Enter the mapping UUID from Key Mapper."
                else -> {
                    val config = Intent(this, DiagnosticMicService::class.java)
                        .putExtra("route", route.selectedItem.toString()).putExtra("state", target.selectedItem.toString())
                        .putExtra("uid", uid.text.toString()).putExtra("seconds", seconds.text.toString().toIntOrNull() ?: 15)
                        .putExtra("mic", mic.isChecked)
                    if (mic.isChecked) startForegroundService(config) else service.arm(config)
                    status.text = "Armed. Switch apps or lock now; countdown continues in accessibility service."
                }
            }
        }
        button("Cancel pending test") { DiagnosticAccessibilityService.instance?.cancel(); status.text = "Cancelled" }
        text("After each attempt, observe whether Claude heard a NEW phrase and responded without another tap or unlock. Return here only afterward. Times below come from synchronized external recording, relative to dispatch_started; do not use return-to-app time.")
        val outcome = choices(listOf("pending", "success", "failure"))
        val ready = field("Observed ready latency ms (dispatch → ready)")
        val uncertainty = field("Recording timing uncertainty ms")
        val notes = field("Recording filename, test phrase, response, interventions, device state confirmation")
        button("Save observation for latest attempt") {
            if (Trace.latestId.isEmpty()) { status.text = "No attempt in this process."; return@button }
            Trace.event(this, Trace.latestId, "observation", "outcome" to outcome.selectedItem.toString(),
                "ready_ms" to ready.text.toString(), "uncertainty_ms" to uncertainty.text.toString(), "notes" to notes.text.toString())
            status.text = "Observation saved for ${Trace.latestId}"
        }
        button("Export raw JSONL log") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/x-ndjson").putExtra(Intent.EXTRA_TITLE, "diagnostic-attempts.jsonl"), 2) }
        button("Refresh status") { status.text = Trace.status }
    }
    private fun validUid(value: String) = try { java.util.UUID.fromString(value); true } catch (_: Exception) { false }
    @Deprecated("Platform callback") override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2 && resultCode == RESULT_OK) data?.data?.let { uri ->
            try { contentResolver.openOutputStream(uri)?.use { output -> openFileInput("attempts.jsonl").use { it.copyTo(output) } }; status.text = "Exported" }
            catch (e: Exception) { status.text = "Export failed: $e" }
        }
    }
}
