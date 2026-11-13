# 评论菜单直接复制链接

## 范围

在 `kntr.common.comment` 的评论长按菜单第一组添加“复制评论链接”。
支持视频、图片动态、文字动态，以及这些内容的楼中楼回复。
覆盖新版菜单的页内面板、底部面板和气泡三种样式。
其他评论类型暂不添加此项，旧版 `comment2`、`comment3` 菜单暂未接入。

点击后直接写入系统剪贴板，再关闭当前菜单。
链接只使用本地菜单模型里的内容 ID、评论类型、评论 ID 和主评论 ID，
不调用评论分享素材接口、分享引擎或短链接接口。
原有“分享”和“复制文本”入口保持原行为。

## 链接格式

依据用户提供的三条实际分享短链接核对重定向：

| 样例 | 重定向中的定位参数 |
| --- | --- |
| `https://b23.tv/aOiQtP9` | `t.bilibili.com/415375385?comment_on=1&comment_root_id=323854887520&type=2#reply323854887520` |
| `https://b23.tv/ZYvkIFZ` | `www.bilibili.com/video/BV1s8WJ6ZEhQ?comment_on=1&comment_root_id=323354225632#reply323354225632` |
| `https://b23.tv/HkfQXwU` | 同一视频，增加 `comment_secondary_id=323354341104`，fragment 改为 `#reply323354341104` |

本地生成时不添加 `share_tag`、`unique_k` 等追踪参数。
视频菜单只有 aid，使用指向同一视频的 `https://www.bilibili.com/video/av{aid}`，
不为获取 BV 号再请求接口。图片动态的评论类型为 11，使用内容 oid 并保留 `type=2`；
文字动态的评论类型为 17，直接使用动态 ID。
主评论的 root 为 0 或等于自身 ID 时，只保留 `comment_root_id`。
楼中楼同时保留主评论 ID 和目标回复 ID，fragment 始终指向被长按的那条评论。
App 内保留视频的打开行为由独立 Hook 处理，理由与实现见[评论链接路由](bilibili-comment-route.md)。

## 实现与样本

样本为 `tv.danmaku.bili` 9.19.0（9190400），沿用新版分享分析中的 APK。
通过 Compose 日志字符串定位评论菜单及三种渲染入口，
通过 `MoreMenuOverlay`、`MoreMenuEntry` 的 `toString` 字节码读取顺序定位混淆字段。
定位结果缓存在 `commentLink.*`，Hook 进入 `BiliHandler` 的卸载列表。

| 成员 | 9.19.0 对应关系 |
| --- | --- |
| 评论菜单 | `CommentMoreMenuKt.a(c1f1, ...)` |
| 评论 ID、主评论 ID | `c1f1.b`、`c1f1.c` |
| 内容 ID、评论类型 | `c1f1.x: Long`、`c1f1.y: Integer` |
| 菜单项 | `v0f1(String, Painter, String, Painter, Function0)` |
| 页内、底部、气泡入口 | `moremenu.j.a`、`moremenu.k.f`、`MoreMenuBubbleKt.c` |

`ActionBarReducerKt$onActionBarActions$5.invokeSuspend` 从当前评论配置的 subject
取 oid 和 type，传给菜单 action；`page.service.b.w` 再写入 `MoreMenuOverlay`。
不需要从全局最近一次评论点击推测当前内容。

在评论菜单方法进入和退出时维护当前线程的调用栈，只在此范围内向渲染参数添加菜单项。
复制外层列表及第一组列表，不修改 Compose 持有的原列表；已有新增项时不重复插入。
沿用第一组最后一项“复制文本”的图标。回调实现宿主类加载器的 `Function0`，
返回宿主 `Unit`，避免模块与宿主 Kotlin 类型不一致。

## 验证

链接测试覆盖图片动态样例、视频和文字动态楼中楼、主评论 root 两种表达、无效 ID 和未支持类型。
真实 APK 测试核对菜单、三个渲染入口及六个字段的定位结果。

设备上另用独立 `app_process` 加载真实宿主 APK 与未安装的模块 APK，
验证三个渲染入口都能插入宿主菜单项，目标回复参数正确、回调属于宿主类加载器、
原列表未被修改、重复处理不重复添加、离开评论菜单后不再插入。
该探针没有调用菜单点击回调，没有请求网络或写入剪贴板，不代替安装后的界面验证。

专项单元测试共 13 项：12 项通过，1 项因缺少旧版 9.17.0 样本跳过。
Debug APK 构建通过。
用户已安装验证，反馈“复制评论链接”功能可用。
