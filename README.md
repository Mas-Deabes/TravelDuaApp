<p align="center">
  <img src="docs/logo.png" alt="Travel Duaa logo" width="180">
</p>

<h1 align="center">Travel Duaa · دعاء السفر</h1>

An Android app that plays the travel duaa automatically when you start the car.

It runs either on **your phone** (plays through the car speakers over Bluetooth) or on an **Android car head unit** (plays when the car starts). You choose which the first time you open the app.

---

## Features

**The duaa**
- **Plays by itself** when the car starts or your phone connects to the car.
- **Pick the duaa** from a list, with its full **Arabic text and source** (checked against the hadith collections), its **transliteration** and its **English meaning**.
- **Choose the audio** for each duaa: built into the app, your own MP3, or **record your own voice** in the app. Each audio has its own volume slider.
- **Follow along:** while it plays, the phrase being recited is highlighted, and shown large in the "Now playing" bar.
- **Return duaa** (آيبون تائبون…) when you arrive: phone mode, when the phone disconnects from the car after a trip.
- **Morning & evening dhikr** after the travel duaa, once each morning and evening.

**When and how it plays**
- **How often:** every start, once a day (first trip), or at most every 2 / 4 / 6 hours.
- **Quiet hours:** late at night, play more quietly or not at all.
- **Music or radio:** lowered during the duaa (default), or paused and resumed afterwards.
- **Several cars** (phone mode), each with its own duaa if you like. Headphones and speakers are ignored.
- **Head unit:** open an app after the duaa (e.g. CPLink for CarPlay), and optionally show Travel Duaa while it plays.

**Easy to use**
- **Guided setup** on first launch, ending with a live test in the car.
- **English 🇬🇧 or Arabic 🇪🇬** interface (العربية, right to left), and **light or dark** mode.
- **Needs attention** checklist: anything that would stop the duaa playing, with a button to fix it.
- **In-app updates** from GitHub Releases.
- **Backup and restore** of settings and recordings to one file.
- **Home screen widget** (phone): one tap plays the duaa.
- **Event log** with a **Share** button, for troubleshooting.

## The two modes

| | 📱 Phone | 🚗 Head unit |
|---|---|---|
| **Install it on** | Your phone | An Android car head unit (FYT, Topway, MTK and similar Chinese units) |
| **When it plays** | When the phone connects to one of your cars by Bluetooth | After a full start-up, and/or when the unit wakes from sleep. You choose which in Settings. |
| **How it plays** | Waits until the car's Bluetooth audio is ready (up to 30 s), then plays through the car speakers | Waits a few seconds for the speakers to wake, then plays |
| **Bluetooth needed** | Yes | No |
| **Won't repeat within** | 10 minutes (e.g. if Bluetooth drops and reconnects) | 30 s to 3 min, depending on **Car must be off for at least** (several start signals can arrive together) |

**How head unit mode notices the car starting:** most Chinese head units don't shut down when you turn the car off. They sleep (often for days) and wake up when you start the car. Many send no signal when they wake, so the app checks every few seconds whether the unit has just been asleep. While a unit sleeps, Android's uptime clock stops but real time keeps going. When the two drift apart, the app knows the car was off and plays the duaa. It also listens for boot, ignition (ACC) and screen signals, for units that do send them.

---

## The duaas

| Duaa | Arabic | Source |
|---|---|---|
| Travel duaa (full) | دعاء السفر كاملاً | Sahih Muslim 1342 |
| Duaa for getting in the car | دعاء الركوب | Abu Dawud 2602, Tirmidhi 3446 |
| Short version (Quran verses) | الآيتان من سورة الزخرف | Surah Az-Zukhruf 43:13-14 |
| Return duaa (on arrival) | دعاء الرجوع من السفر | Sahih Muslim 1342 |
| Morning & evening dhikr (×3) | ذكر الصباح والمساء | Abu Dawud 5088, Tirmidhi 3388 |

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
| `duaa_return.mp3` | Return duaa | Standard recording |
| `duaa_dhikr.mp3` | Morning & evening dhikr | Standard recording |
| `my_duaa.mp3` | My own recording | My duaa |

