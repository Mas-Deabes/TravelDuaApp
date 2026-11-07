<p align="center">
  <img src="docs/logo.png" alt="Travel Duaa logo" width="180">
</p>

<h1 align="center">Travel Duaa · دعاء السفر</h1>

An Android app that plays the travel duaa automatically when you start the car.

It runs either on **your phone** (plays through the car speakers over Bluetooth) or on an **Android car head unit** (plays when the car starts). You choose which the first time you open the app.

---

## Features

- **Plays by itself** when the car starts or your phone connects to the car.
- **Pick the duaa** from a list, with its full **Arabic text and source** (checked against the hadith collections) and its **English meaning** underneath.
- **Choose the audio** for each duaa: one built into the app, or your own MP3.
- **Several cars**: in phone mode, choose which Bluetooth devices count as your car(s). Headphones and speakers are ignored.
- **Test button** that runs the real start-up steps, so you can check it without driving.
- **Event log** showing what happened at each start-up, for troubleshooting.
- **Open an app after the duaa** (head unit), e.g. CPLink for CarPlay, so it starts once the duaa has finished.
- **Dark design** that's easy to read in the car at night, with large buttons.

---

## The two modes

| | 📱 Phone | 🚗 Head unit |
|---|---|---|
| **Install it on** | Your phone | An Android car head unit (FYT, Topway, MTK and similar Chinese units) |
| **When it plays** | When the phone connects to one of your cars by Bluetooth | After a full start-up, and/or when the unit wakes from sleep. You choose which in Settings. |
| **How it plays** | Waits until the car's Bluetooth audio is ready (up to 30 s), then plays through the car speakers | Waits a few seconds for the speakers to wake, then plays |
| **Bluetooth needed** | Yes | No |
| **Won't repeat within** | 10 minutes (e.g. if Bluetooth drops and reconnects) | 3 minutes (ignition and screen-on signals arrive together) |

**How head unit mode notices the car starting:** most Chinese head units don't shut down when you turn the car off. They sleep (often for days) and wake up when you start the car. Many send no signal when they wake, so the app checks every few seconds whether the unit has just been asleep. While a unit sleeps, Android's uptime clock stops but real time keeps going. When the two drift apart, the app knows the car was off and plays the duaa. It also listens for boot, ignition (ACC) and screen signals, for units that do send them.

---

## The duaas

| Duaa | Arabic | Source |
|---|---|---|
| Travel duaa (full) | دعاء السفر كاملاً | Sahih Muslim 1342 |
| Duaa for getting in the car | دعاء الركوب | Abu Dawud 2602, Tirmidhi 3446 |
| Short version (Quran verses) | الآيتان من سورة الزخرف | Surah Az-Zukhruf 43:13-14 |

Tap a duaa to select it. The app shows its full Arabic text, with the English meaning underneath.

---

## Choosing the duaa and its audio

The **Choose the duaa** section lists four options:

- Travel duaa (full)
- Duaa for getting in the car
- Short version (Quran verses)
- My own recording (any audio you like)

**Tap one to select it.** The selected card shows:

- **The full Arabic text** and its source
- **The meaning in English**, so anyone can understand what they're hearing
- **The Audio list**: every audio for that duaa, both built into the app and added by you
  - ◉ marks the one that plays when the car starts. **Tap another one to use it instead.**
  - Audios you added have a **✕** to remove them. Built-in ones can't be removed.
  - Each audio has its own **volume slider** (10%–200%). Above 100% boosts recordings that were saved too quietly. Move it while the duaa plays to adjust by ear.
- **Three buttons: ＋ Add audio, ▶ Play and ■ Stop**

**Adding an audio never replaces anything.** It's added to the bottom of the list, and the one you were using stays selected until you tap a different one. The only exception is a duaa's first audio, which is used automatically.

Each duaa keeps its own list and its own choice. Cards show **🎵 Ready** when they have an audio and **No audio chosen** when they don't.

There are two ways to add an audio.

### Option 1: Add it on the device

