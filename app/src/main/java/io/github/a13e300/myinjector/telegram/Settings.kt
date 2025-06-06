@file:Suppress("DEPRECATION")

package io.github.a13e300.myinjector.telegram

import android.app.AlertDialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.preference.ListPreference
import android.preference.MultiSelectListPreference
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.preference.PreferenceScreen
import android.preference.SwitchPreference
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.Entry
import io.github.a13e300.myinjector.R
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.addModuleAssets
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.forceSetSelection
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.inflateLayout
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.restartApplication
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.sp2px

class SettingDialog(context: Context) : AlertDialog.Builder(context) {

    class PrefsFragment : PreferenceFragment(), Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {
        private lateinit var listView: ListView
        private lateinit var adapter: BaseAdapter
        private var searchItems = listOf<SearchItem>()
        private var ListAdapter.preferenceList: List<Preference>
            get() = getObjAs("mPreferenceList")
            set(value) {
                setObj("mPreferenceList", value)
            }

        @Deprecated("Deprecated in Java")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            preferenceManager.sharedPreferencesName = "MyInjector_telegram"
            preferenceScreen = preferenceManager.call(
                "inflateFromResource",
                ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight),
                R.xml.tg_settings,
                preferenceScreen
            ) as PreferenceScreen
            searchItems = retrieve(preferenceScreen)
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val inf = inflater.cloneInContext(
                ContextThemeWrapper(
                    context,
                    android.R.style.Theme_DeviceDefault_DayNight
                )
            )

