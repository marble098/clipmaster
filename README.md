# ClipMaster — Floating Clipboard Manager for Android

A system-level floating clipboard manager built with Jetpack Compose, Room, and root integration. Designed to be built entirely via **GitHub Actions** — no local Android Studio required.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                      MainActivity                        │
│              (Compose Onboarding Flow)                   │
│    ┌──────────┐  ┌──────────┐  ┌──────────────────┐     │
│    │  Root    │  │ Overlay  │  │  Accessibility   │     │
│    │  Check   │  │ Setting  │  │    Setting       │     │
│    └──────────┘  └──────────┘  └──────────────────┘     │
└────────────────────────┬────────────────────────────────┘
                         │ startForegroundService()
                         ▼
┌─────────────────────────────────────────────────────────┐
│              FloatingBubbleService                       │
│  ┌────────────┐    ┌─────────────────────────────┐      │
│  │  Bubble    │◄──►│       ClipPanel              │      │
│  │ (Draggable)│    │  (Compose in WindowManager)  │      │
│  └────────────┘    └──────────┬──────────────────┘      │
│                               │                          │
│                    ┌──────────▼──────────┐               │
│                    │   ClipRepository    │               │
│                    │  (insert + prune)   │               │
│                    └──────────┬──────────┘               │
│                               │                          │
│                    ┌──────────▼──────────┐               │
│                    │   Room Database     │               │
│                    │  (FIFO: 50 items)   │               │
│                    └─────────────────────┘               │
└─────────────────────────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ▼                             ▼
┌──────────────────┐         ┌──────────────────┐
│ Accessibility    │         │  RootExecutor    │
│ Service          │         │  (su commands)   │
│ • Screen scrape  │         │  • Clipboard set │
│ • Text capture   │         │  • Paste inject  │
│ • Paste into     │         └──────────────────┘
│   focused field  │
└──────────────────┘
```

## Project Structure

```
ClipMaster/
├── .github/workflows/build.yml    ← CI/CD pipeline
├── app/
│   ├── build.gradle.kts           ← All dependencies (Compose, Room, etc.)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/clipmaster/floating/
│       │   ├── ClipMasterApp.kt           ← Application + notification channel
│       │   ├── MainActivity.kt            ← Onboarding host
│       │   ├── data/
│       │   │   ├── db/
│       │   │   │   ├── ClipEntry.kt       ← Room @Entity
│       │   │   │   ├── ClipDao.kt         ← Room @Dao with FIFO pruning
│       │   │   │   └── ClipDatabase.kt    ← Room database singleton
│       │   │   └── repository/
│       │   │       └── ClipRepository.kt  ← Insert + dedup + prune logic
│       │   ├── service/
│       │   │   ├── FloatingBubbleService.kt  ← Foreground service + Compose overlay
│       │   │   ├── ClipAccessibilityService.kt ← Screen text capture
│       │   │   └── BootReceiver.kt        ← Auto-start after reboot
│       │   ├── root/
│       │   │   └── RootExecutor.kt        ← su shell commands for clipboard
│       │   ├── clipboard/
│       │   │   └── ClipboardHelper.kt     ← ClipboardManager read/write (+ root fallback)
│       │   ├── ui/
│       │   │   ├── theme/                 ← Material3 dark theme
│       │   │   ├── onboarding/            ← Pager-based permission setup
│       │   │   └── bubble/                ← Floating panel Compose UI
│       │   └── util/
│       │       └── PermissionHelper.kt    ← Permission checks
│       └── res/
│           ├── drawable/                  ← Vector icons
│           ├── xml/accessibility_config.xml
│           └── values/strings.xml
├── kernelsu/
│   ├── module.prop                ← KernelSU module metadata
│   └── customize.sh              ← Installation script
├── build.gradle.kts               ← Root project plugins
├── settings.gradle.kts
├── gradle.properties
└── gradle/libs.versions.toml      ← Version catalog
```

## How to Build

### Via GitHub Actions (primary method)

1. Push this repo to GitHub.
2. The workflow triggers on every push to `main`.
3. Get the build either way:
   - **GitHub Release** (recommended) — each push to `main` publishes a release tagged `v1.0.<run number>` with `ClipMaster.apk` and `ClipMaster-KernelSU-Module.zip` attached as direct, native-format assets (an `.apk` downloads as an `.apk`, not wrapped in an extra zip).
   - **Actions artifacts** — the same two files are also uploaded as workflow artifacts (`ClipMaster-APK`, `ClipMaster-KernelSU-Module`) for every run, including PRs. Note: GitHub always wraps *artifact* downloads in an extra zip, regardless of the file inside — that's a platform-level behavior of Actions artifacts, not something this workflow controls. Use the Release assets above to avoid that extra layer.

### Locally (if needed)

```bash
# Requires JDK 17 + Android SDK
./gradlew assembleRelease
```

## Permissions Required

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Floating bubble overlay |
| `BIND_ACCESSIBILITY_SERVICE` | Screen text capture + paste into fields |
| Root (`su`) | System clipboard injection bypassing restrictions |

## Clip Actions & System Clipboard

- **System clipboard sync**: `FloatingBubbleService` registers a `ClipboardManager.OnPrimaryClipChangedListener`, so anything copied anywhere on the device is picked up into clip history automatically — not just text captured via the accessibility service.
- **Re-copy**: Tapping a clip (or its copy icon) writes it back to the system clipboard via `ClipboardHelper`, which uses the standard `ClipboardManager` API first and falls back to the root shell write in `RootExecutor` if the OS blocks background clipboard access.
- **Edit**: The edit icon opens a dialog to modify a clip's text in place (`ClipRepository.updateClip`), preserving its position in history.
- **Share**: The share icon hands a clip's text to the Android share sheet (`Intent.ACTION_SEND`) so it can be sent to any app.
- **Clear all**: The "Clear all" chip (with a confirmation dialog) wipes the entire clip history via `ClipRepository.clearAll()`.

## Smart Bubble Positioning

The floating bubble behaves like a chat head, with several ways to reposition it:
- **Drag + edge snapping**: Releasing a drag glides the bubble to whichever screen edge (left/right) it's closer to, animated with a `ValueAnimator`. Toggle **Snap to screen edges** off in Settings to drop it exactly where you release it instead (full manual placement).
- **Tap-to-move**: The panel's "Move" chip opens a menu to jump the bubble straight to any of the four screen corners.
- **Reset**: Settings has a one-tap "Reset bubble position" action, applied live via a broadcast to the running service.
- **Bounds-aware**: The bubble is clamped during drag and after rotation so it never lands under the status bar or nav bar, or off-screen.
- **Remembers its spot**: The resting position (edge or exact coordinates, depending on the snap setting) is saved to `SharedPreferences` and restored the next time `FloatingBubbleService` starts.
- **Auto-hide when empty**: With clip history empty, the bubble hides itself (it never hides while its own panel is open) and reappears the instant something is captured — capture keeps working in the background regardless, since it's driven by the system clipboard listener, not bubble visibility. Toggle this off in Settings to always keep the bubble visible.

## Settings

A full settings screen (`SettingsActivity` / `SettingsScreen.kt`, reachable from the panel's gear icon or from the onboarding screen) backed by `SettingsStore` (SharedPreferences + `StateFlow`, live across the running service and any UI):
- Snap bubble to screen edges (on/off)
- Auto-hide bubble when clipboard is empty (on/off)
- Auto-capture from system clipboard (on/off — disable to only save clips via manual screen capture)
- Show source app under each clip (on/off)
- Clip history limit (20 / 50 / 100 / 200), which also drives `ClipDao`'s FIFO cap
- Reset bubble position
- Clear all clips

## FIFO Logic

The Room database enforces a FIFO cap set by the "Clip history limit" setting (default 50):
1. On every insert, `ClipDao.pruneOldEntries(limit)` runs a DELETE that keeps only the `limit` newest rows by timestamp.
2. Back-to-back duplicate text is skipped at the repository layer.
3. The UI observes `ClipDao.observeRecent(limit)` (a `Flow<List<ClipEntry>>`) for live updates; `ClipDao.observeCount()` separately tracks the total count for bubble auto-hide, independent of the display limit.

## KernelSU Module Packaging

The GitHub Actions workflow zips these into a flashable module:

```
ClipMaster-KernelSU-Module.zip
├── META-INF/com/google/android/
│   ├── update-binary
│   └── updater-script
├── module.prop
├── customize.sh
└── system/app/ClipMaster/
    └── ClipMaster.apk
