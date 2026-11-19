package io.github.a13e300.myinjector.bili

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.findBaseActivityOrNull
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.bridge.HookParam
import io.github.a13e300.myinjector.logI
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.ref.WeakReference
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.URI

/** 视频只生成普通详情路由，图文保留卡片给出的原始详情地址。 */
internal fun biliCardTaskUrl(goTo: String?, id: String?, url: String?): String? {
    if (goTo == "av" || goTo == "vertical_av") {
        val value = id?.removePrefix("av") ?: return null
        val video = when {
            value.toLongOrNull()?.let { it > 0 } == true -> "av$value"
            Regex("BV[0-9A-Za-z]{10}").matches(value) -> value
            else -> return null
        }
        return "bilibili://video/$video"
    }
    if (goTo != "picture") return null
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    val detail = when {
        uri.scheme in listOf("bilibili", "experimental-bilibili") && uri.host in listOf("opus", "following") ->
            Regex("/detail/[1-9][0-9]*/?").matches(uri.path.orEmpty())
        uri.scheme in listOf("http", "https") && uri.host == "www.bilibili.com" ->
            Regex("/opus/[1-9][0-9]*/?").matches(uri.path.orEmpty())
        uri.scheme in listOf("http", "https") && uri.host == "t.bilibili.com" ->
            Regex("/[1-9][0-9]*/?").matches(uri.path.orEmpty())
        else -> false
    }
    return url.takeIf { detail && uri.rawUserInfo == null && uri.port == -1 }
}

internal fun biliFollowingTaskUrl(type: Int, aid: Long, dynamicId: Long): String? = when (type) {
    // DynamicType: av、story、word、draw。
    2, 22 -> biliCardTaskUrl("av", aid.toString(), null)
    6, 7 -> if (dynamicId > 0) "bilibili://opus/detail/$dynamicId?opus_type=dyn" else null
    else -> null
}

