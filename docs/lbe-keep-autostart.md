# 权限管理服务「保持自启动」开关（HyperOS 3 修复）

## 问题现象

应用管理界面（安全中心 `com.miui.securitycenter` 的应用详情页）由 MyInjector 注入的「保持自启动」开关在 HyperOS 3 上失效：开关行仍然显示，但既无法打开、也无法反映真实状态。

## 原因（权限管理服务 1.9.2 反编译结论）

旧实现读写 `content://com.lbe.security.miui.permmgr/active` 表的 `suggestAccept` 字段，把自启动位（`1<<14 = 16384`）置 1 表示"保持自启动"。HyperOS 3 的权限管理服务（`com.lbe.security.miui`，1.9.2）里：

- OTA 迁移代码 `DatabaseHelper.syncAutoStartPermissionForOTA` 把旧表里该位的应用迁移到新存储 `PermissionConfigSharePref`（SharedPreferences 文件 `permission_config_sharePref` 的 `AUTO_START_WHITE_LIST` 集合）和 `OP_AUTO_START_SWITCH`（10053），随后 `drop table active`——旧表的读写目标在物理上不存在了。
- 新的自动回收调度器 `AutoRevokePermissionManager.lambda$startScheduleASCheck$1` 只跳过四类应用：使用自启动 SDK（需小米服务器鉴权，无法伪造）、内置白名单或 `PermissionConfigSharePref.isAutoStartWhiteList`、云端白名单、系统豁免（`AutoStartUtil.isExemptFromAutoStartRestriction`，仅隐藏系统应用）。它**不再读取** `suggestAccept`。
- `AUTO_START_WHITE_LIST` 是权限管理服务的私有存储，**没有**任何对外 provider 方法可以写入（仅 OTA 迁移与云端推送 `CloudUpdater` 会改），因此只能在该服务进程内写入。

## 实现方式

按原作者设计：UI hook 只负责加开关，flag 通过"已有的通道"写入服务自身存储，不 hook 回收逻辑，模块关闭后已写入的 flag 仍被原生调度器尊重。

- `LbeHandler`（在 `com.lbe.security.miui` 进程内）：保留原有的定时检查 nop（`lambda$startScheduleASCheck$1`，1.9.2 中仍存在，作为模块启用时的兜底）；新增 hook `PermissionManagerProvider.call(String, String, Bundle)`，识别两个模块专用方法名（`myinjector_get_keep_autostart` / `myinjector_set_keep_autostart`），进程内转调原生 `PermissionConfigSharePref.isAutoStartWhiteList / addAutoStartWhiteList / removeAutoStartWhiteList`。原 provider 把所有原生方法名按整数解析，字符串方法名不会冲突；provider 在 manifest 层要求 `miui.permission.READ_AND_WIRTE_PERMISSION_MANAGER`，扩展方法自动继承同样的访问控制。
- `MIUISecurityCenterHandler`（按钮侧）：UI 注入不变；读状态优先 `call` 新方法，写状态优先 `call` 新方法并在失败时报错提示；两路径在扩展不存在（旧版本权限管理服务、或模块未勾选该应用）时自动回退旧的 `suggestAccept` 表路径。

flag 语义与官方 OTA 迁移一致：迁移代码就是"旧 suggestAccept 位 → AUTO_START_WHITE_LIST 条目"的转换，因此行为与升级前官方认定的状态等价。

## 验证限制

- 证据来源：`权限管理服务_1.9.2.apk`（jadx 反编译，4 个错误，相关类完整可读）。没有安全中心 APK，按钮所在 Fragment 类名沿用现有 hook，用户确认 UI 仍显示。
- 无已连接设备，CI 编译通过不代表真机验证。需验证：开关状态回显与切换、写入后 `permission_config_sharePref` 中出现对应包名、模块关闭后自启动在 30 天未使用场景下仍保持、云端 `AutoStartRevokeWhiteList` 推送是否影响该条目（`CloudUpdater` 存在移除逻辑）、旧版本设备上回退路径无回归。
