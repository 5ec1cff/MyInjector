package io.github.a13e300.myinjector

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.text.InputType
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
import io.github.a13e300.myinjector.system_server.ResultReceiver
import io.github.a13e300.myinjector.ui.ModernActionButton
import io.github.a13e300.myinjector.ui.ModernChevronView
import io.github.a13e300.myinjector.ui.ModernInjectedSearchBar
import io.github.a13e300.myinjector.ui.ModernSettingsCard
import io.github.a13e300.myinjector.ui.ModernSettingsHeader
import io.github.a13e300.myinjector.ui.ModernSettingsPalette
import io.github.a13e300.myinjector.ui.ModernSettingsRow
import io.github.a13e300.myinjector.ui.ModernSwitchView
import io.github.a13e300.myinjector.ui.dp
import io.github.a13e300.myinjector.ui.setTextSizeDp
import org.xmlpull.v1.XmlPullParser
import java.util.Arrays
import java.util.UUID
import java.util.stream.Collectors
import kotlin.math.min

class SettingsActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var scrollView: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var header: ModernSettingsHeader
    private lateinit var headerScrim: View
    private lateinit var palette: ModernSettingsPalette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = ModernSettingsPalette.from(this)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        configureWindow()
        setContentView(R.layout.activity_settings)

        root = findViewById(R.id.settings_root)
        root.setBackgroundColor(palette.background)
        buildContent()
    }

    @Suppress("DEPRECATION")
    private fun configureWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (palette.isLight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (palette.isLight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun buildContent() {
        scrollView = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }

        scrollView.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )
        root.addView(
            scrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        )

        loadSectionsFromPrefsXml().forEach(::addSection)
        buildAppsSection()?.let(::addSection)

        headerScrim = View(this).apply {
            background = palette.headerScrim()
        }
        root.addView(
            headerScrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(124),
                Gravity.TOP,
            )
        )

        header = ModernSettingsHeader(this, palette).apply {
            setTitle(getString(R.string.app_name))
            setOnCloseClickListener { finish() }
        }
        root.addView(
            header,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(78),
                Gravity.TOP,
            )
        )

        root.setOnApplyWindowInsetsListener { _, windowInsets ->
            applyInsets(windowInsets)
        }
        root.requestApplyInsets()

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val progress = min(1f, scrollY / dp(72).toFloat())
            header.setScrollProgress(progress)
            headerScrim.alpha = 0.72f + 0.28f * progress
        }
    }

    @Suppress("DEPRECATION")
    private fun applyInsets(windowInsets: WindowInsets): WindowInsets {
        val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = windowInsets.getInsets(WindowInsets.Type.systemBars())
            SystemBarInsets(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            SystemBarInsets(
                windowInsets.systemWindowInsetLeft,
                windowInsets.systemWindowInsetTop,
                windowInsets.systemWindowInsetRight,
                windowInsets.systemWindowInsetBottom,
            )
        }

        scrollView.setPadding(
            0,
            insets.top + dp(74),
            0,
            insets.bottom + dp(24),
        )

        val headerLp = header.layoutParams as FrameLayout.LayoutParams
        headerLp.height = insets.top + dp(78)
        header.layoutParams = headerLp
        header.setTopInset(insets.top)

        val scrimLp = headerScrim.layoutParams as FrameLayout.LayoutParams
        scrimLp.height = insets.top + dp(132)
        headerScrim.layoutParams = scrimLp

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets.CONSUMED
        } else {
            windowInsets.consumeSystemWindowInsets()
        }
    }

    private fun addSection(section: SettingsSectionSpec) {
        val title = TextView(this).apply {
            text = section.title
            setTextColor(palette.summary)
            setTextSizeDp(13.2f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = true
        }
        content.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
                bottomMargin = dp(8)
            }
        )

        val card = ModernSettingsCard(this, palette)
        section.items.forEachIndexed { index, item ->
            val showDivider = hasFollowingRow(section.items, index)
            when (item) {
                is SettingsItemSpec.Action -> addAction(card, item, showDivider)
                is SettingsItemSpec.Switch -> addSwitch(card, item, showDivider)
                is SettingsItemSpec.Text -> addTextEditor(card, item, showDivider)
            }
        }
        content.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(22)
            }
        )
    }

    private fun addSwitch(
        card: ModernSettingsCard,
        item: SettingsItemSpec.Switch,
        showDivider: Boolean,
    ) {
        val switchView = ModernSwitchView(this, palette).apply {
            setCheckedImmediately(prefs.getBoolean(item.key, item.defaultValue))
            contentDescription = item.title
            onCheckedChangeListener = { checked ->
                prefs.edit().putBoolean(item.key, checked).apply()
                commitForSection(item.sectionKey)
            }
        }
        val row = ModernSettingsRow(
            this,
            palette,
            item.title,
            item.summary,
            switchView,
            showDivider,
        ).apply {
            setOnClickListener { switchView.performClick() }
        }
        card.addView(row)
    }

    private fun addTextEditor(
        card: ModernSettingsCard,
        item: SettingsItemSpec.Text,
        showDivider: Boolean,
    ) {
        val row = ModernSettingsRow(
            this,
            palette,
            item.title,
            item.summary,
            ModernChevronView(this, palette),
            showDivider,
        ).apply {
            setOnClickListener {
                if (item.key == "clipboardWhitelistPackages") {
                    showClipboardWhitelistDialog(item)
                } else {
                    showTextEditor(item)
                }
            }
        }
        card.addView(row)
    }

    private fun addAction(
        card: ModernSettingsCard,
        item: SettingsItemSpec.Action,
        showDivider: Boolean,
    ) {
        if (item.style == ActionStyle.Button) {
            val button = ModernActionButton(this, palette, item.title).apply {
                setOnClickListener { handleAction(item) }
            }
            card.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(46),
                ).apply {
                    leftMargin = dp(22)
                    rightMargin = dp(22)
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                }
            )
            return
        }

        val row = ModernSettingsRow(
            this,
            palette,
            item.title,
            item.summary,
            ModernChevronView(this, palette),
            showDivider,
        ).apply {
            setOnClickListener { handleAction(item) }
        }
        card.addView(row)
    }

    private fun hasFollowingRow(items: List<SettingsItemSpec>, currentIndex: Int): Boolean {
        for (i in currentIndex + 1 until items.size) {
            val next = items[i]
            return next !is SettingsItemSpec.Action || next.style == ActionStyle.Row
        }
        return false
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
            setSingleLine(true)
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
                    prefs.edit().putString(item.key, entries.joinToString("\n")).apply()
                    commitForSection(item.sectionKey)
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
                }
            }.start()
        } else {
            entries = clipboardAppEntries(selectedPackages)
            adapter.setEntries(entries)
            loadingView.visibility = View.GONE
            listView.visibility = View.VISIBLE
            adapter.refreshEmptyState()
        }

        showModernDialog(
            title = item.title,
            content = content,
            actions = listOf(
                ModernDialogAction("取消"),
                ModernDialogAction("保存", emphasized = true) {
                    prefs.edit()
                        .putString(item.key, sortedClipboardPackages(entries, selectedPackages))
                        .apply()
                    commitForSection(item.sectionKey)
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
                    icon = packageManager.defaultActivityIcon,
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
                        icon = appInfo.loadIcon(pm) ?: pm.defaultActivityIcon,
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
            "commit" -> commitSystem()
            "commitSysUI" -> commitSystemUI()
            else -> item.action?.invoke()
        }
    }

    private fun commitForSection(sectionKey: String) {
        when (sectionKey) {
            "system" -> commitSystem()
            "systemui" -> commitSystemUI()
            "miuihome" -> commitMiuiHome()
        }
    }

    private fun loadSectionsFromPrefsXml(): List<SettingsSectionSpec> {
        val sections = mutableListOf<MutableSettingsSectionSpec>()
        var current: MutableSettingsSectionSpec? = null
        val parser = resources.getXml(R.xml.prefs)

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "PreferenceCategory" -> {
                                val section = MutableSettingsSectionSpec(
                                    key = parser.attr("key").orEmpty(),
                                    title = parser.textAttr("title"),
                                )
                                current = section
                                sections.add(section)
                            }

                            "SwitchPreference" -> {
                                val key = parser.attr("key")
                                val section = current
                                if (key != null && section != null) {
                                    section.items.add(
                                        SettingsItemSpec.Switch(
                                            sectionKey = section.key,
                                            key = key,
                                            title = parser.textAttr("title"),
                                            summary = parser.optionalTextAttr("summary"),
                                            defaultValue = parser.getAttributeBooleanValue(
                                                ANDROID_NS,
                                                "defaultValue",
                                                false,
                                            ),
                                        )
                                    )
                                }
                            }

                            "EditTextPreference" -> {
                                val key = parser.attr("key")
                                val section = current
                                if (key != null && section != null) {
                                    val summary = parser.optionalTextAttr("summary")
                                    val helpText = if (key in TEXT_HELP_CARD_KEYS) summary else null
                                    section.items.add(
                                        SettingsItemSpec.Text(
                                            sectionKey = section.key,
                                            key = key,
                                            title = parser.textAttr("title"),
                                            summary = if (helpText == null) summary else null,
                                            helpText = helpText,
                                        )
                                    )
                                }
                            }

                            "Preference" -> {
                                val key = parser.attr("key")
                                val section = current
                                if (key != null && section != null) {
                                    section.items.add(
                                        SettingsItemSpec.Action(
                                            sectionKey = section.key,
                                            key = key,
                                            title = parser.textAttr("title"),
                                            summary = parser.optionalTextAttr("summary"),
                                            style = actionStyleFor(key),
                                        )
                                    )
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name == "PreferenceCategory") current = null
                    }
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }

        return sections.map { SettingsSectionSpec(it.key, it.title, it.items) }
    }

    private fun XmlResourceParser.attr(name: String): String? =
        getAttributeValue(ANDROID_NS, name)

    private fun XmlResourceParser.optionalTextAttr(name: String): CharSequence? {
        val resId = getAttributeResourceValue(ANDROID_NS, name, 0)
        if (resId != 0) return resources.getText(resId)
        return getAttributeValue(ANDROID_NS, name)
    }

    private fun XmlResourceParser.textAttr(name: String): CharSequence =
        optionalTextAttr(name) ?: ""

    private fun actionStyleFor(key: String): ActionStyle =
        if (key == "commit" || key == "commitSysUI") ActionStyle.Button else ActionStyle.Row

    private fun buildAppsSection(): SettingsSectionSpec? {
        val pm = packageManager
        val items = APP_PACKAGES.mapNotNull { pkg ->
            runCatching {
                val info = try {
                    pm.getApplicationInfo(pkg, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
                val label = info.loadLabel(pm)
                SettingsItemSpec.Action(
                    sectionKey = "apps",
                    key = "open:$pkg",
                    title = "打开 $label 设置",
                    summary = pkg,
                    style = ActionStyle.Row,
                    action = {
                        runCatching {
                            val intent = pm.getLaunchIntentForPackage(pkg)
                            if (intent != null) {
                                intent.action = "io.github.a13e300.myinjector.SHOW_SETTINGS"
                                intent.categories.clear()
                                intent.addCategory("io.github.a13e300.myinjector.SHOW_SETTINGS")
                                intent.addCategory(UUID.randomUUID().toString())
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                            }
                        }.onFailure {
                            logE("failed to open settings for $pkg:", it)
                        }
                    },
                )
            }.onFailure {
                logE("addPackageSettings $pkg", it)
            }.getOrNull()
        }

        if (items.isEmpty()) return null
        return SettingsSectionSpec("apps", "Apps", items)
    }

    private fun commitSystem() {
        val intent = Intent("io.github.a13e300.myinjector.UPDATE_SYSTEM_CONFIG")
        val pendingIntent =
            PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val config = SystemServerConfig.newBuilder()
            .setNoWakePath(prefs.getBoolean("noWakePath", false))
            .setNoMiuiIntent(prefs.getBoolean("noMiuiIntent", false))
            .setClipboardWhitelist(prefs.getBoolean("clipboardWhitelist", false))
            .setFixSync(prefs.getBoolean("fixSync", false))
            .setXSpace(prefs.getBoolean("xSpace", false))
            .setBypassShellDexOptRestriction(
                prefs.getBoolean(
                    "bypassShellDexOptRestriction",
                    false,
                )
            )
            .setNoSwipeToKillLockedProcess(prefs.getBoolean("noSwipeToKillLockedProcess", false))
            .setNoSwipeToKillNoRestrictProcess(
                prefs.getBoolean(
                    "noSwipeToKillNoRestrictProcess",
                    false,
                )
            )
            .addAllClipboardWhitelistPackages(
                Arrays.stream<String?>(
                    prefs.getString("clipboardWhitelistPackages", "")!!.trim { it <= ' ' }
                        .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                )
                    .collect(
                        Collectors.toList()
                    )
            )
            .setForceNewTask(prefs.getBoolean("forceNewTask", false))
            .addAllForceNewTaskRules(
                Arrays.stream<String?>(
                    prefs.getString(
                        "forceNewTaskRules",
                        "",
                    )!!.trim { it <= ' ' }.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                ).map<NewTaskRule?> { x: String? ->
                    val parts: Array<String?> =
                        x!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    var ignoreResult = false
                    var useNewDoc = false
                    if (parts.size == 3) {
                        val options: Array<String?> =
                            parts[2]!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        for (o in options) {
                            if ("ir" == o) {
                                ignoreResult = true
                            } else if ("nd" == o) {
                                useNewDoc = true
                            }
                        }
                    } else if (parts.size != 2) return@map null
                    var sourcePackage: String = parts[0]!!
                    var targetPackage: String = parts[1]!!
                    var sourceComponent = ""
                    var targetComponent = ""
                    if (sourcePackage.contains("/")) {
                        val l: Array<String?> =
                            sourcePackage.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        if (l.size == 2) {
                            sourcePackage = l[0]!!
                            sourceComponent = l[1]!!
                            if (sourceComponent.startsWith(".")) sourceComponent = l[0] + l[1]
                        }
                    }
                    if (targetPackage.contains("/")) {
                        val l: Array<String?> =
                            targetPackage.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        if (l.size == 2) {
                            targetPackage = l[0]!!
                            targetComponent = l[1]!!
                            if (targetComponent.startsWith(".")) targetComponent = l[0] + l[1]
                        }
                    }
                    NewTaskRule.newBuilder()
                        .setSourcePackage(sourcePackage)
                        .setTargetPackage(targetPackage)
                        .setSourceComponent(sourceComponent)
                        .setTargetComponent(targetComponent)
                        .setUseNewDocument(useNewDoc)
                        .setIgnoreResult(ignoreResult)
                        .build()
                }.filter { it != null }.collect(Collectors.toList())
            )
            .setOverrideStatusBar(prefs.getBoolean("overrideStatusBar", false))
            .addAllOverrideStatusBarRules(
                Arrays.stream<String?>(
                    prefs.getString("overrideStatusBarRules", "")!!
                        .trim { it <= ' ' }
                        .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                ).map<OverrideStatusBarRule?> { x: String? ->
                    val parts: Array<String?> =
                        x!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (parts.size != 2) return@map null
                    var pkg: String = parts[0]!!
                    var light = false
                    if ("light" == parts[1]) {
                        light = true
                    } else if ("dark" != parts[1]) {
                        return@map null
                    }
                    var component = ""
                    val pkgSplit: Array<String?> =
                        pkg.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (pkgSplit.size == 2) {
                        pkg = pkgSplit[0]!!
                        component = pkgSplit[1]!!
                        if (component.startsWith(".")) component = pkg + component
                    } else if (pkgSplit.size != 1) return@map null
                    OverrideStatusBarRule.newBuilder()
                        .setPackage(pkg)
                        .setComponent(component)
                        .setLight(light)
                        .build()
                }.filter { it != null }.collect(Collectors.toList())
            )
            .setForceNewTaskDebug(prefs.getBoolean("forceNewTaskDebug", false))
            .build()
        intent.putExtra("EXTRA_CREDENTIAL", pendingIntent)
        intent.putExtra("EXTRA_CONFIG", config.toByteArray())
        sendBroadcast(intent)
    }

    private fun commitSystemUI() {
        val intent = Intent("io.github.a13e300.myinjector.UPDATE_SYSTEMUI_CONFIG")
        val pendingIntent =
            PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val config = SystemUIConfig.newBuilder()
            .setAlwaysExpandNotification(prefs.getBoolean("alwaysExpandNotification", false))
            .setNoDndNotification(prefs.getBoolean("noDndNotification", false))
            .setShowNotificationDetail(prefs.getBoolean("showNotificationDetail", false))
            .setFixWhiteSplash(prefs.getBoolean("fixWhiteSplash", false))
            .build()
        intent.putExtra("EXTRA_CREDENTIAL", pendingIntent)
        intent.putExtra("EXTRA_CONFIG", config.toByteArray())
        sendBroadcast(intent)
    }

    private fun commitMiuiHome() {
        val intent = Intent("io.github.a13e300.myinjector.UPDATE_MIUI_HOME_CONFIG")
        val pendingIntent =
            PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val config = MiuiHomeConfig.newBuilder()
            .setOpenAospSettings(prefs.getBoolean("miuiHomeOpenAospSettings", false))
            .setDragKill(prefs.getBoolean("miuiHomeDragKill", false))
            .setDisablePreLaunch(prefs.getBoolean("miuiHomeDisablePreLaunch", false))
            .build()
        intent.putExtra("EXTRA_CREDENTIAL", pendingIntent)
        intent.putExtra("EXTRA_CONFIG", config.toByteArray())
        sendBroadcast(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun showHotUpdateDialog() {
        val statusText = TextView(this).apply {
            text = "准备检查"
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

        fun command(name: String, args: Bundle.() -> Unit = {}, cb: (Int, Bundle?) -> Unit) {
            val intent = Intent("io.github.a13e300.myinjector.SYSTEM_SERVER_ENTRY")
            intent.`package` = "android"
            val pendingIntent =
                PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
            val receiver = object : ResultReceiver() {
                override fun onReceive(code: Int, data: Bundle?) {
                    cb(code, data)
                }
            }
            val b = Bundle().apply {
                putParcelable("EXTRA_CREDENTIAL", pendingIntent)
                putString("EXTRA_ACTION", name)
                putBinder("EXTRA_RECEIVER", receiver)
                args()
            }
            intent.putExtras(b)
            sendBroadcast(intent)
        }

        fun check() {
            statusText.text = "检查中……"
            command("needUpdate") { code, _ ->
                statusText.post {
                    statusText.text = if (code == 1) "可更新" else "无需更新"
                }
            }
        }

        fun update(force: Boolean) {
            command("reload", { putBoolean("force", force) }) { code, _ ->
                statusText.post {
                    statusText.text = if (code == 1) "重新加载成功" else "重新加载失败"
                }
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(this@SettingsActivity).apply {
                    gravity = Gravity.CENTER
                    addView(
                        statusText,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
                matchWidthLayoutParams(bottom = 14),
            )
            addView(modernDialogButton("检查更新") { check() }, matchWidthLayoutParams(bottom = 8))
            addView(
                modernDialogButton("主动 GC") {
                    command("gc") { code, _ ->
                        statusText.post {
                            statusText.text = if (code == 0) "GC 成功" else "GC 失败"
                        }
                    }
                },
                matchWidthLayoutParams(bottom = 8),
            )
            addView(
                modernDialogButton("查询旧模块") {
                    command("reportOldHook") { _, data ->
                        val hooks = data?.getString("hooks").orEmpty()
                        statusText.post {
                            statusText.text = "旧模块：" + hooks.ifEmpty { "无" }
                        }
                    }
                },
                matchWidthLayoutParams(bottom = 8),
            )
            addView(modernDialogButton("重新加载") { update(false) }, matchWidthLayoutParams(bottom = 8))
            addView(modernDialogButton("强制重新加载") { update(true) }, matchWidthLayoutParams())
        }

        check()

        showModernDialog(
            title = "热更新",
            content = content,
            actions = listOf(ModernDialogAction("关闭", emphasized = true)),
        )
    }

    private fun showModernDialog(
        title: CharSequence,
        content: View,
        actions: List<ModernDialogAction>,
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.44f)
            setGravity(Gravity.CENTER)
            setWindowAnimations(0)
            attributes = attributes.apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER
                dimAmount = 0.44f
            }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = palette.cardBackground(this@SettingsActivity)
            setPadding(dp(24), dp(22), dp(24), dp(24))
        }

        card.addView(
            TextView(this).apply {
                text = title
                setTextColor(palette.title)
                setTextSizeDp(16f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                includeFontPadding = true
            },
            matchWidthLayoutParams(bottom = 16),
        )

        card.addView(content, matchWidthLayoutParams(bottom = 18))

        val actionBar = LinearLayout(this).apply {
            orientation = if (actions.size <= 2) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        actions.forEachIndexed { index, action ->
            val button = modernDialogButton(action.title, action.emphasized) {
                action.onClick?.invoke()
                dialog.dismiss()
            }
            val lp = if (actions.size <= 2) {
                LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                    if (index > 0) marginStart = dp(10)
                }
            } else {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(46),
                ).apply {
                    if (index < actions.lastIndex) bottomMargin = dp(8)
                }
            }
            actionBar.addView(button, lp)
        }
        card.addView(actionBar, matchWidthLayoutParams())

        val outer = FrameLayout(this).apply {
            minimumWidth = resources.displayMetrics.widthPixels
            setPadding(dp(24), 0, dp(24), 0)
            addView(
                card,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
            )
        }

        dialog.setContentView(
            outer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        dialog.show()
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
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

        override fun getCount(): Int = visibleEntries.size

        override fun getItem(position: Int): ClipboardAppEntry = visibleEntries[position]

        override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: createClipboardAppRow()
            val holder = row.tag as ClipboardAppRowHolder
            val entry = getItem(position)
            holder.icon.setImageDrawable(entry.newIconDrawable())
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
        val icon: Drawable,
    ) {
        fun newIconDrawable(): Drawable =
            icon.constantState?.newDrawable()?.mutate() ?: icon
    }

    private data class ClipboardAppRowHolder(
        val icon: ImageView,
        val title: TextView,
        val summary: TextView,
        val checkbox: CheckBox,
    )

    private data class ModernDialogAction(
        val title: CharSequence,
        val emphasized: Boolean = false,
        val onClick: (() -> Unit)? = null,
    )

    private data class SettingsSectionSpec(
        val key: String,
        val title: CharSequence,
        val items: List<SettingsItemSpec>,
    )

    private data class MutableSettingsSectionSpec(
        val key: String,
        val title: CharSequence,
        val items: MutableList<SettingsItemSpec> = mutableListOf(),
    )

    private sealed class SettingsItemSpec {
        abstract val sectionKey: String
        abstract val key: String
        abstract val title: CharSequence
        abstract val summary: CharSequence?

        data class Switch(
            override val sectionKey: String,
            override val key: String,
            override val title: CharSequence,
            override val summary: CharSequence?,
            val defaultValue: Boolean,
        ) : SettingsItemSpec()

        data class Text(
            override val sectionKey: String,
            override val key: String,
            override val title: CharSequence,
            override val summary: CharSequence?,
            val helpText: CharSequence?,
        ) : SettingsItemSpec()

        data class Action(
            override val sectionKey: String,
            override val key: String,
            override val title: CharSequence,
            override val summary: CharSequence?,
            val style: ActionStyle,
            val action: (() -> Unit)? = null,
        ) : SettingsItemSpec()
    }

    private enum class ActionStyle {
        Row,
        Button,
    }

    private data class SystemBarInsets(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    companion object {
        private const val PREFS_NAME = "system_server"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        private val TEXT_HELP_CARD_KEYS = setOf(
            "forceNewTaskRules",
            "overrideStatusBarRules",
        )

        private var clipboardAppEntryCache: List<ClipboardAppEntry>? = null

        private val APP_PACKAGES = listOf(
            "com.xingin.xhs",
            "com.kiwibrowser.browser",
            "com.android.chrome",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.messenger.beta",
            "org.telegram.plus",
            "com.exteragram.messenger",
            "com.radolyn.ayugram",
            "uz.unnarsx.cherrygram",
            "xyz.nextalone.nagram",
            "nu.gpu.nagram",
            "com.xtaolabs.pagergram",
            "fork.risin42.nagramx",
        )
    }
}
