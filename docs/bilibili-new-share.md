# 哔哩哔哩新版框架复制原始链接及文本

## 范围

对 `kntr.common.share` 框架的 `COPY` 渠道，使用本地已有的视频、动态或图文链接，
跳过获取在线分享内容的 `/x/share/click` 请求。
视频复制“标题＋链接”，图文复制“标题＋正文＋链接”，各部分换行分隔。
相同标题与正文只保留一份，正文中的换行、段落与表情保留，不主动截断。
文本取自本地分享模型；若宿主只提供摘要，就复制已有摘要，不请求补全文本。
视频保留分 P 等语义参数，去掉 `share_session_id`、`share_source`、`bbid`、`share_medium`。
直播、番剧、短链、普通文本等内容不做本地替换；旧框架的 `hookShare` 保留。

将 `ff_share_kt_imp` 的分享选择入口固定返回 true，避免下发配置关闭时切回旧框架。
作用范围是读取该开关的视频、图文等入口，不将所有业务入口强行替换为同一实现。

这不是整个分享流程的断网开关。打开面板的渠道列表请求、分享完成上报仍由宿主处理。
用户已反馈功能正常，下面的自动验证未覆盖完整界面和抓包验收。

## 实现

复制内容使用两个 Hook：

1. 在 `ShareConfig` 的内容提供方法返回后，检查目标为 `COPY` 且结果是
   `CopyShareContent`，再读取其内容工厂闭包捕获的 `ShareBaseInfo`。
   视频使用 `title` 和 `link`；图文使用 `title`、`content` 和 `link`，
   返回包含文本和原始链接的宿主 `CopyShareContent`。
   视频默认的本地 COPY 内容是作者和分 P 描述，因此不直接把它当作视频标题。
   R8 可能将闭包捕获字段的声明类型合并成 `Object`，这里检查字段值的实际类型。
2. 在 `ShareEngine.shareToTarget` 进入时，对上述链接的 COPY 内容设置原生
   `disableFetch=true`，沿用复制 Handler、成功结果与面板收尾。
   只验证、清理最后一行的 URL，前面的标题和正文保持原样。
   其他配置直接提供的本地动态 URL 也可经过这个入口。

不修改共享的 `ShareBaseInfo`，其他分享渠道的标题、正文和链接不受影响。
无法取得有效本地链接时不改变当前任务。

通过日志字符串、方法签名和调用关系定位混淆成员，并缓存到 `newShare.*`。
`ShareBaseInfo` 的三个文本字段按 `toString` 读取 title、content、link 的字节码顺序定位，
不写死字段名。所有 Hook 进入 `BiliHandler` 的卸载列表。

框架选择另加一个 Hook：从 `ff_share_kt_imp` 所在 lambda 的构造调用定位初始化类，
再找到该类读取 `Lazy` 的静态无参 boolean 方法，在入口直接返回 true。
这覆盖首次读取和已经缓存 false 的情况，不修改配置存储或其他灰度开关；卸载 Hook 后恢复宿主逻辑。

## 9.19.0 样本

设备包名 `tv.danmaku.bili`，版本 `9.19.0`（`9190400`）。
APK SHA-256：`a9a0ed372797d8310ebff3b7237ea9605eea6b41a3e2298eef6a9faae1d0fbd6`。

本样本对应关系如下，实际实现不依赖这些混淆短名称：

| 成员 | 本样本 |
| --- | --- |
| 分享引擎 | `ShareEngine.a(wwe, uwe, boolean, ShareLocalContext, Continuation)` |
| 配置内容提供方法 | `j162.a(wwe)` |
| 配置的内容提供器字段 | `j162.a: q162` |
| 选择内容工厂 | `q162.a(wwe)` |
| 标题 | `a062.a: String` |
| 正文 | `a062.b: String` |
| 本地原始链接 | `a062.c: String` |
| COPY 内容 | `dr10.g: String` |
| 新版框架选择 | `ez52.a(): boolean` |

Theseus2 视频的 `playercontainer.h.a()` 会生成
`https://www.bilibili.com/video/{BV或av}`，分 P 大于 1 时添加 `?p=`。
对应的 COPY 工厂 `h0d0` 原来读取 `ShareBaseInfo.content`，Hook 改用 `title` 和 `link`。
Theseus 的 `UGCActionDelegate.j0()` 也构造同类内容提供器。

图文详情的 `OpusDetailShareSession$launch$2` 优先使用 `dynIdStr`，为空时使用 `oid`，
生成 `https://t.bilibili.com/{id}`。COPY 工厂 `rcb` 的本地内容已经是这个地址。
实现复用宿主生成的 URL，不自行将任意资源 oid 转换为动态 ID。

## 验证

本地链接测试覆盖分 P、时间参数、fragment、编码参数、动态、opus，
以及短链、直播、番剧、异常地址和其他域名不被处理。
文本测试覆盖视频标题、多段长正文、表情、空文本、标题正文去重，以及最后一行 URL 的判断。

可选的真实 APK 定位回归：

```sh
export BILI_NEW_SHARE_APK=/path/to/bili-9.19.0/base.apk
export DEXKIT_HOST_LIBRARY=/path/to/host/libdexkit.so
./gradlew :app:testDebugUnitTest --tests 'io.github.a13e300.myinjector.bili.*'
```

9.19.0 的八个定位结果与原始字节码一致，专项测试 8 项中 7 项通过，
1 项因缺少 9.17.0 样本跳过，Debug APK 构建通过。
框架开关回归同时确认定位到 `ez52.a()`，该方法由视频分享及图文详情的新旧分支读取，
内部只读取缓存的开关值。固定返回 true 后，用户反馈暂未观测到问题。
此前全量回归的两项既有失败是 `PreferenceSyncTest.schemaExactlyMatchesEditableCatalog`
和 `SettingsCatalogTest.preservesExistingStorageKeysAndDefaults`，其预期未包含
已有的 `fixHyperOS4GreezeFCMRestrict` 设置项。

设备上使用独立 `app_process` 加载真实 APK 与未安装的模块 APK，直接调用同一组处理方法：
视频分 P 复制标题和原始链接，图文动态复制标题、多段正文和原始链接，
且引擎参数变为 `disableFetch=true`；
直播链接、b23.tv 以及非 COPY 目标均保持原值。
测试没有调用真实分享引擎、发送请求或写入剪贴板，设备临时文件已清理。
此验证覆盖实际宿主类的反射读取与参数处理，不代替模块安装后的界面验收。
