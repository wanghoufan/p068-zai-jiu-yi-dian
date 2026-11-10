# V1.2 同步改造代码审计

- 审计对象：P068「再久一点」Android 端，工作区 `068-ing-再久一点`
- 审计方式：只读静态核对（未改代码、未构建、未联网）
- 对照基线：`docs/plan/P068-V1.2-Cloud-Sync-Plan.md` §3（数据模型草案）与 §4（同步协议）
- 结论口径：现状＝代码事实；差距＝「计划 §3 要求」减「现状」；不含实现方案设计

---

## 1. 事实表

### 1.1 构建与权限

| 项 | 现状 | 证据 file:line |
|---|---|---|
| 网络权限 | **无** `INTERNET`；仅有 VIBRATE / FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE / POST_NOTIFICATIONS / WAKE_LOCK | `app/src/main/AndroidManifest.xml:4-8` |
| 系统备份 | `allowBackup="false"`，`dataExtractionRules` 全量 exclude，`fullBackupContent="false"` | `AndroidManifest.xml:12-14`、`app/src/main/res/xml/data_extraction_rules.xml:3-14` |
| compileSdk / minSdk / targetSdk | 36 / 26 / 36 | `app/build.gradle.kts:10,14,15` |
| 版本号 | versionCode 3、versionName "1.1.0" | `app/build.gradle.kts:16-17` |
| 语言/构建 | Kotlin 2.2.20、AGP 8.13.0、KSP 2.2.20-2.0.2、JDK 17 | `build.gradle.kts:2-5`、`app/build.gradle.kts:28-31,52-56` |
| Compose | BOM 2025.09.00、material3、activity-compose 1.10.1 | `app/build.gradle.kts:33-36,63-69` |
| Room / 协程 / 设置 | room 2.7.2(KSP) + coroutines 1.10.2 + datastore-preferences 1.1.7 | `app/build.gradle.kts:74-78` |
| DI | **无 Hilt / Dagger**；手写单例 `AppDatabase.get()` + ViewModel 内 new Repository | `app/build.gradle.kts:62-95`、`AppDatabase.kt:78-84`、`ui/AppViewModel.kt:184-188` |
| 网络/序列化/加密/调度依赖 | **均无**（无 OkHttp/Retrofit/Ktor、无 kotlinx-serialization、无 security-crypto、无 WorkManager） | `app/build.gradle.kts:62-95`（全量依赖仅 Compose/Room/DataStore/coroutines/测试） |
| buildConfig | 仅 `buildConfig = true`，**无任何 `buildConfigField`** | `app/build.gradle.kts:33-36` |
| 签名 | **无 `signingConfigs`**；release 仅 `isMinifyEnabled=false`，dist 产物为 debug 签名 | `app/build.gradle.kts:21-26`、`docs/architecture-facts.md:18,51` |

### 1.2 Room 与表结构

| 项 | 现状 | 证据 file:line |
|---|---|---|
| 数据库版本 | `version = 2`，`exportSchema = true`，5 个实体 | `data/db/AppDatabase.kt:10-20` |
| 实体清单 | projects / sessions / active_session / rep_sets / rep_draft | `AppDatabase.kt:11-17` |
| DAO | ProjectDao / SessionDao / ActiveSessionDao / RepSetDao / RepDraftDao | `AppDatabase.kt:22-26`、`data/db/Daos.kt:11-132` |
| 迁移 | 只有 `MIGRATION_1_2`（加 mode/plannedSets/targetRepsPerSet + 建 rep_sets/rep_draft） | `AppDatabase.kt:29-73` |
| 构建方式 | `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`，**无 destructive 兜底** | `AppDatabase.kt:79-83` |
| 导出 schema | `app/schemas/com.wanghoufan.zaijiuyidian.data.db.AppDatabase/{1,2}.json`；2.json 已核 version=2 | `app/build.gradle.kts:58-60`、`app/schemas/.../2.json` |
| projects 主键/时间戳 | `id TEXT PK`；`createdAt`/`updatedAt`；`archivedAt`；**无 deletedAt、无 revision** | `data/db/Entities.kt:9-29` |
| sessions 主键/时间戳 | `id TEXT PK`；`createdAt`/`updatedAt`/`deletedAt`；**无 revision** | `Entities.kt:43-59` |
| sessions 索引 | projectId / endedAt / deletedAt | `Entities.kt:41` |
| sessions 外键 | → projects，`onDelete = RESTRICT` | `Entities.kt:33-40` |
| rep_sets 主键/列 | `id TEXT PK` + sessionId/ordinal/actualReps；**无 createdAt/updatedAt/deletedAt** | `Entities.kt:86-91` |
| rep_sets 约束 | `(sessionId, ordinal)` **唯一索引**；→ sessions `onDelete = CASCADE` | `Entities.kt:72-84`、`AppDatabase.kt:61-64` |
| rep_draft（本机态） | 单例 `id=1`；projectId/plannedSets/targetRepsPerSet/setsCsv/nextReps/touchedAt | `Entities.kt:93-109` |
| active_session（本机态） | 单例 `id=1`；14 列墙钟/单调钟混合运行时状态 | `Entities.kt:111-131` |
| 本机态读取 | DAO 固定 `WHERE id = 1` | `Daos.kt:100,103,115,118` |

