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

**Status: done (2026-07-19)** — owner-verified on device: layout,
typing, page switching, editor integration, and geometry all confirmed;
UI matches the design goals (globe removed per D-022, gear redrawn).

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

**Status: done (2026-07-19)** — owner-verified on device, including
the review refinements below.

**Findings**
- Hold gestures run on absolute deadlines inside the row's pointer
  loop, so finger jitter never resets a timer (the naive
  per-event-timeout approach would starve backspace repeat under real
  touches).
- Callout/menu insertion goes through the normal key path, so digraph
  casing («Къ»/«КЪ») falls out of `applyCase` with no special cases.
- The gear menu writes the `layoutVariant` preference through the
  service; the model stays storage-free.
- Emulator-verified: auto-cap armed «Аее», Caps Lock «АЕ»; long-press
  «к» inserted «кь» with under-finger initial selection matching the
  iOS math; backspace hold deleted ~14 chars with acceleration;
  double space produced «И. » with re-armed shift; space-drag moved
  the caret 9 characters across lines and inserted nothing; the
  «Лезги чӏал» flash and «ЛЕЗГ» hint render; the gear menu switched
  «ъ» to the top row live and back.
- Owner review fixes: overlays must be measured **unconstrained**
  (`overlayAt` modifier) — the 43 dp row otherwise crops menus and
  bubbles (the gear menu showed only one variant); the 1.2× lowercase
  bump takes a lighter font weight (430 vs 480) because Roboto lacks
  SF's optical sizing and larger glyphs render heavier strokes.
- Owner-driven refinements: transient overlays float in a transparent
  pass-through window strip so top-row previews/callouts keep their
  natural offsets while the visible keyboard stays at the 250 dp
  contract (D-025, supersedes the interim D-024 headroom); vertical
  cursor-mode steps send DPAD arrow events so wrapped lines move one
  visual line with the column preserved — better than the iOS context
  math, which only sees logical newlines (D-026). Both verified on
  device by the owner.

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

**Status: done (2026-07-19)** — owner-approved after testing on a
Galaxy A52 (One UI / Android 14), including the switching and
dark-theme polish rounds.

**Findings**
- The per-glyph machinery maps to Compose as: a backing glyph string
  that may keep a hidden zero-width tail while a shortened glyph
  animates out; per-glyph `AnimatedVisibility` (fade + horizontal
  expand/shrink, 0.2 s easeOut) for prefix morphs; a `generation`
  remount with per-glyph staggered `Animatable`s (22 ms × index) for
  the new-word settle. Glyphs present at remount skip the enter
  animation — the ripple owns their appearance; only morph-added
  glyphs fade/expand in.
- The overflow fallback (plain label, no morph) is approximated by
  glyph count until the real engine brings real words — revisit with
  measured text in Stage 5 if needed.
- Driven by `FakeSuggestionSource` (model package, clearly marked):
  prefix-derived candidates with the middle one posing as learned, a
  static idle trio. Deleted wholesale in Stage 5.
- Emulator-verified: idle trio spread evenly; typing shows the quoted
  literal «еп» plus two candidates in content-sized cells; 0.5 s hold
  on the learned candidate swaps in the inline «“епди” чӏурдани?» row
  with «Ваъ»/«Чӏурун» pills; «Ваъ» restores the bar; tapping the
  literal replaced the prefix with «еп » and returned the bar to
  idle. Animation feel (morph/ripple) is judged on device by the
  owner.
- Galaxy A52 (Android 14) polish: Samsung/One UI gesture navigation
  offers no system IME-switcher affordance, so the conditional globe
  key returned with exact iOS semantics (D-027, superseding D-022);
  dark mode switched to Android-native system surfaces (D-028) so the
  keyboard blends with the system chrome like native keyboards do.
- Second A52 round: the globe now opens the system input-method picker
  instead of cycling (D-029 — the third-party convention on Samsung,
  where direct cycling strands users), and renders as a monochrome
  vector like every other icon. Samsung's own switcher surfaces (its
  globe menu, the One UI navbar keyboard button) never list foreign
  IMEs — confirmed expected One UI behavior, not a declaration issue.

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

**Status: done (2026-07-19)** — owner-approved; emulator-verified
S1/S2/S3/S5/S11/S12/S13 flows recorded below.

