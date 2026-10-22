# Project handover

Last updated: 22 October 2026.

## Start here

Repository: https://github.com/Killmonger-death/Personal-Project

Branch: `main`. Diagnostic implementation commit: `7906e58c25373a9910f788be4fe67416fed6bcaa`.

The goal is an Android voice-trigger app: saying **Hey Mapper** should open Claude's intended voice experience through a proven assistant route. The current delivery is deliberately **diagnostic only**. The physical-device gate is **PENDING** because no phone was connected during development. Do not build or deliver the full listener until a route passes the gate below.

Read [README.md](README.md) for the device procedure and [evidence/INSPECTION.md](evidence/INSPECTION.md) for the inspection report, source comparison, commands and limitations. Existing evidence is a historical snapshot; append new device evidence separately rather than presenting these files as fresh results.

## What is implemented

- Kotlin diagnostic app, minimum SDK 26, target/compile SDK 36.
- Separate `ACTION_VOICE_COMMAND`, `ACTION_ASSIST` and explicit Key Mapper broadcast tests.
- User-armed countdown (default 15 seconds, adjustable 5 to 120) so the user can switch apps or lock the phone before dispatch.
- Dispatch from `DiagnosticAccessibilityService`, which is intended to remain the dispatch service in the finished app. Screen-content retrieval and gesture capability are disabled; event callbacks discard events.
- Optional diagnostic microphone capture during the countdown. Audio is discarded in memory. Capture is disabled, stopped, released and its worker joined before dispatch. A two-second handoff deadline suppresses dispatch on timeout/failure and reports `Microphone handoff failed.` Cleanup is best-effort and can block retries until service restart.
- Main-looper state control and generation checks to invalidate stale callbacks; a bounded partial wake lock for each short test.
- A non-exported microphone foreground service started by a visible user action, with START_NOT_STICKY. No boot restart, automatic rearming, speech recognition, Vosk model or always-on listener.
- Manual observation fields and JSONL export. Requests are recorded as **dispatched**, never as proof of Claude success.
- Node evidence-report tool with eight safety tests.

## Files to know

| Path | Purpose |
| --- | --- |
| `deliverables/assistant-diagnostic-debug.apk` | Ready-to-install diagnostic APK |
| `deliverables/diagnostic-evidence.zip` | Original inspection evidence and README snapshot |
| `app/src/main/java/dev/raamiz/assistantdiagnostic/MainActivity.kt` | Test configuration, observations and export UI |
| `app/src/main/java/dev/raamiz/assistantdiagnostic/DiagnosticAccessibilityService.kt` | Countdown, cancellation, handoff deadline and dispatch |
| `app/src/main/java/dev/raamiz/assistantdiagnostic/Capture.kt` | Recorder worker, stop, join and release |
| `app/src/main/java/dev/raamiz/assistantdiagnostic/DiagnosticMicService.kt` | Diagnostic microphone foreground notification/lifecycle |
| `app/src/main/java/dev/raamiz/assistantdiagnostic/Trace.kt` | App-private JSONL event log and monotonic timestamps |
| `tools/gate-report.mjs` | Qualification summaries and baseline summaries |
| `tools/gate-report.test.mjs` | Tests for rejecting incomplete/failed evidence |
| `evidence/` | Raw tool output, APK manifests, source snapshots and empty measurement templates |
| `gradle/verification-metadata.xml`, `app/gradle.lockfile` | Recorded dependency hashes and versions |
| `key-mapper-4-6-1-foss.apk` | Original supplied Key Mapper APK |

## Resume on another computer

Install Git, JDK 21, Android SDK platform 36, build-tools 36.0.0, platform-tools/ADB, and Node.js 18 or newer. Gradle 8.13 is provided through the wrapper; AGP is 8.10.1 and the app Kotlin plugin is 2.1.21. The first build on a fresh computer needs network access for Gradle and dependencies; do not start with `--offline`.

```sh
git clone https://github.com/Killmonger-death/Personal-Project.git
cd Personal-Project
```

