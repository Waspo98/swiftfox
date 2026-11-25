# AGENTS.md — Swiftfox Android Browser

These instructions apply to the Swiftfox repository at `C:\Docker\Swiftfox` (`https://github.com/Waspo98/swiftfox`) and take precedence over `C:\Docker\AGENTS.md` for Android development inside Swiftfox.

## Project Identity

- **App Name:** `Swiftfox` (Beta: `Swiftfox Beta`)
- **Application ID / Package:** `app.overbay.swiftfox` (Beta: `app.overbay.swiftfox.beta`)
- **Root Kotlin Package / Namespace:** `app.overbay.swiftfox`
- **Source Root:** `app/src/main/java/app/overbay/swiftfox/`
- **SDK Targets:** `minSdk 31` (Android 12+), `targetSdk 35` (Android 15), `compileSdk 36`
- **Stack:** Kotlin, Jetpack Compose, Material 3, Android `WebView` (`androidx.webkit` + `ProfileStore`, `addDocumentStartJavaScript`, `WebMessageListener`), Jetpack `WindowManager`, Room (KSP + exported schemas), DataStore Preferences, WorkManager, OkHttp, Kotlinx Serialization, `adblock-rust` (Rust/JNI via Docker `cargo-ndk`), bundled JS assets (`darkreader.min.js`, `readability.min.js`, `zapper.js`, `gm_shim.js`), Mozilla Android Components / `application-services` + JNA (`service-firefox-accounts`, `browser-storage-sync`, `support-appservices`, custom Sync 1.5 HAWK/AES-GCM client for Zen Spaces), and CameraX + ZXing (QR device pairing).
- **Repository:** `https://github.com/Waspo98/swiftfox` (Public)

## Authoritative Context Files

- `README.md` — Public user-facing overview, build instructions, and limitations.
- `PRODUCT.md` — Product truth, constraints, brand & accessibility commitments.
- `DESIGN.md` & `.impeccable/design.json` — Token-accurate Material 3 design system covering M1–M3 browser chrome, power-tool overlays, and sync screens.
- `docs/PLAN.md` — Architecture reference, repo layout, and M1–M3 milestone status.
- `.impeccable/surfaces/` — Locked Direction Contract for the primary browser shell (`canon` Material 3 + Fox Amber Orange splashes).
- `app/schemas/` — Exported Room database schemas (committed; verify migrations against these).

## Architecture Guardrails

