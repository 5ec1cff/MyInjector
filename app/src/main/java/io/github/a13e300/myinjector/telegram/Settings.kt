package io.github.a13e300.myinjector.telegram

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import io.github.a13e300.myinjector.SettingDialog
import io.github.a13e300.myinjector.ui.settings.*
import io.github.a13e300.myinjector.addSettingsIntentInterceptor
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.ui.ModernInjectedDialogAction
import io.github.a13e300.myinjector.ui.modernInjectedMessageView
import io.github.a13e300.myinjector.ui.modernInjectedScrollContent
import io.github.a13e300.myinjector.ui.showModernInjectedDialog
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier

class TgSettingsDialog(context: Context) : SettingDialog(context) {
    override val dialogTitle = "Telegram 设置"
    override fun onSettingChanged(setting: SettingItem, newValue: Any?): Boolean {
        val settings = TelegramHandler.settings.toBuilder()
        val v = newValue as Boolean
        when (setting.key) {
            "enabled" -> settings.disabled = !v

            "showMsgId" ->
                settings.showMsgId = v

            "antiAntiCopy" ->
                settings.antiAntiCopy = v

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

            "showExactLastSeenTime" ->
                settings.showExactLastSeenTime = v

            "channelDetailNumbers" ->
                settings.channelDetailNumbers = v

            "showIdInProfile" ->
                settings.showIdInProfile = v

            "showDCInProfile" ->
                settings.showDCInProfile = v

            "historicalNewsOption" ->
                settings.historicalNewsOption = v

            "repeatMessage" ->
                settings.repeatMessage = v

            "addSubItemChannel" ->
                settings.addSubItemChannel = v

            "addInfoContainer" ->
                settings.addInfoContainer = v

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
                settingGroup.find("hidePhoneNumberForSelfOnly").isEnabled = v
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
                settingGroup.find("disableProfileAvatarBlurExtendAvatar").isEnabled = v
            }

            "disableProfileAvatarBlurExtendAvatar" ->
                settings.disableProfileAvatarBlurExtendAvatar = v
        }
        TelegramHandler.updateSettings(settings.build())
        return true
    }

    override fun onSettingClicked(setting: SettingItem): Boolean {
        if (setting.key == "customEmojiMappingConfig") {
            CustomEmojiMapping.importEmojiMap(activityCtx)
            return true
        }
        return false
    }

    override fun createSettings(settingGroup: SettingsGroup) {
        settingGroup.run {
            toggle("总开关", "enabled") {
                isChecked = true
            }

            group("聊天") {
                toggle(
                    "查看发送者历史消息",
                    "historicalNewsOption",
                    "在消息菜单增加「查看历史消息」，按发送者筛选当前聊天；长按菜单项复制发送者 ID"
                )
                toggle(
                    "复读消息",
                    "repeatMessage",
                    "在消息菜单增加「复读」，发送到当前聊天；禁止转发或话题内仅支持重发文字、贴纸"
                )
                toggle(
                    "显示消息 ID",
                    "showMsgId",
                    "在消息时间处显示消息 ID 、管理员头衔"
                )
                toggle(
                    "反反复制",
                    "antiAntiCopy",
                    "允许在禁止复制的群聊复制和保存内容，并仍然阻止转发（因为不可能成功）"
                )
                toggle(
                    "阻止切换频道",
                    "prohibitChannelSwitching",
                    "阻止在频道底部上拉时切换到其他频道"
                )
                toggle(
                    "消息编辑框禁用语音或相机按钮",
                    "disableVoiceOrCameraButton",
                )
                toggle(
                    "at 列表长按使用无用户名 at",
                    "longClickMention",
                    "at 列表中，长按某人以使用无用户名的方式 at 此人"
                )
                toggle(
                    "保存私密媒体",
                    "saveSecretMedia"
                )
            }

            group("更改默认行为") {
                toggle(
                    "自动勾选删除",
                    "autoCheckDeleteMessageOption",
                    "在私聊中删除消息时自动勾选为对方删除消息"
                )
                toggle(
                    "自动取消分享手机号",
                    "autoUncheckSharePhoneNumber",
                    "添加联系人自动取消勾选分享手机号"
                )
                toggle(
                    "头像列表默认当前头像",
                    "avatarPageScrollToCurrent",
                    "个人资料头像如果存在多个且当前头像非第一个时，下拉展示完整头像列表时自动切到当前头像（原行为是总是切到第一个）"
                )
                toggle(
                    "hashtag 总是搜索本频道",
                    "defaultSearchTab",
                )
                toggle(
                    "默认发送高清晰度图像",
                    "sendImageWithHighQualityByDefault",
                    "需要 11.12.0 (5997) 或更高版本，并移除图片预览左下角的高清标志"
                )
                toggle(
                    "总是允许保存动态图片",
                    "alwaysShowStorySaveIcon"
                )
                toggle(
                    "总是显示下载管理器",
                    "alwaysShowDownloadManager"
                )
            }

            group("隐私") {
                toggle(
                    "默认隐藏电话号码",
                    "hidePhoneNumber",
                    "隐藏主页抽屉的电话号码，点按文本切换显示状态；隐藏资料页面的电话号码，点按右侧按钮切换显示状态"
                )
                toggle(
                    "默认只隐藏自己的隐藏电话号码",
                    "hidePhoneNumberForSelfOnly",
                    "其他人的电话号码默认显示（如有），也可隐藏，需启用「默认隐藏电话号码」"
                )
            }

            group("忽略权限") {
                toggle(
                    "忽略联系人权限",
                    "contactPermission",
                    "打开联系人页面不再请求联系人权限"
                )
                toggle(
                    "打开 apk 无需请求权限",
                    "fakeInstallPermission",
                    "打开 apk 时不检查是否有 REQUEST_INSTALL_PACKAGE 权限，这并不会实际给予权限"
                )
                toggle(
                    "无需谷歌地图",
                    "noGoogleMaps",
                    "不再提示安装谷歌地图"
                )
            }

            group("Emoji 和 Sticker") {
                toggle(
                    "自定义 emoji 映射",
                    "customEmojiMapping",
                )
                action(
                    "自定义 emoji 映射配置",
                    "customEmojiMappingConfig",
                )
                toggle(
                    "查看 Emoji 和 Sticker Pack 创建者",
                    "emojiStickerMenu",
                    "在 Emoji 和 Sticker Pack 列表对话框的菜单增加查看创建者"
                )
                toggle(
                    "使用系统 emoji",
                    "useSystemEmoji",
                )
            }

            group("链接优化") {
                toggle(
                    "阻止重复打开链接",
                    "fixHasAppToOpen",
                )
                toggle(
                    "修复链接的意外字符",
                    "openLinkDialog",
                    "如果打开的链接包含意外字符（比如可能链接和后面的文字无空格），则总是弹出对话框，并可以点击fix按钮打开去除这些意外字符的链接"
                )
                toggle(
                    "打开 tg 用户链接",
                    "openTgUserLink",
                    "将 tg://user?id=xxx 转换成 tg://openmessage?user_id=xxx 并打开"
                )
                toggle(
                    "在私聊中复制消息链接",
                    "copyPrivateChatLink",
                    "链接仅对自己有效，tg://openmessage?user_id=xxx&message_id=yyy"
                )
            }

            group("其他") {
                toggle(
                    "打开关联频道",
                    "addSubItemChannel",
                    "在关联讨论群的资料页菜单增加「打开频道」；重新打开资料页生效"
                )
                toggle(
                    "群信息查询",
                    "addInfoContainer",
                    "显示群信息入口，允许非管理员查询可获取的管理员、成员和群权限；重新打开资料页生效"
                )
                toggle(
                    "资料页显示 ID",
                    "showIdInProfile",
                    "显示用户、群组、频道或话题 ID，点击复制；重新打开资料页生效"
                )
                toggle(
                    "资料页显示 DC",
                    "showDCInProfile",
                    "根据头像显示数据中心，点击复制；无头像或资料未加载时显示未知，重新打开资料页生效"
                )
                toggle(
                    "最后上线时间精确到秒",
                    "showExactLastSeenTime",
                )
                toggle(
                    "频道显示准确关注人数",
                    "channelDetailNumbers",
                )
                toggle(
                    "地图自定义经纬度",
                    "customMapPosition",
                    "长按定位按钮打开对话框"
                )
                toggle(
                    "标记双向联系人",
                    "mutualContact",
                    "在联系人列表标记你的双向联系人（↑↓）"
                )
                toggle(
                    "移除下拉归档",
                    "removeArchiveFolder"
                )
                toggle(
                    "移除主页浮动按钮",
                    "hideFloatFab"
                )
                if (DisableMiuiVarFont.needsDisableMiuiVarFonts) {
                    toggle(
                        "禁用 MiuiVarFonts",
                        "disableMiuiVarFonts",
                        "修复一些 UI 显示问题（重启生效）"
                    )
                }
                toggle(
                    "禁用资料头像模糊",
                    "disableProfileAvatarBlur"
                )
                toggle(
                    "扩展头像显示范围到操作按钮下",
                    "disableProfileAvatarBlurExtendAvatar",
                    "需要同时开启「禁用资料头像模糊」才能生效"
                )
            }
        }
    }

    override fun loadSetting(setting: SettingItem) {

        when (setting.key) {
            "enabled" -> (setting as ToggleSetting).isChecked =
                !TelegramHandler.settings.disabled

            "antiAntiCopy" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.antiAntiCopy

            "showMsgId" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.showMsgId

            "showIdInProfile" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.showIdInProfile

            "showDCInProfile" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.showDCInProfile

            "historicalNewsOption" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.historicalNewsOption

            "repeatMessage" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.repeatMessage

            "addSubItemChannel" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.addSubItemChannel

            "addInfoContainer" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.addInfoContainer

            "prohibitChannelSwitching" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.prohibitChannelSwitching

            "autoCheckDeleteMessageOption" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.autoCheckDeleteMessageOption

            "autoUncheckSharePhoneNumber" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.autoUncheckSharePhoneNumber

            "avatarPageScrollToCurrent" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.avatarPageScrollToCurrent

            "contactPermission" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.contactPermission

            "customEmojiMapping" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.customEmojiMapping

            "customEmojiMappingConfig" -> setting.summary =
                "加载了${CustomEmojiMapping.emotionMap.map.size}条映射规则"

            "showExactLastSeenTime" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.showExactLastSeenTime

            "channelDetailNumbers" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.channelDetailNumbers

            "customMapPosition" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.customMapPosition

            "defaultSearchTab" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.defaultSearchTab

            "disableVoiceOrCameraButton" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.disableVoiceOrCameraButton

            "emojiStickerMenu" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.emojiStickerMenu

            "useSystemEmoji" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.useSystemEmoji

            "fakeInstallPermission" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.fakeInstallPermission

            "fixHasAppToOpen" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.fixHasAppToOpen

            "longClickMention" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.longClickMention

            "mutualContact" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.mutualContact

            "noGoogleMaps" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.noGoogleMaps

            "openLinkDialog" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.openLinkDialog

            "sendImageWithHighQualityByDefault" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.sendImageWithHighQualityByDefault

            "alwaysShowStorySaveIcon" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.alwaysShowStorySaveIcon

            "removeArchiveFolder" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.removeArchiveFolder

            "alwaysShowDownloadManager" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.alwaysShowDownloadManager

            "hidePhoneNumber" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.hidePhoneNumber

            "hidePhoneNumberForSelfOnly" -> (setting as ToggleSetting).apply {
                isChecked =
                    TelegramHandler.settings.hidePhoneNumberForSelfOnly
                isEnabled = TelegramHandler.settings.hidePhoneNumber
            }

            "hideFloatFab" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.hideFloatFab

            "openTgUserLink" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.openTgUserLink

            "copyPrivateChatLink" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.copyPrivateChatLink

            "saveSecretMedia" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.saveSecretMedia

            "disableMiuiVarFonts" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.disableMiuiVarFonts

            "disableProfileAvatarBlur" -> (setting as ToggleSetting).isChecked =
                TelegramHandler.settings.disableProfileAvatarBlur

            "disableProfileAvatarBlurExtendAvatar" -> (setting as ToggleSetting).apply {
                isChecked = TelegramHandler.settings.disableProfileAvatarBlurExtendAvatar
                isEnabled = TelegramHandler.settings.disableProfileAvatarBlur
            }
        }

        if (setting.key in TelegramHandler.hookErrors) {
            val newSummary = SpannableStringBuilder(setting.summary ?: "")
                .apply { if (isNotEmpty()) append("\n") }
                .append(
                    "存在问题",
                    ForegroundColorSpan(Color.RED),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            setting.summary = newSummary
        }
    }

    override fun onConfigureActions(actions: MutableList<ModernInjectedDialogAction>) {
        if (TelegramHandler.hookErrors.isEmpty()) return
        val errors = TelegramHandler.hookErrors.map {
            "${it.key}:\n${it.value}"
        }.joinToString("\n")
        actions.add(
            0,
            ModernInjectedDialogAction("查看错误", dismissAfterClick = false) {
                showModernInjectedDialog(
                    context,
                    "错误详情",
                    modernInjectedScrollContent(
                        context,
                        modernInjectedMessageView(context, errors),
                        0.45f,
                    ),
                    listOf(
                        // TODO: share
                        /*
                        ModernInjectedDialogAction("分享") {
                            thread {
                                runCatching {
                                    val f = File(context.filesDir, "myinjector-error.txt")
                                    f.writeText(errors)
                                    logD("wrote file $f")
                                    val uri = XhsHandler.fileProviderClass.callS(
                                        "getUriForFile",
                                        context,
                                        "com.xingin.xhs.provider",
                                        f
                                    ) as Uri
                                    logD("uri $uri")
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND)
                                                .putExtra(Intent.EXTRA_STREAM, uri)
                                                .setType("text/plain"),
                                            ""
                                        )
                                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                }.onFailure { t ->
                                    logE("share failed: ", t)
                                    runCatching {
                                        activityCtx.findBaseActivity().runOnUiThread {
                                            Toast.makeText(
                                                context,
                                                "分享失败，请尝试复制",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        },*/
                        ModernInjectedDialogAction("复制") {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("", errors))
                        },
                        ModernInjectedDialogAction("关闭"),
                    ),
                )
            },
        )
    }
}

