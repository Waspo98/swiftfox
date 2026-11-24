---
name: Swiftfox
description: Native Material 3 Android browser system pairing 120Hz foldable posture ergonomics with deliberate Fox Amber accent splashes across Day, Dark, and true AMOLED surfaces.
colors:
  fox-amber-brand: "#E86A17"
  fox-amber-day-primary: "#B04200"
  fox-amber-day-container: "#FFE2CC"
  fox-amber-day-on-container: "#4A1B00"
  fox-amber-dark-primary: "#FF9147"
  fox-amber-dark-on-primary: "#2B0F00"
  fox-amber-dark-container: "#3A1F0D"
  fox-amber-dark-on-container: "#FFDEC7"
  indigo-day-primary: "#3D4ED8"
  indigo-day-container: "#DFE3FF"
  indigo-dark-primary: "#93A0FF"
  indigo-dark-container: "#202652"
  forest-day-primary: "#1D6E42"
  forest-day-container: "#CFF3DC"
  forest-dark-primary: "#6CD998"
  forest-dark-container: "#123622"
  mono-day-primary: "#2C2A2F"
  mono-day-container: "#E3E0E6"
  mono-dark-primary: "#DCD8E0"
  mono-dark-container: "#312E36"
  private-day-accent: "#6342B5"
  private-day-container: "#ECE2FF"
  private-dark-accent: "#C1A6FF"
  private-dark-on-accent: "#1F0947"
  private-dark-container: "#281B45"
  private-dark-on-container: "#EBDFFF"
  day-surface: "#FAF8F6"
  day-surface-low: "#F5F1ED"
  day-surface-container: "#EFEAE4"
  day-surface-high: "#E6DFD8"
  day-surface-highest: "#DCD3CA"
  day-on-surface: "#1C1917"
  day-on-surface-variant: "#57504B"
  day-outline-variant: "#D5CCC4"
  dark-surface: "#18161B"
  dark-surface-low: "#1E1C22"
  dark-surface-container: "#232028"
  dark-surface-high: "#2D2933"
  dark-surface-highest: "#383340"
  dark-on-surface: "#ECE7F0"
  dark-on-surface-variant: "#C2BAC9"
  dark-outline-variant: "#3E3947"
  amoled-surface: "#000000"
  amoled-surface-low: "#0A090D"
  amoled-surface-container: "#121016"
  amoled-surface-high: "#1C1922"
  amoled-surface-highest: "#272330"
  amoled-on-surface: "#F3EEF7"
  amoled-on-surface-variant: "#C4BCD0"
  amoled-outline-variant: "#2E2A36"
  error-day: "#BA1A1A"
  error-dark: "#FFB4AB"
typography:
  display-small:
    fontFamily: "Sora, Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "32px"
    fontWeight: 600
    lineHeight: "38px"
    letterSpacing: "-0.6px"
  headline-medium:
    fontFamily: "Sora, Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "26px"
    fontWeight: 600
    lineHeight: "32px"
    letterSpacing: "-0.4px"
  headline-small:
    fontFamily: "Sora, Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "22px"
    fontWeight: 600
    lineHeight: "28px"
    letterSpacing: "-0.25px"
  title-large:
    fontFamily: "Sora, Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "19px"
    fontWeight: 600
    lineHeight: "25px"
    letterSpacing: "-0.15px"
  title-medium:
    fontFamily: "Sora, Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: "22px"
    letterSpacing: "0px"
  title-small:
    fontFamily: "Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: "20px"
    letterSpacing: "0.1px"
  body-large:
    fontFamily: "Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "24px"
    letterSpacing: "0px"
  body-medium:
    fontFamily: "Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
    letterSpacing: "0px"
  body-small:
    fontFamily: "Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: "16px"
    letterSpacing: "0px"
  label-large:
    fontFamily: "Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: "20px"
    letterSpacing: "0.1px"
  label-medium:
    fontFamily: "Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: "16px"
    letterSpacing: "0.25px"
  label-small:
    fontFamily: "Roboto, system-ui, -apple-system, sans-serif"
    fontSize: "11px"
    fontWeight: 500
    lineHeight: "14px"
    letterSpacing: "0.3px"
rounded:
  extra-small: "10px"
  small: "14px"
  row: "16px"
  medium: "18px"
  tile: "20px"
  pill: "22px"
  large: "24px"
  omnibox: "26px"
  extra-large: "28px"
  full: "9999px"
spacing:
  xs: "6px"
  sm: "8px"
  md-tight: "10px"
  md: "12px"
  md-wide: "14px"
  lg: "16px"
  xl: "20px"
  2xl: "24px"
  touch-min: "48px"