1. **WebView Survival on Fold/Unfold:** `MainActivity` intentionally declares `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|uiMode"` so folding/unfolding never destroys live `WebView` instances or interrupts active media/page state. Keep Compose state saveable via ViewModels/`rememberSaveable` as well.
2. **Engine & JS Bridge Boundary:** Keep raw Android `WebView` internals and JS injection encapsulated inside `engine/` (`SwiftfoxWebView`, `BrowserSession`, `TabSessionPool`, `SwiftfoxWebViewClient`, `SwiftfoxChromeClient`, `ScriptInjector`, `BackgroundMediaService`) and `app/src/main/assets/js/`. Keep `WebViewCompat.addWebMessageListener` main-frame gated, and keep Compose UI interacting through reactive state and explicit commands.
3. **Background Media Playback:** Preserve `SwiftfoxWebView.onWindowVisibilityChanged` override (when `backgroundMediaEnabled` is active) and `BackgroundMediaService` foreground `MediaSession` handling so audio/YouTube survives screen-off and app switching.
4. **Private Mode, Container & Auth Profile Isolation:** Private tabs use an isolated `WebViewCompat` `ProfileStore` profile (`swiftfox_private_session`), never record Room history, and clear cookies/storage when the last private tab closes. Optional per-Space container isolation uses `space_<id>` profiles, and Mozilla Account OAuth in `FxAAuthWebViewOverlay` uses a dedicated auth profile (`FXA_AUTH_PROFILE_NAME`). **Always call `ProfileStore.getInstance()` on the Main thread.**
5. **Posture Smart Reload:** Only reload a tab on fold/unfold when its *resolved* site mode (`MOBILE` vs `DESKTOP`, factoring in tab/site overrides and posture defaults) actually changes.
6. **Native `adblock-rust` JNI & 16 KB Page Alignment:** The Rust JNI crate lives in `native/adblock-jni/` (pinned by `Cargo.lock`) and builds via `scripts/build-adblock-jni.ps1` (Docker `cargo-ndk` image with `-C link-arg=-Wl,-z,max-page-size=16384` for Android 15+ 16 KB page-size support). Whenever `native/adblock-jni/` changes, rebuild and commit the updated `.so` libraries in `app/src/main/jniLibs/{arm64-v8a,x86_64}/`. Preserve the Kotlin fallback (`AdblockMatcher`) so JVM unit tests run cleanly without JNI.
7. **Room Schema Export & Explicit Migrations:** `SwiftfoxDatabase` (currently v6 with `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`, `MIGRATION_4_5`, and `MIGRATION_5_6`) uses `exportSchema = true` outputting to `app/schemas/`. For every future schema change, bump the version, add an explicit `Migration` (never destructive upgrade fallbacks), commit the new exported schema JSON, and extend `app/src/androidTest/.../SwiftfoxMigrationAndSmokeTest.kt`.
8. **Sync Isolation, Backup Exclusions & R8 Rules:** Local Room storage stays decoupled from Mozilla Sync so browsing never depends on network/FxA state. Keep `app/src/main/res/xml/data_extraction_rules.xml` excluding Room databases, `places.sqlite`, `tabs.sqlite`, and `app_webview` from Android cloud backup/device transfer. Keep `app/proguard-rules.pro` intact for `@JavascriptInterface`, `NativeAdblockBridge`, JNA, `mozilla.appservices.**`, and `mozilla.components.**`. Keep `libs.mozilla.concept.toolbar` in `app/build.gradle.kts` (`PlacesBookmarksStorage` and `PlacesHistoryStorage` implement its `AutocompleteProvider` supertype).
9. **Modularity & ViewModel Boundary:** `BrowserViewModel.kt` (~3.3k lines) and `BrowserScreen.kt` (~1.7k lines) are already large. Do not add new standalone feature subsystems directly into `BrowserViewModel`—extract focused state holders/controllers under `features/` or `ui/` and decompose incrementally when touching existing flows.
10. **Edge-to-Edge & 120Hz Smoothness:** Keep `enableEdgeToEdge()`, `android:windowSoftInputMode="adjustResize"`, `48x48dp` minimum touch targets with `>=8dp` spacing, `sp` typography, Material 3 color roles across Day/Dark/AMOLED + 4 accent presets, `ANIMATOR_DURATION_SCALE == 0f` reduced-motion guards, and avoid allocating inside hot Compose draw/scroll paths (`drawWithCache`, stable `LazyColumn`/`LazyVerticalGrid` keys and `contentType`).

## Execution Efficiency & Guardrails

- **Zero-Polling on Asynchronous Tasks (CRITICAL):** Long-running commands (Gradle builds, unit tests) run in the background. The platform automatically wakes the agent with a notification when the task finishes. **NEVER poll `manage_task: status` in a loop**, and **NEVER spam `schedule` timers** to check build progress.
- **Batch Build & Verification Pipeline:**
  - While iterating on Kotlin code, validate with `.\gradlew.bat :app:compileDebugKotlin`, `.\gradlew.bat :app:testDebugUnitTest`, or `.\gradlew.bat :app:compileDebugAndroidTestKotlin`.
  - For lint or R8 verification without a release keystore, run `.\gradlew.bat :app:lintDebug` or `.\gradlew.bat :app:minifyReleaseWithR8` (signing is only enforced for release packaging tasks like `assembleRelease`/`bundleRelease`).
  - **ABI Filtering:** Debug/phone builds default to `arm64-v8a` only (saving ~28 MB of unused `x86_64` native libraries). Pass `-PincludeX86=true` to Gradle (or `-IncludeX86` to `scripts/build-apk.ps1`) when building for an `x86_64` Android emulator.
