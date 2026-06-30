# Promenade 2 Greenfield Design

## Purpose

Promenade 2 is a greenfield Android music practice player for a single user and a single modern phone class: at least a Pixel 6a running the latest Android release available to that device. The application package is:

```text
net.shadowspire.promenade2
```

The rewrite is intentionally not backward-compatible with old app internals. It preserves useful user-facing behavior from Promenade, but it rebuilds storage, playback, state, and UI around Android media-player architecture from the first phase.

The most important architectural decision is that Promenade 2 is a real Android media app from day one. Playback is owned by a Media3 `MediaSessionService`, controlled by a Media3 `MediaController`, and exposed to system media controls, Bluetooth controls, lock-screen controls, and background playback immediately. This is not a later migration target.

References checked for this design:

- Media3 recommends hosting the `Player` and `MediaSession` inside a `MediaSessionService` for background playback and external clients such as system media controls, media buttons, companion devices, and Bluetooth headsets: https://developer.android.com/media/media3/session/background-playback
- Media playback foreground services require `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `foregroundServiceType="mediaPlayback"`: https://developer.android.com/develop/background-work/services/fgs/service-types
- Media-session notifications are exempt from Android 13+ notification runtime permission behavior, but the app should still use a proper media session because System UI media controls are populated from session state: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Storage Access Framework directory grants use `ACTION_OPEN_DOCUMENT_TREE` and persisted URI permissions, giving access only to user-selected locations: https://developer.android.com/training/data-storage/shared/documents-files

## Product Scope

Promenade 2 is a local-first practice player. Users choose a tracks folder containing track JSON, audio, optional calls audio, and optional instruction files. Users also choose or create a separate playlists folder where Promenade 2 stores playlist JSON. The app scans the tracks folder, resolves playlist entries, lets the user edit playlists, and plays tracks with synchronized music and calls audio.

The UI should feel similar to the old app: a compact player-first screen with playlist selection, active playlist rows, current track details, repetition status, transport controls, balance, calls mute, and auto-mute settings. The implementation can change anything necessary to make this a better Android media app.

## Platform Decisions

- Android-only, Kotlin-only.
- Package: `net.shadowspire.promenade2`.
- Minimum SDK: 36 for the initial implementation. No lower-SDK compatibility branches, no legacy permission fallbacks, and no feature flags for older Android behavior.
- Compile SDK and target SDK: latest stable SDK installed for the project, starting at 36.
- Jetpack Compose and Material 3 for UI.
- AndroidX Media3 for playback, session, controller, notifications, and media-button integration.
- Kotlin coroutines and `StateFlow`.
- DataStore for app preferences.
- Kotlin serialization for JSON.
- Storage Access Framework for user-selected folders.
- Hilt may be used only if dependency wiring becomes meaningfully complex; otherwise use explicit small factories and constructors.

## Manifest And Permissions

Promenade 2 should request the fewest permissions that support a proper media app.

Required manifest permissions:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

Required service declaration:

```xml
<service
    android:name=".playback.PromenadePlaybackService"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

No separate media-button receiver is declared. Bluetooth, lock-screen, notification, and other external controls connect through the active Media3 session exposed by `PromenadePlaybackService`.

Permissions intentionally not used:

- No `MANAGE_EXTERNAL_STORAGE`.
- No broad shared-storage permission for normal operation.
- No `READ_MEDIA_AUDIO` for playback of SAF-selected files, because the user grants explicit document-tree access.
- No notification permission requirement for core playback controls; media-session notifications are handled through the media session. If a future non-media notification is added, that feature must add its own runtime permission flow.

SAF requirements:

- Tracks folder uses `ACTION_OPEN_DOCUMENT_TREE` with read permission.
- Playlists folder uses `ACTION_OPEN_DOCUMENT_TREE` with read/write permission.
- The app persists URI grants with `takePersistableUriPermission`.
- If a persisted URI is revoked, moved, or deleted, the app shows a folder-repair state and asks the user to choose that folder again.

## Storage Design

Promenade 2 separates immutable practice assets from app-managed playlists.

### Tracks Folder

The tracks folder contains:

- Track metadata JSON files.
- Music audio files.
- Optional calls audio files.
- Optional instruction files.

