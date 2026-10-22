# Assistant route diagnostic

This delivery stops at the diagnostic gate. No connected physical device was available. No route is qualified and no listener, Vosk model, or always-on microphone service is delivered.

## Build and install

Use JDK 21, Android SDK platform 36 and build-tools 36.0.0. Set `sdk.dir` in untracked `local.properties`, or set `ANDROID_HOME`. Run:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
adb install -r deliverables/assistant-diagnostic-debug.apk
```

The debug APK uses a development signing key; its certificate fingerprint is recorded in `evidence/diagnostic-signature.txt`. The key is not distributed. Rebuilds on another machine have a different certificate and require uninstalling this diagnostic first. No Internet permission is requested. Audio is discarded in memory, never saved. Logs remain in app-private storage until explicitly exported through Android's document picker. Logs may contain your mapping UUID and typed notes.

## Physical-device gate procedure

1. Configure Claude and confirm the existing physical-button mapping actually opens voice, hears a fresh phrase and responds without any extra interaction. Record phone model, OS build, Claude version, Key Mapper version, assistant defaults, lock/security settings, battery restrictions and external-recording frame rate in your test notes. Record the physical-button baseline in each state in `evidence/physical-button-baseline.csv`.
2. Open this app. Enable **Assistant route diagnostic** in Accessibility settings. For a sideloaded app, Android may require enabling restricted settings in its app information before accessibility can be enabled. This service cannot retrieve screen content or perform gestures. Disable it after testing.
3. Select `VOICE_COMMAND` and a state. Arm a 15-second (adjustable 5–120 seconds) countdown. Switch to another real app, or lock with the display on/off as selected. The activity must be in the background at dispatch; otherwise dispatch is suppressed. For locked-on tests, set a long enough display timeout. The partial wake lock keeps the CPU awake only during this short test and never turns the display on or unlocks.
4. Observe Claude without tapping or unlocking. Speak a new, identifiable test phrase. Success requires entry into intended voice mode, hearing that phrase and an appropriate response. A visible screen, chooser, returned call, or Key Mapper broadcast is insufficient. Record a failure if any additional interaction is needed.
5. Return only after the observation. Enter success/failure, observed dispatch-to-ready latency, measurement uncertainty, recording filename, spoken phrase, response, and any intervention. Save the observation against the displayed latest attempt ID. Export JSONL regularly. Observations can be corrected by saving again; the report uses the last observation. After process death logs persist but the in-memory latest ID does not; annotate interrupted attempts externally, or leave them pending. Nothing automatically rearms after process death.
6. Repeat with microphone capture enabled (grant permission first). Capture begins while the activity is visible, with a microphone foreground notification. At countdown expiry the service disables capture, stops the recorder, waits for worker termination and confirmed release, then dispatches from the accessibility service. A two-second timeout or any release failure suppresses dispatch and records **Microphone handoff failed.** Cleanup runs best-effort. If cleanup cannot confirm release, disable the service and reopen the app before retrying.
7. Obtain **10 consecutive successes in each of six cells**: other app foregrounded, locked/display on, locked/display off, each without and with microphone release. Test `ASSIST` independently in the same matrix. Any failure or missing evidence breaks the current success streak. Prefer a direct route passing all six cells; if both pass, compare observed p95 readiness times with the same balanced attempt counts and baseline. Do not combine cells from different phones/configurations to qualify one route.
8. Only if neither direct route qualifies, enable **Allow other apps to trigger this key map** on the existing working Key Mapper mapping, copy its UUID, select `KEY_MAPPER`, and run the same matrix. Uses an explicit component, documented action and string extra; requests are logged as **dispatched**, with no execution acknowledgement.

Export to a workstation and run with Node.js 18 or newer:

```powershell
node tools/gate-report.mjs diagnostic-attempts.jsonl evidence/physical-button-baseline.csv
node --test tools/gate-report.test.mjs
```

Times must come from synchronized external device recordings (include visible device monotonic clock/logcat synchronization markers). Use `dispatch_started` as the common origin. Record frame/clock synchronization uncertainty; don't infer readiness from when you return to this app. App events use `elapsedRealtimeNanos` plus wall time for correlation. Unlocked `other_app` telemetry proves only unlocked/display state and that this activity was paused; the recording must prove another app was foregrounded. CSV baseline uses the physical button activation as its timing origin. Report baseline and route medians/p95 and their differences; the script prints summaries side by side. Empty logs remain pending, not a pass.

## Scope after the gate

The original plan's Compose UI, phrase rules, Vosk pins/model/licenses/native compatibility checks, 30-attempt acceptance timing, fault cases, one-hour screen-off/battery trial, and matched disabled baseline remain blocked. Before listener integration, audit every native dependency and test execution in a 16 KB page-size environment. The absence of native libraries in this diagnostic does not validate Vosk. Do not interpret a successful build or lint result as a device result.

Source references: [Key Mapper integration](https://keymapper.app/user-guide/keymaps/), [Android intents](https://developer.android.com/reference/android/content/Intent), [audio sharing](https://developer.android.com/media/platform/sharing-audio-input), [16 KB pages](https://developer.android.com/guide/practices/page-sizes).
