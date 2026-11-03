package io.github.a13e300.myinjector.telegram

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callOrig
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.logE
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Proxy

// Only searches opened from the message menu use this two-way result window.
internal class AnchoredMessageSearch(
    private val loader: ClassLoader,
    chatClass: Class<*>,
    private val enabled: () -> Boolean,
    private val error: (Throwable) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val creator = TelegramHandler.creator
    private val found by lazy { findAnchoredSearch(creator.bridge, chatClass.name) }
    private val fieldKeys = setOf("list", "results", "loaded", "flicker", "adapter")
    private val members = (fieldKeys + setOf("refresh", "fullRefresh", "scroll", "getLayout", "first", "last", "position", "view")).associateWith { key ->
        creator.create("AnchoredSearch.$key") {
            val descriptor = found.getValue(key)
            if (key in fieldKeys) DexField(descriptor).let { ObfsInfo(it.className, it.name, descriptor) }
            else DexMethod(descriptor).let { ObfsInfo(it.className, it.name, descriptor) }
        }
    }
    private val fields = fieldKeys.associateWith { DexField(members.getValue(it).descriptor).getFieldInstance(loader).apply { isAccessible = true } }
    private val methods = (members.keys - fieldKeys).associateWith { DexMethod(members.getValue(it).descriptor).getMethodInstance(loader).apply { isAccessible = true } }
    private val mediaClass = loader.loadClass("org.telegram.messenger.MediaDataController")
    private val requestClass = loader.loadClass("org.telegram.tgnet.TLRPC\$TL_messages_search")
    private val delegateClass = loader.loadClass("org.telegram.tgnet.RequestDelegate")
    private val messageConstructor = loader.loadClass("org.telegram.messenger.MessageObject").getDeclaredConstructor(
        Int::class.javaPrimitiveType, loader.loadClass("org.telegram.tgnet.TLRPC\$Message"),
        Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
    private val events = loader.loadClass("org.telegram.messenger.NotificationCenter")
    private val loadingEvent = events.getObjSAs<Int>("chatSearchResultsLoading")
    private val resultEvent = events.getObjSAs<Int>("chatSearchResultsAvailable")
    private val sessions = mutableMapOf<Any, Session>()
    private var pending: Pair<Any, Any>? = null
    private var publishing = false
    private var scrollDirection: Int? = null

    init {
        mediaClass.declaredMethods.single { it.name == "searchMessagesInChat" && it.parameterCount >= 11 && it.parameterTypes[6] == Boolean::class.javaPrimitiveType }.hookBefore { p ->
            val arm = pending
            if (arm != null && arm.first.call("getMediaDataController") === p.thisObject && (p.args[7] != null || p.args[8] != null)) {
                sessions.remove(p.thisObject)?.close()
                val session = Session(arm.first, arm.second, p.thisObject, p.args.copyOf())
                sessions[p.thisObject] = session
                p.result = null
                runCatching { session.start() }.onFailure { session.fail(it) }
            } else {
                val session = sessions[p.thisObject]
                if (session != null) {
                    if (p.args[0] == null && p.args[3] == session.guid && p.args[4] in listOf(1, 2) && enabled()) {
                        p.result = null
                        runCatching { session.move(p.args[4] as Int) }.onFailure { session.fail(it) }
                    } else {
                        sessions.remove(p.thisObject)?.close()
                    }
                }
            }
        }
        mediaClass.hookAllBefore("loadMoreSearchMessages") { p ->
            sessions[p.thisObject]?.let { session ->
                p.result = null
                if (!publishing && (scrollDirection == null || scrollDirection!! > 0)) {
                    runCatching { session.load(1) }.onFailure { session.fail(it) }
                }
            }
        }
        mediaClass.hookAllBefore("searchEndReached") { p -> sessions[p.thisObject]?.let { p.result = it.olderEnd } }
        mediaClass.hookAllBefore("isSearchLoading") { p -> sessions[p.thisObject]?.let { p.result = it.request != 0 } }
        mediaClass.hookAllBefore("getMask") { p -> sessions[p.thisObject]?.let { p.result = it.mask() } }
        mediaClass.hookAllBefore("clearFoundMessageObjects") { p -> sessions.remove(p.thisObject)?.close() }
        chatClass.hookAllBefore("onFragmentDestroy") { p ->
            sessions.entries.filter { it.value.chat === p.thisObject }.toList().forEach { (controller, session) ->
                sessions.remove(controller); session.close()
            }
        }
        methods.getValue("refresh").hookBefore { p ->
            val session = sessions.values.firstOrNull { it.adapter() === p.thisObject } ?: return@hookBefore
            @Suppress("UNCHECKED_CAST")
            val rows = fields.getValue("results").get(p.thisObject) as MutableList<Any>
            rows.clear(); rows.addAll(session.messages)
            fields.getValue("loaded").setInt(p.thisObject, rows.size)
            fields.getValue("flicker").setInt(p.thisObject, 0)
            methods.getValue("fullRefresh").callOrig(p.thisObject)
            p.result = null
        }
        methods.getValue("scroll").hook(
            before = { p -> if (sessions.values.any { it.list() === p.args[0] }) scrollDirection = p.args[2] as Int },
            after = { p ->
                scrollDirection = null
                if (!publishing) {
                    val session = sessions.values.firstOrNull { it.list() === p.args[0] }
                    val layout = session?.layout()
                    if (session != null && layout != null && session.request == 0 && session.messages.isNotEmpty()) {
                        runCatching {
                            val dy = p.args[2] as Int
                            if (dy < 0 && (methods.getValue("first").invoke(layout) as Int) < 5) session.load(-1)
                            else if (dy > 0 && (methods.getValue("last").invoke(layout) as Int) >= session.messages.size - 5) session.load(1)
                        }.onFailure { session.fail(it) }
                    }
                }
            },
        )
    }

    fun fromMessage(chat: Any, message: Any, search: () -> Unit) {
        if ((message.call("getId") as Int) <= 0) { search(); return }
        pending = chat to message
        try { search() } finally { pending = null }
    }

    private inner class Session(val chat: Any, anchor: Any, val media: Any, val args: Array<Any?>) {
        val guid = args[3] as Int
        private val account = chat.call("getCurrentAccount") as Int
        private val connections = chat.call("getConnectionsManager")!!
        private val controller = chat.call("getMessagesController")!!
        private val notification = chat.call("getNotificationCenter")!!
        private val anchorId = anchor.call("getId") as Int
        private val anchorDialog = anchor.call("getDialogId") as Long
        private val peers = listOf(args[1] as Long, args[2] as Long).filter { it != 0L }.distinct()
        private val window = MessageSearchWindow(peers, SearchMessageKey(anchorDialog, anchorId))
        val messages = ArrayList<Any>()
        var request = 0
        val olderEnd get() = window.olderEnd
        private var closed = false
        private var first = true
        private var selected = 0
        private var navigation = 0

        fun list() = fields.getValue("list").get(chat) as? ViewGroup
        fun adapter() = fields.getValue("adapter").get(chat)
        fun layout() = list()?.let { methods.getValue("getLayout").invoke(it) }
        private fun id(m: Any) = m.call("getId") as Int
        private fun dialog(m: Any) = m.call("getDialogId") as Long
        fun mask(): Int {
            val position = media.getObj("lastReturnedNum") as Int
            return (if (!olderEnd || position < messages.lastIndex) 1 else 0) or
                (if (!window.newerEnd || position > 0) 2 else 0)
        }

        fun start() {
            // Stop requests started while opening the search field, including
            // callbacks already queued on the UI thread.
            for (name in listOf("reqId", "mergeReqId")) {
                val old = media.getObj(name) as Int
                if (old != 0) connections.call("cancelRequest", old, true)
                media.setObj(name, 0)
            }
            media.setObj("lastReqId", (media.getObj("lastReqId") as Int) + 1)
            media.setObj("lastReturnedNum", 0)
            media.setObj("lastSearchQuery", args[0] as? String ?: "")
            media.setObj("messagesLocalSearchCount", 0)
            media.setObj("loadingMoreSearchMessages", false)
            media.setObj("loadingSearchLocal", false)
            media.setObj("loadedPredirectedSearchLocal", false)
            for (name in listOf("searchResultMessages", "searchServerResultMessages", "searchLocalResultMessages")) {
                (media.getObj(name) as MutableList<*>).clear()
            }
            notify(loadingEvent, guid)
            fetch(window.initial)
        }

        fun close() {
            closed = true
            if (request != 0) connections.call("cancelRequest", request, true)
            request = 0
        }

        fun load(direction: Int) {
            if (closed || request != 0 || !enabled() || first) return
            window.next(direction)?.let(::fetch)
        }

        fun move(direction: Int) {
            if (messages.isEmpty()) return
            selected = (media.getObj("lastReturnedNum") as Int).coerceIn(0, messages.lastIndex)
            val next = selected + if (direction == 2) -1 else 1
            if (next in messages.indices) { selected = next; publish(true) }
            else { navigation = if (direction == 2) -1 else 1; load(navigation) }
        }

        private fun fetch(page: SearchPage) {
            val peer = page.peer
            val req = requestClass.getDeclaredConstructor().newInstance()
            req.setObj("peer", controller.call("getInputPeer", peers[peer]))
            req.setObj("q", args[0] as? String ?: "")
            req.setObj("filter", args.getOrNull(11) ?: loader.loadClass("org.telegram.tgnet.TLRPC\$TL_inputMessagesFilterEmpty").getDeclaredConstructor().newInstance())
            req.setObj("limit", 40)
            req.setObj("offset_id", page.offset)
            req.setObj("add_offset", page.addOffset)
            var flags = 0
            val sender = args[7] ?: args[8]
            if (sender != null) {
                req.setObj("from_id", controller.javaClass.callS("getInputPeer", sender)); flags = flags or 1
            }
            val topic = args[5] as Long
            if (topic != 0L && peers[peer] == args[1]) {
                val saved = peers[peer] == (chat.call("getUserConfig").getObj("clientUserId") as Long) ||
                    chat.call("getMessagesStorage").call("isMonoForum", peers[peer]) == true
                if (saved) { req.setObj("saved_peer_id", controller.call("getInputPeer", topic)); flags = flags or 4 }
                else { req.setObj("top_msg_id", topic.toInt()); flags = flags or 2 }
            }
            args[10]?.let { reaction ->
                @Suppress("UNCHECKED_CAST")
                (req.getObj("saved_reaction") as MutableList<Any>).add(reaction.call("toTLReaction")!!)
                flags = flags or 8
            }
            req.setObj("flags", flags)
            val callback = Proxy.newProxyInstance(loader, arrayOf(delegateClass)) { proxy, method, response ->
                if (method.name == "run") handler.post {
                    if (!enabled() && sessions[media] === this) { sessions.remove(media); close() }
                    if (!closed && sessions[media] === this && enabled()) {
                        request = 0
                        runCatching {
                            if (response!![1] != null || response[0] == null) {
                                notify(resultEvent, guid, 0, mask(), 0L, selected, messages.size, false)
                                first = false
                                Toast.makeText(list()?.context, "搜索加载失败，请重新搜索或滚动重试", Toast.LENGTH_SHORT).show()
                            } else received(response[0]!!, page)
                        }.onFailure { fail(it) }
                    }
                }
                else when (method.name) {
                    "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                    "equals" -> return@newProxyInstance response?.get(0) === proxy
                    "toString" -> return@newProxyInstance "AnchoredSearchCallback"
                }
                null
            }
            request = connections.call("sendRequest", req, callback) as Int
            connections.call("bindRequestToGuid", request, guid)
        }

        private fun received(response: Any, page: SearchPage) {
            controller.call("putUsers", response.getObj("users"), false)
            controller.call("putChats", response.getObj("chats"), false)
            val oldTop = layout()?.let { methods.getValue("first").invoke(it) as Int } ?: -1
            val oldMessage = messages.getOrNull(oldTop)
            val oldView = layout()?.let { methods.getValue("view").invoke(it, oldTop) as? View }
            val top = (oldView?.top ?: 0) - (list()?.paddingTop ?: 0)
            val selectedMessage = messages.getOrNull(selected)
            @Suppress("UNCHECKED_CAST")
            val raw = response.getObj("messages") as List<Any>
            val usable = raw.filter { it.javaClass.name != "org.telegram.tgnet.TLRPC\$TL_messageEmpty" &&
                it.getObj("action")?.javaClass?.name != "org.telegram.tgnet.TLRPC\$TL_messageActionHistoryClear" }
            val added = window.add(page, usable.map { it.getObj("id") as Int }).map { it.id }.toSet()
            for (m in usable.distinctBy { it.getObj("id") }.filter { it.getObj("id") in added }) {
                val obj = messageConstructor.newInstance(account, m, true, true)
                obj.setObj("isPrimaryGroupMessage", true)
                obj.setObj("isSavedFiltered", true)
                obj.call("setQuery", args[0] as? String ?: "", true)
                messages.add(obj)
            }
            messages.sortWith(compareBy<Any> { peers.indexOf(dialog(it)) }.thenByDescending(::id))
            if (page.direction != 0 && added.isEmpty()) {
                window.next(page.direction)?.takeIf { it.peer != page.peer }?.let { fetch(it); return }
            }
            selected = selectedMessage?.let { messages.indexOf(it) }?.coerceAtLeast(0) ?: 0
            val wasFirst = first
            if (first) {
                first = false
                selected = messages.indexOfFirst { dialog(it) == anchorDialog && id(it) == anchorId }
                if (selected < 0) selected = messages.indexOfFirst { dialog(it) == anchorDialog && id(it) <= anchorId }.coerceAtLeast(0)
            } else if (navigation != 0) {
                selected = (selected + navigation).coerceIn(0, messages.lastIndex.coerceAtLeast(0))
            }
            val jump = navigation != 0
            navigation = 0
            publish(jump)
            val target = if (wasFirst) selected else oldMessage?.let { messages.indexOf(it) } ?: -1
            if (target >= 0) layout()?.let { methods.getValue("position").invoke(it, target, if (wasFirst) 0 else top) }
        }

        private fun publish(jump: Boolean) {
            for (name in listOf("searchResultMessages", "searchServerResultMessages")) {
                @Suppress("UNCHECKED_CAST")
                (media.getObj(name) as MutableList<Any>).apply { clear(); addAll(messages) }
            }
            @Suppress("UNCHECKED_CAST")
            (media.getObj("searchLocalResultMessages") as MutableList<Any>).clear()
            media.setObj("lastReturnedNum", selected)
            (media.getObj("messagesSearchCount") as IntArray).apply { this[0] = messages.size; this[1] = 0 }
            @Suppress("UNCHECKED_CAST")
            val maps = media.getObj("searchServerResultMessagesMap") as Array<Any>
            maps.forEach { it.call("clear") }
            messages.forEach { m -> maps[if (dialog(m) == peers[0]) 0 else 1].call("put", id(m), m) }
            val m = messages.getOrNull(selected)
            publishing = true
            try { notify(resultEvent, guid, m?.let(::id) ?: 0, mask(), m?.let(::dialog) ?: 0L, selected, messages.size, jump) }
            finally { publishing = false }
        }

        private fun notify(event: Int, vararg values: Any?) { notification.call("postNotificationName", event, values) }
        fun fail(t: Throwable) {
            logE("Anchored message search failed", t); error(t)
            close()
            sessions.remove(media)
            Toast.makeText(list()?.context, "搜索加载失败，请重新搜索", Toast.LENGTH_SHORT).show()
        }
    }
}
