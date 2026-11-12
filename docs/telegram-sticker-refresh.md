# 打开贴纸包时刷新

在 Telegram 的 MyInjector 设置 →「Emoji 和 Sticker」中开启「打开贴纸包时刷新」。默认关闭，开启后新打开的普通贴纸包及自定义 Emoji 包弹窗都从服务器获取整包信息，已有图片和动画文件继续复用。

## 实现

- 普通贴纸包：每个弹窗首次调用加载方法时清除该弹窗持有的整包对象；若只有完整包对象、没有请求标识，按包 ID 和 access_hash 补齐标识。在这次同步调用期间，让 ID、名称两个内存查询返回空，随后由原生代码发送 `messages.getStickerSet(hash=0)`。返回后仍使用原生缓存写入和界面更新。后续通知引起的界面重载不再强制请求，避免循环。
- 自定义 Emoji 包：只在弹窗 show / loader init 的调用期间，将单包缓存查询替换为原生 `fetchStickerSetInternal`，直接发送 hash 为 0 的请求。收到完整结果后用 `putStickerSet` 更新缓存，并发送原生 `groupStickersDidLoad` 通知。
- 多 Emoji 包：原生通知处理会填充所有空槽，因此某个包先返回时，其他包可能从旧缓存被提前填充。模块等待该弹窗的全部请求完成后统一发布结果；等待期间跳过该加载器的通知处理。失败时沿用原生错误回调，不发布可能从旧缓存补齐的列表。
- 只绕过弹窗加载使用的缓存，不清空全局贴纸缓存，不修改数据库结构，也不删除媒体文件。

## APK 适配

通过 DexKit 定位构造参数中的协议类型、请求字段、加载方法及通知关系，不依赖贴纸弹窗类名。官方版和旧 NaGram 的 R8 已删除 loadStickerSet 的 force 参数，且将 Emoji 加载器的 start/init 内联到 show；未混淆版本仍保留这些方法，按实际调用位置挂接。

对实际加载入口执行 deoptimize，避免 ART 把缓存查询内联到调用者后绕过 Hook。依据 libxposed API 102 的 `XposedInterface.deoptimize` 文档（本地 sources.jar 的 XposedInterface.java 第 472–487 行），需要处理的是查询的调用者。

本功能的成员定位、回调接口、内存查询调用点及请求默认 hash 检查通过以下四个 APK：

- 官方 Telegram 13.1.1（72371）。
- AyuGram 13.0.3（71835）。
- exteraGram 13.0.3（71835）。
- NaGram 12.10.3（1249）。

静态检查已加入 `scripts/check_telegram_apks.py`。同时修复检查入口未设置资源 APK 路径的问题，官方 split APK 会与 base 一并提供给资源解析器。

可通过新增的 `--feature` 参数单独复查本功能：

```sh
python3 scripts/check_telegram_apks.py --feature RefreshStickerPacks \
  ~/tg-apks/telegram-13.1.1-72371/base.apk \
  ~/tg-apks/AyuGram-arm64-v8a-20261028.apk \
  ~/tg-apks/exteraGram-arm64-v8a-20261028.apk \
  ~/nagram-1249.apk
```

## 验证与边界

debug、release 构建及现有单元测试通过。额外用生产 Hook 文件和真实 DexMethod / DexField 反射包装，在 JVM 模拟宿主中验证无 force 参数及有 force 参数两种入口，包括 ID、名称、仅预加载整包、开关关闭、通知重载、请求失败、多包乱序返回、并发弹窗和调用范围恢复。2026-11-12，用户已在真机确认功能可用；四个 APK 的专项静态检查不代表各版本均已完成真机验证。

完整 APK 回归在官方版通过；AyuGram 的完整回归另外暴露现有 CustomMapPosition、Anchored search state、UseSystemEmoji 的定位失败，这些查询未在本次改动中调整。不能把本功能四个 APK 的专项检查结果理解为全部功能都通过。

开启后需要联网才能取得新包内容。它不修复 Telegram 原有 stickersets2 主键拼写和缓存重复记录的问题，但本功能的首次远端加载不会从该表读取旧包。已经打开的弹窗不会定时轮询服务器；关闭后重新打开会重新请求。
