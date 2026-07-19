# Android Architecture Plan

High-level architecture of the Android port, fixed **before**
implementation. This document defines structure and responsibilities;
`ANDROID_PORT_CONTEXT.md` defines behavior; `docs/IOS_PARITY.md` defines
undocumented behavior; `docs/DECISIONS.md` records why. If code and this
document ever disagree, one of them is wrong — fix the divergence in the
same change.

Guiding rule: the Android keyboard is the same product on a different OS.
The architecture deliberately mirrors the iOS implementation component by
component (D-005), so that a behavior found in one codebase can be located
in the other by name.

## 1. Technology and constraints

- Kotlin, single Gradle module (`app`), no dynamic features.
- `InputMethodService` + `InputConnection` for all text operations.
- Jetpack Compose (foundation only — no Material components; every
  control is custom-drawn to match the product design, D-010).
- Raw `android.database.sqlite` — no Room/ORM (D-006): the SQL strings,
  ordering, `LIKE` semantics, and integer division must stay
  byte-compatible with iOS.
- All engine and storage work is synchronous on the main thread, exactly
  like iOS (D-007): the per-keystroke queries are tiny, and identical
  event ordering is a correctness requirement (metrics, learn hooks).
- No INTERNET permission; `android:allowBackup="false"` (D-008).
- No DI framework; the service wires everything by hand (D-011).
- minSdk 26, targetSdk latest stable (D-014).

## 2. Project structure

```
LezgiKeyboard-Android/
├── docs/                             this documentation set
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/lezgi_words.sqlite     bundled dictionary (identical to iOS)
│       ├── res/
│       │   ├── values/strings.xml        en subtitles (default)
│       │   ├── values-ru/strings.xml     ru subtitles
│       │   ├── xml/method.xml            IME declaration, subtype lez
│       │   └── drawable/                 monochrome vector key icons
│       └── kotlin/com/lekitech/lezgikeyboard/
│           ├── ime/
│           │   ├── LezgiInputMethodService.kt
│           │   ├── ImeLifecycleOwner.kt
│           │   └── EditorState.kt
│           ├── model/
│           │   └── KeyboardModel.kt
│           ├── layout/
│           │   └── LezgiLayout.kt        (+ KeyCap, KeyboardPage, LayoutVariant)
│           ├── store/
│           │   ├── WordSuggestions.kt
│           │   └── LearnedWords.kt
│           ├── settings/
│           │   └── KeyboardSettings.kt
│           ├── ui/
│           │   ├── KeyboardView.kt
│           │   ├── keys/                 RowView, KeyButton, KeyPreviewBubble,
│           │   │                         CalloutBubble, LayoutMenuBubble
│           │   ├── suggestions/          SuggestionBar, MorphingWordText
│           │   ├── emoji/                EmojiPage, EmojiData
│           │   ├── panel/                SettingsPanelView
│           │   └── theme/                KeyboardColors
│           └── onboarding/
│               └── MainActivity.kt
├── build.gradle.kts, settings.gradle.kts, gradle/ (wrapper, version catalog)
```

- applicationId / namespace: `com.lekitech.lezgikeyboard` (matches the
  iOS bundle id).
- The root-level `lezgi_words.sqlite` moves into `app/src/main/assets/`
  when the skeleton is created — one copy, one source of truth.
- File names mirror iOS on purpose. The exact split inside `ui/` may
  refine during stages; the package responsibilities below are fixed.

## 3. Component responsibilities

