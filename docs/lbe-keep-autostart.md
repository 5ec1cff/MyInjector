# 权限管理服务「保持自启动」开关（HyperOS 3 修复 + Android 17 适配）

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

## 安全服务（手机管家）12.8.1 UI 兼容性核对

反编译 `安全服务_12.8.1-260816.0.1.apk`（`com.miui.securitycenter`）逐项核对了开关注入的全部依赖，均在位，UI 注入逻辑无需功能性修改：

- `ApplicationsDetailsActivity` 仍注册于 manifest、由 `ManageFragment` 启动，已无 `initView`（正常走 Fragment 路径）
- `ApplicationsDetailsFragment.onCreatePreferences` 仍调用 `addPreferencesFromResource(R.xml.am_applications_detils_settings)`；包名仍从 Activity intent 的 `package_name` 读取（`F3()`），与 hook 一致
- `app_detail_perm_category`、`miuix.preference.CheckBoxPreference(Context)` 均存在；后续 Loader 回调只按特定引用移除原生条目，不会清掉注入的开关

因此「按钮显示但打不开」完全由数据路径（旧 `active` 表在权限管理服务 1.9.2 中被删除）解释，见上文修复方案。本次仅加固边界：Activity intent 无有效包名或分类缺失时跳过注入（12.8.1 的 `onCreatePreferences` 在 `F3()` 失败时会 `finish()` 并提前返回，after-hook 仍会在垂死页面上执行，原先的 `!!` 会抛 NPE）；`ApplicationsDetailsActivity` 类缺失时不再让整个 hook 失败。

## 验证限制

- 证据来源：`权限管理服务_1.9.2.apk` 与 `安全服务_12.8.1-260816.0.1.apk`（jadx 反编译，分别 4/65 个错误，相关类完整可读）。
- 无已连接设备，CI 编译通过不代表真机验证。需验证：开关状态回显与切换、写入后 `permission_config_sharePref` 中出现对应包名、模块关闭后自启动在 30 天未使用场景下仍保持、云端 `AutoStartRevokeWhiteList` 推送是否影响该条目（`CloudUpdater` 存在移除逻辑）、旧版本设备上回退路径无回归。

---

# Android 17 适配（lbe-17 分支）

## 新系统证据（权限管理服务 4.0.0 / 安全服务 13.5.8 反编译结论）

Android 17（安全服务 13.5.8，权限管理服务 4.0.0）对该链路做了整体重构：

- 权限管理服务的包名空间从 `com.lbe.security.*` 迁移到 `com.hyperos.security.*`，但**应用包名仍是 `com.lbe.security.miui`**，我手上这份 4.0.0 APK 的 provider authority 仍是 `com.lbe.security.miui.permmgr`（`PermissionManagerProvider`，exported，要求 `miui.permission.READ_AND_WIRTE_PERMISSION_MANAGER`），`call(String, String, Bundle)` 三参签名与 `Integer.parseInt` 方法名解析不变——模块专用字符串方法名依旧不会与原生方法冲突。**但真机日志显示设备上更新的构建已把 authority 改名**（对旧 authority 报 `Unknown authority`），因此安全中心侧改为运行时从 provider 列表动态解析（见下）。
- 自启动状态从"私有 SP 白名单"变成**框架权限 `PERM_ID_AUTOSTART`**（`com.miui.permission.PermissionManager.setApplicationPermission`，action 3=允许 / 1=拒绝，底层 AppOps）。UI 开关（provider case 6）、云端 one-push 回收（`dealOnePush`）、第三方 SDK（`AutoStartManagerProvider`）、安全中心 osbot 全部汇入该框架调用。**真机日志确认 `PERM_ID_AUTOSTART == 16384`**（正是旧 `suggestAccept` 的自启动位）。
- `PermissionConfigSharePref` 里旧的白名单方法（`isAutoStartWhiteList` 等）**全部消失**，只剩云同步/one-push 记账；1.9.2 的 30 天调度器 `AutoRevokePermissionManager` 从该服务中**整体移除**，剩下的 JobService 只有云同步（205）、one-push（207）、数据上报（206）。`OnePushJobService` 的 AppOps `setMode` 调用全部传 mode 0（恢复自启动），不是回收路径。
- 安全服务 13.5.8 的应用详情页结构**完全不变**：`com.miui.appmanager.fragment.ApplicationsDetailsFragment`、`R.xml.am_applications_detils_settings`、`app_detail_perm_category` 均在原位，UI 注入无需改动。

## Android 17 上的实现（lbe-17）

存储文件保持不变：`permission_config_sharePref` SP 的 `AUTO_START_WHITE_LIST` StringSet（与 1.9.x 官方格式一致，双版本共用）。但**4.x 上不再经 `PermissionConfigSharePref.mSp` 静态字段读写**（原因见事故复盘第三条），改为经进程 Application 上下文打开同一 SP 文件（进程内 `getSharedPreferences` 返回同一缓存实例，与服务自身 `mSp` 完全一致）；1.9.x 上仍优先转调原生白名单方法。

安全中心侧不再硬编码 provider authority：运行时用 `GET_PROVIDERS` 从权限管理服务的 provider 列表中按类名 `PermissionManagerProvider` 解析真实 authority，解析不到再依次尝试已知候选（`com.lbe.security.miui.permmgr`、`com.hyperos.security.miui.permmgr`），`Unknown authority` 自动跳下一个。

