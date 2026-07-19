# Project Handoff — LezgiKeyboard-Android

A self-contained continuation guide for the next engineer or
development agent taking over this project without access to prior
project conversations. Read this first, then the documents in §4, and
you can work from the current baseline.

## 1. Project overview

**Purpose.** A native Android port of the iOS Lezgi keyboard
([github.com/LekiTech/LezgiKeyboard](https://github.com/LekiTech/LezgiKeyboard)).
The goal is *behavioral identity*, not adaptation: a user switching
between iOS and Android must never have to relearn the keyboard. When
"more Android-like" conflicts with "identical to iOS", identical wins;
platform-specific differences are acceptable only when the Android IME
framework requires them, and they must not change user-visible
behavior.

**Technology.** Kotlin, `InputMethodService`, Jetpack Compose
(foundation only — no Material), raw SQLite (no ORM), single Gradle
module, no DI framework, no INTERNET permission, `allowBackup=false`.
minSdk 26, targetSdk 36.

**Current status.** Stage 1 of 8 complete (tag `stage-1`): the
fixed-height IME shell is installed, verified on device, and audited.
The keyboard renders placeholder key rows; typing arrives with Stage 2.

**Repository structure.**

```
ANDROID_PORT_CONTEXT.md      behavioral specification (authoritative)
BEHAVIOR_SCENARIOS.md        acceptance suite S1–S17 (authoritative)
HANDOFF.md                   this file
README.md                    public overview
docs/                        architecture plan, parity notes, decision
                             log, stage log (see §4)
app/src/main/
  AndroidManifest.xml        IME service + onboarding activity
  assets/lezgi_words.sqlite  bundled dictionary (20,356 words)
  res/xml/method.xml         IME declaration, subtype lez
  res/values[-ru]/           en/ru onboarding + subtitle strings
  kotlin/com/lekitech/lezgikeyboard/
    ime/                     LezgiInputMethodService, ImeLifecycleOwner
    ui/                      KeyboardView (placeholder), theme/KeyboardColors
    onboarding/              MainActivity (enable/switch shortcuts)
gradle/, gradlew, *.kts      Gradle 8.14.3, AGP 8.10.1, Kotlin 2.1.21
```

## 2. Stage 1 summary

**Implemented.** Gradle skeleton; IME registration (subtype `lez`, name
«Лезги чӏал»); a ComposeView input view fixed at 250 dp rendering the
height contract as placeholder blocks; light/dark palette resolution;
minimal onboarding activity; the bundled dictionary in assets; the
documentation set; `.gitignore`.

**Problems solved (all verified with runtime evidence):**

1. *Keyboard never appeared — cause A (code).* Compose resolves its
   `Recomposer` from the **window root** (decor view), not from the
   `ComposeView`. An IME window has no lifecycle owners, so composition
   crashed with `IllegalStateException: ViewTreeLifecycleOwner not
   found`. Fix: `ImeLifecycleOwner` is attached to
   `window.window.decorView` in `onCreateInputView` (and to the
   ComposeView). Any future window-level Compose surface must do the
   same.
2. *Keyboard never appeared — cause B (environment).* On emulators
   with a hardware keyboard and «show on-screen keyboard while hardware
   keyboard active» off, the default
   `InputMethodService.onEvaluateInputViewShown()` silently declines
   every show (`ImeTracker onFailed at
   PHASE_IME_ON_SHOW_SOFT_INPUT_TRUE`, no exception). This is not a
   bug; Gboard overrides the check, we deliberately honor the user's
   toggle. Emulator testing requires
   `settings put secure show_ime_with_hard_keyboard 1`.
3. *Landscape broke the height contract.* The platform default enables
   fullscreen extract mode in landscape (the IME window grew to nearly
   the whole screen). Fix: `onEvaluateFullscreenMode() = false`,
   permanently (D-018).
4. *Stale dictionary.* The repo initially carried a pre-migration
   dictionary using Latin `I` as the palochka in 2,374 words. It was
   replaced with the iOS bundle's migrated file (Cyrillic `ӏ` U+04CF
   everywhere, md5 `a33c970b680efae7f39f500f0b8408e4`). Both platforms
   must always ship byte-identical dictionary data (D-002).

**Why decisions were made.** Every architectural decision is logged
with rationale in `docs/DECISIONS.md` (D-001…D-018). The load-bearing
ones: the iOS *implementation* (not the spec) is the source of truth
for user-visible behavior (D-001); components mirror iOS names 1:1
(D-005); raw SQLite with verbatim SQL (D-006); synchronous main-thread
engine for identical event ordering (D-007); structural privacy
(D-008); everything renders inside the fixed 250 dp window (D-009);
custom-drawn UI, no Material (D-010); iOS preference keys (D-012);
password fields get no bar content and no learning (D-015); the return
key uses `performEditorAction` (D-016); Android always paints its own
keyboard background (D-017).

## 3. Current architecture

| Component | Responsibility today | Grows into |
|---|---|---|
| `ime/LezgiInputMethodService` | Hosts the 250 dp ComposeView; attaches lifecycle owners at the window root; disables extract mode | The full iOS `KeyboardViewController` analog: owns `InputConnection`, stores, model wiring, the event pipeline (see plan §4/§6) |
| `ime/ImeLifecycleOwner` | Lifecycle/saved-state glue Compose requires in a Service window | Unchanged |
| `ui/KeyboardView` | Renders the height contract as placeholders | Root composable: suggestion bar + key grid + overlays + settings panel |
| `ui/theme/KeyboardColors` | Light/dark palette (background, letter key), resolved from `uiMode` | Full palette; forced-theme resolution in Stage 7 |
| `onboarding/MainActivity` | Enable/switch shortcuts (verified) | Unchanged |

**Data flow today** is trivial (no input handling). The target
pipeline, fixed in `docs/ANDROID_ARCHITECTURE_PLAN.md` §4–§8, mirrors
iOS: key touch → model handles → `InputConnection` edit → suggestions
refresh; host change (`onUpdateSelection`) → sync pipeline in fixed
order (host-clear learn → resync composed word → shift re-evaluation →
suggestions).

**Extension points for Stage 2**: new packages `layout/` (pure truth
table), `model/` (KeyboardModel first slice), `ime/EditorState.kt`
(pure `EditorInfo` mapping), `ui/keys/` (RowView + KeyButton);
`KeyboardColors` regains `letterKeyPressed` and `label` roles when
KeyButton uses them (they were removed in the Stage 1 audit as
not-yet-used).

## 4. Documentation map

Authority order for behavior questions:

1. `ANDROID_PORT_CONTEXT.md` — the specification.
2. `BEHAVIOR_SCENARIOS.md` — acceptance scenarios S1–S17 (the finished
   keyboard must pass all of them verbatim).
3. The iOS implementation — canonical for anything the spec does not
   state (github.com/LekiTech/LezgiKeyboard; locally at
   `/Users/enver/projects/iOS/lezgikeyboard` on the owner's machine).
   If spec and implementation conflict on user-visible behavior, the
   implementation wins (D-001) — but surface the conflict to the owner.

Working documents in `docs/` — where changes get recorded:

| Document | Role | Update when |
|---|---|---|
| `ANDROID_ARCHITECTURE_PLAN.md` | Target structure, responsibilities, pipelines | Architecture evolves |
| `IOS_PARITY.md` | Undocumented iOS behaviors that must be preserved (normative) | Any new parity detail is discovered — document it **before** implementing it |
| `DECISIONS.md` | Chronological decision log with rationale (D-001…) | Any decision affecting future development; never delete entries, supersede them |
| `IMPLEMENTATION_STAGES.md` | Stage plan and implementation log with device checklists | Stage progress, findings, audits |

Rule: architecture must never exist only in code. Documentation is part
of the implementation, not an afterthought.

## 5. Stage 2 readiness

**Deliverables** (full detail in `IMPLEMENTATION_STAGES.md` Stage 2):
`layout/LezgiLayout` (letter rows in both `ъ` variants, numbers,
symbols pages; callout data table; weights 1.0 / 1.2 / 1.8→2.3 / 4.5;
labels; case helpers for digraphs; return labels; font-size rules);
`model/KeyboardModel` first slice (page state, row assembly, character
commit through a narrow text-edit proxy the service implements);
`EditorState`; `ui/keys` RowView (weight layout, 6 dp paddings/gaps,
visual + transparent gesture layers, ±5.5 dp expanded hit zones,
nearest-key dispatch) and KeyButton (colors, radius 8, hairline bottom
shadow, 22 pt / ×1.2-lowercase typography, icons); page switching;
punctuation-returns-to-letters; action-aware return key
(`performEditorAction`, Search icon, weight 2.3 for labels > 6 chars);
globe key gated by `shouldOfferSwitchingToNextInputMethod()`.

**Dependencies**: none beyond the existing baseline; no new libraries
expected. **Assumptions**: the 250 dp frame, single ComposeView, and
`ImeLifecycleOwner` glue stay untouched. **Constraints**: exact
geometry and strings from the spec §2–§4; keyboard UI strings are Lezgi
only, verbatim; no Material widgets; no interactions beyond plain taps
(bubbles/Shift/repeats are Stage 3). **Risks** (bounded, non-blocking):
Roboto has a `wght` axis but no width axis — typography is tuned by eye
on device against iOS (target: between Regular and Medium, slightly
narrow); monochrome vector icons must be drawn to match the iOS look.

## 6. Development principles

- **Plan first.** Answer "how" questions in text only; write code only
  after the owner explicitly approves («давай» / "do it"). Stages are
  approved one at a time; never implement ahead of the current stage.
- **Scope discipline.** Change only what was requested. Never refactor,
  redesign, or "improve" unrelated code. The port is a port.
- **Ask on ambiguity.** If the spec is ambiguous or conflicts with the
  iOS implementation, stop and ask — never guess silently.
- **Verification.** Verify changes by Gradle build only
  (`./gradlew :app:assembleDebug`). The owner does functional testing
  on real devices. Never launch emulators or UI automation on your own
  initiative; the owner may explicitly grant adb-based evidence
  gathering (as happened in Stage 1).
- **Localization.** Keyboard UI strings are Lezgi only, copied verbatim
  from the spec/iOS. Settings subtitles are localized ru/en. No modal
  dialogs inside the keyboard, ever.
- **Optimization protocol.** For any ranking/quality change: metrics
  baseline → exactly one behavioral change → measure. Never combine
  ranking changes in one commit.
- **Commits.** Small, focused, self-contained, reversible. Commit and
  push only when the owner explicitly asks. The repository stays
  tool-neutral: code, comments, commits, and documentation never
  reference the tooling or assistants used to produce them — the
  project must read as written by its maintainers, nothing else.
- **Never change without explicit owner approval:**
  - the height contract numbers (250 = 36 + 8 + 4×43 + 3×11 + 1);
  - dictionary read-only status and byte-parity across platforms;
  - the learned-store schema, ranking formulas, thresholds, caps,
    decay semantics (cloud-sync compatibility, plan Stage 8);
  - the base-score contract (never numerically mix dictionary scores
    with personal counters — `docs/DECISIONS` context and the iOS
    roadmap);
  - palochka = Cyrillic `ӏ` U+04CF everywhere;
  - preference keys (`set_*`, `layoutVariant`, `recentEmojis`);
  - the event-shaped nature of the learning store.

**Performance goals**: per-keystroke work is synchronous on the main
thread by design (D-007) — queries are tiny; revisit only with profiler
evidence, as its own logged decision. The emoji page must keep the flat
lazy-column structure (memory limits killed nested lazy grids on iOS;
IMEs have tight budgets on Android too).

## 7. Known limitations

- Placeholder keyboard UI only (by design — Stage 1 scope).
- Launcher uses the default system icon (product icon deferred; noted
  in the stage log).
- `versionName` is 1.0.0; the iOS settings panel displays 1.2.0 — the
  Android panel version string is a Stage 7 concern.
- Gradle prints deprecation warnings from AGP/plugins (not from our
  scripts); harmless until a Gradle 9 upgrade.
- No automated tests by deliberate policy — acceptance is S1–S17 on
  device, run by the owner.
- A fresh checkout needs `local.properties` (or `ANDROID_HOME`) —
  standard Android practice, documented in README.

## 8. Recommended workflow for the next developer or agent

**Read first, in order**: this file → `ANDROID_PORT_CONTEXT.md` →
`BEHAVIOR_SCENARIOS.md` → `docs/IMPLEMENTATION_STAGES.md` (current
stage) → `docs/ANDROID_ARCHITECTURE_PLAN.md` → `docs/IOS_PARITY.md` →
`docs/DECISIONS.md`. For implementation detail, read the iOS source —
it is small (~4,300 lines) and is the ground truth.

**Do not change** anything in the never-change list (§6), the docs'
authority order, or the iOS-mirroring component names (D-005).

**Validating changes**: build with `./gradlew :app:assembleDebug`;
keep the working tree clean; update the relevant document in the same
change; report honestly (if something is unverified, say so). When
device evidence is authorized: `adb` install → focus a text field →
check `logcat -b crash`, `ImeTracker` lines, and
`dumpsys input_method` / `dumpsys window windows`; on emulators mind
the hardware-keyboard setting (§2.2).

**Common mistakes to avoid:**
- Trusting the host's text context for the active prefix — it lags
  behind fast typing; the local composed-word buffer is the truth
  (spec §7; bit iOS during development, will bite Android too).
- Inventing Android-flavored behavior where the spec/iOS defines the
  product. Platform conventions apply only where the IME framework
  forces them (log such cases as decisions).
- Adding Material, DI, ORMs, coroutine dispatchers for storage, or new
  modules — all deliberately rejected (D-006/007/010/011).
- Skipping documentation while implementing, or documenting after the
  fact from memory.
- Committing or pushing without an explicit owner request.
- Marking stages or checklists complete without evidence.

## 9. Current repository state

- **Remote**: https://github.com/LekiTech/LezgiKeyboard-Android.git
- **Branch**: `main` (this handoff document lands directly on top of
  the milestone commit)
- **Stage 1 milestone commit**: `be9ca8f` — "Mark Stage 1 complete
  after audit"
- **Latest tag**: `stage-1` (annotated, on `be9ca8f`)
- **Milestone**: Stage 1 of 8 complete, audited, and verified on an
  API 36 emulator; Stage 2 (keyboard layout + typing) is approved
  architecture-wise and awaits the owner's go-ahead to begin.
