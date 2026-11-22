# 抖音评论表情预览

在抖音 `com.ss.android.ugc.aweme` 的主进程加载 `DouyinHandler`。开启模块作用域并重启抖音后直接生效，没有额外功能开关。当前分析和适配样本为 41.2.0（410201）。

## 实现方式

- 半屏表情详情：通过 DexKit 定位 `EmojiServiceImpl` 中含 `short_video_comment_emoji` 的方法，只有参数场景为 `COMMENT` 时改用图片预览。
- 旧表情 Activity：拦截 `SmartRoute.open`，只处理 `//gif_emoji` 且包含 `commentMobParameters` 的评论入口。这也覆盖旧组件和旧列表。
- 复制 `Emoji.animate_url` 的 URL 模型，缺少尺寸时从表情模型补全。没有动态资源时使用 `static_url`。创建临时 `CommentImageStruct`，填入五个图片地址字段，通过 `key_image` 打开抖音原生 `ImageDetailActivity`。不修改评论数据。
- 单图预览页原生加载器启用了动画播放，GIF、动态 WebP 和动态 HEIF 使用原始动态地址。仅在模块打开的预览页增加“保存图片”按钮。
- 保存地址与预览地址分开保留。对所选的动态或静态 URL 模型，优先使用 `downUrlList`，其次使用 HTTP(S) 形式的 `uri`；两者都没有时使用 `urlList`。只在首选资源路径相同的 CDN 地址之间重试，不降级到其他格式或尺寸，也不混用动态资源和静态封面。
- 直接通过 HTTP 下载源文件，不经过预览处理管线。识别 GIF、WebP、PNG、JPEG 和 HEIF 文件头，所有格式均复制原始字节，使用对应后缀和 MIME 类型写入 `Pictures/Douyin`。不解码、缩放、重新编码或添加水印。
- 41.2.0 的评论动图可以使用动态 HEIF，`animate_url` 列表还带有 JPEG 备用版本。旧代码不认识 HEIF，错误地继续尝试备用地址，导致预览会动、保存却是 JPEG。现在直接保存 `.heic` 文件，保留全部帧、透明度和原始画质。相册能否播放动画取决于其动态 HEIF 支持。取得文件后如果格式识别或写入失败，立即报错，不再保存备用静态图。
- 最高质量以模型提供的源文件为上限。`Emoji` 没有独立的未压缩上传原图字段；如果服务端仅提供带压缩模板的 URL，模块不能恢复上传前的原图，也不修改带签名 URL 的压缩参数来猜测其他资源。

Android 10 及以上通过 MediaStore 的待完成记录写入。Android 8、9 使用目标应用的存储权限；首次授权后再次点击保存。保存由用户点击触发，网络和写入在后台线程执行。

## 验证

`EmojiImageFormatTest` 检查动态文件格式、静态文件格式，以及错误响应和不完整文件头。真实 APK 定位测试需要设置 `DOUYIN_APK` 和 `DEXKIT_HOST_LIBRARY`，分别指向 41.2.0 APK 和主机可用的 DexKit JNI 库。

设备验收应检查：评论表情进入原生图片页，GIF 持续播放，点击“保存图片”后相册文件保留动画、后缀与 MIME 类型正确，以及普通评论图片和聊天表情入口不受影响。以上设备交互需要与构建、定位测试分开确认。
