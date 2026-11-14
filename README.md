# OneStar · 拾光

**English** · [简体中文](README.zh-CN.md)

![Android](https://img.shields.io/badge/Android-5.0%2B%20(API%2021)-3DDC84?logo=android&logoColor=white)
![targetSdk](https://img.shields.io/badge/targetSdk-37-3DDC84)

> The `targetSdk 37` badge reflects the **value actually built here** (`AndroidManifest.xml` plus `TARGET_SDK` in `build.sh`). Play has required 36 since 2026-08-31; this project ships through GitHub Releases and sideloading, and has tracked Android 17 (API 37) since 0.12.2, above that bar. Every bump of `TARGET_SDK` must update the README badge, the CI platform packages and the regression images in step, and re-run the 16 KB alignment and release gates.
![Gradle](https://img.shields.io/badge/Gradle-not%20required-1f6feb)
![Build](https://img.shields.io/badge/build-aapt2%20%2B%20javac%20%2B%20d8%20%2B%20apksigner-8957e5)
![Languages](https://img.shields.io/badge/i18n-15%20languages-f2cc60)
![License](https://img.shields.io/badge/license-MIT-2ea44f)

A native Android app built **with no Gradle and no Android Gradle Plugin**: a home-screen
widget, a Canvas-drawn starfield UI and a hand-maintained JNI layer, with the build chain
strung together by hand from `aapt2 / javac / d8 / apksigner`. Every line compiles to an APK
straight from the command line.

> The point of this repository is not "a journaling app". It is **a reproducible experiment in
> building Android without Gradle**. If you have ever wondered what AGP actually does for you,
> here is an answer that runs.

![Star map panel](docs/screenshots/star-map-panel.png)

| Star map overview | Long-press star note | App icon |
| --- | --- | --- |
| ![Star map](docs/screenshots/star-map.png) | ![Star note](docs/screenshots/long-press.png) | ![Icon](docs/screenshots/icon-1024.png) |

---

## Contents

- [1. What this project is](#1-what-this-project-is)
- [2. Build chain](#2-build-chain)
- [3. Quick start](#3-quick-start)
- [4. Repository layout](#4-repository-layout)
- [5. In depth: three things worth their own section](#5-in-depth-three-things-worth-their-own-section)
- [6. CI](#6-ci)
- [7. Versioning and release](#7-versioning-and-release)
- [8. Data and privacy](#8-data-and-privacy)
- [9. FAQ](#9-faq)
- [10. Known limits](#10-known-limits)
- [11. Roadmap](#11-roadmap)
- [12. Documentation index](#12-documentation-index)
- [12.1 Cross-platform development and release rules](#121-cross-platform-development-and-release-rules)
- [13. Contributing](#13-contributing)
- [14. License and credits](#14-license-and-credits)

## 1. What this project is

OneStar (拾光, "picking up light") is a lightweight daily-star journal: one mood entry per day,
rendered frame by frame on a Canvas, accumulating into a star map. It ships a home-screen widget,
a mood curve, a year-in-review poster, a melodic chime, and a 5-second undo for deletions.

Technically it **deliberately avoids the standard modern Android approach**, in order to test
whether a more manual route still holds up:

- No Gradle project files, no `build.gradle`, no `settings.gradle`
- No AndroidX, no Material Components, no third-party SDK
- Every screen is drawn by hand (`Canvas` plus hand-computed coordinates), no XML layout inflation
- All strings and resources are hand-written; `aapt2` processes `res/` directly
- The native layer is a thin JNI shim, compiled with `zig cc` (no NDK installed)

### 1.1 Features

| Capability | Description | Main implementation |
| --- | --- | --- |
| One star a day | One mood entry per day, accumulating into a star map; back-filling and edits supported | `StarFieldView` / `DaysStore` |
| Hand-drawn sky | The entire UI is drawn frame by frame on Canvas, including orbits, twinkle and particles | `StarFieldView` (3000+ lines) |
| Long-press star note | Long-press a star to read what you wrote that day; editable and deletable | `StarFieldView` / `TextDraw` |
| Undo delete | Deletions can be undone within 5 seconds; the snapshot encoding stays version-compatible | `DaysCodec` / `DayEdit` |
| Mood curve | Samples mood values over time and draws them as a curve | `MoodCurve` |
| Year poster | Exports a year of entries as one shareable long image | `PosterRenderer` |
| Home-screen widget | An AppWidget showing today and cumulative star counts, refreshed with the system language | `StarWidget` / `WidgetState` |
| Melodic chime | Plays a short melody when a star lights up; the sequence and tempo are testable | `ChimePlayer` / `Melody` |
| 15 languages | Simplified and Traditional Chinese, English, Japanese, Korean, French, German, Spanish (Spain and Latin America), Arabic, Thai, Portuguese (Brazil and Europe), Tibetan, Uyghur | `I18n` / `TextDraw` (RTL reordering) |
| Basic accessibility | The night sky is readable and clickable as a whole; the top buttons expose a Button role and a 48dp touch target | `StarFieldView` (since 0.12.2) |

### 1.2 Why Gradle is not required

AGP is not part of the Android runtime. It mainly does four things: compile resources into binary
form, generate the `R` class, turn classes into dex, and pack everything into an APK with signing
and alignment. Those four map onto `aapt2`, `aapt2 link --java`, `d8` and
`zipalign / apksigner` — all command-line tools shipped with the SDK.

| Normally done by AGP | What this project does |
| --- | --- |
| Resource compilation and merging | `aapt2 compile` → `aapt2 link`; `res/` is hand-written, no `values-*` variants |
| Generating `R.java` | `aapt2 link --java gen/`, then `javac` alongside `java/` |
| Java compilation and desugaring | `javac -source 8 -target 8 --release 8`; no syntax that needs desugaring |
| dex conversion | `d8 --min-api 21 --release` |
| Packaging and compression | Hand-written `zip` entries, aligned by `tools/zip16k.py` |
| Signing | `apksigner sign`, with a pinned keystore (see 5.3) |
| Variant and dependency management | Not needed: no third-party dependencies, no flavours, ABIs controlled by `ABIS` |
| Incremental builds | None; a full build takes tens of seconds, in exchange for a 200-line script you can read and change |

The cost is equally explicit: no dependency resolution, no annotation processing, no resource
shrinking, no variant matrix, and no one-click run from an IDE. This repository exists to put
both the cost and the benefit on the table.

## 2. Build chain

```text
res/  ──aapt2 compile──►  res.zip
                             │
AndroidManifest.xml ──aapt2 link──►  unsigned APK (with R.java + assets/)
                             │
java/ + gen/ ──javac(source 8)──►  classes/
                             │
classes/ ──d8 --min-api 21──►  classes.dex ──►  into the APK
                             │
jni/src/*.c ──zig cc (aarch64-linux-musl)──►  lib/arm64-v8a/*.so ──►  into the APK
                             │
tools/zip16k.py ──►  16 KB page-aligned repackaging (.so stored uncompressed at 16384 alignment)
                             │
apksigner sign ──►  onestar-<version>-signed.apk
```

The script behind it is `build.sh` in the repository root, roughly 200 lines of Bash. Before
building it runs two version sanity checks: `versionCode` must not be lower than the highest
value seen historically, and the version in `assets/COPYRIGHT.txt` must match
`AndroidManifest.xml` (set `SKIP_VERSION_CHECK=1` to skip deliberately).

## 3. Quick start

### 3.1 Requirements

| Dependency | Version | Notes |
| --- | --- | --- |
| JDK | 17+ | Only `javac` / `keytool` are used |
| Android SDK build-tools | 37.0.0+ | Provides `aapt2` / `d8` / `apksigner`; 34 can still build, but the 16 KB alignment check and targetSdk 36 need 35.0.0+ (this project also has `tools/zip16k.py` as a fallback) |
| Android SDK platform | android-37.0 | Provides `android.jar` |
| Python | 3.8+ | Only for `tools/zip16k.py` |
| zig | 0.11+ | Optional, compiles `jni/src/*.c`; without it the native library is skipped |

### 3.2 Build

```bash
# Minimal: build an installable APK with the default debug signature
ANDROID_HOME=$HOME/Android/Sdk ./build.sh

# Compile JNI and sign with your own keystore
ANDROID_HOME=$HOME/Android/Sdk \
JAVA_HOME=/path/to/jdk-17 \
ZIG=$(command -v zig) \
ONESTAR_KS=release.keystore ONESTAR_KS_PASS=****** ONESTAR_KS_ALIAS=onestar \
./build.sh
```

The output lands at `build/onestar-<versionName>-signed.apk`.

Environment variables: `ANDROID_HOME` / `ANDROID_SDK_ROOT`, `BUILD_TOOLS`, `JAVA_HOME`,
`MIN_SDK` (default 21), `TARGET_SDK` (default 35), `ABIS` (default `arm64-v8a`), `ZIG`,
`ONESTAR_KS` / `ONESTAR_KS_PASS` / `ONESTAR_KS_ALIAS`, `ONESTAR_KS_B64` (base64-inlined keystore,
used by CI), `SKIP_VERSION_CHECK`.

### 3.3 Install on a device

```bash
adb install -r build/onestar-0.12.2-signed.apk
```

`-r` installs over the existing app while keeping data. An in-place upgrade requires **the same
signing certificate** and a `versionCode` no lower than the installed one; fail either and the
system refuses the install (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` or
`INSTALL_FAILED_VERSION_DOWNGRADE`). Details in [5.3](#53-signing-and-in-place-upgrades).

### 3.4 Run the tests (no emulator, no android.jar)

```bash
JAVA_HOME=/path/to/jdk-17 ./test.sh
```

`test.sh` picks out the pure-logic classes that do not `import android.*`, compiles them together
with the assertion programs in `test/` using plain `javac`, then runs each `main`. There are
currently 9 test classes and 3131 assertions in total (221 across the first seven classes, 2910
across the two i18n layers):

| Test class | Covers | Assertions |
| --- | --- | --- |
| `CompatTableTest` | OS version / page size / memory tier mapping / page-size wording | 38 |
| `V4LogicTest` | Orbit replay and curve sampling | 59 |
| `ConstellationTest` | Constellation and star-name generation | 31 |
| `V62LogicTest` | Back-fill markers, undo snapshot encoding compatibility | 26 |
| `NoteTextTest` | Note text wrapping and truncation | 24 |
| `DayEditTest` | Day-entry editing state machine | 23 |
| `MelodyTest` | Melody sequence and tempo | 20 |
| `I18nTableTest` | i18n table completeness, language detection, date/number localisation | 2818 |
| `I18nTest` (package `com.yangyang2857rgb.onestar`) | No Han characters may leak from logic classes in foreign-language modes | 92 |

This habit — extract the logic that can be made pure, and hold it down with JVM assertions — is
the main reason this project can evolve by hand for a long time without collapsing.

## 4. Repository layout

```text
.
├── AndroidManifest.xml          # single Activity + one AppWidget receiver, no permissions declared
├── build.sh                     # the Gradle-free build script
├── test.sh                      # pure-JVM test entry point
├── assets/
│   └── COPYRIGHT.txt            # attribution (packed into the APK, also used for in-version notes)
├── docs/
│   ├── README.md                # docs index, reading order and maintenance conventions
│   ├── 全生态开发与发布总纲.md  # six-platform master plan (failure surfaces / timeline / 12 gates)
│   ├── ecosystems/              # per-platform source notes
│   ├── 发版规范.md              # versioning rules and release discipline
│   ├── 拾光OneStar_版本时间轴.md  # version history and legacy build-number mapping
│   ├── 签名与覆盖升级.md        # certificate pinning, CI injection and verification
│   ├── 凭据与令牌管理.md        # PAT types and scopes, transfer, leak handling
│   └── screenshots/             # screenshots referenced by this README
├── java/com/yangyang2857rgb/onestar/
│   ├── MainActivity.java        # entry point, gestures, lifecycle
│   ├── StarFieldView.java       # core custom view (3036 lines)
│   ├── I18n.java                # 15-language string table (3237 lines, the largest file here)
│   ├── TextDraw.java            # Canvas text drawing and RTL visual reordering
│   ├── DaysStore.java           # SharedPreferences storage
│   ├── DaysCodec.java           # entry encoding/parsing (pure logic, unit-tested)
│   ├── DayEdit.java             # editing state machine (pure logic)
│   ├── Constellation.java       # constellations / star names (pure logic)
│   ├── StarName.java            # star names, Chinese and English (pure logic)
│   ├── ChimePlayer.java         # chime playback
│   ├── MoodCurve.java           # mood curve sampling
│   ├── Replay.java              # orbit replay
│   ├── Melody.java              # chime melody
│   ├── NoteText.java            # note wrapping
│   ├── PosterRenderer.java      # year-in-review poster
│   ├── StarWidget.java          # home-screen widget
│   ├── WidgetState.java         # widget state (pure logic)
│   ├── Compat.java / CompatTable.java  # system capability adaptation (the latter is pure logic)
│   └── NativeCore.java          # JNI declarations with a pure-Java fallback
├── jni/
│   ├── include/                 # minimal headers needed for cross-compilation
│   └── src/{ons_page,ons_dsp,ons_curve}.c
├── res/                         # hand-written drawable / mipmap / xml (including locales_config.xml)
├── tools/
│   ├── zip16k.py                # 16 KB alignment repackaging
│   └── verify_apk_signature.py  # signature verification and certificate fingerprint check
└── test/                        # pure-JVM assertion programs
```

## 5. In depth: three things worth their own section

### 5.1 16 KB page alignment

Starting with Android 15, some devices use 16 KB memory pages, and a `.so` that is not 16 KB
aligned will fail to load. `zipalign` in build-tools 34 and below does not support `-P 16` (35.0.0+
does), so `tools/zip16k.py` exists: it repackages `lib/**/*.so` as STORED (uncompressed) entries
and pushes the data offset to a multiple of 16384 using a ZIP extra field, while aligning the
remaining entries to 4 bytes. The script has a built-in `--check`, and the build prints the actual
offset and alignment of every `.so` at the end.

### 5.2 JNI without an NDK

`jni/src` holds three thin C files (page index, sampling, curves), all compiled with `-nostdlib`,
needing only the `jni.h` shipped with the JDK plus a minimal shim. Cross-compiling with
`zig cc --target aarch64-linux-musl` avoids a multi-gigabyte NDK install. `NativeCore` falls back
to a pure-Java implementation when the library is missing, so the project builds and runs without
`ZIG` too (at some performance cost).

### 5.3 Signing and in-place upgrades

Android validates three things on an in-place install: **same package name**, **same certificate**,
and **a `versionCode` that does not go backwards**. Fail any of them and the installer refuses.

This repository once had that exact problem in CI: every workflow run called
`keytool -genkeypair` and generated a throwaway debug keystore, so **every build produced a
differently signed APK**. Users who installed one build and then the next got
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, had to uninstall, and lost their data.

The rule now: **pinned certificate, pinned passphrase, monotonically increasing build number.**

| Item | Value |
| --- | --- |
| Package name | `com.yangyang2857rgb.onestar` |
| Certificate subject | `CN=OneStar Debug, O=OneStar, C=CN` |
| Certificate SHA-256 | `B0:F1:E2:A1:36:84:16:90:0A:AC:9C:A2:F9:9F:19:1C:3F:E2:C4:13:43:50:3A:7F:38:A0:F7:98:79:D5:4C:D1` |
| Validity | 2026-11-07 → 2054-03-25 |
| Signature schemes | v1 (JAR) + v2 (APK Signature Scheme v2) — `build.sh` pins them explicitly with `--v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled false --v4-signing-enabled false`, never relying on build-tools defaults |

CI injects that certificate through repository secrets rather than generating one:

1. Base64 the keystore locally: `base64 -w0 onestar-debug.keystore > ks.b64`
2. Create `ONESTAR_KS_B64` (the certificate) and `ONESTAR_KS_PASS` (the passphrase) under
   **Settings → Secrets and variables → Actions**
3. When `ONESTAR_KS_B64` is present, `build.sh` decodes it into `build/work/` before signing; only
   without it does it fall back to a throwaway debug keystore
4. At the end of the build, `tools/verify_apk_signature.py` checks the certificate fingerprint and
   fails the pipeline on a mismatch

Local self-check:

```bash
# Print the APK's signing certificate
apksigner verify --print-certs build/onestar-0.12.2-signed.apk

# Or just check that the fingerprint matches the one this project pins
python3 tools/verify_apk_signature.py build/onestar-0.12.2-signed.apk
```

One boundary worth stating clearly: the private key of this debug certificate travels with the
repository and its secrets, and **it offers no authenticity guarantee whatsoever**. It is fit for
personal use, for trying the app and for validating the build chain — **it cannot be published to
an app store and is not suitable for public distribution**. Real distribution requires your own
release keystore (`ONESTAR_KS` / `ONESTAR_KS_PASS`), at the cost of existing users uninstalling
and reinstalling.

## 6. CI

`.github/workflows/android.yml` is a ready-to-use workflow: on Ubuntu it installs build-tools
35.0.0 and platform 35, runs `test.sh` then `build.sh`, and uploads the signed APK as a workflow
artifact. The pipeline contains no Gradle either.

Triggers: pushes to `main`, `v*` tags, or a manual **Actions → build → Run workflow**. The artifact
appears under that run's Artifacts → `onestar-apk`.

Signing: the workflow reads the `ONESTAR_KS_B64` / `ONESTAR_KS_PASS` secrets. If they are absent it
builds with a throwaway debug keystore and logs an explicit warning — such artifacts cannot be
installed as an upgrade.

## 7. Versioning and release

Version history lives in [CHANGELOG.md](CHANGELOG.md), the full rules in
[docs/发版规范.md](docs/发版规范.md), and the historical mapping in
[docs/拾光OneStar_版本时间轴.md](docs/拾光OneStar_版本时间轴.md).

Three hard rules:

- **`versionName` is semantic**: `MAJOR` for breaking changes (storage format, package name, build
  method, signing), `MINOR` for new user-visible features, `PATCH` for fixes, feel, copy and
  accessibility only
- **`versionCode` is an independent build number**: no arithmetic relation to `versionName`, never
  decreasing, never reused; `build.sh` checks it before building
- **Documentation-only changes do not get a release**: edits to the README, comments or CHANGELOG
  go straight to `main` without consuming a version number

A complete release:

1. Change `versionCode` / `versionName` in `AndroidManifest.xml` and keep `assets/COPYRIGHT.txt`
   in sync
2. Update `CHANGELOG.md` (Keep a Changelog style, unreleased work under `Unreleased`)
3. `./test.sh && ./build.sh`, both green
4. Commit, tag (`v0.12.2`), push
5. Create the Release from that tag on **Releases**, attach the APK and its SHA-256, and spell out
   the changes and upgrade notes

> Historical baggage: `v6.3`–`v6.7` used the old build-number-style names, and `0.12.x` marks the
> return to semantic versioning. The two are mapped in the CHANGELOG and the version timeline,
> while `versionCode` stays continuous throughout. `0.12.2` is `versionCode 23`.

## 8. Data and privacy

- **No permissions declared**: `AndroidManifest.xml` contains no `INTERNET`, no storage, no
  location, no sensor permissions — at the permission level the app cannot reach the network
- **No third-party SDK, analytics, ads or crash reporting**
- Entries live in app-private `SharedPreferences`, encoded by `DaysCodec`; upgrades do not change
  the storage format, and uninstalling deletes everything
- `android:allowBackup="true"`, so system backups (including `adb backup` and device migration)
  carry the data along; set it to `false` if you would rather not
- The year-in-review poster is rendered locally on Canvas and uploaded nowhere

## 9. FAQ

**Q: An in-place install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`?**
The certificates differ. Compare both APKs with `apksigner verify --print-certs`; if they really
differ, either uninstall and reinstall (losing data) or re-sign with the same keystore.

**Q: `INSTALL_FAILED_VERSION_DOWNGRADE`?**
The new package's `versionCode` is less than or equal to the installed one. `versionCode` only goes
up — raise it and rebuild.

**Q: `build.sh` cannot find `aapt2` / `d8` / `apksigner`?**
`ANDROID_HOME` points at the wrong place, or build-tools is too old (35.0.0+ is required). Check
with `ls $ANDROID_HOME/build-tools`, and pin it explicitly with `BUILD_TOOLS=35.0.0` if needed (CI
already pins that version).

**Q: Why are there no `res/values-zh/` style resource folders?**
UI copy is not handed to the resource system. It lives in the string table in `I18n.java`, with
`TextDraw` handling wrapping, word breaking and RTL reordering. `res/` holds only the few entries
the system itself must read (icon names, widget titles), and `res/xml/locales_config.xml` exists
only to hook into the Android 13 per-app language screen.

**Q: Can I build without zig?**
Yes. `jni/` is skipped and `NativeCore` falls back to the pure-Java implementation — full
functionality, slightly lower performance.

**Q: Why only `arm64-v8a`?**
Almost every device in use is 64-bit ARM. For other ABIs change `ABIS` and add the matching
`zig cc` target yourself.

**Q: What is the minimum supported version?**
`minSdkVersion 21` (Android 5.0). Systems below Android 13 ignore `locales_config.xml`; the
language still follows the system through `I18n.detect()`.

**Q: Why is the version sometimes 6.x and sometimes 0.12.x?**
See the historical note in [7. Versioning and release](#7-versioning-and-release): 6.x used the
early build-number naming, 0.12.x returned to semantic versioning, and `versionCode` never broke
continuity.

## 10. Known limits

- Only `arm64-v8a` is built; other ABIs need a change to `ABIS` plus your own cross toolchain target
- UI coordinates are hand-computed for a fixed portrait ratio and need tuning at extreme aspect ratios
- Releases and CI artifacts are signed with a fixed **debug certificate**, fit only for personal
  use, trying the app and validating the build chain — **not for app-store publication and not for
  public distribution** (see 5.3)
- 15 languages are supported (Simplified and Traditional Chinese, English, Japanese, Korean, French,
  German, Spanish for Spain and Latin America, Arabic, Thai, Brazilian and European Portuguese,
  Tibetan, Uyghur), all implemented through the `I18n.java` string table and `TextDraw` RTL
  reordering, with no third-party i18n framework
- Accessibility is partial: since 0.12.2 the top capsule buttons expose a Button role and a
  `contentDescription` to TalkBack and have a touch target of at least 48dp; the full accessibility
  tree, focus order and reading rhythm have not been systematically worked through
- No incremental builds, always a full rebuild; no dependency management, so a new third-party
  library must be wired in by hand

## 11. Roadmap

| Stage | Work | Status |
| --- | --- | --- |
| 0.12.2 | Capsule button contrast and touch target, empty-state guidance, basic accessibility, pinned signing | Preview build `v0.12.2-rc1`, awaiting device regression |
| 1.0.0 | Freeze layout and copy; 15-language device regression; extreme system font sizes and orientation-change regression; copyright registration material | Planned |
| 1.x | Complete accessibility tree and focus order; more ABIs; entry export/import | TBD |

## 12. Documentation index

| Document | Contents |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | Per-version change log with `versionCode` mapping |
| [docs/发版规范.md](docs/发版规范.md) | Practical rules for SemVer 2.0.0 + Keep a Changelog + Conventional Commits + DORA metrics + WCAG 2.2 |
| [docs/拾光OneStar_版本时间轴.md](docs/拾光OneStar_版本时间轴.md) | Version history and the legacy-to-semantic build-number mapping |
| [docs/签名与覆盖升级.md](docs/签名与覆盖升级.md) | Certificate pinning, CI secret injection, verification and troubleshooting |
| [docs/凭据与令牌管理.md](docs/凭据与令牌管理.md) | PAT types and least privilege, token transfer, pre-commit self-checks and leak handling |
| [docs/全生态开发与发布总纲.md](docs/全生态开发与发布总纲.md) | One voice across six ecosystems: global timeline, identity and signing anchors, 12 unified release gates, risk matrix |
| [docs/README.md](docs/README.md) | docs index and reading paths |
| [docs/ecosystems/](docs/ecosystems/) | Full source notes per platform, including signing commands and per-item checklists |
| [LICENSE](LICENSE) | MIT |

## 12.1 Cross-platform development and release rules

OneStar maintains six ecosystems at once: Android, Web, HarmonyOS, Apple, Windows and Linux. Their
store policies, SDK thresholds and signing chains differ sharply, and **most of those constraints
are irreversible** — change a package name once and it is a different app, raise a Linux build
baseline once and old systems are gone for good, miss notarisation on macOS and the download simply
will not open.

So the cross-platform rules are written as something executable, kept in [`docs/`](docs/), entered
through the
[six-ecosystem master plan](docs/全生态开发与发布总纲.md):

| Document | Contents | When to read |
| --- | --- | --- |
| [Six-ecosystem master plan](docs/全生态开发与发布总纲.md) | One voice across the six ecosystems: global timeline, identity and signing anchors, cross-platform matrix, 12 unified release gates, risk matrix | Scheduling, setting anchors, before a release |
| [docs index](docs/README.md) | Document structure and reading paths | First time in `docs` |
| [docs/ecosystems/](docs/ecosystems/) | Full per-platform notes, including signing commands and item-by-item checklists | When you need exact commands or a checklist |

### The three facts worth knowing first

1. **The "already in force" column of the timeline is the one that matters.** The master plan lists
   "in force" and "pending" separately: Android already requires target API 36 (Wear OS / AAOS 35,
   TV / XR 34) and 16 KB page alignment; Apple already requires the iOS 26 SDK, and macOS 27 will be
   Apple Silicon only; Windows 10 is fully EOL (consumer ESU ended 2026-10-13, commercial ESU runs
   to 2028-01 at most). Fail these and your package is not "a worse experience" — it **cannot be
   submitted**. (Since 0.12.2 this project builds `targetSdk 37` and distributes through GitHub
   Release sideloading, which already covers Play's 36 threshold — the same fact as §5 and the badge
   note above.)
2. **EV certificates no longer bypass SmartScreen.** The only zero-warning path on Windows is the
   Microsoft Store; non-Store distribution means accepting a reputation build-up of weeks and
   hundreds of installs. Buying an EV certificate purely to silence the warning is money burnt.
3. **Privacy review on all six platforms is the same chain**: permission declarations → actual SDK
   behaviour → network requests → store privacy forms → privacy policy. The five forms should all
   derive from one data-flow diagram; doing it the other way round reliably produces
   "declaration does not match behaviour" rejections.

### Maintenance conventions

Rule documents age as platform policy moves. The convention: re-run the timeliness check in §8 of
the master plan every quarter (Android target API, Apple SDK thresholds, Windows App SDK versions,
HarmonyOS listing rules, plus the Chrome release cycle, OWASP Top 10 and WCAG versions); when a
policy changes, edit the original under `docs/ecosystems/` first and then sync the master plan, so
the two cannot drift. **After changing a timeliness item in the master plan, the summary in this
section and the badges at the top (targetSdk and friends) must be written back** — the same fact
was once wrong in both the master plan and this README at the same time.

## 13. Contributing

- Bug fixes and feel tweaks: open a PR against `main` with Conventional Commits (`fix:` / `feat:` /
  `docs:` / `chore:`)
- Behaviour changes: open an Issue first explaining the motivation and blast radius; anything
  touching the storage format or signing must spell out the upgrade path
- Run `./test.sh` before opening a PR; changes to `build.sh` or `tools/` should come with a full
  build log
- Adding or changing copy: all 15 languages in the `I18n` table must be updated together, and
  `I18nTableTest` checks row counts and placeholder consistency

## 14. License and credits

Code is released under the [MIT License](LICENSE). Keep the attribution in `assets/COPYRIGHT.txt`
with any redistribution.

Thanks to the Android SDK command-line toolchain (`aapt2` / `d8` / `apksigner`) and to
[zig](https://ziglang.org/) for offering another way without Gradle and without the NDK.

---

Author: Wang Sunyang (王孙阳) · independent development
