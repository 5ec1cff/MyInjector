@file:Suppress("DEPRECATION")
package io.github.a13e300.myinjector.telegram

import android.content.Context
import android.preference.Preference
import android.preference.PreferenceScreen
import android.preference.SwitchPreference
import android.view.View
import io.github.a13e300.myinjector.SettingDialog
import io.github.a13e300.myinjector.addSettingsIntentInterceptor
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.category
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.preference
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.switchPreference
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logE
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier

class TgSettingsDialog(context: Context) : SettingDialog(context) {
    override fun onPrefChanged(preference: Preference, newValue: Any?): Boolean {
        val settings = TelegramHandler.settings.toBuilder()
        val v = newValue as Boolean
        when (preference.key) {
            "enabled" -> settings.disabled = !v

            "showMsgId" ->
                settings.showMsgId = v

            "prohibitChannelSwitching" ->
                settings.prohibitChannelSwitching = v

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

            "useSystemEmoji" ->
                settings.useSystemEmoji = v

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

            "openTgUserLink" ->
                settings.openTgUserLink = v

            "copyPrivateChatLink" ->
                settings.copyPrivateChatLink = v

            "saveSecretMedia" ->
                settings.saveSecretMedia = v

            "disableMiuiVarFonts" ->
                settings.disableMiuiVarFonts = v

            "disableProfileAvatarBlur" -> {
                settings.disableProfileAvatarBlur = v
                prefScreen.findPreference("disableProfileAvatarBlurExtendAvatar").isEnabled = v
            }

            "disableProfileAvatarBlurExtendAvatar" ->
                settings.disableProfileAvatarBlurExtendAvatar = v
        }
        TelegramHandler.updateSettings(settings.build())
        return true
    }

    override fun onPrefClicked(preference: Preference): Boolean {
        if (preference.key == "customEmojiMappingConfig") {
            CustomEmojiMapping.importEmojiMap(activityCtx)
            return true
        }
        return false
    }

    override fun onCreatePref(prefScreen: PreferenceScreen) {
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
                switchPreference(
                    "使用系统 emoji",
                    "useSystemEmoji",
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
                switchPreference(
                    "打开 tg 用户链接",
                    "openTgUserLink",
                    "将 tg://user?id=xxx 转换成 tg://openmessage?user_id=xxx 并打开"
                )
                switchPreference(
                    "在私聊中复制消息链接",
                    "copyPrivateChatLink",
                    "链接仅对自己有效，tg://openmessage?user_id=xxx&message_id=yyy"
                )
            }

            category("其他") {
                switchPreference(
                    "显示消息 ID",
                    "showMsgId",
                    "在消息时间处显示消息 ID 、管理员头衔"
                )
                switchPreference(
                    "阻止切换频道",
                    "prohibitChannelSwitching",
                    "阻止在频道底部上拉时切换到其他频道"
                )
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
                switchPreference(
                    "保存私密媒体",
                    "saveSecretMedia"
                )
                if (DisableMiuiVarFont.needsDisableMiuiVarFonts) {
                    switchPreference(
                        "禁用 MiuiVarFonts",
                        "disableMiuiVarFonts",
                        "修复一些 UI 显示问题（重启生效）"
                    )
                }
                switchPreference(
                    "禁用资料头像模糊",
                    "disableProfileAvatarBlur"
                )
                switchPreference(
                    "扩展头像显示范围到操作按钮下",
                    "disableProfileAvatarBlurExtendAvatar",
                    "需要同时开启「禁用资料头像模糊」才能生效"
                )
            }
        }
    }

    override fun onRetrievePref(preference: Preference) {

        when (preference.key) {
            "enabled" -> (preference as SwitchPreference).isChecked =
                !TelegramHandler.settings.disabled

            "showMsgId" -> (preference as SwitchPreference).isChecked =
                TelegramHandler.settings.showMsgId

            "prohibitChannelSwitching" -> (preference as SwitchPreference).isChecked =
                TelegramHandler.settings.prohibitChannelSwitching

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

            "useSystemEmoji" -> (preference as SwitchPreference).isChecked =
                TelegramHandler.settings.useSystemEmoji

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

            "openTgUserLink" -> (preference as SwitchPreference).isChecked =
                TelegramHandler.settings.openTgUserLink

            "copyPrivateChatLink" -> (preference as SwitchPreference).isChecked =
                TelegramHandler.settings.copyPrivateChatLink

            "saveSecretMedia" -> (preference as SwitchPreference).isChecked =
                TelegramHandler.settings.saveSecretMedia

            "disableMiuiVarFonts" -> (preference as SwitchPreference).isChecked =
                TelegramHandler.settings.disableMiuiVarFonts

            "disableProfileAvatarBlur" -> (preference as SwitchPreference).isChecked =
                TelegramHandler.settings.disableProfileAvatarBlur

            "disableProfileAvatarBlurExtendAvatar" -> (preference as SwitchPreference).apply {
                isChecked = TelegramHandler.settings.disableProfileAvatarBlurExtendAvatar
                isEnabled = TelegramHandler.settings.disableProfileAvatarBlur
            }
        }
    }
}

