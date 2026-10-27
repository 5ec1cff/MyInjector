# 小米 WLAN 详情页补充信息

在 LSPosed 中启用 `com.android.settings` 作用域，安装新模块后重启设置进程，
进入「设置 → WLAN → 当前连接网络的详情」。无需新增开关。

## APK 分析

使用 dexsql MCP 分析 `~/Settings.apk`，Manifest 中包名为 `com.android.settings`，
版本为 `17`（versionCode 37，SDK 37）。以下实现针对这份 APK 的小米页面，
不是 AOSP 的 `WifiNetworkDetailsFragment`。

- `com.android.settings.wifi.MiuiWifiDetailFragment.initUI()` 创建详情数据，
  放入 `mWifiDetailGridMap`，交给 `WifiDetailLinearLayout.initWifiDetailGrid(HashMap)`
  渲染，随后清空 Map。
- `formatIpAddresses(LinkProperties, StaticIpConfiguration)` 已经读取 IPv4 和 IPv6，
  但把 IPv6 排在前面、IPv4 排在末尾，同时计算 `mPrefixLength` 和 `mSubnet`。
- `WifiDetailLinearLayout.getBaseView(int)` 使用 `android.R.id.text1/text2` 绑定标题和内容。
  它投递的 `WifiDetailLinearLayout$2.run()` 在内容超过两行时调用 `setSingleLine()`
  并启用跑马灯。这是 IPv4 容易被藏在末尾的显示原因，不是系统不能同时提供两种地址。
- `mWifiEntry.getWifiInfo()` 提供该详情页所对应连接的 `WifiInfo`，可读取 `getFrequency()`。
  `mWifiEntry.mNetwork` 对应本连接，`ConnectivityManager.getLinkProperties(Network)`
  可取得它的地址。
- `onUpdated()` 原先只检查连接状态、部分网络能力、Wi-Fi 代际及特定情况下的链路速度，
  不比较 IP 和频率，因此同一网络漫游换频或地址变化时可能不刷新。

## Hook 实现

入口为 `SettingsHandler` 中的 `SettingsWifiDetails` 子 hook。

1. 在原 `formatIpAddresses()` 执行后，把 IPv4 调到首行，保留所有 IPv6。
   原方法照常运行，子网掩码等计算不受影响。
2. 拦截地址卡片和新增频段卡片的单行转换，允许换行且不启用省略或横向滚动，
   避免 IPv6 较长时再次隐藏 IPv4。
3. 在 `initUI()` 执行前加入一条原生 `WifiDetailInfoBean`，复用设置内的
   `wifi_setting_frequency_band_title` 标题和 `ic_wifi_generation_icon` 图标。
   示例为两行：`5 GHz` 和 `5180 MHz`。支持 2.4、5、6、60 GHz，
   其他正数频率只显示 MHz，避免错误分类。
4. 记录已显示的连接状态、频率及地址，在 `onUpdated()` 时比较最新值，
   有变化便设置原字段 `mRefrshPage`，由原页面更新网络信息并重新渲染。
   地址比较使用当前 `WifiEntry` 的 Network，避免取到 VPN、蜂窝网络或另一条 WLAN。
5. 对相关调用者撤销优化，避免已编译的内联代码绕过 hook。

## 信息边界

- 显示的是系统分配给此 WLAN 接口的地址，不是互联网出口的公网 IPv4。
  若连接只有 IPv6、没有分配 IPv4，模块不会虚构 IPv4 或通过外网服务探测。
- 频率未知、非正数或连接已断开时不显示频段卡片；未连接的已保存网络不展示扫描频率。
- [Android 的 WifiInfo 文档](https://developer.android.com/reference/android/net/wifi/WifiInfo#getFrequency())
  规定，Wi-Fi 7 MLO 下 `getFrequency()` 返回 RSSI 最高的关联链路频率。
  本功能按这一语义显示单个频段，不代表全部 MLO 链路。
- 主 WLAN 与辅助 WLAN 均从详情页自己的 `WifiEntry` 取值；系统是否给辅助连接返回完整信息，
  仍需实际设备验证。
- 类名、内部字段及 Runnable 名称属于厂商实现，其他系统版本可能需要重新适配。
  hook 安装和回调失败会写入 MyInjector 日志。

## 验证

构建命令：`./gradlew :app:assembleDebug`。

本次 Debug APK 构建成功；对实际编译后的 Kotlin 格式化函数执行了 17 项检查，
覆盖双栈排序、多 IPv4、IPv6 单栈、空地址、2.4/5/6/60 GHz、5935 MHz 特殊信道、
未知正数频率及 0/-1 无效频率，全部通过。

设备检查建议：

- IPv4 单栈、IPv6 单栈、IPv4/IPv6 双栈，以及多个较长 IPv6 地址。
- 2.4/5/6 GHz 连接；同 SSID 漫游换频、DHCP 地址改变、断开与重新连接。
- VPN 开启、移动网络为默认网络、双 WLAN、未连接的已保存网络。
- 大字体及深色模式，确认地址可完整换行、频段卡片不会重复。

当前环境未连接 ADB 设备，尚未验证实际页面布局和设备上的 hook 命中情况。
