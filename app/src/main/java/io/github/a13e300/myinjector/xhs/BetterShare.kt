package io.github.a13e300.myinjector.xhs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import io.github.a13e300.myinjector.arch.Edge
import io.github.a13e300.myinjector.arch.FindObjectConfiguration
import io.github.a13e300.myinjector.arch.currentApplication
import io.github.a13e300.myinjector.arch.findPathToObject
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookCAfter
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD
import java.lang.reflect.Field

private fun toHtmlText(title: String?, desc: String?, url: String): String {
    val sb = StringBuilder()
    var hasPre = false
    if (title?.isNotEmpty() == true) {
        sb.append("<b>$title</b>")
        hasPre = true
    }
    val id = Regex("https://www.xiaohongshu.com/discovery/item/([0-9a-z]*)")
        .find(url)?.groupValues?.getOrNull(1)
    val text = if (id != null) "xhs/$id" else url.removePrefix("https://").split("?").first()
    if (hasPre) sb.append("<br>")
    sb.append("<a href=\"$url\">$text</a>")
    if (desc?.isNotEmpty() == true) {
        sb.append("<br><blockquote>")
        sb.append(desc.split("\n").joinToString("<br>"))
        sb.append("</blockquote>")
    }
    return sb.toString()
}

class BetterShare : MyDynHook("betterShare") {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.betterShare

    override fun onHook() {
        val shareMethod = XhsHandler.creator.create("shareMethod") { bridge ->
            val shareMethod = bridge.findMethod {
                matcher {
                    usingEqStrings("TYPE_LINKED", "xhsdiscover://tagged_me")
                }
            }.single()

            shareMethod.toObfsInfo()
        }

        val shareClass = findClass(shareMethod.className)

        val shareEntityField = shareClass.declaredFields.single {
            it.type.name.endsWith("ShareEntity")
        }.also { it.isAccessible = true }

        val noteItemBeanField = shareClass.declaredFields.single {
            it.type.name.endsWith("NoteItemBean")
        }.also { it.isAccessible = true }

        var longPressed = false

        fun shareCommon(shareEntity: Any, noteItem: Any) {
            val longClicked = longPressed
            longPressed = false
            val pageUrl = shareEntity.getObjAs<String>("pageUrl")
            var desc = noteItem.getObjAsN<String>("desc")
            var title = noteItem.getObjAsN<String>("title")
            if (title != null && desc?.startsWith(title) == true) title = null

            if (!longClicked && desc != null) {
                desc = desc.let {
                    if (it.length > 50) {
                        val last2 = it[47]
                        if (last2.code in 0xd800..<0xdc00) {
                            it.substring(0, 47).trimEnd() + ".."
                        } else {
                            it.substring(0, 48).trimEnd() + ".."
                        }
                    } else it.trimEnd()
                }
            }

            val uri = Uri.parse(pageUrl)
            val newUri = uri.buildUpon()
                .also {
                    uri.getQueryParameters("xsec_token").firstOrNull()?.let { token ->
                        it.clearQuery()
                        it.appendQueryParameter("xsec_token", token)
                    }
                }
                .build()
                .toString()
            // logD("newUri=$newUri desc=$desc title=$title")
            val html = toHtmlText(title, desc, newUri)
            var text = title
            if (text.isNullOrEmpty()) text = desc
            else text += "\n" + desc
            if (text.isNullOrEmpty()) text = newUri
            else text += "\n" + newUri
            // logD("html=$html")

            currentApplication()
                .getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newHtmlText("", text, html))
        }

        shareClass.hookAllBefore(shareMethod.memberName, cond = ::isEnabled) { param ->
            if (param.args[0] == "TYPE_LINKED") {
                val shareEntity = shareEntityField.get(param.thisObject)
                val noteItem = noteItemBeanField.get(param.thisObject)

                shareCommon(shareEntity, noteItem)

                param.result = null
            }
        }