```

When flashed, `customize.sh` validates the device meets API 26+, sets file permissions, and the module manager overlays the APK onto `/system/app/`, granting it system-app privileges automatically.

## Signing & Versioning

- **Consistent signature across builds**: `keystore/clipmaster-release.jks` is committed to the repo and wired up as the `release` signing config in `app/build.gradle.kts`. Every build — any commit, CI or local — signs with this same key, so a newly built APK always installs as an update over a previous one instead of failing with "signatures do not match". For real production use, swap this for a private keystore delivered via GitHub Secrets instead.
- **Auto-incrementing version**: `versionCode`/`versionName` are derived from the CI-provided `GITHUB_RUN_NUMBER` env var (`app/build.gradle.kts`), so every GitHub Actions build gets its own unique, ever-increasing version with no manual bump. The KernelSU module's `module.prop` is stamped with the same build number during packaging (see `build.yml`). Local builds outside CI fall back to build `1`.

## Important Notes

- **Gradle wrapper JAR**: You'll need to run `gradle wrapper` once locally or add the `gradle-wrapper.jar` binary to the repo. GitHub's `setup-java` action with Gradle cache handles this, but if the jar is missing, add it via `gradle wrapper --gradle-version 8.8` from any machine with Gradle installed.
- **Accessibility caution**: Google Play restricts accessibility service usage. This app is designed for sideloading or system-level installation via KernelSU/Magisk.
