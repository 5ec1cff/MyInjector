package io.github.a13e300.myinjector.xhs

import io.github.a13e300.myinjector.arch.hookAllConstantIf
import io.github.a13e300.myinjector.arch.toObfsInfo

class ForceSaveImage : MyDynHook("forceSaveImage") {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.forceSaveImage

    override fun onHook() {
        // 单张图片保存
        val method = XhsHandler.creator.create("disableSave") { bridge ->
            val clazz = bridge.findClass {
                matcher {
                    fields {
                        add {
                            name("disable")
                        }
                        add {
                            name("disableToast")
                        }
                        add {
                            name("checkLogin")
                        }
                    }
                }
            }.single()
            val method = clazz.findMethod {
                matcher {
                    usingFields {
                        add {
                            name("disable")
                        }
                    }
                    returnType(java.lang.Boolean::class.java)
                }
            }.single()
            method.toObfsInfo()
        }
        findClass(method.className).hookAllConstantIf(
            method.memberName,
            false,
            cond = ::isEnabled
        )
        // 笔记分享的保存所有图片
        // ShareInfoDetail$Operate
        val method2 = XhsHandler.creator.create("disableSaveAll") { bridge ->
            val clazz = bridge.findClass {
                matcher {
                    fields {
                        add {
                            name("disable")
                        }
                        add {
                            name("disableToast")
                        }
                        add {
                            name("labelImageUrl")
                        }
                    }
                }
            }.single()
            val method = clazz.findMethod {
                matcher {
                    usingFields {
                        add {
                            name("disable")
                        }
                    }
                    returnType(java.lang.Boolean.TYPE)
                }
            }.single()
            method.toObfsInfo()
        }
        findClass(method2.className).hookAllConstantIf(
            method2.memberName,
            false,
            cond = ::isEnabled
        )
    }
}