class Settings : IHook() {
    fun deobf(
        creator: ObfsTableCreator
    ): ObfsTable {

        val settingsFragment = creator.create("SettingsActivity") { bridge ->
            bridge.findClass {
                matcher {
                    usingStrings("store bundled ")
                    superClass = creator.obfsTable["BaseFragment"]!!.className
                }
            }.single().toObfsInfo()
        }

        // it is in fact UItem.ofFactory, since getFactory only has one caller
        val uItemOfFactory = creator.create("UItemOfFactory") { bridge ->
            bridge.findMethod {
                matcher {
                    // "UItemFactory was not setuped: "
                    usingStrings {
                        add("UItemFactory", StringMatchType.StartsWith)
                    }
                }
            }.single().toObfsInfo()
        }

        // so we find the factoryInstance to retrieve the factory
        val factoryInstancesField = creator.create("UItemFieldFactoryInstances") { bridge ->
            bridge.findField {
                matcher {
                    declaredClass = uItemOfFactory.className
                    readMethods {
                        add {
                            name(uItemOfFactory.memberName)
                            declaredClass(uItemOfFactory.className)
                        }
                    }
                    modifiers(Modifier.STATIC)
                    type("java.util.HashMap")
                }
            }.single().toObfsInfo()
        }

        val uItemFactoryViewTypeField = creator.create("UItemFactoryViewTypeField") { bridge ->
            bridge.findField {
                matcher {
                    readMethods {
                        add {
                            name(uItemOfFactory.memberName)
                            declaredClass(uItemOfFactory.className)
                        }
                    }
                    type("int")
                }
            }.single().toObfsInfo()
        }

        val settingsActivityFillItems = creator.create("SettingsActivityFillItems") { bridge ->
            bridge.findMethod {
                matcher {
                    declaredClass(settingsFragment.className)
                    usingStrings("PREMIUM_GRACE")
                }
            }.single().toObfsInfo()
        }

        val settingsActivityOnClick = creator.create("SettingsActivityOnClick") { bridge ->
            bridge.findMethod {
                matcher {
                    declaredClass(settingsFragment.className)
                    addInvoke {
                        name("<init>")
                        declaredClass("org.telegram.ui.UserInfoActivity")
                    }
                }
            }.single().toObfsInfo()
        }

        // called by SettingsActivity.fillItems
        val settingsActivitySettingsCellFactoryOf =
            creator.create("SettingsActivitySettingsCellFactoryOf") { bridge ->
                bridge.findMethod {
                    matcher {
                        addCaller {
                            declaredClass(settingsFragment.className)
                            name(settingsActivityFillItems.memberName)
                        }
                        modifiers(Modifier.STATIC)
                        paramTypes(
                            "int",
                            "int",
                            "int",
                            "int",
                            "java.lang.CharSequence",
                            "java.lang.CharSequence",
                            "java.lang.CharSequence"
                        )
                    }
                }.single().toObfsInfo()
            }

        // also called by fillItems
        val uItemAsShadow = creator.create("UItemAsShadow") { bridge ->
            bridge.findMethod {
                matcher {
                    addCaller {
                        declaredClass(settingsFragment.className)
                        name(settingsActivityFillItems.memberName)
                    }
                    declaredClass(uItemOfFactory.className)
                    modifiers(Modifier.STATIC)
                    paramTypes("java.lang.CharSequence")
                }
            }.single().toObfsInfo()
        }

        // we need UItem.id and UItem.viewType(AdapterWithDiffUtils.Item.viewType)
        // AdapterWithDiffUtils.Item.viewType is the only int field in its declaring class, so we don't need to find it by dexkit
        // we only find the id field by finding the only method write it
        val uItemId = creator.create("UItemId") { bridge ->
            bridge.findField {
                matcher {
                    declaredClass(uItemOfFactory.className)
                    addWriteMethod {
                        declaredClass(uItemOfFactory.className)
                        modifiers(Modifier.STATIC)
                        paramTypes("int", "java.lang.CharSequence")
                    }
                    type("int")
                }
            }.single().toObfsInfo()
        }

        return creator.obfsTable
    }

    @Suppress("UNCHECKED_CAST")
    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        addSettingsIntentInterceptor {
            TgSettingsDialog(it).show()
        }