### 1.3 删除与更新路径（同步改造核心面）

| 项 | 现状 | 证据 file:line |
|---|---|---|
| 项目删除 | `DELETE FROM projects WHERE id = :id` **硬删** | `Daos.kt:34-35` |
| 项目删除调用 | `deleteProject` 直接 `deleteById` | `ui/AppViewModel.kt:838-852`（`846`） |
| 项目删除前置判定 | 有历史（`sessionCount>0`）则改为「归档」，无历史才「删除」 | `AppViewModel.kt:826-836`、`Sheets.kt:98-120` |
| `sessionCount` 口径 | `SELECT COUNT(*) FROM sessions WHERE projectId=:id`，**含软删记录** | `Daos.kt:22-23` |
| 项目归档/恢复 | `@Update` 写 `archivedAt`，`updatedAt=now` | `Daos.kt:31-32`、`AppViewModel.kt:760-814` |
| 项目更新点 | 目标/预设/计次默认/改名/归档/恢复全部走 `update` + `updatedAt=now` | `AppViewModel.kt:521,552,650,685,741,775,797` |
| 项目 upsert | `@Insert(onConflict=REPLACE)`；**有历史时被 FK RESTRICT 拒绝**（已有回归） | `Daos.kt:25-26`、`app/src/test/.../ProjectDaoTest.kt:77-116` |
| 会话软删 | `UPDATE sessions SET deletedAt=:t, updatedAt=:t WHERE id AND deletedAt IS NULL` | `Daos.kt:64-65`、`AppViewModel.kt:1439-1454` |
| 会话恢复 | `UPDATE sessions SET deletedAt=NULL, updatedAt=:now` | `Daos.kt:67-68`、`AppViewModel.kt:1461-1469`、`HistoryScreenV11.kt:153-161` |
| 会话硬删 | `hardDelete` 已定义但**全仓库无调用点** | `Daos.kt:70-71` |
| 会话 upsert | `@Insert(REPLACE)`；用于保存/编辑/导入 | `Daos.kt:58-59`、`AppViewModel.kt:1106,1335,1424,1908`、`BackupRepository.kt:107,148` |
| 组明细删除 | `DELETE FROM rep_sets WHERE sessionId=:id`（**硬删**） | `Daos.kt:91-92` |
| 组明细编辑路径 | 先 upsert session，再 `deleteForSession` + `upsertAll`（先删后建） | `AppViewModel.kt:1422-1427` |
| 组明细 ID | 派生 `"$sessionId:${ordinal}"` | `AppViewModel.kt:1098,1416`、`BackupRepository.kt:112,152` |
| 整库替换（恢复） | `repSetDao.deleteAll` → `repDraft.delete` → `sessionDao.deleteAll` → `projectDao.deleteAll` 后重灌 | `BackupRepository.kt:140-179`（`143-146`） |
| 自动备份触发器 | InvalidationTracker 观察 `projects/sessions/rep_sets/rep_draft`；`active_session` 不触发 | `AppViewModel.kt:200-206`、`:309-317` |