执行层按版本自动选择：

- **HyperOS 3（legacy helpers 存在）**：与原 lbe 分支完全一致（调度器 skip + nop 兜底），不新增任何 hook，零回归风险。
- **Android 17（legacy helpers 缺失）**：
  1. **deny 拦截**：hook 进程内框架 `com.miui.permission.PermissionManager.setApplicationPermission`，但**只匹配服务自身 autostart 路径使用的三种确切重载**（`(JII String)` 云端 one-push、`(JIII Bundle String)` SDK、`(JII String[])` provider case 6），且要求 permId 精确等于运行时读取的 `PERM_ID_AUTOSTART`、action == 1（系统唯一回收动作），包名命中 keep 列表才 veto；veto 返回值按方法返回类型给默认值（避免原始类型拆箱 NPE）。任何其他重载/其他权限一律不碰。
  2. **自愈看门狗**：`OnePushJobService.onStartJob` 后置 hook，**先经服务自身的 `PermissionUtil.getPermissionAction` 查询当前状态**，仅对确实被拒的 keep 包名重新授 action 3，不做盲目写入。
  3. `PERM_ID_AUTOSTART` 运行时以 `Number` 语义反射读取，**读取失败则整套执行层不安装**（仅记录日志）——绝不猜测数值。

语义统一为：**keep 开启 = 自启动锁定为开**；要先关自启动，先关「保持自启动」。

## Android 17 真机事故复盘（v1 → v2 → v3）

v1（4284a1b）在 Android 17 真机上导致权限管理异常；v2（e5dad70）修了三个缺陷但**仍崩溃循环**；真机日志（`logcat -s MyInjector`）最终定位真因，v3 全部修复：

- **v3 修复——`mSp` 反射毒化服务类（崩溃循环真因）**：v1/v2 都通过 `PermissionConfigSharePref.mSp` 静态字段读 keep 列表。`field.get(null)` 会强制类初始化，而该类静态块执行 `Application.getApplication().getSharedPreferences(...)`——当 hook 在 Application 就绪前（启动期的权限 deny binder 调用）触发它，静态块 NPE，类被**永久毒化**（后续 `NoClassDefFoundError`），服务原生代码一碰该类就崩 → 进程被拉起 → 启动期再触发 → 再崩，**每 ~10 秒一循环**（日志中 com.lbe.security.miui 进程 pid 持续翻新即此）。v2 的三处修复都没碰这个点，所以"还是炸了"。v3：彻底不再触碰该类，改经进程上下文开同一 SP 文件；Application 未就绪时保持惰性（返回空列表），任何路径都不再初始化服务类。
- **v2 修复（真机日志证伪了 v2 的 permId 担忧）**：v1 曾在反射失败时回退 16384L。真机日志 `PERM_ID_AUTOSTART=16384` 证明常量真值就是 16384（旧 suggestAccept 位值），此前"位索引 14"的推断是 jadx 反编译误导。v2 的 `Number` 语义读取 + 读不到不安装的防御保留（猜值风险确实存在，只是这次没踩中）。
- **v2 修复——全量 hook 按位置猜参数**：v1 对所有 `setApplicationPermission` 重载用 `args[0]/args[1]` 判断 permId/action，其他重载若参数布局不同就会误判误拦。v2：精确签名白名单 + action == 1。
- **v2 修复——`param.result = null` 打在非 void 返回上**：原始类型返回值拆箱 null 会 NPE。v2：按返回类型给默认值；同时撤掉了安全中心进程内的直调 veto（它挂在 UI 每次权限切换的路径上，收益小风险大，由看门狗兜底即可）。
- **v3 修复——设备构建的 authority 已改名**：真机日志安全中心侧对 `com.lbe.security.miui.permmgr` 报 `Unknown authority`（手上的 4.0.0 APK 尚未改名，设备构建更新），因此 GET/SET 全部到不了服务。v3 动态解析真实 authority。

## Android 17 验证限制

- 证据来源：`权限管理服务_4.0.0.apk`、`安全服务_13.5.8-260908.0.1.apk`（jadx 反编译）+ 真机 `logcat -s MyInjector` 日志（崩溃循环、PERM_ID_AUTOSTART=16384、Unknown authority 三项事实均来自真机）。
- 框架 `com.miui.permission.PermissionManager` 的方法重载以 4.0.0 进程内调用点归纳（`(JII String)`、`(JIII Bundle String)`、`(JII String[])`），veto 仅对这三种签名生效，其余一律放行；设备构建若签名有出入则对应路径不生效（安全但需日志确认）。
- `PERM_ID_AUTOSTART` 运行时按 `Number` 反射读取；不可读时整套执行层不安装并记录日志。
- 需真机复验（v3）：权限服务进程不再 ~10 秒重启（`adb shell ps -A | grep lbe` 观察或看 `logcat -b crash` 是否还有 FATAL）、开关回显与切换恢复、`logcat -s MyInjector` 应出现 `permmgr uris =` 与 GET/SET/veto/看门狗日志；若权限服务仍崩溃，请抓 `adb logcat -b crash -d`（MyInjector 标签过滤不到原生崩溃栈）。
