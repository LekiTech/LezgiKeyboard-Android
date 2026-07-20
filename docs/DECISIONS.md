# Architectural Decisions

Chronological log of decisions that affect future development. Every
entry records the reasoning, so later changes stay consistent with it.
When a decision is superseded, mark it and link the replacement — never
delete history.

Format: `D-NNN (date) — title`, then decision and rationale.

---

## D-001 (2026-07-19) — The iOS implementation is the behavioral source of truth

When `ANDROID_PORT_CONTEXT.md` and the working iOS keyboard disagree on
user-visible behavior, follow the implementation and update the
specification afterwards. First application: the suggestion-bar press
capsule and the «Ваъ» confirm pill are filled with the **letter-key
color** (as implemented), not the pressed-key color (as the spec's color
table said).

*Why*: users experience the implementation, not the document; parity
means matching what users know.

## D-002 (2026-07-19) — Both platforms ship identical dictionary data

The Android repo's `lezgi_words.sqlite` was a stale pre-migration copy
(2,374 words with a Latin `I` palochka). It was replaced with the iOS
bundle's migrated file (Cyrillic `ӏ` U+04CF everywhere, 20,356 words,
md5 `a33c970b680efae7f39f500f0b8408e4`). Any future dictionary update
must land on both platforms as the same file.

*Why*: exact-membership lookups (quoted-literal rule, saved-words
filter) and Stage 8 cloud plans require byte-identical data.

## D-003 (2026-07-19) — Android scope is keyboard-only

The Android project ports the keyboard extension only. The launcher
activity provides the minimum onboarding to enable and switch to the
keyboard. Sticker export and other iOS container-app features are out of
scope unless explicitly requested.

## D-004 (2026-07-19) — Documentation lives in docs/

`docs/IOS_PARITY.md` (undocumented behaviors to preserve — evolving),
`docs/ANDROID_ARCHITECTURE_PLAN.md` (structure), `docs/DECISIONS.md`
(this log). Any architectural decision made during development updates
the appropriate document in the same change. Architecture never exists
only in code. Refined by D-019: a small set of normative contracts and
entry points deliberately stays in the repository root.

## D-005 (2026-07-19) — Components mirror iOS one-to-one

Class names and responsibilities match the iOS files (`KeyboardModel`,
`LezgiLayout`, `WordSuggestions`, `LearnedWords`, `KeyboardSettings`,
`KeyboardView`, `SettingsPanelView`), with Android-only glue kept
separate (`ImeLifecycleOwner`, `EditorState`).

*Why*: a behavior found in one codebase must be locatable in the other
by name; the two implementations will be maintained in lockstep.

## D-006 (2026-07-19) — Raw SQLite, no Room/ORM

Both databases are accessed through `android.database.sqlite` with SQL
ported verbatim from iOS.

*Why*: ranking formulas, `LIKE`/`ESCAPE` semantics, `ORDER BY` behavior,
and integer-division decay are behavioral contracts; an ORM abstraction
adds a translation layer where byte-compatibility is required.

## D-007 (2026-07-19) — Synchronous main-thread engine

All engine and storage work runs synchronously on the main thread, like
iOS. No coroutine dispatchers for DB access.

*Why*: identical event ordering is a correctness requirement (learn
hooks, metrics flags, resume-word backspace); the per-keystroke queries
are microseconds on a 20k-row indexed table. Revisit only with profiler
evidence, as its own decision.

## D-008 (2026-07-19) — Structural privacy: no INTERNET permission, no backup

The app declares no INTERNET permission and sets
`android:allowBackup="false"`; both databases live in
`noBackupFilesDir`.

*Why*: iOS enforces privacy structurally (`RequestsOpenAccess = false`
⇒ OS-denied network). The Android equivalent is to make network and
cloud copies impossible by construction, not by promise. Learned data
dies with the app, matching iOS.

## D-009 (2026-07-19) — Fixed 250 dp; everything renders inside it

The input view is exactly 250 dp and top-aligned. Bubbles, callouts, the
layout menu, and the settings panel are clamped to render inside the
view — verified against the iOS geometry, which clamps the same way. No
popup windows, no `onComputeInsets` manipulation.