| Component | Mirrors (iOS) | Responsibility |
|---|---|---|
| `LezgiInputMethodService` | `KeyboardViewController` | IME entry point. Owns the `InputConnection`, the model, the stores, the fixed-height ComposeView, timers (name flash). Translates host events into the model pipeline and model closures into `InputConnection` calls. Applies the theme. Never shows dialogs. |
| `ImeLifecycleOwner` | — (Android-only glue) | Minimal `LifecycleOwner` / `SavedStateRegistryOwner` harness that Compose requires inside a Service window. No behavior. |
| `EditorState` | proxy traits reading | Pure mapping of `EditorInfo`/`InputType` → return-key action, autocapitalization mode, password-field flag, globe requirement. |
| `KeyboardModel` | `KeyboardModel` | The brain. Pages, shift state machine, `composedWord` / `lastCompletedWord`, key handling, three-state suggestion pipeline, shared capitalization, learn hooks, metrics flags, settings application, emoji recents. Exposes Compose state; contains no Compose UI and no `InputConnection` calls (it edits text through a narrow proxy interface the service implements). |
| `LezgiLayout` | `LezgiKeyboardLayout` | Static truth: rows per page/variant, callout table, case helpers, labels, weights, return-key labels/weights, font sizing rules. Pure data + pure functions. |
| `WordSuggestions` | `WordSuggestions` | Read-only bundled dictionary: prefix LIKE (top 3 by length), exact contains, random trio. Query layer shaped so `ORDER BY baseScore DESC, LENGTH(word)` can slot in without engine changes (base-score contract). |
| `LearnedWords` | `LearnedWords` | `learned.sqlite`: schema, learn/bigram writes, ranked suggestion queries, next-word query, visibility gate, learnability filters, decay/prune/vacuum, per-word and full reset, saved-words query, metrics counters. |
| `KeyboardSettings` | `KeyboardSettings` | Immutable settings value type + load/save over `SharedPreferences` with the exact iOS keys. |
| `ui/*` | `KeyboardView`, `SettingsPanelView` | Rendering and gesture reporting only. All decisions live in the model; views call closures. |
| `MainActivity` | container app (reduced) | Minimal onboarding: explain how to enable the keyboard, buttons to the system IME settings and the input-method picker. Nothing else. |

## 4. InputMethodService lifecycle

| Callback | Action |
|---|---|
| `onCreate` | Open stores (`WordSuggestions` from assets copy, `LearnedWords` in app storage), load settings, create `KeyboardModel`. Log the DEBUG metrics line. |
| `onCreateInputView` | Build the ComposeView (fixed 250 dp height) with `ImeLifecycleOwner` attached; set content to `KeyboardView(model, closures)`. |
| `onStartInputView(info, restarting)` | The `viewWillAppear` + trait refresh analog: recompute `EditorState` (return action, autocap, password flag), `needsGlobe`, refresh idle suggestions, start the 1.5 s keyboard-name flash, run the sync pipeline once. |
| `onUpdateSelection` | The `textDidChange` analog: run the sync pipeline (below). Fires for the keyboard's own edits too — the pipeline is idempotent by design, same as the iOS resync. |
| `onFinishInputView` | Cancel timers, close the settings panel state, dismiss transient UI. Nothing is learned (draft rule). |
| `onDestroy` | Close both databases. |

**Sync pipeline** (one function, fixed order — parity-critical):
host-clear send detection → resync `composedWord` + `lastCompletedWord`
from the host context → settle `pendingAcceptedWord` → re-evaluate shift
from context → update suggestions.

**Fixed height contract**: the input view is exactly 250 dp tall and
top-aligned; every overlay (bubbles, callouts, layout menu, settings
panel) is clamped to render inside it, exactly as on iOS — so no
`onComputeInsets` tricks and no popup windows are needed. Bottom system
insets (gesture bar), if any, are handled outside the 250 dp content and
documented when Stage 1 meets real devices.

## 5. State ownership

| State | Owner | Notes |
|---|---|---|
| Page, shift, suggestions, literal marker, learned-display set, idle words, cursor-mode flag, name-flash flag, panel visibility, layout variant, settings, recents | `KeyboardModel` (Compose `mutableStateOf`) | Mirrors the iOS `@Published` set one-to-one. |
| `composedWord`, `lastCompletedWord`, metrics flags, idle-transition flag, double-space timestamp | `KeyboardModel` (plain fields) | Not observable — engine-internal, never rendered directly. |
| Pressed key/frame, bubbles, callout selection, bar press index, pending delete word, panel page stack, emoji scroll state | UI (`remember` state) | Transient, render-only — as in the iOS views. |
| Return action, autocap mode, password flag, `needsGlobe` | Service → model | Recomputed from `EditorInfo` on field changes. |
| Persistent values | `SharedPreferences` / SQLite | See layers below. |

