@file:Suppress("DEPRECATION")

package io.github.a13e300.myinjector

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Bundle
import android.preference.ListPreference
import android.preference.MultiSelectListPreference
import android.preference.Preference
import android.preference.PreferenceGroup
import android.preference.PreferenceScreen
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.deoptimize
import io.github.a13e300.myinjector.arch.findBaseActivity
import io.github.a13e300.myinjector.arch.forceSetSelection
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.inflateLayout
import io.github.a13e300.myinjector.arch.newInstAs
import io.github.a13e300.myinjector.arch.restartApplication
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.sp2px

abstract class SettingDialog(val activityCtx: Context) : Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener {
    private val appCtx = (activityCtx.call(
        "createApplicationContext",
        ApplicationInfo(activityCtx.applicationInfo).apply {
            packageName = BuildConfig.APPLICATION_ID
            sourceDir = Entry.modulePath
            publicSourceDir = Entry.modulePath
            splitSourceDirs = null
            splitPublicSourceDirs = null
            splitNames = null
        }, 0
    ) as Context).createConfigurationContext(activityCtx.resources.configuration)
    val context = object : ContextThemeWrapper(
        appCtx,
        R.style.AppTheme
    ) {
        override fun getSystemService(name: String): Any? {
            if (name == WINDOW_SERVICE) return activityCtx.getSystemService(name)
            return super.getSystemService(name)
        }
    }

    private lateinit var listView: ListView
    private lateinit var adapter: BaseAdapter
    protected lateinit var prefScreen: PreferenceScreen
    private var searchItems = listOf<SearchItem>()
    private var ListAdapter.preferenceList: List<Preference>
        get() = getObjAs("mPreferenceList")
        set(value) {
            setObj("mPreferenceList", value)
        }


    @Deprecated("Deprecated in Java")
    override fun onPreferenceChange(
        preference: Preference,
        newValue: Any?
    ): Boolean {
        return onPrefChanged(preference, newValue)
    }

    abstract fun onPrefChanged(preference: Preference, newValue: Any?): Boolean

    @Deprecated("Deprecated in Java")
    override fun onPreferenceClick(preference: Preference): Boolean {
        return onPrefClicked(preference)
    }

    abstract fun onPrefClicked(preference: Preference): Boolean

    fun search(text: String) {
        val preferences = if (text.isEmpty()) {
            searchItems.map { it.restore(); it.preference }
        } else {
            searchItems.sortedByDescending { it.calcScoreAndApplyHintBy(text) }
                .filterNot { it.cacheScore == 0 }.map { it.preference }
        }
        adapter.preferenceList = preferences
        adapter.notifyDataSetChanged()
        listView.forceSetSelection(0)
    }

    private fun retrieve(group: PreferenceGroup): List<SearchItem> = buildList {
        for (i in 0 until group.preferenceCount) {
            val preference = group.getPreference(i)
            val entries = when (preference) {
                is ListPreference -> preference.entries
                is MultiSelectListPreference -> preference.entries
                else -> arrayOf()
            }.orEmpty()
            if (preference !is PreferenceGroup) {
                preference.isPersistent = false
                preference.onPreferenceChangeListener = this@SettingDialog
                preference.onPreferenceClickListener = this@SettingDialog
                onRetrievePref(preference)
            }
            val searchItem = SearchItem(
                preference,
                preference.key.orEmpty(),
                preference.title ?: "",
                preference.summary ?: "",
                entries,
                preference is PreferenceGroup,
            )
            // searchItem.appendExtraKeywords()
            add(searchItem)
            if (preference is PreferenceGroup) {
                addAll(retrieve(preference))
            }
        }
    }

    abstract fun onRetrievePref(preference: Preference)