components:
  omnibox-pill:
    backgroundColor: "{colors.dark-surface-high}"
    textColor: "{colors.dark-on-surface}"
    typography: "{typography.body-medium}"
    rounded: "{rounded.omnibox}"
    padding: "0px 4px 0px 12px"
    height: "48px"
  omnibox-pill-focused:
    backgroundColor: "{colors.dark-surface-high}"
    textColor: "{colors.dark-on-surface}"
    typography: "{typography.body-large}"
    rounded: "{rounded.extra-large}"
    padding: "0px 6px 0px 14px"
    height: "52px"
  omnibox-pill-private:
    backgroundColor: "{colors.private-dark-container}"
    textColor: "{colors.private-dark-on-container}"
    typography: "{typography.body-medium}"
    rounded: "{rounded.omnibox}"
    padding: "0px 4px 0px 12px"
    height: "48px"
  quick-control-tile:
    backgroundColor: "{colors.dark-surface-high}"
    textColor: "{colors.dark-on-surface}"
    typography: "{typography.title-small}"
    rounded: "{rounded.tile}"
    padding: "14px"
    height: "82px"
  quick-control-tile-active:
    backgroundColor: "{colors.fox-amber-dark-container}"
    textColor: "{colors.fox-amber-dark-on-container}"
    typography: "{typography.title-small}"
    rounded: "{rounded.tile}"
    padding: "14px"
    height: "82px"
  space-pill:
    backgroundColor: "{colors.dark-surface-high}"
    textColor: "{colors.dark-on-surface}"
    typography: "{typography.label-large}"
    rounded: "{rounded.pill}"
    padding: "12px 16px"
    height: "48px"
  space-pill-selected:
    backgroundColor: "{colors.fox-amber-dark-container}"
    textColor: "{colors.fox-amber-dark-on-container}"
    typography: "{typography.label-large}"
    rounded: "{rounded.pill}"
    padding: "12px 16px"
    height: "48px"
  tab-count-badge:
    backgroundColor: "{colors.fox-amber-dark-container}"
    textColor: "{colors.fox-amber-dark-on-container}"
    typography: "{typography.label-medium}"
    rounded: "{rounded.extra-small}"
    padding: "2px 6px"
    size: "28px"
  action-pill:
    backgroundColor: "{colors.dark-surface-high}"
    textColor: "{colors.dark-on-surface}"
    typography: "{typography.label-large}"
    rounded: "{rounded.large}"
    padding: "12px 16px"
    height: "48px"
  controls-pill:
    backgroundColor: "{colors.fox-amber-dark-container}"
    textColor: "{colors.fox-amber-dark-on-container}"
    typography: "{typography.label-large}"
    rounded: "{rounded.large}"
    padding: "10px 16px"
    height: "48px"
  extended-fab:
    backgroundColor: "{colors.fox-amber-dark-primary}"
    textColor: "{colors.fox-amber-dark-on-primary}"
    typography: "{typography.label-large}"
    rounded: "{rounded.large}"
    padding: "16px 20px"
    height: "56px"
---

# Design System: Swiftfox

## Overview

**Creative North Star: "The Warm Amber Cockpit"**

Swiftfox is a native Android Material Design 3 browser engineered for 120Hz foldable hardware. Its chrome behaves like a calm, responsive cockpit wrapped around live web content: warm tonal surfaces stay visually quiet so scrolling pages hold center stage, while generous splashes of Fox Amber light up the controls that matter right now—active Space pills, the Tab Count badge, security indicators, focused search rings, and tactile Quick Control toggles.

Rather than forcing a single layout across cover and inner displays, Swiftfox treats folded one-handed reach and unfolded tablet browsing as independent ergonomic states. Every surface avoids sterile flat-grey utility chrome and harsh right-angle corners, favoring soft pill capsules, generous `48dp` thumb targets, and clear tonal elevation across Day, Dark, and true AMOLED Black themes.

**Key Characteristics:**
- Webpage-first tonal chrome in Warm Paper Day, Charcoal-Plum Dark, and true `#000000` AMOLED Black.
- Deliberate Fox Amber accent splashes on active state, switchable across Midnight Indigo, Forest, Monochrome, and Android Material You dynamic color.
- Unmistakable violet chrome shift for `ProfileStore`-isolated Private browsing sessions.
- Rounded capsule geometry (`10dp`–`28dp`) culminating in the 2×2 Quick Actions card inside Settings and the Arc/Zen Spaces sidebar.
- Independent Folded and Unfolded toolbar profiles, collapsible Spaces sidebar, `sp` system font scaling, and `ANIMATOR_DURATION_SCALE` reduced-motion compliance.

## Colors

Swiftfox pairs three warm-shifted Material 3 neutral surface ladders with a single active primary accent family (aliased to Material `secondary`) and a dedicated violet tertiary family reserved for Private Mode isolation. All foreground/background pairs target WCAG AA contrast across Day, Dark, and AMOLED surfaces.

### Primary

