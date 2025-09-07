package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.setObjS
import io.github.a13e300.myinjector.logD
import io.github.a13e300.myinjector.logE

class DisableMiuiVarFont : DynHook() {
    override fun isFeatureEnabled() =
        TelegramHandler.settings.disableMiuiVarFonts && needsDisableMiuiVarFonts

    override fun onHook() {
        // https://github.com/YuKongA/DisableMiFontOverlay/blob/29debc2795666a52651814e7c16716a2df2e1a58/module/jni/module.cpp#L44-L58
        runCatching {
            Class.forName("miui.util.font.FontSettings")
                .setObjS("HAS_MIUI_VAR_FONT", false)
            logD("set HAS_MIUI_VAR_FONT = false")
        }.onFailure {
            logE("set HAS_MIUI_VAR_FONT", it)
        }
    }

    companion object {
        private fun needDisableMiuiVarFonts() = runCatching {
            val clz = try {
                Class.forName("miui.util.font.FontSettings")
            } catch (_: ClassNotFoundException) {
                return@runCatching false
            }

            try {
                clz.getDeclaredField("HAS_MIUI_VAR_FONT")
            } catch (_: NoSuchFileException) {
                return@runCatching false
            }

            return@runCatching true
        }.onFailure {
            logE("needDisableMiuiVarFonts", it)
        }.getOrDefault(false)

        val needsDisableMiuiVarFonts by lazy { needDisableMiuiVarFonts() }
    }
}