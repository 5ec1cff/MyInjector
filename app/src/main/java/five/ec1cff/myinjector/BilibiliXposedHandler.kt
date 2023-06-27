package five.ec1cff.myinjector

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.RuntimeException
import java.lang.ref.WeakReference

class BilibiliXposedHandler: IXposedHookLoadPackage {
    var app: Context? = null

    private fun getApplication(): Context {
        if (app != null) return app as Context
        app = loadClass("android.app.ActivityThread")
            .invokeStaticMethod("currentActivityThread")
            ?.getObject("mInitialApplication") as Context
        if (app == null) throw RuntimeException("failed to get application, abort!")
        return app as Context
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "tv.danmaku.bili" || !lpparam.processName.startsWith("tv.danmaku.bili")) return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        EzXHelperInit.setLogTag("myinjector-bili")
        Log.i("inject bilibili, pid=${Process.myPid()}, processName=${lpparam.processName}")

        // hookComment()
        // hookIntentHandler()
        // hookShare()
        hookOAID()
        hookShare2()
        // hookArticle()
    }

    fun hookIntentHandler() {
        findMethod("tv.danmaku.bili.ui.intent.IntentHandlerActivity") {
            name == "onCreate"
        }.hookBefore {
            Log.d("IntentHandlerActivity onCreate called")
            val intent = it.thisObject.invokeMethod("getIntent") as Intent?
            if (intent != null) {
                if (intent.data?.host == "story") {
                    intent.putExtra("from_intent_handler", "hook")
                }
            }
        }

        findMethod("com.bilibili.video.story.StoryVideoActivity") {
            name == "onCreate"
        }.hookBefore {
            Log.d("StoryVideoActivity onCreate called")
            val thiz = it.thisObject as Activity
            val intent = it.thisObject.invokeMethod("getIntent") as Intent?
            if (intent != null) {
                if (intent.getStringExtra("from_intent_handler") != null) return@hookBefore
                var url: String? = intent.data?.toString()
                // Log.d("" + url)
                if (url != null && url.startsWith("bilibili://story")) {
                    url = url.replace("bilibili://story", "bilibili://video")
                    intent.data = Uri.parse(url)
                    intent.component = null
                    thiz.startActivity(intent)
                    thiz.finish()
                    // Log.d("replace story url:" + url)
                }
            }
        }
    }

    fun hookComment() {
        val classSheetItem = loadClass("y1.c.h.i")

        findMethod("y1.c.h.b") {
            name == "h"
        }.hookBefore {
            val list = it.thisObject.getObjectAs<ArrayList<Any>>("b")
            val item0 = list.get(0)
            val fieldC = item0.getObject("c")
            val itemNew = classSheetItem.newInstance(
                args(
                fieldC,
                "menu_get_url", "获取评论 url"
            ), argTypes(
                Context::class.java,
                String::class.java,
                String::class.java
            )
            )
            if (itemNew != null) {
                list.add(itemNew)
            }
        }

        findMethod("com.bilibili.app.comm.comment2.comments.a.r1\$c\$a") {
            name == "k"
        }.hookBefore {
            val id = it.args[0].invokeMethod("a")
            Log.d("clicked: ${id}")
            if (id == "menu_get_url") {
                val data = it.thisObject.getObject("a").getObject("a").getObject("b")
                val type = data.invokeMethod("u")
                val oid = data.invokeMethod("p")
                val rpid = it.thisObject.getObject("a").getObject("a").invokeMethod("q")
                val url = "bilibili://comment/detail/${type}/${oid}/${rpid}"
                Log.d("url: ${url}")
                val clipboardManager: ClipboardManager = getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", url))
                it.result = null
            }
        }

        Log.i("hook done")
    }

    fun hookShare() {
        findMethod("com.bilibili.lib.sharewrapper.online.api.ShareClickResult") {
            name == "getContent"
        }.hookBefore {
            it.result = null
        }

        findMethod("com.bilibili.lib.sharewrapper.online.api.ShareClickResult") {
            name == "getLink"
        }.hookBefore {
            it.result = null
        }
    }

    private fun hookOAID() {
        findMethod("com.bilibili.lib.oaid.internal.IdsManager") {
            name == "l"
        }.hookReplace {
            Log.d("fuck oaid")
            false
        }
    }

    private fun hookShare2() {
        findMethod("com.bilibili.app.comm.comment2.share.CommentShareManager") {
            parameterTypes[0] == Context::class.java
                    && parameterTypes[1] == java.lang.Long.TYPE
                    && parameterTypes[2] == java.lang.Long.TYPE
                    && parameterTypes[3] == java.lang.Long.TYPE
        }.hookBefore {
            val activity = it.args[0] as? Activity
            if (activity == null) {
                Log.w("context is not activity: ${it.args[0]}, skip")
                return@hookBefore
            }
            val oid = it.args[1] as Long
            val type = it.args[2] as Long
            val rpid = it.args[3] as Long
            val url = "bilibili://comment/detail/${type}/${oid}/${rpid}"
            val httpUrl = when (type) {
                1L -> "https://www.bilibili.com/video/av$oid#reply$rpid"
                12L -> "https://www.bilibili.com/read/cv$oid#reply$rpid"
                17L -> "https://t.bilibili.com/$oid#reply$rpid"
                else -> "https://t.bilibili.com/$oid?type=$type#reply$rpid"
            }
            val activityWeakRef = WeakReference(activity)
            AlertDialog.Builder(activity)
                .setMessage(httpUrl)
                .setTitle("选择分享方式")
                .setPositiveButton("复制链接") { _, _ ->
                    activityWeakRef.get()?.let { a ->
                        val clipboardManager: ClipboardManager = a
                            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", httpUrl))
                    }
                }
                .setNegativeButton("图片分享") { _, _ ->
                    activityWeakRef.get()?.let { a ->
                        XposedBridge.invokeOriginalMethod(
                            it.method, null, arrayOf(
                                a, oid, type, rpid
                            )
                        )
                    }
                }
                .setNeutralButton("复制 bilibili 链接") { _, _ ->
                    activityWeakRef.get()?.let { a ->
                        val clipboardManager: ClipboardManager = a
                            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", url))
                    }
                }
                .show()
            it.result = null
        }
    }

    private val ARTICLE_REGEX = "^bilibili://article/(\\d+)".toRegex()

    private fun hookArticle() {
        // 2023.04.24 尝试强制让 article 使用新 UI ，抛弃 Webview
        // 发现并非所有文章都能在新 UI 显示，一些文章仍然需要 Webview ，因此放弃
        findMethod("android.app.Instrumentation") {
            name == "execStartActivity"
        }.hookBefore {
            val intent = it.args[4] as? Intent ?: return@hookBefore
            val d = intent.data?:return@hookBefore
            ARTICLE_REGEX.find(d.toString())?.also { r ->
                val newUrl = "bilibili://opus/detail/${r.groupValues[1]}?opus_type=article"
                Log.d("replaced url: $newUrl")
                val newIntent = Intent()
                newIntent.component = ComponentName("tv.danmaku.bili", "tv.danmaku.bili.ui.intent.IntentHandlerActivity")
                newIntent.data = Uri.parse(newUrl)
                it.args[4] = newIntent
            }
        }
    }
}