- **Fox Amber Brand Splash** (`#E86A17`, paired with `#1F0A00` ink): Brand mark fill on `SwiftfoxEmblem` and the Fox Amber palette preview swatch in Settings.
- **Fox Amber Day Primary & Container** (`#B04200` on `#FFFFFF`; container `#FFE2CC` with `#4A1B00` ink): Accessible burnt-amber accent for light-mode icons, active focus rings, progress indicators, and primary containers across selected Space pills, active Quick Control tiles, and the Tab Count badge.
- **Fox Amber Dark Primary & Container** (`#FF9147` on `#2B0F00`; container `#3A1F0D` with `#FFDEC7` ink): Luminous apricot-amber primary and deep roasted-amber containers built to stay vivid and legible on both Charcoal-Plum Dark and true AMOLED surfaces.
- **Midnight Indigo Preset** (Day `#3D4ED8` / `#DFE3FF` with `#0C1566` ink; Dark `#93A0FF` on `#0D1763` / `#202652` with `#E1E5FF` ink; swatch `#5B6CFA`): Switchable primary accent preset for cool editorial chrome.
- **Forest Preset** (Day `#1D6E42` / `#CFF3DC` with `#043018` ink; Dark `#6CD998` on `#002B14` / `#123622` with `#CBF5DA` ink; swatch `#2E8B57`): Switchable botanical green primary preset.
- **Monochrome Preset** (Day `#2C2A2F` / `#E3E0E6` with `#19171C` ink; Dark `#DCD8E0` on `#1B191E` / `#312E36` with `#EFECF2` ink; swatch `#6B6A70`): High-restraint neutral primary preset.
- **Material You Dynamic Color** (Android 12+ opt-in): Replaces the active accent scheme with the system wallpaper palette while explicitly overriding background and surface containers to the AMOLED ladder when AMOLED Black is active.

### Tertiary

- **Private Violet Day** (`#6342B5` on `#FFFFFF`; container `#ECE2FF` with `#261057` ink; Material `tertiary` `#6B4FA8`): Signals isolated Private tabs in Day mode across the omnibox pill, Tab Count badge, Private Space filter chip, Start Page shield medallion, and Private tab FAB.
- **Private Violet Dark & AMOLED** (`#C1A6FF` on `#1F0947`; container `#281B45` with `#EBDFFF` ink; Material `tertiary` `#C8B3FF` / `#2E1E52`): High-legibility lavender accent over deep plum-violet containers for unmistakable Private Mode separation in night themes.
- **Error Semantic** (Day `#BA1A1A` on `#FFFFFF`, container `#FFDAD6` / `#410002`; Night `#FFB4AB` on `#690005`, container `#93000A` / `#FFDAD6`): Standard Material 3 error roles for destructive alerts and warnings.

### Neutral

- **Day Warm Paper Ladder** (`#FAF8F6` base surface → `#F5F1ED` low → `#EFEAE4` container → `#E6DFD8` high → `#DCD3CA` highest; `#FFFFFF` lowest): Sunlit paper-tinted surfaces that soften contrast glare around web pages, paired with charcoal ink (`#1C1917`), muted stone secondary ink (`#57504B`), and warm sand dividers (`#D5CCC4`, outline `#837870`).
- **Dark Charcoal-Plum Ladder** (`#18161B` base surface → `#1E1C22` low → `#232028` container → `#2D2933` high → `#383340` highest; `#121015` lowest): Subtle cool-plum charcoal steps that keep Fox Amber warm without muddying, paired with soft alabaster text (`#ECE7F0`), lavender-grey secondary copy (`#C2BAC9`), and hairline strokes (`#3E3947`, outline `#8C8494`).
- **AMOLED True-Black Ladder** (`#000000` base surface & lowest → `#0A090D` low → `#121016` container → `#1C1922` high → `#272330` highest): Zero-emission OLED canvas that lifts toolbars, sheets, and interactive pills via crisp low-luminance stops, crisp ink (`#F3EEF7`), muted secondary ink (`#C4BCD0`), and dark strokes (`#2E2A36`, outline `#8A8294`).

### Named Rules

**The Warm Splash Rule.** Reserve saturated accent fills (`primaryContainer` paired with `primary` icons and tinted borders) for live state—active Quick Actions tiles, selected Space pills, the Tab Count badge, the Split-bar New Tab button, focused omnibox borders, and primary CTAs—so resting toolbars remain calm and webpage-first.

**The Dual-Contrast Amber Rule.** Never place brand Fox Amber (`#E86A17`) behind body copy or interactive button labels; use the brand splash strictly for `SwiftfoxEmblem` and palette swatches, while text, icons, and interactive surfaces resolve to WCAG AA primaries (`#B04200` in Day, `#FF9147` in Dark and AMOLED).

**The Violet Boundary Rule.** Private browsing must always switch chrome identity from the active accent preset to the Private Violet tokens (`#6342B5` / `#ECE2FF` Day; `#C1A6FF` / `#281B45` Dark and AMOLED) across the omnibox pill, Tab Count badge, Start Page emblem, Private chip, and New Private Tab FAB.

