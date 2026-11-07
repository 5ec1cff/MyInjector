package io.github.a13e300.myinjector.telegram

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData

// These fields are also read directly by ChatActivity after R8 removes getters.
internal fun findSearchState(bridge: DexKitBridge, chat: String): Map<String, String> {
    val methods = listOf("searchMessagesInChat", "getMask", "getSearchCount", "searchEndReached",
        "loadMoreSearchMessages", "clearFoundMessageObjects").associateWith { telegramSearchMethod(bridge, it) }
    val found = methods.mapValues { it.value.descriptor }.toMutableMap()
    val search = methods.getValue("searchMessagesInChat")
    val media = search.className
    fun fields(method: MethodData) = method.usingFields.map { it.field }.distinctBy { it.descriptor }.filter { it.className == media }
    fun save(key: String, field: FieldData) { found[key] = field.descriptor }
    val mask = fields(methods.getValue("getMask"))
    save("lastReturnedNum", mask.single { it.typeName == "int" })
    val results = mask.single { it.typeName == "java.util.ArrayList" }
    save("searchResultMessages", results)
    val count = fields(methods.getValue("getSearchCount"))
    save("messagesLocalSearchCount", count.single { it.typeName == "int" })
    save("messagesSearchCount", count.single { it.typeName == "int[]" })
    val server = count.single { it.typeName == "java.util.ArrayList" }
    save("searchServerResultMessages", server)
    save("searchLocalResultMessages", fields(search).single {
        it.typeName == "java.util.ArrayList" && it != results && it != server
    })
    val code = scanTelegramDex(bridge, search)
    val requests = code.writes.filter { it.field.className == media && it.value?.resultOf?.name == "sendRequest" }
        .map { it.field }.distinctBy { it.descriptor }
    check(requests.size == 2)
    save("mergeReqId", requests.first())
    save("reqId", requests.last())
    save("lastReqId", code.writes.filter { it.field.className == media && it.value?.incremented == true && it.value.field == it.field }
        .map { it.field }.distinctBy { it.descriptor }.single { it.descriptor != found.getValue("lastReturnedNum") })
    save("lastSearchQuery", fields(search).single { it.typeName == "java.lang.String" })
    save("searchServerResultMessagesMap", fields(search).single { it.typeName == "android.util.SparseArray[]" })
    save("loadingMoreSearchMessages", fields(methods.getValue("loadMoreSearchMessages")).single { it.typeName == "boolean" })
    val local = scanTelegramDex(bridge, methods.getValue("searchEndReached")).compares.flatMap { it.fields }
        .filter { it.className == media && it.typeName == "boolean" }.distinctBy { it.descriptor }
    check(local.size == 2)
    save("loadingSearchLocal", local[0]); save("loadedPredirectedSearchLocal", local[1])
    val notification = telegramApiClass(bridge, "org.telegram.messenger.NotificationCenter")
    val posts = code.calls.filter { it.method.className == notification && it.method.paramTypeNames == listOf("int", "java.lang.Object[]") }
    found["postNotificationName"] = posts.map { it.method.descriptor }.distinct().single()
    save("chatSearchResultsLoading", posts.filter { it.args[1]?.arraySize == 1 }.mapNotNull { it.args[0]?.field }.distinctBy { it.descriptor }.single())
    save("chatSearchResultsAvailable", posts.filter { (it.args[1]?.arraySize ?: 0) > 1 }.mapNotNull { it.args[0]?.field }.distinctBy { it.descriptor }.single())
    val message = telegramApiClass(bridge, "org.telegram.messenger.MessageObject")
    val refresh = bridge.getMethodData(findAnchoredSearch(bridge, chat).getValue("refresh"))!!
    save("isPrimaryGroupMessage", refresh.usingFields.map { it.field }.distinctBy { it.descriptor }.single { it.className == message && it.typeName == "boolean" })
    val update = bridge.getClassData(media)!!.methods.filter { it.paramCount == 0 && it.returnTypeName == "void" }
        .single { method -> method.usingFields.any { it.field.descriptor == found.getValue("isPrimaryGroupMessage") } }
    save("isSavedFiltered", scanTelegramDex(bridge, update).writes.filter { it.field.className == message && it.field.typeName == "boolean" && it.value?.number == 1 }
        .map { it.field }.distinctBy { it.descriptor }.single())
    return found
}
