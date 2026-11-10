# 主界面项目标签 · 样张说明（2026-11-10）

对应实现：[TrainScreen.kt](file:///Users/zzymima0000/Developer/coding/1.Active/068-ing-再久一点/app/src/main/java/com/wanghoufan/zaijiuyidian/ui/screens/TrainScreen.kt) 的 `ProjectSelector` / `ProjectTab`。
采集设备：Redmi Note 12 Pro（ruby），Android 14；应用内字号 115%，设备 `font_scale=1.0`、`wm density=440`。

| 文件 | 版本状态 | 展示内容 |
|---|---|---|
| `主界面项目标签-样张-v1.png` | **已被 v2 取代，仅作迭代留痕** | build 5 的「等宽三标签」：≤4 项目时每格 `weight(1f)` 等分、内边距 8dp |
| `主界面项目标签-样张-v2-四项目.png` | 当前（build 8 / 1.2.4） | 4 个计时项目**平铺态**：按内容取宽、居中，四字项目名「平板支撑」完整显示、无截断 |
| `主界面项目标签-样张-v2-五项目未滑动.png` | 当前（build 8 / 1.2.4） | 5 个项目**初始态**：最右一项被右边缘裁掉，提示可横向滑动 |
| `主界面项目标签-样张-v2-五项目滑动.png` | 当前（build 8 / 1.2.4） | 5 个项目**左滑到底**：最右长名以省略号截断完整露出，全程无「更多」收口 |

- v1 被取代的原因：等分宽度 + 应用字号 115% 时，4 个四字项目名会被截成「平板…」，故改成按内容取宽、放不下才横向滑动。
- 送用户确认时应发 **v2 三张**，不要发 v1。
- 各标签高度 44dp、圆角 12dp、选中态用 `accent2` 底 + `accent` 描边、字号 15sp、`maxLines=1` + 省略号；`widthIn(min=56.dp, max=150.dp)`。
