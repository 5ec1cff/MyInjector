package io.github.a13e300.myinjector.telegram

import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.hookBefore
import java.util.Locale

class ShowExactLastSeenTime : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.showExactLastSeenTime
    private val mFormatterTime = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    private val mFormatterDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    override fun onHook() {
        // org.telegram.messenger.LocaleController#formatDateOnline
        val kLocaleController = findClass("org.telegram.messenger.LocaleController")
        val formatDateOnline = kLocaleController.declaredMethods.single {
            it.name == "formatDateOnline" && it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == Long::class.java
        }
        formatDateOnline.hookBefore(cond = ::isEnabled) {
            val timeMillis = (it.args[0] as Long) * 1000L
            val strLastSeenFormatted =
                kLocaleController.callS("getString", "LastSeenFormatted") as String
            val strYesterdayAtFormatted =
                kLocaleController.callS("getString", "YesterdayAtFormatted") as String
            val strLastSeenDateFormatted =
                kLocaleController.callS("getString", "LastSeenDateFormatted") as String
            val hasLocError =
                strLastSeenFormatted.contains("LC_ERR") || strYesterdayAtFormatted.contains("LC_ERR") || strLastSeenDateFormatted.contains(
                    "LC_ERR"
                )
            it.result = when {
                hasLocError -> mFormatterDateTime.format(timeMillis)  // sigh...
                isToday(timeMillis) -> strLastSeenFormatted.format(mFormatterTime.format(timeMillis))
                isYesterday(timeMillis) -> {
                    var shouldBeShorter = false
                    if (formatDateOnline.parameterTypes.size == 2) {
                        val madeShort = it.args[1] as? BooleanArray
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