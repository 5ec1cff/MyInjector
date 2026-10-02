package io.github.a13e300.myinjector.telegram

import android.view.View
import android.widget.FrameLayout
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import java.util.WeakHashMap
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

class HideFloatFab : MyDynHook("hideFloatFab") {
    private val fabViews = WeakHashMap<View, Boolean>()

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.hideFloatFab
    override fun onHook() {
        val creator = TelegramHandler.creator
        val createView = creator.create("HideFloatFabDialogsCreateView") { bridge ->
            bridge.findMethod { matcher {
                paramTypes("android.content.Context")
                returnType("android.view.View")
                usingEqStrings("storyhint")
                addUsingField { declaredClass("org.telegram.messenger.R\$drawable"); name("outline_fab_story_24") }
            } }.single().toObfsInfo()
        }
        val mainButton = creator.create("DialogsFloatingButton") { bridge ->
            val update = bridge.findMethod { matcher {
                declaredClass(createView.className)
                paramTypes()
                returnType("void")
                addUsingField { declaredClass("org.telegram.messenger.R\$drawable"); name("floating_check") }
            } }.single()
            update.usingFields.map { it.field }.distinctBy { it.descriptor }.single {
                it.className == createView.className &&
                    bridge.getClassData(it.typeName)?.superClass?.name == "android.widget.FrameLayout"
            }.toObfsInfo()
        }
        val storyButton = creator.create("DialogsFloatingStoriesButton") { bridge ->
            val type = bridge.getFieldData(mainButton.descriptor)!!.typeName
            bridge.findField { matcher { declaredClass(createView.className); type(type) } }
                .single { it.descriptor != mainButton.descriptor }.toObfsInfo()
        }
        val buttons = listOf(mainButton, storyButton).map {
            DexField(it.descriptor).getFieldInstance(classLoader).apply { isAccessible = true }
        }
        DexMethod(createView.descriptor).getMethodInstance(classLoader)
            .hookAfter(cond = ::isEnabled) { param ->
            val fab1 = buttons[0].get(param.thisObject) as? FrameLayout
            val fab2 = buttons[1].get(param.thisObject) as? FrameLayout
            listOfNotNull(fab1, fab2).forEach { fab ->
                fabViews[fab] = true
                fab.visibility = View.GONE
            }
        }
        findClass("android.view.View").hookBefore("setVisibility",
            Int::class.javaPrimitiveType!!,
            cond = ::isEnabled
        ) { param ->
            val view = param.thisObject as? View ?: return@hookBefore
            if (fabViews.containsKey(view)) {
                param.args[0] = View.GONE
            }
        }
    }
}
