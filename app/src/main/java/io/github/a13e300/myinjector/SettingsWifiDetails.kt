package io.github.a13e300.myinjector

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.StaticIpConfiguration
import android.net.wifi.WifiInfo
import android.view.View
import android.widget.TextView
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.deoptimize
import io.github.a13e300.myinjector.arch.extraField
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.setObj

/** 适配小米 WLAN 详情页；所有数据均取自该页对应的连接。 */
class SettingsWifiDetails : IHook() {
    private data class ConnectionState(
        val connected: Boolean,
        val frequency: Int = 0,
        val addresses: List<String>? = null,
    )

    override fun onHook() {
        val fragmentClass = findClassOrNull("com.android.settings.wifi.MiuiWifiDetailFragment")
            ?: return
        runCatching { hookIpAddresses(fragmentClass) }
            .onFailure { logE("hook Wi-Fi IP addresses failed", it) }
        runCatching { hookMultilineSummaries() }
            .onFailure { logE("hook Wi-Fi multiline summaries failed", it) }
        runCatching { hookFrequency(fragmentClass) }
            .onFailure { logE("hook Wi-Fi frequency failed", it) }
    }

    private fun hookIpAddresses(fragmentClass: Class<*>) {
        fragmentClass.getDeclaredMethod(
            "formatIpAddresses", LinkProperties::class.java, StaticIpConfiguration::class.java,
        ).hookAfter { param ->
            val addresses = param.result as? String ?: return@hookAfter
            // 保留原方法计算前缀长度、子网掩码等副作用，只调整最终显示顺序。
            param.result = ipv4First(addresses)
        }
        fragmentClass.deoptimize("initUI")
    }

    private fun hookMultilineSummaries() {
        val strings = findClass("com.android.settings.R\$string")
        val ipTitle = strings.getObjSAs<Int>("wifi_ip_address")
        val bandTitle = strings.getObjSAs<Int>("wifi_setting_frequency_band_title")
        findClass("com.android.settings.wifi.WifiDetailLinearLayout\$2")
            .getDeclaredMethod("run").hookBefore { param ->
                runCatching {
                    val summary = param.thisObject.getObjAs<TextView>("val\$summaryTv")
                    val title = (summary.parent as? View)
                        ?.findViewById<TextView>(android.R.id.text1)?.text?.toString()
                    val resources = summary.resources
                    if (title == resources.getString(ipTitle) ||
                        title == resources.getString(bandTitle)
                    ) {
                        // 原 Runnable 在超过两行时启用单行跑马灯，会把末尾的 IPv4 隐藏。
                        summary.setSingleLine(false)
                        summary.maxLines = Int.MAX_VALUE
                        summary.ellipsize = null
                        summary.setHorizontallyScrolling(false)
                        param.result = null
                    }
                }.onFailure { logE("update Wi-Fi multiline summary failed", it) }
            }
    }

    private fun hookFrequency(fragmentClass: Class<*>) {
        val beanClass = findClass("com.android.settings.wifi.WifiDetailInfoBean")
        val bandTitle = findClass("com.android.settings.R\$string")
            .getObjSAs<Int>("wifi_setting_frequency_band_title")
        val icon = findClass("com.android.settings.R\$drawable")
            .getObjSAs<Int>("ic_wifi_generation_icon")

        fragmentClass.getDeclaredMethod("initUI").hookBefore { param ->
            runCatching {
                val fragment = param.thisObject
                val state = readConnectionState(fragment)
                var displayedState by extraField<ConnectionState?>(fragment, STATE_KEY, null)
                val grid = fragment.getObjAs<MutableMap<String, Any>>("mWifiDetailGridMap")
                grid.remove(BAND_KEY)
                if (state.connected) {
                    formatFrequency(state.frequency)?.let { summary ->
                        grid[BAND_KEY] = beanClass.newInst(icon, bandTitle, summary)
                    }
                }
                displayedState = state
                // initUI 随后把该 Map 交给原生网格渲染，并在完成后清空。
            }.onFailure { logE("update Wi-Fi frequency failed", it) }
        }

        fragmentClass.getDeclaredMethod("onUpdated").hookBefore { param ->
            runCatching {
                val fragment = param.thisObject
                val displayedState by extraField<ConnectionState?>(fragment, STATE_KEY, null)
                // 原页面未比较频率和 IP；同 SSID 漫游或 DHCP 变化时也要触发刷新。
                if (displayedState != null && displayedState != readConnectionState(fragment)) {
                    fragment.setObj("mRefrshPage", true)
                }
            }.onFailure { logE("refresh Wi-Fi connection details failed", it) }
        }
        fragmentClass.deoptimize("onActivityCreated")
        fragmentClass.deoptimize("refreshPage")
    }

    private fun readConnectionState(fragment: Any): ConnectionState {
        val entry = fragment.getObjAsN<Any>("mWifiEntry")
            ?: return ConnectionState(false)
        if (entry.call("getConnectedState") != 2) return ConnectionState(false)
        val wifiInfo = entry.call("getWifiInfo") as? WifiInfo
        // 使用该 WifiEntry 的 Network，避免 VPN、移动网络或双 WLAN 时取错地址。
        val network = entry.getObjAsN<Network>("mNetwork")
        val connectivity = fragment.getObjAsN<ConnectivityManager>("mCm")
        val properties = if (network != null && connectivity != null) {
            connectivity.getLinkProperties(network)
        } else {
            fragment.getObjAsN<LinkProperties>("mLinkProperties")
        }
        return ConnectionState(
            connected = true,
            frequency = wifiInfo?.frequency ?: 0,
            addresses = properties?.linkAddresses?.map { it.toString() }?.sorted(),
        )
    }

    companion object {
        private const val BAND_KEY = "myinjector_wifi_frequency_band"
        private const val STATE_KEY = "myinjector_wifi_connection_state"

        internal fun ipv4First(addresses: String): String {
            val lines = addresses.lineSequence().filter { it.isNotBlank() }.toList()
            return (lines.filterNot { ':' in it } + lines.filter { ':' in it })
                .joinToString("\n")
        }

        internal fun formatFrequency(frequency: Int): String? {
            if (frequency <= 0) return null
            // 与 Android ScanResult 的频段边界一致，包括 6 GHz 的特殊信道 2。
            val band = when (frequency) {
                in 2412..2484 -> "2.4 GHz"
                in 5160..5885 -> "5 GHz"
                5935, in 5955..7115 -> "6 GHz"
                in 58320..70200 -> "60 GHz"
                else -> return "$frequency MHz"
            }
            return "$band\n$frequency MHz"
        }
    }
}
