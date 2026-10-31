# 小米应用商店开屏广告

## 样本与启用

从当前 ADB 设备 `/product/app/MIUISuperMarket/MIUISuperMarket.apk` 提取 `com.xiaomi.market`，
版本 `4.123.10`（`40007540`），APK SHA-256：
`689f2738267ad6afe58a62440b1e593670ce487c88f1ce4f5afa7848a26f2110`。
APK 与定向反编译保留在工作目录 `analysis/xiaomi-splash-ads/`，不提交 APK。

模块新增应用商店作用域与 `XiaomiMarketHandler`。在模块主页进入应用商店的模块设置，
启用作用域并管理各功能。配置存储于模块 `market` 组 remote prefs，不写入商店的偏好文件。
“移除开屏广告”默认开启，关闭后后续广告资格检查使用原生结果。
系统应用分组及图标继续按 PackageManager 的结果显示。
只在应用商店主进程安装 Hook，卸载模块时移除全部 Hook。

新版复核样本为 `4.129.s.00`（`40007700`），SHA-256：
`bb35fb49833309548a36ae1528a047794fca49680eda132f4a0d9f30a5c0b80b`。
从设备重新提取；主进程日志出现 `market cold, foreground and detail splash ads disabled`，
证明这三个开屏入口按原类名成功安装，未进行动态反混淆。

## 原生流程

用 DexSQL 查询调用关系，并核对 Smali 与 JADX 的 `SplashManager` 定向反编译：

- 冷启动：`MarketTabActivity.trySplash()` 注册开屏结束监听，再调用 `SplashManager.tryAdSplash`。
- 后台返回：`MainAppDelegate` 的 Activity 回调调用 `trySplashWhenApplicationForeground`，
  超过配置间隔后进入相同的 `tryAdSplash`。
- 外部进入详情：`OnePixel2DetailActivity.tryToDisplaySplashAd` 检查
  `DetailSplashAdManager.canRequestSplashAd(Env)`。允许后记录关闭回调、设置请求状态，
  最终同样进入 `SplashManager.tryAdSplash`。

`tryAdSplash(Activity, String)` 首先同步调用 `shownSplashCoverIfNeed` 设置启动封面，
再异步执行 `SplashTask.run()`。任务通过 synthetic accessor 调用 `canShowSplash(Activity)`，
检查页面类型、活跃状态、网络/授权、页面开屏资格及来源。

允许展示才会初始化请求状态、安排三秒超时并调用 `SystemSplashAd.j` 请求系统开屏广告。
资格为 false 时，任务调用 `onSplashEnd(activity, "", -1, false)`，不请求广告。
后者在主线程退出全屏、移除封面，将 FocusVideo 的开屏信息置空，并通知开屏结束监听。
这些通知驱动首页、视频和等待中的页面继续，因此不能只把 `tryAdSplash` 替换为空操作。

## 内置开关与展示条件

当前 `4.123.10` 的设置资源、设置项处理代码和开屏调用链中，未发现类似米家那样直接关闭开屏的开关。
“隐私设置 → 推荐”内有“个性化服务”，存储键为 `pref_key_personalized_recommend`，默认 true，
说明文字为“允许我们向您推荐适合您的个性化内容和广告”。
`RecommendSettingsFragment.onPause` 调用 `tryUpdateRecommendState`，将该值作为
`adPersonalizedSwitchStatus` 上传给系统广告服务，未将它加入 `canShowSplash` 的跳过条件。
因此客户端证据支持它控制个性化偏好，不能据此认定关闭它即可关闭开屏。
系统广告服务如何最终使用该偏好，超出这次两个应用 APK 的分析范围。

开屏“有时显示、有时不显示”对应下列分层条件，满足请求条件仍不等于广告一定展示：

