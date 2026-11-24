---
version: 1
slug: "rbay-swiftfox-ui-browser-browserscreen-kt-7fa215e1"
primary_target: "app/src/main/java/app/overbay/swiftfox/ui/browser/BrowserScreen.kt"
related_targets: ["app/src/main/java/app/overbay/swiftfox/ui/theme/Theme.kt","app/src/main/java/app/overbay/swiftfox/ui/tabs/SpacesTabSwitcher.kt","app/src/main/java/app/overbay/swiftfox/ui/tabs/ArcSpacesSidebar.kt","app/src/main/java/app/overbay/swiftfox/ui/settings/QuickActionsSection.kt"]
---

# Swiftfox Primary Browser Shell

- **Mode:** `operate`
- **Platform:** `android` (Jetpack Compose + Material 3, folded compact phone & unfolded expanded foldable)
- **Audience & Job:** Daily-driver Android foldable user searching via Google, reading at crisp 120Hz, toggling ad-block/Dark Reader/Element Zapper/Desktop UA cleanly, and organizing tabs across Zen-synced Spaces & Essentials.
- **Craft Bar:** Google Chrome for Android + Pixel Material 3 Expressive apps (tactile pills, fluid gestures, native polish) blended with Arc Search / Zen / Samsung Internet (playful rounded geometry, slide-over Spaces sidebar drawer, Settings-hosted Quick Actions card, expressive accent splashes).

## Direction contract

- **THESIS:** Familiar Android Material 3 browser ergonomics elevated with generous Fox Amber accent splashes, an Arc/Zen Spaces sidebar, and tactile Settings Quick Actions tiles, refusing sterile grey utility chrome while keeping 120Hz web content center stage.
- **OWN-WORLD:** Material 3 tonal surfaces across Day (`#FAF8F6`), Dark (`#18161B`), and true AMOLED (`#000000`) energized by deliberate Fox Amber Orange (`#E86A17` primary, warm tonal amber containers `#3A1F0D` / `#FFE2CC`) plus switchable Indigo, Forest, and Monochrome palettes; `24–28dp` pill radii, Roboto/Material type roles, and accent-lit active Space pills, shield counters, omnibox focus rings, and quick-toggle tiles.
- **STORY:** Users immediately recognize every Android browser affordance, trust its 120Hz responsiveness, and reach power tools (Shield, Dark Reader, Element Zapper, Desktop/Mobile posture switch, Spaces) in one effortless thumb motion.
- **FIRST VIEWPORT:** Edge-to-edge `WebView` canvas (or calm Swiftfox start screen with Google search capsule, Fox Amber Essentials tiles, and Space switcher pills) anchored by a posture-configurable rounded pill toolbar with 1:1 scroll-linked auto-hide (Top/Bottom/Split folded with a slide-over Arc/Zen Spaces drawer; plus collapsible docked Spaces sidebar and optional Side Rail unfolded) whose Shield count, active tab badge, and focused omnibox carry crisp Fox Amber highlights; Settings opens with a tactile Quick Actions card for Shield, Dark Reader, Zapper, Reader Mode, and Site Mode.
- **FORM:** Standard Material 3 Android Browser (canon standing exit chosen by user with extra accent splashes), `seed:71cad797ee7947a5`.
- **FINISH:** unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
