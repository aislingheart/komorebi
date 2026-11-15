# Contributing to Komorebi

Thanks for wanting to help! Please read this guide before writing any code.

## Getting Started

1. Check existing [issues](https://github.com/aislingheart/komorebi/issues) to see if your bug report or feature idea is already being discussed.
2. For major features or architectural changes, open a [feature request](https://github.com/aislingheart/komorebi/issues/new?template=feature_request.yml) first to align on design and scope.
3. For bug fixes, translations, and documentation updates, feel free to open a pull request directly.

## Scope & Quality Guidelines

- **Keep changes focused**: One feature or fix per pull request when possible.
- **Follow project conventions**: Review the [Development Guide](docs/DEVELOPMENT_GUIDE.md) before modifying shared services or UI components.
- **Verify builds and tests**: Ensure `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` pass before submitting.
- **Validate strings**: If you edit translations or string resources, run `python3 scripts/validate_strings.py`.

## Environment Setup

1. Latest stable [Android Studio](https://developer.android.com/studio) and JDK 17 or higher.
2. `git clone https://github.com/aislingheart/komorebi.git`
3. Many privileged features require [Shizuku](https://shizuku.rikka.app/) or Root for testing.

## Discussions & Ideas

For questions or loose ideas that aren't a concrete issue yet, open a thread in [Discussions](https://github.com/aislingheart/komorebi/discussions).
