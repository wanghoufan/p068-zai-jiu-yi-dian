# QA 报告｜再久一点（P068）V1.1-R2

- 日期：2026-11-06
- 计划/原型：`docs/plan/P068-Android-Dual-Mode-Training-V1.1-R2-Final-Approved-Plan.md`、`docs/plan/P068-Dual-Mode-Full-History-Interactive-Prototype-V1.1-R2.html`
- 版本：1.1.0（versionCode 3），DEBUG 测试签名
- APK：`dist/P068-zai-jiu-yi-dian-v1.1.0-debug.apk`（16,033,544 字节）
- SHA-256：`3d81a707df8473a1e54365c7a815cc4ff359602c0a1812bc5e78a481d1055919`
- 自动测试：32/32 通过
- 验收矩阵：`docs/qa/产品验收追踪矩阵-P068-V1.1-R2.md`
- 已提交 `693f685` 并推送 `origin/main`；未发布；用户签收 OPEN。

## 1. 本轮完成

| 任务 | 结果 |
|---|---|
| V11R2-T01 现状保护 | 保留 R2 未提交源码/设置；两台旧库先拉取备份；`install -r` 完成 v1→v2 迁移 |
| V11R2-T02 四 Tab | 底部改为时长/次数/历史/设置；TIME 保留原倒计时与后台服务，跨 Tab 不停止 |
| V11R2-T03 Room 数据层 | Project/Session 增加模式与计次计划；新增 rep_sets、rep_draft；显式 MIGRATION_1_2 |
| V11R2-T04 计次训练 | 项目创建、3×10/4×12、逐组实际次数、修改/撤销/超计划追加、提前结束、备注、进程死亡草稿恢复 |
| V11R2-T05 完整历史 | 三图切换、计次 KPI/关系表、项目/时间/搜索筛选、补录/逐组编辑/软删/撤销/恢复、混合口径 |
| V11R2-T06 设置与 V3 | 设置按项目类型显示时长预设或计次默认；V3 JSON/CSV、V1/V2 兼容、合并/覆盖、安全点、重复导入不翻倍 |
| V11R2-T07 UI/UX | 浅色默认、145% 字体、四 Tab、计次主按钮、历史图表/表格；输入框与键盘可用 |
| V11R2-T08 测试/真机 | 32 项自动测试；Note 11 主路径、Note 12 Pro 迁移/四 Tab；12T NOT_RUN |
| V11R2-T09 交付 | 1.1.0 APK、哈希、证据、README/架构/HANDOFF/矩阵/报告 |

## 2. 本轮发现并修复的缺陷

1. **V3 覆盖恢复后计次 KPI 立即变 0（P0）**：`applyRestoredSettings` 只回填 sessions、未带 rep sets，手动覆盖了 Room Flow 的结果；重启后才恢复。修复：恢复时同时读取 rep_sets 并按 session 组装。真机重复覆盖恢复后 KPI 立即恢复 3/40/10/4。
2. **修改当次计划后“下一组建议次数”不更新**：`NextSetCard` 只在组数变化时重置输入。修复：把 `nextReps` 纳入 remember key；最终 APK 实测改 4×12 后输入框变为 12。
3. **计次记录可被计时编辑器误转成 TIME**：补充 existing 模式守卫；计次记录只能逐组编辑。
4. 历史/图例文案修正：周平均次数、平均次数表头。

## 3. 真机证据

| 证据 | 内容 |
|---|---|
| `evidence-v11/01-four-tabs-time.png`、`00-note12pro-final-four-tabs.png` | 两台手机四 Tab |
| `evidence-v11/02-reps-idle.png` | 计次首页：计划 3×10、完成组数/总次数/最佳单组、唯一主按钮 |
| `evidence-v11/03-reps-draft-28.png` | 正在进行 4/3 组、总 38、草稿已保存 |
| `evidence-v11/04-history-reps-trend.png` | 每次总次数趋势 + 目标标记 |
| `evidence-v11/05-history-reps-weekly.png` | 周频率 + 周平均次数 |
| `evidence-v11/06-history-reps-scatter-top.png`、`07-history-reps-relation-table.png` | 频率关系散点与关系数据表 |
| `evidence-v11/08-history-time.png`、`09-history-mixed.png` | 时长统计、全部项目只合并次数/天数 |
| `evidence-v11/10-restore-v3-immediate.png` | V3 覆盖恢复后即时 KPI（3/40/10/4） |
| `evidence-v11/11-reps-font-145.png`、`12-history-font-145.png` | 145% 字体无重叠 |
| `evidence-v11/13-backup-v3-sample.json`、`13-backup-v3-summary.txt` | V3：2 项目/4 记录/3 计次/9 组明细 |
| `evidence-v11/14-plan-change-and-acceptance.txt` | 计划 4×12、提前结束、超计划、历史编辑、删除恢复、V3 恢复与合并的逐项结果 |

## 4. 未验证 / 已知限制

- V1.1 APK 的锁屏 3 分钟语音未重跑；服务代码未改，沿用 R2 证据。
- V3 自动备份 7 版轮转、目录授权撤销、空间不足/写失败未重跑；手动 V3 导出/覆盖/合并已验。
- 深色与窄屏未在 V1.1 APK 重跑；145% 字体已验。R2 深色证据仍有效。
- Note 12T Pro 已连接：安装/哈希/启动/v1→v2 迁移通过，完整双模式/历史链路 NOT_RUN；Note 12 Pro 完成安装/迁移/四 Tab，未跑完整计次链路。
- 当前仍为 DEBUG 签名；未提交 Git、未发布；V11R2-AC15 用户签收未完成。
- Note 11 上保留了本轮合成 QA 项目/记录（PullUp）与一份原始“练习 A”项目；未擅自清除用户数据。