The model is created once per service lifetime; views observe it. There
is no ViewModel and no saved instance state — the keyboard rebuilds its
state from storage and the host context on every appearance, like iOS.

## 6. Event flow

```
key touch ─ RowView gesture ─▶ onKey(cap) ─▶ Service
                                              │ model.handleKey(cap)  (edits text via InputConnection)
                                              │ backspace ⇒ model.updateShiftFromContext()
                                              └ model.updateSuggestions()

suggestion tap ─ SuggestionBar ─▶ onSuggestion(word) ─▶ Service
                                              │ capture prefix + previous word
                                              │ batch edit: delete max(prefix, composed) chars, insert word (+space)
                                              │ model.recordPickedSuggestion(...)
                                              │ consume Shift-once
                                              └ model.updateSuggestions()   (hosts may not echo our edits promptly)

host change ─ onUpdateSelection ─▶ sync pipeline (§4)
```

Long-press flows (callouts, gear menu), space-cursor drags, and the
emoji page report through their own closures identically to iOS
(`onCursorMove`, `onCursorLineMove`, `onEmojiInsert`,
`onSuggestionDelete`, `onLearnedReset`).

## 7. Prediction pipeline

Identical to iOS, hosted in `KeyboardModel.updateSuggestions`:

1. Master switch off → clear everything, return (learning continues).
2. Prefix = `composedWord`.
   - Non-empty → learned candidates (ranked, gated) then dictionary
     candidates (`LIKE prefix% ORDER BY LENGTH LIMIT 3`), case-insensitive
     dedup, top 3 → display capitalization → quoted-literal rule
     (unknown to both sources → literal first + 2 predictions).
   - Empty → next-word candidates from bigrams of `lastCompletedWord`
     (gate ≥ 2), else idle trio (re-rolled only on the transition into
     idle).
3. Metrics flags maintained as described in IOS_PARITY.md.

The base-score contract holds: dictionary ordering may later become
`ORDER BY baseScore DESC, LENGTH(word)` inside `WordSuggestions` with no
engine change, and base scores are never numerically mixed with personal
counters.

## 8. Learning pipeline

Triggers (all in `KeyboardModel`, storage in `LearnedWords`):

- terminator typed (`. , ? ! ; :`), space, return → learn `composedWord`
  predecessor (count +1) + bigram;
- suggestion accepted → learn picked (+1 picked) + bigram (previous
  captured before replacement);
- host-clear send detection → learn as manual completion;
- password fields → learning fully disabled (and the bar hidden, D-015).

`LearnedWords` enforces: lowercased storage, learnability filters
(digraph-aware letter count ≥ 2, ≤ 64 chars, no digits/`@ / .`),
visibility threshold (1/3/5), decay every 2000 events, prune checks every
200, caps 5000/10000, filters-version purge. Metrics counters live in
`meta` and survive reset.

## 9. Database layer

- **Bundled dictionary**: shipped in `assets/`, copied once into
  `noBackupFilesDir` (re-copied when the app version changes), opened
  read-only. Same file as iOS, byte-identical.
- **Learned store**: `learned.sqlite` in `noBackupFilesDir`, WAL,
  schema and every SQL statement ported verbatim from iOS (minus the
  iOS-only palochka migration). `SQLiteDatabase` directly — prepared
  statements via `compileStatement`/`rawQuery`.
- Both handles owned by the service, opened in `onCreate`, closed in
  `onDestroy`. Open failures degrade silently (nullable stores), like
  iOS.

## 10. Settings layer

- `SharedPreferences` (default file), exact iOS keys:
  `set_wordSuggestions`, `set_nextWordSuggestions`,
  `set_autoSpaceAfterSuggestion`, `set_doubleSpacePeriod`,
  `set_spaceCursor`, `set_spaceLabel`, `set_learnSpeed`,
  `set_calloutDelay`, `set_theme`, plus `layoutVariant` and
  `recentEmojis` (JSON array string).
