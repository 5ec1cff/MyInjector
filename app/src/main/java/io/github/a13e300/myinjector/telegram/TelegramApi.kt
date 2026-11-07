package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method

internal fun telegramApiClass(loader: ClassLoader, name: String): Class<*> {
    val info = TelegramHandler.creator.create("Api.Class.$name") { bridge ->
        ObfsInfo(telegramApiClass(bridge, name), "")
    }
    return loader.loadClass(info.className)
}

internal fun telegramApiMethod(loader: ClassLoader, owner: String, name: String, vararg params: String): Method {
    val info = TelegramHandler.creator.create("Api.Method.$owner.$name(${params.joinToString()})") { bridge ->
        telegramApiMethod(bridge, owner, name, *params).toObfsInfo()
    }
    return DexMethod(info.descriptor).getMethodInstance(loader).apply { isAccessible = true }
}

internal class TelegramLocale(loader: ClassLoader) {
    private val getString = telegramApiMethod(loader, "org.telegram.messenger.LocaleController", "getString", "java.lang.String", "int")

    fun string(key: String, resource: Int = 0): String = getString.invoke(null, *getString.parameterTypes.map {
        val value: Any = if (it == Integer.TYPE) resource else key
        value
    }.toTypedArray()) as String
}
