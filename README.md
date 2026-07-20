# LezgiKeyboard-Android

Native Android port of the Lezgi keyboard — the same product as the iOS
version, running on a different OS. A user switching between platforms
must never have to relearn how the keyboard behaves.

- Kotlin, `InputMethodService`, Jetpack Compose.
- Bundled 20k-word Lezgi dictionary (identical data on both platforms,
  Cyrillic palochka ӏ U+04CF everywhere).
- On-device learning, no INTERNET permission, no backup — nothing typed
  ever leaves the device.

## Documents

| Document | Role |
|---|---|
| [ANDROID_PORT_CONTEXT.md](ANDROID_PORT_CONTEXT.md) | The behavioral specification (source of truth) |
| [BEHAVIOR_SCENARIOS.md](BEHAVIOR_SCENARIOS.md) | Acceptance suite S1–S17 |
| [docs/ANDROID_ARCHITECTURE_PLAN.md](docs/ANDROID_ARCHITECTURE_PLAN.md) | Architecture: structure, responsibilities, pipelines |
| [docs/IOS_PARITY.md](docs/IOS_PARITY.md) | Undocumented iOS behaviors that must be preserved |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Chronological architectural decision log |
| [docs/IMPLEMENTATION_STAGES.md](docs/IMPLEMENTATION_STAGES.md) | Staged plan and implementation log |

## Building

Requires an Android SDK (set `sdk.dir` in `local.properties` or
`ANDROID_HOME`) and JDK 17+.

```sh
./gradlew :app:assembleDebug   # build
./gradlew :app:installDebug    # install on a connected device
```

Then enable «Лезги чӏал» in the system keyboard list (the app's
onboarding screen has shortcuts). On emulators with a hardware keyboard,
also enable «show on-screen keyboard while hardware keyboard is active»
— otherwise Android suppresses every on-screen IME.

## Status

All 8 stages complete (see
[docs/IMPLEMENTATION_STAGES.md](docs/IMPLEMENTATION_STAGES.md)): the
fixed-height IME, the full three-page layout with typing and every key
interaction, the animated suggestion bar, the complete on-device
intelligence — bundled-dictionary predictions, the personal learned
store with iOS-compatible ranking, and bigram next-word suggestions —
the in-keyboard settings panel with live toggles, instant themes, and
the saved-words dictionary, local quality metrics, the emoji page,
and the eagle sticker pack inserted straight from the keyboard where
the app accepts images.
