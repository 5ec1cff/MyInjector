package io.github.a13e300.myinjector.telegram

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import io.github.a13e300.myinjector.arch.toObfsInfo
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
import java.time.LocalDate

// Menu searches and filtered searches share the two-way result window.
internal class AnchoredMessageSearch(
    private val loader: ClassLoader,
    chatClass: Class<*>,
    private val enabled: () -> Boolean,
    private val error: (Throwable) -> Unit,
    officialControls: Boolean = false,
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
    private val mediaClass = telegramApiClass(loader, "org.telegram.messenger.MediaDataController")
    private val state by lazy { findSearchState(creator.bridge, chatClass.name) }
    private val stateKeys = listOf("searchMessagesInChat", "getMask", "getSearchCount", "searchEndReached", "loadMoreSearchMessages", "clearFoundMessageObjects",
        "lastReturnedNum", "searchResultMessages", "messagesLocalSearchCount", "messagesSearchCount", "searchServerResultMessages",
        "searchLocalResultMessages", "mergeReqId", "reqId", "lastReqId", "lastSearchQuery", "searchServerResultMessagesMap",
        "loadingMoreSearchMessages", "loadingSearchLocal", "loadedPredirectedSearchLocal", "postNotificationName",
        "chatSearchResultsLoading", "chatSearchResultsAvailable", "isPrimaryGroupMessage", "isSavedFiltered")
    private val stateMembers = stateKeys.associateWith { key -> creator.create("SearchState.$key") { bridge ->
        val value = state.getValue(key)
        if ('(' in value) bridge.getMethodData(value)!!.toObfsInfo() else bridge.getFieldData(value)!!.toObfsInfo()
    } }
    private val stateFields = stateMembers.filterValues { '(' !in it.descriptor }.mapValues {
        DexField(it.value.descriptor).getFieldInstance(loader).apply { isAccessible = true }
    }
    private val stateMethods = stateMembers.filterValues { '(' in it.descriptor }.mapValues {
        DexMethod(it.value.descriptor).getMethodInstance(loader).apply { isAccessible = true }
    }
    private fun readMedia(media: Any, name: String): Any? = stateFields.getValue(name).get(media)
    private fun writeMedia(media: Any, name: String, value: Any?) = stateFields.getValue(name).set(media, value)
    private val messageId = telegramApiMethod(loader, "org.telegram.messenger.MessageObject", "getId")
    private val messageDialog = telegramApiMethod(loader, "org.telegram.messenger.MessageObject", "getDialogId")
    private val peerId = telegramApiMethod(loader, "org.telegram.messenger.MessageObject", "getPeerId", "org.telegram.tgnet.TLRPC\$Peer")
    private val messageOwner = telegramApiField(loader, "org.telegram.messenger.MessageObject", "messageOwner")
    private val setQuery = telegramApiMethod(loader, "org.telegram.messenger.MessageObject", "setQuery", "java.lang.String", "boolean")
    private val clientId = telegramApiMethod(loader, "org.telegram.messenger.UserConfig", "getClientUserId")
    private val inputPeer = telegramApiMethod(loader, "org.telegram.messenger.MessagesController", "getInputPeer", "long")
    private val inputPeerObject = telegramApiMethod(loader, "org.telegram.messenger.MessagesController", "getInputPeer", "org.telegram.tgnet.TLObject")
    private val putUsers = telegramApiMethod(loader, "org.telegram.messenger.MessagesController", "putUsers", "java.util.ArrayList", "boolean")
    private val putChats = telegramApiMethod(loader, "org.telegram.messenger.MessagesController", "putChats", "java.util.ArrayList", "boolean")
    private val isMonoForum = telegramApiMethod(loader, "org.telegram.messenger.MessagesStorage", "isMonoForum", "long")
    private val requestClass = loader.loadClass("org.telegram.tgnet.TLRPC\$TL_messages_search")
    private val delegateClass = loader.loadClass("org.telegram.tgnet.RequestDelegate")
    private val messageConstructor = telegramApiClass(loader, "org.telegram.messenger.MessageObject").getDeclaredConstructor(
        Int::class.javaPrimitiveType, loader.loadClass("org.telegram.tgnet.TLRPC\$Message"),
        Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
    private val loadingEvent = stateFields.getValue("chatSearchResultsLoading").getInt(null)
    private val resultEvent = stateFields.getValue("chatSearchResultsAvailable").getInt(null)
    private val sessions = mutableMapOf<Any, Session>()
    private var pending: Pair<Any, Any>? = null
    private var publishing = false
    private var scrollDirection: Int? = null
    private class SearchContext(val chat: Any, var args: Array<Any?>) {
        var filters = MessageSearchFilters()
        var generation = 0
        var dateRequest = 0
    }
    private val contexts = mutableMapOf<Any, SearchContext>()
    private val controls = if (officialControls) SearchFiltersUi(chatClass, enabled,
        { chat -> contexts[chat]?.filters }, ::changeFilters, error) else null

    init {
        stateMethods.getValue("searchMessagesInChat").hookBefore { p ->
            val arm = pending
            val chat = arm?.first ?: controls?.chatFor(p.thisObject, p.args[3] as Int)
            val context = chat?.let { contexts.getOrPut(it) { SearchContext(it, p.args.copyOf()) } }
            if (context != null && p.args[0] != null) {
                cancelDate(context)
                context.args = p.args.copyOf()
            }
            if (arm != null && arm.first.call("getMediaDataController") === p.thisObject && (p.args[7] != null || p.args[8] != null)) {
                sessions.remove(p.thisObject)?.close()
                val anchor = SearchMessageKey(messageDialog.invoke(arm.second) as Long, messageId.invoke(arm.second) as Int)
                val session = Session(arm.first, anchor, p.thisObject, p.args.copyOf(), context?.filters ?: MessageSearchFilters())
                sessions[p.thisObject] = session
                p.result = null
                runCatching { session.start() }.onFailure { session.fail(it) }
            } else if (context != null && context.filters.active && p.args[0] != null && enabled()) {
                p.result = null
                restart(context)
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
        stateMethods.getValue("loadMoreSearchMessages").hookBefore { p ->
            sessions[p.thisObject]?.let { session ->
                p.result = null
                if (!publishing && (scrollDirection == null || scrollDirection!! > 0)) {
                    runCatching { session.load(1) }.onFailure { session.fail(it) }
                }
            }
        }
        stateMethods.getValue("searchEndReached").hookBefore { p -> sessions[p.thisObject]?.let { p.result = it.olderEnd } }
        stateMethods.getValue("getMask").hookBefore { p -> sessions[p.thisObject]?.let { p.result = it.mask() } }
        stateMethods.getValue("clearFoundMessageObjects").hookBefore { p ->
            sessions.remove(p.thisObject)?.close()
            contexts.values.filter { it.chat.call("getMediaDataController") === p.thisObject && controls?.isSearching(it.chat) == false }
                .toList().forEach { context -> cancelDate(context); contexts.remove(context.chat) }
        }
        chatClass.hookAllBefore("onFragmentDestroy") { p ->
            contexts.remove(p.thisObject)?.let(::cancelDate)
            controls?.forget(p.thisObject)
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
        if ((messageId.invoke(message) as Int) <= 0) { search(); return }
        pending = chat to message
        try { search() } finally { pending = null }
    }

    private fun cancelDate(context: SearchContext) {
        context.generation++
        if (context.dateRequest != 0) context.chat.call("getConnectionsManager").call("cancelRequest", context.dateRequest, true)
        context.dateRequest = 0
    }

    private fun restart(context: SearchContext, anchor: SearchMessageKey? = null, time: Int? = null) {
        val media = context.chat.call("getMediaDataController")!!
        sessions.remove(media)?.close()
        val session = Session(context.chat, anchor ?: SearchMessageKey(context.args[1] as Long, 0), media,
            context.args.copyOf(), context.filters, time)
        sessions[media] = session
        runCatching { session.start(); controls?.showResults(context.chat) }.onFailure { session.fail(it) }
    }

    private fun changeFilters(chat: Any, filters: MessageSearchFilters, date: LocalDate?) {
        val context = contexts[chat] ?: return
        val media = chat.call("getMediaDataController")!!
        val anchor = if (filters.type != context.filters.type) {
            sessions[media]?.takeIf { it.chat === chat }?.currentAnchor() ?: currentSearchAnchor(chat, media)
        } else null
        cancelDate(context)
        context.filters = filters
        if (date == null) restart(context, anchor) else jumpToDate(context, filters.jumpTime(date))
    }

    private fun currentSearchAnchor(chat: Any, media: Any): SearchMessageKey? {
        val view = fields.getValue("list").get(chat) as? ViewGroup
        val adapter = fields.getValue("adapter").get(chat)
        var message: Any? = null
        if (view?.isShown == true && !view.isLayoutRequested && adapter != null) {
            val layout = methods.getValue("getLayout").invoke(view)
            if (layout != null) {
                val first = methods.getValue("first").invoke(layout) as Int
                message = (fields.getValue("results").get(adapter) as List<*>).getOrNull(first)
            }
        }
        // In chat view, use the result selected by native search arrows.
        if (message == null) {
            message = (readMedia(media, "searchResultMessages") as List<*>).getOrNull(readMedia(media, "lastReturnedNum") as Int)
        }
        return message?.let { SearchMessageKey(messageDialog.invoke(it) as Long, messageId.invoke(it) as Int) }
            ?.takeIf { it.id > 0 }
    }

    private fun jumpToDate(context: SearchContext, time: Int) {
        val chat = context.chat
        val peers = listOf(context.args[1] as Long, context.args[2] as Long).filter { it != 0L }.distinct()
        val generation = context.generation
        val found = mutableListOf<Pair<SearchMessageKey, Int>>()
        val controller = chat.call("getMessagesController")!!
        fun seek(index: Int) {
            val req = loader.loadClass("org.telegram.tgnet.TLRPC\$TL_messages_getHistory").getDeclaredConstructor().newInstance()
            req.setObj("peer", inputPeer.invoke(controller, peers[index]))
            req.setObj("offset_date", time)
            req.setObj("limit", 1)
            context.dateRequest = sendRequest(chat, req) { response, failure ->
                if (contexts[chat] !== context || generation != context.generation || controls?.isSearching(chat) != true || !enabled()) return@sendRequest
                context.dateRequest = 0
                if (failure != null || response == null) {
                    Toast.makeText((fields.getValue("list").get(chat) as View).context, "日期定位失败，请重试", Toast.LENGTH_SHORT).show()
                    return@sendRequest
                }
                @Suppress("UNCHECKED_CAST")
                val message = (response.getObj("messages") as List<Any>).firstOrNull { (it.getObj("id") as Int) > 0 }
                if (message != null) found += SearchMessageKey(peers[index], message.getObj("id") as Int) to (message.getObj("date") as Int)
                if (index < peers.lastIndex) seek(index + 1)
                else restart(context, found.maxByOrNull { it.second }?.first ?: SearchMessageKey(peers.last(), 1), time)
            }
        }
        seek(0)
    }

    private fun sendRequest(chat: Any, req: Any, complete: (Any?, Any?) -> Unit): Int {
        val callback = Proxy.newProxyInstance(loader, arrayOf(delegateClass)) { proxy, method, response ->
            when (method.name) {
                "run" -> { handler.post { runCatching { complete(response!![0], response[1]) }.onFailure {
                    logE("Message search request failed", it); error(it)
                } }; null }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> response?.get(0) === proxy
                "toString" -> "MessageSearchCallback"
                else -> null
            }
        }
        val connections = chat.call("getConnectionsManager")!!
        val id = connections.call("sendRequest", req, callback) as Int
        connections.call("bindRequestToGuid", id, chat.call("getClassGuid"))
        return id
    }

    private inner class Session(val chat: Any, anchor: SearchMessageKey, val media: Any, val args: Array<Any?>,
        private val filters: MessageSearchFilters, private val anchorTime: Int? = null) {
        val guid = args[3] as Int
        private val account = chat.call("getCurrentAccount") as Int
        private val connections = chat.call("getConnectionsManager")!!
        private val controller = chat.call("getMessagesController")!!
        private val notification = chat.call("getNotificationCenter")!!
        private val sender = args[7] ?: args[8]
        // Telegram rejects from_id with a media filter in group searches.
        private val localSender = sender != null && filters.type != SearchMessageType.ALL
        private val senderId = sender?.let {
            val id = it.getObj("id") as Long
            if (loader.loadClass("org.telegram.tgnet.TLRPC\$Chat").isInstance(it)) -id else id
        }
        private val selfId = clientId.invoke(chat.call("getUserConfig")) as Long
        private val anchorId = anchor.id
        private val anchorDialog = anchor.dialog
        private val peers = listOf(args[1] as Long, args[2] as Long).filter { it != 0L }.distinct()
        private val window = MessageSearchWindow(peers, SearchMessageKey(anchorDialog, anchorId))
        val messages = ArrayList<Any>()
        var request = 0
        val olderEnd get() = window.olderEnd
        private var closed = false
        private var first = true
        private var selected = 0
        private var navigation = 0
        private var cancelFillCheck: (() -> Unit)? = null

        fun list() = fields.getValue("list").get(chat) as? ViewGroup
        fun adapter() = fields.getValue("adapter").get(chat)
        fun layout() = list()?.let { methods.getValue("getLayout").invoke(it) }
        private fun id(m: Any) = messageId.invoke(m) as Int
        private fun dialog(m: Any) = messageDialog.invoke(m) as Long
        fun currentAnchor(): SearchMessageKey =
            (if (!first) currentSearchAnchor(chat, media) else null) ?: SearchMessageKey(anchorDialog, anchorId)

        fun mask(): Int {
            val position = readMedia(media, "lastReturnedNum") as Int
            return (if (!olderEnd || position < messages.lastIndex) 1 else 0) or
                (if (!window.newerEnd || position > 0) 2 else 0)
        }

        fun start() {
            // Stop requests started while opening the search field, including
            // callbacks already queued on the UI thread.
            for (name in listOf("reqId", "mergeReqId")) {
                val old = readMedia(media, name) as Int
                if (old != 0) connections.call("cancelRequest", old, true)
                writeMedia(media, name, 0)
            }
            writeMedia(media, "lastReqId", (readMedia(media, "lastReqId") as Int) + 1)
            writeMedia(media, "lastReturnedNum", 0)
            writeMedia(media, "lastSearchQuery", args[0] as? String ?: "")
            writeMedia(media, "messagesLocalSearchCount", 0)
            writeMedia(media, "loadingMoreSearchMessages", false)
            writeMedia(media, "loadingSearchLocal", false)
            writeMedia(media, "loadedPredirectedSearchLocal", false)
            for (name in listOf("searchResultMessages", "searchServerResultMessages", "searchLocalResultMessages")) {
                (readMedia(media, name) as MutableList<*>).clear()
            }
            notify(loadingEvent, guid)
            fetch(window.initial)
        }

        fun close() {
            closed = true
            clearFillCheck()
            if (request != 0) connections.call("cancelRequest", request, true)
            request = 0
        }

        private fun clearFillCheck() {
            cancelFillCheck?.invoke()
            cancelFillCheck = null
        }

        private fun fillAfterLayout() {
            clearFillCheck()
            val view = list() ?: return
            val observer = view.viewTreeObserver
            val listener = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (closed || sessions[media] !== this@Session || !enabled()) {
                        clearFillCheck()
                        return true
                    }
                    if (!view.isShown || view.isLayoutRequested || view.height <= view.paddingTop + view.paddingBottom) return true
                    clearFillCheck()
                    // Wait for real row sizes rather than guessing a message count.
                    if (view.childCount > 0 && !view.canScrollVertically(-1) && !view.canScrollVertically(1)) {
                        runCatching { load(if (!olderEnd) 1 else -1) }.onFailure { fail(it) }
                    }
                    return true
                }
            }
            cancelFillCheck = {
                (if (observer.isAlive) observer else view.viewTreeObserver).removeOnPreDrawListener(listener)
            }
            observer.addOnPreDrawListener(listener)
        }

        fun load(direction: Int) {
            if (closed || request != 0 || !enabled() || first) return
            window.next(direction)?.let(::fetch)
        }

        fun move(direction: Int) {
            if (messages.isEmpty()) return
            selected = (readMedia(media, "lastReturnedNum") as Int).coerceIn(0, messages.lastIndex)
            val next = selected + if (direction == 2) -1 else 1
            if (next in messages.indices) { selected = next; publish(true) }
            else { navigation = if (direction == 2) -1 else 1; load(navigation) }
        }

        private fun fetch(page: SearchPage) {
            clearFillCheck()
            val peer = page.peer
            val req = requestClass.getDeclaredConstructor().newInstance()
            req.setObj("peer", inputPeer.invoke(controller, peers[peer]))
            req.setObj("q", args[0] as? String ?: "")
            val filter = if (filters.type == SearchMessageType.ALL) args.getOrNull(11) else null
            req.setObj("filter", filter ?: loader.loadClass("org.telegram.tgnet.TLRPC\$TL_inputMessagesFilter${filters.type.filter}").getDeclaredConstructor().newInstance())
            req.setObj("min_date", filters.minDate)
            req.setObj("max_date", filters.maxDate)
            req.setObj("limit", 40)
            req.setObj("offset_id", page.offset)
            req.setObj("add_offset", page.addOffset)
            var flags = 0
            if (sender != null && !localSender) {
                req.setObj("from_id", inputPeerObject.invoke(null, sender)); flags = flags or 1
            }
            val topic = args[5] as Long
            if (topic != 0L && peers[peer] == args[1]) {
                val saved = peers[peer] == (clientId.invoke(chat.call("getUserConfig")) as Long) ||
                    isMonoForum.invoke(chat.call("getMessagesStorage"), peers[peer]) == true
                if (saved) { req.setObj("saved_peer_id", inputPeer.invoke(controller, topic)); flags = flags or 4 }
                else { req.setObj("top_msg_id", topic.toInt()); flags = flags or 2 }
            }
            args[10]?.let { reaction ->
                @Suppress("UNCHECKED_CAST")
                (req.getObj("saved_reaction") as MutableList<Any>).add(reaction.javaClass.declaredMethods.single { it.parameterCount == 0 && it.returnType.name == "org.telegram.tgnet.TLRPC\$Reaction" }.apply { isAccessible = true }.invoke(reaction)!!)
                flags = flags or 8
            }
            req.setObj("flags", flags)
            request = sendRequest(chat, req) { response, failure ->
                if (!enabled() && sessions[media] === this) { sessions.remove(media); close() }
                if (!closed && sessions[media] === this && enabled()) {
                    request = 0
                    runCatching {
                        if (failure != null || response == null) {
                            notify(resultEvent, guid, 0, mask(), 0L, selected, messages.size, false)
                            first = false
                            val code = failure?.getObj("code")
                            val text = failure?.getObj("text") as? String ?: "EMPTY_RESPONSE"
                            val description = "Telegram search failed: $code $text, filter=${filters.type}, " +
                                "sender=${sender != null}, localSender=$localSender, " +
                                "offset=${page.offset}, addOffset=${page.addOffset}"
                            logE(description)
                            error(IllegalStateException(description))
                            Toast.makeText(list()?.context, "搜索加载失败：$text", Toast.LENGTH_LONG).show()
                        } else received(response, page)
                    }.onFailure { fail(it) }
                }
            }
        }

        private fun messageSender(message: Any): Long {
            val from = message.getObj("from_id")
            if (from != null) return peerId.invoke(null, from) as Long
            if (message.getObj("out") == true) return selfId
            return peerId.invoke(null, message.getObj("peer_id")) as Long
        }

        private fun received(response: Any, page: SearchPage) {
            putUsers.invoke(controller, response.getObj("users"), false)
            putChats.invoke(controller, response.getObj("chats"), false)
            val oldTop = layout()?.let { methods.getValue("first").invoke(it) as Int } ?: -1
            val oldMessage = messages.getOrNull(oldTop)
            val oldView = layout()?.let { methods.getValue("view").invoke(it, oldTop) as? View }
            val top = (oldView?.top ?: 0) - (list()?.paddingTop ?: 0)
            val selectedMessage = messages.getOrNull(selected)
            @Suppress("UNCHECKED_CAST")
            val raw = response.getObj("messages") as List<Any>
            // Keep cursors for every server result, including locally hidden media.
            val added = window.add(page, raw.map { it.getObj("id") as Int }).map { it.id }.toSet()
            val usable = raw.filter { (!localSender || messageSender(it) == senderId) &&
                (!filters.type.spoilerOnly || it.getObj("media")?.getObj("spoiler") == true) &&
                filters.dates.contains(it.getObj("date") as Int, filters.zone) &&
                it.javaClass.name != "org.telegram.tgnet.TLRPC\$TL_messageEmpty" &&
                it.getObj("action")?.javaClass?.name != "org.telegram.tgnet.TLRPC\$TL_messageActionHistoryClear" }
            val before = messages.size
            for (m in usable.distinctBy { it.getObj("id") }.filter { it.getObj("id") in added }) {
                val obj = messageConstructor.newInstance(account, m, true, true)
                stateFields.getValue("isPrimaryGroupMessage").setBoolean(obj, true)
                stateFields.getValue("isSavedFiltered").setBoolean(obj, true)
                setQuery.invoke(obj, args[0] as? String ?: "", true)
                messages.add(obj)
            }
            messages.sortWith(compareBy<Any> { peers.indexOf(dialog(it)) }.thenByDescending(::id))
            if (messages.size == before) {
                window.nextAfterEmpty(page, first)?.let { fetch(it); return }
            }
            selected = selectedMessage?.let { messages.indexOf(it) }?.coerceAtLeast(0) ?: 0
            val wasFirst = first
            if (first) {
                first = false
                selected = messages.indexOfFirst { dialog(it) == anchorDialog && id(it) == anchorId }
                if (selected < 0) selected = messages.indexOfFirst { dialog(it) == anchorDialog && id(it) <= anchorId }.coerceAtLeast(0)
                anchorTime?.let { time ->
                    selected = messages.indexOfLast { (messageOwner.get(it).getObj("date") as Int) >= time }
                    if (selected < 0) selected = 0
                }
            } else if (navigation != 0) {
                selected = (selected + navigation).coerceIn(0, messages.lastIndex.coerceAtLeast(0))
            }
            val jump = navigation != 0
            navigation = 0
            publish(jump)
            val target = if (wasFirst) selected else oldMessage?.let { messages.indexOf(it) } ?: -1
            if (target >= 0) layout()?.let { methods.getValue("position").invoke(it, target, if (wasFirst) 0 else top) }
            fillAfterLayout()
        }

        private fun publish(jump: Boolean) {
            for (name in listOf("searchResultMessages", "searchServerResultMessages")) {
                @Suppress("UNCHECKED_CAST")
                (readMedia(media, name) as MutableList<Any>).apply { clear(); addAll(messages) }
            }
            @Suppress("UNCHECKED_CAST")
            (readMedia(media, "searchLocalResultMessages") as MutableList<Any>).clear()
            writeMedia(media, "lastReturnedNum", selected)
            (readMedia(media, "messagesSearchCount") as IntArray).apply { this[0] = messages.size; this[1] = 0 }
            @Suppress("UNCHECKED_CAST")
            val maps = readMedia(media, "searchServerResultMessagesMap") as Array<Any>
            maps.forEach { it.call("clear") }
            messages.forEach { m -> maps[if (dialog(m) == peers[0]) 0 else 1].call("put", id(m), m) }
            val m = messages.getOrNull(selected)
            publishing = true
            try { notify(resultEvent, guid, m?.let(::id) ?: 0, mask(), m?.let(::dialog) ?: 0L, selected, messages.size, jump) }
            finally { publishing = false }
        }

        private fun notify(event: Int, vararg values: Any?) { stateMethods.getValue("postNotificationName").invoke(notification, event, values) }
        fun fail(t: Throwable) {
            logE("Anchored message search failed", t); error(t)
            close()
            sessions.remove(media)
            Toast.makeText(list()?.context, "搜索加载失败，请重新搜索", Toast.LENGTH_SHORT).show()
        }
    }
}
