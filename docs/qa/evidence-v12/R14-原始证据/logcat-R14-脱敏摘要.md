# R14 三态验收 · 原始 logcat 脱敏摘要

- 原文件：`logcat-R14-2026-11-10.txt`（9,675,109 字节 / 86,071 行），采集于 2026-11-10，来源为当次验收机（Redmi Note 12 Pro）。
- 用途：R14「到点只响一次」三态（后台 / 锁屏 / 省电模式）验收的原始设备日志。
- 为什么不在仓库里：该文件是全量 logcat，含与本应用无关的第三方数据，不适合随公开仓库分发。
- 脱敏内容（已从仓库移除，原件只保留在本机 `.local/evidence/`，不进仓）：
  1. 23 处小米账号层 `PassportLog` 记录（含服务票据、用户标识、设备标识等字段）；
  2. 420 行涉及其他应用的通知记录（标题/正文/包名）；
  3. 与本应用无关的系统与音频子系统日志。
- 仓库内保留的等价证据（已裁剪到本应用与目标通知 id）：
  - `00-device-state.txt`
  - `01-test1-background-alarm.txt`、`02-test1-background-logcat-excerpt.txt`
  - `03-test2-lockscreen-alarm.txt`、`04-test2-lockscreen-notification.txt`、`05-test2-lockscreen-logcat-excerpt.txt`
  - `06-test3-batterysaver-alarm.txt`、`07-test3-batterysaver-notification.txt`、`08-test3-batterysaver-logcat-excerpt.txt`
  - `INDEX-R14三态验收.md`
- 复现方式：`adb -s <serial> logcat -v time` 按三态分别抓取，落盘前先按本应用包名与目标通知 id 过滤，禁止整段落盘。