- `KeyboardSettings` is a value type: `load()` applies defaults for
  missing keys (defaults = pre-settings behavior), `save()` writes all.
  `KeyboardModel.updateSettings` applies + persists + syncs model-side
  effects (threshold, clearing the bar), exactly like iOS.
- Theme: `system` resolves against the current `uiMode`; `light`/`dark`
  force resolution and additionally paint the keyboard background
  (#D1D3D9 / #2B2B2B). Implemented as a single `KeyboardColors` provider
  recomputed from (theme setting, system dark flag) — a tap in the panel
  recolors everything live.
- Localized subtitles come from `res/values` (en default, `values-ru`);
  all Lezgi strings are verbatim literals in code, as on iOS.

## 11. Compose hierarchy

```
KeyboardView (Box, 250 dp, keyboard coordinate space)
├── EmojiPage                                  (when page == emoji)
│   ├── section title strip
│   ├── lazy row of 5-emoji columns
│   └── category bar (visual layer + gesture layer)
├── Column(spacing 8)                          (letters/numbers/symbols)
│   ├── SuggestionBar (36 dp)
│   │   ├── DeleteConfirmRow                   (when pending delete)
│   │   └── cells: MorphingWordText + press capsule
│   │       └── full-bar gesture layer
│   └── key grid (Column, spacing 11, h-padding 6)
│       └── RowView ×4
│           ├── visual: KeyButton per key (weights, 43 dp)
│           └── gesture: full-row surface, expanded ±5.5 dp
├── KeyPreviewBubble          (overlay, hit-testing off)
├── CalloutBubble             (overlay, hit-testing off)
├── LayoutMenuBubble          (overlay, hit-testing off)
└── SettingsPanelView         (slide-up, covers everything, easeOut 0.28)
    └── page stack: home / layout / input / theme / dictionary / about
        └── delete-all sheet (in-panel dim + card)
```

Key frames are computed by the row layout (weights → x/width) and
reported upward for bubble positioning — same "visual layer + gesture
layer + frame math" pattern as iOS, which is what guarantees the
gap-free touch zones and bubble anchoring.

Timers inside gestures (long-press, repeat, space-cursor activation) are
coroutines scoped to the gesture (`pointerInput`), which cancels them on
release — different mechanism from iOS `DispatchWorkItem`, identical
observable timing. All timing/geometry constants live next to their iOS
values in one place per component.

## 12. Dependency graph

```
onboarding/MainActivity        (no dependencies on the IME internals)

ime/LezgiInputMethodService
 ├──▶ model/KeyboardModel ────▶ layout/LezgiLayout   (pure)
 │        ├──▶ store/WordSuggestions
 │        ├──▶ store/LearnedWords
 │        └──▶ settings/KeyboardSettings
 ├──▶ ime/EditorState          (pure)
 └──▶ ui/KeyboardView ────────▶ model (observes), layout, ui/theme
```

Rules: `ui` never touches `store` or `InputConnection`; `model` never
touches Compose UI or `InputConnection` (text edits go through the
service-implemented proxy interface); `layout` and `EditorState` are
pure; `store` classes know nothing about each other.

## 13. Stable architectural decisions

Recorded in `docs/DECISIONS.md` (D-001 … D-015) — the ones that shape
everything here: iOS implementation as behavioral source of truth;
identical dictionary data; keyboard-only scope; 1:1 component naming;
raw SQLite with verbatim SQL; synchronous main-thread engine; structural
privacy (no INTERNET, no backup); fixed 250 dp with everything rendered
inside; no Material; manual wiring; iOS-parity preference keys;
centralized constants; minSdk 26; password-field behavior.

Platform-required differences (the only sanctioned kind) are listed in
DECISIONS.md as they arise; the first known ones: the return key uses
`performEditorAction` for action fields (newline only for multiline
fields), `onUpdateSelection` echoes the keyboard's own edits (handled by
the idempotent resync), and emoji availability is filtered at runtime
with `Paint.hasGlyph` on the same catalog.