- **Headless / No Unsolicited Emulators:** Do not launch Android emulators via ADB/CLI unless explicitly requested (e.g., for visual UI audits/screenshots). Rely on Gradle compilation, unit tests, lint, CI, and GitHub Release APK downloads for everyday changes.
- **Destructive Command Ban:** Never execute destructive git commands (`git reset --hard`, `git clean -fd`, `git restore .`, `git checkout -- .`, `git push --force`) without explicit user instruction.

## Git Workflow, Standalone Beta Identity & Automatic APK Delivery

- **Mandatory Session Kickoff Protocol (Every New Chat):**
  - In the first response of every new chat session, inspect the current branch and status:
    ```powershell
    git status; git branch --show-current
    ```
  - Explicitly confirm with the user which branch to work on before modifying files.
- **Branch Strategy:** Develop and test on `beta`; promote verified milestones to `main` via `/promote` and cut versioned signed releases via `/release`. Commit changes cleanly before shipping milestone builds to `beta-latest` (`scripts/build-apk.ps1` prints a warning if uploading from a dirty working tree).
- **Automatic APK Root Copy & Standalone Beta Package Identity (CRITICAL):**
  - `app/build.gradle.kts` automatically detects the `beta` branch via `.git/HEAD` (including git worktrees) or `-PbetaBuild=true` so **Swiftfox Beta** installs side-by-side with **Swiftfox** on Android without conflict:
    - **Standard Branches (`main` / `feature/*`):** `applicationId = "app.overbay.swiftfox"`, app title `"Swiftfox"`, copied to `Swiftfox-debug.apk` (removes stale beta APKs).
    - **Beta Branch (`beta` / `Beta`):** `applicationId = "app.overbay.swiftfox.beta"`, version suffix `"-beta"`, overlays `app/src/beta/res/` (`app_name = "Swiftfox Beta"`), copied to `SwiftfoxBeta-debug.apk` (removes stale standard APKs).
- **AUTOMATIC APK BUILD & DELIVERY RULE (Final Turn of User-Facing `:app` Changes):**
  - Unless the user explicitly says otherwise, at the conclusion of every task that makes **user-testable changes to `:app` code, resources, or build packaging**, automatically run `cmd /c scripts\build-apk.cmd` to run unit tests, compile the `arm64-v8a` APK, and upload it to GitHub Releases without waiting to be asked.
  - Pure documentation, CI-only, or non-user-facing internal/test-only tasks do **not** need a GitHub Release upload (run Gradle verification or `cmd /c scripts\build-apk.cmd -SkipUpload` instead unless an APK is requested).
  - Whenever an APK is uploaded, your **final completion response** MUST include the direct clickable download link:
    - **Beta Branch:**
      ```markdown
      📥 **[Download SwiftfoxBeta-debug.apk](https://github.com/Waspo98/swiftfox/releases/download/beta-latest/SwiftfoxBeta-debug.apk)**
      ```
    - **Main / General Branches:**
      ```markdown
      📥 **[Download Swiftfox-debug.apk](https://github.com/Waspo98/swiftfox/releases/download/debug-latest/Swiftfox-debug.apk)**
      ```
  - Intermediate responses (such as yielding while Gradle compiles in the background) do NOT include this link.

## Gitignore & Repo Hygiene Notes

- Do not blanket-ignore `.impeccable/`. Commit `PRODUCT.md`, `DESIGN.md`, `.impeccable/surfaces/`, and `.impeccable/design.json`. Runtime folders (`mocks/`, `questions/`, `review/`) maintain their own `.gitignore`, local overrides (`.impeccable/config.local.json`, `.impeccable/reference/`) stay ignored, and root `*.apk` files remain gitignored.
- Commit exported Room schemas under `app/schemas/` and the pinned Rust lockfile at `native/adblock-jni/Cargo.lock`.
- Keep `.gitattributes` enforcing LF across source/Gradle/Rust files and CRLF for Windows `.bat`/`.cmd`/`.ps1` scripts.
