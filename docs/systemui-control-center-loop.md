# 通知与控制中心循环滑动

展开后从顶部左右热区下拉切页的独立功能见 [顶部下拉切换](systemui-expanded-top-pull.md)。

## 结论与分析范围

可以实现两个页面双向循环切换：同一方向连续滑动时在通知与控制中心间往返，反方向同样可用。
当前版本的核心限制是固定端点的滑动进度、松手落点及页面平移计算，未发现需要修改系统服务的部分。
实现放在 SystemUI 作用域，复用原有页面、Folme 动画和切换完成流程，仅扩展反向越界手势。

模块内“系统组件 → 系统界面”新增“通知与控制中心循环滑动”，默认关闭，
通过已有 systemui 组 remote prefs 热更新。关闭不打断已经开始的切换，该次动画结束后恢复原生方向。
当前实现针对下述 HyperOS 样本结构；初始化时先确认字段、方法，结构不匹配不安装本功能的 Hook，
不影响其他 SystemUI 功能。核心方向计算和配置链路有单元测试，循环效果仍需实机验收。

## Hook 实现

`systemui/ShadeLoopHook.kt` 不替换 `onTouchEvent` 的结果或原有事件分发，
只在它执行期间记录线程上下文。原生通过可切换条件、子控件接管和横向阈值检查后，
会在设置 switching=true 之前调用 progressAnimator.setProgress(float) 初始化进度。
Hook 在这个首帧判断是否向原来的端点外拖动；正常方向完全沿用原生逻辑。
只有恰好一个页面展开、且与原生 fromCtrl 一致时才镜像；双面板同时展开或过渡异常状态保持原样。

反向手势一次性翻转 switchDistance 及两个页面包装对象的 triggerShifting，
从而同时归一化进度和镜像页面平移/触发入退场动画。p=0/1 的含义保持不变。
VelocityTracker.getXVelocity() 仅在该触摸调用内、且 tracker 是这个控制器私有实例时反号，
使原生快速松手的 RTL/LTR 判定与镜像距离一致；其他控件及调用不变。
触摸方法显式反优化，避免 ART 内联绕过 setProgress/getXVelocity Hook。

页面整体平移与子项的阶梯动画是两条路径。通知项的
MiuiPanelAnimValueObjectImpl.slideNotificationCenter(index, appeared, direction, animated, listener)
使用 `(index + 1) * 200 * direction` 计算横向偏移；插件 SpreadRowsAnimator.slideControlCenter
使用 `row * 200 * direction`（动画分支另乘 RTL 符号），其回调默认传入 direction=1。
只镜像页面距离不会改变这些偏移，因此循环时还需翻转两处 direction 参数。

实现用 setTriggerAppeared 的嵌套线程上下文限定影响范围，捕获当前控制器是否处于镜像会话，
仅在该次同步通知回调中翻转通知项与控制中心按钮的方向，不改行序、缓动、完成监听或真实 RTL。
控制中心动画类来自插件独立 ClassLoader；在通知原生监听器前扫描当前实际实例并安装 Hook，
按 Class 对象去重，同时处理首开、模块热重载及插件重建。通知和插件的动画调用方显式反优化。

方向状态保持到原生 setSwitching(false)，因此动画中重新按下仍使用当前方向及剩余进度。
连续快速滑动有一条额外路径：在原生 onInterceptTouchEvent 已允许接管新横向手势后，
如果上一段尚在切换、并且新手势朝当前 toCtrl 目标页的外侧继续滑动，先完成上一段到其目标端点，
恢复正常几何参数，再让原生 onTouchEvent 按新的起点初始化下一段。
此处按当前有效距离（包含上一段的镜像）判断，而非上一段 fromCtrl，保证普通/镜像切换连续交替。
向回拖动中断动画、原有拖动尚未结束、纵向手势或位移未达阈值时，仍使用原生的中断续拖。
动画完成后恢复距离和触发偏移。资源重算、异常与模块卸载时取消进度动画、收束到最近端点，
通过原生页面收尾后恢复几何参数。卸载清理在 SystemUI 主线程执行；恢复未完成时不允许卸载。