**The True-Black Preservation Rule.** Whenever AMOLED Black is selected, base canvas and lowest surfaces must remain `#000000`—even when Material You dynamic color is active—stepping upward exclusively through the low-luminance AMOLED container ladder (`#0A090D` → `#121016` → `#1C1922` → `#272330`).

## Typography

**Display & Header Font:** Bundled **Sora** (`sora_bold.ttf` for the `Swiftfox` wordmark; `sora_semibold.ttf` for all app headers from `displaySmall` through `titleMedium`)
**Body Font:** Android system Sans-Serif / Roboto (`FontFamily.SansSerif`, fallback `system-ui, sans-serif`)
**Label Font:** Android system Sans-Serif / Roboto (`FontFamily.SansSerif`, fallback `system-ui, sans-serif`)

**Character:** Bundled Sora (`700` Bold for the `Swiftfox` wordmark lockup at `-0.025em` tracking; `600` SemiBold across screen, sheet, and section headers) pairs wide geometric velocity with the faceted fox emblem, while native Roboto handles body copy, URLs, and dense controls at 120Hz. All frontmatter `px` values map 1:1 to Android `sp` at `1.0x` system font scale.

### Hierarchy

- **Wordmark (`SwiftfoxWordmark`)** (Sora `700` Bold, `-0.025em` tracking): Brand lockup alongside the full-shaded Orange or Purple `SwiftfoxEmblem` / `SwiftfoxAppIcon`.
- **Display Small** (Sora `600` SemiBold, `32sp` / `38sp` line height, `-0.6sp` tracking): Large display headers.
- **Headline Medium** (Sora `600` SemiBold, `26sp` / `32sp` line height, `-0.4sp` tracking): Private Space Start Page header.
- **Headline Small** (Sora `600` SemiBold, `22sp` / `28sp` line height, `-0.25sp` tracking): Top-level overlay screen titles such as `SpacesTabSwitcher` active Space / `Private Tabs` heading.
- **Title Large** (Sora `600` SemiBold, `19sp` / `25sp` line height, `-0.15sp` tracking): TopAppBar titles, Start Page Essential monogram glyph, and empty-state titles.
- **Title Medium** (Sora `600` SemiBold, `16sp` / `22sp` line height, `0sp` tracking): Settings section headers (`primary` colored), Start Page section titles (`Essentials`, `Open in Space`), and compact headers.
- **Title Small** (`600` SemiBold, `14sp` / `20sp` line height, `0.1sp` tracking): Quick Control tile titles, Tab Preview card titles, Start Page jump card titles, Settings switch/radio titles, and library row titles.
- **Body Large** (`400` Regular, `16sp` / `24sp` line height, `0sp` tracking): Focused omnibox input text, Start Page search prompt, omnibox suggestion primary lines, and Context Menu action rows.
- **Body Medium** (`400` Regular, `14sp` / `20sp` line height, `0sp` tracking): Resting omnibox display host (bumped to `500` Medium when displaying an active host), Find-in-Page input, dialog messages, and empty-state descriptions.
- **Body Small** (`400` Regular, `12sp` / `16sp` line height, `0sp` tracking): Quick Control tile subtitles, page security/posture meta line, Settings descriptions, and suggestion URLs.
- **Label Large** (`600` SemiBold, `14sp` / `20sp` line height, `0.1sp` tracking): Space pills, Quick Action pills, Split-bar `Controls` pill, Extended FAB, sidebar tab titles, and Settings group labels.
- **Label Medium** (`600` SemiBold, `12sp` / `16sp` line height, `0.25sp` tracking): Tab Count badge numerals (bumped to `700` Bold), `!bang` triggers, `Switch` open-tab chip, Find-in-Page match counter, and compact Essential labels.
- **Label Small** (`500` Medium, `11sp` / `14sp` line height, `0.3sp` tracking): Essential tile captions, Tab Preview card hostlines, and sidebar tab domains.

### Named Rules

**The Scalable Type Rule.** Define every text style in `sp` with explicit line-height headroom and explicit `maxLines` + `TextOverflow.Ellipsis` guards on titles, hostnames, and pill labels so 1.3x+ Android system font scaling never clips interactive chrome.

**The Semibold Affordance Rule.** Use SemiBold (`600`) or Bold (`700`) with slight positive tracking (`0.1sp`–`0.3sp`) for buttons, pills, badges, and section headings, reserving Regular (`400`) for URLs, subtitles, and descriptive copy so interactive targets read cleanly at a glance.

## Layout

Swiftfox runs fully edge-to-edge (`enableEdgeToEdge()`, transparent system navigation bar via `isNavigationBarContrastEnforced = false`) and adapts across three live width/posture bands reported by Jetpack `WindowManager` (`PostureObserver`):

