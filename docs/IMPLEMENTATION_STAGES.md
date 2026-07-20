# Implementation Stages

The staged implementation plan and running log. Each stage is
independently buildable and testable on a real device. As stages
complete, their entries gain a status line and any findings; discovered
parity details go to `IOS_PARITY.md`, architectural decisions to
`DECISIONS.md` — this file records *what was built when* and *what to
test*.

Statuses: `planned` → `in progress` → `done (date)`.

Acceptance scenarios (S1–S17) reference `BEHAVIOR_SCENARIOS.md`.

---

## Stage 1 — Project skeleton + fixed-height IME

**Status: done (2026-07-19)** — verified on an API 36 emulator and
closed with a full repository audit.

**Objective**: a buildable, installable IME that appears with the exact
fixed-height geometry and correct background — the height contract
proven on a real device before any keys exist.

**Components implemented**
- Gradle project: single `app` module, Kotlin + Compose, version
  catalog, wrapper; namespace `com.lekitech.lezgikeyboard`; no INTERNET
  permission; `allowBackup="false"`.
- IME declaration: manifest service + `res/xml/method.xml`, subtype
  language `lez`, keyboard name «Лезги чӏал».
- `ime/LezgiInputMethodService`: hosts a ComposeView fixed at 250 dp.
- `ime/ImeLifecycleOwner`: lifecycle/saved-state glue Compose requires
  inside a Service window.
- `ui/KeyboardView`: placeholder rendering of the height contract
  (36 bar + 8 gap + 4×43 rows + 3×11 gaps + 1 slack, side padding 6).
- `ui/theme/KeyboardColors`: light/dark palette resolution from
  `uiMode` (system theme only at this stage).
- `onboarding/MainActivity`: minimal enable/switch instructions with
  buttons to system IME settings and the input-method picker.
- Bundled dictionary moved to `app/src/main/assets/` (single copy).
- `.gitignore`.

**Intentionally deferred**
- All key rendering, typing, gestures (Stage 2–3).
- Suggestion bar content and animations (Stage 4–5).
- Databases, settings, themes beyond system, emoji, metrics.

**Device test checklist**
- [x] App installs; onboarding opens; both buttons work (settings list,
      IME picker).
- [x] Keyboard can be enabled and selected; it appears in any app.
- [x] Height is exactly 250 dp of content; the layout blocks match the
      contract; no host-driven resizing (window frame 719 px = 250 dp
      + navigation inset, portrait and landscape).
- [x] Background and placeholder colors follow system light/dark.
- [x] Rotation and switching between fields do not change the height.
- [x] Gesture-navigation devices: keyboard content sits above the
      system gesture area.

**Acceptance scenarios expected to pass**: none yet (no typing).

**Architectural notes**
- The 250 dp is the *content* height; system navigation insets are
  padded outside it (platform-required, D-009/D-017 context).
- Android always paints its own keyboard background (D-017).

**Findings**
- Blocker "keyboard never appears" — two stacked causes, both verified
  at runtime on an API 36 emulator:
  1. *Environmental*: on emulators with a hardware keyboard attached
     and «show on-screen keyboard with physical keyboard» off
     (`Settings.Secure show_ime_with_hard_keyboard = 0`), the default
     `InputMethodService.onEvaluateInputViewShown()` returns false and
     the IME silently declines every show — logcat shows only
     `ImeTracker onFailed at PHASE_IME_ON_SHOW_SOFT_INPUT_TRUE`, no
     exception. Gboard shows regardless because it overrides that
     evaluation; we keep the platform-default behavior (the toggle is
     the user's choice). Emulator testing requires the setting on;
     real phones report `KEYBOARD_NOKEYS` and are unaffected.
  2. *Code bug (was hidden behind 1)*: with the show allowed, the
     keyboard crash-looped —
     `IllegalStateException: ViewTreeLifecycleOwner not found from
     LinearLayout{… android:id/parentPanel}` in
     `createLifecycleAwareWindowRecomposer` during
     `AbstractComposeView.onAttachedToWindow`. Compose resolves the
     window `Recomposer` from the **window root**, not from the
     `ComposeView`, and an IME window has no lifecycle owners of its
     own — `ImeLifecycleOwner` must be attached to
     `window.window.decorView` in `onCreateInputView`, not only to the
     ComposeView. Verified fixed: no exception, window
     `isVisible=true`, placeholder renders. Any future window-level
     Compose surface must follow the same rule.
- Closing audit: in landscape the platform switched the IME into
  fullscreen extract mode (window 943 px, host field replaced by a
  fullscreen editor) — fixed by `onEvaluateFullscreenMode() = false`
  (D-018), landscape re-verified at the contract height. Cleanup:
  removed an unused import, two not-yet-used palette roles, and the
  unused `core-ktx` dependency; README expanded into a project
  overview. Deferred cosmetics: the launcher uses the default system
  icon (a product icon is a Stage 7+ asset decision).

---

## Stage 2 — Keyboard layout + typing

**Status: implemented 2026-07-19 — awaiting device verification**

**Findings**
- The framework's IME navigation-bar band (48 dp, drawn inside our
  window with gesture navigation) shadowed the bottom key row's touch
  targets — taps on «123» hit the system back button and hid the
  keyboard. Fixed by reserving the band below the 250 dp content
  (D-021, `ui/BottomInset.kt`).
