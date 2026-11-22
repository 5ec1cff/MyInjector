package io.github.a13e300.myinjector.douyin

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** 下载源文件，避免预览管线中的缩放和转码影响保存质量。 */
internal class DouyinImageSource {
    fun read(url: String, write: (InputStream, Long) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.useCaches = false
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("下载图片失败：HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { write(it, connection.contentLengthLong) }
        } finally {
            connection.disconnect()
        }
    }
}
