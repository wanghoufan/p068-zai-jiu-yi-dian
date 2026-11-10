# HANDOFF｜交接（暂停/恢复用，先读我）

> 上限 ≤100 行 / ≤8KB。事实进 `docs/architecture-facts.md`，风险进 `docs/risks.md`，踩坑进 `agent.md`，本文件只留指针与现势。

## §0 常驻指针

| 要什么 | 去哪 |
|---|---|
| 开工路由、红线、索引 | `AGENTS.md`（唯一真源） |
| 架构与构建事实（含云端对象） | `docs/architecture-facts.md` |
| 未决风险与待验证项 | `docs/risks.md`（R13 已处置、R14 已修并实测） |
| 踩坑与审查交付约定 | `agent.md` |
| 经验条目 | `经验一句话.md` |
| V1.2 云同步计划 | `docs/plan/P068-V1.2-Cloud-Sync-Plan.md` |
| V1.2 验收矩阵 | `docs/qa/产品验收追踪矩阵-P068-V1.2.md` |
| 共享云环境登记簿 | `docs/plan/共享云环境登记簿-P068.md` |
| 治理细则 / 角色卡 | `docs/sop/`、`docs/roles/` |

## §1 当前进展

- Captured at：2026-11-10
- PROJECT_PHASE：DEVELOP（V1.2 云同步主体已交付，后台提醒已修并验收，待补测与签收）
- DEV_BASELINE：V1.1-R2 已批准计划 + V1.2 云同步计划（APPROVED_FOR_EXECUTION）
- 执行方式：主开发者模式（用户口令「你是主开发者·连续推进」），未派治理角色链
- 已完成：
  - 云端 `p059-prompter`（PG，上海，体验版 3000 点/月，2027-04-25 到期，当前 26 点）：已建 `p068_projects` / `p068_sessions`，8 条 RLS，`server_updated_at` 触发器；REST 12/12 PASS（A/B 隔离、伪造 owner 403、revision 冲突 0 行、墓碑、触发器）。
  - 登录：仅用户名 + 密码（CloudBase 身份认证，账号 `zjyd`）；手机/邮箱/微信/匿名未启用；密码不入库、不落文档。
  - 客户端：Room v2→v3（`projects/sessions.revision`、`projects.deletedAt`、`rep_sets.updatedAt/deletedAt`、`sync_outbox`、`sync_state`）；Outbox、条件更新、增量拉取、设置页云同步入口；单测 39/39。
  - 真机：AC02/04/05/06/07/11 PASS；AC01/03/08/09/10 PARTIAL；AC12 OPEN。
  - 冲突：两机同改同一记录实测——B 被 0 行更新拦下、云端未覆盖，冲突两版落 `sync_state`，UI 提供「用本机/云端覆盖」；本机覆盖路径实测通过（rev4），A 已拉齐。
  - README 中英两份已同批改写为「默认只在本机，主动登录并确认首传后才联网」；R13 移交收口。
  - 后台提醒（R14）已修复并真机验收：高优先级渠道 `training_alert`（importance=4、系统闹钟铃声、`USAGE_ALARM`）＋独立通知 6802（category=alarm）、提示音改闹钟流、3 次长震、精确闹钟 `RTC_WAKEUP` 兜底、结束取消；到点只发一次（2 秒去重），常驻通知只在状态变化时更新。Note 11 5G（媒体音量 0、闹钟音量 10、后台 1 分钟）通过，证据 `docs/qa/evidence-v12/notification-alert-2026-11-10.txt`。
  - APK：`dist/P068-zai-jiu-yi-dian-v1.2.0-debug.apk`，17,010,870 字节，SHA-256 `0d22afbd144e06b2b9afa3e23f9ffe44b69286d774a4023053c5494d68fc0811`，versionCode 4 / 1.2.0；两机安装包哈希一致，B 为合并本地数据后拉取，不是覆盖。

## §2 下一步

- Next Single Action：补测剩余 5 项 PARTIAL（AC01 抓包、AC03 幂等重放、AC08 过期 token、AC09 1.2.0 真机 V3 恢复共存、AC10 额度巡检），证据落 `docs/qa/evidence-v12/`。
- 之后：M5 成本阈值（50/80/95% 人工巡检节奏）落登记簿，再提请 AC12 用户签收。
- 人要拍板：
  1. 是否签收 V1.2（AC12）；签收前需接受上述 PARTIAL 或等待补测。

## §3 未完成 / 剩 P0

- **AC12 用户签收 OPEN**（首次发布前必须停，不代签）。
- **PARTIAL 五项**：AC01（未抓包）、AC03（幂等未单独重放）、AC08（App 端过期 token 未测）、AC09（1.2.0 真机 V3 恢复未重跑）、AC10（无自动告警，需人工巡检）。
- **后台提醒边界**：R14 已修并真机验收（`docs/qa/evidence-v12/notification-alert-2026-11-10.txt`）；勿扰与 OEM 省电仍为系统限制，渠道不能自行 `bypassDnd`，勿扰开启下的通过结论未取得。
- 跨 APP 隔离未证明：两机同账号只证明用户隔离，不证明应用隔离；正式上线或第二个应用写 PG 前复评。
- V1.1-R2 遗留：AC02/12/13/14 PARTIAL、AC15 签收 OPEN（旧矩阵 `docs/qa/产品验收追踪矩阵-P068-V1.1-R2.md`）。
- 未闭环评审：主开发者模式无独立 reviewer/qa 角色链，自审与真机证据已落 `docs/qa/`。
- Git：本轮文档改动未提交（无 commit/push 授权）。

## §4 红线

- 不 push；commit 需明确授权（含分支名）。
- 产品验收未落盘或关键 AC 未测、首次发布未签收，不得报完工。
- 体系更新未同步两包与概览、或门禁未过，不得收工。
- 不删历史（只迁移/归档）；不替用户判定文件无用。

## §5 三日流水

- 2026-11-06：V1.1-R2 四 Tab、计次、三图、Room v1→v2、V3 备份；32/32；APK 1.1.0；AC02/12/13/14 部分与签收待闭环。
- 2026-11-10：V1.2 云同步立项 → 复用 P059 → 建表/RLS/触发器 → 客户端 Room v3 + Outbox → 两机验收 → APK 1.2.0；README/架构事实/矩阵/风险/登记簿同批收口。
- 2026-11-10：文档收口（本轮）——README 中英、`architecture-facts.md`、V1.2 矩阵、本文件、云同步计划、登记簿、`risks.md` 同步更新；未改代码、未构建、未联网。
- 2026-11-10：冲突处理补齐并实测——`sync_state` 持久化冲突两版，云同步 sheet 提供「用本机/云端覆盖」；两机同改实测 B 不覆盖云端、本机覆盖后 A 拉齐（AC05 PASS）。
- 2026-11-10：后台提醒（R14）修复并真机验收——`training_alert` 高优先级渠道 + 通知 6802、闹钟流提示音、3 次长震、`RTC_WAKEUP` 精确闹钟兜底；到点只发一次（2 秒去重），常驻通知仅状态变化时更新；APK 重新出包 17,010,870 字节 / `0d22afbd…`。勿扰与 OEM 省电仍为系统限制。

## §6 接续提示词

先读本文件 §0–§3 与 `docs/qa/产品验收追踪矩阵-P068-V1.2.md`。云同步主体已交付（APK 1.2.0，两机哈希一致），后台提醒 R14 已修并验收；未完成的是 5 项 PARTIAL 补测与 AC12 签收。不得把 PARTIAL 写成 PASS，不得自行宣布完工或发布；commit/push 需用户明确指令。