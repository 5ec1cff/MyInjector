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
每组成功迁移后记录本地完成标记；以后远端清空时使用默认值，避免把旧缓存再次当作旧数据导回。
已迁移后的离线待提交修改仍会按 key 补交。

界面显示连接与同步状态。离线修改可跨 App 进程退出保留；
不以 apply 或客户端缓存读回值作为远端保存成功的证据。
配置按 Android 用户隔离：system_server 使用 user 0，其余宿主按自身用户读取。

实施按写端、Java 读端、系统框架、HYOS native 分阶段提交。
SystemUI 和旧 Java 桌面已通过 RemoteConfig 获取初始快照、监听整组刷新，
卸载时注销监听并停止刷新任务；不再读写宿主配置文件或注册配置广播。
系统框架也已停止配置广播和文件持久化；更新时比较旧配置，保留 xSpace 与 dexopt 的即时副作用。
HYOS 使用 native API 103 直接订阅 miuihome，系统桥接从同组校验设置；
已删除配置广播、启动握手和 native receiver，不再写宿主/system_server 配置文件。
回调同步读取并发布快照后才返回；FULL、空配置、类型错误、读取失败重试均有 native 回归测试。
原有特权命令行为保持，预启动/AOSP 页面/Toast 不在本次存储迁移中改写。

验证：迁移/远端优先、commit 失败但缓存改变、并发编辑不被旧确认覆盖、outbox 重建恢复、
22 项配置与设置目录一致的单元测试，以及 release Kotlin 编译。

## 本轮验证与边界

迁移、失败缓存重试、提交期间再编辑、服务切换、远端清空不复活旧缓存、原规则解析的 JVM 单元测试通过。
native 测试覆盖首次订阅失败重试、初始 FULL 早于 subscribe 返回、同步发布、类型检查、
clear、失败后唤醒重试和句柄释放；原手势/SHA 测试保留。
release APK 构建、唯一入口/元数据与 ELF/APK 16KB 对齐检查通过。
真实桌面样本 7751 的解析与错误拒绝测试通过，解析测试不等于模块注入验收。

未自动安装模块、部署框架或重启设备；需在支持当前 API 103 的 LSPosed release 上验收：
升级保留 22 项、离线编辑杀 App 再重连、各配置即时生效、Java 热更新与 HYOS 冻结恢复。
小米桌面预启动、AOSP 页面及 Toast 仍维持原有行为，本轮只迁移配置；强停仍需系统框架作用域。
