# QA 报告｜再久一点（P068）V1.0.1-R2

- 日期：2026-11-06
- 计划：`docs/plan/P068-Android-Reliability-And-Acceptance-V1.0.1-R2.md`
- 版本：1.0.1（versionCode 2），DEBUG 测试签名
- APK：`dist/P068-zai-jiu-yi-dian-v1.0.1-debug.apk`（15,701,351 字节）
- SHA-256：`8b546d712113defb64660dd63ae03d49bd17a8e75d2b0d3c72342b6c2eb4e908`
- 签名：Android Debug，证书 SHA-256 `8e82a7975008e8ec87ff0f2ecadf495b5263effa34aaec9ab6f135ebea911cf3`
- 自动测试：25/25 通过
- 验收矩阵：`docs/qa/产品验收追踪矩阵-P068-V1.0.1-R2.md`

## 1. 本轮完成

| 任务 | 结果 |
|---|---|
| R2-T01 备份 V2 | V2 写入 voice/font/range/chart/filter；V1 仍用原 SHA-256 算法校验；V1 fixture 与 V2 round trip 单测通过 |
| R2-T02 自动本地备份 | SAF 目录持久授权；数据变动 1.2s 合并；写后回读校验；只保留 7 个自动版本；状态/错误可见；安全点与手动文件不参与轮转 |
| R2-T03 可见备份列表与回滚 | 设置 → 备份与恢复：状态、最近成功/失败、自动/保护点列表、预览；恢复前写并校验保护点，再原子替换；恢复后数据可再次回滚 |
| R2-T04 锁屏/后台语音 | `specialUse` 前台服务 + 通知；沿用本地 WAV/SoundPool；按有效时长 3/6/9… 与目标播报；暂停冻结、服务停止、节点去重；通知拒绝时如实提示 |
| R2-T05 长时训练 | 25:53 真实训练，暂停 30 秒，锁屏/后台，超时继续；服务节点日志与备份元数据一致 |
| R2-T06 三机 | Note 12 Pro 完整；Note 11 5G 关键场景（语音/自动备份/列表）；Note 12T Pro 已安装 1.0.1 并完成锁屏语音，SAF/恢复 NOT_RUN |
| R2-T07 交付 | V1.0.1 APK、哈希/签名、升级保留数据、README/HANDOFF/QA 更新；未提交/未推送 |

## 2. 本轮发现并修复的缺陷

1. **恢复操作死锁（P0）**：`withRestoreLock` 持有不可重入 Mutex，内部 `writeSafetySnapshot` 再次加锁，恢复永远等待且不报错。修复：保护点写入改为“调用方必须已持锁”，并在真机完成恢复与保护点校验。
2. **恢复后项目列表短暂为空**：恢复事务后依赖 Room Flow 回填，UI 出现“暂无项目/目标 0”。修复：恢复设置时同步回填项目、记录与删除列表。
3. **通知权限拒绝时仍承诺锁屏提醒**：开始提示会误报。修复：检测 POST_NOTIFICATIONS，拒绝时提示“锁屏提醒可能不可用”。
4. **前台服务启动失败无兜底**：`startForeground` 异常可能结束进程。修复：捕获异常、停止服务，保留前台播报降级。

## 3. 真机证据

| 证据 | 内容 |
|---|---|
| `evidence-r2/01-long-session-25m53s.png` | 25.9 分钟记录、100% 达标 |
| `evidence-r2/02-v1-import-preview.png` / `03-v1-merged.png` | V1 备份预览与合并成功 |
| `evidence-r2/04-rollback-result.png` | 从 28 条版本回滚到 1 条版本后的历史 |
| `evidence-r2/05-notification-denied.png` | 通知拒绝后的如实提示 |
| `evidence-r2/06-backup-list.png` | 自动备份列表与状态 |
| `evidence-r2/backup-files-and-metadata.txt` | 备份文件、V2 字段、25:53 元数据 |
| `evidence-r2/voice-cues-observed.txt` | 锁屏节点观察记录 |
| `evidence-r2/rotation-and-denial.txt` | 11→7 轮转、权限降级 |
| `evidence-r2/device-matrix.txt` | 三机逐台结果 |
| `evidence-r2/push-2026-11-06-v1.0.1.txt` | 三台安装 1.0.1、回读哈希与启动记录 |

## 4. 未验证 / 已知限制

- 自动备份写失败、空间不足、目录授权撤销、系统重启后的服务行为未逐项实测。
- Note 12T Pro 已完成安装/启动/锁屏语音，但 SAF/自动备份/恢复未完成；Note 11 的恢复流程未完成。
- 锁屏语音原始 logcat 被后续重装清空，保留观察抄录与持久记录/备份元数据。
- 前台服务受 OEM 电池策略/强制停止影响；不能承诺系统强制停止后仍播报。
- 当前仍为 DEBUG 签名；未提交 Git、未发布；三台安装记录见推送证据。
- AC-R2-12 用户签收未完成。