- **Folded (`<600dp` width, cover screen):** Activates the Folded profile (default: `SPLIT` toolbar with top omnibox capsule and bottom thumb action bar; `MOBILE` default site mode; slide-over `ArcSpacesSidebar` drawer). Start Page content centers within `540dp` max width, rendering a 4-column Essentials grid and 2-column Jump cards inside `20dp` horizontal margins.
- **Unfolded Compact (`600dp–779dp` width or flat/half-opened `FoldingFeature`):** Switches automatically to the independent Unfolded profile (default: `TOP` toolbar with inline Back/Forward navigation, `DESKTOP` default site mode, and collapsible Arc/Zen-style `ArcSpacesSidebar` at `72dp` collapsed or `232dp` expanded). Unlocks `SIDE_RAIL` placement (`68dp` ergonomic right-edge vertical rail) while letting `TOP` and `SIDE_RAIL` render live web content right beneath the transparent gesture bar.
- **Unfolded Expanded (`≥780dp` width):** Expands `ArcSpacesSidebar` to `264dp`, widens Start Page measure to `720dp` (scaling the Essentials grid to 4–5 columns depending on count), and lets `SpacesTabSwitcher` flow naturally across `GridCells.Adaptive(minSize = 164.dp)` with `12dp` gutters.

**Spacing Rhythm:** Components step along a compact Android dp scale—`6dp` tight icon-label pairs, `8dp` chip/toolbar gaps, `10dp` Quick Actions tile gutters, `12dp` toolbar outer margins and tab grid spacing, `14dp` tile inner padding, `16dp` sheet stacks and Settings margins, `20dp` Start Page horizontal margins, and `24dp` bottom sheet clearance above navigation bar insets.

**Motion & Reduced Motion:** `OmniboxOverlay` transitions with a Material fade and `FindInPageBar` slides vertically from the top toolbar (`slideInVertically { -it } + fadeIn()`). Before animating, `BrowserScreen` checks Android's `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` ("Remove animations") and replaces transitions with `EnterTransition.None` / `ExitTransition.None`. URL-bar gestures switch adjacent tabs past a `96px` horizontal drag threshold and open the Tab Switcher past an `88px` drag toward the viewport.

### Named Rules

**The Independent Posture Rule.** Folded and Unfolded postures own separate toolbar placement, default site mode, and Spaces sidebar settings; unfolding never stretches a phone bar, and unfolded-only placements (`SIDE_RAIL`) fall back cleanly to `SPLIT` whenever the device folds.

**The 48dp Thumb Floor Rule.** Every primary interactive control—toolbar buttons, omnibox pills, Space chips, Quick Action pills, Settings chips, and tab close icons—must provide at least a `48×48dp` (`48px`) hit area separated by `8dp`–`12dp` gaps so one-handed reach remains reliable at 120Hz.

**The Deduplicated Chrome Rule.** When the unfolded Spaces sidebar is visible, Start Page suppresses its inline Space switcher row (and suppresses open-tab Jump cards whenever the sidebar is expanded) so workspace controls appear once per viewport.

## Elevation & Depth

Swiftfox is **tonally layered rather than shadow-heavy**. Instead of stacking blurred drop shadows on dark or AMOLED glass, hierarchy is communicated by stepping upward across Material 3's five container tiers (`surface` → `surfaceContainerLow` → `surfaceContainer` → `surfaceContainerHigh` → `surfaceContainerHighest`), augmented by minimal tonal elevation (`1dp` on docked `ArcSpacesSidebar`, `2dp` on toolbars and `ErgonomicSideRail`, `3dp` on `FindInPageBar` and overlay `ArcSpacesSidebar`) and selective hairline borders.

Native modal elevation is reserved for Material 3 `ModalBottomSheet` (`SiteRulesSheet` and Long-Press Context Menu, `surfaceContainerLow` over a dimmed scrim), `AlertDialog`, and `ExtendedFloatingActionButton` in `SpacesTabSwitcher`.

### Named Rules

**The Tonal Ladder Rule.** Express surface hierarchy by stepping up one container tier—`surfaceContainerLow` for persistent bars, sidebars, and modal sheets; `surfaceContainer` for grouped cards and tab tiles; `surfaceContainerHigh` for interactive pills and resting toggles—rather than adding decorative box shadows.

**The Hairline State Ring Rule.** Use `1dp` `outlineVariant` borders at `35%–55%` alpha only to define resting shapes on dark and AMOLED surfaces, escalating to `1.5dp–2dp` `primary` (or `privateAccent`) borders strictly to signal active selection, enabled Quick Actions toggles, or omnibox focus.

**The Flat Settings Rule.** Group related preferences inside a single `surfaceContainer` section card (`22dp` radius, `18dp` padding) separated internally by `outlineVariant` hairline dividers (`35%` alpha); never nest secondary cards inside settings sections.

## Shapes

Swiftfox speaks in generously rounded, tactile capsules rooted in `SwiftfoxShapes` (`10dp` extraSmall, `14dp` small, `18dp` medium, `24dp` large, `28dp` extraLarge) alongside calibrated intermediate radii (`16dp`, `20dp`, `22dp`, `26dp`) and `CircleShape`.