### 1.4 备份与恢复

| 项 | 现状 | 证据 file:line |
|---|---|---|
| 格式版本 | `LEGACY=1 / V2=2 / SCHEMA_VERSION=3`；15MB、100 项目、10 万条上限 | `data/backup/BackupCodec.kt:138-143` |
| 版本接受范围 | `1..3`，否则拒绝 | `BackupCodec.kt:309-311` |
| V3 增量字段 | project mode/plannedSets/targetRepsPerSet；session 同上+sets；settings lastTime/lastReps；draft | `BackupCodec.kt:232-236,257-262,275-278,280-294` |
| 校验摘要 | 三档独立算法（V1/V2/V3），V3 字段集固定，禁止先补字段再校验 | `BackupCodec.kt:573-578,624-652` |
| 导入合并 | **只按 id 去重，冲突保留现有版本，不做字段级合并** | `BackupCodec.kt:563-571`、`BackupRepository.kt:102-138` |
| 覆盖恢复 | 写安全快照后整库清空重灌 | `BackupRepository.kt:140-187`、`AppViewModel.kt:1611-1663` |
| 自动备份 | SAF 树授权；写后回读再 `parse` 校验；只轮转 `zjyd-auto-*`，保留 7 份 | `AutoBackupManager.kt:68-89,204-215,290-298,310` |
| 备份触发去抖 | 1200ms 合并触发 | `AutoBackupManager.kt:54-66` |
| 导出/导入 UI | 设置页 4 行入口；CreateDocument/OpenDocument；预览页可选「合并/覆盖」 | `SettingsScreen.kt:141-173`、`ui/AppRoot.kt:55-115`、`Sheets.kt:834-879` |
| 备份管理 sheet | 自动备份开关、目录选择、列表、恢复入口 | `Sheets.kt:975-1156` |
| 导出时间戳 | `exportedAt` 用系统时区 ISO_OFFSET_DATE_TIME | `BackupCodec.kt:158`、`domain/Formatters.kt:36,44-45` |

### 1.5 设置页与入口位置

| 项 | 现状 | 证据 file:line |
|---|---|---|
| 技术栈 | Jetpack Compose（Material3），单页 `Column + verticalScroll` | `SettingsScreen.kt:32-62` |
| 分组顺序 | 训练偏好 → 外观 → 数据管理 → 关于 | `SettingsScreen.kt:65,110,131,175` |
| 数据管理组内容 | 备份与恢复（Sheet）、导出完整备份、导出历史表格、导入与恢复 | `SettingsScreen.kt:131-173`（入口行 `142-148`） |
| 弹层模型 | `sealed interface Sheet` 19 个分支，含 `BackupManager`/`ImportPreview`/`BackupRestorePreview` | `AppViewModel.kt:73-101` |
| 弹层分派 | 对话框分支 + ModalBottomSheet 分支 | `Sheets.kt:75-134`、`:165-182` |
| 页面挂载 | `Tab.SETTINGS -> SettingsScreen(...)`；`SheetsHost` 在 AppRoot 末尾 | `AppRoot.kt:206-232,245` |
| 设置持久化 | DataStore Preferences；现有 15 个 key，无同步相关 key | `data/prefs/SettingsRepository.kt:17,21-37,39-62` |

### 1.6 测试结构

| 项 | 现状 | 证据 file:line |
|---|---|---|
| JVM/Robolectric 单测 | 6 个文件：BackupCodec/BackupRepository/Migration/ProjectDao/Stats/TimerMath | `app/src/test/java/com/wanghoufan/zaijiuyidian/` |
| Robolectric | 4.14.1，`@Config(sdk=[34])`，依赖仓库走 maven central | `app/build.gradle.kts:42-49,85` |
| Room 测试 | `room-testing` 已引入；DAO 用 inMemoryDatabaseBuilder | `app/build.gradle.kts:87`、`ProjectDaoTest.kt:24-30` |
| 迁移测试 | **只测 v1→v2**，无 v2→v3 | `MigrationTest.kt:19-21,93-117` |
| Instrumented | `app/src/androidTest/` **目录存在但无任何文件** | `app/src/androidTest/` |
| 运行命令 | `./gradlew :app:testDebugUnitTest`（当前 32/32） | `docs/architecture-facts.md:17,55` |

