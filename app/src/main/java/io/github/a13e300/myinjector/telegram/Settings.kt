@file:Suppress("DEPRECATION")

package io.github.a13e300.myinjector.telegram

import android.app.AlertDialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.preference.ListPreference
import android.preference.MultiSelectListPreference
import android.preference.Preference
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
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import io.github.a13e300.myinjector.Entry
import io.github.a13e300.myinjector.R
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.addModuleAssets
import io.github.a13e300.myinjector.arch.category
import io.github.a13e300.myinjector.arch.forceSetSelection
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.inflateLayout
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.newInstAs
import io.github.a13e300.myinjector.arch.preference
import io.github.a13e300.myinjector.arch.restartApplication
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.sp2px
import io.github.a13e300.myinjector.arch.switchPreference

class SettingDialog(context: Context) : AlertDialog.Builder(context),
    Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener {

    private lateinit var listView: ListView
    private lateinit var adapter: BaseAdapter
    private lateinit var prefScreen: PreferenceScreen
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

            "hidePhoneNumber" -> {
                settings.hidePhoneNumber = v
                prefScreen.findPreference("hidePhoneNumberForSelfOnly").isEnabled = v
            }

            "hidePhoneNumberForSelfOnly" ->
                settings.hidePhoneNumberForSelfOnly = v

            "alwaysShowStorySaveIcon" ->
                settings.alwaysShowStorySaveIcon = v

            "removeArchiveFolder" ->
                settings.removeArchiveFolder = v

            "alwaysShowDownloadManager" ->
                settings.alwaysShowDownloadManager = v

            "hideFloatFab" ->
                settings.hideFloatFab = v
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
                preference.onPreferenceChangeListener = this@SettingDialog
                preference.onPreferenceClickListener = this@SettingDialog
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

                    "alwaysShowStorySaveIcon" -> (preference as SwitchPreference).isChecked =
                        TelegramHandler.settings.alwaysShowStorySaveIcon

                    "removeArchiveFolder" -> (preference as SwitchPreference).isChecked =
                        TelegramHandler.settings.removeArchiveFolder

                    "alwaysShowDownloadManager" -> (preference as SwitchPreference).isChecked =
                        TelegramHandler.settings.alwaysShowDownloadManager

                    "hidePhoneNumber" -> (preference as SwitchPreference).isChecked =
                        TelegramHandler.settings.hidePhoneNumber

                    "hidePhoneNumberForSelfOnly" -> (preference as SwitchPreference).apply {
                        isChecked =
                            TelegramHandler.settings.hidePhoneNumberForSelfOnly
                        isEnabled = TelegramHandler.settings.hidePhoneNumber
                    }

                    "hideFloatFab" -> (preference as SwitchPreference).isChecked =
                        TelegramHandler.settings.hideFloatFab
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

    private fun getContentView(): View {
        prefScreen = PreferenceScreen::class.java.newInstAs(context, null)
        listView = ListView(context)

        prefScreen.run {
            switchPreference("总开关", "enabled") {
                setDefaultValue(true)
            }

            category("更改默认行为") {
                switchPreference(
                    "自动勾选删除",
                    "autoCheckDeleteMessageOption",
                    "在私聊中删除消息时自动勾选为对方删除消息"
                )
                switchPreference(
                    "自动取消分享手机号",
                    "autoUncheckSharePhoneNumber",
                    "添加联系人自动取消勾选分享手机号"
                )
                switchPreference(
                    "头像列表默认当前头像",
                    "avatarPageScrollToCurrent",
                    "个人资料头像如果存在多个且当前头像非第一个时，下拉展示完整头像列表时自动切到当前头像（原行为是总是切到第一个）"
                )
                switchPreference(
                    "hashtag 总是搜索本频道",
                    "defaultSearchTab",
                )
                switchPreference(
                    "默认发送高清晰度图像",
                    "sendImageWithHighQualityByDefault",
                    "需要 11.12.0 (5997) 或更高版本，并移除图片预览左下角的高清标志"
                )
                switchPreference(
                    "总是允许保存动态图片",
                    "alwaysShowStorySaveIcon"
                )
                switchPreference(
                    "总是显示下载管理器",
                    "alwaysShowDownloadManager"
                )
            }

            category("隐私") {
                switchPreference(
                    "默认隐藏电话号码",
                    "hidePhoneNumber",
                    "隐藏主页抽屉的电话号码，点按文本切换显示状态；隐藏资料页面的电话号码，点按右侧按钮切换显示状态"
                )
                switchPreference(
                    "默认只隐藏自己的隐藏电话号码",
                    "hidePhoneNumberForSelfOnly",
                    "其他人的电话号码默认显示（如有），也可隐藏，需启用「默认隐藏电话号码」"
                )
            }

            category("忽略权限") {
                switchPreference(
                    "忽略联系人权限",
                    "contactPermission",
                    "打开联系人页面不再请求联系人权限"
                )
                switchPreference(
                    "打开 apk 无需请求权限",
                    "fakeInstallPermission",
                    "打开 apk 时不检查是否有 REQUEST_INSTALL_PACKAGE 权限，这并不会实际给予权限"
                )
                switchPreference(
                    "无需谷歌地图",
                    "noGoogleMaps",
                    "不再提示安装谷歌地图"
                )
            }

            category("Emoji 和 Sticker") {
                switchPreference(
                    "自定义 emoji 映射",
                    "customEmojiMapping",
                )
                preference(
                    "自定义 emoji 映射配置",
                    "customEmojiMappingConfig",
                )
                switchPreference(
                    "查看 Emoji 和 Sticker Pack 创建者",
                    "emojiStickerMenu",
                    "在 Emoji 和 Sticker Pack 列表对话框的菜单增加查看创建者"
                )
            }

            category("链接优化") {
                switchPreference(
                    "阻止重复打开链接",
                    "fixHasAppToOpen",
                )
                switchPreference(
                    "修复链接的意外字符",
                    "openLinkDialog",
                    "如果打开的链接包含意外字符（比如可能链接和后面的文字无空格），则总是弹出对话框，并可以点击fix按钮打开去除这些意外字符的链接"
                )
            }

            category("其他") {
                switchPreference(
                    "消息编辑框禁用语音或相机按钮",
                    "disableVoiceOrCameraButton",
                )
                switchPreference(
                    "地图自定义经纬度",
                    "customMapPosition",
                    "长按定位按钮打开对话框"
                )
                switchPreference(
                    "at 列表长按使用无用户名 at",
                    "longClickMention",
                    "at 列表中，长按某人以使用无用户名的方式 at 此人"
                )
                switchPreference(
                    "标记双向联系人",
                    "mutualContact",
                    "在联系人列表标记你的双向联系人（↑↓）"
                )
                switchPreference(
                    "移除下拉归档",
                    "removeArchiveFolder"
                )
                switchPreference(
                    "移除主页浮动按钮",
                    "hideFloatFab"
                )
            }
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

    companion object {

        fun show(context: Context) {
            val themedContext = ContextThemeWrapper(
                context,
                android.R.style.Theme_DeviceDefault_DayNight
            )
            try {
                SettingDialog(themedContext).show()
            } catch (_: Resources.NotFoundException) {
                AlertDialog.Builder(themedContext)
                    .setTitle("需要重启")
                    .setMessage("由于加载资源失败，需要重启应用以显示设置界面。")
                    .setPositiveButton("重启") { _, _ ->
                        restartApplication(context.findBaseActivity())
                    }.show()
            }
        }
    }

    init {
        val activity = context.findBaseActivity()
        activity.addModuleAssets(Entry.modulePath)

        setView(getContentView())
        setTitle("MyInjector")
        setNegativeButton("返回", null)
        setPositiveButton("重启") { _, _ ->
            restartApplication(activity)
        }
    }
}

class Settings : IHook() {
    override fun onHook() {
        val drawerLayoutAdapterClass = findClass("org.telegram.ui.Adapters.DrawerLayoutAdapter")
        val itemClass = findClass("org.telegram.ui.Adapters.DrawerLayoutAdapter\$Item")

        drawerLayoutAdapterClass.hookAllAfter("resetItems") { param ->
            val items = param.thisObject.getObjAs<ArrayList<Any?>>("items")
            val settingsIdx = items.indexOfFirst { it != null && itemClass.isInstance(it) && it.getObjAs<Int>("id") == 8 }
            val settingsItem = items[settingsIdx]
            // getItemViewType() return 3 by default
            val mySettingsItem =
                itemClass.newInst(114514, "MyInjector", settingsItem.getObjAs<Int>("icon"))
            mySettingsItem.setObj("listener", object : View.OnClickListener {
                override fun onClick(v: View) {
                    SettingDialog.show(v.context)
                }
            })
            items.add(settingsIdx + 1, mySettingsItem)
        }
    }
}
