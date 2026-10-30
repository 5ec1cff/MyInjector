# 展开面板后从顶部下拉切换通知与控制中心

## 使用与范围

模块内“系统组件 → 系统界面”新增“顶部下拉切换通知与控制中心”，默认关闭，
通过 systemui 组 remote prefs 热更新，与“通知与控制中心循环滑动”独立。

通知或控制中心展开后，仍可从屏幕顶部状态栏区域下拉，进入起点所在热区对应的页面。
例如 LTR 手机在通知页从右上角下拉进入控制中心，在控制中心从左上角下拉进入通知。
热区沿用系统规则，RTL、平板与折叠屏展开状态的分区由原生方法决定。
从当前页面对应的区域下拉继续走原生流程。

## 实现

样本与 APK 提取信息沿用 [循环滑动分析](systemui-control-center-loop.md)。
`NotificationShadeWindowView.dispatchTouchEvent` 的原生状态栏转发主要在面板收起时处理，
已展开时的触摸会交给当前页面；`ShadeHeaderTouchHandler` 也只将触摸发往当前页面。
因此仅扩展横向切页的方向不能提供顶部下拉切页。

新增 `systemui/ShadeTopPullHook.kt`，在窗口分发入口记录顶部单指 DOWN，
使用 `ControlCenterEventHandlerImpl.calculateCtrlHotZone(left, width, rtl)` 取得控制中心热区。
该方法内部调用 `getCtrlHotZoneShare()`，手机通常为一半，平板及展开折叠屏为四分之一；
折叠小屏刘海边距与原生 `handleExpandEvent` 的入参处理一致。
状态栏高度取 SystemUI/框架资源与窗口 statusBars inset 的较大值。

只有原生允许切页、恰好一个页面展开、且当前没有拖动或切页动画时才记录候选。
锁屏、认证、息屏、启动动画、遮挡过渡、控制中心关闭及原生编辑/详情等切页限制继续生效。
达到系统 touch slop 后，只有向下位移大于横向位移才接管；
横划或上划一旦被确认，本次手势不再转为下拉切页。模糊手势使用系统 slop 倍率。
多指、窗口大小变化、配置关闭及起始页面变化时放弃尚未接管的候选。
快速下拉仅有最后一个 UP 超过阈值时，也可识别。

接管前，原事件正常分发，保留点击和原生子控件处理。
接管时通过同一个窗口分发一份独立的 ACTION_CANCEL，完整结束原页面的触摸、
状态栏转发及 falsing 状态；原 MotionEvent 不被修改。
随后调用原生 `switchToControlCenter()` / `switchToNotification()`，
由系统负责页面显示隐藏、阶梯动画、进度和完成回调。
剩余事件消费到 UP/CANCEL，避免把缺少 DOWN 的 MOVE/UP 发往新页面。
关闭配置或卸载时，已经接管的尾流继续消费，之后恢复原路径。
Hook 卸载不接管新手势，尾流结束后移除分发 Hook。

本功能不改横向循环的几何参数或阶梯动画方向，不需要插件新接口、系统服务或 native API。

## 验证与证据

`TopPullGestureTest` 覆盖两个起点、同侧保持原生、状态栏范围、RTL、四分之一热区、
横划/上划后不再接管、模糊手势阈值、快速下拉与非法输入。
配置测试覆盖默认值、远端解析与 UI/同步键一致。

原生入口的完整 Smali 保存在 `research/systemui-control-center-loop/`：

- `ControlCenter-calculateCtrlHotZone.smali`：原生 RTL 热区计算入口。
- `ShadeSwitchController-switchToControlCenter.smali`：切换到控制中心及原生动画流程。
- `ShadeSwitchController-switchToNotification.smali`：切换到通知及原生动画流程。

尚需实机视觉验收：两侧顶部下拉、同侧下拉与点击、通知列表滚动、横向循环并用、
锁屏及编辑/详情界面限制、手势取消与多指、手势中关闭配置及模块热重载。
