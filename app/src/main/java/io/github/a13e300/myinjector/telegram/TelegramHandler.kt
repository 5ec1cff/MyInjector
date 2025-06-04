package io.github.a13e300.myinjector.telegram

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.a13e300.myinjector.arch.IHook

class TelegramHandler : IHook() {
    override fun onHook(param: LoadPackageParam) {
        subHook(OpenLinkDialog())
        subHook(MutualContact())
        subHook(ContactPermission())
        subHook(AutoCheckDeleteMessageOption())
        subHook(AutoUncheckSharePhoneNumber())
        subHook(DisableVoiceOrCameraButton())
        subHook(LongClickMention())
        subHook(FakeInstallPermission())
        subHook(NoGoogleMaps())
        subHook(CustomEmojiMapping())
        subHook(EmojiStickerMenu())
        subHook(FixHasAppToOpen())
        subHook(DefaultSearchTab())
        subHook(CustomMapPosition())
        subHook(AvatarPagerScrollToCurrent())
    }
}
