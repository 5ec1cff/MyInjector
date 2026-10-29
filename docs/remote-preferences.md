# 模块端配置的 remote preferences 迁移

仅迁移模块 Activity 管理的 22 项设置（19 个布尔值、3 个规则字符串）。
Telegram、Chrome、XHS 等注入设置仍按宿主原路径保存。

模块通过 libxposed service 102 写入 `system`、`systemui`、`miuihome` 三个组，
沿用原 key、false 默认值和规则语法。`SettingsSchema` 定义范围；`HookConfigs` 复用原规则解析。

`ModulePreferences` 在 App 的 service 连接后自动导入旧私有设置；远端已有值优先。
旧 `system_server` SharedPreferences 保留为界面缓存及持久化 outbox，
每次本地修改同时保存唯一 token。后台串行 commit 成功后只清除该批次 token，
不会覆盖提交期间的新编辑；失败自动重试，重新连接后使用新的 service 句柄。
remote 为 Hook 的权威来源，本地缓存不会在每次启动时整组覆盖远端。

界面显示连接与同步状态。离线修改可跨 App 进程退出保留；
不以 apply 或客户端缓存读回值作为远端保存成功的证据。
配置按 Android 用户隔离：system_server 使用 user 0，其余宿主按自身用户读取。

实施按写端、Java 读端、系统框架、HYOS native 分阶段提交。
SystemUI 和旧 Java 桌面已通过 RemoteConfig 获取初始快照、监听整组刷新，
卸载时注销监听并停止刷新任务；不再读写宿主配置文件或注册配置广播。
系统框架与 HYOS 读端切换前暂保留对应旧广播。

验证：迁移/远端优先、commit 失败但缓存改变、并发编辑不被旧确认覆盖、outbox 重建恢复、
22 项配置与设置目录一致的单元测试，以及 release Kotlin 编译。
