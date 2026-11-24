# Swiftfox

Swiftfox is an Android web browser built on Android's system Chromium `WebView` so scrolling text stays smooth at 120Hz, paired with Firefox and Zen Browser workflows: native `adblock-rust` blocking, an interactive Element Zapper, built-in Dark Reader, `.user.js` userscripts, Reader Mode, independent folded/unfolded layouts, and two-way Firefox/Zen Sync.

## What It Does

- **120Hz WebView core:** Uses Android's Chromium `WebView` (`androidx.webkit`) for smooth scrolling and Play Store engine security updates, with a warm multi-tab pool that survives folding and unfolding without reloading active pages or stopping media.
- **Foldable posture profiles:** Detects folded vs. unfolded states with Jetpack `WindowManager` and stores separate toolbar placements (Top, Bottom, Split, or unfolded Side Rail), optional scroll-hiding toolbars, and default site modes (Mobile or Desktop) per posture. Tabs only reload on fold transitions when the resolved site mode actually changes.
- **Spaces, Essentials, and container isolation:** Organizes tabs into Zen-style Spaces with pinned Essentials, a slide-over Spaces sidebar drawer and card grid tab switcher when folded, a collapsible docked vertical sidebar when unfolded, ephemeral `ProfileStore`-isolated Private tabs, and optional per-Space cookie containers.
- **Native ad blocking and Element Zapper:** Embeds `adblock-rust` over JNI with EasyList, EasyPrivacy, uBlock Origin lists, custom filter list URLs, and background updates. Includes a bottom-HUD Element Zapper for temporary hide-with-undo or permanent per-site CSS rules with a parent/child scope slider.
- **Web dark mode and app themes:** Runs Day, Dark, and true `#000000` AMOLED chrome themes with four accent presets (Fox Amber, Midnight Indigo, Forest, Monochrome) plus optional Material You. Web-content dark mode toggles independently using the bundled Dark Reader engine (with AMOLED black and automatic skip for native dark sites) or Chromium Algorithmic Darkening.
- **Userscripts, per-site rules, and Reader Mode:** Installs `.user.js` scripts from URLs, local files, or an in-app editor (`@match`, `@exclude`, `@run-at`, `GM_addStyle`, `GM_getValue`/`GM_setValue`, `GM_xmlhttpRequest`). Per-site rules control adblock, Dark Reader, JavaScript, cookies, HTTPS-Only bypass, Mobile/Desktop lock, and custom User-Agents. Reader Mode extracts articles with Mozilla Readability into a native Compose view.
- **Firefox and Zen Browser sync:** Connects to Mozilla Accounts via in-app OAuth or QR code pairing (CameraX + ZXing) to sync bookmarks, history, synced open tabs, and Zen Browser Spaces/Essentials/pinned items. Also imports and exports standard Chrome Netscape HTML bookmark files.
- **Daily-driver browser tools:** Smart omnibox (Google default plus DuckDuckGo, Kagi, Brave, Startpage, or custom search, live suggestions, and `!bang` shortcuts), Picture-in-Picture and background audio playback, downloads with Blob/data-URI support, find-in-page, PDF/Print, Clean URL sharing that strips tracking parameters, default browser registration, and Android System Autofill support.

## Requirements

- **Device:** Android 12 or newer (`minSdk 31`, `targetSdk 35`), `arm64-v8a` (or `x86_64` emulator).
- **Building from source:** JDK 17 and Android SDK (`compileSdk 36`, configured in `local.properties` or `ANDROID_HOME`).
- **Optional (rebuilding Rust adblock binaries):** Docker, for running `scripts/build-adblock-jni.ps1`. Prebuilt `libswiftfox_adblock.so` binaries are committed in `app/src/main/jniLibs/`, so normal Gradle builds do not need Rust or the Android NDK installed locally.

## Building From Source

Clone the repository and build a debug APK with Gradle:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Useful Gradle flags:
- `-PbetaBuild=true`: Build side-by-side as **Swiftfox Beta** (`app.overbay.swiftfox.beta`). This activates automatically when checked out on the `beta` branch.
- `-PincludeX86=true`: Include `x86_64` native libraries for Android emulators. Default builds package `arm64-v8a` only to keep phone APKs smaller.

Helper scripts in `scripts/` (PowerShell on Windows):
- `scripts/build-apk.ps1` (or `scripts/build-apk.cmd`): Runs unit tests, builds the debug APK, and copies it to the repo root (`Swiftfox-debug.apk` or `SwiftfoxBeta-debug.apk`). Pass `-SkipUpload` to skip uploading to GitHub Releases, or `-IncludeX86` for emulator builds.
- `scripts/build-adblock-jni.ps1`: Rebuilds `native/adblock-jni/` inside Docker (`cargo-ndk`, locked via `Cargo.lock`, 16 KB page-size aligned) into `app/src/main/jniLibs/`.

Signed release builds require keystore credentials via `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` environment variables (or `release.keystore.*` properties in `local.properties`).

## Known Limitations

- **No Chrome Web Store `.crx` extensions:** Android `WebView` does not support Chrome extensions. Filtering and site customization rely on `adblock-rust`, `document_start` script injection, userscripts, and per-site rules. `shouldInterceptRequest` cannot inspect Service Worker fetches, WebSockets, or POST bodies.
- **Chrome sync:** Google restricts Chrome Sync APIs to first-party builds, so Chrome interop is limited to Netscape HTML bookmark import and export.
- **Mozilla Accounts OAuth:** Third-party Android clients authenticate through Fenix-compatible OAuth inside an isolated in-app `WebView`. Local Room storage stays decoupled so browsing works even if upstream sync endpoints change.
- **Passwords:** Handled through Android System Autofill and Credential Manager rather than an in-app password vault.

## Project Docs

- [PRODUCT.md](PRODUCT.md): Product scope, constraints, and accessibility targets.
- [DESIGN.md](DESIGN.md): Material 3 color tokens, typography, foldable layouts, and component specs.
- [docs/PLAN.md](docs/PLAN.md): System architecture, repository layout, and milestone status.