To offer several reciters for one duaa, add `_name` to the end: `duaa_full_mishary.mp3`, `duaa_full_sudais.mp3` and so on. They all appear in that duaa's **Audio** list.

**Name rules:** lowercase letters, numbers and `_` only. Spaces, dashes, capital letters or Arabic letters will make the build fail.

`put_recordings_here.txt` in the same folder is just a note. The app ignores it.

**Tip:** trim any silence at the start of the recording (Audacity does this for free), so the duaa starts as soon as the car is on.

**If you share the app**, only build in recordings you have permission to use. Recording it yourself avoids this.

---

## Settings at a glance (⚙️ Settings)

| Section | What's in it |
|---|---|
| Installed on | Phone or head unit (Change opens the setup) |
| Head unit / My cars | When to play, delays, CPLink, show while playing · or your cars, each with its own duaa |
| Playing | How often · Quiet hours · Lower or pause music |
| More duaas | Morning & evening dhikr · Return duaa (phone) — each with its own audio list and Record button |
| Language and text | English / العربية · transliteration · English meaning · follow along |
| If it doesn't play after the car wakes | Extra reliability helper · "Do not sleep" tip (head unit) |
| Appearance | Dark / Light / Follow system · Run the setup again |
| Backup and restore | Save everything to a .zip, restore it on another device |
| Updates | Check for updates · Download & install |

## In-app updates

The app checks this repo's **Releases** once a day and shows **Update available** with an **Install** button. Android then asks you to confirm.

**Needs a public repo**, because GitHub doesn't let apps read the releases of a private repo. That's safe because the signing key isn't in the repo: the build gets it from GitHub secrets. The first time, Android asks you to allow **Install unknown apps** for Travel Duaa.

**Renaming the repo** (e.g. to `TravelDuaApp`) is fine: the app reads the repo name from GitHub at build time, so the next build checks the new name.

## Getting the APK

GitHub builds the app every time you push a change.

1. Open **Releases** on the right side of this page.
2. Download `TravelDuaa.apk` from the newest build.
3. Copy it to the phone or head unit and open it to install. Allow "install unknown apps" if asked.

You can also start a build by hand: **Actions** tab → **Build APK** → **Run workflow**.

Every build is signed with the same key, so a new version installs over the old one without uninstalling.

---

## Setting it up

The first time you open the app, a **guided setup** takes you through everything (about 2 minutes):

| Step | What you do |
|---|---|
| Welcome | Logo and what the app does |
| 1. Light or dark | Dark (default), Light, or Follow system |
| 2. Where is it installed | Phone or head unit. The app suggests the likely one (wide screen / no real battery = head unit). |
| 3. Duaa and audio | Pick the duaa (Arabic text + English meaning), choose or add an audio, play it, set its volume |
| 4. Settings | **Head unit:** when to play, delays, "After the duaa, open" (e.g. CPLink), "Show while playing", Extra reliability helper. **Phone:** add your car(s), allow Bluetooth, set battery to Unrestricted. |
| 5. Test | Start the test, then turn the car off and on (or Bluetooth off and on). The screen shows ✅ when the duaa played by itself. You can skip it. |
| 6. Done | Summary of your choices |

The setup remembers where you were, so after turning the car off and on for the test, opening the app brings you back to the test result.

**Run it again** any time from **⚙️ Settings → Appearance → Run the setup again**. **⚙️ Settings → Installed on → Change** opens the setup at step 2.

### The three screens

| Screen | What's on it |
|---|---|
| **Main** | ⚙️ Settings and 📋 Log buttons, the **Now playing** bar, the **Needs attention** checklist, the duaa (Arabic, meaning, audio, Play) and the **Test** button |
| **⚙️ Settings** | Installed on (Change), when to play / delays / CPLink (head unit) or your cars / battery (phone), Extra reliability, Appearance, Run the setup again, version |
| **📋 Log** | The event log on its own, updated live |

**← Back** or Android's back button returns to the main screen.

- **Needs attention** (orange box) appears only when something would stop the duaa playing, e.g. no audio chosen, Bluetooth not allowed, no car added, battery restricted, "Display over other apps" not allowed. Each line has a button to fix it.
- **Now playing** (green bar) appears while the duaa plays, with a **Stop** button.
- **Appearance** (in Settings): Dark, Light or Follow system. The screen redraws straight away.

