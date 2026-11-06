P068 V1.1-R2 证据索引｜2026-11-06

APK
- dist/P068-zai-jiu-yi-dian-v1.1.0-debug.apk
- size 16,033,544 bytes
- sha256 3d81a707df8473a1e54365c7a815cc4ff359602c0a1812bc5e78a481d1055919
- versionName 1.1.0 / versionCode 3 / DEBUG

自动测试
- ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest
- 32/32 passed（Stats/Timer/BackupCodec/BackupRepository/Migration/ProjectDao）

Room 迁移（两台旧 1.0.1 设备 install -r 后拉库核对）
- Note 11 5G：projects=1, sessions=0, mode=TIME, plannedSets=3, rep_sets/rep_draft 存在
- Note 12 Pro：projects=1, sessions=0, mode=TIME, plannedSets=3, rep_sets/rep_draft 存在
- MigrationTest：v1 项目/会话/活动计时保留，新增表为空

真机主路径（Redmi Note 11 5G / Android 11）
- 四 Tab、TIME 14 秒暂停/继续/结束/保存
- 计次 3×10：[10,10,10]=30；超计划 4/3 组=40；提前结束 2 组=20 可保存
- 草稿：加组后 force-stop/重启，TIME 页提示继续，恢复后组数保留
- 历史：次数三图、关系表（3 次/周、平均 30、67%）；改组 8→10 后 28→30、0%→100%
- 删除 20 次记录后剩 2 条并重算；最近删除恢复回 3 条
- V3 导出：2 项目/4 记录/3 计次/9 组；覆盖恢复后即时 KPI 3/40/10/4；同文件合并新增 0，库内 4/9
- 覆盖前安全点：files/safety-backups/ 2 份
- 145% 字体：计次页/历史页无重叠

设备矩阵
- Note 11 5G：V1.1 主路径完整（见上）
- Note 12 Pro：安装 1.1.0、v1→v2 迁移、四 Tab、旧项目保留；另一应用抢占前台，未跑完整计次链路
- Note 12T Pro：安装 1.1.0、回读哈希、启动、v1→v2 迁移通过；完整双模式/历史链路 NOT_RUN

已知未跑
- V1.1 锁屏 3 分钟语音节点（沿用 R2 证据）
- V3 自动备份 7 版轮转、目录撤销、空间不足/写失败
- 深色/窄屏在 V1.1 的重跑
- 用户最终签收