class Settings : IHook() {
    fun deobf(
        creator: ObfsTableCreator
    ): ObfsTable {

        val settingsFragment = creator.create("SettingsActivity") { bridge ->
            bridge.getClassData("org.telegram.ui.SettingsActivity")?.let { return@create it.toObfsInfo() }
            bridge.findMethod {
                matcher {
                    // NaGram removed "store bundled ", so we use this key word in onFragmentCreated
                    usingStrings("hasMainTabs")
                    declaredClass {
                        superClass = creator.obfsTable["BaseFragment"]!!.className
                    }
                }
                // only SettingsActivity.onFragmentCreated has only 1 string ref
            }.single { it.usingStrings.size == 1 }.declaredClass!!.toObfsInfo()
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
            bridge.getClassData("org.telegram.ui.Components.UItem\$UItemFactory")?.fields?.singleOrNull { it.name == "viewType" && it.typeName == "int" }
                ?.let { return@create it.toObfsInfo() }
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
            telegramNamedMethod(bridge, settingsFragment.className, "fillItems", "java.util.ArrayList", "org.telegram.ui.Components.UniversalAdapter")
                ?.let { return@create it.toObfsInfo() }
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
            telegramNamedMethod(bridge, uItemOfFactory.className, "asShadow", "java.lang.CharSequence")
                ?.let { return@create it.toObfsInfo() }
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
            bridge.getClassData(uItemOfFactory.className)?.fields?.singleOrNull { it.name == "id" && it.typeName == "int" }
                ?.let { return@create it.toObfsInfo() }
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
