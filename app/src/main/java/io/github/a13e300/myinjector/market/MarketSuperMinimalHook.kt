package io.github.a13e300.myinjector.market

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.RelativeLayout
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.setObjS
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.bridge.Xposed
import io.github.a13e300.myinjector.logD
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** 保留原生我的页面、搜索框和导航生命周期，仅收敛极简模式的入口。 */
internal class MarketSuperMinimalHook(
    private val enabled: () -> Boolean,
    private val failure: (Throwable) -> Unit,
) : IHook() {
    private data class PageState(val originalTabs: List<Any>, val tabs: List<Any>,
        val selected: Any?, val back: Any?, val index: Int)
    private data class SearchState(val search: WeakReference<View>, val row: WeakReference<View>,
        val rowHeight: Int, val titleHeight: Int)
    private val hooks = mutableListOf<Unhook>()
    private val pages = WeakHashMap<Any, PageState>()
    private val searches = WeakHashMap<View, SearchState>()
    private val wrappers = mutableListOf<WeakReference<Any>>()
    private val layoutListeners = WeakHashMap<View, View.OnLayoutChangeListener>()
    @Volatile private var closed = false
    @Volatile private var failed = false
    private lateinit var elder: Class<*>
    private lateinit var pageType: Class<*>
    private lateinit var tabTags: Class<*>
    private lateinit var searchType: Class<*>

    override fun onHook() {
        elder = findClass("com.xiaomi.market.util.ElderChecker")
        Xposed.findField(elder, "INSTANCE")
        Xposed.findMethodExact(elder, "isElderMode", emptyArray())
        pageType = findClass("com.xiaomi.market.model.PageConfig")
        tabTags = findClass("com.xiaomi.market.model.TabTag")
        Xposed.findMethodExact(tabTags, "isNativeMineTab", arrayOf(String::class.java))
        searchType = findClass("com.xiaomi.market.widget.MainSearchContainer")
        val main = findClass("com.xiaomi.market.business_ui.main.MarketTabActivity")
        val wrapper = findClass("com.xiaomi.market.ui.DoubleTabProxyActivityWrapper")
        val title = findClass("com.xiaomi.market.business_ui.main.mine.view.MineTitleBar")
        val mine = findClass("com.xiaomi.market.business_ui.main.mine.v2.NativeMinePagerV2Fragment")
        listOf("tabs", "defaultSelectedPageTag", "defaultBackToPageTag", "defaultSelectedBottomTabIndex")
            .forEach { Xposed.findField(pageType, it) }
        listOf("mPageConfig", "mActivity", "mRequestedTab", "mRequestedSubTab", "mContainer", "bottomTabLayout")
            .forEach { Xposed.findField(wrapper, it) }
        Xposed.findMethodExact(findClass("com.xiaomi.market.widget.MainSearchView"), "initPageRef", arrayOf(String::class.java))
        Xposed.findMethodExact(searchType, "getSearchBarHeight", emptyArray())
        val getPage = Xposed.findMethodExact(main, "getPageConfig", emptyArray())
        val updateTabs = Xposed.findMethodExact(pageType, "updateTabs", arrayOf(Boolean::class.javaPrimitiveType!!))
        val onCreate = Xposed.findMethodExact(wrapper, "onCreate", arrayOf(Bundle::class.java))
        val finishTitle = Xposed.findMethodExact(title, "onFinishInflate", emptyArray())
        val titleHeight = Xposed.findMethodExact(title, "getMineTitleBarHeight", emptyArray())
        val padding = Xposed.findMethodExact(mine, "initBottomPadding", emptyArray())
        val mineCreated = Xposed.findMethodExact(mine, "onViewCreated", arrayOf(View::class.java, Bundle::class.java))
        try {
            hooks += updateTabs.hookAfter { param -> if (param.throwable == null) safely {
                // PageConfig 也用于其他页面；只跟踪已经由主 Activity 认领的实例。
                if (isPrepared(param.thisObject)) preparePage(param.thisObject)
            } }
            // 缓存中的 PageConfig 不一定重新解析；在主页创建包装器前也处理一次。
            hooks += getPage.hookAfter { param -> if (param.throwable == null) safely { param.result?.let(::preparePage) } }
            hooks += onCreate.hook(
                before = { param -> safely {
                    if (!main.isInstance(param.thisObject.getObj("mActivity"))) return@safely
                    val page = param.thisObject.getObj("mPageConfig") ?: return@safely
                    preparePage(page)
                    if (pages.containsKey(page)) {
                        param.thisObject.setObj("mRequestedTab", 0)
                        param.thisObject.setObj("mRequestedSubTab", -1)
                        // 不修改框架保存的 Bundle；仅纠正旧双页状态的索引。
                        (param.args[0] as? Bundle)?.let { original ->
                            param.args[0] = Bundle(original).apply {
                                putInt("selected_tab_index", 0)
                                putInt("selected_sub_tab_index", -1)
                                putInt("tabCount", 1)
                            }
                        }
                    }
                } },
                after = { param -> if (param.throwable == null) safely {
                    if (main.isInstance(param.thisObject.getObj("mActivity")) &&
                        isPrepared(param.thisObject.getObj("mPageConfig"))) hideBottom(param.thisObject)
                } },
            )
            hooks += titleHeight.hookAfter { param -> if (param.throwable == null) safely {
                if (active()) param.result = maxOf(param.result as Int, searchType.callS("getSearchBarHeight") as Int)
            } }
            hooks += finishTitle.hookAfter { param -> if (param.throwable == null) safely { if (active()) addSearch(param.thisObject as ViewGroup) } }
            hooks += padding.hookAfter { param -> if (param.throwable == null) safely {
                if (!active()) return@safely
                val header = param.thisObject.call("getExpandCollapseHeaderLayout") as View
                header.setPadding(header.paddingLeft, header.paddingTop, header.paddingRight,
                    (22 * header.resources.displayMetrics.density).toInt())
            } }
            hooks += mineCreated.hookAfter { param -> if (param.throwable == null) safely {
                if (!active()) return@safely
                val root = param.args[0] as View
                (root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                    params.bottomMargin = 0
                    root.layoutParams = params
                }
            } }
            logD("market super minimal hooks installed")
        } catch (error: Throwable) {
            hooks.asReversed().forEach { it.unhook() }
            hooks.clear()
            throw error
        }
    }

    private fun active() = !closed && !failed && enabled() && elder.getObjS("INSTANCE").call("isElderMode") == true

    private fun isPrepared(page: Any?) = synchronized(this) { pages.containsKey(page) }

    @Synchronized private fun preparePage(page: Any) {
        val current = page.getObjAs<List<Any>>("tabs")
        val saved = pages[page]
        if (saved != null && current === saved.tabs) {
            if (!active()) restorePage(page, saved)
            return
        }
        pages.remove(page)
        if (!active()) return
        val mine = current.singleOrNull { tabTags.callS("isNativeMineTab", it.call("getTag")) == true }
            ?: error("native mine tab missing")
        val mineTag = mine.call("getTag") as String
        val clone = mine.call("deepClone")!!
        clone.setObj("defaultSelected", true)
        val tabs = arrayListOf(clone)
        pages[page] = PageState(current, tabs, page.getObj("defaultSelectedPageTag"),
            page.getObj("defaultBackToPageTag"), pageType.getObjS("defaultSelectedBottomTabIndex") as Int)
        page.setObj("tabs", tabs)
        page.setObj("defaultSelectedPageTag", mineTag)
        page.setObj("defaultBackToPageTag", mineTag)
        pageType.setObjS("defaultSelectedBottomTabIndex", 0)
    }

    @Synchronized private fun restorePage(page: Any, state: PageState) {
        if (page.getObj("tabs") === state.tabs) {
            page.setObj("tabs", state.originalTabs)
            page.setObj("defaultSelectedPageTag", state.selected)
            page.setObj("defaultBackToPageTag", state.back)
            pageType.setObjS("defaultSelectedBottomTabIndex", state.index)
            page.call("initDefaultSelectedTab")
            page.call("initDefaultBackPageTag")
        }
        pages.remove(page)
    }

    @SuppressLint("DiscouragedApi")
    private fun id(view: View, name: String) = view.resources.getIdentifier(name, "id", "com.xiaomi.market")

    private fun hideBottom(wrapper: Any) {
        val root = wrapper.call("getRootView") as View
        if (layoutListeners.containsKey(root)) return
        val owner = WeakReference(wrapper)
        val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val target = owner.get() ?: return@OnLayoutChangeListener
            safely { applyBottom(target, view) }
        }
        layoutListeners[root] = listener
        wrappers += owner
        root.addOnLayoutChangeListener(listener)
        applyBottom(wrapper, root)
    }

    private fun applyBottom(wrapper: Any, root: View) {
        if (closed || failed) return
        root.findViewById<View>(id(root, "tab_container_layout"))?.visibility = View.GONE
        (wrapper.getObj("bottomTabLayout") as View).visibility = View.GONE
        val container = wrapper.getObj("mContainer") as View
        val params = container.layoutParams as ViewGroup.MarginLayoutParams
        val bottom = root.rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
        if (params.bottomMargin != bottom) { params.bottomMargin = bottom; container.layoutParams = params }
    }

    @SuppressLint("DiscouragedApi")
    private fun addSearch(title: ViewGroup) {
        if (searches.containsKey(title)) return
        val settings = title.findViewById<View>(id(title, "setting_layout")) ?: error("mine settings entry missing")
        val row = settings.parent as RelativeLayout
        val layout = title.resources.getIdentifier("main_search_container", "layout", "com.xiaomi.market")
        check(layout != 0) { "native search layout missing" }
        // 完整 inflate 以执行原生 ViewStub、主题和搜索监听初始化，再只移入搜索控件。
        val temporary = LayoutInflater.from(title.context).inflate(layout, title, false) as ViewGroup
        val search = temporary.findViewById<View>(id(title, "search_view")) ?: error("native search view missing")
        (search.parent as ViewGroup).removeView(search)
        search.call("initPageRef", "mine")
        val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START)
            addRule(RelativeLayout.START_OF, settings.id)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        searches[title] = SearchState(WeakReference(search), WeakReference(row), row.layoutParams.height, title.layoutParams.height)
        row.layoutParams.height = (searchType.callS("getSearchViewHeight") as Int).coerceAtLeast(row.layoutParams.height)
        title.layoutParams.height = title.call("getMineTitleBarHeight") as Int
        row.addView(search, 0, params)
        // 隐藏底栏后，极简布局自己的底部留白也应随之去掉。
        ((title.parent as? View)?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = 0
    }

    private inline fun safely(action: () -> Unit) {
        if (closed || failed) return
        try { action() } catch (error: Throwable) { failed = true; close(); failure(error) }
    }

    override fun onHookError(t: Throwable) { failure(t) }

    fun close() {
        closed = true
        hooks.asReversed().forEach { it.unhook() }
        hooks.clear()
        val cleanup = Runnable { runCatching {
            layoutListeners.forEach { (view, listener) -> view.removeOnLayoutChangeListener(listener) }
            layoutListeners.clear()
            synchronized(this) { pages.entries.map { it.key to it.value } }.forEach { (page, state) -> restorePage(page, state) }
            searches.forEach { (title, state) ->
                state.search.get()?.let { (it.parent as? ViewGroup)?.removeView(it) }
                state.row.get()?.let { it.layoutParams.height = state.rowHeight }
                title.layoutParams.height = state.titleHeight
            }
            searches.clear()
            wrappers.mapNotNull { it.get()?.getObj("mActivity") as? android.app.Activity }.distinct().forEach {
                if (!it.isFinishing && !it.isDestroyed) it.recreate()
            }
            wrappers.clear()
        }.onFailure(failure) }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run() else Handler(Looper.getMainLooper()).post(cleanup)
    }
}
