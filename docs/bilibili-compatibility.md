# 哔哩哔哩 9.17.0 适配检查

## 样本与结论

样本从当前 ADB 设备提取：`tv.danmaku.bili`，`9.17.0`（`9170300`），
SHA-256：`2e09f6d224275071a2f66c1dfe1d855c2140df9926fac3fbc1a645f6f7f2f0d3`。
APK 保存在工作目录 `analysis/bili-9.17.0/base.apk`，不提交到仓库。

当前 APK 同时包含 Theseus 与 Theseus2 两套播放页，不能只检查原有类和方法是否还存在。

| 功能 | 原实现检查结果 | 修复方式 |
| --- | --- | --- |
| 移除视频下方广告 | 确认失效；广告创建被内联到两个协程回调，原来的唯一匹配失败，设备缓存也没有该项 | 定位并拦截两个广告创建回调 |
| 移除暂停广告及倒计时 | 原 Theseus 入口有效；Theseus2 已改用独立配置类，未被覆盖 | 同时拦截新播放器的广告资格配置 |
| 移除云视听小电视角标 | 原 protobuf 响应过滤仍覆盖旧播放器；新播放器使用 KDmViewReply，未经过原 Hook | 过滤 Kotlin 模型的目标活动元数据 |
| 移除开屏广告 | 原开屏展示方法仍有效；新的 Kotlin 开屏流程通过独立容器及选择器展示，未被覆盖 | 在新流程的广告选择器返回无可用广告，保留原生结束流程 |
| 复制链接使用原地址 | 对所有 shareTo 重载读取第二个参数会在单参数调用时越界；单参数调用在 delegate 中生成 Bundle，也不会经过 wrapper 的双参数方法 | 在 delegate 的双参数分发方法处理最终 Bundle，覆盖两条路径 |

上表是字节码与反混淆检查结论；不将“Hook 安装成功”等同于完整界面验收。

## 视频下方广告

原匹配先查找 `DetailAdService-onCreateViews`，再要求一个方法同时包含
`DetailAdService`、`-`、`onCreateViews`。该版本没有原独立方法；同时包含
`DetailAdService` 和 `onCreateViews` 的方法有两个：

- `com.bilibili.ship.theseus.ugc.ad.DetailAdService$1$2.invokeSuspend(Object)`。
- `com.bilibili.ship.theseus2.ugc.ad.AndroidUnderPlayerAdService$observeAd$4.invokeSuspend(Object)`。

两个回调读取广告响应，随后创建下方广告视图、注册半屏广告回调并挂载广告容器。
它们是单次 Flow 消费回调，正常结束返回宿主的 `kotlin.Unit.INSTANCE`，没有挂起点。
新版定位保留全部匹配结果，并验证返回类型；对于协程回调返回宿主 Unit，
对于旧的 void 方法返回 null。不按方法名批量拦截其他重载。

## 暂停广告

旧 `DDConfig.getPausePageEnable()` 仍被 `PausedPageService` 使用。
Theseus2 的 `PausedPageServiceImpl` 与 `PausedPageController` 则调用
`kntr.app.ad.biz.videodetail.pausedpage.h.getEnabled()`，后者读取
`dd_ad_pausedpage_enable`。用配置键、无参、boolean 返回类型定位新入口，
在创建广告请求或启动倒计时前走原生禁用分支，不修改暂停播放本身。

## 云视听小电视角标

旧 `InteractLayerService.setDmViewReply(DmViewReply)` 的过滤保留。
对新 `KDmViewReply`，通过 `getActivityMeta()` 的字段读取指令定位实际混淆字段
（本样本为 `r: List`），在构造完成后替换目标列表。
仅移除 JSON 的 `id` 为 `10011` 的活动；其他活动、无法解析的元数据及其余响应字段保留。

不能只 Hook Kotlin getter：生成的序列化器直接读取字段，Chronos 消费的是序列化数据。
不硬编码字段名或构造函数的参数序号。

## 新开屏流程

`MainActivityV2.ka()` / `oa(boolean)` 可以经由 `splash.shell.a` 创建
`kntr.srcs.app.splash.startup.SplashContainerFragment`，绕开原有开屏展示方法。
新流程在 `kntr.srcs.app.splash.core.f.a(...)` 选择有效广告，
本地与实时选择共用此方法，冷、热启动均可到达。
用 `[KSplash]SplashOrderSelectComponent` 和 `getValidResSplash, splashId = ` 定位该方法。

在选择器返回 null 表示没有可用广告，保留请求任务完成、状态更新及页面结束处理。
不直接跳过容器初始化、取消协程或阻断结束事件；旧开屏和热开屏 Activity 拦截继续保留。

## 分享与生命周期

`ShareHelperV2.shareTo(String)` 会调用 delegate 的单参数方法，再通过
`ShareHelper.Callback.getShareContent()` 生成 Bundle，最后进入 delegate 的双参数方法。
本样本的共同入口是 `com.bilibili.lib.sharewrapper.a.shareTo(String, Bundle)`。
使用日志字符串及参数类型定位，仍按原规则替换 b23.tv 并去掉分享追踪参数。

反混淆缓存版本升级为 2，按 APK 路径及版本校验，记录所有广告回调和新入口。
扫描结束关闭 DexKit；各功能独立捕获安装错误并写日志。
热重载卸载时释放全部 Hook，避免重复叠加。应用方法的反优化由框架处理。

## 验证

`BiliApkCompatibilityTest` 是可选的真实 APK 回归测试，核对两套广告回调、分享入口、
暂停配置、Kotlin 活动元数据字段及新旧开屏入口；没有样本时跳过。
使用与项目 DexKit JNI 接口匹配的主机库运行：

```sh
export BILI_917_APK=/path/to/bili-9.17.0/base.apk
export DEXKIT_HOST_LIBRARY=/path/to/host/libdexkit.so
./gradlew :app:testDebugUnitTest
```

小型原始字节码证据保存在 `research/bili-9.17.0/`，完整反编译产物留在工作目录。

当前样本的真实 APK 定位回归及其余 JVM 测试共 37 项通过。
在设备上使用独立 `app_process` 和未安装的模块测试 APK，调用模块实际的活动元数据过滤方法，
再将合成的 `DmViewReply` 解码为宿主 `KDmViewReply`、替换已定位字段并重新序列化。
确认仅删除 `id=10011`（数值或字符串形式），保留其他活动、无效 JSON 和无 id 条目；
序列化后与原 protobuf 仅移除目标项的结果相等，其他响应字段保持不变。
测试脚本 `analysis/bili-9.17.0/BiliModelProbe.java` 留在工作目录，设备临时产物已清理。

以上检查未覆盖完整的播放、暂停、分享面板与冷/热启动界面验收。
