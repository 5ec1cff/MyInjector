package io.github.a13e300.myinjector.telegram

import android.app.Activity
import android.app.AndroidAppHelper
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.logE
import org.json.JSONObject
import java.io.File

// 支持导入和使用自定义 emoji 文本映射
object CustomEmojiMapping : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.customEmojiMapping

    // emoji name -> (emoji id, emoji)
    data class EmotionMap(
        val map: Map<String, Pair<String, String>>,
        val regex: Regex
    )

    private var _emotionMap: EmotionMap? = null
    private fun loadEmotionMap(json: String): EmotionMap {
        val data = JSONObject(json)
        val mp = mutableMapOf<String, Pair<String, String>>()
        for (k in data.keys()) {
            val v = data.getJSONArray(k)
            val id = v.getString(0)
            val name = v.getJSONArray(1).getString(0)
            mp.put(k, Pair(id, name))
        }
        val regex = Regex(mp.keys.joinToString("|") { Regex.escape(it) })
        return EmotionMap(mp, regex)
    }

    private fun Context.getEmotionMapFile(): File {
        return File(getExternalFilesDir(""), "emotion_map.json")
    }

    val emotionMap: EmotionMap
        get() {
            val m = _emotionMap
            if (m == null) {
                synchronized(this) {
                    val m2 = _emotionMap
                    if (m2 == null) {
                        val f = AndroidAppHelper.currentApplication().getEmotionMapFile()
                        val mp = runCatching {
                            if (f.isFile)
                                loadEmotionMap(f.readText())
                            else null
                        }.onFailure {
                            logE("load emotion map from $f failed  ", it)
                        }.getOrNull() ?: EmotionMap(emptyMap(), Regex(""))
                        _emotionMap = mp
                        return mp
                    }
                    return m2
                }
            }
            return m
        }

    private fun hookPasteCustomEmoji() {
        ClipboardManager::class.java.hookAllAfter("getPrimaryClip", cond = ::isEnabled) { param ->
            // logD("afterHookedMethod: getPrimaryClip")
            val result = param.result as? ClipData ?: return@hookAllAfter
            val item = result.getItemAt(0)
            val origText = item.text
            val newText = StringBuilder()
            var pos = 0
            // logD("afterHookedMethod: $origText")
            val mp = emotionMap
            while (true) {
                val r = mp.regex.find(origText, pos) ?: break
                val kw = r.value
                val replacement = mp.map[kw]?.let {
                    "<animated-emoji data-document-id=\"${it.first}\">&#${it.second.firstUnicodeChar()};</animated-emoji>"
                } ?: kw.toHtml()
                newText.append(origText.substring(pos until r.range.first).toHtml())
                newText.append(replacement)
                pos = r.range.last + 1
                // logD("afterHookedMethod: replaced=$newText")
            }
            if (pos != 0) {
                if (pos < origText.length) newText.append(origText.substring(pos).toHtml())
                // logD("replace: $newText")
                param.result = ClipData.newHtmlText(
                    "",
                    newText,
                    newText.toString()
                )
            }
        }
    }

    fun importEmojiMap(context: Context) {

        Toast.makeText(context, "选择一个 emoji 映射的 json 配置文件", Toast.LENGTH_SHORT)
            .show()
        val activity = context.findBaseActivity()
        activity.startActivityForResult(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
            },
            114514
        )
    }

    private fun hookEmojiManage() {
        val chatActivityEnterView = findClass(
            "org.telegram.ui.Components.ChatActivityEnterView"
        )

        val cst = chatActivityEnterView.declaredConstructors.maxBy { it.parameterCount }!!
            .also { it.isAccessible = true }
        cst.hookAfter(cond = ::isEnabled) { param ->
            param.thisObject.getObjAs<View>("emojiButton")
                .setOnLongClickListener { v ->
                    importEmojiMap(v.context)
                    true
                }
        }

        Activity::class.java.hookAllAfter("dispatchActivityResult") { param ->
            if (param.args[1] == 114514) {
                // logD("dispatchActivityResult: " + param.args[3])
                val ctx = param.thisObject as Activity
                (param.args[3] as? Intent)?.data?.let { url ->
                    ctx.contentResolver.openInputStream(url)?.let {
                        val text = it.readBytes().toString(Charsets.UTF_8)
                        val mp = try {
                            loadEmotionMap(text)
                        } catch (t: Throwable) {
                            logE("loadEmotionMap: ", t)
                            Toast.makeText(ctx, "load failed: $t", Toast.LENGTH_LONG).show()
                            return@hookAllAfter
                        }
                        synchronized(this@CustomEmojiMapping) {
                            _emotionMap = mp
                        }
                        ctx.getEmotionMapFile().writeText(text)
                        Toast.makeText(ctx, "加载成功", Toast.LENGTH_SHORT).show()
                        return@hookAllAfter
                    }
                }
                Toast.makeText(ctx, "未提供文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onHook() {
        hookPasteCustomEmoji()
        hookEmojiManage()
    }
}