1. Copy the MP3 to the phone or head unit (USB cable, USB stick, or drag it onto the emulator).
2. In the app, select the duaa it belongs to (or **My own recording**).
3. Tap **＋ Add audio** and pick the file.
4. It's added to that duaa's **Audio** list. Tap it in the list to use it. The app keeps its own copy, so you can remove the USB stick afterwards.

The recording only exists on that one device.

### Option 2: Build it into the app

Put the MP3 in this folder and rebuild:

```
app/src/main/res/raw/
```

Every audio file in that folder **appears in that duaa's Audio list automatically**. No code changes are needed.

**In Android Studio:**

1. In the Project panel, open `app → res → raw`.
   If there's no `raw` folder: right-click **res** → **New → Android Resource Directory** → set *Resource type* to **raw** → **OK**.
2. Drag the MP3 onto the **raw** folder.
3. Press **▶ Run**.

**The file name decides how it shows in the app:**

| File name | Listed under | As |
|---|---|---|
| `duaa_full.mp3` | Travel duaa (full) | Standard recording |
| `duaa_full_mishary.mp3` | Travel duaa (full) | Mishary |
| `duaa_riding.mp3` | Duaa for getting in the car | Standard recording |
| `duaa_short.mp3` | Short version | Standard recording |
| `my_duaa.mp3` | My own recording | My duaa |

To offer several reciters for one duaa, add `_name` to the end: `duaa_full_mishary.mp3`, `duaa_full_sudais.mp3` and so on. They all appear in that duaa's **Audio** list.

**Name rules:** lowercase letters, numbers and `_` only. Spaces, dashes, capital letters or Arabic letters will make the build fail.

`put_recordings_here.txt` in the same folder is just a note. The app ignores it.

**Tip:** trim any silence at the start of the recording (Audacity does this for free), so the duaa starts as soon as the car is on.

**If you share the app**, only build in recordings you have permission to use. Recording it yourself avoids this.

---

## Getting the APK

GitHub builds the app every time you push a change.

1. Open **Releases** on the right side of this page.
2. Download `TravelDuaa.apk` from the newest build.
3. Copy it to the phone or head unit and open it to install. Allow "install unknown apps" if asked.

You can also start a build by hand: **Actions** tab → **Build APK** → **Run workflow**.

Every build is signed with the same key, so a new version installs over the old one without uninstalling.

---

## Setting it up

1. Open the app and choose **📱 On my phone** or **🚗 On the car head unit**.
2. Tap a duaa to select it, then tap an audio in its **Audio** list (or **＋ Add audio**).
3. Tap **▶ Play** to hear it.
4. Tap **🧪 Test** to run the full start-up steps.

**Phone mode only:**

5. Allow the **Bluetooth** and **notification** permissions.
6. Under **Settings → My cars**, tap **＋ Add a car** and tick your car(s). Car kits are marked 🔊 and listed first.
   **If the list is empty, nothing plays automatically.** This stops it playing on headphones.
7. Tap **Open app settings** → **Battery** → **Unrestricted**. Samsung, Xiaomi and Huawei phones stop apps in the background otherwise.

**Head unit only:**

5. Under **Play when the head unit…**, choose:
   - **Starts up fully**: after a full restart, or when the unit was off longer than its sleep time
   - **Wakes from sleep**: car off, then back on, without a full restart (what most units do day to day)
6. Set **Car must be off for at least** (default 1 min), so switching off and straight back on doesn't replay it.
7. Set the **Delay after the car starts** (default 5 s). If you hear nothing or it's cut off, raise this to 5–10 s.
8. **Optional: After the duaa, open.** Pick an app to open once the duaa has finished, e.g. **CPLink** for CarPlay / Android Auto.
   - Allow **Display over other apps** when asked. Android needs it to let Travel Duaa open another app.
   - Turn off that app's own auto-start (if it has one), so it doesn't open before the duaa has finished.
9. If your unit has a "start apps on boot" or "auto-start" list in its settings, add Travel Duaa.

---

## Testing

### The Test button