        // removed after 12.4.0
        findClassOrNull("org.telegram.ui.Adapters.DrawerLayoutAdapter")?.let { drawerLayoutAdapterClass ->
            val itemClass = findClass("org.telegram.ui.Adapters.DrawerLayoutAdapter\$Item")

            drawerLayoutAdapterClass.hookAllAfter("resetItems") { param ->
                val items = param.thisObject.getObjAs<ArrayList<Any?>>("items")
                val settingsIdx = items.indexOfFirst {
                    it != null && itemClass.isInstance(it) && it.getObjAs<Int>("id") == 8
                }
                val settingsItem = items[settingsIdx]
                // getItemViewType() return 3 by default
                val mySettingsItem =
                    itemClass.newInst(114514, "MyInjector", settingsItem.getObjAs<Int>("icon"))
                mySettingsItem.setObj("listener", object : View.OnClickListener {
                    override fun onClick(v: View) {
                        TgSettingsDialog(v.context).show()
                    }
                })
                items.add(settingsIdx + 1, mySettingsItem)
            }
            return
        }

        val settingsActivitySettingsCellFactoryOf = table["SettingsActivitySettingsCellFactoryOf"]!!
        val settingsActivityInfo = table["SettingsActivity"]!!
        val UItemFieldFactoryInstances = table["UItemFieldFactoryInstances"]!!
        val SettingsActivityFillItems = table["SettingsActivityFillItems"]!!
        val UItemAsShadow = table["UItemAsShadow"]!!
        val UItemId = table["UItemId"]!!
        val SettingsActivityOnClick = table["SettingsActivityOnClick"]!!

        findClassOrNull(settingsActivitySettingsCellFactoryOf.className)?.let { settingsCellFactoryClass ->
            val settingsActivity = findClass(settingsActivityInfo.className)
            val uItemClass = findClass(UItemFieldFactoryInstances.className)
            val uItemViewTypeField =
                uItemClass.superclass.declaredFields.single { it.type == Integer.TYPE }
                    .also { it.isAccessible = true }
            val uItemIdField =
                uItemClass.getDeclaredField(UItemId.memberName).also { it.isAccessible = true }
            val viewType by lazy {
                runCatching {
                    // UItem.getFactory(SettingsActivity$SettingCell$Factory.class)
                    (uItemClass.getObjS(UItemFieldFactoryInstances.memberName) as HashMap<*, *>)
                        .get(findClass(settingsActivitySettingsCellFactoryOf.className))
                        .getObj("viewType") as Int
                }.onFailure { t ->
                    logE("get ViewType", t)
                }.getOrDefault(-1)
            }
            val myId = 11451419
            val fillsItemsMethod =
                settingsActivity.declaredMethods.single { it.name == SettingsActivityFillItems.memberName }
            val fillsItemsItemsParamIdx =
                fillsItemsMethod.parameterTypes.indexOfFirst { it == ArrayList::class.java }
            require(fillsItemsItemsParamIdx >= 0)
            val onClickMethod =
                settingsActivity.declaredMethods.single { it.name == SettingsActivityOnClick.memberName }
            val onClickItemParamIdx = onClickMethod.parameterTypes.indexOfFirst { it == uItemClass }
            val onClickMethodIsStatic = Modifier.isStatic(onClickMethod.modifiers)
            require(onClickItemParamIdx >= 0)
            settingsActivity.hookAllAfter(SettingsActivityFillItems.memberName) { param ->
                val items = param.args[fillsItemsItemsParamIdx] as java.util.ArrayList<Any?>
                fun addSettings(idx: Int) {
                    runCatching {
                        val shadow = uItemClass.callS(UItemAsShadow.memberName, null as String?)
                        items.add(idx, shadow)
                    }
                    items.add(
                        idx,
                        settingsCellFactoryClass.callS(
                            settingsActivitySettingsCellFactoryOf.memberName,
                            myId,
                            0,
                            0,
                            0,
                            "MyInjector",
                            "MyInjector Settings",
                            null
                        )
                    )
                }
                runCatching {
                    // always before id == 1
                    val idx =
                        items.indexOfFirst {
                            it != null && uItemIdField.getInt(it) == 1 && uItemViewTypeField.getInt(
                                it
                            ) == viewType
                        }
                            .let { if (it < 0) 0 else it }
                    addSettings(idx)
                }.onFailure {
                    logE("failed to add, use fallback", it)
                    addSettings(0)
                }
            }
            settingsActivity.hookAllAfter(SettingsActivityOnClick.memberName) { param ->
                if (uItemIdField.getInt(param.args[onClickItemParamIdx]) == myId) {
                    val self = if (onClickMethodIsStatic) param.args[0] else param.thisObject
                    TgSettingsDialog(self.call("getContext") as Context).show()
                }
            }
        }
    }
}
