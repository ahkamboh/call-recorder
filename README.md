# Call Recorder (personal, no root, no Play Store)

Records cellular phone calls to `Music/CallRecordings/<number>/Call_<date>_<time>_<in|out>_<number>.m4a`.
No beep, no announcement, no cloud. Android 12+. Verified on a Pixel 7 running Android 17.

## What the app does (v2)

- **On/off switch** on the home screen; off means calls are ignored entirely.
- **One folder per number**, named from the call log, with the contact name when the call log has it.
- **Number rules** (Settings): record all numbers, only a list, or all except a list. Numbers are added from contacts, from recent calls (multi-select), or typed. The caller's number arrives in the phone-state broadcast before the call is answered, so excluded numbers are never recorded; if the number is only known after the call, the file is deleted when the rule says so.
- **Audio source** switch (voice recognition / voice communication / microphone) for phones where the other side is silent.
- **Tap to play** inside the app; long-press to multi-select folders or files.
- **Share** one or many recordings to WhatsApp, Gmail, Drive, etc. (shared through a FileProvider so the real file name is kept).
- **Copy to folder** for the selection, keeping one sub-folder per number. Android's picker does not allow the storage root or Downloads itself, so pick or create a sub-folder.
- **Delete** with confirmation. **Open in Files** opens the folder in the system Files app.
- The list refreshes live when a recording lands.

## How it gets the audio without root

Since Android 10 the microphone is silenced for ordinary apps during a phone call.
An app with an **enabled accessibility service** is exempt when it records with the
`VOICE_RECOGNITION` source. That is the whole trick (it is what Cube ACR's "helper" does,
and why Google banned it from the Play Store, not from Android). The service never reads
the screen; it only keeps the app alive and lets it use the mic during calls.

Whether the **other party** is in the recording depends on the phone's audio chip/firmware:

| Phone family | Typical result |
|---|---|
| Samsung, Xiaomi, OnePlus, Oppo, Vivo, Realme, Infinix, Tecno | both sides, usually fine |
| Google Pixel | only your voice; both sides only on speakerphone |

If the other side is silent, open the app and switch the audio source; test one call per source.

## Install (one time)

1. Copy `CallRecorder.apk` to the phone and open it. Allow "install unknown apps" for the file manager / browser if asked. Play Protect may warn because the app is unsigned by a store; choose Install anyway.
2. Open Call Recorder → **Grant permissions** → allow Microphone, Phone, Call log, Notifications.
3. **Open accessibility settings** → Downloaded apps → Call Recorder → On.
   - If the switch is greyed out ("Restricted setting"): Settings › Apps › Call Recorder › ⋮ (top-right) › **Allow restricted settings**, then try again. Android 13+ does this for every sideloaded app. Installing with `adb install` skips the lock.
4. Make a call. A "Recording call" notification shows while it runs. Files appear in the app list and in any Files/music app under Music/CallRecordings.

Permissions: Microphone and Phone are required. Call log is only used to put the number in the file name. Notifications only show the "recording" status.

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (signed with the debug key, fine for sideloading).

## Test on the emulator

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
for p in RECORD_AUDIO READ_PHONE_STATE READ_CALL_LOG POST_NOTIFICATIONS; do adb shell pm grant com.ahkamboh.callrecorder android.permission.$p; done
adb shell settings put secure enabled_accessibility_services com.ahkamboh.callrecorder/com.ahkamboh.callrecorder.CallRecorderService
adb shell settings put secure accessibility_enabled 1
adb emu gsm call 5551234; sleep 3; adb emu gsm accept 5551234; sleep 8; adb emu gsm cancel 5551234
adb logcat -d -s CallRecorder
adb shell ls -la /sdcard/Music/CallRecordings/
```

## Files

- `CallRecorderService.kt` – the accessibility service: watches call state (telephony off-hook, audio mode as backup), reads the number from the phone-state broadcast, applies the rules, starts/stops the recorder, files the recording from the call log.
- `Recorder.kt` – MediaRecorder → AAC/M4A straight into MediaStore; starts in the base folder, moved into `<number>/` after the call; tries the chosen source, then the others.
- `Rules.kt` – record-all / only-list / except-list, stored as JSON in SharedPreferences, matched with `PhoneNumberUtils.compare` so +92 300… and 0300… are the same number.
- `Repo.kt` – MediaStore listing grouped by folder, call-log names, delete, copy via SAF, share intents, open-in-Files.
- `MainActivity.kt` – Compose UI (Material 3, dynamic colour): folders, files, selection bar, settings.
