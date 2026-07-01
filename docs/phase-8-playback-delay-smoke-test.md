# Phase 8 Playback Delay Smoke Test

Run this on the target Pixel class device with a valid tracks folder selected.

## App Controls

- Load a track. It should remain paused.
- Set Count-in delay to `3s`.
- Tap Play. The player should show `Starting in 3`, then `2`, then `1`.
- Audio should start only after the countdown completes.
- Repeat and tap Pause during the countdown. Countdown should disappear and audio should remain paused at the original position.
- Repeat and tap Next during the countdown. Countdown should disappear and the next track should load paused or remain bounded by the queue.
- Repeat and tap Previous during the countdown. Countdown should disappear and the previous track should load paused or seek to the start when already at the first track.

## System Controls

- Set Count-in delay to `5s`.
- Start playback, then use notification or lock-screen Pause during countdown. Countdown should cancel.
- Start playback from notification or lock-screen controls. Countdown should run before audio starts.
- Use notification or lock-screen Next/Previous during countdown. Countdown should cancel consistently.

## Background

- Set Count-in delay to `10s`.
- Tap Play, immediately press Home, and confirm playback starts after the countdown.
- Tap Play, lock the screen during countdown, and confirm lock-screen controls remain responsive.
- Return to the app after countdown. The player should show normal playback state.

## Persistence

- Set Count-in delay to `5s`.
- Force-stop and relaunch the app.
- Confirm the `5s` delay option is still selected.
- Set Count-in delay to `Off`, relaunch, and confirm playback starts immediately from Play.
