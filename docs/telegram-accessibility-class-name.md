# Telegram 无障碍节点类名兼容修复

fooView 1.7.0 遍历节点时，在 `FooAccessibilityService.S` 中直接执行
`node.getClassName().toString()`。该调用位于相对 dexpc `119`（`0x77`），
对应样本文件偏移 `0x21e492`，用于生成日志中的类名。
遇到 Telegram 回复引用节点的空类名时会抛出异常，外层 `v0` 捕获后返回 `null`，
本轮收集的候选因此无法交给后续命中流程。

Android 并不保证这里的类名非空。本地 Android SDK 37 源码
`android/view/accessibility/AccessibilityNodeInfo.java` 第 1163 行的 `mClassName`
默认为 `null`，第 3700 行的 getter 直接返回该字段，第 3716 行的 setter
也不拒绝 `null`。`obtain()`、`setSource()` 不会替代 View 的节点初始化过程。
本修复在 Telegram 侧补类名，以兼容 fooView 的非空假设。

## 实现

`FixAccessibilityNodeClassName` 使用 DexKit 查找 Telegram 包内直接继承
`AccessibilityNodeProvider` 的类，hook 它们的
`createAccessibilityNodeInfo(int)`，不依赖提供器混淆后的类名。

方法正常返回非空节点后，如果 `className == null`，补上 `android.view.View`。
此时节点还在应用进程中，尚未由框架封装为只读节点，可以直接调用 `setClassName`。
已有类名（包括空字符串）、文字、描述、坐标、动作以及其他属性均保留。
选择通用 View 类名，避免给回复引用等节点添加 Button 或 TextView 的角色语义。

开关位于 Telegram 设置 → 其他 → 修复无障碍节点类名，默认启用，
同时遵循模块的 Telegram 总开关。关闭后停止修正后续返回的节点，重新开启即时生效。
设置使用 protobuf 可选字段 43；旧设置未包含该字段时仍默认开启，显式关闭会持久保存。
初始化定位失败会进入现有的 hook 错误记录。

## 源码检查

检查范围为本地 `~/Telegram` 的 13.1.1 源码，提交
`3de37c8cfb039452be7398909461da6288babf15`。
遍历了节点创建位置、自定义提供器，以及节点初始化和类名 getter。
表中行号均对应该提交，不能直接套用到设备 APK 的混淆类。

| 位置 | 未设置类名的条件 | 源码 |
| --- | --- | --- |
| 回复引用，虚拟 ID 497 | `REPLY` 分支设置描述、坐标及点击动作，未设置类名 | [ChatMessageCell.java:27805](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java#L27805) |
| 转发来源，虚拟 ID 494 | `FORWARD` 分支同样缺少类名 | [ChatMessageCell.java:27828](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java#L27828) |
| 投票选项，虚拟 ID 500–999 | `pollVoted == true`；只有未投票分支设置 Button 类名 | [ChatMessageCell.java:27679](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java#L27679) |
| 通话接听界面的宿主，虚拟 ID -1 | `obtain(hostView)` 后只设置包名和子节点，没有调用 View 初始化 | [AcceptDeclineView.java:512](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/TMessagesProj/src/main/java/org/telegram/ui/Components/voip/AcceptDeclineView.java#L512) |
| 联系人操作，虚拟 ID 489/490/491 | `contactButtons` 中没有对应类型时，循环结束后仍返回缺少类名的节点；属于状态失配路径 | [ChatMessageCell.java:27759](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java#L27759) |

消息提供器也会为未命中任何处理分支的虚拟 ID 返回未初始化完整的节点。
上表各路径都由当前 hook 统一处理类名；不会补造文字、边界或其他缺失属性。
回复和转发分支的内容在 `contentDescription`，修复不会将其复制到 `text`。

第三个自定义提供器
[ProfileActionsView.java:1253](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/TMessagesProj/src/main/java/org/telegram/ui/Components/ProfileActionsView.java#L1253)
会初始化宿主，并为子节点设置 Button 类名，本次未发现遗漏。
普通 View 的相关重写调用父类初始化或显式设置类名，未发现同类问题。
RecyclerView 的 LayoutManager 方法仅补充已经初始化的节点，不是独立节点工厂。
以上是本地源码的静态检查结果，不代表对所有运行时状态的穷举。

另以设备提取的 Telegram 13.2.2（73031）APK 检查消息提供器
`org.telegram.ui.Cells.a1`，确认其回复、转发分支仍缺少 `setClassName`。
本地源码中的其他缺失路径不能仅凭此视为已在该 APK 上逐项复现。

## 验证

- `:app:assembleDebug`、`:app:assembleRelease` 构建通过。
- 开关检查调用实际编译的 `isFeatureEnabled` 和 `isEnabled`，验证新配置、旧配置默认开启，
  显式关闭的序列化保存、重新开启、Telegram 总开关，以及其他设置字段的保留。
- 使用生产查询函数检查官方 13.2.2、官方 13.1.1（72371）、NaGram 12.10.3（1249），
  三个 APK 均定位到消息、资料页操作、通话接听这三个提供器。
- 在独立 Android 14 / API 34 模拟器中，通过 `app_process` 调用 debug APK 中实际编译的
  修复函数，验证回复、投票文本 span、宿主、已有类名、空字符串、重复调用，以及封装进
  Parcel 后的结果；文字、描述、坐标、动作、状态、extras 和 unique ID 均保留。
  验证后已关闭该模拟器，测试入口不包含在模块源码中。

APK 定位检查已加入 `scripts/check_telegram_apks.py` 的
`--feature FixAccessibilityNodeClassName`。本机静态检查使用 JDK 17 和
`-Pkotlin.compiler.execution.strategy=in-process`，避开本地旧 Kotlin 编译器与 JDK 25
的兼容问题。

分析样本、临时测试和日志位于
`~/Documents/Codex/scratch/MyInjectorPriv/telegram-accessibility-class-name/`。
没有安装模块或重启设备上的 Telegram，尚未验证真实 Xposed 回调和 fooView 的端到端取词。
更新模块后需重启 Telegram，再在回复引用、转发来源及投票结果上验证。

增加开关时运行了 `:app:testDebugUnitTest`：70 项中 62 项通过、6 项跳过、2 项失败。
失败项为 `SettingsCatalogTest.preservesExistingStorageKeysAndDefaults` 和
`PreferenceSyncTest.schemaExactlyMatchesEditableCatalog`，原有断言未包含
`fixHyperOS4GreezeFCMRestrict`，与本次 Telegram 设置无关。
