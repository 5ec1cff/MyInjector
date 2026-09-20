package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.TelegramSettings
import io.github.a13e300.myinjector.arch.DynHookManager
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import java.io.InputStream
import java.io.OutputStream

object TelegramHandler : DynHookManager<TelegramSettings>() {
    override fun isEnabled(): Boolean = !settings.disabled

    private var _creator: ObfsTableCreator? = null
    val creator: ObfsTableCreator
        get() = _creator ?: ObfsTableCreator("tg", 1, appInfo = loadPackageParam.appInfo)
            .also { _creator = it }

    override fun onHook() {
        super.onHook()

        creator.create("BaseFragment") { bridge ->
            bridge.findClass {
                matcher {
                    fields {
                        add {
                            name("fragmentView")
                            type("android.view.View")
                        }
                    }
                }
            }.single().toObfsInfo()
        }

        subHook(Settings())
        subHook(ContactPermission())
        subHook(AutoCheckDeleteMessageOption())
        subHook(CustomEmojiMapping)
        subHook(DisableMiuiVarFont())
        subHook(MutualContact())

        /*
        subHook(StickerLoadGuard())
        subHook(OpenLinkDialog())
        subHook(AutoUncheckSharePhoneNumber())
        subHook(DisableVoiceOrCameraButton())
        subHook(LongClickMention())
        subHook(FakeInstallPermission())
        subHook(NoGoogleMaps())
        subHook(EmojiStickerMenu())
        subHook(FixHasAppToOpen())
        subHook(DefaultSearchTab())
        subHook(CustomMapPosition())
        subHook(AvatarPagerScrollToCurrent())
        subHook(SendImageWithHighQualityByDefault())
        subHook(HidePhoneNumber())
        subHook(AlwaysShowStorySaveIcon())
        subHook(RemoveArchiveFolder())
        subHook(AlwaysShowDownloadManager())
        subHook(HideFloatFab())
        subHook(OpenTgUserLink())
        subHook(CopyPrivateChatLink())
        subHook(SaveSecretImage())
        subHook(DisableProfileAvatarBlur())*/
        _creator?.let {
            it.persist()
            it.close()
            _creator = null
        }
    }

    override fun onReadSettings(input: InputStream): TelegramSettings =
        TelegramSettings.parseFrom(input)

    override fun defaultSettings(): TelegramSettings = TelegramSettings.getDefaultInstance()

    override fun onWriteSettings(output: OutputStream, setting: TelegramSettings) {
        settings.writeTo(output)
    }
}
