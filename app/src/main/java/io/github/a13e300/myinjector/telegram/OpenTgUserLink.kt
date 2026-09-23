package io.github.a13e300.myinjector.telegram

import android.content.Intent
import android.net.Uri
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD

class OpenTgUserLink : MyDynHook("openTgUserLink") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.openTgUserLink

    private fun deobf(creator: ObfsTableCreator): ObfsTable {

        creator.create("LaunchActivityHandleIntent") { bridge ->
            bridge.findMethod {
                matcher {
                    // this can't be obfuscated
                    declaredClass("org.telegram.ui.LaunchActivity")
                    usingEqStrings("currentAccount", "voip", "voip_answer")
                }
            }.single().toObfsInfo()
        }
        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val LaunchActivityHandleIntent = table["LaunchActivityHandleIntent"]!!
        findClass("org.telegram.ui.LaunchActivity")
            .declaredMethods
            .filter { it.name == LaunchActivityHandleIntent.memberName }
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