| 入口 | 本地条件 |
| --- | --- |
| 冷启动首页 | `MarketTabActivity.trySplash` 要求页面没有处于重建状态，不在联网授权或首次推荐流程中，也不在长辈、TalkBack、海外等特殊模式；调用来源须被识别为小米桌面。 |
| 后台返回 | `MarketApp.onApplicationBackground` 记录退后台时间；恢复时仅在存在记录且后台停留时长**大于** `ClientConfig.splashInterval` 时再次请求，随后重置记录。APK 默认 `60000ms`，服务端配置可覆盖；这个间隔不应解读成所有入口的全局展示间隔。 |
| 外部进入详情 | 当前无详情开屏请求，来源不是 `com.miui.systemAdSolution`，并且详情响应 `AppDetailV3.showOpenScreenAd == true`；后续仍需通过统一资格检查。 |

统一资格检查 `canShowSplash` 还要求：当前 Activity 属于商店 `BaseActivity` 且活跃、有网络、
已允许联网、页面注解 `PageSettings.needShowSplash` 允许展示。
普通页面还须 `pageRef == com.miui.home` 或调用包是小米桌面；被动详情广告可绕过此来源限制。
这里的 `BaseActivity.needShowSplash()` 读取的是页面注解，不是用户设置开关。

通过这些检查后，SDK 才向系统开屏广告服务发起请求。
服务调用失败、广告返回错误、展示错误或超过三秒超时，商店都会结束开屏流程。
服务端库存、投放频控及系统服务内的额外条件，无法仅从当前商店 APK 推断具体数值，
因此这里不将请求通过等同于必定出现广告，也不声称设备当前远端间隔就是默认 60 秒。

## Hook 方案

安装前确认目标类、参数、返回类型及 static 属性，然后安装三个 Hook：

1. `SplashManager.canShowSplash(Activity)` 返回 false。冷启动与后台返回仍经过任务，
   使用原生无广告收尾，保留首页继续加载及开屏结束监听。
2. `SplashManager.shownSplashCoverIfNeed(Activity, String)` 不执行。
   封面设置早于异步资格判断，单独禁用它可避免无广告时的短暂遮挡。
3. `DetailSplashAdManager.canRequestSplashAd(Env)` 返回 false。
   外部进入详情页时走原生“不展示广告”分支，避免先置请求状态再等待广告。

应用方法的反优化交给框架处理，本功能不再手动反优化或依赖调用方的名称。
类和语义方法在该样本中未混淆，不扫描全进程 SDK，也不更改系统广告服务或其他应用。
安装失败会移除已安装部分；卸载时按相反顺序移除三个 Hook。

### 失败提醒

目标类/方法缺失、签名校验失败或安装失败，均记录 `MyInjector` 错误日志和堆栈。

失败后通过框架 `Instrumentation.callActivityOnResume` 等待首个商店页面进入前台，
显示包含失败功能名称及查看日志提示的 Toast，开屏、超级极简模式、升级页精简各自独立记录失败。
随后复用 `Application.ActivityLifecycleCallbacks`，每次从后台重新打开再提示一次，
同一次前台会话中的页面切换和旋转不重复提醒。后台初始化不直接弹 Toast。
通知入口不依赖商店内部类，Toast 的异常单独记录，避免影响宿主启动。
正常安装不注册提醒；卸载时移除等待 Hook、注销生命周期回调。

## 验证与依据

现有 JVM 测试与 release 构建通过。静态核对冷启动、后台返回、外部进入详情的入口，
并确认无广告分支会发出结束通知。尚需实机验证三条入口的实际效果，以及首页点击、
视频自动播放和下载/更新功能；不能将静态分析或构建通过视为真机验收。

原始字节码保存在 `research/xiaomi-splash-ads/`：

- `Market-tryAdSplash.smali`：封面与异步任务的先后顺序。
- `Market-canShowSplash.smali`：统一开屏资格判断。
- `Market-SplashTask-run.smali`：不满足资格时的原生结束分支及 SDK 请求入口。
- `Market-onSplashEnd.smali`、`Market-onSplashEnd-main.smali`：主线程清理与结束通知。
- `Market-detail-tryToDisplay.smali`：详情页关闭资格后的原生返回路径。
- `Market-cold-start.smali`、`Market-special-mode.smali`：冷启动资格和特殊模式。
- `Market-foreground-interval.smali`：后台停留时长比较及重置记录。
- `Market-detail-server-approval.smali`：详情广告由服务端字段决定。
- `Market-personalized-state.smali`、`Market-recommend-settings.xml`：个性化开关及上传路径。
