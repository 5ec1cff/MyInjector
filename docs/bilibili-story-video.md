# 替换竖版视频

参考 BiliRoaming 的 `StartActivityHook` 与 `ProtoBufHook`，将带稿件 ID 的 Story 视频改用普通
视频详情页打开，并关闭横屏、连播时自动进入 Story 的配置。不改变视频画面本身的比例。

开关位于「MyInjector 设置 → 视频工具 → 替换竖版视频」，JSON 键为 `replaceStoryVideo`，
默认开启。所有 Hook 在启动时注册，回调读取当前开关；切换后对下一次跳转和配置读取生效，
不改变已经打开的页面。定位或运行错误归入此功能的 Hook 错误汇报。

## 路由处理

当前 9.19.0 同时包含旧版 Activity 路由和新版页面路由。新版 Story 可以在同一个 Activity 中
打开，因此不照搬修改最终 Intent 的方式，而是在匹配路由前替换请求：

- 旧版 `BLRouter.newCall`：复制原始 `RouteRequest`，将 Story URI 改为 `bilibili://video/{id}`，
  保留请求的额外参数、回调、flags 与动画，并移除强制进入 Story 的 `-Arouter`、`-Atype` 参数。
- 新版 `kntr.base.router.Router.find/launch`：将 Story URI 改为
  `experimental-bilibili://video/{id}`，交给普通视频的参数转换逻辑，再创建详情页。
  同时覆盖 `bilibili` 和 `experimental-bilibili` 两种 Story scheme。

保留原始查询参数编码、重复参数和 fragment，包括播放进度、分 P、预加载与评论定位信息。
URI 中仅删除值为 `story` 的 `-Arouter`、`-Atype`；不请求视频信息，不手工伪造播放预加载数据，
也不固定普通视频页最终采用哪一套框架。

只替换正数 AV ID（可带 `av` 前缀）或 BV ID 对应的 Story 路由。没有稿件 ID、ID 为 0 的随机
推荐入口，以及直播、番剧等其他路由保持宿主行为。本功能覆盖通过上述路由打开的视频，
不拦截其他代码直接显式启动 Story Activity 的 Intent。

## 自动进入 Story

9.19.0 的实际读取方已经使用 Kotlin 配置，BiliRoaming 所 Hook 的旧 `PlayConfig` getter
只剩 protobuf Builder 调用。因此在当前版本改为让
`KPlayConfig.getLandscapeAutoStory()`、`getShouldAutoStory()` 返回默认 `KBoolValue`，即关闭状态。
已确认普通播放设置、旧版播放器配置与新版 `PlaySettings` 的调用链使用这两个 getter。
关闭功能后恢复宿主的原始返回值，不改写宿主保存的播放偏好。

## 验证范围

通过当前 APK 的路由注册、请求构建和播放配置源码确认成员与调用链。
Release 编译通过，用户已安装测试并确认功能有效。
