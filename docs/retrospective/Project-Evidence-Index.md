# P068 再久一点｜项目证据索引

> 用途：为 `Project-Lessons-V1.0.md` 的每条结论提供可重定位出处，并如实标注「已验证 / 推断 / 无法证实」。
> 只记路径、提交号与事实摘要，不复制代码正文，不复制任何密钥或凭据内容。

## 0. 项目标识与复盘基线

| 项 | 值 |
|---|---|
| 项目 ID / 名称 | P068 / ing-再久一点 |
| 项目根 | `/Users/zzymima0000/Developer/coding/1.Active/068-ing-再久一点` |
| Git 基线 | `f2178ca`，6 个提交，首提交 2026-11-05，末提交 2026-11-06 |
| 远端 | `origin/main` = `f2178ca`；ahead/behind = 0/0（已推送） |
| 工作树 | 4 项（`docs/retrospective/` 本轮新增 + 3 项原有未跟踪） |
| 复盘执行日 | 2026-11-08 |
| 取证方式 | 只读：`git log` / `git show --stat` / `git status` / `git rev-list` / `ls` / `wc` / `shasum`。未建分支、未 commit、未 push、未改业务文件 |

## 1. 需求与计划类原始文档

| 文件 | 用途 | 结论引用 |
|---|---|---|
| `docs/plan/P068-Android-Development-Plan-V1.2.md` | V1.0 的原始开发计划 | §2 时间线；§5 前置发现 |
| `docs/plan/P068-Android-Dual-Mode-Training-V1.1-Approved-Plan.md` | V1.1 双模式训练获批计划（第一版） | §2 时间线 |
| `docs/plan/P068-Android-Dual-Mode-Training-V1.1-R2-Final-Approved-Plan.md` | V1.1 双模式**终版**获批计划（存在 R2 说明 V1.1 曾被打回一版） | §3 L1；§4 演化 |
| `docs/plan/P068-Android-Reliability-And-Acceptance-V1.0.1-R2.md` | V1.0.1 可靠性与验收计划 | §2 时间线 |
| `docs/plan/P068-Approved-Interactive-Prototype-V1.0.html` | V1.0 获批交互原型 | §6 UI 经验 |
| `docs/plan/P068-Dual-Mode-Full-History-Interactive-Prototype-V1.1-R2.html` | V1.1 R2 双模式全历史交互原型 | §6 UI 经验 |
| `docs/pm/` | 仅模板（`*.template.md`） | §1 证据缺口 |

## 2. 验收、QA 与审计类

| 文件 | 证明什么 | 结论引用 |
|---|---|---|
| `docs/qa/QA-REPORT-P068-V1.0.md` | V1.0 首轮 QA | §1 当前状态 |
| `docs/qa/QA-REPORT-P068-V1.0.1-R2.md` | V1.0.1 R2 复验 | §2 时间线 |
| `docs/qa/QA-REPORT-P068-V1.1-R2.md` | V1.1 R2 复验（双模式/计次/三图/V3 备份） | §2 时间线；§3 L1 |
| `docs/qa/产品验收追踪矩阵.md`、`-P068-V1.0.md`、`-P068-V1.0.1-R2.md`、`-P068-V1.1-R2.md` | 四份 AC 矩阵，可看出 AC 范围随版本扩张 | §3 L1；§5 |
| `docs/qa/evidence/`（60 个文件） | 第一轮验收截图与日志 | §1 证据缺口 1 |
| `docs/qa/evidence-r2/`（17 个文件） | V1.0.1 R2 复验证据 | §2 |
| `docs/qa/evidence-v11/`（19 个文件） | V1.1 R2 复验证据 | §2；§6 |
| `docs/qa/BUGS.template.md` | 仅模板，实际缺陷未落独立清单 | §3 L3 |

**实测合计：60 + 17 + 19 = 96 个证据文件**（`ls docs/qa/evidence*/ | wc -l`）。
A 路 V1.0 记「41 项」、B 路 V1.0 记「62 个」，均与实测不符，以 96 为准（见 §6 缺口 2）。

