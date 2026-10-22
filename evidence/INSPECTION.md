# Diagnostic delivery inspection

Gate status: **PENDING. Listener delivery blocked.** `adb-devices.txt` contains an empty device list. No APK installation, accessibility-service execution, Claude voice interaction, microphone capture/handoff, locked-state behavior, readiness latency or physical-button baseline was observed. Empty `device-attempts.jsonl` and `physical-button-baseline.csv` deliberately contain no invented measurements. `gate-status.txt` reports every cell pending.

## Static APK findings

Supplied file: `key-mapper-4-6-1-foss.apk`.

* Recomputed SHA-256: `CB26FB63378BFAC629721A49E81407CDF541BB4C2159A7BC6279D0B10994565D` (`apk-sha256.txt`).
* Binary manifest/badging: package `io.github.sds100.keymapper`, version name `4.6.1-foss`, version code `286`, minimum SDK 26, target SDK 37 (`keymapper-badging.txt`). This target belongs to the supplied APK, not the diagnostic.
* Binary manifest has `io.github.sds100.keymapper.api.TriggerKeyMapsBroadcastReceiver`, exported=true, action `io.github.sds100.keymapper.ACTION_TRIGGER_KEYMAP_BY_UID`, with no receiver-level permission attribute (`keymapper-manifest.txt`, receiver section). No claim that this executes a mapping follows from that declaration.
* APK signature verification and certificate details are in `keymapper-signature.txt`. Signature validity does not establish provenance from any particular source checkout.

Source comparison: GitHub tree request for tag `v4.6.1` returned commit `45c0a2ce701d329bd15935f8059dd21686260145` (`keymapper-source-tree.json`). Relevant downloaded source is attached with `source-` filenames preserving original paths:

* `api/src/main/AndroidManifest.xml`: exported trigger receiver declaration.
* `api/src/main/java/io/github/sds100/keymapper/api/Api.kt`: action and extra constants. The Kotlin identifier `EXTRA_KEYMAP_ID` contains wire value `io.github.sds100.keymapper.EXTRA_KEYMAP_UID`.
* `api/src/main/java/io/github/sds100/keymapper/api/TriggerKeyMapsBroadcastReceiver.kt`: reads that string extra and launches a coroutine sending `TriggerKeyMapEvent(uid)`. No execution acknowledgement is provided to callers.
* `system/src/main/java/io/github/sds100/keymapper/system/apps/AndroidPackageManagerAdapter.kt`, lines 150â€“176: voice assistant uses `ACTION_VOICE_COMMAND`/NEW_TASK directly; device assistant uses `ACTION_ASSIST`/NEW_TASK via a trampoline. This diagnostic deliberately tests both actions directly from its own accessibility service as requested. Broadcast tests use the existing mapping and thus its actual action behavior.