**Findings**
- `store/WordSuggestions` ports the iOS queries verbatim (unescaped
  prefix LIKE ordered by length, exact contains, random trio); the
  asset copies into `noBackupFilesDir` and re-copies when the app
  version changes, so dictionary updates ship with releases. Open
  failures degrade to a null store — the keyboard types without
  suggestions rather than crashing.
- The engine lives in `KeyboardModel` exactly as on iOS: local
  `composedWord` (updated synchronously per keystroke, resynced on
  every `onUpdateSelection`), `wordPrefix` with the iOS separator set,
  the three-state `updateSuggestions` pipeline, `displayForm`
  capitalization, resume-word backspace, and acceptance replacing
  `max(context prefix, composed word)` characters. Learned candidates,
  next-word, host-clear learning, and metrics slot into the marked
  seams in Stage 6+.
- Private fields (password variations + `IME_FLAG_NO_PERSONALIZED_LEARNING`)
  render an empty bar per D-015.
- `FakeSuggestionSource` deleted; zero scaffolding references remain
  (grep-verified).
- Emulator-verified with real data: «кӏв» → literal «кӏв» + кӏве,
  кӏвал (S1); accepting кӏвал replaced the prefix and committed
  «кӏвал » (S3); one backspace resumed the full word — bar showed
  кӏвал, кӏвалах, кӏвалин unquoted (S11, S2); further deletion
  shortened the prefix live (S12); erasing to empty rolled a random
  idle trio, capitalized by the sentence-start rule
  («Тӏанутӏ Жумартвалун Лётчиквал» — S13, S5).

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

**Status: implemented 2026-07-19 — awaiting device verification**

**Findings**
- `store/LearnedWords` ports the iOS schema and every formula verbatim
  (WAL, `meta`/`user_word`/`user_bigram`, ranking
  `(count + 3·picked) × recency ×2 × (1 + min(bigram, 4))`, decay every
  2000 events, prune every 200, caps 5000/10000, digraph-aware
  learnability, filters-version purge; the iOS-only palochka migration
  is deliberately not ported — Android starts clean).
- Android pitfall: `rawQuery` binds args as TEXT and SQLite does not
  coerce text in numeric comparisons — numeric values are interpolated
  into the SQL (internal constants only; text stays bound).
- Learning triggers: terminators `. , ? ! ; :`, space, return,
  suggestion acceptance (picked), and host-clear send detection inside
  the resync — all guarded by the private-field flag (D-015).
  `lastCompletedWord` never crosses `. ! ?`, double-space, or return.
- Emulator-verified: three typed completions of «зэз» made it a
  learned suggestion above the dictionary (S6) and produced a bigram
  that surfaced as a single centered next-word prediction (S8);
  long-press → «“зэз” чӏурдани?» → «Чӏурун» deleted the real record
  and the word became a quoted literal again (S9). Host-clear learning
  and picked-weight (S7) are implemented per spec; verifying them
  needs a real messenger send — owner device pass.

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

**Status: implemented 2026-07-19 — awaiting device verification**

**Findings**
- `settings/KeyboardSettings` ports the iOS struct 1:1: a data class
  with the exact preference keys **and** enum raw values
  (`fast/normal/conservative`, `short/normal/long`,
  `system/light/dark`) so a future cloud sync reads both platforms
  uniformly (D-012). The model stays storage-free: the service loads
  at `onCreate` and persists in `applySettings`; every change funnels
  through `KeyboardModel.updateSettings`, which syncs
  `LearnedWords.minVisibleUses` and clears the bar state when the
  master switch goes off (iOS `updateSettings` verbatim).
- Master-off (S14) clears `suggestions`/`learnedDisplayWords`/
  `fallbackSuggestions` synchronously and both `updateSuggestions`
  and `refreshFallbackSuggestions` guard on the setting, so the bar
  is empty the moment the panel closes; learning paths are untouched.
  Re-enabling shows content again from the next refresh (keystroke or
  host change) — exactly the iOS timing.
- The panel is a Compose-foundation port of `SettingsPanelView`:
  page-stack state, 40 dp header (Клавиатура/Кьулухъ back), 36×5
  capsule, radius-14 row cards, verbatim Lezgi strings, 19 ru/en
  subtitle strings ported from the iOS catalog into
  `res/values[-ru]`. It slides over the whole fixed keyboard with
  easeOut 0.28 (graphicsLayer translation) and leaves composition
  only when fully hidden — every reopen is therefore a fresh panel
  (home page, saved words re-fetched), the exact iOS lifecycle.