## 样本

- 系统：`OS4.0.4.0.XPBCNXM`，Android 17 / SDK 37。
- SystemUI：`com.android.systemui`，`17.03.260226.r` / `202602260`。
  设备路径：`/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk`。
- 插件：`miui.systemui.plugin`，`18.3.2.57.0` / `183025700`。
  设备路径：`/product/app/MIUISystemUIPlugin/MIUISystemUIPlugin.apk`。
- SHA-256 和提取信息见 `research/systemui-control-center-loop/extraction.json`。
  APK 及完整反编译保留在原工作目录 `analysis/systemui-control-center-loop`，不纳入模块仓库。

`research/systemui-control-center-loop/runtime-shade-switch-dump.txt` 是定向查询控制器的结果，
确认该控制器已加载；采样时两个页面均未展开。没有通过真实滑动追踪验证 Hook 效果。

## 当前实现

### SystemUI 主包负责两页切换

主要类为 `com.miui.systemui.shade.ShadeSwitchControllerImpl`，保存：

- `fromCtrl` / `toCtrl`：切换起点与终点页面。
- `dragging` / `switching` / `progressAnimating`：拖动和动画状态。
- `initX` / `initY` / `startX` / `remainProgress`：手势起点及中断后的剩余进度。
- `switchDistance`：有符号的切换距离，资源更新时根据布局及 RTL 计算。
- `notification` / `controlCenter`：两个现有页面包装对象。

进度定义是固定的：`p = 0` 对应控制中心，`p = 1` 对应通知。
`getHandlingEventTarget()`、`setSwitching()`、`tryFinishSwitching()` 负责目标页面、显示隐藏和收尾。
`performProgressChanged()` 向页面、页头及插件监听器传播进度和动画状态。

本版本没有可单独 Hook 的 `ShadeSwitchControllerImpl.onSwitchTouch()` / `onSwitchIntercept()`：
这些日志标签对应的手势实现已内联进
`com.android.systemui.shade.NotificationShadeWindowView.onTouchEvent()` / `onInterceptTouchEvent()`。
应 Hook 实际存在的方法，不能按日志标签推断方法名。

### 为什么向另一侧滑动会回弹

已用 `NotificationShadeWindowView-onTouchEvent.smali` 核对以下逻辑；Java 反编译存在重复分支，
不能直接照抄其控制流。整理后的计算为：

```text
开始拖动：控制中心的 remainProgress = 0，通知的 remainProgress = 1
pRaw = remainProgress + (rawX - startX) / switchDistance

pRaw < 0 或 pRaw > 1：进入 Folme.afterFrictionValue 的越界阻尼
慢速松手：按 pRaw 是否达到 0.5 决定终点
快速松手：abs(xVelocity) > 1000 px/s 时按速度符号和 RTL 决定终点
最终 toCtrl = true -> p = 0；否则 p = 1
```

因此，两页被当成一条只有两个端点的线段，而不是一个环。
从端点向外继续拖动不会自然出现另一页，松手选择也依赖原有方向。

页面包装类 `ShadeSwitchControllerImpl$notification$1` 被 R8 合并用于两个页面，
`$r8$classId` 区分通知和控制中心。`updateTransX()` 的主要平移是：

```text
通知：-(1 - p) * switchDistance
控制中心：p * switchDistance
另加 triggerShifting * triggerProgress 的入场/退场偏移
```

这也固定了另一个页面出现的侧边。只修改落点而不修改动画方向，不能得到跟随手指的循环效果。
对应字节码已保存为 `SwitchAnimator-updateTransX.smali`。

### 插件及子控件的手势交接

控制中心插件的 `MainPanelTouchController.handleMotionEvent(MotionEvent, boolean, boolean)`
在相关分支用 `abs(dx) > abs(dy)` 判断横向手势，不区分 dx 的正负。
满足子控件未强制占用等条件后，调用
`ControlCenterWindowViewImpl.setAllowInterceptSwitchEvent(true)`，交给父级处理。

