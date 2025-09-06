package io.github.a13e300.myinjector.telegram

import android.content.Intent
import android.net.Uri
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.logD

class OpenTgUserLink : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.openTgUserLink

    override fun onHook() {
        findClass("org.telegram.ui.LaunchActivity")
            .declaredMethods
            .filter { it.name == "handleIntent" }
            .maxBy { it.parameterCount }
            .hookBefore { param ->
                val intent = param.args[0] as? Intent ?: return@hookBefore
                val data = intent.data ?: return@hookBefore
                if (data.scheme == "tg" && data.authority == "user") {
                    val userId = data.getQueryParameter("id") ?: return@hookBefore
                    logD("replace old url $data")
                    val newUrl = Uri.parse("tg://openmessage?user_id=$userId")
                    logD("new Url $newUrl")
                    intent.data = newUrl
                }
            }
    }
}