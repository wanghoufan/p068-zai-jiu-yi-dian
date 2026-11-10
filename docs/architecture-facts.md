# 架构与构建事实｜再久一点（P068）

## 1. 这是什么

- 一句话定位：双模式训练 Android 应用——时长倒计时与按组计次共用完整历史、统计与备份；云同步为可选能力，默认关闭。
- 交付形态：原生 Android 单端 APK，中文 UI，本地优先；未开启云同步时数据只在本机。
- 包名：`com.wanghoufan.zaijiuyidian`；应用名「再久一点」。
- 账号与云：用户名 + 密码登录（CloudBase 身份认证的账号 `zjyd`）；云端按 `owner_uid` 隔离；不启用手机号/邮箱/微信/匿名登录。

## 2. 技术栈与目录

| 项 | 值 |
|---|---|
| 语言 / UI | Kotlin 2.2.20 + Jetpack Compose（Material 3，BOM 2025.09.00） |
| 构建 | Gradle 8.14.3 / AGP 8.13.0 / JDK 17；compileSdk 36，targetSdk 36，minSdk 26 |
| 数据 | Room 2.7.2（KSP，schema v3）+ DataStore Preferences 1.1.7 |
| 网络 | 仅 HTTPS 调用 CloudBase（`*.api.tcloudbasegateway.com`）：`/auth/v1/*` 登录 + PostgREST 业务表读写；不用 WebView/长连接 |
| 构建命令 | `./gradlew :app:assembleDebug` |
| 测试命令 | `./gradlew :app:testDebugUnitTest`（含 Robolectric DAO 回归） |
| 产物 | `dist/P068-zai-jiu-yi-dian-v1.2.0-debug.apk`（versionCode 4，17,010,870 字节，SHA-256 `0d22afbd144e06b2b9afa3e23f9ffe44b69286d774a4023053c5494d68fc0811`，DEBUG 测试签名） |

| 路径 | 职责 |
|---|---|
| `app/src/main/java/.../data/db/` | Room v3 实体/DAO/数据库（projects、sessions、active_session、rep_sets、rep_draft、sync_outbox、sync_state） |
| `app/src/main/java/.../data/sync/` | `SyncManager.kt`：登录/登出、首传预览、Outbox 上行、按游标增量拉取、条件更新与冲突标记、状态汇总 |
| `app/src/main/java/.../data/prefs/` | DataStore 设置（主题、提示、筛选、字体、时长/计次上次项目） |
| `app/src/main/java/.../data/backup/` | 备份 V3/V2/V1 校验、每组明细与草稿、CSV、SAF 自动备份、轮转、列表与回滚 |
| `app/src/main/java/.../domain/` | 双模式统计、计时纯函数、格式化、模型与字体档位 |
| `app/src/main/java/.../ui/` | ViewModel、四 Tab 页面（时长/次数/历史/设置）、底部弹层、三图；设置页含云同步入口（登录/登出/立即同步/状态） |
| `app/src/main/java/.../audio/` | `VoiceCues` + `TrainingForegroundService`：前台服务锁屏/后台播报 |
| `app/src/main/res/raw/cue_*.wav` | 离线语音资产（36 个，约 3.2 MB）；`tools/make_voice_cues.sh` 可复现生成 |
| `app/src/test/` | JVM 单测（stats/timer/backup/Room-Robolectric/sync） |
| `docs/qa/` | 验收矩阵、QA 报告、证据截图 |
| `test-data/` | 合成匿名 QA 备份（非真实个人数据） |

## 3. 关键约定（改代码前必须知道）