- Compose pitfalls worth remembering: a plain background is not
  hit-testable, so the panel root needs a catch-all `pointerInput`
  or touches fall through to the key surfaces underneath; and row
  tap handlers capture `settings` copies, so `tappable()` routes
  through `rememberUpdatedState` — a stale closure would re-apply an
  old settings snapshot.
- Theme forcing lives at the root: `KeyboardView` derives the
  effective appearance from `settings.theme` and resolves
  `KeyboardColors` from it, so a radio tap recolors keyboard and
  panel in the same frame with the panel open. Forced dark is the
  D-028 Android-native palette; the background is always painted
  (D-017), covering the forced case by construction. Panel palette
  roles (`panelBackground`, `panelSeparator`, `labelTertiary`) joined
  `KeyboardColors`; accent/tint/card/secondary reuse the existing
  menu roles (same values by design).
- The panel shows the Android app's own `versionName` (currently
  1.0.0), not the iOS 1.2.0 — D-030.
- SF-symbol icons (keyboard, sliders, half-circle, book, info,
  chevrons, xmark, checkmark) are drawn as vector line art
  (`ic_panel_*`), matching the existing key-icon style.
- One shared scroll container backs all pages, so the scroll offset
  persists across page navigation — the same behavior as the iOS
  panel's single ScrollView.
- Emulator-verified (Pixel API level image, `emulator-5554`): gear
  tap opens the panel over bar + keys; home rows show live values
  (variant, theme, saved-count, version); master off ⇒ bar fully
  empty both idle and while composing «Кен», back on ⇒ candidates
  return on the next keystroke (S14); Мичӏи recolored panel +
  keyboard + background instantly with the panel open, Системадин
  restored light the same way (S16); a non-dictionary «зузуз» typed
  three times appeared in Гафарган with counter 1 derived from the
  list, and the delete-all sheet reset to «Гьеле гафар авач» /
  counter 0 (S10); the preferences XML holds exactly the iOS keys
  and raw values; crash buffer clean. Timing-sensitive toggles
  (callout delay, space-cursor off, «ЛЕЗГ» off, auto-space off,
  double-space off) are wired but need the owner's device pass.

**Objective**: full in-keyboard settings with live effect.

**Components implemented**: `settings/KeyboardSettings`
(SharedPreferences, iOS keys); gear-key slide-up panel with page stack
(home / Раскладка / Кхьин / Тема / Гафарган / Клавиатурадикай), exact
palette and strings, ru/en subtitles; all toggles wired live (S14
semantics); learning-speed and callout-delay application; theme
system/light/dark applied instantly with background painting; layout
variant unified with the quick menu; saved-words page with derived
counter, per-row delete, delete-all sheet
(`LearnedWords.topWords`/`reset`).

**Intentionally deferred**: metrics, emoji page.

**Device test checklist**: S10 (saved words = user vocabulary only,
counter equality, per-row × delete), S14 (master off ⇒ empty bar,
learning continues — verify via Гафарган counter growth while off),
S16 (instant theme, both directions, system restore); every toggle's
live effect (callout delay 0.2/0.45, space-cursor off, «ЛЕЗГ» off,
auto-space off, double-space off, learning speed 1/5); variant switch
from both entry points.

**Acceptance scenarios expected to pass**: S10, S14, S16 — full suite
S1–S17 now green except emoji-related checks (none exist in S1–S17).

---

## Stage 8 — Metrics, diagnostics + emoji page

**Status: implemented 2026-07-19 — awaiting device verification**

**Findings**
- The five `meta` counters port the iOS semantics exactly: the
  `wordHadPredictions` flag (set only while composing, reset by the
  empty-prefix branch and every completion hook), the
  `pendingAcceptedWord` correction tracking (settled by
  `syncComposedWord` when the cursor moves on, converted to
  `m_corrected` by the backspace resume-into-word), literal-tap
  exclusion in `recordPickedSuggestion`, and the manual-completion
  hook in terminator/host-clear paths. `bumpMetric` is one upsert;
  `reset()` never touches `m_*` keys, so the history survives a
  learned wipe. The DEBUG-only startup line (`kb-metrics` logcat tag,
  `BuildConfig.DEBUG`) prints the same summary format as iOS.
