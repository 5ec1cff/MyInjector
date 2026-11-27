package io.github.a13e300.myinjector.xhs

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

internal fun locateXhsHomeCache(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val showCache = bridge.findMethod {
        matcher {
            paramTypes("java.lang.String")
            returnType = "void"
            usingEqStrings("show cache notes", "showCache")
        }
    }.single()
    val refresh = bridge.findMethod {
        matcher {
            declaredClass = showCache.className
            returnType = "void"
            usingEqStrings("home feed refresh type:")
        }
    }.single()
    val aheadLoad = bridge.findMethod {
        matcher {
            paramTypes("java.lang.String", "int", "boolean")
            returnType = "void"
            usingEqStrings("load data requested in advance, pos=", ", needShowCache=")
        }
    }.single()
    val modelGetter = showCache.invokes.single {
        it.className == showCache.className && it.returnTypeName == aheadLoad.className
    }
    val cachedNotes = showCache.usingFields.map { it.field }
        .filter { it.className == aheadLoad.className && it.typeName == "java.util.ArrayList" }
        .distinctBy { it.descriptor }.single()
    val view = aheadLoad.declaredClass!!.fields.single { it.typeName == showCache.className }
    val channel = refresh.usingFields.map { it.field }
        .filter { it.className == showCache.className && it.typeName == "com.xingin.entities.BaseChannelData" }
        .distinctBy { it.descriptor }.single()
    return mapOf(
        "showCache" to showCache.toObfsInfo(),
        "refresh" to refresh.toObfsInfo(),
        "aheadLoad" to aheadLoad.toObfsInfo(),
        "modelGetter" to modelGetter.toObfsInfo(),
        "cachedNotes" to cachedNotes.toObfsInfo(),
        "view" to view.toObfsInfo(),
        "channel" to channel.toObfsInfo(),
    )
}

internal fun shouldKeepXhsHomeCache(channelId: String?, coldStart: Boolean, hasCachedNotes: Boolean): Boolean =
    channelId == "homefeed_recommend" && coldStart && hasCachedNotes

class KeepHomeCache : MyDynHook("keepHomeCache") {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.keepHomeCache

    override fun onHook() {
        val creator = XhsHandler.creator
        val targets by lazy { locateXhsHomeCache(creator.bridge) }
        fun info(key: String) = creator.create("keepHomeCache.$key") { targets.getValue(key) }
        fun method(key: String) = DexMethod(info(key).descriptor).getMethodInstance(classLoader)
            .apply { isAccessible = true }
        fun field(key: String) = DexField(info(key).descriptor).getFieldInstance(classLoader)
            .apply { isAccessible = true }

        val showCache = method("showCache")
        val modelGetter = method("modelGetter")
        val notesField = field("cachedNotes")
        val viewField = field("view")
        val channelField = field("channel")
        val noteClass = findClass("com.xingin.entities.NoteItemBean")
        val fromCache = noteClass.getDeclaredField("isFromCache").apply { isAccessible = true }
        val viewClass = showCache.declaringClass
        val setHasLoadData = viewClass.getDeclaredMethod("setHasLoadData", Boolean::class.javaPrimitiveType)
        val setLoadFinish = viewClass.getDeclaredMethod("setLoadFinish", Boolean::class.javaPrimitiveType)

        fun cachedNotes(model: Any): Boolean = (notesField.get(model) as? List<*>)?.any {
            noteClass.isInstance(it) && fromCache.getBoolean(it)
        } == true

        fun keepCache(view: Any) {
            // 缓存已填入列表，没有网络回调时也允许手动刷新和加载更多
            setHasLoadData.invoke(view, true)
            setLoadFinish.invoke(view, true)
        }

        method("refresh").hookBefore(cond = ::isEnabled) { param ->
            val coldStart = (param.args[0] as? Enum<*>)?.name == "COLD_START"
            if (!coldStart) return@hookBefore
            val view = param.thisObject
            val channelId = channelField.get(view).getObjAs<String>("channelId")
            if (shouldKeepXhsHomeCache(channelId, coldStart, cachedNotes(modelGetter.invoke(view)!!))) {
                keepCache(view)
                param.result = null
            }
        }

        method("aheadLoad").hookBefore(cond = ::isEnabled) { param ->
            val channelId = param.args[0] as String
            if (channelId != "homefeed_recommend") return@hookBefore
            val model = param.thisObject
            val view = viewField.get(model)!!
            // 预加载路径尚未显示磁盘缓存，先沿用宿主的缓存展示和校验逻辑
            if (param.args[2] == true && (notesField.get(model) as? List<*>).isNullOrEmpty()) {
                showCache.invoke(view, channelId)
            }
            if (shouldKeepXhsHomeCache(channelId, true, cachedNotes(model))) {
                keepCache(view)
                param.result = null
            }
        }
    }
}