- Key label sizes are density-fixed so the system font-size setting
  cannot break the geometry (D-020).
- `EditorState` currently parses only the return action (the fields
  Stage 2 consumes); the autocapitalization mode and password flag join
  in Stages 3 and 5 respectively.
- Emulator-verified: letters/numbers/symbols pages render with correct
  weights and icons; «йцӏх» commits with the Cyrillic palochka; «123 →
  #+= → ?» switches pages and the punctuation returns to letters with
  the character committed; a search field renders the magnifier return
  key. Reinstalling the APK deselects the IME (Android behavior) — 
  re-select it before testing.
- Owner review: the in-keyboard globe key was removed — Android always
  provides its own switcher, so the key only duplicated it (D-022);
  the settings gear was redrawn as a proper eight-tooth gear contour.

**Objective**: full static layout with working text input on all three
character pages.

**Components implemented**
- `layout/LezgiLayout`: rows for letters (both variants; classic active),
  numbers, symbols; callout table (data only); weights (1.0 / 1.2 /
  1.8→2.3 / 4.5); labels; case helpers; return-key labels and weights;
  font-size rules (22 / ×1.2 lowercase bump; service sizes).
- `model/KeyboardModel` (first slice): page state, `rows(needsGlobe:)`,
  bottom-row assembly, character commit via the text-edit proxy.
- `ime/EditorState`: return action, autocap mode, password flag, globe
  requirement from `EditorInfo`.
- `ui/keys`: RowView (weight layout, 6 dp paddings/gaps, visual +
  gesture layers, expanded ±5.5 dp hit zones, nearest-key dispatch),
  KeyButton (colors, radius 8, hairline shadow, typography, icons).
