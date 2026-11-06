# Zai Jiu Yi Dian (P068)

An **offline-first** Android app for duration training: set a goal, run a countdown, confirm the session, review history and charts, and back up locally.

- No network, no account, no uploads: the app declares no INTERNET permission.
- Three tabs: **Train / History / Settings**.
- Per-project goals; every session keeps its own target snapshot.
- Charts: actual-duration trend, weekly frequency + average, weekly frequency vs. performance with denominators.
- Backup: full JSON (projects, targets, presets, history, notes, settings) and CSV for spreadsheets.
- In-app font size: Settings → Appearance → Font size (100% / 115% / 130% / 145%, default 115%).
- Light theme by default, dark theme available.
- Voice feedback: a vibration + spoken "开始训练" on start, a spoken checkpoint every 3 minutes, and a spoken cue when the target is reached. Can be disabled under Settings → Training preferences → Voice reminders.

## Install

APK: `dist/P068-zai-jiu-yi-dian-v1.0.0-debug.apk`

- Package: `com.wanghoufan.zaijiuyidian`
- Version: 1.0.0 (versionCode 1)
- SHA-256: `15cc5d3e133e256b7c0dcff38d55e0644e9e524e3ea74649ca0d17f93e9f6e5c`
- DEBUG test-signed build for personal sideloading, not a store release.

```bash
adb install -r -t dist/P068-zai-jiu-yi-dian-v1.0.0-debug.apk
```

On Xiaomi/HyperOS, if installation is rejected with `INSTALL_FAILED_USER_RESTRICTED`, enable Developer options → "Install via USB" and keep the screen awake. The switch may turn off after locking or rebooting.

## Build & test

Requires JDK 17 and Android SDK 36:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## Known limitations

- No lock-screen/background target alert; elapsed time is accurate when returning to the app.
- Automatic local backups are not implemented; manual JSON/CSV export is available.
- Functional QA on Redmi Note 12 Pro (Android 14); install/launch also verified on Redmi Note 12T Pro (Android 15) and Redmi Note 11 5G (Android 11).
- Test-signed; not published to any store.

See `docs/qa/QA-REPORT-P068-V1.0.md` and `docs/qa/产品验收追踪矩阵-P068-V1.0.md` for evidence.