- **双模式**：`ProjectEntity/SessionEntity.mode` 为 `TIME`/`REPS`；旧数据迁移默认 `TIME`。计次不使用秒数列：`actualSec/targetSec=0`，每组一条 `rep_sets.actualReps`，总次数由 Sets 求和。
- **计次计划快照**：Session 保存 `plannedSets/targetRepsPerSet`；当次改计划只影响草稿/默认，不回改旧记录。
- **草稿**：`rep_draft` 单行持久化（sets CSV、nextReps、touchedAt），与正式 sessions 隔离；进程退出/切 Tab 后恢复，未保存不进历史；不参与云同步。
- **Room 迁移**：DB v1→v2 显式 `MIGRATION_1_2`（加列 + 建 rep_sets/rep_draft）；v2→v3 显式 `MIGRATION_2_3`（`projects/sessions.revision`、`projects.deletedAt`、`rep_sets.updatedAt/deletedAt`、`sync_outbox`、`sync_state`）。禁止 destructive migration；真机升级用 `adb install -r` 原地迁移。
- **时长只用单调时钟**：`SystemClock.elapsedRealtime()`；墙钟只用于展示与导出完成时间。重启通过 `BOOT_COUNT` 与 boot-wall-base 双判据识别。
- **目标快照**：每条 Session 独立保存 `targetSec`；计次保存计划快照。改项目默认目标不得回改历史。
- **结束确认不改暂停计数**：`requestFinish` 只取快照；`pauseCount/pausedSec` 来自真实暂停。
- **项目行只能 UPDATE，不能 REPLACE**：`ProjectDao.upsert` 的 `INSERT OR REPLACE` 会瞬时删父行，触发 `sessions.projectId` 外键 RESTRICT。已存在项目的改名/目标/预设/归档/恢复必须用 `@Update`；回归见 `ProjectDaoTest`。
- **备份 schema**：V3 写 `version=3`，新增 mode、计次计划、每组 sets、lastTime/lastReps 与草稿；V2 写 theme/sound/vibration/voice/font/range/chart/filter；V1/V2 文件仍按各自原算法校验并补默认值，不得先补字段再校验。
- **统计口径**：TIME 用分钟、REPS 用总次数/最佳单组；`全部项目` 只合并训练次数、天数与类型数量，不把分钟与次数相加。周一为周起点；草稿与软删除不计。
- **历史**：三图（成绩趋势/周频率/频率关系）+ 关系数据表；项目/时间/搜索筛选联动 KPI 与图；计次记录逐组编辑后即时重算。
- **自动备份**：用户 SAF 树授权；数据变动 1.2s 合并；写后回读校验；只保留最近 7 个 `zjyd-auto-*`；`zjyd-safety-*` 与手动文件不轮转。恢复前必须写并校验保护点。
- **锁屏语音**：`TrainingForegroundService`（specialUse FGS + 常驻通知 + PARTIAL_WAKE_LOCK）读活动会话；节点仍以单调时钟为准；服务存活时间隔/达标由服务播报，UI 抑制重复播放；通知拒绝时如实提示，不承诺系统强杀后继续。
- **到点提醒**：独立渠道 `training_alert`「训练到点提醒」（IMPORTANCE_HIGH，声音 `content://settings/system/alarm_alert`，AudioAttributes `USAGE_ALARM`，锁屏可见；渠道震动关闭，震动由代码统一 3 次约 500ms，避免双份）。到点发通知 id=6802（category=alarm），结束/放弃取消；提示音走闹钟流（SoundPool `USAGE_ALARM`，ToneGenerator `STREAM_ALARM`）。开始/继续按剩余时间挂 `AlarmManager`，Android 12+ 无精确闹钟权限降级 `setAndAllowWhileIdle`，`TargetAlarmReceiver`（exported=false）与前台服务共用同一通知路径；暂停/结束/放弃取消闹钟。常驻通知 6801 只在状态变化（开始/暂停/继续）时更新，到点不再刷新；服务与闹钟 2 秒内只发一次 6802（此前每秒刷新 + 双发会被 MIUI 判为 `Muting recently noisy` 而静音）。前台服务 wakeLock 放宽到 6 小时。勿扰/静音是系统限制（`mBypassDnd=false`，需用户把提醒设为优先或训练时关勿扰）；国产 ROM 省电仍可能延迟闹钟。
- **网络权限**：`app/src/main/AndroidManifest.xml:9` 声明 `INTERNET`，仅用于用户主动开启的云同步。未登录/未开启同步时代码路径无后台请求、无定时任务；该结论来自代码审查，尚未做真机抓包（AC01 PARTIAL）。
- **云同步单元**：只同步 project / session 两级，sets 随 session 载荷整体提交；`sync_outbox` 记录 mutationId、实体、操作、payload、baseRevision；写路径先落本机事务再排队。
- **条件更新**：更新携带 `baseRevision`，云端影响 0 行即冲突，不静默覆盖；已实现 409/0 行的检测与本地标记，两机并发冲突的恢复 UI 未实测。
- **增量拉取**：按 `(server_updated_at, id)` 游标拉取；服务端 `server_updated_at` 由触发器维护，不信任设备墙钟。
- **首传顺序**：登录 → 显示本机项目数/记录数 → 用户确认 → 上传 → 再拉取云端增量；B 机为「合并本机数据后拉取」，不是覆盖本机。
- **令牌**：登录凭证存加密文件（Android Keystore），退出登录后文件不含 access/refresh，仅保留 keyset；密码不落盘。
- **应用内字体缩放**：`ZaiJiuTheme(fontScale)` 通过覆盖 `LocalDensity.fontScale` 实现，档位 100/115/130/145%，默认 115%。系统字体仍叠加。
- **语音节点**：有效训练时长每满 3 分钟（`TimerMath.intervalMark`，180000ms）播报“已经训练 X 分钟”；仅在前台触发，暂停不计；进/重启按当前节点初始化，不重复旧点。>99 分钟用通用播报。语音/提示音/震动三个开关独立，语音失败自动回退短提示音。
- **调试签名**：当前 APK 用 Debug keystore；正式分发前必须另行决定签名与上架。

