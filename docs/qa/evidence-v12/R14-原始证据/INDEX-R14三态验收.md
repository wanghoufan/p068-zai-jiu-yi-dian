# R14 到点提醒 · 三态真机验收索引（2026-11-10）

## 0. 采集环境

| 项 | 值 | 来源 |
|---|---|---|
| 设备 | Redmi Note 12 Pro（ruby） | `getprop ro.product.model` |
| Android | 14（SDK 34） | `getprop ro.build.version.release` |
| 应用 | `com.wanghoufan.zaijiuyidian`，versionCode 4 / versionName 1.2.0 | `dumpsys package` |
| 安装 | firstInstallTime 2026-11-06 08:51:25，lastUpdateTime 2026-11-10 16:37:36 | 同上 |
| 精确闹钟权限 | `SCHEDULE_EXACT_ALARM` appop = 未授权（`Default mode: default`） | `appops get` |
| 采集日期 | 2026-11-10 17:14–17:29 | 设备时钟 |

> 本文件不含设备序列号。设备以型号标识，ADB 目标由 `android-device-target-lock` 的 `resolve` 取得后再以 `adb -s` 显式指定。

### 采样命令（三个状态通用）

```bash
# 持久日志流（整轮一次，写入本目录 logcat-R14-2026-11-10.txt）
adb -s <serial> logcat -v time > logcat-R14-2026-11-10.txt
# 打时间标记（写入 logcat）
adb -s <serial> shell 'log -t R14_MARK "TESTx_begin device_clock=$(date +%H:%M:%S)"'
# 闹钟登记与到点
adb -s <serial> shell dumpsys alarm
# 通知（when = 通知创建时间）
adb -s <serial> shell "dumpsys notification --noredact | grep -E 'id=6802' -A45"
# 截图
adb -s <serial> exec-out screencap -p > <file>.png
```

## 1. 三态结果

### 状态一：切到后台（TEST1 / TEST1A）

| 项 | 值 |
|---|---|
| 设定时长 | 60 秒（自定义时间保存为「1 分钟」，UI 回读「目标已保存为 1 分钟」「01:00」） |
| 开始时间 | 17:18:13.041（`TEST1A_begin device_clock`） |
| 计划到点 | 17:19:13.160（`dumpsys alarm`：`RTC_WAKEUP origWhen=2026-11-10 17:19:13.160`，`window=+44s995ms`，`flags=0x20`，无 `exactAllowReason`） |
| 实际提醒（前台服务路径） | 17:19:13.673（`P068Voice: service cue interval=0 target=true voicePlayed=true voice=true sound=true`）→ **+513 ms** |
| 兜底闹钟实际投递 | 17:19:58.173（系统日志 `AlarmManager: mPendingIntent -> PendingIntent{... zaijiuyidian broadcastIntent}`）→ 比计划晚 **+45.01 s**（落在 window 末端） |
| 通知 | 6802 `channel=training_alert` `importance=4` `category=alarm`，17:20:17 截图可见「训练到点了 「平板支撑」目标 01:00 已到」 |
| 结论 | **准点**（音频/通知 <1 s）。但降级闹钟迟到 45 s 并**二次投递提醒**（见 §2 缺陷 D1） |

证据文件：`01-test1-background-alarm.txt`（首次尝试 17:16:56 的闹钟原文）、`02-test1-background-logcat-excerpt.txt`、`test1-background-notification.png`、`logcat-R14-2026-11-10.txt`。
说明：`01` 是首次尝试（17:16）的 `dumpsys alarm` 原文，该次 logcat 流中途被杀，数据不完整；有效一轮为 TEST1A，其闹钟特征与 `01` 一致（`window≈+45s`、`flags=0x20`）。

### 状态二：锁屏（TEST2）

| 项 | 值 |
|---|---|
| 设定时长 | 60 秒（目标沿用 01:00） |
| 开始时间 | 17:21:24（`TEST2_begin device_clock=17:21:24`） |
| 熄屏 | 17:21:26（`TEST2_lock_at`，`input keyevent KEYCODE_POWER`） |
| 计划到点 | 17:22:24.217（`window=+44s998ms`，`flags=0x20`，无 `exactAllowReason`；`policyWhenElapsed: battery_saver=-5s199ms`） |
| 实际提醒（前台服务路径） | 17:22:24.512 → **+295 ms**；通知 `when=1794302544537`（17:22:24.537）→ **+320 ms** |
| 兜底闹钟实际投递 | 17:23:09.272 → 比计划晚 **+45.06 s**，同一条 6802 被再次 post（`when=1794302589245`，17:23:09.245），系统日志 `AlertCoordinator: onViewBound, buzzBeep for 0|...|6802` → 第二次提示音/震动 |
| 锁屏可见性 | 唤醒后锁屏 **未见**该通知（仅见媒体控制与 USB 通知）；顶边下拉后仍未见，见 `test2-lockscreen-notification.png` |
| 结论 | **准点**；但存在二次提醒（D1）与「锁屏不显示通知」（D2）两项未达标 |

