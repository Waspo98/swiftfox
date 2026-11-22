# AGENTS.md — Swiftfox Android Browser

These instructions apply to the Swiftfox repository at `C:\Docker\Swiftfox` (`https://github.com/Waspo98/swiftfox`) and take precedence over `C:\Docker\AGENTS.md` for Android development inside Swiftfox.

## Project Identity

- **App Name:** `Swiftfox` (Beta: `Swiftfox Beta`)
- **Application ID / Package:** `app.overbay.swiftfox` (Beta: `app.overbay.swiftfox.beta`)
- **Root Kotlin Package / Namespace:** `app.overbay.swiftfox`
- **Source Root:** `app/src/main/java/app/overbay/swiftfox/`
- **SDK Targets:** `minSdk 31` (Android 12+), `targetSdk 35` (Android 15), `compileSdk 36`
- **Stack:** Kotlin, Jetpack Compose, Material 3, Android `WebView` (`androidx.webkit` + `ProfileStore`), Jetpack `WindowManager`, Room (KSP), DataStore Preferences, OkHttp, Kotlinx Serialization.
- **Repository:** `https://github.com/Waspo98/swiftfox` (Public)

## Authoritative Context Files

- `PRODUCT.md` — Product truth, constraints, brand & accessibility commitments.
- `DESIGN.md` & `.impeccable/design.json` — Token-accurate Material 3 design system generated from shipped M1 code.
- `docs/PLAN.md` — 3-milestone architecture & feature roadmap (M1 Daily Driver, M2 Adblock/Zapper/Dark Reader/Userscripts/Reader, M3 Firefox/Zen Sync).
- `.impeccable/surfaces/` — Locked Direction Contract for the primary browser shell (`canon` Material 3 + Fox Amber Orange splashes).

## Architecture Guardrails

1. **WebView Survival on Fold/Unfold:** `MainActivity` intentionally declares `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|uiMode"` so folding/unfolding never destroys live `WebView` instances or interrupts active media/page state. Keep Compose state saveable via ViewModels/`rememberSaveable` as well.
2. **Engine Boundary:** Keep raw Android `WebView` internals encapsulated inside `engine/` (`SwiftfoxWebView`, `BrowserSession`, `TabSessionPool`, `SwiftfoxWebViewClient`, `SwiftfoxChromeClient`, `BackgroundMediaService`). Compose UI interacts with reactive state and explicit commands.
3. **Background Media Playback:** Preserve `SwiftfoxWebView.onWindowVisibilityChanged` override (when `backgroundMediaEnabled` is active) and `BackgroundMediaService` foreground `MediaSession` handling so audio/YouTube survives screen-off and app switching.
4. **Private Mode & Container Isolation:** Private tabs use an isolated `WebViewCompat` `ProfileStore` profile (`swiftfox_private_session`), never record Room history, and clear cookies/storage when the last private tab closes. Optional per-Space container isolation uses `space_<id>` profiles.
5. **Posture Smart Reload:** Only reload a tab on fold/unfold when its *resolved* site mode (`MOBILE` vs `DESKTOP`, factoring in tab/site overrides and posture defaults) actually changes.
6. **Edge-to-Edge & 120Hz Smoothness:** Keep `enableEdgeToEdge()`, `android:windowSoftInputMode="adjustResize"`, `48x48dp` minimum touch targets with `>=8dp` spacing, `sp` typography, Material 3 color roles across Day/Dark/AMOLED + 4 accent presets, `ANIMATOR_DURATION_SCALE == 0f` reduced-motion guards, and avoid allocating inside hot Compose draw/scroll paths.

## Execution Efficiency & Guardrails

- **Zero-Polling on Asynchronous Tasks (CRITICAL):** Long-running commands (Gradle builds, unit tests) run in the background. The platform automatically wakes the agent with a notification when the task finishes. **NEVER poll `manage_task: status` in a loop**, and **NEVER spam `schedule` timers** to check build progress.
- **Batch Build Pipeline:** While iterating on Kotlin code, validate with `.\gradlew.bat :app:compileDebugKotlin` or `.\gradlew.bat :app:testDebugUnitTest`. Run the full assembly and release upload (`cmd /c scripts\build-apk.cmd`) once all edits for the task are complete.
- **Headless / No Unsolicited Emulators:** Do not launch Android emulators via ADB/CLI unless explicitly requested (e.g., for visual UI audits/screenshots). Rely on Gradle compilation, unit tests, and GitHub Release APK downloads for everyday changes.
- **Destructive Command Ban:** Never execute destructive git commands (`git reset --hard`, `git clean -fd`, `git restore .`, `git checkout -- .`, `git push --force`) without explicit user instruction.

## Git Workflow, Standalone Beta Identity & Automatic APK Delivery

- **Mandatory Session Kickoff Protocol (Every New Chat):**
  - In the first response of every new chat session, inspect the current branch and status:
    ```powershell
    git status; git branch --show-current
    ```
  - Explicitly confirm with the user which branch to work on before modifying files.
- **Branch Strategy:** Develop and test on `beta`; promote verified milestones to `main` via `/promote` and cut versioned signed releases via `/release`.
- **Automatic APK Root Copy & Standalone Beta Package Identity (CRITICAL):**
  - `app/build.gradle.kts` automatically detects the `beta` branch via `.git/HEAD` (or `-PbetaBuild=true`) so **Swiftfox Beta** installs side-by-side with **Swiftfox** on Android without conflict:
    - **Standard Branches (`main` / `feature/*`):** `applicationId = "app.overbay.swiftfox"`, app title `"Swiftfox"`, copied to `Swiftfox-debug.apk` (removes stale beta APKs).
    - **Beta Branch (`beta` / `Beta`):** `applicationId = "app.overbay.swiftfox.beta"`, version suffix `"-beta"`, overlays `app/src/beta/res/` (`app_name = "Swiftfox Beta"`), copied to `SwiftfoxBeta-debug.apk` (removes stale standard APKs).
- **MANDATORY AUTOMATIC APK BUILD & DELIVERY RULE (Final Turn of Every Code Change):**
  - Unless the user explicitly says otherwise, at the conclusion of **every** task that modifies `:app` code or resources, you **MUST automatically run `cmd /c scripts\build-apk.cmd`** to run unit tests, compile the APK, and upload it to GitHub Releases without waiting to be asked.
  - Your **final completion response** MUST always include the direct clickable download link:
    - **Beta Branch:**
      ```markdown
      📥 **[Download SwiftfoxBeta-debug.apk](https://github.com/Waspo98/swiftfox/releases/download/beta-latest/SwiftfoxBeta-debug.apk)**
      ```
    - **Main / General Branches:**
      ```markdown
      📥 **[Download Swiftfox-debug.apk](https://github.com/Waspo98/swiftfox/releases/download/debug-latest/Swiftfox-debug.apk)**
      ```
  - Intermediate responses (such as yielding while Gradle compiles in the background) do NOT include this link.

## Gitignore Notes

Do not blanket-ignore `.impeccable/`. Commit `PRODUCT.md`, `DESIGN.md`, `.impeccable/config.json`, `.impeccable/surfaces/`, `.impeccable/design.json`, and `.impeccable/critique/ignore.md`. Runtime folders (`mocks/`, `questions/`, `review/`) maintain their own `.gitignore`, and root `*.apk` files remain gitignored.