*Why*: the height contract is deterministic and host-independent;
single-window rendering is the most robust IME approach and matches iOS
exactly. Refined by D-025: a transparent, touch-transparent top strip
carries transient overlays via `onComputeInsets`; the *visible*
keyboard stays fixed-height.

## D-010 (2026-07-19) — Compose foundation only, no Material

All controls (keys, toggles, radio rows, pills, sheets) are custom-drawn
to the product design.

*Why*: the design is the product's own (iOS-parity layout and the
panel's own palette); Material widgets would fight it and add
theming-behavior coupling.

## D-011 (2026-07-19) — No DI framework, single module

The service constructs and wires everything. One Gradle module.

*Why*: one entry point (the IME service), a handful of singletons,
lockstep maintenance with an iOS codebase that has no DI either.

## D-012 (2026-07-19) — Preference keys identical to iOS

`SharedPreferences` uses the exact iOS UserDefaults keys
(`set_*`, `layoutVariant`, `recentEmojis`).

*Why*: one less mapping to document; future cloud sync (Stage 8) can
treat settings uniformly across platforms.

## D-013 (2026-07-19) — Timing and geometry constants are centralized

Every timing/geometry constant carries the same value as iOS and lives
in one place per component (layout metrics in `LezgiLayout`, animation
constants next to their composable), never inlined at call sites twice.

*Why*: the iOS code had to keep mirrored constants in sync manually
(row spacing in two places); the port should make divergence hard.

## D-014 (2026-07-19) — minSdk 26, targetSdk latest stable

*Why*: Compose requires 21+; variable-font APIs and reliable
`Paint.hasGlyph` push higher; 26 covers virtually all active devices in
the target audience while keeping the font-matching options open.

## D-015 (2026-07-19) — Password fields: no learning and no suggestion bar content

In password-type fields (`TYPE_TEXT_VARIATION_PASSWORD` and variants)
and fields with `IME_FLAG_NO_PERSONALIZED_LEARNING`, learning is fully
disabled and the bar displays no content of any kind — no predictions,
no learned words, no next-word suggestions, no idle words. The bar
itself stays visually present so the fixed-height contract holds.

*Why*: the spec mandates "never learn there"; hiding the bar's content
prevents leaking previously learned vocabulary into a password screen,
while keeping the bar area preserves the deterministic keyboard height.
iOS has no precedent (the OS swaps keyboards for secure fields), so this
is the closest structural equivalent. Approved 2026-07-19.

## D-016 (2026-07-19) — Return key uses the editor action

For fields with an IME action (Go/Send/Done/Next/Search/…), the return
key calls `performEditorAction`; only multiline/no-action fields get a
committed `"\n"`. Model-side effects (learn word, clear sentence
context, arm Shift) run identically in both cases.

*Why*: platform-required difference — on Android, committing a newline
does not trigger the field's action; the user-visible behavior (the
labeled key does what it says) is what parity means here.

## D-017 (2026-07-19) — Android always paints its own keyboard background