## 4. 云端对象（p059-prompter，2026-11-10 已建）

| 项 | 内容 |
|---|---|
| 环境 | `p059-prompter`（EnvId `p059-prompter-d6gzk482uf502540d`），上海，PG 模式，体验版 3000 点/月，到期 2027-04-25 |
| 表 | `public.p068_projects`、`public.p068_sessions`（带 `owner_uid varchar(64)`、`revision`、`deleted_at`、`server_updated_at`） |
| 安全 | 8 条 RLS（两表 SELECT/INSERT/UPDATE/DELETE 均限 `owner_uid = auth.uid()`）；anon 无私有表权限 |
| 触发器 | `server_updated_at` 由触发器在写入时刷新 |
| 验证 | REST 12/12 PASS：A/B 隔离、伪造 owner 403、revision 冲突 0 行、墓碑删除、触发器刷新；证据 `docs/qa/evidence-v12/cloud-tables-2026-11-10.txt` |

## 5. 验证与门禁

- 本地自测：`./gradlew :app:testDebugUnitTest`，本轮 39/39 PASS（含 stats/timer/backup、Room v1→v2→v3 迁移与 DAO 外键回归、Outbox 与同步逻辑）。
- 云端 REST：12/12 PASS（表结构与 RLS 探针）；早期账号/越权探针 13/13 PASS。
- 真机验收（Note 11 5G / Android 11 + 22101316C / Android 14，同账号两机）：
  - AC02 首传预览 PASS；AC04 两机一致 PASS（A 5 条 → B 拉到 5 条 → B 补 1 条 → A 同步 6 条；B 本地 5 项目/6 记录/13 组与 A 一致）。
  - AC06 墓碑 PASS（A 删除 → 云端 rev2 墓碑 → B 同步 live 5 / deleted 1 / outbox 0，未回传）。
  - AC07 断网补传 PASS（离线新建 OfflineTest 显示「待同步 1 条」，恢复网络立即同步成功，错误提示为中文）。
  - AC11 退出登录 PASS（回登录页，令牌无 access/refresh，本机 2 项目/5 记录不变，outbox 0）。
  - AC03/05/08/09/10 为 PARTIAL，AC01 为 PARTIAL，AC12 OPEN；逐条见 `docs/qa/产品验收追踪矩阵-P068-V1.2.md`。
  - 两机安装包 SHA-256 一致；B 为合并本地数据后拉取，不是覆盖。
- 发布前必须通过：验收矩阵 P0 全 PASS 或已书面接受 PARTIAL；AC12 用户签收；构建哈希与 `dist/SHA256SUMS.txt` 一致。
- 后台到点提醒已修（2026-11-10 实机验证：闹钟渠道 6802 + 长震动 + 闹钟流提示音）；剩余边界为勿扰/静音与 OEM 省电，见 R14。