package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge

internal fun locateBiliLegacySearchHintObserver(bridge: DexKitBridge): ObfsInfo {
    val observer = bridge.findMethod { matcher {
        usingEqStrings("search.search-discover.default-word.0.show")
    } }.single { method -> method.invokes.any {
        it.methodName == "setQueryHint" && it.className == "tv.danmaku.bili.widget.SearchView"
    } }
    return observer.toObfsInfo()
}

internal fun locateBiliSearchPurify(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val square = bridge.findMethod { matcher {
        usingEqStrings("https://app.bilibili.com/x/v2/search/square")
    } }.single()
    val repository = square.declaredClass!!
    val recommend = repository.methods.single {
        "https://app.bilibili.com/x/v2/search/recommend" in it.usingStrings
    }
    val defaultWord = repository.methods.single { method -> method.invokes.any {
        it.className == "com.bapis.bilibili.app.interfaces.v1.KSearchMoss" && it.methodName == "defaultWords"
    } }
    val history = bridge.findMethod { matcher { usingEqStrings("History(title=") } }.single().declaredClass!!
    check(square.invokes.any { it.className == history.name && it.methodName == "<init>" })
    val wordModels = bridge.findMethod { matcher { usingEqStrings("SearchDefaultWord(show=") } }
        .map { it.className }.toSet()
    fun wordSerializer(name: String): Map<String, ObfsInfo> {
        val init = bridge.findMethod { matcher { usingEqStrings(name) } }.single()
        val deserialize = init.declaredClass!!.methods.single {
            it.methodName == "deserialize" && it.paramTypeNames == listOf("kotlinx.serialization.encoding.Decoder")
        }
        val model = deserialize.invokes.filter { it.methodName == "<init>" && it.className in wordModels }
            .distinctBy { it.className }.single().declaredClass!!
        return mapOf("model" to model.toObfsInfo(), "deserialize" to deserialize.toObfsInfo())
    }
    val search = wordSerializer("kntr.app.search.model.SearchDefaultWord")
    val home = wordSerializer("tv.danmaku.bili.khomeapi.frame.SearchDefaultWord")
    check(defaultWord.invokes.any { it.className == search.getValue("model").className && it.methodName == "<init>" })
    fun wordState(text: String) = bridge.findMethod { matcher { usingEqStrings(text) } }.single()
        .declaredClass!!.methods.single {
            it.methodName == "<init>" && it.paramTypeNames.count { name -> name == search.getValue("model").className } == 1
        }.toObfsInfo()
    return mapOf(
        "square" to square.toObfsInfo(), "recommend" to recommend.toObfsInfo(),
        "defaultWord" to defaultWord.toObfsInfo(), "history" to history.toObfsInfo(),
        "wordModel" to search.getValue("model"), "wordDeserialize" to search.getValue("deserialize"),
        "homeWordModel" to home.getValue("model"), "homeWordDeserialize" to home.getValue("deserialize"),
        "pageState" to wordState("SearchPageState(searchBarState="),
        "discoverContent" to wordState("Content(sections="),
    )
}

internal fun biliSearchPurifyMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliSearchPurify(creator.bridge) }
    return listOf("square", "recommend", "defaultWord", "history", "wordModel", "wordDeserialize",
        "homeWordModel", "homeWordDeserialize", "pageState", "discoverContent").associateWith { key ->
        creator.create("searchPurify.$key") { found.getValue(key) }
    }
}
