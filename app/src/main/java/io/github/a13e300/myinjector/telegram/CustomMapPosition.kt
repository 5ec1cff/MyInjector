package io.github.a13e300.myinjector.telegram

import android.content.Context
import android.location.Location
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.ui.showModernInjectedTextInputDialog

// 允许通过经纬度设置地图位置（长按定位按钮）
class CustomMapPosition : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.customMapPosition

    override fun onHook() {
        // 聊天发送
        val caall = findClass("org.telegram.ui.Components.ChatAttachAlertLocationLayout")
        caall.hookAllCAfter(cond = ::isEnabled) { param ->
            val self = param.thisObject
            param.thisObject.getObjAs<ImageView>("locationButton").run {
                setOnLongClickListener {
                    val ctx = it.context
                    val et = EditText(ctx)
                    showModernInjectedTextInputDialog(ctx, "latitude,longitude") { text ->
                        val l = text.split(",", limit = 2)
                        if (l.size == 2) {
                            val la = l[0].trim().toDoubleOrNull()
                            val lo = l[1].trim().toDoubleOrNull()
                            if (la != null && lo != null) {
                                self.call("resetMapPosition", la, lo)
                                return@showModernInjectedTextInputDialog
                            }
                        }
                        Toast.makeText(
                            ctx,
                            "wrong position",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@setOnLongClickListener true
                }
            }
        }

        // 企业版个人资料设置位置
        val la = findClass("org.telegram.ui.LocationActivity")
        la.hookAfter("createView", Context::class.java, cond = ::isEnabled) { param ->
            val self = param.thisObject
            param.thisObject.getObjAs<ImageView>("locationButton").run {
                setOnLongClickListener {
                    val ctx = it.context
                    val et = EditText(ctx)
                    showModernInjectedTextInputDialog(ctx, "latitude,longitude") { text ->
                        val l = text.split(",", limit = 2)
                        if (l.size == 2) {
                            val la = l[0].trim().toDoubleOrNull()
                            val lo = l[1].trim().toDoubleOrNull()
                            if (la != null && lo != null) {
                                self.call(
                                    "positionMarker",
                                    Location(null).apply {
                                        latitude = la
                                        longitude = lo
                                    })
                                return@showModernInjectedTextInputDialog
                            }
                        }
                        Toast.makeText(
                            ctx,
                            "wrong position",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@setOnLongClickListener true
                }
            }
        }
    }
}