---

## 2. 与计划 §3 的差距

计划 §3 原文见 `docs/plan/P068-V1.2-Cloud-Sync-Plan.md:39-52`。

| 计划要求 | 现状 | 差距 | 触达文件 |
|---|---|---|---|
| `projects.revision INTEGER NOT NULL DEFAULT 1` | 无该列（schema 2.json 已核） | **缺列**；无任何 revision 语义 | `Entities.kt:9-29`、`AppDatabase.kt:18` |
| `projects.deletedAt INTEGER` + 硬删改软删 | 无 deletedAt；`deleteById` 硬删 | **缺列 + 删除语义错**；归档（archivedAt）与删除必须分开 | `Daos.kt:34-35`、`AppViewModel.kt:846` |
| `sessions.revision INTEGER NOT NULL DEFAULT 1` | 无 revision；只有 updatedAt/deletedAt | **缺列**；updatedAt 是设备本地钟 | `Entities.kt:43-59` |
| `rep_sets.updatedAt INTEGER` | 无 | **缺列**；组级改动目前靠「整组删了重建」表达 | `Entities.kt:86-91`、`AppViewModel.kt:1425` |
| `rep_sets.deletedAt INTEGER` + 删除改软删 | 无；`deleteForSession` 硬删 | **缺列 + 语义错**；且与唯一索引 `(sessionId,ordinal)` 冲突 | `Daos.kt:91-92`、`Entities.kt:81-84` |
| 新表 `sync_outbox` | **不存在**；现有 5 表无 outbox | 新表 + DAO + 迁移建表 | `AppDatabase.kt:11-17` |
| 新表 `sync_state`（owner_uid / lastPullCursor / lastSyncAt / 设备标识） | **不存在**；本机只有 DataStore 偏好（15 个 key） | 新表 + DAO + 迁移；或明确改放 DataStore（与计划不一致需评审） | `SettingsRepository.kt:21-37` |
| `owner_uid` 云端每表归属 | 本地无任何归属/账号字段 | 无登录、无 uid、无 token 存储 | 全局缺失 |
| 不同步 `rep_draft` / `active_session` | 两表为单例本机态，符合计划 | **现状已满足**；风险是后续误把 outbox 观察扩到这两表 | `Entities.kt:93-131` |
| 「updatedAt 不作唯一游标，需服务端时间或 revision」 | 全部时间戳为 `System.currentTimeMillis()`（设备钟） | **无服务端时间、无 revision、无单调计数** | `Entities.kt:16-17,54-55`、`AppViewModel.kt` 全部写入点 |
| 写路径同事务写 outbox | 所有业务写入只写业务表 | **全局缺失**：约 20 处业务写点需接线 | `AppViewModel.kt:521…1908`、`BackupRepository.kt:106-172` |
| 上行幂等（mutationId） | 无 | 无 mutation 概念；导入去重只按 record id | `BackupCodec.kt:563-571` |
| 下行按游标增量 + revision 校验 | 无 | 无 pull 通道、无冲突表 | 全局缺失 |
| 墓碑保留 90 天 / 离线 30 天全量重算 | 无墓碑、无清理任务 | 无 | 全局缺失 |
| 触发：进前台 / 写后去抖 / 手动 | 只有备份触发（进前台 `onForeground` 仅恢复计时服务） | **同步触发缺失**；且无 WorkManager 依赖 | `AppViewModel.kt:200-206,1524-1550`、`MainActivity.kt:55` |
| 状态暴露在设置页 | 设置页无同步状态位 | 插入点明确（`数据管理` 组），但无 UI/状态模型 | `SettingsScreen.kt:131-173` |
| 未开启不同步时零网络请求 | 现状天然满足（无权限、无网络库） | 权限一旦新增，需靠「默认关闭 + 不初始化客户端」保住 AC01 | `AndroidManifest.xml:4-8` |

---

## 3. 最小改造清单（只列改动点，不写实现）

### 3.1 新增文件