            return super.onCreateView(inf, container, savedInstanceState)
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            listView = view?.findViewById(android.R.id.list) ?: return
            adapter = listView.adapter as BaseAdapter
        }

        @Deprecated("Deprecated in Java")
        override fun onPreferenceChange(
            preference: Preference,
            newValue: Any?
        ): Boolean {
            val settings = TelegramHandler.settings.toBuilder()
            val v = newValue as Boolean
            when (preference.key) {
                "enabled" -> settings.disabled = !v
                "autoCheckDeleteMessageOption" ->
                    settings.autoCheckDeleteMessageOption = v

                "autoUncheckSharePhoneNumber" ->
                    settings.autoUncheckSharePhoneNumber = v

                "avatarPageScrollToCurrent" ->
                    settings.avatarPageScrollToCurrent = v

                "contactPermission" ->
                    settings.contactPermission = v

                "customEmojiMapping" ->
                    settings.customEmojiMapping = v

                "customMapPosition" ->
                    settings.customMapPosition = v

                "defaultSearchTab" ->
                    settings.defaultSearchTab = v

                "disableVoiceOrCameraButton" ->
                    settings.disableVoiceOrCameraButton = v

                "emojiStickerMenu" ->
                    settings.emojiStickerMenu = v

                "fakeInstallPermission" ->
                    settings.fakeInstallPermission = v

                "fixHasAppToOpen" ->
                    settings.fixHasAppToOpen = v

                "longClickMention" ->
                    settings.longClickMention = v

                "mutualContact" ->
                    settings.mutualContact = v

                "noGoogleMaps" ->
                    settings.noGoogleMaps = v

                "openLinkDialog" ->
                    settings.openLinkDialog = v

                "sendImageWithHighQualityByDefault" ->
                    settings.sendImageWithHighQualityByDefault = v
            }
            TelegramHandler.updateSettings(settings.build())
            return true
        }

        @Deprecated("Deprecated in Java")
        override fun onPreferenceClick(preference: Preference): Boolean {
            if (preference.key == "customEmojiMappingConfig") {
                CustomEmojiMapping.importEmojiMap(context)
                return true
            }
            return false
        }

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
                    preference.onPreferenceChangeListener = this@PrefsFragment
                    preference.onPreferenceClickListener = this@PrefsFragment
                    when (preference.key) {
                        "enabled" -> (preference as SwitchPreference).isChecked =
                            !TelegramHandler.settings.disabled

                        "autoCheckDeleteMessageOption" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.autoCheckDeleteMessageOption

                        "autoUncheckSharePhoneNumber" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.autoUncheckSharePhoneNumber

                        "avatarPageScrollToCurrent" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.avatarPageScrollToCurrent

                        "contactPermission" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.contactPermission

                        "customEmojiMapping" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.customEmojiMapping

                        "customEmojiMappingConfig" -> preference.summary =
                            "加载了${CustomEmojiMapping.emotionMap.map.size}条映射规则"

                        "customMapPosition" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.customMapPosition

                        "defaultSearchTab" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.defaultSearchTab

                        "disableVoiceOrCameraButton" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.disableVoiceOrCameraButton

                        "emojiStickerMenu" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.emojiStickerMenu

                        "fakeInstallPermission" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.fakeInstallPermission

                        "fixHasAppToOpen" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.fixHasAppToOpen

                        "longClickMention" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.longClickMention

                        "mutualContact" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.mutualContact

                        "noGoogleMaps" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.noGoogleMaps

                        "openLinkDialog" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.openLinkDialog

                        "sendImageWithHighQualityByDefault" -> (preference as SwitchPreference).isChecked =
                            TelegramHandler.settings.sendImageWithHighQualityByDefault
                    }
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
    }

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

    private fun getContentView(fragment: PrefsFragment): View {
        val contentView = LinearLayout(fragment.context).apply {
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
            ) = fragment.search(s?.toString()?.trim().orEmpty())
        })
        editView.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                fragment.search(v.text.toString().trim())
                true
            } else false
        }
        clearView.setOnClickListener {
            editView.setText("")
        }
        contentView.addView(searchBar)
        contentView.addView(fragment.view)
        return contentView
    }

    companion object {

        fun show(context: Context) {
            try {
                SettingDialog(
                    ContextThemeWrapper(
                        context,
                        android.R.style.Theme_DeviceDefault_DayNight
                    )
                ).show()
            } catch (_: Resources.NotFoundException) {
                AlertDialog.Builder(context)
                    .setTitle("需要重启")
                    .setPositiveButton("重启") { _, _ ->
                        restartApplication(context.findBaseActivity())
                    }.show()
            }
        }
    }

    init {
        val activity = context.findBaseActivity()
        activity.addModuleAssets(Entry.modulePath)

        // dirty way to make list preference summary span style take effect,
        // we have no choice, see ListPreference#getSummary
        val summaryHook = ListPreference::class.java.hookBefore("getSummary") { param ->
            param.thisObject.setObj("mSummary", null)
        }

        val prefsFragment = PrefsFragment()
        activity.fragmentManager.beginTransaction().add(prefsFragment, "Setting").commit()
        activity.fragmentManager.executePendingTransactions()

        prefsFragment.onActivityCreated(null)

        val unhook = Preference::class.java.hookAfter(
            "onCreateView", ViewGroup::class.java
        ) { param ->
            if (PreferenceCategory::class.java.isInstance(param.thisObject)
                && TextView::class.java.isInstance(param.result)
            ) {
                val textView = param.result as TextView
                if (textView.textColors.defaultColor == -13816531)
                    textView.setTextColor(Color.GRAY)
            }
        }

        setView(getContentView(prefsFragment))
        setTitle("MyInjector")
        setNegativeButton("返回", null)
        setPositiveButton("重启") { _, _ ->
            restartApplication(activity)
        }
        setOnDismissListener {
            unhook.unhook()
            summaryHook.unhook()
        }
    }
}

class Settings : IHook() {
    override fun onHook(param: XC_LoadPackage.LoadPackageParam) {
        val drawerLayoutAdapterClass = findClass("org.telegram.ui.Adapters.DrawerLayoutAdapter")
        val itemClass = findClass("org.telegram.ui.Adapters.DrawerLayoutAdapter\$Item")

        drawerLayoutAdapterClass.hookAllAfter("resetItems") { param ->
            val items = param.thisObject.getObjAs<ArrayList<Any?>>("items")
            val sepIdx = items.lastIndexOf(null)
            val settingsIdx = sepIdx - 1
            val settingsItem = items[settingsIdx]
            // getItemViewType() return 3 by default
            val mySettingsItem =
                itemClass.newInst(114514, "MyInjector", settingsItem.getObjAs<Int>("icon"))
            mySettingsItem.setObj("listener", object : View.OnClickListener {
                override fun onClick(v: View) {
                    SettingDialog.show(v.context)
                }
            })
            items.add(sepIdx, mySettingsItem)
        }
    }
}