主包的 `ControlCenterImpl.getAllowParentInterceptSwitchEvent()` 继续检查：
不在展开手势处理中、面板已经展开、插件允许父级接管。
通知侧也有独立的 `allowParentInterceptSwitchEvent`，用于与通知卡片等子控件协调。
这些机制需要保留；不应为了循环滑动而无条件返回 true。

## 设计依据

1. 在原有手势允许切换、横向位移超过阈值、且普通页面可切换时识别手势方向。
   原有正常方向继续走系统逻辑；只对原本越界回弹的方向补充处理。
2. 为本次手势记录方向和页面起点，把拖动映射到现有的规范进度 0～1。
   保持 p=0/1 的页面含义及现有监听器协议，避免直接对全局进度取模。
3. 对反向切换同步调整两个包装对象的实际平移方向，包含触发入场偏移；
   不能只翻转 `switchDistance`，否则松手速度判定及入场/退场偏移仍可能使用原方向。
4. 松手时用该手势方向归一化速度，保留原有速度阈值和慢速落点规则。
   再复用 `progressAnimator`、`setTriggerAppeared()` 和原有切换完成流程。
5. 动画结束、取消、收起、资源更新及 Hook 卸载时清理本次方向状态。
   拖动动画中重新按下时应从当前进度继续，避免方向状态丢失、跳页或残留平移。

主体改动可集中在主包的触摸入口和页面包装对象，不需要重写页面或新增重复页，
也不需要 system_server bridge 或新的 native API。插件现有的手势交接可继续复用；
是否需要修正个别页头/卡片动画，应在实机验证后决定。

`reversePanel()` / `switchToNotification()` / `switchToControlCenter()` 已经存在，
可以用于松手后触发切换的简化实现，但它们本身不提供根据手势方向变化的跟手动画，
不能单独满足完整循环滚动的要求。

## 已保留的边界与实机验收项

- `isSwitchable()`：原本的锁屏限制及两个页面各自的可切换条件。
- 插件：主页面、路由空闲，排除编辑模式及等待进入编辑状态。
- 通知：排除锁屏、认证界面、折叠通知、悬浮通知展开和通知详情等既有限制。
- 通知卡片横划、亮度/音量滑块、媒体卡片等子控件对触摸的占用。
- 双向连续多次切换、慢拖和快速甩动、半途反向、取消及动画打断。
- 页头/状态栏图标、背景模糊、触摸目标和可交互页面须始终与最终页面一致。
- 横屏、RTL、折叠/分屏及配置变化时的距离和方向重算。

## 证据文件

以下文件均位于 `docs/research/systemui-control-center-loop/`：

- `NotificationShadeWindowView-onTouchEvent.smali`：完整触摸方法，作为控制流核对依据。
- `SwitchAnimator-updateTransX.smali`：固定页面位置的字节码依据。
- `ShadeSwitchController-isSwitchable.smali`、`Plugin-getSwitchable.smali`：既有切换限制。
- `ControlCenter-getAllowParentInterceptSwitchEvent.smali`：主包向插件查询手势交接条件。
- `Notification-slideNotificationCenter.smali`：通知项阶梯偏移及 direction 参数。
- `ControlCenter-slideControlCenter.smali`、`ControlCenter-onControlCenterAppearChanged.smali`：插件阶梯偏移及固定方向入口。

`ShadeLoopGeometryTest` 覆盖 LTR/RTL、两个起点的正常/越界方向、速度反号、完整平移及触发偏移镜像、
恢复、纵向/模糊手势阈值和非法数值，并覆盖动画未结束时连续同向切换，以及反向拖回时保留原生处理。
阶梯偏移测试覆盖通知项行距、按钮行距、RTL、正常方向不变及零偏移行保持静止。
配置测试核对默认关闭、远端解析及 UI/同步范围一致。
