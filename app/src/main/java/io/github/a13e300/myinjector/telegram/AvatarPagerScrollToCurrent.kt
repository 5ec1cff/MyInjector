package io.github.a13e300.myinjector.telegram

import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookAllBefore

// 个人资料头像如果存在多个且主头像非第一个时，下拉展示完整头像列表时自动切到当前头像（原行为是总是切到第一个）
class AvatarPagerScrollToCurrent : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.avatarPageScrollToCurrent

    override fun onHook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        val pgvClass = findClass("org.telegram.ui.Components.ProfileGalleryView")
        pgvClass.hookAllBefore("resetCurrentItem", cond = ::isEnabled) { param ->
            if (!param.thisObject.javaClass.name.startsWith("org.telegram.ui.ProfileActivity")) return@hookAllBefore
            val pa = param.thisObject.getObj("this$0")
            val currentPhoto = pa.getObj("userInfo")?.getObj("profile_photo")
                ?: pa.getObj("chatInfo")?.getObj("chat_photo") ?: return@hookAllBefore
            val id = currentPhoto.getObjAs<Long>("id")
            val photos = param.thisObject.getObjAs<List<*>>("photos")
            val idx = photos.indexOfFirst {
                it?.getObjAs<Long>("id") == id
            }
            if (idx == -1) return@hookAllBefore
            var exactIdx = 0
            // I know it's dumb, but ...
            // https://github.com/DrKLO/Telegram/blob/289c4625035feafbfac355eb01591b726894a623/TMessagesProj/src/main/java/org/telegram/ui/Components/ProfileGalleryView.java#L1502
            while (param.thisObject.call("getRealPosition", exactIdx) != idx) {
                exactIdx++
                // I believe no one can set over 300 photos
                if (exactIdx > 300) return@hookAllBefore
            }
            param.thisObject.call("setCurrentItem", exactIdx, false)
            param.result = null
        }
        // fix wrong transition image
        val inSetFgImg = ThreadLocal<Boolean>()
        val paClass = findClass("org.telegram.ui.ProfileActivity")
        paClass.hookAll(
            "setForegroundImage",
            cond = ::isEnabled,
            before = { param ->
                if (param.args[0] == false) {
                    inSetFgImg.set(true)
                }
            },
            after = { param ->
                inSetFgImg.set(false)
            }
        )
        pgvClass.hookAllBefore("getImageLocation", cond = ::isEnabled) { param ->
            if (inSetFgImg.get() == true) {
                val pa = param.thisObject.getObj("this$0")
                val currentPhoto = pa.getObj("userInfo")?.getObj("profile_photo")
                    ?: pa.getObj("chatInfo")?.getObj("chat_photo") ?: return@hookAllBefore
                val id = currentPhoto.getObjAs<Long>("id")
                val photos = param.thisObject.getObjAs<List<*>>("photos")
                val idx = photos.indexOfFirst {
                    it?.getObjAs<Long>("id") == id
                }
                if (idx == -1) return@hookAllBefore
                param.args[0] = idx
            }
        }
        /*
        val currentExactIdx = ThreadLocal<Int?>()
        XposedBridge.hookAllMethods(
            pgvClass, "setAnimatedFileMaybe", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!param.thisObject.javaClass.name.startsWith("org.telegram.ui.ProfileActivity")) return
                    val pa = param.thisObject.getObj("this$0")
                    val currentPhoto = pa.getObj("userInfo")?.getObj("profile_photo")
                        ?: pa.getObj("chatInfo")?.getObj("chat_photo") ?: return
                    val id = currentPhoto.getObjAs<Long>("id")
                    val photos = param.thisObject.getObjAs<List<*>>("photos")
                    val idx = photos.indexOfFirst {
                        it?.getObjAs<Long>("id") == id
                    }
                    if (idx == -1) return
                    var exactIdx = 0
                    // I know it's dumb, but ...
                    // https://github.com/DrKLO/Telegram/blob/289c4625035feafbfac355eb01591b726894a623/TMessagesProj/src/main/java/org/telegram/ui/Components/ProfileGalleryView.java#L1502
                    while (param.thisObject.call("getRealPosition", exactIdx) != idx) {
                        exactIdx++
                        // I believe no one can set over 300 photos
                        if (exactIdx > 300) return
                    }
                    logD("current photo for anim idx $idx real $exactIdx")
                    currentExactIdx.set(exactIdx)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    currentExactIdx.set(null)
                }
            }
        )
        val adapterClass = findClass("org.telegram.ui.Components.CircularViewPager\$Adapter")
        XposedBridge.hookAllMethods(adapterClass, "getRealPosition", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val exactIdx = currentExactIdx.get()
                logD("getRealPosition ${param.args[0]} $exactIdx")
                if (exactIdx == null) return

                if (param.args[0] != exactIdx) {
                    param.result = 114514
                } else {
                    logD("set fake position for anim")
                    param.result = 0
                }
            }
        })*/
    }
}
