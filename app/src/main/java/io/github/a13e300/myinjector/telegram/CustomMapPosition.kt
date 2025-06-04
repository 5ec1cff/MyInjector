package io.github.a13e300.myinjector.telegram

import android.app.AlertDialog
import android.content.Context
import android.location.Location
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllCAfter

// 允许通过经纬度设置地图位置（长按定位按钮）
class CustomMapPosition : IHook() {
    override fun onHook(param: XC_LoadPackage.LoadPackageParam) {
        // 聊天发送
        val caall = findClass("org.telegram.ui.Components.ChatAttachAlertLocationLayout")
        caall.hookAllCAfter { param ->
            val self = param.thisObject
            param.thisObject.getObjAs<ImageView>("locationButton").run {
                setOnLongClickListener {
                    val ctx = it.context
                    val et = EditText(ctx)
                    AlertDialog.Builder(ctx)
                        .setTitle("latitude,longitude")
                        .setView(et)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val l = et.text.toString().split(",", limit = 2)
                            if (l.size == 2) {
                                val la = l[0].trim().toDoubleOrNull()
                                val lo = l[1].trim().toDoubleOrNull()
                                if (la != null && lo != null) {
                                    self.call("resetMapPosition", la, lo)
                                    return@setPositiveButton
                                }
                            }
                            Toast.makeText(
                                ctx,
                                "wrong position",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
            }
        }

        // 企业版个人资料设置位置
        val la = findClass("org.telegram.ui.LocationActivity")
        la.hookAfter("createView", Context::class.java) { param ->
            val self = param.thisObject
            param.thisObject.getObjAs<ImageView>("locationButton").run {
                setOnLongClickListener {
                    val ctx = it.context
                    val et = EditText(ctx)
                    AlertDialog.Builder(ctx)
                        .setTitle("latitude,longitude")
                        .setView(et)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val l = et.text.toString().split(",", limit = 2)
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
                                    return@setPositiveButton
                                }
                            }
                            Toast.makeText(
                                ctx,
                                "wrong position",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
            }
        }
    }
}
