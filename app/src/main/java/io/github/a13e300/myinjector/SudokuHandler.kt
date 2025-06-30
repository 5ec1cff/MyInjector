package io.github.a13e300.myinjector

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.setObj
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.base.StringMatcher

class SudokuHandler : IHook() {
    companion object {
        private const val AdsConfigDto = "AdsConfigDto"
    }

    override fun onHook() {
        hookNotHidingNavBar()
        hookRemoveAd()
    }

    @Suppress("deprecation")
    private fun hookNotHidingNavBar() {
        View::class.java.hookBefore("setSystemUiVisibility", Integer.TYPE) { param ->
            var flags = param.args[0] as Int
            flags = flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
            param.args[0] = flags
        }
    }

    private fun hookRemoveAd() = runCatching {
        val tbl = createObfsTable("sudoku", 2) { bridge ->
            val adsConfigDto = bridge.findClass {
                matcher {
                    addUsingString(StringMatcher("AdsConfigDto", StringMatchType.StartsWith))
                }
            }.single()
            val enabledField = adsConfigDto.findField {
                matcher {
                    type(Integer::class.java)
                }
            }.single()

            mutableMapOf(
                AdsConfigDto to ObfsInfo(adsConfigDto.name, enabledField.name)
            )
        }

        val adsConfigDto = tbl[AdsConfigDto]!!
        findClass(adsConfigDto.className).hookAllCAfter { param ->
            param.thisObject.setObj(adsConfigDto.memberName, 0)
        }

        Instrumentation::class.java.hookAllBefore("execStartActivity") { param ->
            val intent = param.args[4] as Intent
            if (intent.component?.className == "com.easybrain.crosspromo.ui.CrossPromoActivity") {
                logD("fuck com.easybrain.crosspromo.ui.CrossPromoActivity")
                param.result = null
            }
        }
    }.onFailure {
        logE("hookRemoveAd", it)
    }
}