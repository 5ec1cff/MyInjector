# 视频菜单查看封面

在 Theseus 视频页右上角三点菜单的第二行增加“查看封面”，紧接“查看字幕”，其余按钮顺序不变。
点击后先关闭菜单，再用应用内置的 `com.bilibili.bplus.baseplus.activity.ImagesViewerActivity` 打开当前视频封面。
查看器底部提供保存按钮，图片下载、权限处理和保存结果提示均沿用宿主实现。

## 实现

复用 `BiliSubtitleToolbar` 已验证的操作行插入点，字幕和封面各用独立的点击标记与禁用状态。
封面缺失时仍显示“查看封面”，置灰禁用。
图标是本地生成的图片轮廓 PNG，缓存到宿主应用的 `cache/myinjector_cover_icon.png`，无需联网下载。

`BiliCover.snapshot` 从该工具栏的 `PlayerContainer` 取得 `IPlayDirector.getCurrentPlayableParams()`，
读取 `GeminiCommonPlayableParams.getCover()`。封面地址在打开菜单时保存，不使用全局最近视频缓存，
不为此请求视频信息；`//` 地址补为 HTTPS，保留原封面地址，不添加缩略图参数。

`BiliCover.show` 构造宿主 `ImageInfo`，同一个封面地址用于预览、原图和缓存图。
调用宿主的 Intent 创建方法，生成图片列表、起始索引、`can_download=true` 等参数，
再显式启动上述查看器。混淆入口缓存在 `cover.intent`，无需模块自己下载图片或申请存储权限。

宿主布局 `bplus_activity_images_viewer` 的底部按钮栏默认 `alpha=0`，正常从图片缩放进入时，
由打开动画将它变为可见。从封面菜单进入没有缩放起点，因此使用独立 Intent 标记，
只在这次查看器的 `onCreate` 后将 `bottom_layout.alpha` 设为 1，显示原生保存按钮。
其他入口打开的图片查看器不受影响。

9.19.0（9190400）的对应成员：

| 功能 | 成员 |
| --- | --- |
| 当前视频封面 | `GeminiCommonPlayableParams.getCover()`，读取 `J:String` |
| 创建查看器 Intent | `ImagesViewerActivity.m9(FragmentActivity, ArrayList, ArrayList, CropType, Rect)` |
| 图片数据 | `com.bilibili.bplus.imageviewer.ImageInfo` |
| 保存按钮点击 | `ImagesViewerActivity.onClick(View)` |
| 下载原图 | `f2t0.then(Task)`，使用 `BiliImageLoader.useOrigin()` |

反编译源码中，`GeminiCommonPlayableParams.java:198` 返回封面字段。
`ImagesViewerActivity.m9` 生成可下载的 Intent；`onCreate` 根据 `can_download` 显示保存按钮，
`onClick` 请求宿主存储权限后，由 `f2t0` 下载并保存原图。
这些源码来自本地 9.19.0 APK，仅用于分析，不随项目分发。

初版使用通用 `MediaViewerActivity`，图片能打开，但其默认 `ImageFragment` 没有保存入口。
因此改用带保存按钮的宿主查看器，避免把“能查看”误当作“能保存”。

## 验证

专项测试 26 项：25 项通过，1 项因缺少 9.17.0 样本跳过；Debug APK 构建通过。
真实 APK 测试检查带保存功能的查看器 Intent 创建入口。
用户已确认按钮顺序和封面查看可用。
修正保存入口后已实际点击右下角下载图标，宿主提示保存成功，且在 `Pictures/bili` 中确认生成 JPG 文件。