On iOS the `system` theme leaves the root transparent over the
OS-provided blurred keyboard backdrop; only forced themes paint a
background. Android has no host-provided backdrop — an IME window is
opaque — so the Android keyboard always paints its background using the
same stand-in colors iOS uses for forced themes (#D1D3D9 light /
#2B2B2B dark). The `system` theme resolves those colors from `uiMode`;
forced themes will pin them (Stage 7).

*Why*: platform-required difference; the chosen colors are the ones iOS
already matched by eye to the native backdrop, so the visual result is
identical.

## D-018 (2026-07-19) — Fullscreen extract mode is never used

`onEvaluateFullscreenMode()` returns false unconditionally. The
platform default enables extract mode in landscape, replacing the host
field with a fullscreen editor above the keyboard.

*Why*: extract mode breaks the fixed-height contract (§3: the keyboard
owns its height, the host is never restyled) and has no iOS
counterpart — the keyboard is always just the keyboard. Found during
the Stage 1 audit: in landscape the IME window grew to 943 px instead
of the contract height.

## D-019 (2026-07-19) — Two-tier documentation layout

Documentation is organized in two deliberate tiers (refines D-004):

- **Repository root** — normative contracts and entry points only:
  `ANDROID_PORT_CONTEXT.md` (the behavioral specification),
  `BEHAVIOR_SCENARIOS.md` (the acceptance suite), `README.md` (public
  overview), `HANDOFF.md` (continuation guide). These gate all work
  and must be unmissable on first contact with the repository — the
  first thing seen on the hosting landing page or in a directory
  listing.
- **`docs/`** — living engineering records consulted and updated
  *during* work: the architecture plan, the iOS parity notes, this
  decision log, the stage log.

New documentation defaults to `docs/` unless it is a normative project
contract or a repository entry point. The root set is intentionally
small and stable; do not "tidy" the root documents into `docs/`.

*Why*: the failure mode that matters is someone starting work without
having read the contract, not filesystem clutter. Discoverability of
the gating documents outweighs tidiness. The iOS repository keeps the
port-context document under `docs/` because there it is an *export
produced for* this project; here the same document is the *governing
contract*, which earns it root placement.

## D-020 (2026-07-19) — Key label sizes are density-fixed

Key and bar label sizes are specified in dp and converted to sp through
the current density (`Dp.toSp()`), so the system font-size setting does
not scale them.

*Why*: the keyboard's geometry is a fixed contract (§3–§4); labels that
scale with the font setting outgrow their keys and break the visual
match. iOS keyboard extensions ignore Dynamic Type the same way, so
fixed sizes are also the parity-correct behavior.

## D-021 (2026-07-19) — The framework's IME navigation-bar band is reserved

With gesture navigation, `InputMethodService` draws its own back and
IME-switcher buttons **inside the IME window** (`imeDrawsImeNavBar`) in
a band of `navigation_bar_frame_height` (48 dp), while the window's
`navigationBars` inset reports only 24 dp — the buttons' touch targets
shadow anything placed in the difference. The keyboard reserves
`max(navigationBars inset, that band)` below its 250 dp content
(`ui/BottomInset.kt`); the internal-resource lookup is a
framework-required accommodation with a graceful fallback to the plain
inset.

*Why*: found during Stage 2 device verification — taps on the
bottom-left «123» key landed on the framework's back button and hid the
keyboard (`HIDE_SOFT_INPUT_BY_BACK_KEY`, `fromUser=true` in ImeTracker,
with the `NavigationBarFrame` at window coordinates 593–719 confirming
the 48 dp band). Reserving the band restores reliable bottom-row input;
the content contract stays 250 dp with the system band outside it,
exactly like iOS above the home indicator.

## D-022 (2026-07-19) — No in-keyboard globe key — SUPERSEDED by D-027

The keyboard carries no input-method switch key on any page. The
`supportsSwitchingToNextInputMethod` declaration stays in `method.xml`
so the system's own switcher works with this IME.

*Why*: the spec (§2) shows the globe "only when the OS requires an
input switcher". On iOS that requirement (`needsInputModeSwitchKey`) is
false on modern devices because the system offers switching below the
keyboard; on Android it is *never* a requirement —
`shouldOfferSwitchingToNextInputMethod()` is advisory, and every
supported Android version (26+) provides its own switcher affordance
whenever several keyboards are enabled (the navigation-bar keyboard
icon with 3-button navigation; the D-021 band's switcher button with
gesture navigation). An in-keyboard globe would duplicate the system
affordance and shrink the space bar. Owner-reviewed and confirmed
after Stage 2.

## D-023 (2026-07-19) — Parity is the default, not an absolute

iOS parity remains the default goal for every behavior. But when
Android's native conventions or capabilities offer a demonstrably
better user experience than the iOS behavior, the difference is
surfaced to the owner — with both approaches explained and a
recommendation — before anything is implemented. The owner decides;
deliberate divergences are recorded here. Mechanical copying of iOS
where Android is genuinely better is as wrong as silent Android-isms
where parity matters.

## D-024 (2026-07-19) — Overlay headroom above the suggestion bar — SUPERSEDED by D-025

The keyboard reserves 21 dp of extra space above the suggestion-bar
area (total content 271 dp vs the iOS 250), so top-row key previews
and callouts render at their natural offsets from the pressed key. iOS
instead clamps both downward over the key on the top row; that clamp
stays in the code as a safety net but no longer engages. 21 dp is the
exact requirement of the taller overlay: previews need 54 + 11 above
the key (65 vs the 44 available), callouts 54 + 16 − 9 (61).

*Why*: owner-decided refinement under D-023 — callout legibility over
an otherwise empty corner of the bar area. Accepted costs, reviewed
before implementation: the keyboard silhouette is 21 dp taller than
iOS (most noticeable in landscape); the divergence must be remembered
against §3. The bar *content* area is untouched — exactly 36 dp with
an 8 dp gap, so the Stage 4 suggestion-bar spec applies unchanged.

## D-025 (2026-07-19) — Transient overlays float in a pass-through strip

The IME window keeps a 21 dp transparent strip above the keyboard
content, excluded from the host-facing insets: `onComputeInsets` sets
`contentTopInsets`/`visibleTopInsets` below the strip and restricts
`touchableInsets` to a region starting there, so the app lays out
against the 250 dp contract and taps in the strip pass through to it.
Top-row key previews and callouts draw upward into the strip at their
natural offsets, floating over app content — the native Android
preview behavior (Gboard works the same way).

*Why*: supersedes D-024 (permanent 21 dp headroom): same overlay
freedom without costing the app any space or diverging the visible
silhouette from iOS. This is the one sanctioned use of
`onComputeInsets` (D-009 refinement); the touchable region must always
cover everything below the strip, including the framework's IME
navigation band — verified by a pass-through tap test. iOS cannot do
this (`UIInputView` clips at its bounds), which is why iOS clamps
top-row callouts downward instead; the clamp remains in code as a
safety net.

## D-026 (2026-07-19) — Vertical cursor-mode steps use arrow-key events

In space-cursor mode, each 30 dp vertical step sends a
`DPAD_UP`/`DPAD_DOWN` key event (`sendDownUpKeyEvents`) instead of
computing a character offset from the text context. Horizontal steps
keep the character-precise `setSelection` movement (iOS parity).

*Why*: D-023 divergence, owner-approved. The iOS context math only
sees logical (newline-separated) lines; visual wraps are invisible to
IMEs on both platforms, so in a long wrapped paragraph the ported math
degenerated to jumping the caret to the paragraph edges (iOS's blind
one-line jump also relies on iOS truncating context at paragraph
boundaries — Android's untruncated context breaks that assumption).
Arrow-key events delegate the movement to the editor, which owns its
layout: wrapped lines step correctly and the visual column is
preserved. No coordinate-based caret-placement API exists for
third-party IMEs (`InputConnection` is text-index based;
`CursorAnchorInfo` is read-only feedback with inconsistent editor
support), so editor-driven stepping is the ceiling of what Android
allows. Rare non-standard editors will treat the events like hardware
arrow keys — acceptable by construction.

## D-027 (2026-07-19) — Conditional globe key, iOS semantics

The globe key is back, but conditional: it appears only when
`shouldOfferSwitchingToNextInputMethod()` is true AND the system does
not draw its own IME switcher (`config_imeDrawsImeNavBar` false).
Supersedes D-022, whose premise — "Android always provides its own
switcher" — proved false on Samsung/One UI (Android 14, gesture
navigation): Samsung Keyboard's globe menu switches only its own
languages, and no system switcher affordance exists near the keyboard,
leaving Settings as the only way back to this IME. This is the exact
iOS `needsInputModeSwitchKey` design the spec describes in §2: the
globe appears only when the OS requires it. On stock Android 15+ with
the navigation-band switcher the key stays hidden — no duplication.

*Why*: found by owner testing on a Galaxy A52 (Android 14). The
required IME declarations were all verified present and correct; the
gap was environmental, not a manifest issue.

## D-028 (2026-07-19) — Android-native dark surfaces

Dark mode uses Android's own surface colors for the three keyboard
surfaces — background `system_neutral1_900`, keys `system_neutral1_800`,
pressed `system_neutral1_600` (dynamic, API 31+; Material dark grays
as static fallbacks below) — instead of the iOS dark grays. Light mode,
label colors, and the panel/menu palette are unchanged, as are all
geometry, spacing, and animations. Refines D-017's dark stand-ins;
Stage 7's forced-dark theme uses this same palette.

*Why*: D-023 divergence, owner-directed after device testing — native
keyboards (Samsung, Gboard) blend into the system chrome in dark mode;
the iOS grays visibly separated ours from the navigation area. The
iOS-inspired geometry stays; only the dark color semantics are
Android's.
