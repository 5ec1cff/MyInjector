# 应用图标

图形由开放的应用方框、指向框内的箭头和右上角模块块组成，表达模块向应用接入功能。
使用粗线条和圆角，缩小后仍可辨识；不添加文字或细节纹理。

配色沿用 InjectorTheme 的 Material 3 基础紫色：

- 背景 `#EADDFF`：primaryContainer。
- 应用方框 `#21005D`：onPrimaryContainer。
- 箭头与模块 `#6750A4`：primary。

Android 8 起使用分层 adaptive icon，背景与前景由系统按桌面形状裁切。
Android 13 起提供 monochrome 图层，由桌面的主题图标功能着色。
全彩图标使用上述基础配色，应用界面在支持时仍使用动态配色。
另提供五档 PNG 资源，供传统图标读取器使用。

前景使用原生 VectorDrawable，108×108 坐标中的关键图形位于中心 66dp 安全圆内；
无需额外图标库。SVG 与 512px 预览保存在 [assets/app-icon.svg](assets/app-icon.svg)、
[assets/app-icon.png](assets/app-icon.png)。SVG 的可见区域对应 adaptive icon 的中心遮罩区域。