证据文件：`03-test2-lockscreen-alarm.txt`、`04-test2-lockscreen-notification.txt`、`05-test2-lockscreen-logcat-excerpt.txt`、`test2-lockscreen-notification.png`。

### 状态三：省电模式（TEST3，`low_power=1` + 熄屏）

| 项 | 值 |
|---|---|
| 设定时长 | 60 秒（目标沿用 01:00） |
| 开始时间 | 17:25:55（`TEST3_begin device_clock=17:25:55 target=01:00 battery_saver=1`） |
| 熄屏 | 17:25:57（`TEST3_lock_at`） |
| 省电模式 | `settings put global low_power 1` → `dumpsys power: mSettingBatterySaverEnabled=true`（原值 0，测后已复原 0） |
| 计划到点 | 17:26:55.760（`window=+44s998ms`，`flags=0x20`，`policyWhenElapsed: battery_saver=-5s164ms`） |
| 实际提醒（前台服务路径） | 17:26:55.894 → **+134 ms**；通知 `when=1794302815931`（17:26:55.931）→ **+171 ms** |
| 兜底闹钟实际投递 | 17:27:40.822 → 比计划晚 **+45.06 s**，6802 再次 post（`when=1794302860794`，17:27:40.794） |
| 锁屏可见性 | 同 TEST2，未显示（`test3-batterysaver-lockscreen.png`、`-expanded.png`） |
| 结论 | **准点**；二次提醒（D1）与锁屏不可见（D2）同样复现 |

证据文件：`06-test3-batterysaver-alarm.txt`、`07-test3-batterysaver-notification.txt`、`08-test3-batterysaver-logcat-excerpt.txt`、`test3-batterysaver-lockscreen.png`、`test3-batterysaver-lockscreen-expanded.png`。

## 2. 本轮发现

