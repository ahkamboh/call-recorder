# Install guide

This guide takes a phone from nothing to recording calls, in about 3 minutes.

**You need:** an Android phone on Android 12 or newer, and the `CallRecorder.apk` file.

**Red box + arrow** means tap there. **Numbered badges** are explained in the list under each picture.

> Screenshots are from an Android emulator that shows the same screens as a Pixel. Wording can differ slightly on your phone; the steps are the same.

**Contents**

1. [Install the app](#part-1-install-the-app)
2. [Set up the app (one time)](#part-2-set-up-the-app-one-time)
3. [Use the app](#part-3-use-the-app)
4. [Updating to a new version](#updating-to-a-new-version)
5. [Problems and fixes](#problems-and-fixes)

---

## Part 1: Install the app

Because this is a personal build and not on the Play Store, the phone shows a few extra confirmation screens the first time. They are normal for any app you install yourself.

### Step 1. Get the APK onto the phone and open it

Options:

- Open the [latest release](https://github.com/ahkamboh/call-recorder/releases/latest) in Chrome, tap **CallRecorder.apk** under **Assets**, then tap **Open** when it finishes.
- Or copy the file to the phone with Quick Share, Google Drive or USB, then open it from **Files > Downloads**.

<img src="images/01-open-download.png" width="320" alt="Chrome download bar with the Open button marked">

*The web address is blurred in the picture; on your phone it shows github.com.*

### Step 2. Let the app installer continue

The phone shows a "Permission required" screen because the file did not come from the Play Store. Tap **Settings**.

<img src="images/02-unknown-apps-settings.png" width="320" alt="Permission required dialog with the Settings button marked">

### Step 3. Turn on "Allow from this source"

Turn on the switch, then go back. The installer resumes on its own; if not, press back once.

<img src="images/03-allow-from-this-source.png" width="320" alt="Install unknown apps screen with the switch marked">

### Step 4. Install

Tap **Install**.

<img src="images/04-install.png" width="320" alt="Install this app dialog with the Install button marked">

### Step 5. Play Protect check

Play Protect has not seen this app before, since it is not on the Play Store. You can let Google scan it (**Scan app**), or continue without the scan. To continue without it, tap **More details**.

<img src="images/05-play-protect-more-details.png" width="320" alt="Play Protect dialog with More details marked">

Then tap the option to install without scanning.

<img src="images/06-install-without-scanning.png" width="320" alt="Play Protect dialog expanded, install-without-scanning option marked">

> This only affects the one-time upload of the file to Google. It does not weaken the phone's security afterwards.

### Step 6. Open the app

When it says the app is installed, tap **Open**.

<img src="images/07-open-app.png" width="320" alt="App installed dialog with Open marked">

---

## Part 2: Set up the app (one time)

The app opens on a setup card. Work top to bottom.

### Step 7. Grant the permissions

Tap **Grant**.

<img src="images/08-grant-permissions.png" width="320" alt="Setup card with the Grant button marked">

Four requests appear one after another. Allow each one:

| # | Request | Tap |
|---|---|---|
| 1 | Record audio | **While using the app** |
| 2 | Access call logs | **Allow** |
| 3 | Send notifications | **Allow** |
| 4 | Make and manage phone calls | **Allow** |

<p>
<img src="images/09-allow-microphone.png" width="200" alt="Allow microphone">
<img src="images/10-allow-call-logs.png" width="200" alt="Allow call logs">
<img src="images/11-allow-notifications.png" width="200" alt="Allow notifications">
<img src="images/12-allow-phone.png" width="200" alt="Allow phone">
</p>

Microphone and phone are required. Call log is used to name the folder per number. Notifications show the small "recording" status.

### Step 8. Open accessibility settings

Back on the card, tap **Open** on the accessibility line.

<img src="images/13-open-accessibility.png" width="320" alt="Setup card with the accessibility Open button marked">

**Why this is needed:** since Android 10, a normal app cannot reach the call audio through the microphone during a call. An app with an accessibility service turned on is the exception. The service reads nothing on your screen; it only lets the app capture call audio and keeps it running to watch for calls.

### Step 9. Handle "Restricted setting"

Under **Downloaded apps**, tap **Call Recorder**. The first time, it is greyed out and says **Controlled by Restricted Setting**, so tapping shows a blocked message. Tap **Close**.

<p>
<img src="images/14-restricted-setting.png" width="240" alt="Call Recorder greyed out in accessibility list">
<img src="images/15-denied-close.png" width="240" alt="Access denied dialog with Close marked">
</p>

Android locks accessibility for apps installed outside the Play Store until you unlock it once. To unlock:

1. Open **Settings > Apps > Call Recorder** (shown here from the full app list).
2. Tap the **three-dot menu** at the top right.
3. Tap **Allow restricted settings**. The phone may ask for your PIN or fingerprint.

<p>
<img src="images/16-apps-call-recorder.png" width="200" alt="App list with Call Recorder marked">
<img src="images/17-app-info-menu.png" width="200" alt="App info with the three-dot menu marked">
<img src="images/18-allow-restricted-settings.png" width="200" alt="Allow restricted settings menu item marked">
</p>

> Installing over a USB cable with a computer skips this lock, if you ever use that route.

### Step 10. Turn the service on

Go back to **Settings > Accessibility > Call Recorder**. The switch is no longer greyed out. Turn on **Use Call Recorder**, then tap **Allow** on the confirmation.

<p>
<img src="images/19-use-call-recorder.png" width="240" alt="Use Call Recorder switch marked">
<img src="images/20-allow-full-control.png" width="240" alt="Full control confirmation with Allow marked">
</p>

### Step 11. Done

Reopen Call Recorder. The top card now reads **On - recording all numbers**. Setup is complete; you do not repeat any of this.

<img src="images/21-ready.png" width="320" alt="Home screen showing recording is on">

> **On a Pixel, the other person is recorded only when you turn on speakerphone.** Your own voice always records. On most Samsung and Xiaomi phones both sides come through without speaker. This is set by the phone's hardware, not the app.

---

## Part 3: Use the app

Recording is automatic. During a call a small notification shows **Recording call** with the audio source.

<img src="images/22-recording-notification.png" width="320" alt="Notification showing a call being recorded">

### Home screen

One folder per number. Contacts show the saved name.

<img src="images/23-home.png" width="320" alt="Home screen with folders and controls marked">

1. **On/off switch** - off means no calls are recorded.
2. **Tap a folder** to open that number's recordings.
3. **Share icon** on a row shares every recording for that number at once.
4. **Folder icon** opens Music/CallRecordings in the Files app.
5. **Gear icon** opens Settings.

To select more than one folder, **long-press** a folder, then tap others.

### Inside a number's folder

<img src="images/24-folder.png" width="320" alt="One number's recordings with controls marked">

1. **Play** a recording in the app.
2. **Share** that one recording.
3. **Folder icon** opens just this number's folder in Files.

### Selecting, sharing, copying, deleting

**Long-press** any recording to start selecting, then tick more. The bar at the top acts on everything selected.

<img src="images/25-select.png" width="320" alt="Selection bar with share, copy and delete marked">

1. **Share** to WhatsApp, Gmail, Drive and so on. The real file name is kept.
2. **Copy to a folder** of your choice. It keeps one sub-folder per number. Android does not allow the storage root or the Downloads folder itself as the destination, so open or create a sub-folder.
3. **Delete**, with a confirmation.

### Choosing which numbers to record

Open **Settings > Which calls to record**.

<img src="images/26-rules.png" width="320" alt="Rules screen with the options marked">

1. Three modes: **All numbers**, **Only the numbers in the list**, or **All numbers except the list**.
2. **Remove (x)** takes a number off the list.
3. **Add from contacts** - pick one contact.
4. **Add from recent calls** - tick one or several from your call history.
5. **Type a number** by hand.

Example of "Add from recent calls": tick the numbers, then tap **Add**.

<img src="images/27-recent-calls.png" width="320" alt="Recent calls picker with a number ticked and Add marked">

A blocked number is never kept. For an incoming call whose number is known before you answer, the app skips it and never starts recording. For an outgoing call, or any call whose number the phone reveals only after it ends, the app records to a temporary file during the call and deletes that file automatically at hang-up once it sees the number is on the block list. So no recording of a blocked number survives, though a temporary one can exist on disk while such a call is in progress.

### Audio source

If the other person is missing or faint in a recording, open **Settings** and try another source, one test call per option.

<img src="images/28-audio-source.png" width="320" alt="Audio source options">

> On a Pixel, the other side is captured only on speakerphone. On most Samsung and Xiaomi phones both sides come through normally. This depends on the phone, not the app.

---

## Updating to a new version

Download the new APK and open it the same way. Choose **Update** (or install over the old one). Your recordings, folders and settings stay. You do **not** redo Part 2.

Do not uninstall the old version first. Uninstalling removes the app's claim on its recordings; the audio files survive, but they move into the phone's general audio and the folder view starts empty.

---

## Problems and fixes

| Problem | Fix |
|---|---|
| Accessibility switch is greyed out | Do Step 9: App info > three-dot menu > Allow restricted settings. |
| Top card still says "Setup needed" | A permission or the service is off. Open the app and finish the red steps on the card. |
| Nothing records | Check the on/off switch is on, and that the "Recording call" notification appears during a call. |
| Only your voice is in the file | Expected on Pixel without speakerphone. Try another audio source, or use speaker. |
| Recordings vanished after reinstalling | The old version was uninstalled. See the warning above. Files are still in Music/CallRecordings via the Files app. |
| Can't find the files outside the app | Any Files or music app, folder Music/CallRecordings, one sub-folder per number. |
