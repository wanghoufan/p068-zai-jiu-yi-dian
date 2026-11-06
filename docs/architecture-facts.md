# 架构与构建事实｜再久一点（P068）

## 1. 这是什么

- 一句话定位：通用「时长进阶」Android 应用——设目标 → 单调时钟计时 → 结束确认 → 本地历史与统计 → 备份恢复。
- 交付形态：原生 Android 单端 APK，中文 UI，离线优先，无账号/无云。
- 包名：`com.wanghoufan.zaijiuyidian`；应用名「再久一点」。

## 2. 技术栈与目录

| 项 | 值 |
|---|---|
| 语言 / UI | Kotlin 2.2.20 + Jetpack Compose（Material 3，BOM 2025.09.00） |
| 构建 | Gradle 8.14.3 / AGP 8.13.0 / JDK 17；compileSdk 36，targetSdk 36，minSdk 26 |
| 数据 | Room 2.7.2（KSP）+ DataStore Preferences 1.1.7 |
| 构建命令 | `./gradlew :app:assembleDebug` |
| 测试命令 | `./gradlew :app:testDebugUnitTest`（含 Robolectric DAO 回归） |
| 产物 | `dist/P068-zai-jiu-yi-dian-v1.0.0-debug.apk`（DEBUG 测试签名） |

| 路径 | 职责 |
|---|---|
| `app/src/main/java/.../data/db/` | Room 实体/DAO/数据库（projects、sessions、active_session） |
| `app/src/main/java/.../data/prefs/` | DataStore 设置（主题、提示、筛选、字体大小） |
| `app/src/main/java/.../data/backup/` | JSON/CSV 导出、校验、合并/覆盖、事务与安全快照 |
| `app/src/main/java/.../domain/` | 统计、计时纯函数、格式化、模型与字体档位 |
| `app/src/main/java/.../ui/` | ViewModel、三 Tab 页面、底部弹层、图表 |
| `app/src/main/java/.../audio/` | `VoiceCues`：SoundPool 预加载本地 WAV，开始/每 3 分钟/达标播报 |
| `app/src/main/res/raw/cue_*.wav` | 离线语音资产（36 个，约 3.2 MB）；`tools/make_voice_cues.sh` 可复现生成 |
| `app/src/test/` | JVM 单测（stats/timer/backup/Room-Robolectric） |
| `docs/qa/` | 验收矩阵、QA 报告、证据截图 |
| `test-data/` | 合成匿名 QA 备份（非真实个人数据） |

## 3. 关键约定（改代码前必须知道）

- **时长只用单调时钟**：`SystemClock.elapsedRealtime()`；墙钟只用于展示与导出完成时间。重启通过 `BOOT_COUNT` 与 boot-wall-base 双判据识别。
- **目标快照**：每条 Session 独立保存 `targetSec`，改项目默认目标不得回改历史。
- **结束确认不改暂停计数**：`requestFinish` 只取快照；`pauseCount/pausedSec` 来自真实暂停。
- **项目行只能 UPDATE，不能 REPLACE**：`ProjectDao.upsert` 的 `INSERT OR REPLACE` 会瞬时删父行，触发 `sessions.projectId` 外键 RESTRICT。已存在项目的改名/目标/预设/归档/恢复必须用 `@Update`；回归见 `ProjectDaoTest`。
- **备份 schema v1**：`app=duration-progress`、`version=1`、SHA-256 校验摘要；设置只含 theme/sound/vibration（字体大小为本机偏好，不参与备份校验，以兼容旧备份）。
- **无 INTERNET 权限**：Manifest 不声明网络权限，离线能力是结构保证而非行为约定。
- **应用内字体缩放**：`ZaiJiuTheme(fontScale)` 通过覆盖 `LocalDensity.fontScale` 实现，档位 100/115/130/145%，默认 115%。系统字体仍叠加。
- **语音节点**：有效训练时长每满 3 分钟（`TimerMath.intervalMark`，180000ms）播报“已经训练 X 分钟”；仅在前台触发，暂停不计；进/重启按当前节点初始化，不重复旧点。>99 分钟用通用播报。语音/提示音/震动三个开关独立，语音失败自动回退短提示音。
- **调试签名**：当前 APK 用 Debug keystore；正式分发前必须另行决定签名与上架。

## 4. 验证与门禁

- 本地自测：`./gradlew :app:testDebugUnitTest`（当前 23/23）。
- 真机验收：Redmi Note 12 Pro / Android 14（功能）；12T Pro / Android 15、11 5G / Android 11（安装启动）；证据见 `docs/qa/evidence/`。
- 发布前必须通过：验收矩阵 P0 全 PASS（AC-20 需用户签收）；构建哈希与 `dist/SHA256SUMS.txt` 一致。
