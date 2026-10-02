package io.github.a13e300.myinjector.telegram

import android.location.Location
import android.widget.ImageView
import android.widget.Toast
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.ui.showModernInjectedTextInputDialog
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

// 允许通过经纬度设置地图位置（长按定位按钮）
class CustomMapPosition : MyDynHook("customMapPosition") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.customMapPosition

    override fun onHook() {
        val creator = TelegramHandler.creator
        val found by lazy {
            val bridge = creator.bridge
            val layoutInit = bridge.findMethod { matcher {
                name("<init>")
                addUsingField { declaredClass("org.telegram.messenger.R\$drawable"); name("msg_current_location") }
            } }.single()
            val createView = bridge.findMethod { matcher {
                paramTypes("android.content.Context")
                returnType("android.view.View")
                addUsingField { declaredClass("org.telegram.messenger.R\$drawable"); name("msg_current_location") }
            } }.single()
            val resetMap = bridge.findMethod { matcher {
                declaredClass(layoutInit.className)
                paramTypes("double", "double")
                returnType("void")
                addInvoke { descriptor("Landroid/location/Location;->setLatitude(D)V") }
                addInvoke { descriptor("Landroid/location/Location;->setLongitude(D)V") }
            } }.single()
            val position = bridge.findMethod { matcher {
                declaredClass(createView.className)
                paramTypes("android.location.Location")
                returnType("void")
                addInvoke { descriptor("Lorg/telegram/messenger/LocationController;->getSharingLocationInfo(J)Lorg/telegram/messenger/LocationController\$SharingLocationInfo;") }
            } }.single()
            mapOf(
                "layout" to ObfsInfo(layoutInit.className, ""),
                "layoutButton" to viewFieldWithResource(bridge, layoutInit, "msg_current_location").toObfsInfo(),
                "reset" to resetMap.toObfsInfo(),
                "createView" to createView.toObfsInfo(),
                "activityButton" to viewFieldWithResource(bridge, createView, "msg_current_location").toObfsInfo(),
                "position" to position.toObfsInfo()
            )
        }
        val members = listOf("layout", "layoutButton", "reset", "createView", "activityButton", "position")
            .associateWith { key -> creator.create("CustomMapPosition.$key") { found.getValue(key) } }
        fun field(key: String) = DexField(members.getValue(key).descriptor)
            .getFieldInstance(classLoader).apply { isAccessible = true }
        fun method(key: String) = DexMethod(members.getValue(key).descriptor)
            .getMethodInstance(classLoader).apply { isAccessible = true }
        val layoutButton = field("layoutButton")
        val activityButton = field("activityButton")
        val resetMap = method("reset")
        val positionMarker = method("position")
        // 聊天发送
        val caall = findClass(members.getValue("layout").className)
        caall.hookAllCAfter(cond = ::isEnabled) { param ->
            val self = param.thisObject
            (layoutButton.get(self) as ImageView).run {
                setOnLongClickListener {
                    val ctx = it.context
                    showModernInjectedTextInputDialog(ctx, "latitude,longitude") { text ->
                        val l = text.split(",", limit = 2)
                        if (l.size == 2) {
                            val la = l[0].trim().toDoubleOrNull()
                            val lo = l[1].trim().toDoubleOrNull()
                            if (la != null && lo != null) {
                                resetMap.invoke(self, la, lo)
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
        method("createView").hookAfter(cond = ::isEnabled) { param ->
            val self = param.thisObject
            (activityButton.get(self) as ImageView).run {
                setOnLongClickListener {
                    val ctx = it.context
                    showModernInjectedTextInputDialog(ctx, "latitude,longitude") { text ->
                        val l = text.split(",", limit = 2)
                        if (l.size == 2) {
                            val la = l[0].trim().toDoubleOrNull()
                            val lo = l[1].trim().toDoubleOrNull()
                            if (la != null && lo != null) {
                                positionMarker.invoke(
                                    self,
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