## 3. 事实、风险、交接类

| 文件 | 关键内容 | 结论引用 |
|---|---|---|
| `docs/handoff/HANDOFF.md` | V1.0→V1.1 两段交接，记三机推送与 S4 沉淀 | §2；§3 L4 |
| `docs/architecture-facts.md` | 架构与构建事实（Kotlin/Compose + Room v2 + SAF 备份 + 前台服务） | §7 |
| `docs/risks.md` | 风险清单 | §3 L2 |
| `agent.md` | 踩坑表 | §3 L2；§6 |
| `docs/history/`、`docs/review/`、`docs/sop/`、`docs/roles/` | 过程与治理材料 | 旁证 |

**排除项**：仓库内无凭据类文件；`dist/*.apk` 为可安装产物，只登记 SHA-256，不分发。

## 4. Git 提交证据表（`git log --format='%h|%ad|%s' --date=short` 实测）

| 提交 | 日期 | 主题 | 变更规模 |
|---|---|---|---|
| `19034b0` | 2026-11-05 | chore: bootstrap 再久一点 (P068) | 3 文件 +64 |
| `23232a4` | 2026-11-06 | feat: P068 再久一点 Android V1.0（计时/历史/统计/备份/语音） | **159 文件 +8,308 / −4** |
| `d992f36` | 2026-11-06 | docs: 更新 HANDOFF（三机推送、S4 沉淀、收尾） | 1 文件 +2/−1 |
| `3109039` | 2026-11-06 | chore: 同步 ORCA 公开仓忽略块 | 1 文件 +49 |
| `693f685` | 2026-11-06 | feat: P068 V1.1 双模式与完整历史（四 Tab/计次/三图/V3 备份） | **80 文件 +7,440 / −305** |
| `f2178ca` | 2026-11-06 | docs: 回填 V1.1 提交与推送状态（HEAD） | 4 文件 +6/−6 |

**观察**：V1.0 与 V1.1 各为单提交巨块（159 文件 / 80 文件），中间没有可读的增量提交。
过程决策只能从 `docs/plan/`、`docs/qa/` 与 `HANDOFF.md` 还原，Git 本身不提供细粒度演化轨迹（→ §3 L3）。

## 5. 交付物与产物证据

| 产物 | 字节 | SHA-256 |
|---|---|---|
| `dist/P068-zai-jiu-yi-dian-v1.1.0-debug.apk` | 16,033,544 | `3d81a707df8473a1e54365c7a815cc4ff359602c0a1812bc5e78a481d1055919` |
| `dist/P068-zai-jiu-yi-dian-v1.0.1-debug.apk` | — | `8b546d712113defb64660dd63ae03d49bd17a8e75d2b0d3c72342b6c2eb4e908` |
| `dist/P068-zai-jiu-yi-dian-v1.0.0-debug.apk` | — | `15cc5d3e133e256b7c0dcff38d55e0644e9e524e3ea74649ca0d17f93e9f6e5c` |
| `dist/SHA256SUMS.txt` | — | V1.1 哈希与 `shasum -a 256` 实测一致 |

**纠正 B 路 V1.0**：B 称「Glob 未在 `dist/` 匹配到文件」，实测存在 3 个 APK + `SHA256SUMS.txt`。
B 其后自行撤回该断言，本索引按实测记录；同时保留 B 的自我约束「不得据读不到断言不存在」。

`app/src/main/res/raw/*.wav` = **36 个**（离线语音提示音资源）。

## 6. 代码级实测

| 位置 | 实测内容 | 结论引用 |
|---|---|---|
| `ui/theme/Theme.kt:77-79` | `bodyLarge 15.sp` / `bodyMedium 13.sp` / `bodySmall 11.sp` / `titleLarge 25.sp` / `titleMedium 19.sp` / `labelLarge 14.sp` | §6 UI 经验；§5 前置发现 |
| `domain/Models.kt:106` | `enum class ThemeMode { LIGHT, DARK }`——**缺 SYSTEM 档** | §6；§5 前置发现 |