- **D1（缺陷，三态全部复现）**：无精确闹钟权限时 `TrainingAlerts` 走 `setAndAllowWhileIdle` 降级，系统窗口约 45 s；前台服务已在到点瞬间提醒，兜底闹钟在窗口末端（+45 s）再次投递并**重新 post 同一条通知**，去重窗口只有 2 s（[TrainingAlerts.kt](file:///Users/zzymima0000/Developer/coding/1.Active/068-ing-再久一点/app/src/main/java/com/wanghoufan/zaijiuyidian/audio/TrainingAlerts.kt#L69-L100)），挡不住第二次 → 声音/震动重复一次。三次实测迟到量：+45.01 s / +45.06 s / +45.06 s。
- **D2（视觉可见性未达成）**：锁屏下该通知不显示（两次均只显示媒体控制与 USB）。`dumpsys notification` 中该条 `vis=PUBLIC`、`importance=4`、`category=alarm` 均正常已 post；`dumpsys` 无法证明是 HyperOS 锁屏通知策略（系统层设置）还是应用侧问题。**暂不下结论**，列为待定位项。
- **准点性结论**：三态「到点—实际提醒」偏差分别为 +513 ms / +295 ms / +134 ms，全部 <1 s，**准点**。

## 3. 测试过程与收尾

- 三态训练均**未计入历史**（结束对话框选「不计入」）。测后回读本机库：`sessions=0`、`rep_sets=0`、`projects=2`，历史未被污染。
- TEST2 会话在恢复前台时被一次上滑手势误触结束（非产品行为，结论已按 17:22:24 的到点证据判定）；TEST3 改用 `wm dismiss-keyguard` 解锁，未再复现。
- 系统设置复原：`low_power` 1→0（`mSettingBatterySaverEnabled=false`）；`zen_mode` 临时改 0 后已复原 1；媒体音量未改动。
- 整轮未登录云端、未做任何云端写操作。

## 4. D1 修复后复测（2026-11-10 17:52–17:59，versionCode 6 / 1.2.2）

修复方式：`TrainingAlerts.notifyTarget` 去重窗从 2 s 提升到 60 s，覆盖「无精确闹钟权限时最晚 +45 s」的降级窗口；同时把最近提醒时间写入 SharedPreferences，进程被杀后重启也能挡住兜底闹钟的重复提醒。

| 场景 | 到点提醒 | 兜底闹钟投递 | 结果 |
|---|---|---|---|
| A 切后台 | 17:53:31.152（`service cue target=true`）→ 17:53:31.161 post 6802 | 17:54:15.678，比提醒晚 44.52 s | `skip duplicate: elapsed_ms=44519`，**未二次提醒** |
| B 锁屏 | 17:56:03.677 → 17:56:03.687 post 6802 | 17:56:48.397，晚 44.71 s | `skip duplicate: elapsed_ms=44711`，**未二次提醒** |
| C 省电模式+锁屏 | 17:58:30.754 → 17:58:30.764 post 6802 | 17:59:15.678，晚 44.92 s | `skip duplicate: elapsed_ms=44915`，**未二次提醒** |

- 每态 `P068Alert: post target alert` 各 1 次、`P068Voice: service cue ... target=true` 各 1 次、`skip duplicate` 各 1 次；三态均**只响一次**。
- 省电测试前 `low_power=0` → 置 1 → 测后复原 0（已回读确认）；三次训练结束均选「不计入」，回读页面为「准备开始」，历史未污染。
- 原始证据（已入库）：`D1复测/A-background-logcat.txt`、`B-lockscreen-logcat.txt`、`C-batterysaver-logcat.txt`；三份 `*-notification.txt` 为 `dumpsys notification --noredact` 的**裁剪摘录**（只保留本应用 id=6802 的记录，含 `when`、`channel=training_alert`、`importance=4`、`category=alarm`、`vis=PUBLIC`）。
- 截图取证本轮**失败、未入库**：`A-background-notification-shade.png` 实为桌面（截图时通知栏未展开，画面不含 6802），`B-lockscreen.png` / `C-batterysaver.png` 为全黑（熄屏态 `screencap` 结果）。因此本轮**没有「通知在锁屏/通知栏可见」的视觉证据**。
- **D1 结论范围（只到这一步）**：本节的通过结论仅主张「同一次到点只提醒一次」，依据是 `P068Alert` 日志与三态复测（各 1 次 post、各 1 次 skip duplicate），**不含「通知是否可见」**。通知可见性属 D2，维持 R14 首轮「仍未定位」结论（`docs/risks.md` R15）；**D1 通过不等于 D2 通过**，后续不得据此宣称锁屏提醒已可用。
- 全量 dump（含其他应用通知正文）与三张原始截图（含桌面应用列表）属真实隐私数据，已移出仓库至 `.local/evidence-raw/D1复测/`，不入库。

#### 60 秒去重窗不会误伤下一次真实到点（口径）

- **训练目标最短 1 分钟**：现场目标经 `Math.round(minutes * 60).coerceIn(60, 18_000)` 落库（[AppViewModel.kt](file:///Users/zzymima0000/Developer/coding/1.Active/068-ing-再久一点/app/src/main/java/com/wanghoufan/zaijiuyidian/ui/AppViewModel.kt#L538)、[同文件 L619](file:///Users/zzymima0000/Developer/coding/1.Active/068-ing-再久一点/app/src/main/java/com/wanghoufan/zaijiuyidian/ui/AppViewModel.kt#L619)、[L758](file:///Users/zzymima0000/Developer/coding/1.Active/068-ing-再久一点/app/src/main/java/com/wanghoufan/zaijiuyidian/ui/AppViewModel.kt#L758)），自定义目标输入 0.5 分钟也会被抬到 60 秒（`Sheets.kt` 的「分钟（1～300）、最多 1 位小数」）。
- **同一会话只提醒一次**：训练会话用 `notifiedAtTarget` 标记，到点后继续训练不会再发第二次。
- **因此两次真实到点之间必然 > 60 s**：下一次提醒只能来自新会话，新会话起点晚于上一次提醒时刻，最短目标 60 s ⇒ 间隔严格大于 60 000 ms，不会被 `DUPLICATE_WINDOW_MS = 60_000` 吞掉。三态实测的 `skip duplicate` 差值 44.52 / 44.71 / 44.92 s，均落在「兜底闹钟窗口 45 s + 0.5 s 内」，属同一目标的重复投递。
- **边界（未实测）**：跨进程去重依赖墙钟；若系统时间被回拨且进程恰好重启，理论上会多挡一小段（概率极低，未做实测）。

