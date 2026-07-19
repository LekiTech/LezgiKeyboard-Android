# Android Port Context — Lezgi Keyboard

A self-contained specification for building an **identical** Lezgi keyboard
on Android. It captures everything the iOS implementation
(`LezgiKeyboard/LezgiChalKeyboard/`) does as of 2026-07-19, at the level of
behavior, exact metrics, and algorithms — not iOS API details. Read together
with `BEHAVIOR_SCENARIOS.md` (acceptance scenarios S1–S17),
`LOCAL_SUGGESTIONS_ROADMAP.md` (learning stages, base-score contract,
Stage 8 cloud architecture), and `CODEBASE_OVERVIEW.md`.

Units: iOS points map 1:1 to Android dp. Colors are sRGB.

## 1. Product identity and design philosophy

- The **keys** must feel like the native platform keyboard (on iOS: Apple's;
  on Android: match this spec's geometry/behavior, styled natively).
- The **suggestion bar** is deliberately NOT platform-imitating — it is
  best-in-class (reference: Yandex Keyboard): content-sized cells, no
  separators, per-glyph animations, quoted unknown words.
- Guiding principle: *effortless, not clever*. Every feature must reduce
  typing friction; nothing may add visual noise or require thought.
- **All keyboard UI text is Lezgi only** (exact strings below — reuse them
  verbatim). Explanatory subtitles in the settings panel are localized
  (ru/en, en fallback). No modal dialogs inside the keyboard — all
  confirmations are inline.
- The Lezgi palochka is the Cyrillic **ӏ U+04CF** everywhere (typed text,
  dictionary, learned store). Never the Latin I or the digit 1.

## 2. Pages and layout

Four pages: letters, numbers («123»), symbols («#+=»), emoji.

### Letter rows (variant `classic`)

```
Row 1: й ц у к е н г ш ӏ з х            (11 keys)
Row 2: ф ы в а п р о л д ж э            (11 keys)
Row 3: ⇧ я ч с м и т ь б ю ⌫           (shift + 9 + backspace)
Row 4: 123 ⚙ 😀 [🌐] ␣ ъ ⏎
```

Variant `topRow` moves «ъ» from row 4 to the end of row 1 (12th key).
The variant is user-selectable (settings panel + long-press on the gear).
The globe key appears only when the OS requires an input switcher.

### Numbers page

```
Row 1: 1 2 3 4 5 6 7 8 9 0
Row 2: - / : ; ( ) ₽ & @ "
Row 3: #+= . , ? ! ' ⌫
Row 4: АБВ [🌐] ␣ ⏎
```

### Symbols page

```
Row 1: [ ] { } # % ^ * + =
Row 2: _ \ | ~ < > € £ ¥ •
Row 3: 123 . , ? ! ' ⌫
Row 4: АБВ [🌐] ␣ ⏎
```

Typing `. , ? ! '` on the numbers/symbols pages returns to the letters
page (like native); `. ? !` also arm Shift for the next letter (unless
Caps Lock).

### Key width weights

Row width is divided by weight (unit = (rowWidth − 6·(n−1) spacing) / Σweights):

| Key | Weight |
|---|---|
| any character | 1.0 |
| shift, backspace | 1.0 |
| 123 / #+= / АБВ / gear / emoji / globe | 1.2 (the bottom-left cluster must be exactly equal-sized) |
| return | 1.8; **2.3 when its label is longer than 6 characters** |
| space | 4.5 |

The row normalizes weights, so a widened return key automatically shrinks
the space bar — no other layout change.

## 3. Vertical geometry (the height contract)

Total fixed height **250**:

```
36  suggestion bar
 8  gap below the bar
4×43 key rows (key height 43)
3×11 inter-row gaps
 1  slack
```

- Horizontal: rows have 6 side padding; 6 between keys.
- Touch zones: each key's tap zone extends vertically by half the row gap
  (5.5) into the gaps above/below (not for the outermost rows), so the
  whole key area is tappable with no dead bands. Horizontal gap taps go to
  the nearest key by midX.
- The keyboard view must own its height deterministically (Android:
  a fixed-height input view; no host-driven resizing).

## 4. Key visuals and typography

### Colors

| Role | Light | Dark |
|---|---|---|
| Letter key | #FFFFFF | rgb(0.227, 0.227, 0.235) |
| Pressed key / bar press capsule | rgb(0.82, 0.84, 0.87) | rgb(0.36, 0.36, 0.37) |
| Keyboard background (forced themes only) | rgb(0.820, 0.827, 0.851) ≈ #D1D3D9 | rgb(0.169,·,·) ≈ #2B2B2B |

Key shape: corner radius 8, hairline bottom shadow (black 30%, offset y=1,
no blur). System theme uses the OS keyboard background; forced Light/Dark
themes paint the background explicitly (see §10 Theme).

### Character key typography (matched by eye to iOS native Russian)

- Uppercase and caseless glyphs: **22**; lowercase: **26.4** (22 × 1.2 —
  lowercase x-height optically matches native this way). Decided per
  rendered label: if it contains lowercase letters → bump.
- Variable font axes: weight **0.19** on the SF scale where regular = 0.0
  and medium = 0.23 (i.e. just under medium); width **−0.1** (slightly
  condensed). On Android: closest match with the chosen font — target
  "between Regular and Medium, slightly narrow, letters read tall".
- Service keys: icons ~18–20; «123»/«АБВ»/«#+=» text 18; return text 14
  (single line, min scale 0.8); space corner label 10.

### Space bar

- Bottom-right corner label «ЛЕЗГ», 10, gray, padding 6/4 — hidden by a
  setting and while the keyboard name shows.
- On keyboard appearance the space bar flashes the keyboard name
  «Лезги чӏал» (16, centered, key highlighted as pressed) for **1.5 s**,
  fading 0.25 s. First keystroke dismisses it early.

### Return key

- Label by editor action, in Lezgi: go «Фин», send «Ракъурун», done
  «Хьанва», next «Къведайди», continue «Давамрун», join «ЭкечIун», route
  «Рехъ», emergencyCall «Хаталувилин зенг»; **search shows a
  magnifying-glass icon** (monochrome, same style as other key icons — not
  an emoji); everything else shows the return-arrow icon.
- Labels > 6 chars widen the key (weight 2.3). Single line always.

## 5. Key interaction

- **Press bubble**: while a character key is held, a bubble above the key
  shows the character (native style); the key's own label is hidden. No
  bubble for service keys.
- **Long-press callouts** (alternates; delay user-set 0.2 / 0.3 / 0.45 s,
  default 0.3): a horizontal bubble of options, 44 per option; the base
  character is always the first option; finger slides to select, release
  inserts. Alternates are inserted as whole strings (digraphs):

```
ц → цӏ        у → уь        к → кӏ кь къ    е → ё
г → гь гъ     ш → щ         х → хь хъ       п → пӏ
ч → чӏ        т → тӏ
```

- **Case for digraphs**: Shift capitalizes only the first letter
  («къ» → «Къ»); Caps Lock uppercases all («КЪ»).
- **Shift**: tap cycles off → once → capsLock → off. "Once" consumes
  itself after one letter or an accepted suggestion. Auto-capitalization:
  sentence starts (empty field, after `. ! ?` + space, after newline)
  arm Shift automatically, honoring the host's autocapitalization setting;
  backspace re-evaluates the context.
- **Backspace repeat**: hold 0.4 s → repeats at 0.1 s, accelerating ×0.85
  per step, floor 0.03 s.
- **Space cursor mode** (user-disableable): hold space 0.4 s → keyboard
  enters cursor mode (all key labels hidden), horizontal drag moves the
  caret 1 character per 8 pt, vertical 1 line per 30 pt.
- **Double-space → «. »** (user-disableable): second space within 0.35 s,
  only when the character before the first space is a letter/digit;
  replaces the space, arms Shift, ends the sentence.
- **Gear key**: tap opens the in-keyboard settings panel; long-press
  0.35 s opens a quick two-row menu above the key for the «ъ» layout
  variant (current variant always the top row; release outside = no
  change).
- **Return**: newline, ends the sentence (next-word context cleared),
  arms Shift per autocapitalization.

## 6. Suggestion bar (best-in-class — do not copy platform defaults)

### Layout

- Height 36. Text **18** regular. No separators; cells are content-sized
  (text + 12 horizontal padding), spread evenly with flexible gaps
  (min 6); empty slots are dropped entirely (1 word → centered, 2 →
  balanced, 3 → spread).
- Press highlight: a capsule hugging the cell (radius 10, 4 vertical
  inset), colored like a pressed key. It must always fully contain the
  text.
- Overlong words: shrink to 0.85 scale first, then middle/end-truncate.
- Tap dispatch: the whole bar is one gesture surface; a touch goes to the
  cell with the nearest center X (boundaries = midpoints between cells) —
  the entire bar stays tappable.
- Long-press 0.5 s on a **learned** suggestion (only) → the bar is
  replaced inline by a confirmation row: «“слово” чӏурдани?» with pills
  «Ваъ» (no) and «Чӏурун» (delete, red). Deletes the learned record only.

### Animations (exact parameters)

1. **Prefix morph** (word extended/shortened while typing — old and new
   are non-empty and one is a prefix of the other): per-character
   rendering with positional identity; retained letters slide to their new
   centered positions, the added/removed letter fades; easeOut **0.2 s**.
   Cell width animates in the same transaction; the rest of the layout
   never animates on its own.
2. **New word appearance** (any other change): glyphs take final layout
   instantly (no reflow animation), then settle left→right — each glyph
   starts at opacity 0, scale 1.15, offset y +1.5 and eases to normal,
   easeOut **0.2 s**, staggered **22 ms per letter index**.
3. Completely different candidate sets snap with no whole-bar sliding.

### The quoted literal (unknown-word candidate)

- Condition: the composed word is absent from the bundled dictionary
  (exact, lowercased) AND not a *visible* learned word (see §8 threshold).
- Then the bar's **first cell** shows the word exactly as typed, wrapped
  in **«…»** (guillemets — presentation only), followed by up to 2
  predictive matches.
- The stored value is the raw word: tapping inserts it (+ trailing space
  per setting) and learns it with picked weight. The quotes never enter
  any text, lookup, or learning. Tapping the literal is NOT an acceptance
  for metrics (§11).

## 7. Suggestion engine

Local composition buffer `composedWord` tracks the word being typed
(host context lags behind fast typing — never trust it for the active
prefix). Cleared by separators/space/return/accepted suggestion; resynced
from the host context whenever the host confirms state.

### Three bar states

1. **Prefix path** (composedWord non-empty): learned candidates first,
   then dictionary candidates, deduplicated case-insensitively, top 3;
   plus the quoted literal rule (§6).
2. **Next-word path** (empty prefix, a completed word precedes): bigram
   continuations of the last completed word, pairs seen ≥ 2 times, up to
   3. Sentence boundaries (`. ! ?`, newline) clear this context.
3. **Idle** (nothing else): 3 random dictionary words. Re-rolled ONLY on
   the transition into idle (keyboard appearance, or erasing the typed
   prefix) — never continuously while idle.

### Ranking

- Learned candidates:
  `score = (count + 3·picked) × (recent? ×2 : ×1) × (1 + min(bigramCount, 4))`,
  tiebreak by last_used desc. "Recent" = used within 14 days. Visibility
  gate: `count + picked ≥ minVisibleUses` (learning-speed setting 1/3/5)
  and ≥ 2 characters.
- Next-word: `count × (recent? ×2 : ×1)`, gate count ≥ 2.
- Dictionary candidates: `LIKE prefix% ORDER BY LENGTH(word) LIMIT 3`
  (no base ranking data yet — see the **base-score contract** in the
  roadmap: the dictionary will eventually carry an opaque read-only
  `baseScore`; the engine must never mix it numerically with personal
  counters; learned candidates always rank above dictionary candidates
  as an ordering rule).
- Display capitalization: a typed prefix dictates case (capitalized
  prefix → capitalized suggestions, all-caps prefix / Caps Lock →
  uppercase); with no prefix, sentence context decides (sentence start →
  capitalized, else as stored).

### Editing behaviors

- **Backspace over the trailing space of a completed word** (exactly: no
  active composition, context ends with one space, a word character
  before it) → the whole word becomes the active composition again and
  the bar shows suggestions for the full word. All other deletions behave
  normally (remove one char from the prefix).
- **Accepting a suggestion**: replaces max(context-prefix length,
  composedWord length) characters with the word + trailing space
  (auto-space setting on; off → word stays the active composition).
  Chains directly into next-word suggestions.

## 8. Learning store (`learned.sqlite` equivalent)

SQLite in the keyboard's own sandbox. WAL. Schema:

```sql
meta(key TEXT PRIMARY KEY, value INTEGER NOT NULL);
user_word(word TEXT PRIMARY KEY, count INTEGER, picked INTEGER, last_used INTEGER);
user_bigram(prev TEXT, word TEXT, count INTEGER, last_used INTEGER, PRIMARY KEY(prev, word));
```

- Words stored lowercased (palochka ӏ preserved). Learn on completion:
  space / `. , ? ! ; :` / return / suggestion accepted (picked=1 instead
  of count=1) / **host-clear send detection**: if the host clears the
  document while a word was being composed (message sent without a
  trailing space), the composed word is learned. Bigram (prev, word)
  learned alongside.
- **Learnability filters**: ≥ 2 Lezgi letters (digraphs like «къ», «цӏ»
  count as one letter), max length 64, no emails/URLs/tokens with digits.
- **Caps and decay**: max 5000 words / 10000 bigrams (lowest-ranked
  pruned, checked every 200 events); every 2000 learn events all counters
  halve (integer division) so stale habits fade.
- **Visibility threshold**: a word appears in suggestions only after
  `count + picked ≥ minVisibleUses` (1 fast / 3 normal / 5 conservative).
- **Reset**: wipes user_word + user_bigram + event counter; bundled
  dictionary untouched; metrics counters (§11) survive.
- **Saved-words page shows** only: past the visibility threshold AND
  absent from the bundled dictionary. Counter derives from the same
  filtered list (can never disagree). Per-word delete removes learned
  data only.
- **Architectural constraint (Stage 8)**: keep the store event-shaped
  (word+counters, bigram+counter, picked/typed distinction, no sentences)
  — it must be replayable as upload events for the future cloud sync and
  personal/global language-model split. Never write learned data into the
  bundled dictionary.

## 9. Bundled dictionary

`lezgi_words.sqlite` — single table `words(word TEXT PRIMARY KEY)`,
**20,356** lowercase Lezgi words with palochka ӏ. Read-only, reusable on
Android as-is. Exact-membership lookups use plain lowercased equality.
Future: an opaque `baseScore` column (see roadmap contract) — design the
query layer so `ORDER BY baseScore DESC, LENGTH(word)` can slot in without
engine changes.

## 10. Settings (all persist in the keyboard's own storage)

Defaults reproduce pre-settings behavior. iOS keys given for parity:

| Setting | Default | Key |
|---|---|---|
| Word suggestions master (off ⇒ bar fully empty; learning continues) | on | `set_wordSuggestions` |
| Next-word suggestions | on | `set_nextWordSuggestions` |
| Auto-space after accepting | on | `set_autoSpaceAfterSuggestion` |
| Double-space period | on | `set_doubleSpacePeriod` |
| Space cursor mode | on | `set_spaceCursor` |
| «ЛЕЗГ» space label | on | `set_spaceLabel` |
| Learning speed (fast/normal/conservative = 1/3/5) | normal | `set_learnSpeed` |
| Callout delay (0.2/0.3/0.45 s) | normal | `set_calloutDelay` |
| Theme (system/light/dark) | system | `set_theme` |

**Settings panel** lives inside the keyboard (gear key), a slide-up page
stack with header (back «Кьулухъ» / close «Клавиатура»). Pages: home
(«Параметрар», caption «ЛЕЗГИ КЛАВИАТУРА»), Раскладка (variant: «Арадин
къвалаг» = next to space / «Вини жергеда» = top row), Кхьин (input
toggles; sections «Теклифар», «Чирун» with «Фад/Адетдин/Яваш», «Алава
гьарфар (ӏ, кь, къ…)» with «ял» rows), Тема («Системадин / Экуь / Мичӏи»),
Гафарган (saved words: count, «чирнавай гафар», empty state «Гьеле гафар
авач», per-row ×, «Вири чирнавай гафар чӏурун» with inline confirm
sheet), Клавиатурадикай (about). Subtitles localized ru/en.

Panel palette (light / dark): background #F4F4F8 / #151517, row cards
white / #1F1F24 (corner radius 14), separators #EAEAF1 / #2C2C33, accent
(links, toggles, selected radio) #5B57E0 / #8B88FF. Header 40 tall with a
36×5 drag capsule above it; row min height 46.

**Theme** applies instantly on tap without closing the panel — the whole
keyboard re-resolves colors live. Forced themes must also paint the
keyboard background (§4 colors) because the OS backdrop follows the
system, not the app.

## 11. Local quality metrics (never transmitted)

Five counters in `meta` (survive learned-data reset):

- `m_opportunities` — completed words that had ≥ 1 predictive candidate
  (the quoted literal alone does not count) visible while composing. One
  per completed word, not per refresh.
- `m_accepted` — predictive suggestions accepted (literal taps excluded).
- `m_typed_manually` — all manual completions (terminator, host-clear
  send, literal tap).
- `m_ignored` — manual completions that had predictive candidates.
- `m_corrected` — accepted word edited again (resume-into-word) before
  any other word completed. Event-based, no timeout.

Acceptance rate = accepted / (accepted + ignored). Exposed via a
debug-only log line at keyboard startup. Purpose: baseline → one ranking
change at a time → compare.

## 12. Emoji page (brief)

Fullscreen page replacing the key area: horizontally scrolling grid of
5-emoji columns grouped in sections (generated catalog + «recents»
section, id −1), section jump bar at the bottom with letters-return
(«АБВ») and backspace zones, press flash like a key, 0.4 s hold-to-repeat
on its backspace. Inserting an emoji records it into recents.

## 13. Android mapping hints

- Keyboard = `InputMethodService`; text ops via `InputConnection`
  (`commitText`, `deleteSurroundingText`, `getTextBeforeCursor` — the
  analog of the iOS document proxy, with the same lag caveats: keep the
  local `composedWord`).
- Return-key action from `EditorInfo.imeOptions` (IME_ACTION_SEARCH →
  icon, GO/SEND/DONE/NEXT → Lezgi labels).
- Input switcher key ↔ `shouldOfferSwitchingToNextInputMethod()`.
- Settings storage ↔ `SharedPreferences`; theme ↔ follow `uiMode` for
  system, force overlays for Экуь/Мичӏи.
- Both SQLite files can be shipped/created identically; keep schemas and
  all formulas byte-compatible so a future account sync (Stage 8) serves
  both platforms from one personal model.
- Secure/password fields: the OS hides third-party keyboards on iOS;
  on Android respect `TYPE_TEXT_VARIATION_PASSWORD` — never learn there.

## 14. Acceptance

`BEHAVIOR_SCENARIOS.md` (S1–S17) is the acceptance suite: the Android
build should pass every scenario verbatim. When behavior diverges
intentionally, change the scenario first, on both platforms.