**判定**：字号 `13.sp` / `11.sp` 低于用户全局下限（正文不小于 15px、任何文本不小于 14px），主题缺 `SYSTEM` 与「默认 SYSTEM」要求不符。
两项均为**获批偏差**：用户在 V1.0/V1.1 计划获批时未提出异议，故记入「计划者应前置发现」而非「执行失误」（→ §5、§10 责任划分）。

## 7. 本轮复盘产物

| 文件 | 字节 | 说明 |
|---|---|---|
| `A-Day1-Reconstruction-V1.0.md` | 56,662 | 第一轮 A 路独立基线，不得覆盖 |
| `B-Gap-RootCause-V1.0.md` | 29,424 | 第一轮 B 路独立基线，不得覆盖 |
| `A-Day1-Reconstruction-V1.1.md` | 55,991 | 第二轮 A 路（读 B V1.0 后修订） |
| `B-Gap-RootCause-V1.1.md` | 42,321 | 第二轮 B 路（读 A V1.0 后修订，反向纠正 A 7 处引用错误） |
| `Project-Lessons-V1.0.md` | 19,228 | 主开发者终审结论 |
| `Project-Evidence-Index.md` | 8,770 | 本文件（证据索引） |
| **合计** | **212,396** | 六件齐 |

字符数（`wc -m`）：A V1.0 30,210 / A V1.1 28,609 / B V1.0 13,428 / B V1.1 20,570 / Lessons 9,625 / Evidence 6,039。

字数一律以 `wc -c` 实测为准，不采用子代理自报值。

## 8. 证据缺口与待补验

| # | 缺口 | 影响 | 现状 |
|---|---|---|---|
| 1 | 验收证据 96 个文件无索引清单 | 无法把某条 AC 定位到具体截图 | 只有目录粒度，未逐条映射 AC 编号 |
| 2 | A/B 两路证据计数均错（41 / 62，实测 96） | 若采信会低估验收覆盖度 | **已纠正**，以 `ls`+`wc` 实测 96 为准 |
| 3 | B 路 Bash 沙箱拒绝 `git show`/`git status` | B 对 Git 历史的判断属推断 | 6 个提交全部由主开发者实测补齐，本索引 §4 为实测值 |
| 4 | V1.0 → V1.1 无增量提交 | 「计次缺席为何波及四层」只能从文档与代码现状反推 | 结论标为**推断**，未当作既成事实 |
| 5 | 用户验收现场的口头反馈无留档 | §3 L5「反馈节奏验收现场才补」依据的是 `HANDOFF.md` 转述 | 记为**弱证据**，仅作方向性结论 |
| 6 | 三台机型的推送记录只在 `HANDOFF.md` 文字中 | 不能从 Git 复核「三机」事实 | 保留缺口，不作量化结论 |
| 7 | 主题/字号偏差是否经用户显式同意 | 责任归属不同 | 记为**获批偏差**（计划获批时未异议），不归为执行失误 |

## 9. 复核方式（只读）

```bash
cd "/Users/zzymima0000/Developer/coding/1.Active/068-ing-再久一点"
git log --oneline | wc -l                              # 6
git rev-parse --short origin/main                      # f2178ca
git rev-list --left-right --count origin/main...HEAD   # 0  0
git log --shortstat --format='%h %s'                   # §4 变更规模
ls docs/qa/evidence | wc -l                            # 60
ls docs/qa/evidence-r2 | wc -l                         # 17
ls docs/qa/evidence-v11 | wc -l                        # 19
ls app/src/main/res/raw/*.wav | wc -l                  # 36
shasum -a 256 dist/P068-zai-jiu-yi-dian-v1.1.0-debug.apk
wc -c docs/retrospective/*.md
```
