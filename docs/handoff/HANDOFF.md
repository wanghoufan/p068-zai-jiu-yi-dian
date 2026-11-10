# HANDOFF｜交接（暂停/恢复用，先读我）

> 上限 ≤100 行 / ≤8KB。事实进 `docs/architecture-facts.md`，风险进 `docs/risks.md`，踩坑进 `agent.md`，本文件只留指针与现势。

## §0 常驻指针

| 要什么 | 去哪 |
|---|---|
| 开工路由、红线、索引 | `AGENTS.md`（唯一真源） |
| 架构与构建事实（含云端对象） | `docs/architecture-facts.md` |
| 未决风险与待验证项 | `docs/risks.md`（R14 已修实测、R15/R16 未定位、R17 待决） |
| 踩坑与审查交付约定 | `agent.md` |
| 经验条目 | `经验一句话.md` |
| V1.2 云同步计划 | `docs/plan/P068-V1.2-Cloud-Sync-Plan.md` |
| V1.2 验收矩阵 | `docs/qa/产品验收追踪矩阵-P068-V1.2.md` |
| 共享云环境登记簿（唯一真源） | `026-数据库管家/台账/共享环境登记簿.md`（本仓同名文件仅历史快照） |
| 治理细则 / 角色卡 | `docs/sop/`、`docs/roles/` |

## §1 当前进展

- Captured at：2026-11-10｜PROJECT_PHASE：DEVELOP（V1.2 主体已交付，待补测与签收）
- 执行方式：主开发者模式（口令「你是主开发者」），未派治理角色链。
- 云端 `p059-prompter`（PG 上海，体验版 3000 点/月，2027-04-25 到期）：`p068_projects`/`p068_sessions`、8 条 RLS、`server_updated_at` 触发器；REST 12/12 PASS。
- 登录：仅用户名+密码（CloudBase，账号 `zjyd`）；密码不入库不落文档。
- 客户端：Room v2→v3（revision/deletedAt/updatedAt、`sync_outbox`、`sync_state`）、Outbox、条件更新、增量拉取；单测 39/39。
- 真机：AC02/04/05/06/07/11 PASS；AC01/03/08/09/10 PARTIAL；AC12 OPEN。
- 冲突：两机同改实测——B 被 0 行更新拦下、云端未覆盖；两版落 `sync_state`，UI 可「用本机/云端覆盖」，本机覆盖路径已验（AC05 PASS）。
- 后台提醒 R14：高优先级渠道 `training_alert`＋通知 6802＋闹钟流＋`RTC_WAKEUP` 兜底，三态验收通过（`docs/qa/evidence-v12/R14-原始证据/INDEX-R14三态验收.md`）。
- APK：`dist/P068-zai-jiu-yi-dian-v1.2.5-debug.apk`，16,746,908 字节，versionCode 9 / 1.2.5（以 `output-metadata.json` 现读为准）；三台装机 base.apk SHA-256 均为 `1c86cbdd75c1…97c0`。
- 推送：1.2.5(9) 已覆盖安装 3 台（Note 11 5G、Note 12 Pro、OPPO PLG110），`firstInstallTime` 未变、数据条数前后一致；commit `da1352e`（未 push）；台账已登记但设备列仍两台（R17）。
- 三页标签化：`ProjectTabs`/`TabItem` 共享组件，时长/次数/历史均按 v2 样张铺开（≤4 居中取宽 / >4 横向滑动）；**用户未对样张表态，不得记为已验收**。
- 云端基准：OPPO 唯一（本机侧 3 项目/4 记录已核，云端待有凭据复核）；另两台已清空训练数据并退出同步，不要再触发首传。

## §2 下一步

- Next Single Action：补测 5 项 PARTIAL（AC01 抓包、AC03 幂等重放、AC08 过期 token、AC09 V3 恢复共存、AC10 额度巡检），证据落 `docs/qa/evidence-v12/`。
- OPPO PLG110 云同步登录已过期（R16）：已退出登录并停在登录表单，等用户手输 `zjyd`+密码后验证同步恢复。
- 人要拍板：① AC12 是否签收 ② 台账设备列补记方式（R17） ③ 三页标签样张是否批准。

## §3 未完成 / 剩 P0

- **AC12 用户签收 OPEN**（首次发布前必停，不代签）。用户口头「验收通过」只针对本轮推送与三页外观，不等于 V1.2 签收。
- **PARTIAL 五项**：AC01（未抓包）、AC03（幂等未单独重放）、AC08（App 端过期 token 未测）、AC09（V3 恢复未重跑）、AC10（无自动告警）。
- **R15**：锁屏不显示到点通知（D2），未定位，待用户确认 HyperOS 每应用锁屏通知开关。
- **R16**：多设备同账号登录态可能互相失效，未定位；未证实前不得写成应用缺陷。
- 跨 APP 隔离未证明：两机同账号只证明用户隔离，不证明应用隔离。
- V1.1-R2 遗留：AC02/12/13/14 PARTIAL、AC15 签收 OPEN（旧矩阵）。
- Git：`da1352e` 已本地提交（未 push）；`input-bugs/`、两本账、`USER_MODEL_OVERRIDE.md.旧版-2026-11-05` 未入库。

## §4 红线

- 不 push；commit 需明确授权（含分支名）。
- 产品验收未落盘或关键 AC 未测、首次发布未签收，不得报完工。
- 体系更新未同步两包与概览、或门禁未过，不得收工。
- 不删历史（只迁移/归档）；不替用户判定文件无用。

## §5 三日流水

- 2026-11-10：云同步立项→复用 P059→建表/RLS→Room v3+Outbox→两机验收→APK 1.2.0；README/架构事实/矩阵/风险/登记簿同批收口。
- 2026-11-10：冲突处理补齐并实测；R14 后台提醒修复并三态验收（去重窗改 60 s 后复测只响一次）。
- 2026-11-10：三机推送 1.2.0(4) 并登记；云端按用户要求重置为 OPPO 唯一基准，两台测试机清空退出同步。
- 2026-11-10：三页标签化（`ProjectTabs`）落 1.2.5(9)；补推三台并 SHA 认包，Note 11 释放交 027；OPPO 登录过期诊断（R16）；踩坑 6 条进 `agent.md`。

## §6 接续提示词

先读本文件 §0–§3 与 `docs/qa/产品验收追踪矩阵-P068-V1.2.md`。云同步与后台提醒已交付，只剩 5 项 PARTIAL 补测与 AC12 签收；OPPO 云同步待用户手输密码。不得把 PARTIAL 写成 PASS，不得自行宣布完工或发布，commit/push 需用户明确指令。
