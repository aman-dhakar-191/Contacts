# Contacts

A personal Android phone app: contacts, dialer, call history, incoming-call screen and call recording.
Experimental pre-release, installed from the private app store (`aman-dhakar-191/private-app-store`).

Package `io.github.amandhakar.contacts`, minSdk 29 (Android 10), Kotlin + Jetpack Compose.

## What it does

- **Contacts** use the phone's own contacts database (so Google contacts that are synced to the phone
  appear automatically, and edits sync back). Search, labels (groups), favorites, photos, add / edit / delete.
- **Import from Google**: add the Google account to the phone and turn on contacts sync (Settings has shortcuts
  and a *Sync now* button). No Google Cloud project or sign-in screen is involved. Also vCard (.vcf) import / export.
- **Keypad** with T9 name search, speed dial (long-press 1–9, set from a contact), dual-SIM picker.
- **Calls**: placing calls, incoming-call screen (answer / decline), mute, speaker, hold, DTMF keypad, second call swap.
  Needs the app to be the **default phone app** (Settings → *Set*).
- **Call history**: filters (missed / incoming / outgoing), grouped by day, per-contact history, block, delete.
- **Missed-call notification** with a *Call back* button.
- **Block numbers** (system block list) and **merge duplicate contacts**.
- **Call recording**: manual button during a call, or automatic. See the limits below.

## Call recording

Android does not let normal apps capture the other person's voice on most phones: the call-audio inputs need a
system-only permission. So the app finds out what your phone allows instead of guessing:

- **Settings → Recording test** (also the *Rec test* button on the call screen). During a call, put it on speaker and run it:
  the app records 3 seconds from every audio input, measures the level and saves a sample you can play. It tells you which
  inputs capture sound (and whether Android is silencing the app on purpose), and lets you pick the one to use.
- **Silence check on every recording**: loudness is read every half second. If an input is completely silent for 6 seconds
  it is blocked, so the recorder switches to the next input; a recording that stays silent is deleted instead of kept.
  When Android says outright that it is silencing an input, the switch happens after about a second instead.
- **Starts on the input that works**: the input that last recorded sound goes first, then the ones the recording test heard
  sound on; inputs the test found blocked or silent go last, even if picked in Settings.
- **Each recording says what it captured**: "call audio", or "microphone (voice recognition), speaker on" and so on, with the
  route (speaker, earpiece, Bluetooth, wired, or "route changed during the call"). Route changes are written to the log.
- **Speaker fallback is off by default**: on some phones (POCO X6, HyperOS) the voice-recognition input records both voices
  with the earpiece, so the speaker isn't turned on unless you switch Speaker fallback on.
- **Auto-record decisions are logged**: each connected call adds an "auto-record check" line saying which rule matched or why
  none did. The check runs on every call state and details change, because some phones skip one of them.
- Inputs are tried in order: the one you chose, then call audio, voice recognition, voice communication, unprocessed mic, mic.
  Microphone-type inputs record your voice; whether they also hear the other person depends on the phone (on a POCO X6
  voice recognition does, even with the earpiece). Each input is tried at a few
  sample rates (call inputs at 16 and 8 kHz first), because some phones only open them at low rates.
- **Recording log** (Settings → Recording test, at the bottom): every start, failure and stop, with the inputs tried, the step
  that failed and Android's error, plus whether the Microphone permission is on, whether this is the default phone app and
  whether the app was on screen. Stays on the phone, never holds numbers or names, and has a Copy button. The same lines go to
  logcat with the tag `ContactsRec`.
- Errors stay on the call screen until tapped (a toast cuts them to two lines). Recording is not started at all when the
  Microphone permission is off, because Android refuses a microphone service without it.

### Recording rules

Settings → Call recording decides which calls are recorded by themselves (as soon as they connect):

- **Record all calls**
- **Record unknown numbers**: any number that isn't in your contacts, and hidden numbers
- **Auto-record list**: numbers you add by hand or pick from contacts; also *Always record this number* on a contact's
  number and *Always record* in the call history menu

The call screen says why a recording started ("auto: unknown number"). Stopping it by hand sticks for the rest of that call.

Many places require everyone on the call to agree to being recorded.

## Call identifier

Settings → **Call identifier** puts calls into categories (Spam, Delivery, …) by rules or by a manual flag.

- **Master switch** (off by default) and a **switch per rule**. A rule that turns out to be broken (e.g. a bad pattern) is switched
  off by itself, with the reason shown.
- A **rule** has a name, a category and conditions that must all be true: number starts with / matches a pattern / has at most N digits /
  is hidden, in contacts or not, has a label, name contains, call type, answered call shorter than N seconds, N calls within H hours,
  time of day. Rules are checked top to bottom and the first match wins.
- A **manual flag** (long-press a call in Recents, or a contact's number menu → *Flag as…*) always wins over the rules.
- **What you did with a number's calls** can be a condition too: answered N or more of its calls, declined/blocked N or more, missed N or more,
  you called it N or more times, or you answered less (or at least) P% of its calls over at least M calls. Add `/days` to look at
  only the last D days. It uses the last 2,000 calls in the call log.
- **Reporting a wrong category**: "This is wrong…" in the processing log, "This category is wrong…" in a call's menu in Recents, or tap
  the category on the call screen. It records which rule was wrong for which number and what it should have been, and can flag the number
  so it is right at once ("No category" = never label this number). Rules show how many numbers were reported wrong; the *Reported wrong*
  screen groups the reports by rule and exports them, and the processing log, as CSV.
- **Export / import rules**: Settings → Call identifier → *Back up and share rules* saves your categories and rules (never any phone
  numbers) to a JSON file and reads one back. An import shows what will change first: add to your rules (rules that already do the same
  are left out) or replace them, imported rules can start switched off, and broken or unknown rules in the file are skipped and reported.
  Rules from a file are untrusted, so a pattern only ever sees the first 32 characters of a number.
- Where it shows: the call history, the call screen and the incoming-call notification.
- **Processing log**: every record processed (live calls and call-history rows) with the outcome and the rules tried, kept on the
  phone only (latest 2,000). Nothing from the identifier is sent to Firebase.
- The engine is plain Kotlin (`identifier/Engine.kt`) and unit tested.

## Build

```sh
./gradlew testDebugUnitTest assembleDebug
```

CI (`.github/workflows/build.yml`) runs the tests and builds a debug APK on every push and pull request.

## Release

Push a tag (`git tag v0.1.0 && git push origin v0.1.0`) or run **Actions → Release** on `main`.
The workflow builds a signed APK and publishes it as a GitHub release; the store app reads it from there.
`versionCode = major·1,000,000 + minor·1,000 + patch`.

### Signing key

If the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` exist, releases are
signed with them. Without them the workflow signs with `keystore/experimental.jks`, whose password is public in this
repo. That is fine for personal experimenting but means anyone could build an APK that Android accepts as an update.
**Switching to your own key later changes the signature, so Android refuses the update: uninstall first**
(contacts and call history are not lost, but app settings and recordings are).

## Crash reports and usage (Firebase)

The app sends crash reports (Crashlytics) and anonymous usage events (Analytics) so problems can be understood.
Settings → Diagnostics has a switch to turn it off, a harmless test report and a test crash.

- Events are things like screen names, which recording input worked, counts and error types. **Never** names, phone
  numbers, emails, account names or call content. The advertising id and Android id are not collected.
- `app/google-services.json` holds the Firebase project config. It is not a secret: the same values are inside every
  APK. If it is missing, the build still works and nothing is sent.
- Crashlytics needs the first report before the Firebase console shows anything; a crash is uploaded the next time the app opens.