Track JSON files are direct children of the tracks folder with extension `.json` and names not starting with `playlist_`.

The app does not write to the tracks folder in MVP. This makes permission intent clear and avoids accidentally modifying source material.

### Playlists Folder

The playlists folder contains app-managed playlist JSON files. Playlist files use extension `.json`; their filenames are generated by Promenade 2 and do not need the old `playlist_` prefix.

Separating playlists from tracks means:

- Practice assets can be treated as read-mostly input.
- Playlist edits never modify the source track library folder.
- Future backup/export is simpler.

Playlist entries reference track IDs by the track JSON filename from the tracks folder. Duplicate entries are valid and preserved.

### Stored Preferences

DataStore persists:

- Tracks folder tree URI.
- Playlists folder tree URI.
- Last selected playlist ID.
- Balance.
- Calls mute state.
- Auto-mute settings.
- Last active queue and index for playback resumption.
- Last position for playback resumption.
- UI preferences that are actually implemented.

DataStore does not persist resolved library state. The library is rescanned from folders.

## File Contracts

### Track JSON

Promenade 2 keeps the existing track JSON shape:

```json
{
  "name": "Track Name",
  "music_file": "music.mp3",
  "calls_file": "calls.mp3",
  "instructions_file": "instructions.txt",
  "intro": "32 bars",
  "repetitions": [
    { "start": 12.5 },
    { "start": 48.0 }
  ]
}
```

Validation rules:

- `name`: required non-blank string.
- `music_file`: required non-blank relative filename that resolves to readable audio.
- `calls_file`: optional relative filename; if present but missing, warn and continue music-only.
- `instructions_file`: optional relative filename; if present but missing, warn and continue.
- `intro`: required string; empty is valid.
- `repetitions`: required array; empty is valid.
- `repetitions[*].start`: non-negative finite seconds.
- Repetitions are sorted ascending during parsing. If input order changes, emit a warning.

### Playlist JSON

Promenade 2 playlist files are stored in the playlists folder:

```json
{
  "name": "Practice Set",
  "entries": [
    "track_a.json",
    "track_b.json",
    "track_a.json"
  ]
}
```

Validation rules:

- `name`: required non-blank string.
- `entries`: required array of non-blank track JSON filenames.
- Duplicate entries are valid.
- Unknown entries stay in the playlist and appear as unresolved rows in the player and editor.

Promenade 2 may import old `playlist_*.json` files if the user chooses a folder that contains them, but import is a one-time convenience and not a compatibility layer. Imported playlists are written into the playlists folder using the Promenade 2 filename generator.

## Architecture

Code layout should make ownership obvious and keep Android framework dependencies at the edges.

```text
app/src/main/java/net/shadowspire/promenade2/
  MainActivity.kt
  app/
    PromenadeApplication.kt
    AppContainer.kt
  core/
    model/
    result/
    time/
  data/
    diagnostics/
    json/
    library/
    playlist/
    preferences/
    storage/
  domain/
    automute/
    balance/
    library/
    playlist/
    repetition/
  playback/
    PromenadePlaybackService.kt
    PromenadeSessionCallback.kt
    PromenadeMediaController.kt
    PracticePlaybackEngine.kt
    DualAudioCoordinator.kt
    PlaybackQueueStore.kt
  ui/
    navigation/
    player/
    playlists/
    folders/
    instructions/
    diagnostics/
    components/
```

Package rule: package names mirror disk layout. A class belongs in the narrowest package that owns its reason to change.

### Core

Core models are plain Kotlin data types with no Android framework dependency unless a type represents a boundary object that explicitly wraps a platform concept.

Important models:

```kotlin
data class TrackId(val jsonFileName: String)
data class PlaylistId(val fileName: String)
data class Seconds(val value: Double)
data class RepetitionMark(val start: Seconds)

data class ContentRef(
    val displayName: String,
    val uriString: String
)

data class FolderRef(
    val displayName: String,
    val treeUriString: String
)

data class Track(
    val id: TrackId,
    val name: String,
    val musicRef: ContentRef,
    val callsRef: ContentRef?,
    val instructionsRef: ContentRef?,
    val intro: String,
    val repetitions: List<RepetitionMark>
)

data class Playlist(
    val id: PlaylistId,
    val name: String,
    val entries: List<TrackId>
)
```

