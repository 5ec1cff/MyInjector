package io.github.a13e300.myinjector.xhs

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.util.concurrent.atomic.AtomicLong

internal fun locateXhsHomeAds(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val process = bridge.findMethod {
        matcher {
            paramTypes("boolean", "java.util.List", "boolean", "boolean")
            returnType = "kotlin.Pair"
            usingEqStrings("Remove duplicate note: id=", "advertisement", "ExplorePresenter")
        }
    }.single()
    val showCache = bridge.findMethod {
        matcher {
            paramTypes("java.lang.String")
            usingEqStrings("show cache notes", "showCache")
        }
    }.single()
    val store = process.declaredClass!!.methods.single {
        it.paramTypeNames == listOf("java.util.List", "boolean") && it.returnTypeName == "void"
    }
    val cache = showCache.invokes.single {
        it.paramTypeNames == listOf("java.lang.String") && it.returnTypeName == "java.util.List"
    }
    val restore = bridge.findMethod {
        matcher {
            declaredClass = showCache.className
            paramTypes("java.util.List", "java.util.List", "android.os.Parcelable", "boolean")
            usingEqStrings("restoreDataAndState")
        }
    }.single()
    val view = process.declaredClass!!.fields.single { it.typeName == showCache.className }
    val channel = showCache.declaredClass!!.fields.single {
        it.typeName == "com.xingin.entities.BaseChannelData"
    }
    return mapOf(
        "process" to process.toObfsInfo(),
        "store" to store.toObfsInfo(),
        "cache" to cache.toObfsInfo(),
        "restore" to restore.toObfsInfo(),
        "view" to view.toObfsInfo(),
        "channel" to channel.toObfsInfo(),
    )
}

internal fun isXhsHomeAd(isAd: Boolean, modelType: String?, adsDesc: String?, showAdTips: Boolean = false): Boolean =
    isAd || showAdTips || modelType == "advertisement" || !adsDesc.isNullOrEmpty()

internal class HomeAdCounter {
    private val filteredCount = AtomicLong()
    val count: Long get() = filteredCount.get()

    fun <T> filter(items: List<T>, countFiltered: Boolean = true, isAd: (T) -> Boolean): List<T> {
        val result = items.filterNot(isAd)
        if (countFiltered) filteredCount.addAndGet((items.size - result.size).toLong())
        return result
    }
}

class HideHomeAds : MyDynHook("hideHomeAds") {
    companion object {
        private val counter = HomeAdCounter()
        val filteredCount: Long get() = counter.count
    }

    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.hideHomeAds

    override fun onHook() {
        val creator = XhsHandler.creator
        val targets by lazy { locateXhsHomeAds(creator.bridge) }
        fun info(key: String) = creator.create("hideHomeAds.$key") { targets.getValue(key) }
        fun method(key: String) = DexMethod(info(key).descriptor).getMethodInstance(classLoader)
            .apply { isAccessible = true }
        fun field(key: String) = DexField(info(key).descriptor).getFieldInstance(classLoader)
            .apply { isAccessible = true }
        fun Class<*>.field(name: String) = getDeclaredField(name).apply { isAccessible = true }

        val viewField = field("view")
        val channelField = field("channel")
        val noteClass = findClass("com.xingin.entities.NoteItemBean")
        val adsClass = findClass("com.xingin.entities.ad.AdsInfo")
        val noteIsAd = noteClass.field("isAd")
        val noteAdTips = noteClass.field("isShowAdTips")
        val modelType = noteClass.field("modelType")
        val adsInfo = noteClass.field("adsInfo")
        val adsDesc = adsClass.field("adsDesc")
        val mediaClasses = listOf("com.xingin.entities.NativeMediaBean", "com.xingin.entities.explorefeed.MediaBean")
            .map { name ->
                val type = findClass(name)
                Triple(type, type.field("isAd"), type.field("isShowAdsTip"))
            }

        fun isAd(item: Any?): Boolean {
            if (noteClass.isInstance(item)) {
                val ads = adsInfo.get(item)
                return isXhsHomeAd(
                    noteIsAd.getBoolean(item), modelType.get(item) as? String,
                    ads?.let { adsDesc.get(it) as? String }, noteAdTips.getBoolean(item),
                )
            }
            // 独立广告卡片会直接以 AdsInfo 缓存在已转换的列表中
            if (adsClass.isInstance(item)) return true
            for ((type, ad, tip) in mediaClasses) {
                if (type.isInstance(item)) return ad.getBoolean(item) || tip.getBoolean(item)
            }
            return false
        }

        fun isRecommend(view: Any) = channelField.get(view).getObjAs<String>("channelId") == "homefeed_recommend"
        fun filtered(items: Any, countFiltered: Boolean = true) =
            counter.filter(items as List<*>, countFiltered, ::isAd)

        // 先过滤原始笔记，再由宿主转换和计算位置，避免留下空卡片
        method("process").hookBefore(cond = ::isEnabled) { param ->
            if (isRecommend(viewField.get(param.thisObject)!!)) param.args[1] = filtered(param.args[1])
        }
        method("store").hookBefore(cond = ::isEnabled) { param ->
            // 同批笔记还会进入 process，只在展示路径计数
            if (isRecommend(viewField.get(param.thisObject)!!)) param.args[0] = filtered(param.args[0], false)
        }
        // 磁盘缓存和页面重建的内存缓存都有直接恢复列表的路径
        method("cache").hookAfter(cond = ::isEnabled) { param ->
            if (param.args[0] == "homefeed_recommend" && param.result != null) {
                param.result = filtered(param.result)
            }
        }
        method("restore").hookBefore(cond = ::isEnabled) { param ->
            if (isRecommend(param.thisObject)) {
                // 第一份是原始笔记，第二份才是恢复到列表的卡片
                param.args[0] = filtered(param.args[0], false)
                param.args[1] = filtered(param.args[1])
            }
        }
    }
}
