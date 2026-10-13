# MIUI 相册预览页照片详情

## 分析来源与范围

参考工作区 `YukiVoyager_4.0.0.Beta3(1).apk` 的设置绑定 `yuki.starlight.voyager.databinding.GalleryContentBinding`、界面 `GalleryFragment` 与实际实现 `yuki.star.voyager.hook.hooks.gallery.InternalPhotoPage`。

参考 APK SHA-256：`7c2444ae13bd58408d9ca827ec890c376543af6d822084d8c00dbb275d00712d`（只记录分析来源，不作为运行白名单）。

绑定类只是界面控件集合；`internalPhotoPageSwitch` 对应 `Internal_Photo_Page` 配置，实际 hook 为 `com.miui.gallery.activity.InternalPhotoPageActivity.onCreate` after。Yuki 从 Intent 的 `photo_transition_data` 读取 `path`、`mimeType`、`location`、`fileLength`、`createTime`，结合文件及 EXIF 显示详情。完整 JADX 反编译报告 5 个错误，目标绑定、设置与 hook 类已输出，可读；不声称整个 APK 无错误反编译。

本适配重新实现原生 Android UI 与元数据读取，未复制 Yuki 的布局、主题、图标、YukiHookAPI 或全套功能。用户选择的范围仅为“照片预览页显示更多详情”；不包含高端设备、AI 写真、Google 同步、独立照片编辑器等开关。原实现通过 root shell 启动截图编辑器的入口不迁移。

## MyInjector 接入

- 仅 `com.miui.gallery` 主进程，原 scope 已包含此包。
- `MiuiGalleryHandler` 使用已有 `DynHookManager` 和独立 protobuf 配置；详情开关默认关闭。
- 模块设置的 Apps 区新增“打开相册设置”；设置保存在相册自身应用数据目录。
- 独立详情 hook 与既有分享 hook 隔离，旧版分享类缺失不会阻止详情 hook 安装。
- 详情按钮位于预览 Activity 内，不申请系统悬浮窗权限，不使用 root，不改变 Android 文件/媒体访问权限。
- IO 在后台执行；无 EXIF、文件不可读、媒体查询无权限时显示可用信息，不让相册崩溃。

## 使用

1. 在 LSPosed 中将 MyInjector 作用域勾选“小米相册 / com.miui.gallery”。
2. 重启相册，打开 MyInjector 设置 → Apps → 打开相册设置。
3. 开启“照片预览页显示更多详情”，重新进入照片预览页。
4. 点击页面左下角的小型信息图标查看详情，可隐藏入口、编辑或复制全部。

## UI 资源与交互

已读取 Yuki 的 `float_button.xml`（25dp 容器、固定 top/start 偏移）、`button_drawable.xml`（半透明白色、20dp 圆角）及 `BlurDialog`（居中、模糊半径48、隐藏/编辑/复制操作）。MyInjector 不沿用固定 775dp 顶部偏移，而是将32dp信息图标锚定左下角，并按系统安全区留出底栏空间。模块新增自己的 vector 信息图标，不复用 Yuki 品牌图片。

详情弹窗使用原生控件重建圆角半透明面板，紧凑排版，标题显示实际 Media ID。Android 12+ 尝试系统背景模糊，不支持或被系统关闭时正常回退。隐藏只隐藏当前预览页面的入口；编辑使用标准 `ACTION_EDIT` 和可授权的 content URI，不执行 root shell，不通过 file URI 绕过权限。无法取得媒体 URI 或找不到编辑器时提示原因。


## 验证限制与设备回归

本次工作区没有提供实际 MIUI 相册 APK，也没有已连接设备。Yuki 的反编译证据能说明原实现原理，不能证明目标相册版本保留同样的类、方法与 Parcelable 字段。类或签名缺失时应记录并跳过；编译成功不等于 MIUI 真机已通过。

请验证：开关默认状态、启用/关闭后重新进入预览、重复打开不叠加按钮、JPEG 带/不带 EXIF、PNG 截图、视频、缺失路径及读取权限、屏幕旋转和快速退出、长路径复制、媒体查询失败、已有分享功能无回归。

当前 Intent 元数据可能只代表进入预览时的照片；若目标相册滑动切图不更新 `photo_transition_data`，不得将详情误认为当前滑动选中的照片。需要提供目标相册 APK 后分析当前项目 accessor，才能完成这项版本专属适配。