| 文件 | 类型 | 内容 |
|---|---|---|
| `app/src/main/java/.../data/db/SyncEntities.kt` | 新增 | `sync_outbox`、`sync_state` 两个 Entity（列按计划 §3） |
| `app/src/main/java/.../data/db/SyncDaos.kt` | 新增 | OutboxDao（enqueue/pending/ack/fail）、SyncStateDao（get/put） |
| `app/src/main/java/.../data/sync/SyncTransport.kt` | 新增 | HTTP 客户端 + 请求/响应 JSON 编解码（需新增网络与序列化依赖） |
| `app/src/main/java/.../data/sync/SyncEngine.kt` | 新增 | push（幂等 mutation）/ pull（游标）/ 冲突落地 |
| `app/src/main/java/.../data/sync/SyncAuthRepository.kt` | 新增 | 登录、token 存取与失效处理（token 不得走明文 DataStore） |
| `app/src/main/java/.../data/sync/SyncTrigger.kt` | 新增 | 进前台 / 写后去抖 / 手动三入口的门控 |
| `app/src/main/java/.../ui/screens/CloudSyncSheet.kt` | 新增 | 云同步面板：登录、开关、首传预览、冲突列表、状态 |
| `app/src/test/java/.../SyncOutboxTest.kt` | 新增 | outbox 幂等、重试、ack 后删除 |
| `app/src/test/java/.../SyncMigrationTest.kt` | 新增 | v2→v3 迁移（含既有数据保留） |

### 3.2 修改文件

| 文件 | 类型 | 改动点 |
|---|---|---|
| `AndroidManifest.xml:4-8` | 修改 | 新增 `INTERNET`（如做网络状态判断再加 `ACCESS_NETWORK_STATE`）；不得顺手加存储/麦克风等无关权限 |
| `app/build.gradle.kts:62-95` | 修改 | 新增网络、JSON 序列化、token 加密存储依赖 |
| `app/build.gradle.kts:33-36` | 修改 | 新增 `buildConfigField`（同步 API 基址、`project_slug=p068_duration_tracker`），禁把密钥写进 BuildConfig |
| `Entities.kt:9-29` | 修改 | ProjectEntity 加 `revision`、`deletedAt` |
| `Entities.kt:43-59` | 修改 | SessionEntity 加 `revision` |
| `Entities.kt:86-91` | 修改 | RepSetEntity 加 `updatedAt`、`deletedAt` |
| `Daos.kt:11-39` | 修改 | ProjectDao：查询统一过滤 `deletedAt IS NULL`；`deleteById` 改软删；新增 revision 递增更新 |
| `Daos.kt:41-75` | 修改 | SessionDao：upsert 改 `UPDATE`/显式 upsert 语义（避免 REPLACE 级联）；软删/恢复同步递增 revision |
| `Daos.kt:77-96` | 修改 | RepSetDao：`deleteForSession` 改软删；查询过滤 `deletedAt` |
| `AppDatabase.kt:10-20` | 修改 | version=3；entities 加两新表 |
| `AppDatabase.kt:22-26` | 修改 | 新增 OutboxDao/SyncStateDao 访问器 |
| `AppDatabase.kt:29-73` 之后 | 修改 | 新增 `MIGRATION_2_3`（只加列/加表，全部 NOT NULL 带 DEFAULT） |
| `AppDatabase.kt:79-83` | 修改 | `addMigrations(MIGRATION_1_2, MIGRATION_2_3)`；**不得**改 destructive |
| `ui/AppViewModel.kt:73-101` | 修改 | Sheet 增加云同步/登录/冲突分支 |
| `ui/AppViewModel.kt:130-155` | 修改 | UiState 增加同步状态、待同步数、冲突数、上次同步时间 |
| `ui/AppViewModel.kt` 全部业务写点（约 20 处，见 §1.3 证据行） | 修改 | 同一 Room 事务内追加 outbox 记录（projects/sessions/rep_sets 三类） |
| `ui/AppViewModel.kt:838-852` | 修改 | 项目删除改软删（写墓碑），保留归档语义独立 |
| `ui/AppViewModel.kt:200-206` | 修改 | 观察者保留备份职责；同步徒发需单独去抖入口，不得复用备份 1200ms |
| `ui/AppViewModel.kt:1524-1550` | 修改 | 进前台门控里加「同步允许时」触发，失败不得阻塞 UI |
| `ui/screens/SettingsScreen.kt:131-173` | 修改 | 「数据管理」组内、`备份与恢复` 之前插入「云同步」行（状态文案 + 入口 Sheet） |
| `ui/screens/Sheets.kt:165-182` | 修改 | 增加新 Sheet 分派；对话框分支见 `:75-134` |
| `data/prefs/SettingsRepository.kt:21-37,64-130` | 修改 | 若开关/上次同步时间放 DataStore：新增 key 与 setter；token 不走此处 |
| `data/backup/BackupRepository.kt:140-179` | 修改 | 覆盖恢复后同步态处理（清 outbox / 写墓碑 / 重置游标），避免云端复活已删数据 |
| `data/backup/BackupCodec.kt`（评审，不建议改） | 修改约束 | **保持 V3 字段集与 checksum 算法不变**；同步元数据不得进入备份正文 |
| `README.md:15`、`README.en.md:15` | 修改 | 隐私口径改为「默认仅本机，云同步由用户主动开启」 |
| `docs/architecture-facts.md:48` | 修改 | 「无 INTERNET 权限＝结构保证」改为条件表述 |
| `docs/risks.md:20-23` | 修改 | R13 同步跟进（发布前必须同批改写） |