Corners stay visually concentric whenever containers nest: `TabPreviewCard` (`20dp` shell with `8dp` inner padding) clips its live webpage thumbnail to `14dp`; `QuickActionsSection` (`22dp` card) houses `20dp` toggle tiles and `24dp` action pills; and the `28dp` Start Page search capsule nests a `32dp` circular search icon badge.

### Named Rules

**The Capsule Hierarchy Rule.** Match corner radius directly to visual role: compact numeral badges at `10dp`, nested thumbnails and inline chips at `14dp–16dp`, cards and toggle tiles at `18dp–22dp`, action pills and FABs at `24dp`, omnibox capsules at `26dp–28dp`, and modal bottom sheets at `28dp` top corners—never sharp `0dp` corners on browser chrome.

## Components

Swiftfox's UI components feel **tactile, pill-soft, and state-honest**: resting chrome blends quietly into the surface ladder while active or toggled controls illuminate immediately with tonal accent containers and crisp borders.

### Buttons

- **Shape:** Full-width primary CTA `Button` uses `22dp` radius (`52dp` height, e.g., Settings default browser CTA); secondary `OutlinedButton` uses `20dp` radius (`48dp` height, e.g., Clear browsing data); `ExtendedFloatingActionButton` uses `24dp` radius (`56dp` height, `New tab` / `Private tab`).
- **Primary:** Solid `primary` container (`#B04200` Day / `#FF9147` Dark & AMOLED) with high-contrast `onPrimary` text and icon (`#FFFFFF` Day / `#2B0F00` Dark & AMOLED); switches to `privateAccent` / `onPrivateAccent` (`#C1A6FF` / `#1F0947` Dark) in Private view.
- **Tonal & Icon Buttons:** `FilledTonalIconButton` (`CircleShape`, `48dp`) sits in the Settings Quick Actions header (`surfaceContainerHigh` resting, flipping to `primaryContainer` + `primary` icon when the active page is bookmarked); bare toolbar `IconButton` targets hold `48×48dp` with disabled states dimmed natively by Material 3.

### Chips & Pills

- **Space Pill Chip (`SpacePillChip`):** `22dp` radius, `48dp` minimum height, `16dp` horizontal / `12dp` vertical padding. Resting uses `surfaceContainerHigh` with `onSurface` label and `onSurfaceVariant` icon/count; selected switches to `primaryContainer` with a `1.5dp` solid `primary` border, `onPrimaryContainer` label, and `primary`-tinted icon, optional container-isolation lock icon (`14dp`), and tab count.
- **Quick Action Pill (`QuickActionPill`):** `24dp` radius, `48dp` minimum height, `16dp` horizontal / `12dp` vertical padding on `surfaceContainerHigh`, pairing an `18dp` `primary`-tinted rounded icon with a `labelLarge` caption (`Zap element`, `Reader mode`, `Site rules`, `Find in page`, `Pin essential`, `Send to Zen`).
- **Split-Bar New Tab Button:** Centered in `SplitBottomActionBar`, `CircleShape`, `48dp` target filled in `primaryContainer` (or `privateContainer` in Private Mode) with a `1dp` `primary` (`45%` alpha) border and `24dp` `+` icon (tap for New Tab, long-press for Private Tab).
- **Filter & Selection Chips:** Settings `SelectionChip` uses Material 3 `FilterChip` (`18dp` radius, `48dp` min height, `primaryContainer` selected fill); `SpacesTabSwitcher` Private toggle uses `20dp` radius shifting to `privateContainer` / `onPrivateContainer` with `privateAccent` shield icon.

### Cards & Containers

- **Tab Preview Card (`TabPreviewCard`):** `20dp` radius on `surfaceContainer`, `1dp` `outlineVariant` (`40%` alpha) border at rest escalating to `2dp` solid `primary` (or `privateAccent` for private tabs) on the active tab. Header pairs title + domain with a `48dp` close button above a `1.15f` aspect-ratio `14dp`-clipped live thumbnail (or Start Page `SwiftfoxEmblem` / monogram fallback with Desktop and audio badges).
- **Settings Section Card (`SettingsSection`):** `22dp` radius on `surfaceContainer`, `18dp` internal padding and vertical spacing, led by a `titleMedium` heading in `primary`.
- **Start Page Tiles & Jump Cards:** `EssentialTile` pairs a `56×56dp` `18dp`-rounded `surfaceContainerHigh` square (`1dp` `primary` border at `22%` alpha) with a centered `titleLarge` `primary` monogram and `labelSmall` caption; `AddEssentialTile` uses `primaryContainer` at `65%` alpha. Jump cards (`18dp` radius, `84dp` min height, `14dp` padding) sit on `surfaceContainer` with a `1dp` `outlineVariant` (`35%` alpha) stroke.

### Inputs & Omnibox

