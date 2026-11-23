# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin + Jetpack Compose + Material 3, Android `WebView` (`androidx.webkit` with `ProfileStore` multi-profile, `addDocumentStartJavaScript`, `WebMessageListener`), Jetpack `WindowManager` (foldable posture detection), Room (KSP + exported schemas) + DataStore + WorkManager, `adblock-rust` (via Docker `cargo-ndk` JNI with Kotlin fallback), bundled Dark Reader & Mozilla Readability JS engines, Mozilla Android Components / `application-services` (`service-firefox-accounts`, `browser-storage-sync`, `support-appservices`, custom Sync 1.5 HAWK/AES-GCM client for Zen Spaces), and CameraX + ZXing QR pairing. Standard Gradle Android project (`app.overbay.swiftfox`, beta side-by-side `app.overbay.swiftfox.beta`), `minSdk 31` (Android 12+), `targetSdk 35` (Android 15), `compileSdk 36`, `beta` -> `main` Git workflow.

## Users

Primary user is Neal (and eventually Android power users / foldable owners) browsing on a 120Hz Android foldable phone who want Firefox and Zen Browser's philosophy, extensions, and sync ecosystem without GeckoView's jittery scrolling text rendering or limited foldable/theming controls on Android.

## Product Purpose

Swiftfox is a lightweight, Firefox-like Android web browser powered by Android's Chromium `WebView` so scrolling text renders with native 120Hz clarity while delivering the power-user features missing from stock Chrome and Firefox on mobile: native ad blocking with a Material 3 Element Zapper, built-in Dark Reader web-content night mode, a userscript and per-site rule engine, Reader Mode, independent folded/unfolded layouts, and native two-way Firefox/Zen Browser sync.

Success means Swiftfox works as a reliable daily-driver default Android browser—fast, crisp at 120Hz, comfortable both folded one-handed and unfolded on a large inner display, and synced with Desktop Firefox / Zen Browser.

## Positioning

Combines Chromium's 120Hz Skia/WebView text scrolling clarity and automatic Google Play engine security updates with Firefox/Zen's open power-user ethos (Zen Spaces & Essentials, native Mozilla Sync, uBlock-grade filter lists, Material 3 Element Zapper, built-in Dark Reader, and `.user.js` extensibility)—without the 150 GB C++ maintenance burden of a Chromium fork or GeckoView's Android frame-pacing jitter.

## Operating Context

Daily mobile browsing on an Android foldable phone across two distinct physical postures:
- **Folded (compact cover screen):** Quick one-handed browsing, searching from the omnibox, zapping intrusive page overlays, reading articles in dark/AMOLED mode, switching tabs via URL-bar swipes.
- **Unfolded (expanded inner screen):** Deeper multi-tab reading and desktop-grade web apps with optional collapsible Zen-style vertical Spaces sidebar, customizable Top/Bottom/Split/Side-Rail toolbars, and independent Mobile vs. Desktop site defaults per posture.
- **Cross-device workflow:** Continuity with Desktop Zen Browser / Firefox via Mozilla Accounts (bookmarks, history, open tabs, QR device pairing, and Zen Spaces/Essentials) plus Chrome HTML bookmark import/export.

## Capabilities and Constraints

**v1 Capabilities (M1–M3 implemented on `beta` at `v0.3.0-beta`, entering real-device verification and polish before `main` promotion / `v1.0.0`):**
- **Core Browser Parity (M1 — Implemented):** Smart URL/search omnibox (default search engine: **Google**, with DuckDuckGo, Kagi, Brave, Startpage, and custom URL options, live search suggestions, open-tab/bookmark/history autocomplete, and keyword bangs), multi-tab pool surviving fold/unfold config changes, `ProfileStore`-isolated Private Mode, optional per-Space container isolation, downloads (+ Blob/data-URI download bridge), find-in-page, long-press context menus, `<input type="file">` uploads, fullscreen HTML5 video, Picture-in-Picture & background media playback, external app link control (`Always in Swiftfox` / `Ask` / `Native app`), HTTPS-Only mode, per-site permissions (camera, mic, location, notifications, DRM/Widevine), URL-bar swipe gestures & pull-to-refresh, Save as PDF/Print, Clean URL sharing (stripping tracking query params), default Android browser registration, and Android System Autofill / Credential Manager integration.
- **Foldable & Workspace Controls (M1 — Implemented):** Independent Folded vs. Unfolded layout profiles (Top / Bottom / Split / Side Rail toolbar placement + Mobile vs. Desktop default site mode + per-site overrides; reloads active tab on fold/unfold only when resolved site mode changes), Zen-inspired Spaces + pinned Essentials.
- **Blocking, Theming & Extensibility (M2 — Implemented):** Embedded `adblock-rust` JNI (uBO/EasyList/EasyPrivacy + custom filter list URLs + WorkManager background updates + Kotlin fallback), native Material 3 Element Zapper ("Zap once" with Undo + "Block permanently" with `-/+` parent/child scope slider and per-site rule manager), decoupled web-content night mode (bundled open-source Dark Reader JS engine with AMOLED `#000000`, brightness/contrast/sepia, smart native-dark skip, 1-tap per-site toggle + Chromium Algorithmic Darkening Fast/Lite fallback), `.user.js` Userscript engine (`@match`/`@run-at`/`GM_*`), per-site overrides (cookies, User-Agent presets/custom UA, JS toggle, adblock pause, Dark Reader override, site mode lock), and built-in Reader Mode (Mozilla Readability + native Compose typography/surface controls).
- **Sync & Interop (M3 — Implemented):** Native Firefox / Zen Browser sync via Mozilla `application-services` (in-app isolated FxA OAuth WebView + CameraX/ZXing QR pairing + manual pairing link, bidirectional Bookmarks, History, Synced Open Tabs, WorkManager periodic sync, and Zen Spaces/Essentials/Pinned items via custom Sync 1.5 HAWK/AES-GCM client) plus Netscape HTML bookmark import/export for Chrome.