---

## 4. 风险与回归面

### 4.1 必须保住的三条既有能力

| 能力 | 现状证据 | 破面风险 | 保住方式（验收点） |
|---|---|---|---|
| 离线训练全链路 | 无网络权限；`MainActivity`/ViewModel 不依赖网络（`AndroidManifest.xml:4-8`、`AppViewModel.kt:854-1263`） | 初始化同步客户端时做网络请求或阻塞启动；写入路径 await 网络 | AC01：未开启同步时无任何网络请求；断网/飞行模式下训练、保存、历史全通 |
| V3 备份兼容 | `BackupCodec` V1/V2/V3 三算法（`BackupCodec.kt:573-652`）；V3 字段集固定 | 新增 revision/deletedAt 被写进导出或 checksum | AC09：新版本导出的 V3 文件仍能被旧算法校验；旧 V1/V2 文件仍能恢复；恢复后不产生重复 |
| v1→v2 迁移 | `MIGRATION_1_2` + `MigrationTest.kt:93-117` 覆盖 | 改 v1→v2 语句或删表重建；v2→v3 用 destructive | v2→v3 只加列加表，`NOT NULL DEFAULT`；既有 32 单测 + 新增迁移测试全绿 |

### 4.2 具体风险清单

| # | 风险 | 证据 | 说明 / 处置方向 |
|---|---|---|---|
| R1 | `sessions` 的 `INSERT OR REPLACE` 会瞬时删父行，触发 `rep_sets` **CASCADE 全删** | `Daos.kt:58-59`、`Entities.kt:72-80` | 现有唯一调用顺序（先 upsert 再重建 sets，`AppViewModel.kt:1424-1426`）侥幸正确；同步下行若复用 `upsert` 会静默丢组明细 → 下行必须 `UPDATE` 且带 revision 校验 |
| R2 | `projects` 的 `INSERT OR REPLACE` 在有历史时被 FK RESTRICT 拒绝 | `Daos.kt:25-26`、`ProjectDaoTest.kt:77-116` | 同步下行写 projects 不得用 REPLACE；只能 UPDATE/INSERT |
| R3 | `rep_sets` 唯一索引 `(sessionId, ordinal)` 与软删冲突 | `Entities.kt:81-84`、`AppDatabase.kt:61-64` | 软删后重新插入同 ordinal 会撞唯一索引 → 需改唯一键（如 `(id)` 或含 deletedAt 的复合），属 v3 迁移重点 |
| R4 | `rep_sets.id` 由 `sessionId + ordinal` 派生 | `AppViewModel.kt:1098,1416`、`BackupRepository.kt:112,152` | 两设备对同一 session 并行编辑，同 ordinal 会产出**相同 ID、不同内容** → 不能靠 ID 幂等，必须 revision + 冲突检测 |
| R5 | `sessionCount` 含软删记录 | `Daos.kt:22-23` | 项目墓碑后「无历史可删」判定会永远为假；需明确统计口径是否含墓碑 |
| R6 | 种子项目 / 项目选择依赖 `projects` 非空且未过滤删除 | `AppViewModel.kt:369-405,407-438,156-165` | 墓碑项目会让「冷启动无项目」判定失效；所有项目查询需加 `deletedAt IS NULL` |
| R7 | 覆盖恢复整库 `deleteAll` | `BackupRepository.kt:143-146` | 同步开启时恢复会绕过墓碑 → 云端把已删记录传回（AC06 破） |
| R8 | 自动备份由表变更触发 | `AppViewModel.kt:200-206` | 新增 outbox/sync_state 表若纳入观察，同步写入会放大 SAF 备份频率；需明确只观察业务表 |
| R9 | 时间戳全为设备钟 | `Entities.kt:16-17,54-55`、计划 §10 已指认 | 不能作游标；跨设备时钟偏斜会导致漏拉/重复拉 |
| R10 | 统计口径按 `deletedAt` 过滤 | `AppViewModel.kt:246-249`、`HistoryScreenV11.kt:147,153-161` | 墓碑后历史仍能显示「已删除项目」依赖 projects 行仍存在；若项目行被真删则丢失项目名 |
| R11 | 无签名配置，产物为 debug 签名 | `app/build.gradle.kts:21-26` | V1.2 若要分发/上架需另决签名与 release 构建；不属于同步代码，但会卡发布 |
| R12 | 无加密存储依赖，token 无处安放 | `app/build.gradle.kts:62-95` | DataStore Preferences 明文，不得存 access token；需 Keystore/EncryptedSharedPreferences 或自封装 |
| R13 | 隐私口径与「离线卖点」冲突 | `README.md:15`、`README.en.md:15`、`architecture-facts.md:48`、`risks.md:20-23` | 加权限必须同批改文案，未改完不得发布（计划 §10） |
| R14 | 设置页现有字号低于全局 UI 下限 | `SettingsScreen.kt:241,250`（13sp/11sp） | 既有偏差；新增云同步行若沿用会继续偏差，记录为既有问题，不在本次同步范围擅自改动 |

