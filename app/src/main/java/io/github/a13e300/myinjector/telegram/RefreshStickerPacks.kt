package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.deoptimize
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookBefore
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.WeakHashMap

class RefreshStickerPacks : MyDynHook("refreshStickerPacks") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.refreshStickerPacks

    private class EmojiLoad(val owner: Any) {
        var depth = 0
        var pending = 0
        var failed = false
        var controller: Any? = null
        val sets = mutableListOf<Any>()
    }

    override fun onHook() {
        val creator = TelegramHandler.creator
        val found by lazy { findStickerPackRefresh(creator.bridge) }
        val fields = setOf("input", "set", "event", "emojiLoader")
        val members = listOf("load", "getName", "getId", "input", "set", "emojiShow", "emojiInit", "emojiLoader", "emojiNotification",
            "get", "fetch", "put", "post", "notification", "event").associateWith { key ->
            creator.create("RefreshStickerPacks.$key") {
                val descriptor = found.getValue(key)
                if (descriptor.isEmpty()) {
                    ObfsInfo("", "")
                } else if (key in fields) {
                    val field = DexField(descriptor)
                    ObfsInfo(field.className, field.name, descriptor)
                } else {
                    val method = DexMethod(descriptor)
                    ObfsInfo(method.className, method.name, descriptor)
                }
            }
        }
        fun field(key: String) = DexField(members.getValue(key).descriptor)
            .getFieldInstance(classLoader).apply { isAccessible = true }
        fun method(key: String) = DexMethod(members.getValue(key).descriptor)
            .getMethodInstance(classLoader).apply { isAccessible = true }
        val input = field("input")
        val set = field("set")
        val inputConstructor = findClass("org.telegram.tgnet.TLRPC\$TL_inputStickerSetID").getConstructor()
        val opened = WeakHashMap<Any, Unit>()
        val load = method("load")
        // 避免 ART 内联缓存查询后绕过 Hook。
        load.deoptimize()
        val normalCalls = ThreadLocal<ArrayDeque<Boolean>>()
        load.hook(cond = ::isEnabled, before = { param ->
            val stack = normalCalls.get() ?: ArrayDeque<Boolean>().also { normalCalls.set(it) }
            stack.addLast(false)
            val alert = param.thisObject
            if (opened.containsKey(alert)) return@hook
            val remoteInput = input.get(alert) ?: set.get(alert)?.getObj("set")?.let { metadata ->
                inputConstructor.newInstance().apply {
                    javaClass.getField("id").set(this, metadata.getObj("id"))
                    javaClass.getField("access_hash").set(this, metadata.getObj("access_hash"))
                }
            } ?: return@hook
            opened[alert] = Unit
            input.set(alert, remoteInput)
            set.set(alert, null)
            stack[stack.lastIndex] = true
        }, after = {
            val stack = checkNotNull(normalCalls.get())
            stack.removeLast()
            if (stack.isEmpty()) normalCalls.remove()
        })
        // 官方 APK 已删掉 force 参数。在本次加载期间跳过两个内存查询。
        for (key in listOf("getName", "getId")) method(key).hookBefore(cond = ::isEnabled) {
            if (normalCalls.get()?.any { active -> active } == true) it.result = null
        }

        val fetch = method("fetch")
        val put = method("put")
        val post = method("post")
        val notification = method("notification")
        val event = field("event").getInt(null)
        val get = method("get")
        val response = get.parameterTypes.last().methods.single {
            Modifier.isAbstract(it.modifiers) && it.parameterCount == 1
        }
        val callbackType = fetch.parameterTypes.last()
        val active = ThreadLocal<ArrayDeque<EmojiLoad>>()
        val loading = WeakHashMap<Any, EmojiLoad>()
        fun finish(batch: EmojiLoad) {
            if (batch.depth != 0 || batch.pending != 0) return
            loading.remove(batch.owner)
            val controller = batch.controller ?: return
            for (pack in batch.sets) put.invoke(controller, pack, false)
            if (batch.failed) return
            val last = batch.sets.lastOrNull() ?: return
            post.invoke(notification.invoke(controller), event,
                arrayOf(last.getObj("set").getObj("id"), last))
        }
        val emojiLoader = field("emojiLoader")
        for (key in listOf("emojiShow", "emojiInit").filter { members.getValue(it).descriptor.isNotEmpty() }) {
            val entry = method(key)
            entry.deoptimize()
            entry.hook(cond = ::isEnabled,
                before = { param ->
                    val owner = if (key == "emojiShow") emojiLoader.get(param.thisObject)!! else param.thisObject
                    val batch = loading.getOrPut(owner) { EmojiLoad(owner) }
                    batch.depth++
                    val stack = active.get() ?: ArrayDeque<EmojiLoad>().also { active.set(it) }
                    stack.addLast(batch)
                },
                after = {
                    val stack = checkNotNull(active.get())
                    val batch = stack.removeLast()
                    if (stack.isEmpty()) active.remove()
                    batch.depth--
                    finish(batch)
                })
        }
        // 原监听器会一次填充所有空槽。等所有远端结果齐全，避免多包弹窗读回旧包。
        method("emojiNotification").hookBefore(cond = ::isEnabled) { param ->
            if (loading.containsKey(param.thisObject)) param.result = null
        }
        get.hookBefore(cond = ::isEnabled) { param ->
            val batch = active.get()?.lastOrNull() ?: return@hookBefore
            val controller = param.thisObject
            val callback = param.args.last()
            batch.controller = controller
            batch.pending++
            val remote = Proxy.newProxyInstance(classLoader, arrayOf(callbackType)) { proxy, called, args ->
                when (called.name) {
                    "toString" -> "RefreshStickerPacks.Callback"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> {
                        val pack = args!![1]
                        if (pack != null) batch.sets += pack else batch.failed = true
                        try {
                            if (callback != null) response.invoke(callback, pack)
                        } finally {
                            batch.pending--
                            finish(batch)
                        }
                        null
                    }
                }
            }
            fetch.invoke(controller, param.args[0], remote)
            param.result = null
        }
    }
}