- Page switching (123 / #+= / АБВ), punctuation-returns-to-letters,
  return key action labels + Search icon, `performEditorAction` (D-016),
  globe key gated by the system switcher requirement.

**Intentionally deferred**: press bubbles, callout interaction, Shift
logic and auto-capitalization, backspace repeat, space gestures, all
suggestion behavior, composedWord tracking (arrives with the engine).

**Device test checklist**: type Lezgi text in several apps across all
three pages; verify geometry/weights against iOS side by side; return
key per field action (S17 visual half); no dead zones between keys;
globe appears only when the system requires it.

**Acceptance scenarios expected to pass**: S17 (visual part).

---

## Stage 3 — Key interactions

**Status: planned**

**Objective**: the keyboard *feels* like the iOS one — every §5 gesture.

**Components implemented**: press bubble; long-press callouts with
slide-to-select and digraph casing; Shift state machine
(off/once/capsLock) + auto-capitalization (context re-evaluation,
honoring host settings; `. ? !` on symbol pages arming rule); backspace
hold-repeat with acceleration; space cursor mode; double-space period;
«Лезги чӏал» appearance flash + «ЛЕЗГ» corner label; gear long-press
quick layout menu; return arming Shift.

**Intentionally deferred**: gear tap (settings panel, Stage 7);
suggestion bar; learning.

**Device test checklist**: every interaction in IOS_PARITY.md «Keys and
gestures» compared against the iOS device side by side — bubble/callout
geometry, timing values (0.2/0.3/0.45, 0.4, 0.35, 0.5 s), repeat
acceleration, cursor-mode ratios (8 dp/char, 30 dp/line), double-space
window (0.35 s), digraph casing («Къ»/«КЪ»).

**Acceptance scenarios expected to pass**: S15 (typing half).

---

## Stage 4 — Suggestion bar UI + animations

**Status: planned**

**Objective**: pixel- and motion-faithful bar, driven by fake data.

**Components implemented**: 36 dp bar with content-sized cells and
flexible-gap spread; press capsule (letter-key color, D-001); shrink →
truncate fallback; whole-bar nearest-center dispatch; prefix morph
(easeOut 0.2 s), new-word settle (0.2 s, 22 ms stagger, scale 1.15,
y +1.5), snap for full swaps; «…» literal rendering; inline delete
confirmation row (stub action); scripted fake candidate source.

**Intentionally deferred**: real candidates, tap-to-insert semantics,
learned-word detection.

**Device test checklist**: animation regimes side by side with iOS
(extend/shorten word, full swap, literal); press capsule always contains
text; empty-slot collapsing (1/2/3 words); confirm row appearance.

**Acceptance scenarios expected to pass**: none end-to-end (fake data).

---

## Stage 5 — Prediction engine + bundled dictionary

**Status: planned**

**Objective**: real suggestions from the bundled dictionary with the
full composition model.

**Components implemented**: `store/WordSuggestions` (prefix LIKE / exact
contains / random trio, asset copy versioning); `composedWord` +
`lastCompletedWord` tracking with the sync pipeline
(`onUpdateSelection`); three bar states (prefix, next-word arrives
Stage 6, idle with transition-only re-roll); quoted-literal rule;
display capitalization; suggestion acceptance (max(prefix, composed)
replacement, auto-space, chaining); backspace resume-word (S11);
password-field bar emptiness (D-015).

**Intentionally deferred**: learning store, next-word path, metrics,
settings (defaults hardcoded).

**Device test checklist**: S1/S2 literal behavior; S3 acceptance; S5
capitalization contexts; S11/S12 deletion behaviors; S13 idle re-roll;
S17 full; fast-typing prefix integrity (composedWord vs lagging
context); password fields show an empty bar.

**Acceptance scenarios expected to pass**: S1, S2, S3, S5, S11, S12,
S13, S17.

---

## Stage 6 — Learning store + ranking

**Status: planned**

**Objective**: the personal model — learning, ranking, next-word.

**Components implemented**: `store/LearnedWords` (exact schema, WAL,
verbatim SQL); completion triggers incl. host-clear send detection;
learnability filters (digraph-aware); ranking formula + recency +
visibility gate; bigram learning + next-word path; caps/prune/decay/
filters-version purge; learned-suggestion long-press delete (real);
password-field learning disablement.

**Intentionally deferred**: settings panel (thresholds fixed at
defaults), saved-words page, metrics counters.

**Device test checklist**: S6 (threshold 3), S7 (picked weight), S8
(bigram ≥ 2), S9 (delete + relearn), sentence-boundary clearing (S15
second half), host-clear learn in a messenger, learned-above-dictionary
ordering.

**Acceptance scenarios expected to pass**: S6, S7, S8, S9, S15.

---

## Stage 7 — Settings panel + themes

**Status: planned**

**Objective**: full in-keyboard settings with live effect.

**Components implemented**: `settings/KeyboardSettings`
(SharedPreferences, iOS keys); gear-key slide-up panel with page stack
(home / Раскладка / Кхьин / Тема / Гафарган / Клавиатурадикай), exact
palette and strings, ru/en subtitles; all toggles wired live (S14
semantics); learning-speed and callout-delay application; theme
system/light/dark applied instantly with background painting; layout
variant unified with the quick menu; saved-words page with derived
counter, per-row delete, delete-all sheet.

**Intentionally deferred**: metrics, emoji page.

**Device test checklist**: S10 (saved words = user vocabulary only,
counter equality), S14 (master off ⇒ empty bar, learning continues),
S16 (instant theme, both directions, system restore); every toggle's
live effect; variant switch from both entry points.

**Acceptance scenarios expected to pass**: S10, S14, S16 — full suite
S1–S17 now green except emoji-related checks (none exist in S1–S17).

---

## Stage 8 — Metrics, diagnostics + emoji page

**Status: planned**

**Objective**: measurement infrastructure and the last page.

**Components implemented**: five `meta` counters with exact semantics
(literal-tap exclusion, event-based corrected, one opportunity per
word), surviving learned reset; DEBUG-only startup log line with
acceptance rate; emoji page (flat lazy 5-emoji columns, sections +
recents id −1 limit 24, category bar with АБВ/repeating backspace,
press flash, hasGlyph filtering, recents persistence).

**Intentionally deferred**: nothing — Stage 8 cloud sync is out of
scope by design (the store is already event-shaped for it).

**Device test checklist**: metrics line in logcat after typing sessions
(opportunities/accepted/ignored/corrected behave per definitions);
emoji insert/recents/section jumps/backspace repeat; full S1–S17 pass.

**Acceptance scenarios expected to pass**: S1–S17 verbatim.
