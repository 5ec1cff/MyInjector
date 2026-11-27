package io.github.a13e300.myinjector.xhs

import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookConstantIf
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexMethod

internal fun locateXhsDoubleTapLike(bridge: DexKitBridge): Map<String, MethodData> {
    val imageClickType = bridge.findMethod {
        matcher {
            name = "<clinit>"
            usingEqStrings("SINGLE_CLICK", "DOUBLE_CLICK", "MULTI_CLICK", "LONG_PRESS")
            declaredClass {
                fields { add { name = "isClickImageArea" } }
            }
        }
    }.single()
    val videoClickType = bridge.findMethod {
        matcher {
            name = "<clinit>"
            usingEqStrings("SINGLE_CLICK", "DOUBLE_CLICK", "MULTI_CLICK", "LONG_PRESS", "ZOOM_IN", "ZOOM_OUT")
        }
    }.single()

    // 从图文手势回调追踪事件出口，不固定 RxJava 的混淆类名
    val imageDispatch = bridge.findMethod {
        matcher {
            paramTypes("android.view.MotionEvent")
            usingFields {
                add {
                    declaredClass = imageClickType.className
                    name = "MULTI_CLICK"
                }
            }
        }
    }.flatMap { it.invokes }
        .filter { it.methodName == "onNext" && it.className != "io.reactivex.Observer" }
        .distinctBy { it.descriptor }.single()

    val followDoubleTaps = bridge.findMethod {
        matcher {
            name = "onDoubleTap"
            paramTypes("android.view.MotionEvent")
            returnType = "boolean"
            usingEqStrings("anim/doubleclick/double_click_like_v2.json")
        }
    }.sortedBy { it.descriptor }
    check(followDoubleTaps.size == 2) { "Expected photo and live photo double tap handlers" }

    // 全屏看图有独立回调，其中部分会在分发事件前播放爱心动画
    val browserDoubleTaps = bridge.findMethod {
        matcher {
            name = "onDoubleTap"
            paramTypes("android.view.MotionEvent")
            returnType = "boolean"
            usingEqStrings("larger")
            usingFields {
                add {
                    declaredClass = imageClickType.className
                    name = "MULTI_CLICK"
                }
            }
        }
    }.sortedBy { it.descriptor }
    check(browserDoubleTaps.size == 4) { "Expected four image browser double tap handlers" }

    return mapOf(
        "imageClickType" to imageClickType,
        "videoClickType" to videoClickType,
        "imageDispatch" to imageDispatch,
        "followPhoto0" to followDoubleTaps[0],
        "followPhoto1" to followDoubleTaps[1],
        "imageBrowser0" to browserDoubleTaps[0],
        "imageBrowser1" to browserDoubleTaps[1],
        "imageBrowser2" to browserDoubleTaps[2],
        "imageBrowser3" to browserDoubleTaps[3],
        "element" to bridge.findMethod {
            matcher {
                name = "invokeSuspend"
                usingEqStrings("DoubleTapLikeElement", "DoubleTapLikeVM")
            }
        }.single(),
        "podcast" to bridge.findMethod {
            matcher {
                name = "invokeSuspend"
                usingEqStrings("PodcastDoubleTapLikeAnimPlayer", "PodcastItemPage")
            }
        }.single(),
    )
}

internal fun locateXhsVideoDoubleTapSources(bridge: DexKitBridge, clickType: String): List<MethodData> =
    listOf("DOUBLE_CLICK", "MULTI_CLICK").flatMap { click ->
        bridge.findMethod {
            matcher {
                paramTypes("android.view.MotionEvent")
                returnType = "void"
                usingFields {
                    add {
                        declaredClass = clickType
                        name = click
                    }
                }
            }
        }
    }.distinctBy { it.descriptor }.also { check(it.isNotEmpty()) { "No video double tap sources" } }

internal fun isXhsDoubleTapLike(event: Any?, clickTypes: Set<Class<*>>): Boolean =
    event is Enum<*> && event.declaringJavaClass in clickTypes &&
        (event.name == "DOUBLE_CLICK" || event.name == "MULTI_CLICK")

class DisableDoubleTapLike : MyDynHook("disableDoubleTapLike") {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.disableDoubleTapLike

    override fun onHook() {
        val creator = XhsHandler.creator
        val targets by lazy { locateXhsDoubleTapLike(creator.bridge) }
        fun info(key: String) = creator.create("disableDoubleTapLike.$key") {
            targets.getValue(key).toObfsInfo()
        }
        fun method(key: String) = DexMethod(info(key).descriptor).getMethodInstance(classLoader)

        val clickTypes = setOf("imageClickType", "videoClickType")
            .mapTo(mutableSetOf()) { findClass(info(it).className) }
        // 视频回调先缓存事件再分发，必须在写入缓存前拦截。回调数量随版本变化，每次按事件类型查找
        val videoSources = locateXhsVideoDoubleTapSources(creator.bridge, info("videoClickType").className)
        method("imageDispatch").hookBefore(cond = ::isEnabled) { param ->
            // 只拦截笔记双击事件，保留单击、长按、缩放及手动点赞
            if (isXhsDoubleTapLike(param.args[0], clickTypes)) param.result = null
        }

        for (source in videoSources) {
            val target = creator.create("disableDoubleTapLike.videoSource.${source.descriptor}") {
                source.toObfsInfo()
            }
            DexMethod(target.descriptor).getMethodInstance(classLoader).hookConstantIf(null, ::isEnabled)
        }
        for (key in listOf("followPhoto0", "followPhoto1", "imageBrowser0", "imageBrowser1",
            "imageBrowser2", "imageBrowser3")) {
            method(key).hookConstantIf(false, ::isEnabled)
        }

        // 新版笔记用协程处理双击，正常结束回调，不进入动画和点赞逻辑
        val unit = findClass("kotlin.Unit").getObjS("INSTANCE")
        for (key in listOf("element", "podcast")) {
            method(key).hookConstantIf(unit, ::isEnabled)
        }
    }
}
