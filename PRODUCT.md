# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin + Jetpack Compose + Material 3, Android `WebView` (`androidx.webkit` with `ProfileStore` multi-profile, `addDocumentStartJavaScript`, `WebMessageListener`), Jetpack `WindowManager` (foldable posture detection), Room + DataStore, `adblock-rust` (via JNI), bundled Dark Reader & Mozilla Readability JS engines, and Mozilla `application-services` (`service-firefox-accounts`, `places`, `sync15`, `tabs`). Standard Gradle Android project at `C:\Docker\Swiftfox`, package `app.overbay.swiftfox`, `minSdk 31` (Android 12+), `targetSdk 35` (Android 15), `beta` → `main` Git workflow.

## Users

Primary user is Neal (and eventually Android power users / foldable owners) browsing on a modern 120Hz Android foldable phone who love Firefox and Zen Browser's philosophy, extensions, and sync ecosystem, but are frustrated by GeckoView's jittery scrolling text rendering and limited foldable/theming controls on Android.

## Product Purpose

Swiftfox is a lightweight, modern, Firefox-like Android web browser powered by Android's Chromium `WebView` so scrolling text renders with native 120Hz clarity while delivering the power-user features missing from stock Chrome and Firefox on mobile: native ad blocking with a clean Material 3 Element Zapper, built-in Dark Reader web-content night mode, a userscript and per-site rule engine, Reader Mode, independent folded/unfolded layouts, and native two-way Firefox/Zen Browser sync.

Success means Swiftfox works as a true daily-driver default Android browser from Milestone 1 onward—fast, crisp at 120Hz, genuinely comfortable both folded one-handed and unfolded on a large inner display, and seamlessly synced with Desktop Firefox / Zen Browser.

## Positioning

Combines Chromium's 120Hz Skia/WebView text scrolling clarity and automatic Google Play engine security updates with Firefox/Zen's open power-user ethos (Zen Spaces & Essentials, native Mozilla Sync, uBlock-grade filter lists, Material 3 Element Zapper, built-in Dark Reader, and `.user.js` extensibility)—without the 150 GB C++ maintenance burden of a Chromium fork or GeckoView's Android frame-pacing jitter.

## Operating Context

Daily mobile browsing on an Android foldable phone across two distinct physical postures:
- **Folded (compact cover screen):** Quick one-handed browsing, searching from the omnibox, zapping intrusive page overlays, reading articles in dark/AMOLED mode, switching tabs via URL-bar swipes.
- **Unfolded (expanded inner screen):** Deeper multi-tab reading and desktop-grade web apps with optional collapsible Zen-style vertical Spaces sidebar, customizable Top/Bottom/Split/Side-Rail toolbars, and independent Mobile vs. Desktop site defaults per posture.
- **Cross-device workflow:** Seamless continuity with Desktop Zen Browser / Firefox via Mozilla Accounts (bookmarks, history, open tabs, and Zen Spaces/Essentials) plus Chrome HTML bookmark import/export.

## Capabilities and Constraints

**Confirmed v1 Capabilities (delivered across 3 APK milestones):**
- **Core Browser Parity (M1):** Smart URL/search omnibox (default search engine: **Google**, with configurable alternatives, live search suggestions, open-tab/bookmark/history autocomplete, and keyword bangs), multi-tab pool surviving fold/unfold config changes, `ProfileStore`-isolated Private Mode, optional per-Space container isolation, downloads (+ Blob download bridge), find-in-page, long-press context menus, `<input type="file">` uploads, fullscreen HTML5 video, Picture-in-Picture & background media playback, external app link control (`Always in Swiftfox` / `Ask` / `Native app`), HTTPS-Only mode, per-site permissions (camera, mic, location, notifications, DRM/Widevine), URL-bar swipe gestures & pull-to-refresh, Save as PDF/Print, Clean URL sharing (stripping tracking query params), default Android browser registration, and Android System Autofill / Credential Manager integration.
- **Foldable & Workspace Controls (M1):** Independent Folded vs. Unfolded layout profiles (Top / Bottom / Split / Side Rail toolbar placement + Mobile vs. Desktop default site mode + per-site overrides; reloads active tab on fold/unfold only when resolved site mode changes), Zen-inspired Spaces + pinned Essentials.
- **Blocking, Theming & Extensibility (M2):** Embedded `adblock-rust` (uBO/EasyList + custom filter list URLs), native Material 3 Element Zapper ("Zap once" with Undo + "Block permanently" with `-/+` parent/child scope slider and per-site rule manager), decoupled web-content night mode (bundled open-source Dark Reader JS engine with AMOLED `#000000`, brightness/contrast/sepia, smart native-dark skip, 1-tap per-site toggle + Chromium Algorithmic Darkening Fast/Lite fallback), `.user.js` Userscript engine (`@match`/`@run-at`/`GM_*`), per-site overrides (cookies, User-Agent, JS toggle), and built-in Reader Mode (Mozilla Readability).
- **Sync & Interop (M3):** Native Firefox / Zen Browser sync via Mozilla `application-services` (Bookmarks, History, Open Tabs + Zen Spaces/Essentials) and Netscape HTML bookmark import/export for Chrome.

