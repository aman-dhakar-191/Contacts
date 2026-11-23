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
- Inputs are tried in order: the one you chose, then call audio, voice recognition, voice communication, unprocessed mic, mic.
  Microphone-type inputs record your voice, plus the other person only if the speaker is on.

Many places require everyone on the call to agree to being recorded.

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