**Hard Technical Constraints:**
- Android `WebView` cannot execute `.crx` Chrome Web Store extensions; blocking and custom scripts are delivered through `adblock-rust`, `addDocumentStartJavaScript`, `WebMessageListener`, and per-site rules.
- Google blocks third-party Google Chrome Sync APIs; Chrome interoperability is limited to HTML bookmark import/export.
- Passwords rely on Android System Autofill / Credential Manager rather than a custom in-app password vault.
- Mozilla Accounts OAuth uses Fenix-compatible in-app WebView OAuth interception inside a dedicated `ProfileStore` profile; local Room storage stays decoupled so local browsing never depends on sync availability.

## Brand Commitments

- **Name:** `Swiftfox` — positioned as a Firefox-like Android browser client that works smoothly at 120Hz.
- **Material & Aesthetic Commitments:** Familiar, native Material Design 3 Android browser conventions—lightweight, clean, with rounded corners rather than harsh square edges, and deliberate extra splashes of accent color across active controls, pills, and highlights.
- **Craft Bar References:** Sit alongside **Google Chrome for Android / Pixel Material 3 Expressive apps** (crisp tactile pills, smooth gestures, zero-lag native feel) and **Arc Search / Soul / Samsung Internet** (playful rounded mobile touches, punchy bottom quick-controls sheet, expressive accent color highlights).
- **Theme Commitments:** Day / Night / System modes; Night supports both standard **Dark** and true **AMOLED Black (`#000000`)**; web content night mode toggles independently of app chrome theme.
- **Color Palette Commitments:** **Fox Amber Orange** as the default accent, with **Midnight Indigo**, **Forest**, and **Monochrome** selectable presets (plus optional Material You dynamic color).

## Evidence on Hand

- All three v1 milestones (M1 Core + Foldable UX, M2 Adblock/Zapper/Dark Reader/Userscripts/Reader, M3 Mozilla/Zen Sync + Chrome HTML interop) are implemented on `beta` (`v0.3.0-beta`) with Room v3 schema export, JNI `adblock-rust` binaries (`arm64-v8a` + `x86_64`, 16 KB aligned), JVM unit tests, and side-by-side `Swiftfox Beta` APK delivery via GitHub Releases (`beta-latest`).
- M1 emulator screenshots (folded phone, unfolded tablet, Day/Dark/AMOLED, `1.3×` font scale) validated the initial Material 3 system documented in `DESIGN.md`; live physical foldable daily-driver testing and M2/M3 polish are the current focus prior to promoting `beta` -> `main` and cutting `v1.0.0`.
- Prior test capture on Neal's device confirmed 120Hz display output in Android Developer Options and TestUFO while highlighting GeckoView scroll text jitter compared to Chromium—establishing the core engineering rationale for Android `WebView`.

## Product Principles

1. **Webpage First, Chrome Quiet:** The browser frame stays lightweight, fast, and visually calm so rendered web content—and crisp 120Hz scrolling text—always commands attention.
2. **Posture-Native Flexibility:** Folded one-handed reach and unfolded tablet productivity are first-class, independent states—never a stretched phone UI or a cramped desktop UI.
3. **Power Tools Without Clutter:** Ad blocking, Element Zapping, Dark Reader, Userscripts, and per-site overrides are one tap away when needed and invisible when reading.
4. **Honest Platform Craft:** Embrace native Android Material 3 components, predictive Back, edge-to-edge insets, and system Autofill instead of fighting the OS.
5. **Local-First Resilience:** Tabs, Spaces, rules, and scripts live reliably on-device first; cloud sync enhances continuity without ever becoming a single point of failure.

## Accessibility & Inclusion

- Minimum `48×48 dp` interactive touch targets with comfortable spacing across both compact folded and expanded unfolded toolbars.
- Respect Android system font scaling (`sp` units everywhere) and system "Remove animations" preference (`ANIMATOR_DURATION_SCALE == 0f`).
- Maintain WCAG AA contrast across Day, Dark, and AMOLED Black themes for all four accent palettes (Fox Amber Orange, Midnight Indigo, Forest, Monochrome).
