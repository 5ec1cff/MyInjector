# 视频详情显示 AV 号

模块 App → 哔哩哔哩「应用内设置」→ 视频工具 →「显示 AV 号」。
按用户要求默认开启，JSON 键为 `showAv`。修改开关后，对新打开的页面实时生效，
无需重启；关闭时恢复宿主原有显示，不主动刷新已经打开的页面。
配置存储、错误详情和热重载卸载沿用 BiliHandler 的设置实现。
AV Hook 在启动时始终注册，选择器与标题渲染回调读取当前开关。
配置成功写入后发布到 volatile 字段，让后续回调可以立即看到新值；
其余功能同样在后续操作读取最新开关，见[设置说明](bilibili-settings.md)。

功能参考 BiliRoaming 的
[EnvHook](https://github.com/yujincheng08/BiliRoaming/blob/7c792dc8fea321ff6a308ce3424668f81019df58/app/src/main/java/me/iacn/biliroaming/hook/EnvHook.kt#L81)。
该实现通过 `bv.enable_bv` 配置与旧 BVCompat 静态开关选择 AV。
本次根据 9.19.0 APK 的实际调用链，分别处理原生选择器和新版标题区。

## 实现

### AV/BV 选择器

旧 `com.bilibili.droid.BVCompat` 和新 `kntr.base.utils.compat.BVCompat` 都有
静态 `String (String, String)` 方法，两个参数分别是调用方已格式化的 AV、BV 文案。
启用功能时直接返回第一个参数。旧 Theseus 标题区以及使用此选择器的分享文字因此显示 AV。
方法按参数、返回类型和 static 属性查找，不依赖混淆方法名。

### Theseus2 标题区

9.19.0 的新版标题区没有调用上述选择器，而是直接使用当前 BV 状态，
只在 BV 为空时才回退到 AV。因此还需要处理标题区的元信息项：

1. 用 `AV`、`bv.pattern_rule_bv_only` 和 Compose 渲染签名定位标题渲染方法。
2. 从它的调用和字段找到当前视频 aid/BV 的读取方法。
3. 在渲染调用范围内，只将与当前 BV 完全相同的 `UgcMetaItem` 文案改成 `AV{aid}`。

这同时让该元信息项原有的长按复制使用 AV。视频回复中的原始 bvid 和正文链接识别保留，
不会因为替换 BV getter 而改变简介中“当前视频”的链接判断。
aid 非正数或 BV 缺失时保留原文，不额外请求网络，也不执行 BV→AV 算法转换。
渲染范围使用线程内栈，嵌套渲染与读取失败后的清理不会串用其他视频的 ID。

样本中的定位结果：

| 用途 | 9.19.0 入口 |
| --- | --- |
| 旧选择器 | `com.bilibili.droid.BVCompat.a(String, String)` |
| 新选择器 | `kntr.base.utils.compat.BVCompat.a(String, String)` |
| 新标题渲染 | `theseus2.ugc.intro.ugcheadline.u.a(Modifier, Composer, int)` |
| 当前 aid/BV | `theseus2.ugc.intro.ugcheadline.r.c():long / d():String` |
| 标题元信息项 | `theseus2.ugc.intro.ugcheadline.y`，toString 前缀 `UgcMetaItem(text=` |

## 验证范围

已确认设备 APK 为 9.19.0（9190400），与本地分析样本 SHA-256 一致。
定位和作用域代码已加入回归用例。按用户要求，完成默认启用的修改后仅编译安装包，
不安装、不执行后续设备验证，实际标题显示、长按复制及开关恢复交由用户测试。
