package io.github.a13e300.myinjector

import android.view.View
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.setObj

class SudokuHandler : IHook() {
    companion object {
        private const val AdsConfigImpl = "AdsConfigImpl"
        private const val AdsConfigImplInitiator = "AdsConfigImplInitiator"
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
        val tbl = createObfsTable("sudoku", 1) { bridge ->
            val clzAdsConfigImpl = bridge.findClass {
                matcher {
                    usingStrings("AdsConfigImpl")
                }
            }.single()
            val adsConfigImplInitiator = bridge.findMethod {
                matcher {
                    addInvoke {
                        name = "<init>"
                        declaredClass = clzAdsConfigImpl.name
                    }
                }
            }.single()
            val enabledField = clzAdsConfigImpl.findField {
                matcher {
                    type(Boolean::class.java)
                }
            }.single()

            mutableMapOf(
                AdsConfigImpl to ObfsInfo(clzAdsConfigImpl.name, enabledField.name),
                AdsConfigImplInitiator to ObfsInfo(
                    adsConfigImplInitiator.className,
                    adsConfigImplInitiator.name
                )
            )
        }

        val initiator = tbl[AdsConfigImplInitiator]!!
        val enableField = tbl[AdsConfigImpl]!!
        findClass(initiator.className).hookAllAfter(initiator.memberName) { param ->
            param.result.setObj(enableField.memberName, false)
        }
    }.onFailure {
        logE("hookRemoveAd", it)
    }
}