### Data Layer

Data owns persistence, scanning, parsing, and serialization.

Interfaces:

```kotlin
interface LibraryRepository {
    val state: StateFlow<LibraryState>
    suspend fun setTracksFolder(folder: FolderRef)
    suspend fun setPlaylistsFolder(folder: FolderRef)
    suspend fun rescan()
}

interface PlaylistRepository {
    suspend fun create(name: String): Playlist
    suspend fun save(playlist: Playlist)
    suspend fun delete(id: PlaylistId)
    suspend fun importLegacyPlaylists(files: List<ContentRef>): ImportSummary
}

interface PreferencesRepository {
    val preferences: StateFlow<AppPreferences>
    suspend fun update(transform: (AppPreferences) -> AppPreferences)
}
```

SAF code lives only in `data/storage`. JSON DTOs live only in `data/json`. Domain and UI should not parse raw JSON.

Playlist writes are serialized through a repository-level `Mutex`. Saves pretty-print JSON and update repository state from the saved model.

### Domain Layer

Domain logic is pure and unit-tested:

- `RepetitionCalculator`.
- `AutoMutePolicy`.
- `BalanceCurve`.
- `PlaylistResolver`.
- `PlaylistFileNameGenerator`.
- `LibraryDiagnosticsReducer`.
- `PlaybackQueueBuilder`.

Domain functions should not depend on Android services, Compose, Media3, ContentResolver, or DataStore.

### Playback Layer

Playback is service-first.

`PromenadePlaybackService` owns:

- Media3 `MediaSession`.
- Media3-compatible player exposed to the session.
- Foreground media notification through Media3.
- Audio focus and noisy-output handling through Media3/player integration.
- Media button handling.
- Playback resumption callback.
- Service lifecycle and resource release.

The Activity never owns a player. It connects to the service through a `MediaController`.

`PracticePlaybackEngine` exposes practice-specific operations:

```kotlin
interface PracticePlaybackEngine {
    val state: StateFlow<PracticePlaybackState>
    suspend fun setQueue(queue: PracticeQueue, startIndex: Int, autoplay: Boolean)
    suspend fun selectIndex(index: Int, autoplay: Boolean)
    suspend fun play()
    suspend fun pause()
    suspend fun seekTo(positionMs: Long)
    suspend fun skipPrevious()
    suspend fun skipNext()
    suspend fun setBalance(balance: Float)
    suspend fun setCallsMuted(muted: Boolean)
    suspend fun setAutoMute(settings: AutoMuteSettings)
}
```

The engine maps practice semantics onto the Media3 session so system controls work naturally:

- Play/pause from Bluetooth, lock screen, notification, and app all call the same session path.
- Next/previous from external controls move within the active practice queue.
- Metadata contains track title, playlist name, repetition summary, and calls availability when useful.
- Playback state and available commands are always accurate so Android System UI shows the right controls.

### Dual-Audio Strategy

Music is the clock source. Calls audio is a secondary synchronized stream.

Initial implementation:

- One Media3 ExoPlayer for music, exposed as the session player.
- One Media3 ExoPlayer for calls, controlled by `DualAudioCoordinator`.
- The service starts, pauses, seeks, and releases both players together.
- Calls waits for music readiness before playback starts.
- Seeks pause or gate both streams, seek both, then resume if playback was active.
- Drift is checked periodically while playing.
- If calls drift exceeds 100 ms, emit a warning diagnostic.
- If calls drift exceeds 200 ms, seek calls to the music position.
- If calls fails to load, continue music-only and show a non-blocking diagnostic.

This keeps Media3 session integration correct from the beginning while isolating synchronization behind one class that can later be replaced with a more advanced combined-source approach.

### Playback Semantics

- Selecting a playlist builds a queue and loads the first resolved track without autoplay.
- Selecting a row loads that queue index without autoplay.
- Pressing play starts or resumes through the Media3 session.
- Track completion advances to the next resolved playlist entry and pauses there.
- Bluetooth/lock-screen next and previous obey playlist bounds.
- Seeking updates both music and calls.
- Playback progress updates UI approximately every 250 ms.
- Auto-mute is evaluated on track load and repetition changes.
- Loading a new track resets manual calls mute to false, then applies auto-mute.

