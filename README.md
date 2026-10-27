# Achievement Factory

A personal Android app for daily, weekly and monthly achievements and one-off todos — counters,
Bronze/Silver/Gold medals, streaks, and celebrations when you finish things. Everything stays on
the phone: no account, no internet access, no sync.

## Try it on your phone

You need an Android phone running **Android 8.0 or newer**. The app isn't in the Play Store; you
install it from a file.

1. **Get the file `app-debug.apk`** from whoever shared it with you (it's about 19 MB).
2. **Copy it to the phone** — over a USB cable into the *Download* folder, through Google Drive,
   or any other way you can open a file on the phone.
3. **Open it on the phone** (Files app → Downloads → `app-debug.apk`) and tap **Install**.
   Android will ask to allow installing apps from that source (Files, Drive, …) — allow it once.
   Google Play Protect may warn that the app is from an unknown developer; choose to install
   anyway.
4. Open **Achievement Factory** and tap **+** to add your first achievement or todo.

Good to know:

- **Your data lives only in the app on that phone.** Uninstalling the app deletes it, and there
  is no backup or sync.
- **Updating:** install a newer `app-debug.apk` over the old one the same way — your data is kept.
  If Android says *App not installed*, the new file was built with a different signing key; ask
  for a correct build rather than uninstalling, or your data will be lost.

## For the developer: where the file comes from

Build it with Docker Desktop running by double-clicking `tools\docker\run-tests.bat`. It runs the
tests and builds the app; when it finishes, the file to share is:

```
app\build\outputs\apk\debug\app-debug.apk
```

Builds made with that script all share one signing key (kept in a Docker volume), so updates
install over each other. For a signed release build installed over USB, see `docs/RELEASE.md`.
