package dev.raamiz.assistantdiagnostic

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject

object Trace {
    var latestId = ""
    var status = "Stopped. Nothing resumes automatically."
    @Synchronized fun event(context: Context, id: String, event: String, vararg fields: Pair<String, Any>) {
        val json = JSONObject().put("id", id).put("event", event)
            .put("elapsed_ns", SystemClock.elapsedRealtimeNanos()).put("wall_ms", System.currentTimeMillis())
        fields.forEach { json.put(it.first, it.second) }
        context.openFileOutput("attempts.jsonl", Context.MODE_APPEND).bufferedWriter().use { it.appendLine(json.toString()) }
        status = "$event ($id)"
    }
}
