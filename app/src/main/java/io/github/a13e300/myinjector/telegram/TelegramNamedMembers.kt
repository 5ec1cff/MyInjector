package io.github.a13e300.myinjector.telegram

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData

/** 未混淆构建优先使用完整签名；找不到时沿用 R8 特征查找。 */
internal fun telegramNamedMethod(bridge: DexKitBridge, owner: String, name: String, vararg params: String): MethodData? =
    bridge.getClassData(owner)?.methods?.singleOrNull { it.name == name && it.paramTypeNames == params.toList() }

/** 对未混淆成员逐个验证，缺失时明确报告具体成员。 */
internal class TelegramNamedLookup(val bridge: DexKitBridge) {
    val members = linkedMapOf<String, String>()
    fun field(key: String, owner: String, name: String = key) {
        members[key] = bridge.getClassData(owner)!!.fields.single { it.name == name }.descriptor
    }
    fun method(key: String, owner: String, name: String, vararg params: String) {
        members[key] = requireNotNull(telegramNamedMethod(bridge, owner, name, *params)) { "$owner.$name(${params.joinToString()})" }.descriptor
    }
}

internal fun namedProfileBlur(bridge: DexKitBridge): Map<String, String>? {
    val pa = "org.telegram.ui.ProfileActivity"
    val top = "$pa\$TopView"
    val overlays = "$pa\$OverlaysView"
    val actions = "org.telegram.ui.Components.ProfileActionsView"
    val blur = "org.telegram.ui.Components.ProfileGalleryBlurView"
    if (telegramNamedMethod(bridge, top, "updateBackgroundPaint") == null) return null
    return TelegramNamedLookup(bridge).apply {
        members["galleryClass"] = "org.telegram.ui.Components.ProfileGalleryView"
        for (key in listOf("avatarsViewPager", "actionsView", "overlaysView", "isPulledDown", "currentExpandAnimatorValue", "avatarContainer", "avatarScale", "openAnimationInProgress", "playProfileAnimation")) field(key, pa)
        field("topProfileActivity", top, "this\$0")
        field("overlaysProfileActivity", overlays, "this\$0")
        for (key in listOf("bottomOverlayRect", "bottomOverlayGradient")) field(key, overlays)
        field("blurActionsView", blur, "actionsView")
        field("blurMusicView", blur, "musicView")
        field("actionsPaint", actions, "paint")
        field("actionsColor", actions, "color")
        field("actionsHasColorById", actions, "hasColorById")
        field("actionsRadialGradient", actions, "radialGradient")
        method("blurDraw", blur, "draw", "android.graphics.Canvas", "$pa\$AvatarImageView", "float", "float", "boolean", "float", "float")
        method("topSetBackgroundColor", top, "setBackgroundColor", "int")
        method("topUpdateBackgroundPaint", top, "updateBackgroundPaint")
        method("actionsCreateColorShader", actions, "createColorShader")
        // 新版 setActionsColor 多出文字颜色参数；使用现有字段／shader 路径，保留原文字色。
        members["actionsSetActionsColor"] = ""
        method("updateExtraViews", pa, "updateExtraViews", "float")
        method("overlaysOnSizeChanged", overlays, "onSizeChanged", "int", "int", "int", "int")
        method("getActionsExtraHeight", pa, "getActionsExtraHeight")
        method("setAvatarExpandProgress", pa, "setAvatarExpandProgress", "float")
        method("needLayout", pa, "needLayout", "boolean")
        method("fixAvatarImageInCenter", pa, "fixAvatarImageInCenter")
        method("dpf2", "org.telegram.messenger.AndroidUtilities", "dpf2", "float")
        method("musicDrawingBlur", "org.telegram.ui.Components.ProfileMusicView", "drawingBlur", "boolean")
        members["blurSuggestionView"] = ""
        members["suggestionDrawingBlur"] = ""
    }.members
}

internal fun namedOpenLink(bridge: DexKitBridge): Map<String, String>? {
    val chat = "org.telegram.ui.ChatActivity"
    if (telegramNamedMethod(bridge, chat, "processExternalUrl", "int", "java.lang.String", "android.text.style.CharacterStyle", "org.telegram.ui.Cells.ChatMessageCell", "boolean", "boolean") == null) return null
    val dialog = "org.telegram.ui.ActionBar.AlertDialog"
    return TelegramNamedLookup(bridge).apply {
        method("external", chat, "processExternalUrl", "int", "java.lang.String", "android.text.style.CharacterStyle", "org.telegram.ui.Cells.ChatMessageCell", "boolean", "boolean")
        method("alert", "org.telegram.ui.Components.AlertsCreator", "showOpenUrlAlert", "android.content.Context", "java.lang.String", "boolean", "boolean", "boolean", "boolean", "long", "org.telegram.messenger.browser.Browser\$Progress", "org.telegram.tgnet.TLRPC\$WebPage", "org.telegram.ui.ActionBar.Theme\$ResourcesProvider")
        method("open", "org.telegram.messenger.browser.Browser", "openUrl", "android.content.Context", "android.net.Uri", "boolean", "boolean", "org.telegram.messenger.browser.Browser\$Progress")
        method("show", dialog, "show")
        field("neutralText", dialog, "neutralButtonText")
        field("neutralListener", dialog, "neutralButtonListener")
        field("message", dialog)
    }.members
}

internal fun namedSecretMedia(bridge: DexKitBridge): Map<String, String>? {
    val chat = "org.telegram.ui.ChatActivity"
    val pv = "org.telegram.ui.PhotoViewer"
    if (telegramNamedMethod(bridge, chat, "getMessageType", "org.telegram.messenger.MessageObject") == null) return null
    return TelegramNamedLookup(bridge).apply {
        method("fill", chat, "fillMessageMenu", "org.telegram.messenger.MessageObject", "java.util.ArrayList", "java.util.ArrayList", "java.util.ArrayList")
        method("messageType", chat, "getMessageType", "org.telegram.messenger.MessageObject")
        method("options", chat, "processSelectedOption", "int")
        field("selected", chat, "selectedObject")
        method("open", pv, "openPhoto", "org.telegram.messenger.MessageObject", chat, "long", "long", "long", "$pv\$PhotoViewerProvider")
        method("instance", pv, "getInstance")
        method("parent", pv, "setParentActivity", "android.app.Activity", "org.telegram.ui.ActionBar.BaseFragment", "org.telegram.ui.ActionBar.Theme\$ResourcesProvider")
        method("theme", chat, "getResourceProvider")
        field("provider", chat, "photoViewerProvider")
        method("topic", chat, "getTopicId")
        field("dialog", chat, "dialog_id")
        field("mergeDialog", chat, "mergeDialogId")
        field("gallery", pv, "galleryButton")
        field("gap", pv, "searchGap")
        method("switch", pv, "setImageIndex", "int", "boolean", "boolean", "boolean")
    }.members
}
