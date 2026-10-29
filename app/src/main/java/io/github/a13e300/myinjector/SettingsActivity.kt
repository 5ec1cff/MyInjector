package io.github.a13e300.myinjector

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.a13e300.myinjector.ui.InjectorTheme
import io.github.a13e300.myinjector.ui.settings.AppSettingsDestination
import io.github.a13e300.myinjector.ui.settings.SettingsApp
import io.github.a13e300.myinjector.ui.settings.SettingsItemSpec
import io.github.a13e300.myinjector.scope.ScopeApplication
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.a13e300.myinjector.ui.showModernInjectedDialog
import io.github.a13e300.myinjector.ui.ModernInjectedDialogAction
import io.github.a13e300.myinjector.ui.ModernActionButton
import io.github.a13e300.myinjector.ui.ModernInjectedSearchBar
import io.github.a13e300.myinjector.ui.ModernSettingsPalette
import io.github.a13e300.myinjector.ui.dp
import io.github.a13e300.myinjector.ui.setTextSizeDp
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.math.min

class SettingsActivity : androidx.activity.ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var palette: ModernSettingsPalette
    private var scopeState by mutableStateOf(io.github.a13e300.myinjector.scope.ScopeManager.State())
    private val scopeListener: (io.github.a13e300.myinjector.scope.ScopeManager.State) -> Unit = { scopeState = it }
    private var appListVersion by mutableStateOf(0)
    private var syncStatus by mutableStateOf("配置等待同步")
    private val syncListener: (String) -> Unit = { syncStatus = it }
    private var serviceConnected by mutableStateOf(false)
    private var xposedServiceListener: App.ServiceStateListener? = null
    private val launcherAlias by lazy { ComponentName(this, "$packageName.LauncherAlias") }
    private var launcherIconVisible by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshLauncherIconVisibility()
        palette = ModernSettingsPalette.from(this)
        prefs = App.settings.cache
        App.settings.addListener(syncListener)
        App.scopes.addListener(scopeListener)
        enableEdgeToEdge()
        xposedServiceListener = { service ->
            runOnUiThread { serviceConnected = service != null }
        }
        App.addServiceStateListener(listener = xposedServiceListener!!)
        setContent {
            InjectorTheme {
                SettingsApp(
                    prefs = prefs,
                    serviceConnected = serviceConnected,
                    launcherIconVisible = launcherIconVisible,
                    onLauncherIconToggle = ::changeLauncherIconVisibility,
                    syncStatus = syncStatus,
                    scopeState = scopeState,
                    onScopeToggle = App.scopes::setEnabled,
                    appListVersion = appListVersion,
                    loadApps = ::loadAppDestinations,
                    openApp = ::openAppSettings,
                    onToggle = { item, checked ->
                        App.settings.putBoolean(item.key, checked)
                    },
                    onEdit = { item ->
                        if (item.key == "clipboardWhitelistPackages") showClipboardWhitelistDialog(item)
                        else showTextEditor(item)
                    },
                    onAction = ::handleAction,
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onDestroy() {
        App.scopes.removeListener(scopeListener)
        App.settings.removeListener(syncListener)
        xposedServiceListener?.let(App::removeServiceStateListener)
        super.onDestroy()
    }

    private fun showTextEditor(item: SettingsItemSpec.Text) {
        val entries = prefs.getString(item.key, "").orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toMutableList()
        val entriesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        lateinit var entriesScroll: ScrollView

        fun entryListHeight(): Int = dp(190)

        fun updateEntryListHeight() {
            val params = entriesScroll.layoutParams as? LinearLayout.LayoutParams ?: return
            val height = entryListHeight()
            if (params.height == height) return
            params.height = height
            entriesScroll.layoutParams = params
        }

        fun refreshEntries() {
            entriesContainer.removeAllViews()
            if (entries.isEmpty()) {
                entriesContainer.addView(
                    TextView(this).apply {
                        text = "暂无条目"
                        setTextColor(palette.summary)
                        setTextSizeDp(13.2f)
                        includeFontPadding = true
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        entryListHeight(),
                    ),
                )
                updateEntryListHeight()
                return
            }

            entries.forEachIndexed { index, value ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(45)
                    setPadding(0, dp(4), 0, dp(4))
                }
                row.addView(
                    TextView(this).apply {
                        text = value
                        setTextColor(palette.title)
                        setTextSizeDp(14.0f)
                        includeFontPadding = true
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                row.addView(
                    TextView(this).apply {
                        text = "×"
                        setTextColor(palette.summary)
                        setTextSizeDp(24f)
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            entries.removeAt(index)
                            refreshEntries()
                        }
                    },
                    LinearLayout.LayoutParams(dp(42), dp(42)),
                )
                entriesContainer.addView(row, matchWidthLayoutParams())

                if (index < entries.lastIndex) {
                    entriesContainer.addView(
                        View(this).apply { setBackgroundColor(palette.divider) },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(0.6f),
                        ),
                    )
                }
            }
            updateEntryListHeight()
        }

        val input = EditText(this).apply {
            hint = "请输入条目"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(palette.title)
            setHintTextColor(palette.summary)
            setTextSizeDp(13.2f)
            background = roundedBackground(
                if (palette.isLight) Color.rgb(244, 247, 251) else palette.button,
                22,
            )
            setPadding(dp(16), 0, dp(16), 0)
        }

        fun addInputEntries() {
            val newEntries = input.text.toString()
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (newEntries.isEmpty()) return
            var changed = false
            var duplicateFound = false
            for (entry in newEntries) {
                if (!entries.contains(entry)) {
                    entries.add(entry)
                    changed = true
                } else {
                    duplicateFound = true
                }
            }
            if (duplicateFound) {
                Toast.makeText(this, "条目已存在", Toast.LENGTH_SHORT).show()
            }
            if (!changed) return
            input.text.clear()
            refreshEntries()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            item.helpText?.takeUnless { it.isBlank() }?.let { helpText ->
                addView(textEditorHelpCard(helpText), matchWidthLayoutParams(bottom = 12))
            }
            addView(
                TextView(this@SettingsActivity).apply {
                    text = "已添加的条目："
                    setTextColor(palette.title)
                    setTextSizeDp(14.0f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    includeFontPadding = true
                },
                matchWidthLayoutParams(bottom = 8),
            )
            entriesScroll = ScrollView(this@SettingsActivity).apply {
                isFillViewport = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(
                    entriesContainer,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            addView(
                entriesScroll,
                matchWidthLayoutParams(bottom = 14).apply {
                    height = entryListHeight()
                },
            )
            addView(
                LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        input,
                        LinearLayout.LayoutParams(0, dp(46), 1f),
                    )
                    addView(
                        modernDialogButton("+") { addInputEntries() }.apply {
                            contentDescription = "添加"
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            setTextSizeDp(27f)
                            includeFontPadding = false
                        },
                        LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                            marginStart = dp(10)
                        },
                    )
                },
                matchWidthLayoutParams(),
            )
        }
        refreshEntries()

        showModernDialog(
            title = item.title,
            content = container,
            actions = listOf(
                ModernDialogAction("取消"),
                ModernDialogAction("保存", emphasized = true) {
                    App.settings.putString(item.key, entries.joinToString("\n"))
                },
            ),
        )
    }

    private fun showClipboardWhitelistDialog(item: SettingsItemSpec.Text) {
        val selectedPackages = prefs.getString(item.key, "").orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
        val initiallySelectedPackages = selectedPackages.toSet()
        var entries = emptyList<ClipboardAppEntry>()
        val countText = TextView(this).apply {
            setTextColor(palette.summary)
            setTextSizeDp(13.2f)
            gravity = Gravity.CENTER
            includeFontPadding = true
            background = roundedBackground(
                if (palette.isLight) Color.rgb(244, 247, 251) else palette.button,
                16,
            )
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }

        fun updateCount() {
            countText.text = "已选择 ${selectedPackages.size} 个应用"
        }

        lateinit var emptyView: TextView
        lateinit var loadingView: LinearLayout
        val adapter = ClipboardAppAdapter(
            selectedPackages,
            initiallySelectedPackages,
            ::updateCount,
        ) { isEmpty ->
            emptyView.visibility = if (isEmpty && loadingView.visibility != View.VISIBLE) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        val listView = ListView(this).apply {
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = 0
            selector = ColorDrawable(Color.TRANSPARENT)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            cacheColorHint = Color.TRANSPARENT
            background = null
            this.adapter = adapter
        }
        fun loadVisibleIcons() {
            if (listView.visibility != View.VISIBLE || listView.childCount == 0) return
            val firstPosition = listView.firstVisiblePosition
            val lastPosition = listView.lastVisiblePosition
            for (position in firstPosition..lastPosition) {
                val entry = adapter.visibleEntryAt(position) ?: continue
                ensureClipboardIcon(entry) { icon ->
                    val childIndex = position - listView.firstVisiblePosition
                    val row = listView.getChildAt(childIndex) as? LinearLayout ?: return@ensureClipboardIcon
                    val holder = row.tag as? ClipboardAppRowHolder ?: return@ensureClipboardIcon
                    if (holder.packageName == entry.packageName) {
                        holder.icon.setImageDrawable(icon.constantState?.newDrawable()?.mutate() ?: icon)
                    }
                }
            }
        }
        loadingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                ProgressBar(this@SettingsActivity).apply {
                    isIndeterminate = true
                },
                LinearLayout.LayoutParams(dp(42), dp(42)),
            )
            addView(
                TextView(this@SettingsActivity).apply {
                    text = "正在读取应用列表..."
                    setTextColor(palette.summary)
                    setTextSizeDp(13.2f)
                    gravity = Gravity.CENTER
                    includeFontPadding = true
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(10)
                },
            )
        }
        emptyView = TextView(this).apply {
            text = "找不到呢..."
            setTextColor(palette.summary)
            setTextSizeDp(13.2f)
            gravity = Gravity.CENTER
            includeFontPadding = true
            visibility = View.GONE
        }
        val listFrame = FrameLayout(this).apply {
            background = roundedBackground(
                if (palette.isLight) Color.rgb(244, 247, 251) else palette.button,
                22,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                clipToOutline = true
            }
        }
        val searchBar = ModernInjectedSearchBar(this, palette)
        val searchInput = searchBar.editText
        fun clearSearchFocus() {
            if (!searchInput.hasFocus()) return
            searchInput.clearFocus()
            getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(searchInput.windowToken, 0)
        }
        fun applySearch() {
            adapter.query = searchInput.text.toString().trim()
            listView.post { loadVisibleIcons() }
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearch()
            }
        })
        searchBar.setOnClickListener {
            searchInput.requestFocus()
            getSystemService(InputMethodManager::class.java)?.showSoftInput(searchInput, 0)
        }
        listView.setOnTouchListener { _, _ ->
            clearSearchFocus()
            false
        }
        listView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    loadVisibleIcons()
                }
            }

            override fun onScroll(
                view: AbsListView?,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int,
            ) = Unit
        })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(this@SettingsActivity).apply {
                    gravity = Gravity.CENTER
                    addView(
                        countText,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
                matchWidthLayoutParams(bottom = 12),
            )
            addView(
                searchBar,
                matchWidthLayoutParams(bottom = 6),
            )
            addView(
                listFrame.apply {
                    addView(
                        listView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    addView(
                        emptyView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    addView(
                        loadingView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    min(dp(500), (resources.displayMetrics.heightPixels * 0.50f).toInt()),
                ),
            )
        }
        updateCount()

        if (clipboardAppEntryCache == null) {
            listView.visibility = View.INVISIBLE
            loadingView.visibility = View.VISIBLE
            Thread {
                val loadedEntries = clipboardAppEntries(selectedPackages)
                Handler(Looper.getMainLooper()).post {
                    entries = loadedEntries
                    adapter.setEntries(loadedEntries)
                    applySearch()
                    loadingView.visibility = View.GONE
                    listView.visibility = View.VISIBLE
                    adapter.refreshEmptyState()
                    listView.post { loadVisibleIcons() }
                }
            }.start()
        } else {
            entries = clipboardAppEntries(selectedPackages)
            adapter.setEntries(entries)
            loadingView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            adapter.refreshEmptyState()
            listView.post { loadVisibleIcons() }
        }

        showModernDialog(
            title = item.title,
            content = content,
            actions = listOf(
                ModernDialogAction("取消"),
                ModernDialogAction("保存", emphasized = true) {
                    App.settings.putString(item.key, sortedClipboardPackages(entries, selectedPackages))
                },
            ),
        )
    }

    private fun clipboardAppEntries(selectedPackages: Set<String>): List<ClipboardAppEntry> {
        val cached = clipboardAppEntryCache ?: loadClipboardAppEntries().also {
            clipboardAppEntryCache = it
        }
        val knownPackages = cached.asSequence().map { it.packageName }.toSet()
        val missingSelected = selectedPackages.asSequence()
            .filterNot { it in knownPackages }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .map {
                ClipboardAppEntry(
                    label = it,
                    packageName = it,
                    canLoadIcon = false,
                )
            }
            .toList()
        return cached + missingSelected
    }

    @Suppress("DEPRECATION")
    private fun loadClipboardAppEntries(): List<ClipboardAppEntry> {
        val pm = packageManager
        val applications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }
        return applications.asSequence()
            .filter { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0 }
            .mapNotNull { appInfo ->
                runCatching {
                    val label = appInfo.loadLabel(pm).toString().ifBlank { appInfo.packageName }
                    ClipboardAppEntry(
                        label = label,
                        packageName = appInfo.packageName,
                        canLoadIcon = true,
                    )
                }.getOrNull()
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label.toString() })
            .toList()
    }

    private fun sortedClipboardPackages(
        entries: List<ClipboardAppEntry>,
        selectedPackages: Set<String>,
    ): String {
        val labels = entries.associate { it.packageName to it.label.toString() }
        return selectedPackages
            .sortedWith(
                compareBy<String> { labels[it] == null }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { labels[it] ?: it }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it },
            )
            .joinToString("\n")
    }

    private fun textEditorHelpCard(helpText: CharSequence): TextView =
        TextView(this).apply {
            text = helpText
            setTextColor(palette.summary)
            setTextSizeDp(13.2f)
            setLineSpacing(dp(2).toFloat(), 1.0f)
            includeFontPadding = true
            setTextIsSelectable(true)
            background = roundedBackground(
                if (palette.isLight) Color.rgb(244, 247, 251) else palette.button,
                18,
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

    private fun handleAction(item: SettingsItemSpec.Action) {
        when (item.key) {
            "checkHotUpdate" -> showHotUpdateDialog()
            else -> item.action?.invoke()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLauncherIconVisibility()
        App.scopes.refresh()
        appListVersion++
    }

    /** 组件状态是唯一数据源；DEFAULT 按 manifest 的声明解析，不另外保存开关偏好。 */
    private fun refreshLauncherIconVisibility() {
        launcherIconVisible = when (packageManager.getComponentEnabledSetting(launcherAlias)) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                packageManager.getActivityInfo(launcherAlias, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> false
        }
    }

    private fun changeLauncherIconVisibility(visible: Boolean) {
        runCatching {
            packageManager.setComponentEnabledSetting(launcherAlias,
                if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
        }.onFailure {
            logE("failed to change launcher icon visibility:", it)
            Toast.makeText(this, "无法修改桌面图标状态：${it.message}", Toast.LENGTH_LONG).show()
        }
        refreshLauncherIconVisibility()
    }

    private fun loadAppDestinations(): List<AppSettingsDestination> {
        val catalog = io.github.a13e300.myinjector.scope.ScopeCatalog
        val packages = checkNotNull(javaClass.classLoader!!.getResourceAsStream("META-INF/xposed/scope.list"))
            .bufferedReader().use { catalog.applications(it.lineSequence()) }
        return packages.mapNotNull { pkg ->
            try {
                val info = packageManager.getApplicationInfo(catalog.applicationPackage(pkg), 0)
                if (info.flags and ApplicationInfo.FLAG_INSTALLED == 0) return@mapNotNull null
                AppSettingsDestination(
                    ScopeApplication(pkg,
                        if (pkg == "system") "系统框架" else info.loadLabel(packageManager).toString(),
                        catalog.isSystemApp(info.flags)),
                    info.loadIcon(packageManager).toBitmap(96, 96).asImageBitmap(),
                )
            } catch (_: PackageManager.NameNotFoundException) { null }
        }.sortedWith(compareBy(catalog.applicationOrder) { it.app })
    }

    private fun openAppSettings(packageName: String) {
        runCatching {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: error("找不到应用的启动入口")
            intent.categories?.toList()?.forEach(intent::removeCategory)
            intent.action = "io.github.a13e300.myinjector.SHOW_SETTINGS"
            intent.addCategory("io.github.a13e300.myinjector.SHOW_SETTINGS")
            intent.addCategory(UUID.randomUUID().toString())
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }.onFailure {
            logE("failed to open settings for $packageName:", it)
            Toast.makeText(this, "无法打开应用设置：${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetTextI18n", "XposedNewApi")
    private fun showHotUpdateDialog() {
        val selectedPids = mutableSetOf<Int>()

        lateinit var emptyView: TextView
        lateinit var loadingView: LinearLayout
        val adapter = HotReloadAdapter(
            selectedPids,
            {
                // TODO
            },
        ) { isEmpty ->
            emptyView.visibility = if (isEmpty && loadingView.visibility != View.VISIBLE) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        val listView = ListView(this).apply {
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = 0
            selector = ColorDrawable(Color.TRANSPARENT)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            cacheColorHint = Color.TRANSPARENT
            background = null
            this.adapter = adapter
        }
        loadingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                ProgressBar(this@SettingsActivity).apply {
                    isIndeterminate = true
                },
                LinearLayout.LayoutParams(dp(42), dp(42)),
            )
            addView(
                TextView(this@SettingsActivity).apply {
                    text = "正在获取可更新列表..."
                    setTextColor(palette.summary)
                    setTextSizeDp(13.2f)
                    gravity = Gravity.CENTER
                    includeFontPadding = true
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(10)
                },
            )
        }
        emptyView = TextView(this).apply {
            text = "无"
            setTextColor(palette.summary)
            setTextSizeDp(13.2f)
            gravity = Gravity.CENTER
            includeFontPadding = true
            visibility = View.GONE
        }
        val listFrame = FrameLayout(this).apply {
            background = roundedBackground(
                if (palette.isLight) Color.rgb(244, 247, 251) else palette.button,
                22,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                clipToOutline = true
            }
        }
        val searchBar = ModernInjectedSearchBar(this, palette)
        val searchInput = searchBar.editText
        fun clearSearchFocus() {
            if (!searchInput.hasFocus()) return
            searchInput.clearFocus()
            getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(searchInput.windowToken, 0)
        }

        fun applySearch() {
            adapter.query = searchInput.text.toString().trim()
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearch()
            }
        })
        searchBar.setOnClickListener {
            searchInput.requestFocus()
            getSystemService(InputMethodManager::class.java)?.showSoftInput(searchInput, 0)
        }
        listView.setOnTouchListener { _, _ ->
            clearSearchFocus()
            false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                searchBar,
                matchWidthLayoutParams(bottom = 6),
            )
            addView(
                listFrame.apply {
                    addView(
                        listView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    addView(
                        emptyView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    addView(
                        loadingView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    min(dp(500), (resources.displayMetrics.heightPixels * 0.50f).toInt()),
                ),
            )
        }

        var entries: MutableList<HotReloadEntry>? = null

        fun updateList() {
            listView.visibility = View.INVISIBLE
            loadingView.visibility = View.VISIBLE
            thread {
                val svc = App.mService
                val targets = svc?.runningTargets
                runOnUiThread {
                    if (targets != null) {
                        val entryList = targets.map { HotReloadEntry(it) }.toMutableList()
                        entries = entryList
                        adapter.setEntries(entryList)
                        applySearch()
                        loadingView.visibility = View.GONE
                        listView.visibility = View.VISIBLE
                        adapter.refreshEmptyState()
                    }
                }
            }
        }

        updateList()

        fun doUpdate(updateAll: Boolean): Boolean {
            val updateTargets = if (updateAll) entries else entries?.filter {
                it.target.pid in selectedPids
            }

            if (updateTargets.isNullOrEmpty()) return updateAll

            val statusText = TextView(this).apply {
                text = ""
                setTextColor(palette.summary)
                setTextSizeDp(13.2f)
                includeFontPadding = true
                gravity = Gravity.CENTER
                background = roundedBackground(
                    if (palette.isLight) Color.rgb(244, 247, 251) else palette.button,
                    16,
                )
                setPadding(dp(14), dp(6), dp(14), dp(6))
            }

            fun update() {
                thread {
                    var i = 1
                    var success = 0
                    val size = updateTargets.size
                    for (ut in updateTargets) {
                        val t = ut.target
                        val countDownLatch = CountDownLatch(1)
                        val callback = XposedService.HotReloadCallback { target, result ->
                            countDownLatch.countDown()
                            if (result.status == HotReloadResult.Status.SUCCEEDED) success += 1
                            runOnUiThread {
                                statusText.append(
                                    SpannableString("更新结果 status=${result.status} message=${result.message}\n").apply {
                                        setSpan(
                                            ForegroundColorSpan(
                                                if (result.status == HotReloadResult.Status.SUCCEEDED)
                                                    0xff067d17.toInt() else Color.RED
                                            ), 0, this.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                )
                            }
                        }
                        runOnUiThread {
                            statusText.append("正在更新 [$i/$size] proc=${t.processName} pid=${t.pid} uid=${t.uid}\n")
                        }
                        try {
                            App.mService?.hotReloadModule(t, null, callback)
                            countDownLatch.await()
                        } catch (e: Throwable) {
                            logE("update pid=${t.pid} proc=${t.processName}", e)
                            runOnUiThread {
                                statusText.append(SpannableString("更新失败 ${e.message}\n").apply {
                                    setSpan(
                                        ForegroundColorSpan(Color.RED),
                                        0,
                                        this.length,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                })
                            }
                        }
                        i += 1
                    }
                    runOnUiThread {
                        statusText.append("更新完成 成功 $success 失败 ${size - success}\n")
                    }
                }
            }

            val content = LinearLayout(this).apply {
                addView(
                    ScrollView(this@SettingsActivity).apply {
                        gravity = Gravity.CENTER
                        addView(
                            statusText,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        min(dp(500), (resources.displayMetrics.heightPixels * 0.50f).toInt()),
                    ),
                )
            }

            update()

            showModernDialog(
                title = "更新进度",
                content = content,
                actions = listOf(ModernDialogAction("复制", emphasized = true) {
                    val text = statusText.text
                    getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(
                            ClipData.newPlainText("", text)
                        )
                }),
            )

            return true
        }

        showModernDialog(
            title = "热更新",
            content = content,
            actions = listOf(
                ModernDialogAction("取消"),
                ModernDialogAction("更新", emphasized = true, onClickWithRes = {
                    doUpdate(false)
                }),
                ModernDialogAction("全部更新", emphasized = true, onClickWithRes = {
                    doUpdate(true)
                })
            ),
        )
    }

    private fun showModernDialog(
        title: CharSequence,
        content: View,
        actions: List<ModernDialogAction>,
    ) {
        showModernInjectedDialog(
            this, title, content,
            actions.map { action ->
                ModernInjectedDialogAction(
                    action.title,
                    emphasized = action.emphasized,
                    onClickWithResult = action.onClickWithRes,
                    onClick = action.onClick,
                )
            },
        )
    }

    private fun modernDialogButton(
        title: CharSequence,
        emphasized: Boolean = false,
        onClick: () -> Unit,
    ): ModernActionButton =
        ModernActionButton(this, palette, title, emphasized).apply {
            setOnClickListener { onClick() }
        }

    private fun matchWidthLayoutParams(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = dp(bottom)
        }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
        }

    private fun selectableItemBackground(): Drawable? {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getDrawable(outValue.resourceId)
        } else {
            @Suppress("DEPRECATION")
            resources.getDrawable(outValue.resourceId)
        }
    }

    private fun clipboardListDividerColor(): Int =
        if (palette.isLight) palette.divider else Color.rgb(78, 78, 78)

    private fun cachedClipboardIcon(entry: ClipboardAppEntry): Drawable {
        val icon = if (entry.canLoadIcon) {
            clipboardIconCache[entry.packageName] ?: packageManager.defaultActivityIcon
        } else {
            packageManager.defaultActivityIcon
        }
        return icon.constantState?.newDrawable()?.mutate() ?: icon
    }

    private fun ensureClipboardIcon(entry: ClipboardAppEntry, onLoaded: (Drawable) -> Unit) {
        if (!entry.canLoadIcon) return
        clipboardIconCache[entry.packageName]?.let {
            onLoaded(it)
            return
        }
        if (!loadingClipboardIcons.add(entry.packageName)) return
        Thread {
            val icon = runCatching {
                packageManager.getApplicationInfo(entry.packageName, 0).loadIcon(packageManager)
            }.getOrElse {
                packageManager.defaultActivityIcon
            }
            clipboardIconCache[entry.packageName] = icon
            loadingClipboardIcons.remove(entry.packageName)
            Handler(Looper.getMainLooper()).post {
                onLoaded(icon)
            }
        }.start()
    }

    private inner class ClipboardAppAdapter(
        private val selectedPackages: MutableSet<String>,
        private val pinnedPackages: Set<String>,
        private val onSelectionChanged: () -> Unit,
        private val onEmptyStateChanged: (Boolean) -> Unit,
    ) : BaseAdapter() {
        private var allEntries = emptyList<ClipboardAppEntry>()
        private var visibleEntries = emptyList<ClipboardAppEntry>()
        var query: String = ""
            set(value) {
                field = value
                visibleEntries = sortedEntries()
                notifyDataSetChanged()
                refreshEmptyState()
            }

        fun setEntries(entries: List<ClipboardAppEntry>) {
            allEntries = entries
            visibleEntries = sortedEntries()
            notifyDataSetChanged()
            refreshEmptyState()
        }

        fun refreshEmptyState() {
            onEmptyStateChanged(visibleEntries.isEmpty())
        }

        fun visibleEntryAt(position: Int): ClipboardAppEntry? = visibleEntries.getOrNull(position)

        override fun getCount(): Int = visibleEntries.size

        override fun getItem(position: Int): ClipboardAppEntry = visibleEntries[position]

        override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: createClipboardAppRow()
            val holder = row.tag as ClipboardAppRowHolder
            val entry = getItem(position)
            holder.packageName = entry.packageName
            holder.icon.setImageDrawable(cachedClipboardIcon(entry))
            holder.title.text = entry.label
            holder.summary.text = entry.packageName
            holder.checkbox.isChecked = entry.packageName in selectedPackages
            row.setOnClickListener {
                if (!selectedPackages.remove(entry.packageName)) {
                    selectedPackages.add(entry.packageName)
                }
                notifyDataSetChanged()
                onSelectionChanged()
            }
            return row
        }

        private fun sortedEntries(): List<ClipboardAppEntry> {
            val normalizedQuery = query.trim()
            val source = if (normalizedQuery.isEmpty()) {
                allEntries
            } else {
                allEntries.filter {
                    it.label.contains(normalizedQuery, ignoreCase = true) ||
                        it.packageName.contains(normalizedQuery, ignoreCase = true)
                }
            }
            return source.sortedWith(
                compareByDescending<ClipboardAppEntry> { it.packageName in pinnedPackages }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label.toString() }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName },
            )
        }
    }

    private fun createClipboardAppRow(): LinearLayout {
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val title = TextView(this).apply {
            setTextColor(palette.title)
            setTextSizeDp(14.0f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = true
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
        val summary = TextView(this).apply {
            setTextColor(palette.summary)
            setTextSizeDp(11.8f)
            includeFontPadding = true
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
        val checkbox = CheckBox(this).apply {
            isClickable = false
            isFocusable = false
            background = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                foreground = null
            }
            buttonTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette.accent, palette.switchOff),
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(8), dp(7))
            addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(
                LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title, matchWidthLayoutParams())
                    addView(summary, matchWidthLayoutParams())
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                    marginEnd = dp(8)
                },
            )
            addView(checkbox, LinearLayout.LayoutParams(dp(42), dp(42)))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(65)
            background = selectableItemBackground()
            isClickable = true
            isFocusable = true
            addView(
                content,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(64),
                ),
            )
            addView(
                View(this@SettingsActivity).apply { setBackgroundColor(clipboardListDividerColor()) },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(0.6f),
                ).apply {
                    marginStart = dp(22)
                    marginEnd = dp(22)
                },
            )
            tag = ClipboardAppRowHolder(icon, title, summary, checkbox)
        }
    }

    private data class ClipboardAppEntry(
        val label: CharSequence,
        val packageName: String,
        val canLoadIcon: Boolean,
    )

    private data class ClipboardAppRowHolder(
        val icon: ImageView,
        val title: TextView,
        val summary: TextView,
        val checkbox: CheckBox,
        var packageName: String? = null,
    )

    private data class ModernDialogAction(
        val title: CharSequence,
        val emphasized: Boolean = false,
        val onClickWithRes: (() -> Boolean)? = null,
        val onClick: (() -> Unit)? = null,
    )

    data class HotReloadEntry(
        val target: HookedTarget
    )

    private inner class HotReloadAdapter(
        private val selectedUids: MutableSet<Int>,
        private val onSelectionChanged: () -> Unit,
        private val onEmptyStateChanged: (Boolean) -> Unit,
    ) : BaseAdapter() {
        private var allEntries = emptyList<HotReloadEntry>()
        private var visibleEntries = emptyList<HotReloadEntry>()
        var query: String = ""
            set(value) {
                field = value
                visibleEntries = sortedEntries()
                notifyDataSetChanged()
                refreshEmptyState()
            }

        fun setEntries(entries: List<HotReloadEntry>) {
            allEntries = entries
            visibleEntries = sortedEntries()
            notifyDataSetChanged()
            refreshEmptyState()
        }

        fun refreshEmptyState() {
            onEmptyStateChanged(visibleEntries.isEmpty())
        }

        fun visibleEntryAt(position: Int): HotReloadEntry? = visibleEntries.getOrNull(position)

        override fun getCount(): Int = visibleEntries.size

        override fun getItem(position: Int): HotReloadEntry = visibleEntries[position]

        override fun getItemId(position: Int): Long = getItem(position).target.pid.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: createHotReloadAppRow()
            val holder = row.tag as HotReloadRowHolder
            val entry = getItem(position)
            holder.packageName = entry.target.processName.split(":").first()
            holder.title.text = entry.target.processName
            holder.summary.text = SpannableStringBuilder()
                .append(
                    entry.target.state.describe(), when (entry.target.state) {
                        HookedTarget.State.STALE -> ForegroundColorSpan(0xff067d17.toInt())
                        HookedTarget.State.FAILED -> ForegroundColorSpan(Color.RED)
                        else -> null
                    }, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                .append("\npid=${entry.target.pid} uid=${entry.target.uid} loaded=${entry.target.loadedVersionCode}")
            holder.checkbox.isChecked = entry.target.pid in selectedUids
            row.setOnClickListener {
                if (!selectedUids.remove(entry.target.pid)) {
                    selectedUids.add(entry.target.pid)
                }
                notifyDataSetChanged()
                onSelectionChanged()
            }
            return row
        }

        private fun sortedEntries(): List<HotReloadEntry> {
            val normalizedQuery = query.trim()
            val source = if (normalizedQuery.isEmpty()) {
                allEntries
            } else {
                allEntries.filter {
                    it.target.processName.contains(normalizedQuery, ignoreCase = true)
                }
            }
            return source.sortedWith { a, b ->
                return@sortedWith if (a.target.state == HookedTarget.State.STALE && b.target.state != HookedTarget.State.STALE) {
                    -1
                } else if (a.target.state != HookedTarget.State.STALE && b.target.state == HookedTarget.State.STALE) {
                    1
                } else {
                    if (a.target.processName == "system" && b.target.processName != "system") {
                        -1
                    } else if (a.target.processName != "system" && b.target.processName == "system") {
                        1
                    } else {
                        a.target.processName.compareTo(b.target.processName)
                    }
                }
            } // stale goes first
        }
    }

    private data class HotReloadRowHolder(
        val title: TextView,
        val summary: TextView,
        val checkbox: CheckBox,
        var packageName: String? = null,
    )

    private fun createHotReloadAppRow(): LinearLayout {
        val title = TextView(this).apply {
            setTextColor(palette.title)
            setTextSizeDp(14.0f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = true
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
        val summary = TextView(this).apply {
            setTextColor(palette.summary)
            setTextSizeDp(11.8f)
            includeFontPadding = true
            isSelected = true
        }
        val checkbox = CheckBox(this).apply {
            isClickable = false
            isFocusable = false
            background = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                foreground = null
            }
            buttonTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette.accent, palette.switchOff),
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(8), dp(7))
            addView(
                LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title, matchWidthLayoutParams())
                    addView(summary, matchWidthLayoutParams())
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                    marginEnd = dp(8)
                },
            )
            addView(checkbox, LinearLayout.LayoutParams(dp(42), dp(42)))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(65)
            background = selectableItemBackground()
            isClickable = true
            isFocusable = true
            addView(
                content,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(64),
                ),
            )
            addView(
                View(this@SettingsActivity).apply { setBackgroundColor(clipboardListDividerColor()) },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(0.6f),
                ).apply {
                    marginStart = dp(22)
                    marginEnd = dp(22)
                },
            )
            tag = HotReloadRowHolder(title, summary, checkbox)
        }
    }

    companion object {

        private var clipboardAppEntryCache: List<ClipboardAppEntry>? = null
        private val clipboardIconCache = java.util.concurrent.ConcurrentHashMap<String, Drawable>()
        private val loadingClipboardIcons = java.util.Collections.synchronizedSet(mutableSetOf<String>())


    }
}

fun HookedTarget.State.describe() = when (this) {
    HookedTarget.State.UP_TO_DATE -> "已是最新"
    HookedTarget.State.FAILED -> "更新失败"
    HookedTarget.State.STALE -> "需要更新"
    HookedTarget.State.RELOADING -> "正在更新"
}