- **Resting Omnibox Capsule (`FullOmniboxToolbar`):** `26dp` radius, `48dp` minimum height, `surfaceContainerHigh` fill with `1dp` `outlineVariant` (`55%` alpha) stroke (shifting to `privateContainer` with `1dp` `privateAccent` `55%` stroke in Private Mode). Leads with an `18dp` `primary` (or `privateAccent`) security/search icon, displays `displayHost` or `Enter URL or !bang`, shows an inline `16dp` `primary` Desktop monitor icon when Desktop UA is active, and ends with a `48dp` Reload/Stop button.
- **Focused Omnibox Overlay (`OmniboxOverlay`):** `28dp` radius, `52dp` minimum height, `surfaceContainerHigh` fill framed by a bold `2dp` solid `primary` focus ring. Live `!bang` triggers convert into a `14dp`-rounded `primaryContainer` badge ahead of `BasicTextField` (`bodyLarge`, `primary` cursor), followed by a horizontal strip of `16dp`-rounded quick `!bang` shortcut chips and `56dp`-tall ranked suggestion rows (`OPEN_TAB` suggestions carry a `14dp` `primaryContainer` `Switch` badge).
- **Find-in-Page Bar & Dialog Inputs:** `FindInPageBar` sits on `surfaceContainerHigh` (`3dp` tonal elevation) with `primary` match ratio (`1 / 4`) and `48dp` previous/next/close buttons; dialog text fields use Material 3 `OutlinedTextField` at `16dp` radius.

### Navigation

- **Posture-Adaptive Toolbars:** `FullOmniboxToolbar` and `SplitBottomActionBar` anchor on `surfaceContainerLow` (`2dp` tonal elevation) with `2.5dp` `primary` linear page-load progress bars and `outlineVariant` (`35%` alpha) edge hairlines. `ErgonomicSideRail` (`68dp` wide, unfolded `SIDE_RAIL` mode) stacks a `48dp` circular `primaryContainer` Search button, New Tab, Back/Forward/Reload, a `16dp`-rounded `primaryContainer` Settings & Quick Actions button, and the Tab Count badge.
- **Tab Count Badge (`TabCountBadgeButton`):** `48×48dp` interactive button centering a `28×28dp` minimum `10dp`-rounded pill (`primaryContainer` + `1.5dp primary` border in normal mode; `privateContainer` + `1.5dp privateAccent` border in Private Mode) displaying bold `labelMedium` counts capped at `99+`.
- **Arc/Zen Spaces Sidebar (`ArcSpacesSidebar`):** `surfaceContainerLow` (`1dp` docked / `3dp` folded slide-over drawer) collapsing between a `72dp` icon rail (`48dp` rounded space/tab buttons) and a `232dp`/`264dp` (or `82%`, max `304dp` folded drawer) vertical workspace showing `SwiftfoxEmblem`, a 4-column Essentials grid, Zen pinned items, `52dp`-tall vertical tab rows (`16dp` radius, active tab highlighted in `primaryContainer` + `1dp primary` 55% border), a `20dp`-rounded `primary` New Tab button, and a bottom Space/Private switcher strip.

### Quick Actions Section (Settings Top Card)

Active-page controls live at the top of `SettingsScreen` inside `QuickActionsSection` (`22dp` `surfaceContainer` card, `18dp` padding):
- **Context Header & Action Row:** Displays page title (`titleSmall`) and `host · HTTPS/HTTP · Posture` metadata (`bodySmall`) above a `FlowRow` of `48dp` circular `FilledTonalIconButton` actions (Back, Forward, Bookmark toggle, Share clean link, Print/PDF).
- **2×2 Tactile Toggle Grid (`QuickControlTile`):** Equal-height rows (`IntrinsicSize.Max`, `10dp` gap) of four `20dp`-rounded tiles (`82dp` min height, `14dp` padding) for **Desktop/Mobile site**, **Web dark** (Dark Reader AMOLED / Dark Reader on / Lite mode / Native dark kept / Site native), **Shield** (live blocked count, per-site pause, or global rule count), and **PiP / Play**. Inactive tiles sit on `surfaceContainerHigh` with a `1dp` `outlineVariant` (`35%` alpha) border and a `40dp` circular `surfaceContainerHighest` icon badge; active tiles flip to `primaryContainer`, a `1.5dp` `primary` (`55%` alpha) border, `onPrimaryContainer` text, and a `40dp` circular `primary` (`16%` alpha) icon badge with `primary` icon tint.
- **Page Action Pills:** Below a `45%` alpha `outlineVariant` divider, a `FlowRow` (`10dp` gaps) wraps `QuickActionPill` controls for webpage actions (`Zap element`, `Reader mode`, `Site rules`, `Find in page`, `Pin essential`, conditional `Send to Zen`), followed immediately in `SettingsScreen` by the Library (`Bookmarks`, `History`, `Downloads`) and Sync sections.

### Power Tools & Sync Overlays (M2 / M3)

