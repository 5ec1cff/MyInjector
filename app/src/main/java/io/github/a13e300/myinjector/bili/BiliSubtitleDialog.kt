package io.github.a13e300.myinjector.bili

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal fun showBiliSubtitleDialog(activity: Activity, tracks: List<BiliSubtitleTrack>) {
    if (activity.isFinishing || activity.isDestroyed || tracks.isEmpty()) return
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val padding = (20 * activity.resources.displayMetrics.density).toInt()
    val text = TextView(activity).apply {
        textSize = 16f
        setPadding(padding, padding / 2, padding, padding)
        setTextIsSelectable(true)
    }
    val languages = Spinner(activity).apply {
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, tracks.map { it.title })
        visibility = if (tracks.size > 1) View.VISIBLE else View.GONE
    }
    val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(languages)
        addView(ScrollView(activity).apply { addView(text) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.55f).toInt()
        ))
    }
    val dialog = AlertDialog.Builder(activity).setTitle("查看字幕").setView(content)
        .setPositiveButton("复制全部", null).setNeutralButton("重试", null)
        .setNegativeButton("关闭", null).create()
    var job: Job? = null
    var selected = 0
    var loadedText: String? = null
    fun load() {
        job?.cancel()
        loadedText = null
        text.text = "字幕加载中…"
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        val track = tracks[selected]
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = if (track.url.startsWith("//")) "https:${track.url}" else track.url
                    val connection = URL(url).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 10_000
                        connection.readTimeout = 20_000
                        connection.setRequestProperty("Referer", "https://www.bilibili.com/")
                        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
                        val source = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        biliSubtitleText(source, track.format)
                    } finally {
                        connection.disconnect()
                    }
                }
                text.text = result.ifBlank { "字幕内容为空" }
                loadedText = result.takeIf(String::isNotBlank)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = loadedText != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                text.text = "字幕加载失败：${e.message ?: "未知错误"}\n可点击“重试”重新加载。"
            }
        }
    }
    languages.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (position != selected) {
                selected = position
                load()
            }
        }
    }
    val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityDestroyed(destroyed: Activity) { if (destroyed === activity) dialog.dismiss() }
        override fun onActivityCreated(activity: Activity, state: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
    }
    dialog.setOnDismissListener {
        scope.cancel()
        activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
    }
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            loadedText?.let {
                activity.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("视频字幕", it))
                Toast.makeText(activity, "已复制全部字幕", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { load() }
        load()
    }
    dialog.show()
    activity.application.registerActivityLifecycleCallbacks(lifecycle)
}
