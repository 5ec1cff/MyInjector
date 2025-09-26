@file:Suppress("DEPRECATION")
package io.github.a13e300.myinjector.telegram

import android.content.Context
import android.preference.Preference
import android.preference.PreferenceScreen
import android.preference.SwitchPreference
import android.view.View
import io.github.a13e300.myinjector.SettingDialog
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.category
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.preference
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.switchPreference

class TgSettingsDialog(context: Context) : SettingDialog(context) {
    override fun onPrefChanged(preference: Preference, newValue: Any?): Boolean {
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

            "openTgUserLink" ->
                settings.openTgUserLink = v

            "copyPrivateChatLink" ->
                settings.copyPrivateChatLink = v

            "saveSecretMedia" ->
                settings.saveSecretMedia = v

            "disableMiuiVarFonts" ->
                settings.disableMiuiVarFonts = v

            "disableProfileAvatarBlur" ->
                settings.disableProfileAvatarBlur = v

            "disableProfileAvatarBlurExtendAvatar" ->
                settings.disableProfileAvatarBlurExtendAvatar = v
        }
        TelegramHandler.updateSettings(settings.build())
        return true
    }

    override fun onPrefClicked(preference: Preference): Boolean {
        if (preference.key == "customEmojiMappingConfig") {
            CustomEmojiMapping.importEmojiMap(context)
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
                    TgSettingsDialog(v.context).show()
                }
            })
            items.add(settingsIdx + 1, mySettingsItem)
        }
    }
}
