# 架构与构建事实｜再久一点（P068）

## 1. 这是什么

- 一句话定位：双模式训练 Android 应用——时长倒计时与按组计次共用完整历史、统计与备份。
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
| 产物 | `dist/P068-zai-jiu-yi-dian-v1.1.0-debug.apk`（versionCode 3，DEBUG 测试签名） |

| 路径 | 职责 |
|---|---|
| `app/src/main/java/.../data/db/` | Room v2 实体/DAO/数据库（projects、sessions、active_session、rep_sets、rep_draft） |
| `app/src/main/java/.../data/prefs/` | DataStore 设置（主题、提示、筛选、字体、时长/计次上次项目） |
| `app/src/main/java/.../data/backup/` | 备份 V3/V2/V1 校验、每组明细与草稿、CSV、SAF 自动备份、轮转、列表与回滚 |
| `app/src/main/java/.../domain/` | 双模式统计、计时纯函数、格式化、模型与字体档位 |
| `app/src/main/java/.../ui/` | ViewModel、四 Tab 页面（时长/次数/历史/设置）、底部弹层、三图 |
| `app/src/main/java/.../audio/` | `VoiceCues` + `TrainingForegroundService`：前台服务锁屏/后台播报 |
| `app/src/main/res/raw/cue_*.wav` | 离线语音资产（36 个，约 3.2 MB）；`tools/make_voice_cues.sh` 可复现生成 |
| `app/src/test/` | JVM 单测（stats/timer/backup/Room-Robolectric） |
| `docs/qa/` | 验收矩阵、QA 报告、证据截图 |
| `test-data/` | 合成匿名 QA 备份（非真实个人数据） |

## 3. 关键约定（改代码前必须知道）

- **双模式**：`ProjectEntity/SessionEntity.mode` 为 `TIME`/`REPS`；旧数据迁移默认 `TIME`。计次不使用秒数列：`actualSec/targetSec=0`，每组一条 `rep_sets.actualReps`，总次数由 Sets 求和。
- **计次计划快照**：Session 保存 `plannedSets/targetRepsPerSet`；当次改计划只影响草稿/默认，不回改旧记录。
- **草稿**：`rep_draft` 单行持久化（sets CSV、nextReps、touchedAt），与正式 sessions 隔离；进程退出/切 Tab 后恢复，未保存不进历史。
- **Room 迁移**：DB v1→v2 显式 `MIGRATION_1_2`（加列 + 建 rep_sets/rep_draft），旧项目/会话/活动计时保留并默认 TIME；禁止 destructive migration。
- **时长只用单调时钟**：`SystemClock.elapsedRealtime()`；墙钟只用于展示与导出完成时间。重启通过 `BOOT_COUNT` 与 boot-wall-base 双判据识别。
- **目标快照**：每条 Session 独立保存 `targetSec`；计次保存计划快照。改项目默认目标不得回改历史。
- **结束确认不改暂停计数**：`requestFinish` 只取快照；`pauseCount/pausedSec` 来自真实暂停。
- **项目行只能 UPDATE，不能 REPLACE**：`ProjectDao.upsert` 的 `INSERT OR REPLACE` 会瞬时删父行，触发 `sessions.projectId` 外键 RESTRICT。已存在项目的改名/目标/预设/归档/恢复必须用 `@Update`；回归见 `ProjectDaoTest`。
- **备份 schema**：V3 写 `version=3`，新增 mode、计次计划、每组 sets、lastTime/lastReps 与草稿；V2 写 theme/sound/vibration/voice/font/range/chart/filter；V1/V2 文件仍按各自原算法校验并补默认值，不得先补字段再校验。
- **统计口径**：TIME 用分钟、REPS 用总次数/最佳单组；`全部项目` 只合并训练次数、天数与类型数量，不把分钟与次数相加。周一为周起点；草稿与软删除不计。
- **历史**：三图（成绩趋势/周频率/频率关系）+ 关系数据表；项目/时间/搜索筛选联动 KPI 与图；计次记录逐组编辑后即时重算。
- **自动备份**：用户 SAF 树授权；数据变动 1.2s 合并；写后回读校验；只保留最近 7 个 `zjyd-auto-*`；`zjyd-safety-*` 与手动文件不轮转。恢复前必须写并校验保护点。
- **锁屏语音**：`TrainingForegroundService`（specialUse FGS + 常驻通知 + PARTIAL_WAKE_LOCK）读活动会话；节点仍以单调时钟为准；服务存活时间隔/达标由服务播报，UI 抑制重复播放；通知拒绝时如实提示，不承诺系统强杀后继续。
- **无 INTERNET 权限**：Manifest 不声明网络权限，离线能力是结构保证而非行为约定。
- **应用内字体缩放**：`ZaiJiuTheme(fontScale)` 通过覆盖 `LocalDensity.fontScale` 实现，档位 100/115/130/145%，默认 115%。系统字体仍叠加。
- **语音节点**：有效训练时长每满 3 分钟（`TimerMath.intervalMark`，180000ms）播报“已经训练 X 分钟”；仅在前台触发，暂停不计；进/重启按当前节点初始化，不重复旧点。>99 分钟用通用播报。语音/提示音/震动三个开关独立，语音失败自动回退短提示音。
- **调试签名**：当前 APK 用 Debug keystore；正式分发前必须另行决定签名与上架。

## 4. 验证与门禁

- 本地自测：`./gradlew :app:testDebugUnitTest`（当前 32/32，含 V1/V2/V3 备份、计次统计、Room v1→v2 迁移与 DAO 外键回归）。
- 真机验收：Note 11 5G / Android 11 完成四 Tab、计次、三图、删除/恢复、V3 导出/覆盖/重复合并、145% 字体；Note 12 Pro / Android 14 完成安装、v1→v2 迁移、四 Tab、旧数据保留；Note 12T Pro 本轮未连接，V1.1 项 NOT_RUN。证据见 `docs/qa/evidence-v11/`。
- 发布前必须通过：验收矩阵 P0 全 PASS（V11R2-AC15 需用户签收）；构建哈希与 `dist/SHA256SUMS.txt` 一致。