## UI Design

Promenade 2 remains a compact practice tool. It should be fast under one thumb, scannable, and stable while playback is running.

### Navigation

Top-level destinations:

- Player.
- Playlist editor.
- Folder setup.
- Instructions.
- Diagnostics.

The first launch opens folder setup until both tracks and playlists folders are valid.

### Player Screen

Keep the old app's rough structure:

- Top app bar with folder status, playlist selector, edit action, diagnostics action.
- Active playlist rows with current row highlight.
- Unresolved rows shown in-place with warning treatment and readable labels.
- Current track panel with name, intro, calls availability, instructions action.
- Repetition status.
- Progress slider with elapsed and duration.
- Previous, play/pause, next.
- Music/calls balance slider.
- Calls mute toggle.
- Auto-mute settings.

The screen observes app state plus Media3 controller state. UI controls dispatch intents; they do not manipulate players directly.

### Playlist Editor

- Lists playlists from the playlists folder.
- Creates, deletes, and edits playlist files in the playlists folder.
- Adds tracks from the scanned tracks folder.
- Allows duplicates.
- Preserves unresolved entries with repair/remove choices.
- Saves after explicit Save in MVP, not after every tiny edit. This avoids confusing background writes and makes failure states easier to show.

Playlist rename is not in MVP unless needed to make create/edit usable.

### Folder Setup

- Tracks folder picker.
- Playlists folder picker.
- Folder health summary.
- Rescan action.
- Import old playlists action if old `playlist_*.json` files are detected in the tracks folder.

### Instructions

MVP uses plain text. Markdown, search, and zoom are future work.

### Accessibility

- Every icon button has a meaningful content description.
- Playback controls have at least 48 dp touch targets.
- Sliders expose semantic labels and values.
- Current track and unresolved rows are not indicated by color alone.

## Diagnostics And Errors

Diagnostics are structured and persisted only as current scan/playback state.

Examples:

- Invalid track JSON.
- Missing required field.
- Missing music file.
- Missing optional calls file.
- Missing instructions file.
- Unknown playlist entry.
- Playlist save failure.
- SAF permission revoked.
- Calls playback failed.
- Calls drift corrected.

Diagnostics appear as:

- Small warning indicator on the player top bar.
- Diagnostics screen with grouped details.
- Snackbar only for immediate user actions such as failed save.

## Testing Strategy

### Unit Tests

Required:

- Track JSON parsing, including missing fields, missing optional files, empty repetitions, and out-of-order repetitions.
- Playlist JSON parsing, including duplicates and unresolved entries.
- Playlist filename generation.
- Repetition calculation.
- Auto-mute policy.
- Balance curve.
- Playlist resolving.
- Queue building.

### Integration Tests

Use fake storage and fake preferences:

- Scan tracks folder with valid and invalid files.
- Scan playlists folder separately from tracks.
- Create/save/delete playlist.
- Import old playlist files.
- Preserve unresolved entries.
- Recover from revoked folder access.

### Playback Tests

Use fake Media3-facing adapters where practical and service smoke tests on device:

- Service starts and exposes a media session.
- App UI controls playback through `MediaController`.
- Bluetooth/media-button play/pause/next/previous are honored.
- Lock-screen/media notification controls reflect state.
- Background playback continues after leaving the Activity.
- Music-only track plays.
- Music+calls track plays and seeks both streams.
- Calls load failure continues music-only.
- Track completion advances paused.

### UI Tests

Compose tests:

- Folder setup incomplete state.
- Folder setup complete state.
- Playlist selection.
- Current row highlight.
- Unresolved playlist row.
- Play/pause intent dispatch.
- Balance and calls mute controls.
- Playlist editor add/remove/reorder.
- Instructions loading and error state.

## Multi-Phase Implementation Plan

Each phase should add a small usable slice without building throwaway architecture. Media session playback starts in phase 1.

### Phase 1: App Skeleton And Media Foundation

Goal: a minimal app that is already a proper Android media app.

