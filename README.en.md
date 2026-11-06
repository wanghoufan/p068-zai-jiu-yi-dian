# Zai Jiu Yi Dian (P068)

An **offline-first** Android app with two training modes: a real countdown timer and set-by-set rep logging, sharing full history and local backups.

- No network, no account, no uploads: the app declares no INTERNET permission.
- Four tabs: **Time / Reps / History / Settings**.
- Time mode keeps the countdown, pause/resume, overrun, target snapshot, and lock-screen cues.
- Reps mode records actual reps per set, with edit/undo/extra-set/early-finish and draft recovery.
- Per-project mode and goals; time and reps projects never mix units.
- Charts: performance trend, weekly frequency, frequency-vs-performance; time uses minutes, reps use total reps/best set, and "all projects" only combines session count and days.
- Backup: full JSON V3 (project mode, rep plans, per-set details, draft, settings) and CSV; V1/V2 backups remain readable.
- In-app font size: Settings → Appearance → Font size (100% / 115% / 130% / 145%, default 115%).
- Light theme by default, dark theme available.
- Voice feedback: a vibration + spoken "开始训练" on start, a spoken checkpoint every 3 minutes, and a spoken cue when the target is reached. Can be disabled under Settings → Training preferences → Voice reminders.

## Install

APK: `dist/P068-zai-jiu-yi-dian-v1.1.0-debug.apk`

- Package: `com.wanghoufan.zaijiuyidian`
- Version: 1.1.0 (versionCode 3)
- Size: 16,033,544 bytes
- SHA-256: `3d81a707df8473a1e54365c7a815cc4ff359602c0a1812bc5e78a481d1055919`
- DEBUG test-signed build for personal sideloading, not a store release.

```bash
adb install -r -t dist/P068-zai-jiu-yi-dian-v1.1.0-debug.apk
```

On Xiaomi/HyperOS, if installation is rejected with `INSTALL_FAILED_USER_RESTRICTED`, enable Developer options → "Install via USB" and keep the screen awake. The switch may turn off after locking or rebooting.

## Build & test

Requires JDK 17 and Android SDK 36:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## Known limitations

- Lock-screen/background cues require the foreground service and notification permission; they are not guaranteed if notifications are denied, Do Not Disturb/battery policy intervenes, or the system force-stops the app. Elapsed time remains accurate when returning.
- Automatic local backups use a user-selected SAF folder, keep the last 7 versions, and can be previewed/restored with a safety checkpoint. Failure paths (write failure, revoked permission, reboot) still need per-device verification.
- V1.1 main paths verified on Redmi Note 11 5G (Android 11): four tabs, reps, all three charts, delete/restore, V3 export/replace/duplicate-merge, 145% font. Redmi Note 12 Pro (Android 14) verified install, v1→v2 migration, four tabs, and old data retention. Redmi Note 12T Pro (Android 15) verified install/hash/launch/migration; its full dual-mode flow is NOT_RUN.
- Test-signed; not published to any store; source committed and pushed to `main` (`693f685`).

See `docs/qa/QA-REPORT-P068-V1.1-R2.md` and `docs/qa/产品验收追踪矩阵-P068-V1.1-R2.md` for evidence.