**Hard Technical Constraints:**
- Android `WebView` cannot execute `.crx` Chrome Web Store extensions; blocking and custom scripts are delivered through `adblock-rust`, `addDocumentStartJavaScript`, `WebMessageListener`, and per-site rules.
- Google blocks third-party Google Chrome Sync APIs; Chrome interoperability is limited to HTML bookmark import/export.
- Passwords rely on Android System Autofill / Credential Manager rather than a custom in-app password vault.
- Mozilla Accounts OAuth uses Fenix-compatible in-app WebView OAuth interception; local Room storage stays decoupled so local browsing never depends on sync availability.

## Brand Commitments

- **Name:** `Swiftfox` — positioned as a Firefox-like Android browser client that works smoothly at 120Hz.
- **Material & Aesthetic Commitments:** Familiar, native Material Design 3 Android browser conventions executed at full craft—lightweight, clean, and modern with rounded corners rather than harsh square edges, and deliberate extra splashes of accent color across active controls, pills, and highlights.
- **Craft Bar References:** Sit alongside **Google Chrome for Android / Pixel Material 3 Expressive apps** (crisp tactile pills, smooth gestures, zero-lag native feel) and **Arc Search / Soul / Samsung Internet** (playful rounded mobile touches, punchy bottom quick-controls sheet, expressive accent color highlights).
- **Theme Commitments:** Day / Night / System modes; Night supports both standard **Dark** and true **AMOLED Black (`#000000`)**; web content night mode toggles independently of app chrome theme.
- **Color Palette Commitments:** **Fox Amber Orange** as the default accent, with **Midnight Indigo**, **Forest**, and **Monochrome** selectable presets (plus optional Material You dynamic color).

## Evidence on Hand

- Greenfield repository at `C:\Docker\Swiftfox` (no existing code, icons, or production user metrics yet).
- Prior test capture on Neal's device confirmed 120Hz display output in Android Developer Options and TestUFO while highlighting GeckoView scroll text jitter compared to Chromium—establishing the core engineering rationale for Android `WebView`.

## Product Principles

1. **Webpage First, Chrome Quiet:** The browser frame stays lightweight, fast, and visually calm so rendered web content—and crisp 120Hz scrolling text—always commands attention.
2. **Posture-Native Flexibility:** Folded one-handed reach and unfolded tablet productivity are first-class, independent states—never a stretched phone UI or a cramped desktop UI.
3. **Power Tools Without Clutter:** Ad blocking, Element Zapping, Dark Reader, Userscripts, and per-site overrides are one tap away when needed and invisible when reading.
4. **Honest Platform Craft:** Embrace native Android Material 3 components, predictive Back, edge-to-edge insets, and system Autofill instead of fighting the OS.
5. **Local-First Resilience:** Tabs, Spaces, rules, and scripts live reliably on-device first; cloud sync enhances continuity without ever becoming a single point of failure.

## Accessibility & Inclusion

- Minimum `48×48 dp` interactive touch targets with comfortable spacing across both compact folded and expanded unfolded toolbars.
- Respect Android system font scaling (`sp` units everywhere) and system "Remove animations" preference.
- Maintain WCAG AA contrast across Day, Dark, and AMOLED Black themes for all four accent palettes (Fox Amber Orange, Midnight Indigo, Forest, Monochrome).