- `EmojiData.kt` is generated mechanically from the iOS
  `EmojiData.swift` and verified byte-identical (8 categories, 1898
  emoji, Lezgi titles); regeneration happens alongside the iOS
  catalog, never by hand. A load-time `Paint.hasGlyph` filter drops
  emoji the device font lacks — Android emoji fonts trail Unicode
  far more than iOS, and the filter is what keeps the grid tofu-free
  on older devices (the filter list is per-process, computed once).
- The emoji page keeps the iOS flat-lazy structure (one `LazyRow` of
  small uniform 5-emoji columns — nested lazy grids defeat laziness
  and blew the iOS extension's memory limit) with the exact geometry:
  38×33 cells, 26 glyphs, spacing 2, 10 dp section gap, 13 semibold
  title strip following the leading edge (derived from the first
  visible column), 36 dp category bar with 44 dp «АБВ»/delete side
  zones, 15 dp icons, 30 dp selection circle, zone-at-touch-down
  dispatch, and the letters-page backspace repeat curve. Category
  taps jump instantly (`scrollToItem`); the icons are drawn vectors
  mapping the SF symbols. Recents: id −1, limit 24, move-to-front
  dedup, persisted newline-joined under the iOS `recentEmojis` key
  (an emoji sequence never contains a newline).
- The sticker section (D-031) joins the page as id −2 «Стикерар»
  after the categories: 2×84 dp cells decoding the bundled WebP at
  quarter resolution only while visible. Both the section and its
  bar icon exist only when `EditorInfo.contentMimeTypes` accepts
  WebP or PNG, so plain text fields and password fields never see
  it. Insertion commits an `InputContentInfo` over a FileProvider
  cache copy with a read grant; PNG is transcoded on demand when
  WebP is not accepted.
- The launcher icon (D-032) derives from `AppIcon-1024.png`:
  stripes-only background (card-free band stretched), card
  foreground scaled into the mask-safe zone, keyboard-glyph
  monochrome layer, legacy raster mipmaps, manifest icon refs.
- Astral-character fixes surfaced by the emoji page (emoji are not
  word separators, so a composed word can now contain one — same as
  iOS): `MorphingWordText` renders grapheme clusters (ICU
  `BreakIterator`) instead of UTF-16 units, which used to tear a
  surrogate pair into two tofu boxes in the quoted literal; and
  `LearnedWords.lezgiLetterCount` no longer counts low surrogates,
  so a lone emoji stays unlearnable exactly like iOS's
  grapheme-based count.
- Emulator-verified (`emulator-5554`, screenshots): metrics baseline
  line at startup; emoji page opens from the emoji key; grid
  scrolls; section title follows; category-bar jump to «Стикерар»;
  АБВ returns to letters; emoji insert lands in the field and
  reorders recents live (persisted across reinstalls); «😀» renders
  intact as the bar's quoted literal after the grapheme fix; sticker
  tap committed the image end-to-end — Google Messages accepted the
  commit and raised its own "attachments not supported in this
  conversation" dialog, which is the emulator's missing MMS/RCS
  transport, not the keyboard (the FileProvider cache copy was
  created and served); adaptive icon live on the launcher under a
  circular mask; crash buffer clean throughout.

**Objective**: measurement infrastructure and the last page.

**Components implemented**: five `meta` counters with exact semantics
(literal-tap exclusion, event-based corrected, one opportunity per
word), surviving learned reset; DEBUG-only startup log line with
acceptance rate; emoji page (flat lazy 5-emoji columns, sections +
recents id −1 limit 24, category bar with АБВ/repeating backspace,
press flash, hasGlyph filtering, recents persistence); sticker
section + Commit Content insertion (D-031); launcher icon (D-032);
grapheme-cluster fixes in the bar and the learnability filter.

**Intentionally deferred**: nothing — Stage 8 cloud sync is out of
scope by design (the store is already event-shaped for it).

**Device test checklist**: metrics line in logcat after typing sessions
(opportunities/accepted/ignored/corrected behave per definitions);
emoji insert/recents/section jumps/backspace repeat; sticker send in
a real messenger (Messages with RCS/MMS, WhatsApp, Telegram) and the
section's absence in plain text fields; themed icon on Android 13+;
full S1–S17 pass.

**Acceptance scenarios expected to pass**: S1–S17 verbatim.
