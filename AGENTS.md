# AGENTS.md — Swiftfox Android Browser

These instructions apply to the Swiftfox repository at `C:\Docker\Swiftfox` and refine the workspace-level `C:\Docker\AGENTS.md` for Android development.

## Project Identity

- **App Name:** Swiftfox
- **Application ID / Package:** `app.overbay.swiftfox`
- **Source Root:** `app/src/main/java/app/overbay/swiftfox/`
- **SDK Targets:** `minSdk 31` (Android 12+), `targetSdk 35` (Android 15), `compileSdk 36`
- **Stack:** Kotlin, Jetpack Compose, Material 3, Android `WebView` (`androidx.webkit` + `ProfileStore`), Jetpack `WindowManager`, Room (KSP), DataStore Preferences, OkHttp, Kotlinx Serialization.
- **Git Workflow:** Develop and verify on `beta`; promote tested milestones to `main`. Never push to a remote repository without explicit confirmation.

## Authoritative Context Files

- `PRODUCT.md` — Product truth, constraints, brand & accessibility commitments.
- `docs/PLAN.md` — 3-milestone architecture & feature roadmap (M1 Daily Driver, M2 Adblock/Zapper/Dark Reader/Userscripts/Reader, M3 Firefox/Zen Sync).
- `.impeccable/surfaces/` — Locked Direction Contract for the primary browser shell (`canon` Material 3 + Fox Amber Orange splashes).
- `DESIGN.md` & `.impeccable/design.json` — Generated from shipped Material 3 code at Milestone 1 finish review.

## Architecture Guardrails

1. **WebView Survival on Fold/Unfold:** `MainActivity` intentionally declares `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|uiMode"` so folding/unfolding never destroys live `WebView` instances or interrupts active media/page state. Keep Compose state saveable via ViewModels/`rememberSaveable` as well.
2. **Engine Boundary:** Keep raw Android `WebView` internals encapsulated inside `engine/` (`SwiftfoxWebView`, `BrowserSession`, `TabSessionPool`, `SwiftfoxWebViewClient`, `SwiftfoxChromeClient`, `BackgroundMediaService`). Compose UI interacts with reactive state and explicit commands.
3. **Background Media Playback:** Preserve `SwiftfoxWebView.onWindowVisibilityChanged` override (when `backgroundMediaEnabled` is active) and `BackgroundMediaService` foreground `MediaSession` handling so audio/YouTube survives screen-off and app switching.
4. **Private Mode & Container Isolation:** Private tabs use an isolated `WebViewCompat` `ProfileStore` profile (`swiftfox_private_session`), never record Room history, and clear cookies/storage when the last private tab closes. Optional per-Space container isolation uses `space_<id>` profiles.
5. **Posture Smart Reload:** Only reload a tab on fold/unfold when its *resolved* site mode (`MOBILE` vs `DESKTOP`, factoring in tab/site overrides and posture defaults) actually changes.
6. **Edge-to-Edge & 120Hz Smoothness:** Keep `enableEdgeToEdge()`, `android:windowSoftInputMode="adjustResize"`, `48x48dp` minimum touch targets, `sp` typography, Material 3 color roles across Day/Dark/AMOLED + 4 accent presets, and avoid allocating inside hot Compose draw/scroll paths.

## Build & Verification Commands (PowerShell)

```powershell
# Assemble debug / beta APK
.\gradlew.bat :app:assembleDebug

# Run local unit tests
.\gradlew.bat :app:testDebugUnitTest

# Check connected devices / emulators
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
android emulator list
```

## Gitignore Notes

Do not blanket-ignore `.impeccable/`. Commit `PRODUCT.md`, `DESIGN.md`, `.impeccable/config.json`, `.impeccable/surfaces/`, `.impeccable/design.json`, and `.impeccable/critique/ignore.md`. Runtime folders (`mocks/`, `questions/`, `review/`) maintain their own `.gitignore`.
