# Komorebi

**Komorebi** is an open-source Android command palette, Raycast-compatible extension runner, and system customization toolkit built with Jetpack Compose and Material 3.

<p align="center">
  <a href="https://github.com/aislingheart/komorebi/releases/latest">
    <img alt="Latest Release" src="https://img.shields.io/github/v/release/aislingheart/komorebi?style=for-the-badge&logo=android&logoColor=%23fff&labelColor=%2348C&color=%2348C" />
  </a>
  <a href="https://github.com/aislingheart/komorebi/releases">
    <img alt="GitHub Downloads" src="https://img.shields.io/github/downloads/aislingheart/komorebi/total?style=for-the-badge&logo=github&logoColor=%23fff&labelColor=%23333&color=%232a6" />
  </a>
  <a href="https://github.com/aislingheart/komorebi/issues/new?template=bug_report.yml">
    <img alt="Report a Bug" src="https://img.shields.io/github/issues/aislingheart/komorebi/bug?style=for-the-badge&logo=openbugbounty&logoColor=%23fff&label=Bugs&labelColor=%232a6&color=%232a6" />
  </a>
  <a href="https://github.com/aislingheart/komorebi/issues/new?template=feature_request.yml">
    <img alt="Feature Request" src="https://img.shields.io/github/issues/aislingheart/komorebi/enhancement?style=for-the-badge&logo=apachespark&logoColor=%23fff&label=Feature%20Request&labelColor=%23a26&color=%23a26" />
  </a>
</p>

---

## Highlights

- **Universal Search & Command Palette**: Fast launcher-style search across apps, shortcuts, contacts, files, media, system settings, and installed extensions.
- **Inline Raycast & Vicinae Extensions**: Run JavaScript/TypeScript extensions (`@raycast/api` & `@vicinae/api` runtime) directly inside the search overlay without leaving your workflow, with support for lists, grids, forms, detail views, OAuth, and store browsing.
- **System Tweaks & Automation**:
  - **Dynamic Island / Notification Island** & **Edge / Notification Lighting**
  - **Physical Button Remapping**, **Lockscreen Shortcuts**, & **DIY Automations**
  - **Shut-Up!** (dynamic developer-option/accessibility hiding for sensitive apps)
  - **Quick Settings Tiles**, **Link Actions**, **Charge Optimization**, **Always-On / Display Overlays**, and **System Keyboard**
- **Built-in Auto-Updater**: Checks [aislingheart/komorebi releases](https://github.com/aislingheart/komorebi/releases) automatically for stable and pre-release updates.

---

## Navigation

- [Installation](#installation)
- [Shell Providers (Shizuku & Root)](#shell-providers-shizuku--root)
- [Building from Source](#building-from-source)
- [Localization](#localization)
- [Contributing](#contributing)
- [Credits & Acknowledgments](#credits--acknowledgments)

---

## Installation

Download the latest APK from [GitHub Releases](https://github.com/aislingheart/komorebi/releases/latest).

> **Note on Android Sideloading & Restricted Settings**:
> Komorebi uses sensitive Android capabilities such as Accessibility Service and Notification Listener for system customization features. On newer Android versions, you may need to allow restricted settings in **App Info → ⋮ → Allow restricted settings** (or temporarily adjust Play Protect / Advanced Protection during sideloading). Always verify you are downloading APKs directly from the official [aislingheart/komorebi](https://github.com/aislingheart/komorebi/releases) repository.

---

## Shell Providers (Shizuku & Root)

Komorebi supports both **Shizuku** and **Root** as privileged shell providers for advanced system-level actions:

- **Shizuku**: Works without root using ADB / Wireless Debugging. A maintained fork such as [rushiranpise/Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next) is recommended on newer Android versions.
- **Root**: On rooted devices, Komorebi can execute privileged operations directly via root without requiring Shizuku.

---

## Building from Source

```bash
git clone https://github.com/aislingheart/komorebi.git
cd komorebi
./gradlew :app:assembleDebug
```

Run unit tests and translation checks:

```bash
python3 scripts/validate_strings.py
./gradlew :app:testDebugUnitTest
```

---

## Localization

Translations live under `app/src/main/res/values-*/strings.xml`.

Before submitting translation changes, validate formatting and XML syntax:

```bash
python3 scripts/validate_strings.py
python3 scripts/validate_strings.py --fix
```

---

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) and our [Code of Conduct](CODE_OF_CONDUCT.md) before opening a pull request.

---

## Credits & Acknowledgments

Komorebi is built on the shoulders of awesome open-source projects and ecosystems:

- **[Essentials](https://github.com/sameerasw/essentials)** by Sameera Wijerathna & contributors (MIT) — Upstream foundation providing the Android system toolkit, Jetpack Compose architecture, and core utilities.
- **[Vicinae](https://github.com/vicinaehq/vicinae)** (GPL-3.0) — Inspiration, extension store API compatibility, and `@vicinae/api` / `@raycast/api` runtime patterns.
- **[Raycast Extensions](https://github.com/raycast/extensions)** (MIT) — Extension manifest conventions, UI component model (`List`, `Grid`, `Detail`, `Form`, `ActionPanel`), and developer API design.
- **[TebbeUbben/ChargeQuickTile](https://github.com/TebbeUbben/ChargeQuickTile)** (MIT) — Pixel battery charge optimization tile reference.
- **[chalda-pnuzig/emojis.json](https://github.com/chalda-pnuzig/emojis.json)** — Built-in keyboard emoji dataset.
- **[xav-ie/generate-kaomoji](https://github.com/xav-ie/generate-kaomoji)** — Built-in keyboard kaomoji dataset.
- **Watch eSIM Helper** — Pixel Watch eSIM handoff and activation code parsing reference.
- **Link Shortener API** — Powered by [@Balajitechlabs](https://github.com/Balajitechlabs).

---

## License

This project is open source. See [LICENSE](LICENSE) and the in-app **Credits & Licenses** sheet for individual component and dependency licenses.
