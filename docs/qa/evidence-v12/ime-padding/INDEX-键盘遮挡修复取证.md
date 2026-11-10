# 键盘遮挡修复取证（imePadding）

采集时间：2026-11-10 17:38–17:41（Asia/Shanghai）
设备：Redmi Note 12 Pro（机型 22101316C，序列号不入库）｜Android 14 / SDK 34｜1080×2400
安装包：debug 构建 17:38:21，versionName 1.2.0 / versionCode 4，`adb install -r` 覆盖安装（保数据）
输入法：系统默认（搜狗），全程未改默认输入法、未装/未启用任何输入法

## 改动

- `ui/AppRoot.kt`：Scaffold 内容 `Box` 增加 `.imePadding()`（训练次数页等所有 Tab 页共用）。
- `ui/screens/Sheets.kt`：`ModalBottomSheet` 内可滚动 `Column` 增加 `.imePadding()`（所有弹层输入框共用）。
- 未改动 `audio/TrainingAlerts.kt`、`TargetAlarmReceiver`、通知渠道与闹钟调度逻辑（`git diff` 仅上述两文件＋文档）。

## 取证结果

| 场景 | 现象 | 判据 |
|---|---|---|
| 次数页输入框聚焦（图 01） | 键盘弹出后内容整体上移，「下一组实际做了多少次？／第 1 组／− 输入框 ＋」完整可见 | 输入框 bounds `[253,1042][827,1196]`；IME 窗口可触区从 y=1505 起，输入框底 1196 < 1505 |
| 弹层内输入框聚焦（图 02） | 「调整这次计划」弹层上移，计划组数 / 每组目标 / 取消 / 保存本次计划全部在键盘上方 | 字段 bounds `[554,1003][1025,1157]`，按钮 `[640,1326][940,1398]`，均 < 1505 |
| 键盘收起（图 03） | 布局复原为原滚动位置，无残留位移 | 收起后 `mInputShown=false`，「＋ 完成一组」回到 `[369,1259][711,1342]` |

输入回读：聚焦次数输入框后 `adb shell input text "12"`，值由 `10` → `101`（第 4 位被 3 位长度上限拦下），确认键盘输入可落入输入框。

命令：`adb -s <target> exec-out screencap -p`、`uiautomator dump`、`dumpsys input_method`、`dumpsys window windows`（IME 窗口 `touchableRegion`）。

## 附：Bug2 假设（inputType=0x2002）核对结论

- 本次聚焦实测 `dumpsys input_method` 的 `inputType=0x2`（即 `TYPE_CLASS_NUMBER`，无 `TYPE_NUMBER_FLAG_DECIMAL`）：
  - 次数页「下一组实际做了多少次」字段（`KeyboardType.Number`）；
  - 计划弹层「计划组数」「每组目标」字段（同为 `KeyboardType.Number`）。
- 全仓使用 `KeyboardType.Decimal`（会产生 0x2002）的字段只有时长/分钟类：新建项目默认目标分钟、目标分钟编辑、预设分钟、结束训练的目标/实际分钟——这些字段按 `toDoubleOrNull()` 解析并乘 60 取秒，确实需要小数。
- 结论：次数类字段已是纯数字，**0x2002 假设在当前离线构建上不成立**；在没有微信输入法设备复现前，不将「改 0x0002」当作修复结论，也不改动代码。