- Create package `net.shadowspire.promenade2`.
- Set modern SDK configuration with no old-SDK branches.
- Add Compose, Media3, DataStore, Kotlin serialization, and test dependencies.
- Add `PromenadePlaybackService` with Media3 `MediaSessionService`.
- Add required media foreground-service permissions and service declaration.
- Add Activity-to-service `MediaController` connection.
- Play one bundled or simple selected audio URI through the service.
- Verify background playback, media notification, lock-screen controls, and Bluetooth/media-button play/pause.

No playlist logic yet. No fake local player in Activity.

### Phase 2: Folder Selection And Library Scan

Goal: choose tracks and playlists folders and scan real track metadata.

- Add folder setup UI.
- Persist tracks and playlists SAF URI grants.
- Implement SAF storage adapter.
- Parse track JSON from tracks folder.
- Validate audio/instruction references.
- Emit structured diagnostics.
- Show scanned tracks in a simple list.

Playback still supports a single selected track through the Media3 service.

### Phase 3: Practice Playback Queue

Goal: play scanned tracks with Promenade practice semantics.

- Add `PracticeQueue`, queue builder, and playback state.
- Load selected track through the service.
- Add music/calls dual-player coordinator.
- Add play/pause/seek/next/previous.
- Add repetition calculation.
- Add balance and calls mute.
- Add auto-mute.
- Verify app controls and external controls share the same Media3 path.

At the end of this phase, the app can be used as a basic practice player without playlist editing.

### Phase 4: Playlists Folder And Playlist Editing

Goal: app-managed playlists stored separately from tracks.

- Parse playlists from playlists folder.
- Resolve playlist entries against scanned tracks.
- Preserve unresolved entries.
- Add playlist selector.
- Add playlist editor create/save/delete.
- Add add/remove/reorder entries.
- Add import action for old `playlist_*.json` files found in tracks folder.
- Ensure completion advances to next resolved entry paused.

### Phase 5: Player UI Parity

Goal: make the main UI feel like the old Promenade player while using the new backend.

- Build compact player screen with playlist rows and current track panel.
- Add diagnostics indicator and diagnostics screen.
- Add instructions screen.
- Polish balance, calls mute, auto-mute controls.
- Audit content descriptions and touch targets.
- Verify small phone layout on Pixel 6a dimensions.

### Phase 6: Hardening

Goal: make the app reliable enough to replace the old app.

- Add device playback smoke-test checklist.
- Add service lifecycle tests and manual verification notes.
- Test background playback after screen off, app switch, and recents dismissal.
- Test Bluetooth controls.
- Test revoked folder permissions.
- Test malformed JSON and missing files.
- Replace temporary sample media from phase 1 with real-folder-only flows.

## Acceptance Criteria

Promenade 2 is functionally complete when:

- The app package is `net.shadowspire.promenade2`.
- It uses a Media3 `MediaSessionService` for playback.
- Playback continues in the background.
- Bluetooth/media-button controls work.
- Android lock-screen and notification media controls work.
- The Activity controls playback through a `MediaController`, not direct player ownership.
- The app uses SAF-selected tracks and playlists folders.
- Track assets and playlist files are stored separately.
- No all-files storage permission is required.
- Valid track JSON files appear in the library.
- Missing optional calls and instructions produce warnings, not crashes.
- Playlist JSON files preserve duplicate and unresolved entries.
- Playlist edits write JSON to the playlists folder.
- Music and calls remain synchronized closely enough for practice use.
- Balance, calls mute, and auto-mute behave correctly.
- Track completion advances to the next resolved playlist entry paused.
- Folder choices, last playlist, balance, auto-mute settings, and playback resumption state survive restart.
- Unit tests cover domain and parsing behavior.
- Device smoke tests cover background playback and external controls.

## Explicit Non-Goals For MVP

- Cloud sync.
- Account system.
- Streaming catalog.
- Android Auto.
- Wear OS companion app.
- Media library browsing for external clients.
- Recursive track-folder scanning.
- Markdown instructions.
- Playlist sharing/export.
- Per-track settings.
- Per-playlist settings.
- Count-in, metronome, waveform, or tempo tools.
