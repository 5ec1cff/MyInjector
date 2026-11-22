package io.github.a13e300.myinjector.douyin

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.TextView
import android.widget.Toast
import io.github.a13e300.myinjector.logE
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread

internal object DouyinImageSave {
    fun save(activity: Activity, button: TextView, urls: List<String>, imageSource: DouyinImageSource) {
        if (Build.VERSION.SDK_INT < 29 && activity.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0x4d49)
            Toast.makeText(activity, "授予存储权限后再次保存", Toast.LENGTH_SHORT).show()
            return
        }
        val context = activity.applicationContext
        button.isEnabled = false
        button.text = "保存中…"
        thread(name = "DouyinSaveEmoji") {
            val result = runCatching {
                var failure: IOException? = null
                for (url in urls.distinct()) {
                    var received = false
                    try {
                        imageSource.read(url) { input, size ->
                            received = true
                            saveSource(context, input, size)
                        }
                        return@runCatching
                    } catch (error: IOException) {
                        // 已取得文件后，写入失败不能降级为另一张静态图。
                        if (received) throw error
                        failure = error
                    }
                }
                throw failure ?: IOException("表情图片地址为空")
            }
            result.exceptionOrNull()?.let { logE("save Douyin comment emoji", it) }
            button.post {
                button.isEnabled = true
                button.text = "保存图片"
                Toast.makeText(context, if (result.isSuccess) "已保存到相册" else "保存失败",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveSource(context: Context, source: InputStream, expectedSize: Long) {
        source.buffered().use { input ->
            input.mark(64)
            val header = ByteArray(64)
            var count = 0
            while (count < header.size) {
                val size = input.read(header, count, header.size - count)
                if (size < 0) break
                count += size
            }
            input.reset()
            val format = EmojiImageFormat.detect(header.copyOf(count))
                ?: throw IOException("无法识别表情图片格式")
            saveImage(context, input, expectedSize, format)
        }
    }

    private fun saveImage(context: Context, input: InputStream, expectedSize: Long,
                          format: EmojiImageFormat) {
        val name = "douyin_emoji_${System.currentTimeMillis()}.${format.extension}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Douyin")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val directory = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "Douyin")
                if (!directory.isDirectory && !directory.mkdirs()) {
                    throw IOException("无法创建图片目录")
                }
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(directory, name).absolutePath)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建相册文件")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("无法写入相册文件")
            // 所有格式都复制源文件，不解码、缩放、转码或添加水印。
            output.use {
                val size = input.copyTo(it)
                if (expectedSize >= 0 && size != expectedSize) {
                    throw IOException("图片下载不完整")
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
}