**🧪 Test** does exactly what happens at a real start-up (the delay, and in phone mode the Bluetooth audio check), then plays. It ignores the "already played recently" rule, so you can press it as often as you like. Watch the **Event log** at the bottom of the screen; it updates live.

### On the emulator (Android Studio)

The emulator tests the screen, the audio and head unit mode. It can't test phone mode with a car, because it has no Bluetooth.

**Fake ignition signal** (head unit mode), in Android Studio's **Terminal**:

```bash
adb shell am broadcast -a com.fyt.boot.ACCON -n com.sif.travelduaa/.BootReceiver
```

**Real boot:** Device Manager → **⋮** next to the emulator → **Cold Boot Now**. A normal start restores a saved state and doesn't send the boot signal.

**Wake from sleep:** press the emulator's power button, wait over 1 minute, then press it again.

### On a real phone

1. Add your car under **My cars**.
2. In the car, turn the phone's Bluetooth off and on.
3. It should play through the car speakers within about 10 seconds.
4. Connect headphones and check that nothing plays.

---

## Troubleshooting

Open the app and read the **Event log**. It shows every signal the app received and why it played or skipped.

| Log says | Meaning / fix |
|---|---|
| `Couldn't open …` | Allow **Display over other apps** for Travel Duaa (Settings → Apps → Travel Duaa). |
| `Duaa finished` → `Opened CPLink` but CPLink was already open | CPLink opened itself first. Turn off its own auto-start / auto-open setting. |
| Nothing at all after starting the car | The unit closed the app and didn't restart it. Add Travel Duaa to the unit's auto-start / keep-in-background list, and don't use "clean memory" tools on it. |
| `Skipped: already played in the last …` | Working as intended: it only plays once per car start. Use **🧪 Test** to play again straight away (tests don't count). |
| `Head unit was asleep for …` | The app noticed the unit sleeping and waking. Normal. |
| `Not playing: off for less than …` | The car was only off briefly. Lower **Car must be off for at least**, or wait longer. |
| `Not playing: "wakes from sleep" is turned off` | Turn on **Wakes from sleep** in Settings. |
| `App restarted, last running … ago` | The unit closed the app while it slept, and Android restarted it. It still plays if the gap was long enough. |
| `Screen off` / `Car off signal: …` | Shows what your unit sends when the car is switched off. Useful when reporting a problem. |
| `Skipped: auto-play is off` | Turn on the first switch under **Settings**. |
| `No audio for "…"` | Select the duaa and tap **＋ Add audio**. |
| `Android refused to start` | The phone stopped the app in the background. Set battery use to **Unrestricted**. |
| `Bluetooth audio not detected, playing anyway` | The car connected for calls but not media audio. In the phone's Bluetooth settings, turn on **Media audio** for the car. |

---

## Building it yourself

**Android Studio:** open the project folder, wait for the sync, then press **▶ Run**.
**Command line:** `./gradlew assembleRelease`. The APK is in `app/build/outputs/apk/release/`.

Requirements: Android Studio (or JDK 17 + Android SDK 35). The app runs on Android 5.0 and newer.

---

## Code layout

| File | What it does |
|---|---|
| `MainActivity.java` | The screen: mode choice, duaa cards and audio choice, test button, settings, event log |
| `Ui.java` | Colours and building blocks for the screen |
| `Duas.java` | The duaa texts, finding every audio (built-in and added on the device), and each duaa's audio choice |
| `DuaaService.java` | Waits for the right moment and plays the audio |
| `BootReceiver.java` | Head unit mode: boot and ignition signals |
| `BluetoothReceiver.java` | Phone mode: car Bluetooth connect / disconnect |
| `Compat.java` | Calls that differ between Android versions |
| `Prefs.java` | Saved settings, the cars list and the event log |
| `.github/workflows/build.yml` | The GitHub build that publishes the APK to Releases |

---

## Signing key

`app/release.keystore` signs every build with the same key, so updates install over the old version. Anyone with this file can sign apps as you, so keep the repo **private**, or move the key into GitHub Secrets before making it public.