---

## 5. 建议验证命令（只列，不执行）

```bash
# 全量 JVM/Robolectric 单测（当前基线 32/32）
./gradlew :app:testDebugUnitTest

# 迁移专项：v1→v2 不得回归；新增 v2→v3
./gradlew :app:testDebugUnitTest --tests com.wanghoufan.zaijiuyidian.MigrationTest
./gradlew :app:testDebugUnitTest --tests com.wanghoufan.zaijiuyidian.SyncMigrationTest

# DAO 外键与 REPLACE 语义回归（projects 不可 REPLACE 删父行）
./gradlew :app:testDebugUnitTest --tests com.wanghoufan.zaijiuyidian.ProjectDaoTest

# 备份 V1/V2/V3 三档校验与合并幂等回归
./gradlew :app:testDebugUnitTest --tests com.wanghoufan.zaijiuyidian.BackupCodecTest
./gradlew :app:testDebugUnitTest --tests com.wanghoufan.zaijiuyidian.BackupRepositoryTest

# 编译与产物
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:lintDebug

# schema 变更核对（应只新增 3.json，2.json 不被改写）
git status --short app/schemas
git diff --stat app/schemas
python3 -c "import json;d=json.load(open('app/schemas/com.wanghoufan.zaijiuyidian.data.db.AppDatabase/3.json'));print(d['database']['version'],[e['tableName'] for e in d['database']['entities']])"

# 迁移前后数据保真（Robolectric 已覆盖，如需真机手工核对）
adb shell run-as com.wanghoufan.zaijiuyidian ls databases
```

> 说明：`app/src/androidTest/` 当前为空，`connectedDebugAndroidTest` 暂无既有用例可复跑；新增 instrumented 用例前不应把它列入验收命令。