        val shareUserView = findClass("com.xingin.sharesdk.ui.view.ShareUserView")
        shareUserView.hookCAfter(
            Context::class.java,
            AttributeSet::class.java,
            Integer.TYPE, cond = ::isEnabled
        ) { param ->
            (param.thisObject as View).setOnLongClickListener {
                longPressed = true
                it.callOnClick()
            }
        }

        val shareMethod2 = XhsHandler.creator.create("shareMethod2") { bridge ->
            // e2c.a#invoke 9.51.0
            val shareMethod = bridge.findMethod {
                matcher {
                    usingEqStrings(
                        "TYPE_COPY_ONLY",
                        "ug-share-copy-link",
                    )
                }
            }.singleOrNull() ?:
            // l2b.e0#invoke 9.34.4
            bridge.findMethod {
                matcher {
                    usingEqStrings(
                        "TYPE_COPY_ONLY",
                        "configData is null copyText:",
                        "ShareHelper"
                    )
                }
            }.single()

            shareMethod.toObfsInfo()
        }

        val skipClipboardTls = ThreadLocal<Boolean>()

        ClipboardManager::class.java.hookAllBefore("setPrimaryClip") { param ->
            if (skipClipboardTls.get() == true) {
                param.result = true
            }
        }

        val shareClass2 = findClass(shareMethod2.className)

        // .d
        val shareEntityPreField = shareClass2.declaredFields.single {
            it.type.declaredFields.any {
                it.type.name.endsWith("ShareEntity")
            }
        }
            .also { it.isAccessible = true }
        // .d.a
        val shareEntityField2 =
            shareEntityPreField.type.declaredFields.single { it.type.name.endsWith("ShareEntity") }
                .also { it.isAccessible = true }
        logD("shareEntityPreField $shareEntityPreField shareEntityField2=$shareEntityField2")

        var typeField: Field? = null
        var noteItemBeanFieldsPath: List<Field>? = null
        val noteItemBeanClass = findClass("com.xingin.entities.NoteItemBean")

        shareClass2.hookAll(shareMethod2.memberName, cond = ::isEnabled, before = { param ->
            val arg0 = param.args[0]
            logD("ShareClass arg0 $arg0")
            var currentTypeField: Field? = typeField
            if (currentTypeField == null) {
                currentTypeField =
                    arg0.javaClass.declaredFields.singleOrNull {
                        it.isAccessible = true
                        it.type == String::class.java && it.get(arg0)
                            .let { it == "TYPE_LINKED" || it == "TYPE_COPY_ONLY" }
                    }
                typeField = currentTypeField
                logD("got type field $currentTypeField")
            }
            if (currentTypeField == null) return@hookAll
            if (currentTypeField.get(arg0).let { it == "TYPE_LINKED" || it == "TYPE_COPY_ONLY" }) {
                val preFieldV = shareEntityPreField.get(param.thisObject)
                val shareEntity = shareEntityField2.get(preFieldV)!!
                logD("shareEntity $shareEntity")
                if (noteItemBeanFieldsPath == null) {
                    val path = findPathToObject(
                        param.thisObject, FindObjectConfiguration(
                            targetClz = noteItemBeanClass,
                            maxDepth = 3,
                            maxTries = 10000,
                            fieldsOnly = true,
                            first = true
                        )
                    ).single()
                    logD("search done: $path")
                    val pathList = mutableListOf<Field>()
                    var p: Edge? = path
                    while (p != null && p !is Edge.StartEdge) {
                        p as Edge.FieldEdge
                        pathList.add(0, p.field)
                        p = p.prev
                    }
                    logD("path: ${pathList.joinToString(".") { it.name }}")
                    noteItemBeanFieldsPath = pathList
                }

                var noteItem: Any = param.thisObject
                for (f in noteItemBeanFieldsPath) {
                    noteItem = f.get(noteItem)!!
                }

                logD("noteItem path: $noteItem")

                shareCommon(shareEntity, noteItem)

                skipClipboardTls.set(java.lang.Boolean.TRUE)
            }
        }, after = {
            skipClipboardTls.remove()
        })
    }
}
