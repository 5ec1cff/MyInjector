package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.toObfsInfo

// 强制在频道中点击 hash tag 时默认搜索本频道（原行为是搜索「全部帖子」）
class DefaultSearchTab : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.defaultSearchTab

    private fun deobf(creator: ObfsTableCreator): ObfsTable {

        val openHashTagSearch = creator.create("ChatActivityOpenHashTagSearch") { bridge ->
            bridge.findMethod {
                matcher {
                    usingEqStrings("#", "$", "@")
                    addInvoke {
                        descriptor("Ljava/lang/String;->startsWith(Ljava/lang/String;)Z")
                    }
                    addInvoke {
                        descriptor("Ljava/lang/String;->isEmpty()Z")
                    }
                }
            }.single().toObfsInfo()
        }

        val chatActivityDefaultSearchPageField =
            creator.create("ChatActivityDefaultSearchPage") { bridge ->
                bridge.findField {
                    matcher {
                        declaredClass(openHashTagSearch.className)
                        addWriteMethod {
                            descriptor(openHashTagSearch.descriptor)
                        }
                        type("int")
                    }
                }.single().toObfsInfo()
            }

        val viewPagerFixedTabsViewScrollToTab =
            creator.create("ViewPagerFixedTabsViewScrollToTab") { bridge ->
                bridge.findMethod {
                    matcher {
                        declaredClass {
                            superClass("android.widget.FrameLayout")
                        }
                        paramTypes("int", "int")
                        addInvoke {
                            descriptor("Landroid/view/View;->setEnabled(Z)V")
                        }
                    }
                }.single().toObfsInfo()
            }

        val viewPagerFixedTabsViewCurrentPosition =
            creator.create("ViewPagerFixedTabsViewCurrentPosition") { bridge ->
                bridge.findField {
                    matcher {
                        declaredClass(viewPagerFixedTabsViewScrollToTab.className)
                        addWriteMethod {
                            descriptor(viewPagerFixedTabsViewScrollToTab.descriptor)
                        }
                        addReadMethod {
                            declaredClass(viewPagerFixedTabsViewScrollToTab.className)
                            name("drawChild")
                        }
                        // addTab writes it
                        addWriteMethod {
                            declaredClass(viewPagerFixedTabsViewScrollToTab.className)
                            paramTypes("int", "java.lang.CharSequence")
                        }
                        type("int")
                    }
                }.single().toObfsInfo()
            }

        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val ChatActivityOpenHashTagSearch = table["ChatActivityOpenHashTagSearch"]!!
        val ChatActivityDefaultSearchPage = table["ChatActivityDefaultSearchPage"]!!
        val ViewPagerFixedTabsViewScrollToTab = table["ViewPagerFixedTabsViewScrollToTab"]!!
        val ViewPagerFixedTabsViewCurrentPosition = table["ViewPagerFixedTabsViewCurrentPosition"]!!
        val chatActivity = findClass(ChatActivityOpenHashTagSearch.className)
        val viewPagerFixedTabsView =
            findClass(ViewPagerFixedTabsViewScrollToTab.className)
        val inOpenHashTagSearch = ThreadLocal<Boolean>()
        val currentPositionField =
            viewPagerFixedTabsView.getDeclaredField(ViewPagerFixedTabsViewCurrentPosition.memberName)
                .also { it.isAccessible = true }
        chatActivity.hook(
            ChatActivityOpenHashTagSearch.memberName, String::class.java, java.lang.Boolean.TYPE,
            cond = ::isEnabled,
            before = { param ->
                inOpenHashTagSearch.set(true)
            },
            after = { param ->
                inOpenHashTagSearch.set(false)
                param.thisObject.setObj(ChatActivityDefaultSearchPage.memberName, 0)
            }
        )
        viewPagerFixedTabsView.hookBefore(
            ViewPagerFixedTabsViewScrollToTab.memberName,
            Integer.TYPE, Integer.TYPE
        ) { param ->
            if (inOpenHashTagSearch.get() == true) {
                if (currentPositionField.getInt(param.thisObject) != 0) {
                    param.args[0] = 0
                    param.args[1] = 0
                } else {
                    param.result = null
                }
            }
        }
    }
}