### Head unit extras

- **After the duaa, open:** pick an app (e.g. CPLink) to open once the duaa has finished. Turn off that app's own auto-start, so it doesn't open first.
- **Show Travel Duaa while the duaa plays:** brings the app to the front during the duaa, then opens the "after" app.
- Both need **Display over other apps**. The app asks for it, and the checklist reminds you if it's off.
- If your unit has a "start apps on boot" or "auto-start" list in its settings, add Travel Duaa.

### If it doesn't play after the car wakes

Head units differ a lot. The app has a standard method plus two options, under **If it doesn't play after the car wakes**. They work together: whichever notices the car start first plays the duaa, and it still plays only once.

| Option | What it does | When to use it |
|---|---|---|
| **Standard** (always on) | Background service: boot, ignition and screen signals, plus the clock check | Works when the unit keeps apps running while it sleeps |
| **Extra reliability helper** | An accessibility service. Android keeps it running and restarts it itself, so it survives units that close apps while sleeping. It uses "the screen changed" only as a nudge to check for a wake-up; it doesn't read the screen. | Turn it on if the duaa plays after a full restart but not after waking |
| **"Do not sleep"** (head unit setting) | The unit fully shuts down when the car is off, so every start is a full start-up | Last resort. Always works, but start-up is slower (about 20–40 s) |

**Turning on the helper:** tap **Turn on in Accessibility**, find **Travel Duaa – car start helper**, and switch it on. On Android 13 and newer, if it's greyed out, open **App info → ⋮ → Allow restricted settings** first.

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
| `Extra reliability helper connected` | The helper is on and running. |
| `Helper noticed the car start` | The helper caught the wake-up. |
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
| `MainActivity.java` | The screen: guided setup, checklist, duaa cards and audio, test button, settings, appearance, event log |
| `Ui.java` | Light / dark colours and building blocks for the screen |
| `Duas.java` | The duaa texts, finding every audio (built-in and added on the device), and each duaa's audio choice |
| `DuaaService.java` | Waits for the right moment and plays the audio |
| `BootReceiver.java` | Head unit mode: boot and ignition signals |
| `BluetoothReceiver.java` | Phone mode: car Bluetooth connect / disconnect |
| `WakeHelperService.java` | Optional "Extra reliability" accessibility helper (head unit) |
| `Updater.java` | Checks GitHub Releases, downloads and installs updates |
| `Backup.java` | Backup / restore of settings and recordings (.zip) |
| `DuaWidget.java` | Home screen widget |
| `Lang.java`, `ArabicStrings.java` | English / Arabic interface |
| `Compat.java` | Calls that differ between Android versions |
| `Prefs.java` | Saved settings, the cars list and the event log |
| `.github/workflows/build.yml` | The GitHub build that publishes the APK to Releases |
| `tools/make-signing-key.bat` | Makes a new signing key and copies it for the GitHub secret |

---

## Signing key

Android only installs an update if it's signed with the **same key** as the installed app. The key is kept **out of the repo**: GitHub gets it from the repo's secrets when it builds.

### Setting it up (once)

1. **Make the key.** Double-click `tools/make-signing-key.bat` (Windows, needs Android Studio). It asks for a password, saves the key in `Documents\TravelDuaa-signing-key\`, and copies it as text to your clipboard.
   **Back up that folder and the password.** Without them you can't release updates.
2. **Add the secrets.** On GitHub: **Settings → Secrets and variables → Actions → New repository secret**:
   | Name | Value |
   |---|---|
   | `KEYSTORE_BASE64` | paste (the text from step 1; also in `KEYSTORE_BASE64.txt`) |
   | `KEYSTORE_PASSWORD` | the password you chose |
3. **Push.** In the Actions log, the step *Get the signing key from secrets* should say *Using the signing key from secrets*.

If the secret is missing, the build **stops on purpose** instead of publishing an APK that couldn't update the app.

Never commit a key: `.gitignore` already blocks `*.keystore`, `*.jks` and `keystore.txt`.

**Builds in Android Studio** on your PC are signed with Android Studio's own debug key. They're fine for testing (▶ Run), but can't update a copy installed from GitHub; uninstall that copy first.