Set `ANDROID_HOME` to that computer's SDK directory or create an untracked `local.properties` containing `sdk.dir=<local SDK path>`. In a Windows properties file, escape the drive colon and use forward slashes, for example `sdk.dir=C\:/Users/YOUR_USER/AppData/Local/Android/Sdk`. Do not copy the old workstation's paths.

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
node --test tools/gate-report.test.mjs
adb devices -l
adb install -r deliverables/assistant-diagnostic-debug.apk
```

On macOS/Linux use `sh ./gradlew :app:assembleDebug :app:lintDebug`; the Node and ADB commands are the same. If testing a newly rebuilt APK, install `app/build/outputs/apk/debug/app-debug.apk` instead of the committed delivery APK.

The debug signing key was not committed. A rebuild on another computer usually has a different certificate and cannot update the existing installation. **Export diagnostic logs before uninstalling**, since uninstalling deletes app-private data. You can continue using the committed APK on another phone without rebuilding. Enable the diagnostic accessibility service and grant microphone permission on each phone; these settings do not transfer through Git. The Key Mapper mapping UUID, Claude configuration and recordings also need to be supplied on the testing device.

The previous Git author identity was configured locally using the repository owner's GitHub noreply address. Local Git configuration and credentials do not transfer with a clone; configure your own author and authenticate before pushing.

## Verified at handover

- Android debug build and lint passed: **0 errors, 16 warnings**. Warnings are described in the inspection report.
- APK signature verification and `zipalign -c -P 16 -v 4` passed.
- Node evidence-tool tests: **8 passed**.
- Diagnostic APK SHA-256: `70FC270B34AB8642F379EF4F91D6C24C569649188CB5D804583FF1A85EC2802C`.
- Supplied Key Mapper APK SHA-256: `CB26FB63378BFAC629721A49E81407CDF541BB4C2159A7BC6279D0B10994565D`.
- Binary Key Mapper manifest reports package `io.github.sds100.keymapper`, version `4.6.1-foss`, code 286, minimum SDK 26 and target SDK 37. Its exported trigger receiver is attached in the evidence.
- Source comparison used tag `v4.6.1`, commit `45c0a2ce701d329bd15935f8059dd21686260145`. Matching source declarations does **not** prove the supplied APK was built from that source.
- No native libraries are packaged in the diagnostic. Packaging alignment does **not** prove execution on a 16 KB device or Vosk compatibility.

No Android runtime behavior was verified: no installation, accessibility-service execution, microphone handoff, background/locked launch, Claude interaction, latency, battery, memory or false-activation measurements. The Node tests exercise the report tool, not the Android controller.

## Immediate next step: physical-device gate

1. Connect the actual phone, confirm ADB authorization, install the diagnostic and follow the README. Capture phone model/build, Claude and Key Mapper versions, assistant defaults, security/battery settings and recording timing uncertainty.
2. Verify the existing physical-button baseline in all three states: another app foregrounded, locked with display on, locked with display off. Record readiness latency using synchronized external recordings.
3. Test `VOICE_COMMAND` and `ASSIST` independently in all three states, each without capture and after diagnostic microphone release: six cells per route. Obtain **10 consecutive successes per cell**. A failure or missing observation breaks the current streak.
4. Success requires Claude entering the intended voice experience, hearing a **newly spoken** phrase and responding appropriately with **no extra tap or unlock**. A visible Claude screen, returned launch call or broadcast dispatch is insufficient.
5. Prefer a direct route passing all six cells. If both qualify, use the lower observed p95 readiness latency, with balanced cell counts and comparison to the physical-button baseline.
6. If neither direct route qualifies, test the existing Key Mapper mapping through the explicit broadcast in the same matrix. Enable its external-trigger option and copy its UUID first. If only that route qualifies, retain it as the assistant bridge.
7. If no route qualifies, or physical testing remains unavailable, stop at this diagnostic and report the failed/pending gate.

Key Mapper integration values:

```text
Component: io.github.sds100.keymapper/io.github.sds100.keymapper.api.TriggerKeyMapsBroadcastReceiver
Action:    io.github.sds100.keymapper.ACTION_TRIGGER_KEYMAP_BY_UID
Extra:     io.github.sds100.keymapper.EXTRA_KEYMAP_UID
Value:     UUID copied from the existing mapping
```

After exporting observations:

```sh
node tools/gate-report.mjs diagnostic-attempts.jsonl evidence/physical-button-baseline.csv
```

Review the linked recordings alongside the report. It cannot independently establish whether Claude heard/responded, which unlocked app was foregrounded, or whether a measurement was synchronized. Do not combine different devices, configurations or reboots into one qualification dataset. App monotonic timestamps are boot-specific. Preserve pending/failed attempts and report timing uncertainty separately. Baseline CSV expects simple comma-free recording filenames.

## Requirements retained for work after a passed gate

Only proceed once physical evidence qualifies a route. These requirements are not implemented yet:

- Compose UI, editable phrase/action list, listening controls and microphone foreground listener service. Support `LaunchDefaultAssistant` and `TriggerKeyMapper(mappingUid)`.
- One serialized controller: `Stopped -> Listening -> Releasing -> AssistantPaused -> Listening`. Atomically disable acceptance and invalidate queued callbacks, stop/unblock capture, confirm worker termination and recorder release, then dispatch. Enforce a two-second handoff deadline; on timeout/release failure suppress dispatch, clean up best-effort and show **Microphone handoff failed.**
- Keep the speech model and foreground service alive while paused. Default resumption is 30 seconds, adjustable, with **Pause until manually resumed**. Reset recognition and create a fresh capture session before accepting commands again. A timer is not evidence that Claude finished; test microphone contention and silenced input. Capture-init failure must produce an actionable error without repeated automatic restarts.
- After process death require a permitted user-initiated restart; reopening must show listening stopped. No background timer may recreate microphone access.
- Default phrase **Hey Mapper**, immediately dispatching the assistant. Normalize case, punctuation and repeated whitespace; match complete word sequences only. Accept an exact normalized partial unchanged for 200 ms, or an exact final earlier. Atomically consume utterance IDs so partial/final callbacks cannot dispatch twice. Apply a two-second cooldown and invalidate callbacks from old capture sessions. Reject duplicate phrases and token-prefix conflicts across all immediately dispatched phrases.
- Pin `com.alphacephei:vosk-android:0.3.75` and `vosk-model-small-en-us-0.15`, lock transitive dependencies, record hashes and bundle applicable engine/model/dependency licenses. Before integration audit **every** packaged native library, including transitives, for ARM64 availability, ELF alignment, APK alignment and execution on a **16 KB page-size environment**. Incompatibility blocks listener delivery; report the dependency before selecting a replacement. Measure memory rather than assuming a small footprint; the original plan notes Vosk's approximately 300 MB typical small-model runtime estimate, which must not be presented as a measurement of this app.
- For **30 attempts per device state**, record spoken-phrase end to recognition acceptance, acceptance to dispatch including complete release, and dispatch to Claude speech readiness. Use app monotonic events plus synchronized observed-device recordings. Report median, p95, failures and uncertainty separately; preserve signed phrase-end offsets when partial acceptance precedes phrase end. Target p95 **below 500 ms for acceptance to dispatch**, and report total perceived latency separately.
- Test unrelated speech, near matches, TV audio, duplicate callbacks, delayed shutdown, release failure, long Claude conversations, manual pause, timed resume, process death and revoked permissions. Run a one-hour screen-off trial and matched listening-disabled baseline; report false activations/hour, missed triggers, memory, CPU and battery change.
- Deliver listener source/APK, licenses, setup and measured acceptance report only after the gate passes. Refresh relevant Android/Vosk documentation before implementation; do not interpret this handover as current platform verification.

## Suggested prompt for the next session

> Read handover.md, README.md and evidence/INSPECTION.md. Resume this diagnostic-first Android project from main. The physical-device gate is pending, and the listener must remain blocked until a route qualifies. Check the current workspace and connected device, then continue collecting or reviewing the diagnostic evidence. Keep static build findings separate from observed phone behavior.
