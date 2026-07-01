# Phase 6 Device Smoke Test

Run this checklist on the target Pixel class device after installing a debug or release build.

## Setup

- Install the build with `adb install -r`.
- Launch `net.shadowspire.promenade2.debug/net.shadowspire.promenade2.MainActivity` for debug builds.
- Confirm the Player screen opens by default.
- Confirm Settings can select separate tracks and playlists folders.
- Confirm Diagnostics is empty for a known-good library.

## Playback Lifecycle

- Load a track from the track list. It should load paused.
- Press Play in the app. Audio should start through the Media3 session.
- Press Home. Playback should continue.
- Turn the screen off. Playback should continue.
- Turn the screen on. Lock-screen media controls should show the current track.
- Pause from lock-screen controls. Playback should pause.
- Resume from lock-screen controls. Playback should resume.
- Open recents and dismiss Promenade while playback is active. Playback should continue.
- Pause playback, dismiss Promenade from recents, and confirm the service exits instead of keeping an idle foreground session alive.

## System Media Controls

- Pull down notifications while a track is loaded.
- Confirm the media banner shows the current track title.
- Tap the media banner. Promenade should open to the app.
- Use notification play/pause. App state and audio state should stay in sync.
- Use notification next/previous. The selected queue item should change without crashing.

## Bluetooth Controls

- Connect a Bluetooth headset or controller.
- Start playback in the app.
- Use Bluetooth play/pause. Playback should toggle through the same Media3 path.
- Use Bluetooth next/previous. The active queue should move within bounds.
- Disconnect Bluetooth during playback. Audio should pause or route according to Android audio-focus/noisy-output behavior.

## Storage Faults

- Choose valid tracks and playlists folders.
- Revoke or remove access to the tracks folder, then relaunch or rescan. Player should show the compact folder warning, and Settings should offer repair.
- Revoke or remove write access to the playlists folder, then relaunch or rescan. Diagnostics should report that the playlists folder needs read/write access.
- Add malformed track JSON to the tracks folder and rescan. Diagnostics should report invalid JSON without crashing.
- Add a track JSON that references a missing music file and rescan. Diagnostics should report the track was skipped.
- Add a track JSON with missing optional calls or instructions files and rescan. Diagnostics should warn while keeping the track usable.
- Add a playlist that references a missing track and rescan. The unresolved row should remain visible.

## Final Checks

- Run `./gradlew check`.
- Run `./gradlew assembleDebug`.
- Install and launch the debug build.
- Check logcat for `AndroidRuntime` or `FATAL EXCEPTION` entries from Promenade.