    class Hint(val hint: String, val startIdx: Int, val fullText: CharSequence)
    class SearchItem(
        val preference: Preference,
        val key: String,
        private val title: CharSequence,
        private val summary: CharSequence,
        private val entries: Array<out CharSequence>,
        private val isGroup: Boolean,
        val extra: MutableList<String> = mutableListOf(),
    ) {
        var cacheScore = 0
            private set

        fun calcScoreAndApplyHintBy(text: String): Int {
            if (text.isEmpty() || isGroup) {
                cacheScore = 0
                return 0
            }
            var score = 0
            var titleHint: Hint? = null
            var summaryHint: Hint? = null
            var otherHint: Hint? = null
            if (title.isNotEmpty() && title.indexOf(text, ignoreCase = true).takeIf { it != -1 }
                    ?.also { titleHint = Hint(text, it, title) } != null
            ) score += 12
            if (summary.isNotEmpty() && summary.indexOf(text, ignoreCase = true).takeIf { it != -1 }
                    ?.also { summaryHint = Hint(text, it, summary) } != null
            ) score += 6
            if (entries.isNotEmpty() && entries.firstNotNullOfOrNull { e ->
                    e.indexOf(text, ignoreCase = true).takeIf { it != -1 }
                        ?.also { otherHint = Hint(text, it, e) }
                } != null) {
                score += 3
            }
            if (extra.isNotEmpty() && extra.firstNotNullOfOrNull { e ->
                    e.indexOf(text, ignoreCase = true).takeIf { it != -1 }
                        ?.also { if (otherHint == null) otherHint = Hint(text, it, e) }
                } != null) {
                score += 2
            }
            cacheScore = score
            applyHint(titleHint, summaryHint, otherHint)
            return score
        }

        fun restore() {
            preference.title = title
            preference.summary = summary
        }

        private fun applyHint(titleHint: Hint?, summaryHint: Hint?, otherHint: Hint?) {
            preference.title = title.withHint(titleHint)
            if (titleHint == null && summaryHint != null) {
                preference.summary = summary.withHint(summaryHint)
            } else if (titleHint == null && otherHint != null) {
                preference.summary = SpannableStringBuilder(summary).apply {
                    if (isNotEmpty()) appendLine()
                    append(otherHint.fullText.withHint(otherHint, true))
                }
            } else {
                preference.summary = summary
            }
        }

        private fun CharSequence.withHint(hint: Hint?, other: Boolean = false): CharSequence {
            if (hint == null || hint.hint.isEmpty())
                return this
            val startIdx = hint.startIdx
            if (startIdx == -1) return this
            val endIdx = startIdx + hint.hint.length
            if (endIdx > length) return this
            val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            val hintColor = preference.context.getColor(R.color.text_search_hint)
            val colorSpan = ForegroundColorSpan(hintColor)
            val boldSpan = StyleSpan(Typeface.BOLD)
            return SpannableStringBuilder(this).apply {
                setSpan(colorSpan, startIdx, endIdx, flags)
                setSpan(boldSpan, startIdx, endIdx, flags)
                if (other) {
                    // to make other text smaller and append to summary
                    val sizeSpan =
                        AbsoluteSizeSpan(12.sp2px(preference.context.resources).toInt(), false)
                    setSpan(sizeSpan, 0, length, flags)
                }
            }
        }

        override fun toString(): String {
            return buildString {
                append("SearchItem {")
                append("\n  preference=")
                append(preference)
                append("\n  title=")
                append(title)
                append("\n  summary=")
                append(summary)
                append("\n score=")
                append(cacheScore)
                append("\n}")
            }
        }
    }

    abstract fun onCreatePref(prefScreen: PreferenceScreen)

    private fun getContentView(): View {
        prefScreen = PreferenceScreen::class.java.newInstAs(context, null)
        listView = ListView(context)

        prefScreen.run {
            onCreatePref(this)
        }
        prefScreen.bind(listView)
        searchItems = retrieve(prefScreen)
        adapter = listView.adapter as BaseAdapter
        val contentView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val searchBar = context.inflateLayout(R.layout.search_bar)
        val editView = searchBar.findViewById<EditText>(R.id.search)
        val clearView = searchBar.findViewById<View>(R.id.clear)
        searchBar.setOnClickListener {
            editView.requestFocus()
            context.getSystemService(InputMethodManager::class.java)
                .showSoftInput(editView, 0)
        }
        editView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) = search(s?.toString()?.trim().orEmpty())
        })
        editView.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(v.text.toString().trim())
                true
            } else false
        }
        clearView.setOnClickListener {
            editView.setText("")
        }
        contentView.addView(searchBar)
        contentView.addView(listView)
        return contentView
    }

    fun show() {
        try {
            val activity = activityCtx.findBaseActivity()
            // activity.addModuleAssets(Entry.modulePath)

            AlertDialog.Builder(context).apply {

                setView(getContentView())
                setTitle("MyInjector")
                setNegativeButton("返回", null)
                setPositiveButton("重启") { _, _ ->
                    restartApplication(activity)
                }

            }.show()
        } catch (e: Resources.NotFoundException) {
            logE("res:", e)
            AlertDialog.Builder(context)
                .setTitle("需要重启")
                .setMessage("由于加载资源失败，需要重启应用以显示设置界面。")
                .setPositiveButton("重启") { _, _ ->
                    restartApplication(activityCtx.findBaseActivity())
                }.show()
        }
    }
}

fun IHook.addSettingsIntentInterceptor(callback: (activity: Activity) -> Unit) = runCatching {
    Activity::class.java.hookAllAfter("performNewIntent") { param ->
        val activity = param.thisObject as Activity
        val intent = param.args[0] as Intent
        // logD("newIntent $intent")
        if (intent.action == "io.github.a13e300.myinjector.SHOW_SETTINGS" || intent.hasCategory("io.github.a13e300.myinjector.SHOW_SETTINGS")) {
            callback(activity)
        }
    }

    Activity::class.java.hookAfter("performCreate", Bundle::class.java) { param ->
        val activity = param.thisObject as Activity
        val intent = activity.intent
        // logD("create Intent $intent")
        if (intent.action == "io.github.a13e300.myinjector.SHOW_SETTINGS" || intent.hasCategory("io.github.a13e300.myinjector.SHOW_SETTINGS")) {
            callback(activity)
        }
    }

    Instrumentation::class.java.deoptimize("callActivityOnCreate")
    Instrumentation::class.java.deoptimize("callActivityOnNewIntent")
}.onFailure {
    logE("addSettingsInterceptor", it)
}
