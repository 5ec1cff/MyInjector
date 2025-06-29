package io.github.a13e300.myinjector

import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.setObj

class SudokuHandler : IHook() {
    companion object {
        private const val AdsConfigImpl = "AdsConfigImpl"
        private const val AdsConfigImplInitiator = "AdsConfigImplInitiator"
    }

    override fun onHook() {
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
    }
}