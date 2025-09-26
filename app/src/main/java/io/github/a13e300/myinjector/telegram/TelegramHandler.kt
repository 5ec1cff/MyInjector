package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.TelegramSettings
import io.github.a13e300.myinjector.arch.DynHookManager
import java.io.InputStream
import java.io.OutputStream

object TelegramHandler : DynHookManager<TelegramSettings>() {
    override fun isEnabled(): Boolean = !settings.disabled

    override fun onHook() {
        super.onHook()

        subHook(OpenLinkDialog())
        subHook(MutualContact())
        subHook(ContactPermission())
        subHook(AutoCheckDeleteMessageOption())
        subHook(AutoUncheckSharePhoneNumber())
        subHook(DisableVoiceOrCameraButton())
        subHook(LongClickMention())
        subHook(FakeInstallPermission())
        subHook(NoGoogleMaps())
        subHook(CustomEmojiMapping)
        subHook(EmojiStickerMenu())
        subHook(FixHasAppToOpen())
        subHook(DefaultSearchTab())
        subHook(CustomMapPosition())
        subHook(AvatarPagerScrollToCurrent())
        subHook(SendImageWithHighQualityByDefault())
        subHook(HidePhoneNumber())
        subHook(Settings())
        subHook(AlwaysShowStorySaveIcon())
        subHook(RemoveArchiveFolder())
        subHook(AlwaysShowDownloadManager())
        subHook(HideFloatFab())
        subHook(OpenTgUserLink())
        subHook(CopyPrivateChatLink())
        subHook(SaveSecretImage())
        subHook(DisableMiuiVarFont())
        subHook(DisableProfilerAvatarBlur())
    }

    override fun onReadSettings(input: InputStream): TelegramSettings =
        TelegramSettings.parseFrom(input)

    override fun defaultSettings(): TelegramSettings = TelegramSettings.getDefaultInstance()

    override fun onWriteSettings(output: OutputStream, setting: TelegramSettings) {
        settings.writeTo(output)
    }
}
