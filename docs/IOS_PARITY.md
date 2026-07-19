# iOS Parity Notes

Behaviors present in the iOS implementation that are **not** (or not fully)
documented in `ANDROID_PORT_CONTEXT.md`, collected from a full review of
the iOS keyboard extension. Every item below is normative for the Android
port: user-visible behavior must match unless a DECISIONS.md entry says
otherwise.

This is a living document. Whenever a new parity detail is discovered
during development, add it here in the matching section — parity knowledge
must never exist only in someone's head or in a diff.

Reference implementation: `lezgikeyboard` (iOS), `LezgiKeyboard/LezgiChalKeyboard/`.
Precedence: `ANDROID_PORT_CONTEXT.md` → `BEHAVIOR_SCENARIOS.md` → the iOS
implementation. When the specification and the implementation conflict,
the **implementation** wins for user-visible behavior (see DECISIONS.md
D-001) and the specification is updated afterwards.

## Keys and gestures

- **Keys commit on touch-up.** The active key is chosen at touch-down
  (nearest key by x, touches inside a key's frame win) and never
  re-targets while the finger slides — no roll-over between keys or rows.
  Long-press timers anchor to that key.
- Each key row is a single gesture surface (visual layer + transparent
  full-row gesture layer). The suggestion bar and the emoji category bar
  follow the same pattern.
- **Press bubble** (character keys only): height 54, min width 44, label
  28, filled with the pressed-key color, drawn as one continuous
  silhouette — radius-8 bubble, S-curved neck exactly the key width
  (curves start 6 above / end 6 below the bubble bottom), flat bottom
  aligned with the key's bottom edge; shadow black 20%, radius 4, y 2.
  The bubble is clamped to stay fully inside the keyboard view (top-row
  bubbles compress). While it shows, the key's own label is hidden.
  Service keys highlight but never show a bubble.
- **Callout bubble**: 44 per option, height 54, 16-tall neck the width of
  the key overlapping the key top by 9; options at 24; selected option
  white on system blue, unselected on pressed-key color; radius 10; shadow
  black 25%, radius 6, y 3; clamped to screen edges with a 6 margin and
  clamped vertically to stay inside the keyboard view. The initially
  selected option is the one under the finger (computed from x); selection
  tracks absolute x across the bubble. Options display case-adjusted
  (Shift → first letter, Caps Lock → all), but the raw lowercase string is
  dispatched through the normal key-handling path, which applies case on
  insert. Option list = base character first + alternates minus
  duplicates of the base.
- **Backspace repeat** logic (0.4 s hold → 0.1 s interval → ×0.85 per
  step → floor 0.03 s) is duplicated verbatim on the emoji page's delete
  zone.
- **Space cursor mode** tracks the finger position while waiting for the
  0.4 s activation so movement starts without a jump. Vertical moves keep
  the column when the neighboring line is visible in the host context;
  otherwise one newline is crossed blindly (up lands at the end of the
  previous line, down at the start of the next). Releasing from cursor
  mode inserts nothing. All key labels are hidden while active.
- **Double space** validates against the host context (must end with a
  single space and have a letter/digit before it) and clears the tap
  timestamp after firing — a third space never chains another period.
- `. ? !` typed on the numbers/symbols pages arm Shift regardless of the
  host's autocapitalization setting (only Caps Lock blocks). Return and
  the automatic context re-evaluation do honor the host setting.
  `allCharacters` and `words` autocapitalization types are supported
  (words = shift after any whitespace).
- Sentence-start detection tolerates trailing whitespace and treats
  whitespace-only context as a sentence start.
- Only `. , ? ! ; :` trigger learning. The remaining separators
  (`"'()[]{}—–-` and whitespace) clear the composition **without**
  learning the word.
- Digraph alternates append to `composedWord` as whole strings; a
  single-character separator insert clears it.
- Any keystroke dismisses the space-bar keyboard-name flash; the flash
  re-arms on every keyboard appearance (1.5 s, 0.25 s fade, space bar
  highlighted as pressed while it shows).
- Shift taps cycle off → once → capsLock → off (deliberate design — not
  the native double-tap caps lock). Icons: outline / filled / caps-lock
  filled.
- No key sounds, no haptics anywhere.
- The globe key simply advances to the next input method.

## Suggestion bar

- The press highlight follows the finger across cells during a drag; the
  accept dispatches to the cell nearest the **release** x. Entering a new
  cell re-arms the 0.5 s long-press delete timer (learned cells only).
- The press capsule and the «Ваъ» confirm pill are filled with the
  **letter-key color** (white / dark-key), not the pressed-key color —
  the implementation is the source of truth here (D-001); the spec's
  color table is outdated on this point.
- Delete-confirm row: text 15 (min scale 0.7) with curly quotes
  “слово”; pills at 14 — «Ваъ» medium on a key-color capsule, «Чӏурун»
  semibold red on a 12% red capsule; horizontal padding 14 / vertical 5;
  row padding 12; spacing 12. Any change to the suggestions dismisses the
  confirm row automatically.
- A word too wide for its cell falls back to a plain, non-morphing label
  (min scale 0.85, then truncation) — the per-glyph machinery only runs
  when the word fits.
- The bar renders the suggestion list when non-empty, else the idle trio.
  The idle words' display form (capitalization) is recomputed on every
  context change even though the raw words hold still.
- The «…» guillemets around the literal render outside the per-glyph
  run, so glyph identity and the morph are unaffected.

## Suggestion engine

- Dictionary candidates include the typed word itself when it is a
  dictionary word (exact match sorts first under `ORDER BY LENGTH`) —
  this is why known words show undecorated with themselves as the first
  candidate.
- The dictionary prefix `LIKE` is **unescaped**; the learned-store `LIKE`
  escapes `\ % _`. Both preserved as-is.
- The learned visibility gate uses `LENGTH(word) >= 2` in characters (not
  Lezgi letters) — identically in prefix suggestions, `isRecognized`,
  next-word, and the saved-words query.
- The next-word state counts as "not idle": transitioning next-word →
  idle re-rolls the random trio. The idle flag starts true on keyboard
  appearance (the appearance refresh rolls its own trio).
- `wordHadPredictions` (metrics) is set only on the prefix path when real
  predictive candidates render — the quoted literal alone never sets it —
  and is cleared when the prefix empties, so abandoned compositions leak
  nothing into the next word's metrics.
- `pendingAcceptedWord` (for `m_corrected`) is set only when auto-space
  was inserted, and cleared by: the cursor settling on a different last
  word, any manual completion, or the resume-into-word backspace (which
  bumps the metric when the resumed word matches).
- Accepting a suggestion consumes Shift-once; the inserted text is the
  displayed (capitalized) form; storage stays lowercase; the replaced
  prefix is deleted as `max(context prefix, composedWord)` single
  backward deletions, then word (+ space) inserted.
- `previousWord`: cut the context at the last `. ! ?` or newline,
  tokenize with the same separator set as `wordPrefix`; trailing
  separator → last token, otherwise second-to-last; nil when the host
  truncates the context short (the bigram is simply skipped).
- Host-clear send detection runs **first** inside the resync (before
  `composedWord` is realigned) and requires a completely empty document;
  it counts as a manual completion for metrics.
- Turning word suggestions off clears the bar synchronously; re-enabling
  repopulates on the next update event (no immediate refresh call).
- Storage failures degrade silently (no crash, no dialog). With no
  dictionary handle every typed word counts as unknown (quoted literal).

## Learning store

- Digraph tails are **derived from the callout table** — the last
  character of every two-character alternate (`ӏ ь ъ`) — and the Lezgi
  letter count ignores every occurrence of a tail character, wherever it
  appears.
- `isLearnable` rejects: length > 64, fewer than 2 Lezgi letters, any
  digit, any of `@ / .` anywhere.
- A bigram is recorded only when the previous word also passes
  `isLearnable`.
- `meta` keys in use: `schema_version`, `total_events`,
  `filters_version` (bumping it purges records that no longer pass the
  current filters — ported), `palochka_fixed` (iOS-only legacy migration
  — **not** ported; Android starts clean).
- Decay: at ≥ 2000 events halve `count` and `picked` (integer division),
  delete rows at zero, reset the event counter, prune, `VACUUM`. Prune
  check additionally at every 200-event multiple. Prune order: words by
  `count + 3·picked ASC, last_used ASC`; bigrams by `count ASC,
  last_used ASC`.
- Deleting a word removes the word row **and every bigram in which it
  appears on either side**. The saved-words query limit (5000) equals the
  row cap, so the settings list is complete.
- `nextWords` orders by `count × recency-boost DESC, last_used DESC` and
  gates `count >= 2` and `LENGTH(word) >= 2`.
- Prefix ranking tiebreak: `last_used DESC`.

## Settings panel and quick layout menu

- Exact content: home caption «ЛЕЗГИ КЛАВИАТУРА» 11 bold, kerning 1.4;
  title «Параметрар» 24 bold; section labels combine Lezgi with a
  localized subtitle («Теклифар — suggestions», «Клавишар»,
  «Чирун — learning», «Алава гьарфар (ӏ, кь, къ…) — long press»,
  «Тема — theme», «Кӏеви лишандин («ъ») чка»); layout radio subtitles
  «Вини жергеда 11 клавиша» / ««ъ» — «х»-дин къвалаг»; home rows show
  live values (variant, theme, saved-word count, version); about page:
  gradient «Л» tile 56×56 (radius 14), «Лезги клавиатура», «Версия
  1.2.0 · LekiTech»; dictionary counter 36 bold over «чирнавай гафар»;
  empty state «Гьеле гафар авач»; per-row × delete buttons (34×34 hit
  area, rows min height 42); delete-all sheet dims 40% black over a
  radius-16 card with «Вири чирнавай гафар чӏурдани?», «Чӏурун» (red)
  above «Ваъ» (accent).
- Nav rows: icon chip 28×28 (accent glyph on accent-tint, radius 8),
  title 15, value 14 secondary, chevron.
- The localized subtitle catalog is 22 strings (ru + en, en fallback) —
  ported verbatim from the iOS string catalog.
- The panel covers the full fixed keyboard height and slides up with
  easeOut 0.28; the drag capsule (36×5) is decorative — no drag-to-close.
  The saved-words list refreshes on panel open and after deletes, not on
  page navigation.
- Version string (1.2.0) appears in two places: home nav row and about.
- Quick layout menu: card 196 × (2×52), radius 12, shadow black 28%
  radius 10 y 5, 8 above the gear key, clamped to 4-margin screen edges;
  drag hit-testing insets the card by −8 horizontal / −4 vertical; rows:
  title 15 medium + subtitle 11 («Ъ — арадин къвалаг» / «Сад лагьай
  вариант», «Ъ — вини жергеда» / «Кьвед лагьай вариант»); the current
  variant is always the top row and marked by accent title tint only;
  releasing outside the card changes nothing. The gear long-press delay
  is fixed at 0.35 s (not the callout-delay setting).
- The layout variant persists under its own storage key
  (`layoutVariant`), separate from the settings struct.

## Emoji page

- Grid: columns of 5, spacing 2 both axes, cells 38×33, glyphs 26, 10
  trailing gap after each category. The page is a flat lazy row of small
  uniform columns — nested lazy grids defeat laziness and blew the iOS
  extension's memory limit; the same structure is kept on Android.
- Category bar: height 36, «АБВ» zone 44 wide (15 medium), delete zone
  44 wide (icon 17), category icons 15 with a 30 selection circle; side
  zones are 48 including the 4 edge padding; dispatch by x across the
  whole bar. Tapping the **current** category re-jumps to its start.
- Section title strip: 13 semibold, secondary color, follows scroll;
  initial section = recents when present, else the first category.
- Recents: section id −1, limit 24, title «Эхиримжибур», move-to-front
  dedup, persisted in preferences (`recentEmojis`) — not in the learning
  database.
- Category titles are Lezgi: «Чинар ва инсанар», «Тӏебиат», «Тӏуьн»,
  «Къугъунар», «Сиягьат», «Затӏар», «Символар», «Пайдахар». The catalog
  is generated (fully-qualified, no skin tones, capped at Emoji 15.1).
- The emoji page replaces the entire keyboard area — no suggestion bar.
- Emoji insert flashes the cell with a radius-6 pressed-key-color
  rectangle.

## Host integration

- Event pipeline order on every host text/selection change: refresh
  return-key type → resync (host-clear learn first, then `composedWord`
  and `lastCompletedWord`) → re-evaluate shift from context → update
  suggestions. After a backspace key: shift re-evaluation, then
  suggestions. After the keyboard's own suggestion accept: suggestions
  refresh synchronously in the tap handler.
- The globe requirement and return-key type are refreshed on layout
  passes / field changes, not only on appearance.
- Nothing is learned when the keyboard disappears — a word left in the
  field is a draft, not a commit.
- iOS ships `RequestsOpenAccess = false` (OS-enforced no-network). The
  Android structural equivalent: the app requests **no INTERNET
  permission** and disables backup, so learned data cannot leave the
  device (see DECISIONS.md D-008).
- Primary language: `lez` (IME subtype language tag on Android).

## Vestigial iOS code — do not port

- `LezgiLayout.label` values for space («Ара») and return («Ракъун»)
  and `LezgiLayout.fontSize` case values 13 for space/return are dead:
  the rendering path overrides them (space renders «ЛЕЗГ» / the name
  flash; return renders its action label at 14 or an icon at 18).
