# 在视频页打开评论链接

## 原因与行为

“复制评论链接”会保留 `#reply{评论ID}`，浏览器需要这个 fragment 才能定位评论。
用户实测发现，App 打开带有这个 fragment 的链接时，会进入单独展示评论的页面，
看不到视频，而且该页面的评论菜单还没有“复制评论链接”。
去掉 fragment 后，App 能打开视频并按查询参数定位到评论，这是希望保留的 App 行为。

因此不修改复制出去的链接，单独增加 `BiliHandler.hookCommentLinkInVideo`：
只在 App 的视频路由入口，把本次请求副本中的 `#reply` 去掉。
浏览器仍收到带 fragment 的原始链接，视频及评论定位参数也完整保留。
这项功能和评论菜单复制功能分别实现、分别提交。

例如楼中楼链接：

```text
复制及浏览器：
https://www.bilibili.com/video/av123?comment_on=1&comment_root_id=456&comment_secondary_id=789#reply789

App 内路由：
https://www.bilibili.com/video/av123?comment_on=1&comment_root_id=456&comment_secondary_id=789
```

只处理 `www.bilibili.com/video/{av或BV}` 的 HTTP/HTTPS 链接，
且要求有效的主评论 ID、可选的楼中楼 ID，与 fragment 的目标 ID 一致。
缺少定位数据或目标冲突时保持宿主行为，不猜测楼中楼的主评论 ID。
动态、专栏、其他 fragment 及显式的 `bilibili://comment/detail/...` 链接不在此功能范围内。

## 实现

9.19.0（9190400）的两处视频路由都保留了读取 fragment 并生成独立评论页的代码：

| 路由 | 样本中的方法 | 相关字节码位置 |
| --- | --- | --- |
| 旧路由 | `jok2.intercept(RouteInterceptor.Chain)` | `:label_007a` 至 `:label_0104` |
| Theseus2 路由 | `com.bilibili.ship.theseus2.detail.route.ugc.b.a(RouterTargetContext, String, pzf2)` | `:label_01d7` 至 `:label_0250` |

上述分支从视频路径提取 aid、从 fragment 提取评论 ID，生成
`bilibili://comment/detail/1/{aid}/{评论ID}`，并附加 `anchor`、`showEnter` 等参数。
Hook 通过字符串和方法签名定位入口，缓存到 `commentRoute.*`。
安装和卸载与其他 Bili Hook 使用同一列表。

宿主会把 URL 查询参数拆入请求数据，`getTargetUri()`、`getUri()` 可能只有路径和 fragment。
不能仅检查这些 URI 的 query，否则真实请求会漏掉评论 ID。
旧路由从 `RouteRequest.getUniformUrl()` 读取完整查询参数，
用 `newBuilder()` 复制请求，仅清除目标 URI 的 fragment，保留已拆出的参数和附加数据。
Theseus2 从 `RouterTargetContext.getUriParam()` 读取评论 ID，
使用宿主 URI 解析器构造去掉 fragment 的 URI。

两个入口各自使用宿主接口的代理，分别替换 `getRequest()` 或 `getUri()` 的返回值，
其余调用转交给原对象，不修改共享请求，也不跳过原路由的其他处理。
实现见 `BiliCommentRoute.kt`；“复制评论链接”的内容生成逻辑无需修改。

## 验证

专项测试共 19 项：18 项通过，1 项因缺少 9.17.0 样本跳过，Debug APK 构建通过。
新增测试覆盖主评论、楼中楼、参数已拆出 URI、编码参数、分 P、非目标链接和定位数据冲突；
真实 APK 回归核对两个路由方法的定位结果。

设备上使用独立 `app_process` 加载宿主 APK 和未安装的模块 APK：

- 调用原旧路由拦截器，复现跳到独立评论页的结果。
- 用同一请求经过新处理后，再调用原拦截器，确认进入后续路由，不再重定向到独立评论页。
- 确认主评论 ID、楼中楼 ID、分 P、编码参数、flags、requestCode 和 options 保留，原请求不变。
- 对 Theseus2 的宿主 URI 与接口代理，确认 fragment 被去掉，其余参数接口仍委托原对象。

探针没有发起网络请求、写入剪贴板或启动 Activity。
以上验证覆盖路由数据和旧拦截器的实际执行，安装后的完整界面跳转仍待实测。