internal class BiliVideoCardTask(
    private val loader: ClassLoader,
    private val enabled: () -> Boolean,
    private val onError: (String, Throwable) -> Unit,
) {
    private val router by lazy { BiliVideoTaskRouter(loader) }
    private val function0 = loader.loadClass("kotlin.jvm.functions.Function0")
    private val function1 = loader.loadClass("kotlin.jvm.functions.Function1")
    private val invoke = function0.getMethod("invoke")
    private val unit = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
    private val fragment = loader.loadClass("androidx.fragment.app.Fragment")
    private val getActivity = fragment.getMethod("getActivity")
    private data class Target(val activity: WeakReference<Activity>, val url: String)
    private class FeedbackMenu(val target: Target, var added: Boolean = false)
    private val feedbackScope = ThreadLocal.withInitial { mutableListOf<FeedbackMenu?>() }
    private data class SearchMenu(val target: Target, var dismiss: Any? = null)

    // 配置是数据类，内容相同的两个菜单也要按对象区分，避免串到其他视频任务。
    private class Menus<T> {
        private val values = mutableListOf<Pair<WeakReference<Any>, T>>()
        @Synchronized operator fun get(key: Any): T? {
            values.removeAll { it.first.get() == null }
            return values.firstOrNull { it.first.get() === key }?.second
        }
        @Synchronized operator fun set(key: Any, value: T) {
            values.removeAll { it.first.get().let { old -> old == null || old === key } }
            values.add(WeakReference(key) to value)
        }
        @Synchronized fun remove(key: Any) {
            values.removeAll { it.first.get().let { old -> old == null || old === key } }
        }
    }

    private fun method(info: ObfsInfo) = DexMethod(info.descriptor).getMethodInstance(loader)
        .apply { isAccessible = true }
    private fun field(info: ObfsInfo) = DexField(info.descriptor).getFieldInstance(loader)
        .apply { isAccessible = true }

    fun install(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        val feedbackReady = runCatching { installFeedback(hooks) }
            .onFailure { onError("安装视频反馈菜单项", it) }.isSuccess
        if (feedbackReady) {
            runCatching { installHome(biliVideoCardTaskMembers(creator, "home"), hooks) }
                .onFailure { onError("安装首页新任务菜单", it) }
            runCatching { installRelate(biliVideoCardTaskMembers(creator, "relate"), hooks) }
                .onFailure { onError("安装底部推荐新任务菜单", it) }
        }
        runCatching { installSearch(biliVideoCardTaskMembers(creator, "search"), hooks) }
            .onFailure { onError("安装搜索新任务菜单", it) }
        runCatching { installNewRelate(biliVideoCardTaskMembers(creator, "newRelate"), hooks) }
            .onFailure { onError("安装新版底部推荐新任务菜单", it) }
        runCatching { installFollowing(biliFollowingTaskMembers(creator), hooks) }
            .onFailure { onError("安装关注信息流新任务菜单", it) }
    }

    private fun target(activity: Activity?, id: String?): Target? {
        if (activity == null || activity.isDestroyed || activity.isFinishing) return null
        val url = biliCardTaskUrl("av", id, null) ?: return null
        return Target(WeakReference(activity), url)
    }

    private fun action(target: Target, type: Class<*>, dismiss: (() -> Unit)? = null): Any =
        Proxy.newProxyInstance(loader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "invoke" -> {
                    runCatching {
                        dismiss?.invoke()
                        target.activity.get()?.let { activity ->
                            // 原生菜单先处理关闭，再发起页面跳转。
                            activity.window.decorView.post {
                                runCatching { router.open(activity, target.url) }.onFailure {
                                    onError("从卡片打开新任务", it)
                                    Toast.makeText(activity, "打开新任务失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }.onFailure { onError("关闭视频卡片菜单", it) }
                    unit
                }
                "toString" -> TITLE
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> error("Unexpected callback: $method")
            }
        }

    private fun installFeedback(hooks: MutableList<Unhook>) {
        val feedback = loader.loadClass("kntr.app.pegasus.feedbackdialog.model.Feedback")
        val style = loader.loadClass("kntr.app.pegasus.feedbackdialog.model.FeedbackStyle")
        val item = loader.loadClass("kntr.app.pegasus.feedbackdialog.model.FeedbackItem")
        val itemType = loader.loadClass("kntr.app.pegasus.feedbackdialog.model.FeedbackItemType")
        val constructor = item.constructors.single { it.parameterCount == 14 }
        val defaultType = itemType.getField("DEFAULT").get(null)
        val title = item.getMethod("getTitle")
        val icon = item.getMethod("getIcon")
        val nightIcon = item.getMethod("getIconNight")
        hooks += feedback.getConstructor(style, String::class.java, List::class.java).hookBefore { param ->
            val scope = feedbackScope.get()?.lastOrNull() ?: return@hookBefore
            if (scope.added || (param.args[0] as Enum<*>).name !in listOf("LIST", "GRID")) return@hookBefore
            runCatching {
                val items = param.args[2] as? List<*> ?: emptyList<Any>()
                if (items.any { title.invoke(it) == TITLE }) {
                    scope.added = true
                    return@runCatching
                }
                val template = items.firstOrNull()
                val entry = constructor.newInstance(-1L, template?.let { icon.invoke(it) },
                    template?.let { nightIcon.invoke(it) }, 20, 20, TITLE, null, defaultType,
                    null, 0, null, false, null, action(scope.target, function1))
                param.args[2] = items + entry
                scope.added = true
            }.onFailure { onError("添加视频反馈菜单项", it) }
        }
    }

    private fun scope(method: Method, hooks: MutableList<Unhook>, getTarget: (HookParam) -> Target?) {
        hooks += method.hook(before = { param ->
            val stack = feedbackScope.get()!!
            stack.add(null)
            if (enabled()) runCatching {
                stack[stack.lastIndex] = getTarget(param)?.let(::FeedbackMenu)
            }.onFailure { onError("读取长按视频", it) }
        }, after = {
            val stack = feedbackScope.get()!!
            stack.removeAt(stack.lastIndex)
            if (stack.isEmpty()) feedbackScope.remove()
        })
    }

    private fun installHome(members: Map<String, ObfsInfo>, hooks: MutableList<Unhook>) {
        val basic = loader.loadClass("com.bilibili.pegasus.api.model.BasicIndexItem")
        val pegasus = loader.loadClass("com.bilibili.pegasus.data.base.BasePegasusData")
        val goto = basic.getField("goTo")
        val id = basic.getField("param")
        val basicUri = basic.getMethod("getUri")
        val getGoto = pegasus.getMethod("getGoTo")
        val getId = pegasus.getMethod("getParam")
        val getUri = pegasus.getMethod("getUri")
        val holderItem = method(members.getValue("holderItem"))
        fun selected(activity: Activity?, model: Any?): Target? {
            val isBasic = basic.isInstance(model)
            if (!isBasic && !pegasus.isInstance(model)) return null
            if (activity == null || activity.isFinishing || activity.isDestroyed) return null
            val url = biliCardTaskUrl(
                (if (isBasic) goto.get(model) else getGoto.invoke(model)) as? String,
                (if (isBasic) id.get(model) else getId.invoke(model)) as? String,
                (if (isBasic) basicUri.invoke(model) else getUri.invoke(model)) as? String,
            ) ?: return null
            return Target(WeakReference(activity), url)
        }
        for (key in listOf("callback0", "callback1", "callback2", "callback3")) {
            val callback = method(members.getValue(key))
            val fields = callback.declaringClass.declaredFields.onEach { it.isAccessible = true }
            val parent = fields.single { fragment.isAssignableFrom(it.type) }
            // DislikeItemData 也实现 BasePegasusData，只选声明为卡片基类的字段。
            val data = fields.singleOrNull { it.type == basic || it.type == pegasus }
            val holder = if (data == null) fields.single { holderItem.declaringClass.isAssignableFrom(it.type) } else null
            scope(callback, hooks) { param ->
                val owner = param.thisObject!!
                val activity = parent.get(owner)?.let { getActivity.invoke(it) as? Activity }
                val model = data?.get(owner) ?: holderItem.invoke(holder!!.get(owner))
                selected(activity, model)
            }
        }

        // 首页也会直接构建本地菜单，不经过上面的 NF 网络回调。
        val newHolder = loader.loadClass("com.bilibili.pegasus.BasePegasusHolder")
        for (key in listOf("entry0", "entry1")) {
            val entry = method(members.getValue(key))
            val holderIndex = entry.parameterTypes.indexOfFirst {
                holderItem.declaringClass.isAssignableFrom(it) || newHolder.isAssignableFrom(it)
            }
            check(holderIndex >= 0) { "首页菜单缺少卡片参数: $entry" }
            val holderType = entry.parameterTypes[holderIndex]
            val readItem = if (holderItem.declaringClass.isAssignableFrom(holderType)) holderItem
                else holderType.getMethod("getBindData")
            val readFragment = holderType.getMethod("getFragment")
            scope(entry, hooks) { param ->
                val holder = param.args[holderIndex]
                val activity = readFragment.invoke(holder)?.let { getActivity.invoke(it) as? Activity }
                selected(activity, readItem.invoke(holder))
            }
        }
        val item = loader.loadClass(
            "com.bilibili.app.comm.list.common.widget.bottomsheet.drag.model.ImageTitleData")
        val constructor = item.getConstructor(String::class.java, String::class.java, String::class.java,
            Drawable::class.java, Boolean::class.javaPrimitiveType, function0)
        val title = item.getMethod("getTitle")
        hooks += method(members.getValue("dragMenu")).hookBefore { param ->
            val menu = feedbackScope.get()?.lastOrNull() ?: return@hookBefore
            runCatching {
                val items = param.args[2] as List<*>
                if (items.any { item.isInstance(it) && title.invoke(it) == TITLE }) return@runCatching
                val activity = menu.target.activity.get() ?: return@runCatching
                // 原生 url(String) 只做 Uri.parse，本地文件需要明确使用 file scheme。
                val icon = Uri.fromFile(File(biliVideoTaskIcon(activity))).toString()
                val nightIcon = Uri.fromFile(File(biliVideoTaskIcon(activity, true))).toString()
                val entry = constructor.newInstance(icon, nightIcon,
                    TITLE, null, false, action(menu.target, function0))
                // 复用原生操作行，点击后的关闭逻辑也由原生菜单执行。
                param.args[2] = listOf(entry) + items
                logI("bili home task menu inserted")
            }.onFailure { onError("添加首页本地菜单项", it) }
        }
    }

    private fun installRelate(members: Map<String, ObfsInfo>, hooks: MutableList<Unhook>) {
        val show = method(members.getValue("show"))
        val add = method(members.getValue("add"))
        val basic = field(members.getValue("basic"))
        val goto = field(members.getValue("goto"))
        val id = field(members.getValue("id"))
        val nf = field(members.getValue("nf"))
        val threePoint = field(members.getValue("threePoint"))
        fun selected(activity: Activity?, card: Any): Target? =
            if (goto.get(card) in listOf("av", "vertical_av"))
                target(activity, id.getLong(basic.get(card)).toString()) else null
        hooks += show.hookBefore { param ->
            if (!enabled()) return@hookBefore
            runCatching {
                // NF 菜单在异步响应中添加，不向闲置的旧菜单服务留下卡片回调。
                if (nf.get(param.args[3]) != null || threePoint.get(param.args[3]) == null) return@runCatching
                val target = selected(param.args[1] as? Activity, param.args[3]!!) ?: return@runCatching
                add.invoke(param.args[6], TITLE, action(target, function0))
            }.onFailure { onError("添加底部推荐菜单项", it) }
        }
        val callback = method(members.getValue("feedback"))
        val activity = callback.declaringClass.declaredFields.single { Activity::class.java.isAssignableFrom(it.type) }
            .apply { isAccessible = true }
        val card = callback.declaringClass.declaredFields.single { it.type == basic.declaringClass }
            .apply { isAccessible = true }
        scope(callback, hooks) { selected(activity.get(it.thisObject) as? Activity, card.get(it.thisObject)!!) }
    }

    private fun installFollowing(members: Map<String, ObfsInfo>, hooks: MutableList<Unhook>) {
        val show = method(members.getValue("show"))
        val click = method(members.getValue("click"))
        val card = field(members.getValue("card"))
        val type = field(members.getValue("type"))
        val video = field(members.getValue("video"))
        val aid = field(members.getValue("aid"))
        val dynamicId = method(members.getValue("dynamicId"))
        val adapterInfo = members.getValue("adapter")
        val adapter = DexMethod(adapterInfo.descriptor).getConstructorInstance(loader)
            .apply { isAccessible = true }
        val entryType = click.parameterTypes[2]
        val getType = entryType.getMethod("getType")
        val defaultType = getType.returnType.enumConstants.first()
        val function2 = loader.loadClass("kotlin.jvm.functions.Function2")
        val render = function2.getMethod("invoke", Any::class.java, Any::class.java)
        val pair = loader.loadClass("kotlin.Pair").getConstructor(Any::class.java, Any::class.java)
        val isNight = loader.loadClass("com.bilibili.lib.ui.util.MultipleThemeUtils")
            .getMethod("isNightTheme", Context::class.java)
        val entries = Menus<Target>()
        scope(show, hooks) { param ->
            val activity = (param.args[0] as? Context)?.findBaseActivityOrNull()
            if (activity == null || activity.isDestroyed || activity.isFinishing) return@scope null
            val model = param.args[1]?.let { card.get(it) } ?: return@scope null
            val videoModel = video.get(model)
            val url = biliFollowingTaskUrl(type.getInt(model), videoModel?.let { aid.getLong(it) } ?: 0L,
                dynamicId.invoke(model) as Long) ?: return@scope null
            Target(WeakReference(activity), url)
        }
        hooks += adapter.hookBefore { param ->
            val menu = feedbackScope.get()?.lastOrNull() ?: return@hookBefore
            runCatching {
                val activity = menu.target.activity.get() ?: return@runCatching
                val icon = Uri.fromFile(File(biliVideoTaskIcon(activity, isNight.invoke(null, activity) == true))).toString()
                val entry = Proxy.newProxyInstance(loader, arrayOf(entryType)) { proxy, method, args ->
                    when (method.name) {
                        "getType" -> defaultType
                        "a" -> false
                        "toString" -> TITLE
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> error("Unexpected menu method: $method")
                    }
                }
                val originalRender = param.args[3]
                val display = pair.newInstance(icon, TITLE)
                val renderer = Proxy.newProxyInstance(loader, arrayOf(function2)) { proxy, method, args ->
                    when (method.name) {
                        "invoke" -> if (args?.get(1) === entry) display
                            else render.invoke(originalRender, args?.get(0), args?.get(1))
                        "toString" -> TITLE
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> error("Unexpected menu renderer: $method")
                    }
                }
                entries[entry] = menu.target
                param.args[1] = listOf(entry) + ((param.args[1] as? List<*>) ?: emptyList<Any>())
                param.args[3] = renderer
            }.onFailure { onError("添加关注信息流菜单项", it) }
        }
        hooks += click.hookBefore { param ->
            val target = param.args[2]?.let { entries[it] } ?: return@hookBefore
            // 外层原生回调仍负责关闭面板，自定义项不执行关注、分享或举报操作。
            invoke.invoke(action(target, function0))
            param.result = null
        }
    }

    private fun installSearch(members: Map<String, ObfsInfo>, hooks: MutableList<Unhook>) {
        val show = method(members.getValue("show"))
        val panel = method(members.getValue("panel"))
        val grid = method(members.getValue("grid"))
        val section = loader.loadClass(members.getValue("section").className)
            .getConstructor(String::class.java, String::class.java, List::class.java)
        val reason = loader.loadClass(members.getValue("reason").className)
            .getConstructor(Int::class.javaPrimitiveType, String::class.java, String::class.java)
        val video = loader.loadClass("com.bilibili.search2.api.SearchVideoItem")
        val id = video.getMethod("getParam")
        val holderFragment = show.parameterTypes[4].getMethod("getFragment")
        val selected = ThreadLocal<SearchMenu?>()
        val configs = Menus<SearchMenu>()
        val entries = Menus<SearchMenu>()
        val configConstructor = panel.parameterTypes[0].getConstructor(Long::class.javaPrimitiveType,
            String::class.java, String::class.java, String::class.java, List::class.java, Boolean::class.javaPrimitiveType)
        hooks += show.hook(before = { param ->
            selected.remove()
            if (enabled()) runCatching {
                if (video.isInstance(param.args[2])) {
                    val activity = holderFragment.invoke(param.args[4])?.let { getActivity.invoke(it) as? Activity }
                    selected.set(target(activity, id.invoke(param.args[2]) as? String)?.let(::SearchMenu))
                }
            }.onFailure { onError("读取搜索视频", it) }
        }, after = { selected.remove() })
        hooks += configConstructor.hook(before = { param ->
            val menu = selected.get() ?: return@hook
            runCatching {
                val entry = reason.newInstance(-1, TITLE, "my_injector_video_task")
                val group = section.newInstance("多任务视频播放", "my_injector_video_task", listOf(entry))
                entries[entry] = menu
                param.args[4] = (param.args[4] as List<*>) + group
            }.onFailure { onError("添加搜索菜单项", it) }
        }, after = { param ->
            if (param.throwable == null) selected.get()?.let { configs[param.thisObject!!] = it }
        })
        hooks += panel.hookBefore { param ->
            configs[param.args[0]!!]?.dismiss = param.args[4]
        }
        // 自定义组沿用原生按钮布局，点击直接关闭面板，不提交原生负反馈。
        hooks += grid.hookBefore { param ->
            val entry = (param.args[3] as List<*>).singleOrNull() ?: return@hookBefore
            val menu = entries[entry] ?: return@hookBefore
            param.args[4] = action(menu.target, function1) {
                menu.dismiss?.let { invoke.invoke(it) }
            }
        }
    }

    private fun installNewRelate(members: Map<String, ObfsInfo>, hooks: MutableList<Unhook>) {
        val show = method(members.getValue("show"))
        val add = method(members.getValue("add"))
        val service = field(members.getValue("service"))
        val display = method(members.getValue("display"))
        val basic = field(members.getValue("basic"))
        val goto = method(members.getValue("goto"))
        val id = field(members.getValue("id"))
        val topActivity = loader.loadClass("com.bilibili.base.BiliContext").getMethod("topActivity")
        val cards = Menus<Target>()
        val services = Menus<Target>()
        hooks += show.hookBefore { param ->
            runCatching {
                val owner = service.get(param.args[0])!!
                val card = param.args[1]!!
                services.remove(owner)
                cards.remove(card)
                if (!enabled() || goto.invoke(card) !in listOf("av", "vertical_av")) return@runCatching
                val target = target(topActivity.invoke(null) as? Activity,
                    id.getLong(basic.get(card)).toString()) ?: return@runCatching
                cards[card] = target
                services[owner] = target
            }.onFailure { onError("读取新版推荐视频", it) }
        }
        hooks += display.hookBefore { param ->
            runCatching {
                val target = services[param.thisObject!!] ?: return@runCatching
                services.remove(param.thisObject!!)
                add.invoke(null, param.thisObject, TITLE, action(target, function0))
            }.onFailure { onError("添加新版推荐菜单项", it) }
        }

        val menuConstructor = loader.loadClass(members.getValue("nfMenu").className)
            .declaredConstructors.single { it.parameterCount == 5 }.apply { isAccessible = true }
        val cardField = field(members.getValue("nfCard"))
        val click = method(members.getValue("nfClick"))
        val response = loader.loadClass(members.getValue("response").className)
        val responseConstructor = response.declaredConstructors.single { it.parameterCount == 3 }
            .apply { isAccessible = true }
        val groups = response.declaredFields.single { it.type == List::class.java }.apply { isAccessible = true }
        val head = response.declaredFields.single { it.type == responseConstructor.parameterTypes[2] }
            .apply { isAccessible = true }
        val groupConstructor = loader.loadClass(members.getValue("group").className)
            .declaredConstructors.single { it.parameterCount == 4 }.apply { isAccessible = true }
        val itemConstructor = loader.loadClass(members.getValue("item").className)
            .declaredConstructors.single { it.parameterCount == 10 }.apply { isAccessible = true }
        val responses = Menus<Boolean>()
        val entries = Menus<Target>()
        hooks += menuConstructor.hookBefore { param ->
            runCatching {
                val original = param.args[0]!!
                if (responses[original] == true) return@runCatching
                val callback = param.args[3]!!
                if (!cardField.declaringClass.isInstance(callback)) return@runCatching
                val target = cards[cardField.get(callback)!!] ?: return@runCatching
                // 这三个序列化构造器用位图指定字段，保留默认类型和原面板标题。
                val entry = itemConstructor.newInstance(511, -1L, null, null, TITLE,
                    null, "default", null, emptyMap<String, String>(), false)
                val group = groupConstructor.newInstance(7, "list", listOf(entry), "多任务视频播放")
                val copy = responseConstructor.newInstance(3, (groups.get(original) as List<*>) + group,
                    head.get(original))
                entries[entry] = target
                responses[copy] = true
                param.args[0] = copy
            }.onFailure { onError("添加新版推荐反馈菜单项", it) }
        }
        hooks += click.hookBefore { param ->
            val target = param.args[0]?.let { entries[it] } ?: return@hookBefore
            // 外层原生回调仍负责关闭菜单，不进入反馈上报分支。
            invoke.invoke(action(target, function0))
            param.result = unit
        }
    }

    companion object { private const val TITLE = "在新任务打开" }
}
