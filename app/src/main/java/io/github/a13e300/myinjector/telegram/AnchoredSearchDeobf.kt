package io.github.a13e300.myinjector.telegram

import org.luckypray.dexkit.DexKitBridge

internal fun findAnchoredSearch(bridge: DexKitBridge, chat: String): Map<String, String> {
    val refresh = bridge.findMethod { matcher {
        paramTypes(); returnType("void")
        addInvoke { declaredClass("org.telegram.messenger.MediaDataController"); name("searchEndReached") }
    } }.single()
    val adapter = refresh.className
    val item = bridge.findMethod { matcher { declaredClass(adapter); paramTypes("int"); returnType("java.lang.Object") } }.single()
    val results = item.usingFields.map { it.field }.distinctBy { it.descriptor }.single { it.typeName == "java.util.ArrayList" }
    val count = bridge.findMethod { matcher { declaredClass(adapter); paramTypes(); returnType("int") } }.single()
    val scroll = bridge.findMethod { matcher {
        returnType("void"); paramCount(3)
        addInvoke { declaredClass("org.telegram.messenger.MediaDataController"); name("loadMoreSearchMessages") }
    } }.single()
    val loaded = scroll.usingFields.map { it.field }.distinctBy { it.descriptor }.single { it.className == adapter && it.typeName == "int" }
    val countFields = count.usingFields.map { it.field }.distinctBy { it.descriptor }
    val flicker = countFields.single { it.className == adapter && it.typeName == "int" && it != loaded }
    val full = refresh.invokes.distinctBy { it.descriptor }.single { it.name == refresh.name && it.className != adapter }
    val create = bridge.findMethod { matcher { declaredClass(chat); name("createView"); paramTypes("android.content.Context") } }.single()
    val adapterField = scroll.usingFields.map { it.field }.distinctBy { it.descriptor }.single { it.className == chat && it.typeName == adapter }
    val last = scroll.invokes.distinctBy { it.descriptor }.single { m -> m.paramCount == 0 && m.returnTypeName == "int" &&
        m.invokes.any { it.paramTypeNames == listOf("int", "int", "boolean", "boolean") && it.returnTypeName == "android.view.View" } }
    val layout = last.declaredClass!!
    val createCalls = scanTelegramDex(bridge, create).calls
    val setAdapter = createCalls.single { it.method.paramTypeNames == listOf(full.className) &&
        it.args.firstOrNull()?.field?.typeName == adapter }
    // R8 can move the setAdapter receiver across a branch that prepares its
    // constructor arguments. Read the list at the earlier setLayoutManager.
    val list = requireNotNull(createCalls.last { it.offset < setAdapter.offset &&
        it.method.paramTypeNames == listOf(layout.superClass!!.name) }.receiver?.field) {
        "Cannot find search list in ${create.descriptor}"
    }
    check(list.className == chat)
    setAdapter.receiver?.field?.let { check(it.descriptor == list.descriptor) }
    val first = layout.methods.filter { it != last && it.paramCount == 0 && it.returnTypeName == "int" }.single { method ->
        scanTelegramDex(bridge, method).calls.any { call ->
            call.method.paramTypeNames == listOf("int", "int", "boolean", "boolean") &&
                call.args[0]?.number == 0 && call.args[2]?.number == 0 && call.args[3]?.number == 1
        }
    }
    val position = layout.methods.single { it.paramTypeNames == listOf("int", "int") && it.returnTypeName == "void" &&
        it.invokes.any { m -> m.paramTypeNames == listOf("int", "int", "boolean") && m.returnTypeName == "void" } }
    val view = layout.methods.single { it.paramTypeNames == listOf("int") && it.returnTypeName == "android.view.View" }
    val getLayout = bridge.getClassData("androidx.recyclerview.widget.RecyclerView")!!.methods.single {
        it.paramCount == 0 && it.returnTypeName == layout.superClass!!.name
    }
    return mapOf("refresh" to refresh.descriptor, "results" to results.descriptor,
        "loaded" to loaded.descriptor, "flicker" to flicker.descriptor, "adapter" to adapterField.descriptor,
        "list" to list.descriptor, "scroll" to scroll.descriptor, "fullRefresh" to full.descriptor,
        "getLayout" to getLayout.descriptor, "first" to first.descriptor, "last" to last.descriptor,
        "position" to position.descriptor, "view" to view.descriptor)
}
