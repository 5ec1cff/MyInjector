# exteragram：阻止切换频道与显示消息 ID

## 分析对象与隔离策略

- APK：`exteraGram-full-universal-20261004.apk`。
- SHA-256：`1ed01526c0cb57bc798c29cc06ab4d01d30282ce2cc1b177f0222e906c22581f`（用于记录分析对象，不作为运行时白名单）。
- 基于工作区 `MyInjector-upload` 的 `compat/exteragram-224` 分支修改。
- `Entry` 已将 `com.exteragram.messenger` 单独路由到 `telegram.legacy224.TelegramHandler`。新增 hook 和设置仅在该路径生效，官方 Telegram 的 281/DexKit 适配保持不变。

使用 JADX r2655.bce6611 反编译，并直接检查 APK 内 DEX 定义。这个 APK 并非所有符号都被重命名：本次功能所需的类、方法与字段保留名称；但数据类型和控制逻辑不能按官方版假定。全量 JADX 会对部分第三方代码报告错误，不应把其伪代码当作完整、可编译源码；本次目标成员另行按 DEX 签名核对。

## 显示消息 ID

实际目标：

| 成员 | APK 中的类型/签名 |
| --- | --- |
| `org.telegram.ui.Cells.ChatMessageCell.measureTime` | `(org.telegram.messenger.MessageObject)void`，实例方法 |
| `currentTimeString` | `android.text.SpannableStringBuilder`，不是官方识别器要求的 `CharSequence` 字段 |
| `timeWidth` / `timeTextWidth` | `int` |
| `isMegagroup` | `boolean` |
| `org.telegram.ui.ActionBar.Theme.chat_timePaint` | 静态 `com.exteragram.messenger.utils.ui.TextPaint`，继承 Android `TextPaint` |
| `MessageObject.messageOwner` | `org.telegram.tgnet.TLRPC$Message` |
| 消息所有者的 `id` / `realId` / `post_author` | `int` / `int` / `String` |

在 `measureTime` 返回后，在时间前加入 ID；`realId != 0` 时使用 `realId`，否则使用 `id`。与 281 行为一致，仅在超级群中来源频道 ID 等于对话 ID 时恢复被原方法省略的 `post_author`，并移除换行。

关键差异处理：

- 使用 `SpannableStringBuilder` 保留编辑标记、星星、TON 等 span，不能给该字段直接赋 `String`。
- 对自定义画笔使用 `TextPaint.isAssignableFrom`，不要求字段声明类型恰好是 Android 的 `TextPaint`。
- 同步增加 `timeTextWidth` 与 `timeWidth`，保留阅读数、回复、置顶等额外图标宽度。
- 使用不可复制的 span 标记防止重复注入；APK 的原方法每次重新创建时间 builder 并重算宽度，因此重复测量或 cell 复用不会累加 ID。
- 反射解析及类型校验全部完成后再安装 hook。未来版本缺少成员或类型改变时明确记录错误，而不是误命中官方混淆成员。

## 阻止切换频道

实际目标 `org.telegram.ui.ChatPullingDownDrawable` 保留名称及方法：

- `getNextUnreadDialog(long,int,int,boolean,int[])`：静态，返回 `TLRPC$Dialog`。
- `getChatId()`：返回 `long`。
- `draw(Canvas,View,float,float)` 与 `drawBottomPanel(Canvas,int,int,int)`：返回 `void`。
- `needDrawBottomPanel()`：返回 `boolean`。
- `isTopic`：实例 `boolean` 字段。

`ChatActivity.animateToNextChat()` 的非 topic 路径，仅当 `pullingDownDrawable.getChatId() != 0` 时构建下一频道的参数并打开聊天。仅把未读频道选择器返回 null 并不足够：drawable 中已有的 `nextChat`、推荐频道仍能触发跳转。

因此开启时：

1. 未读频道选择器返回 null。
2. `getChatId()` 返回 **`0L`**（必须匹配 long 返回类型），阻止已有候选频道跳转。
3. 非 topic drawable 的两个绘制方法不执行，`needDrawBottomPanel()` 返回 false。

不拦截通用的打开聊天、`presentFragment` 或点击频道入口。不把类中所有无参 boolean 方法设为 false，以免破坏动画状态。topic 使用独立的选择器及 `getTopic()` 跳转路径，保留其正常行为。

## 可重复 APK 验证

```powershell
python scripts/verify_exteragram_apk.py path/to/exteragram.apk
```

脚本只使用 Python 标准库，解析每个 DEX 的 `class_data` 定义（而非仅检查引用表），验证 10 个字段、8 个方法的精确描述符和 static/instance 修饰，以及自定义画笔的继承关系。对本次用户 APK 运行结果全部 PASS。该验证只能确认 hook 所需成员布局，不能证明 LSPosed 安装及界面行为。

## 使用和设备回归

在 exteragram 的 MyInjector 设置中，进入“其他”，打开“显示消息 ID”或“阻止切换频道”。模块必须在 LSPosed 中作用于 `com.exteragram.messenger`。

建议真机检查：

- 普通私聊、群、频道、转发、编辑消息的 ID 和时间正常显示，长 ID 不遮挡时间。
- 带星星/TON、编辑标记、阅读数、回复数和作者签名的消息仍保留原样式。
- 上下滚动导致 cell 复用、重复测量后，ID 不重复、宽度不累计。
- 打开阻止开关后，在频道末尾上拉不跳到下一未读频道；推荐频道/已缓存候选也不能跳转。
- 手动点击或通过链接打开频道正常；论坛 topic 行为不受影响。
- 分别关闭两个开关及总开关，重新进入聊天或触发重绘/测量，恢复原行为；重启后开关保存。

本环境没有连接 Android 设备，因此 APK/Dex 签名验证与构建验证不能代替 LSPosed 真机回归。
