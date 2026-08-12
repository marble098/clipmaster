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
3. Download artifacts from the Actions tab:
   - **ClipMaster-APK** — signed standalone APK
   - **ClipMaster-KernelSU-Module** — flashable .zip for KernelSU/Magisk

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

## Smart Bubble Positioning

The floating bubble behaves like a chat head:
- **Edge snapping**: Releasing a drag glides the bubble to whichever screen edge (left/right) it's closer to, animated with a `ValueAnimator`.
- **Bounds-aware**: The bubble is clamped vertically during drag and after rotation so it never lands under the status bar or nav bar.
- **Remembers its spot**: The resting edge and vertical position are saved to `SharedPreferences` and restored the next time `FloatingBubbleService` starts.
- **Rotation-aware**: `onConfigurationChanged` re-clamps and re-snaps the bubble when the screen rotates.

## FIFO Logic

The Room database enforces a strict 50-entry cap:
1. On every insert, `ClipDao.pruneOldEntries()` runs a DELETE that keeps only the 50 newest rows by timestamp.
2. Back-to-back duplicate text is skipped at the repository layer.
3. The UI observes `ClipDao.observeRecent()` (a `Flow<List<ClipEntry>>`) for live updates.

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

## Important Notes

- **Gradle wrapper JAR**: You'll need to run `gradle wrapper` once locally or add the `gradle-wrapper.jar` binary to the repo. GitHub's `setup-java` action with Gradle cache handles this, but if the jar is missing, add it via `gradle wrapper --gradle-version 8.8` from any machine with Gradle installed.
- **Signing**: The CI generates a throwaway debug keystore. For production, replace it with GitHub Secrets (`KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`) and decode in the workflow.
- **Accessibility caution**: Google Play restricts accessibility service usage. This app is designed for sideloading or system-level installation via KernelSU/Magisk.