Manifest/API correspondence with the source tag is a static comparison only. The source files were **not** used to reproduce the supplied APK, and no reproducible-build/provenance claim is made. Current [integration documentation](https://keymapper.app/user-guide/keymaps/) agrees with the explicit component, action and UUID extra used in the diagnostic.

## Diagnostic implementation and checks

Artifact: `../deliverables/assistant-diagnostic-debug.apk`; SHA-256 `70FC270B34AB8642F379EF4F91D6C24C569649188CB5D804583FF1A85EC2802C`.

* Kotlin 2.1.21; AGP 8.10.1; Gradle 8.13; SDK/build-tools 36; JDK 21. Exact tool output is attached in `*-version.txt`.
* Binary manifest confirms minimum SDK 26, target/compile SDK 36, a permission-protected accessibility service and a non-exported microphone foreground service (`diagnostic-manifest.txt`, `diagnostic-badging.txt`). No Internet permission, boot receiver, alarm, restart timer, recognizer or speech model.
* Compiled accessibility XML sets `canRetrieveWindowContent=false` and `canPerformGestures=false` (`diagnostic-accessibility.txt`). Event callbacks discard events. No automated taps, unlock requests or activity trampoline in the diagnostic.
* `DiagnosticAccessibilityService.kt` serializes arming/cancellation/release/dispatch on the main looper. A generation invalidates stale callbacks. Capture is disabled before stop, worker termination and release must both be confirmed before dispatch, and independent two-second deadline failure suppresses dispatch. Capture failures enter an error; cleanup can prevent retry until service restart. One bounded partial wake lock keeps the CPU active during the countdown; this is not a battery/listener trial.
* `Capture.kt` discards audio buffers, owns recorder release on the capture worker and joins that worker off the main thread. No queued recognition callbacks exist in this diagnostic. `DiagnosticMicService.kt` starts capture from an explicitly visible user action and returns START_NOT_STICKY. Reopening after process death requires a new arm action.
* `MainActivity.kt` supplies route, state, countdown, UUID, microphone variant, explicit cancellation, manual external observations and JSONL export. Calls are logged as dispatched without claiming Claude success. Device state telemetry is logged separately from requested state.
* `build.txt`: assembleDebug and lintDebug **BUILD SUCCESSFUL**. `lint-results-debug.txt`: **0 errors, 16 warnings** (English-only diagnostic UI, platform backup/icon suggestions, pinned older tool versions, and a Kotlin SAM heuristic warning on Android `stopService(Intent)`). No lint baseline or global warning suppression was used. These are static checks, not Android instrumentation tests.
* `diagnostic-signature.txt`: debug signing verification passed. `diagnostic-alignment.txt`: `zipalign -c -P 16 -v 4` passed. `diagnostic-native-libraries.txt`: empty list; no `.so` entries in this APK. This establishes APK packaging alignment only. No 16 KB execution environment was tested and no Vosk/transitive native-library audit was attempted because the gate has not passed.
* App dependency locks and `gradle/verification-metadata.xml` record resolved dependency versions and SHA-256 values (initial trust from this build, not independent publisher verification). No Vosk/model artifacts were fetched.
* `gate-tests.txt`: **8/8 Node tests passed**. These check evidence qualification against missing observations/timing, incorrect state, launch return alone, release failure and timeout. They do not exercise Android recorder lifecycle or background launch behavior.

## Commands and reproducibility

Paths below use `$sdk = "$env:LOCALAPPDATA\Android\Sdk"` and `$bt = "$sdk\build-tools\36.0.0"`. Commands were run from the repository root. Outputs are attached verbatim as text; PowerShell native-command stderr may include its diagnostic formatting. The initial sandbox could not initialize Gradle native services or ADB, so those were rerun with permission. Initial offline Gradle resolution lacked javapoet 1.10.0; an online build fetched it. Initial lint errors were fixed before final build.

```powershell
Get-FileHash key-mapper-4-6-1-foss.apk,deliverables/assistant-diagnostic-debug.apk -Algorithm SHA256
& "$bt\aapt2.exe" dump badging key-mapper-4-6-1-foss.apk
& "$bt\aapt2.exe" dump xmltree key-mapper-4-6-1-foss.apk --file AndroidManifest.xml
& "$bt\apksigner.bat" verify --print-certs key-mapper-4-6-1-foss.apk
Invoke-RestMethod https://api.github.com/repos/keymapperorg/KeyMapper/git/trees/v4.6.1?recursive=1
# Attached source files fetched from https://raw.githubusercontent.com/keymapperorg/KeyMapper/v4.6.1/<original-path>
.\gradlew.bat --offline --no-daemon :app:assembleDebug :app:lintDebug --write-locks --write-verification-metadata sha256
& "$bt\aapt2.exe" dump badging deliverables/assistant-diagnostic-debug.apk
& "$bt\aapt2.exe" dump xmltree deliverables/assistant-diagnostic-debug.apk --file AndroidManifest.xml
& "$bt\aapt2.exe" dump xmltree deliverables/assistant-diagnostic-debug.apk --file res/xml/accessibility_service.xml
& "$bt\apksigner.bat" verify --print-certs deliverables/assistant-diagnostic-debug.apk
& "$bt\zipalign.exe" -c -P 16 -v 4 deliverables/assistant-diagnostic-debug.apk
& "$sdk\platform-tools\adb.exe" devices -l
node --test tools/gate-report.test.mjs
node tools/gate-report.mjs evidence/device-attempts.jsonl evidence/physical-button-baseline.csv
```

Use the README procedure to collect actual evidence. Do not mix configurations, devices or reboots in one qualification dataset: monotonic timestamps are boot-specific. Before selecting between qualifying direct routes use balanced cell counts and review baseline, measurement uncertainty and all linked recordings. No total perceived-latency, sub-500 ms handoff, power, memory or false-activation claim is supported at this stage.

