package io.github.a13e300.myinjector.telegram

import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import io.github.a13e300.myinjector.arch.hookBefore
import java.util.Locale

class ShowExactLastSeenTime : MyDynHook("showExactLastSeenTime") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.showExactLastSeenTime
    private val mFormatterTime = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    private val mFormatterDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    override fun onHook() {
        // org.telegram.messenger.LocaleController#formatDateOnline
        val locale = TelegramLocale(classLoader)
        val formatDateOnline = telegramApiMethod(classLoader, "org.telegram.messenger.LocaleController", "formatDateOnline", "long", "boolean[]")
        val kLocaleController = formatDateOnline.declaringClass
        val formatDateOnlineMethods = kLocaleController.declaredMethods.filter { it.name == formatDateOnline.name }
        require(formatDateOnlineMethods.isNotEmpty()) { "formatDateOnline not found" }
        formatDateOnlineMethods.forEach { formatDateOnline ->
            formatDateOnline.hookBefore(cond = ::isEnabled) {
                val timeIndex = formatDateOnline.parameterTypes.indexOf(java.lang.Long.TYPE)
                val timeMillis = (it.args[timeIndex] as Long) * 1000L
                val strLastSeenFormatted =
                    locale.string("LastSeenFormatted")
                val strYesterdayAtFormatted =
                    locale.string("YesterdayAtFormatted")
                val strLastSeenDateFormatted =
                    locale.string("LastSeenDateFormatted")
                val hasLocError =
                    strLastSeenFormatted.contains("LC_ERR") || strYesterdayAtFormatted.contains("LC_ERR") || strLastSeenDateFormatted.contains(
                        "LC_ERR"
                    )
                it.result = when {
                    hasLocError -> mFormatterDateTime.format(timeMillis)  // sigh...
                    isToday(timeMillis) -> strLastSeenFormatted.format(mFormatterTime.format(timeMillis))
                    isYesterday(timeMillis) -> {
                        var shouldBeShorter = false
                        if (formatDateOnline.parameterTypes.size >= 2) {
                            val madeShort = it.args[1 - timeIndex] as? BooleanArray
                            if (madeShort != null) {
                                shouldBeShorter = true
                                madeShort[0] = true
                            }
                        }
                        if (shouldBeShorter) {
                            strYesterdayAtFormatted.format(mFormatterTime.format(timeMillis))
                        } else {
                            strLastSeenFormatted.format(
                                strYesterdayAtFormatted.format(
                                    mFormatterTime.format(
                                        timeMillis
                                    )
                                )
                            )
                        }
                    }

                    else -> strLastSeenDateFormatted.format(mFormatterDateTime.format(timeMillis))
                }
            }
        }
    }

    private fun isToday(timeMillis: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        val year = calendar.get(Calendar.YEAR)
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        calendar.timeInMillis = System.currentTimeMillis()
        return (year == calendar.get(Calendar.YEAR)) && (dayOfYear == calendar.get(Calendar.DAY_OF_YEAR))
    }

    private fun isYesterday(timeMillis: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        val year = calendar.get(Calendar.YEAR)
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        calendar.timeInMillis = System.currentTimeMillis()
        return (year == calendar.get(Calendar.YEAR)) && (dayOfYear == calendar.get(Calendar.DAY_OF_YEAR) - 1)
    }
}