All secondary M2 and M3 surfaces extend the same tonal ladder, capsule radii, `48dp` touch floor, and reduced-motion rules:
- **Element Zapper HUD (`ZapperHudOverlay`):** Floating bottom capsule (`28dp` radius, `max 560dp` width, `surfaceContainerLow`, `3dp` tonal elevation, `1.5dp` `primary` `65%` alpha border) anchored over the live webpage without dimming it. Switches between a compact idle bar (`Tap any element to zap`, optional `Undo`, `Done`) and a selected-element panel featuring monospace selector readout, `-`/`+` parent/child scope controls + Material 3 `Slider`, `Zap once`, and `Block permanently`. Respects `ANIMATOR_DURATION_SCALE == 0f` before applying `animateContentSize()`.
- **Site Rules Sheet (`SiteRulesSheet`):** `28dp` top-corner `ModalBottomSheet` on `surfaceContainerLow` with `48dp` `Reset all` text button, a `22dp` `surfaceContainer` rule card (per-site adblock pause, Web dark override, Mobile/Desktop lock, JavaScript, Cookies, HTTPS-Only bypass, User-Agent `FilterChip` presets + custom `OutlinedTextField`), and inline host zapped-rule removal + quick launch back into the Zapper.
- **Reader Mode Overlay (`ReaderModeOverlay`):** Full-screen native Compose reader with its own dedicated `ReaderPalette` (`Day`, `Sepia`, `Dark`, `AMOLED #000000`, honoring the active accent preset), `SelectionContainer` text selection, adblock-filtered inline image loading, and a bottom appearance `ModalBottomSheet` for font family, text size, line height, and surface switches (`rememberReduceMotion` respected for palette transitions).
- **Management Overlays (`FilterListsOverlay`, `UserscriptsOverlay`, `ZappedRulesOverlay`):** Full-screen Material 3 `Scaffold` + `TopAppBar` screens on `background` with `48dp` navigation/action targets, `surfaceContainer` list cards with `Switch` toggles and delete confirmations, `ExtendedFloatingActionButton` / action bar dialogs for adding custom filter list URLs or `.user.js` scripts (URL, local file picker, or built-in monospace editor), and stable `LazyColumn` keys + `contentType`.
- **Sync & Pairing Overlays (`SyncOverlay`, `QrPairingOverlay`, `SyncedTabsOverlay`, `FxAAuthWebViewOverlay`):** `SyncOverlay` uses `22dp` `surfaceContainer` cards led by a `52dp` `18dp`-rounded `primaryContainer` identity badge, primary `Button` (`Sign in`) + `OutlinedButton` (`Pair with QR code`), per-engine `Switch` toggles (Bookmarks, History, Open Tabs, Zen Spaces), sync-interval `FilterChip` selectors, and Chrome HTML import/export buttons. `QrPairingOverlay` hosts a live CameraX + ZXing scanner with a manual paste-pairing-link `AlertDialog` fallback; `SyncedTabsOverlay` groups remote tabs by device; `FxAAuthWebViewOverlay` runs Mozilla OAuth inside an isolated `ProfileStore` WebView with a `TopAppBar` linear progress bar.

## Do's and Don'ts

### Do:
- **Do** use the tonal surface container ladder (`surface` → `surfaceContainerLow` → `surfaceContainer` → `surfaceContainerHigh`) to separate bars, cards, and pills rather than adding drop shadows.
- **Do** enforce `48×48dp` minimum touch targets and `sp` typography with `TextOverflow.Ellipsis` across all new toolbar buttons, pills, chips, and sheet controls.
- **Do** apply `primaryContainer` + `primary` accents to signal active toggles, selected Spaces, focused inputs, and live status badges while keeping resting chrome neutral.
- **Do** switch every chrome surface cleanly to Private Violet (`privateContainer` / `privateAccent`) whenever displaying a Private tab or Private Space.
- **Do** preserve `#000000` canvas surfaces in AMOLED Black mode, including when Material You dynamic color is turned on.
- **Do** check `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` before introducing new `AnimatedVisibility` or transition effects so Android's "Remove animations" setting is always respected.

### Don't:
- **Don't** place raw Fox Amber Brand (`#E86A17`) behind body copy or interactive labels; always resolve interactive text and fills through `MaterialTheme.colorScheme.primary` / `primaryContainer`.
- **Don't** use sharp `0dp` rectangular corners on browser buttons, chips, cards, omnibox inputs, or modal sheets.
- **Don't** nest secondary cards inside `SettingsSection` containers; flatten radio lists and site-override rows directly inside the section with hairline `outlineVariant` dividers.
- **Don't** duplicate unfolded sidebar controls (Spaces switcher row or open-tab jump cards) onto the Start Page when the sidebar is already presenting them.
- **Don't** couple Folded and Unfolded toolbar placements or default site modes into a single shared toggle, or render `SIDE_RAIL` on `<600dp` folded screens.
- **Don't** force `WebView` or browser chrome to drop below 120Hz responsiveness by allocating paths or paint objects inside hot Compose draw/scroll loops; use `drawWithCache` and stable lazy list keys with `contentType`.
