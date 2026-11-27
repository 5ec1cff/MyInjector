package io.github.a13e300.myinjector.xhs

import java.net.URLEncoder

internal fun buildXhsCommentUrl(noteId: String, commentId: String, type: String, token: String?): String {
    require(noteId.isNotBlank() && commentId.isNotBlank() && type.isNotBlank())
    fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    val params = linkedMapOf<String, String>()
    if (!token.isNullOrEmpty()) params["xsec_token"] = token
    params["anchorCommentId"] = commentId
    params["type"] = type
    return "https://www.xiaohongshu.com/discovery/item/${encode(noteId)}?" +
        params.entries.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
}

internal data class CommentShareText(val text: String, val html: String)

internal fun buildXhsCommentText(url: String, authorName: String?, content: String?): CommentShareText {
    fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    val label = if (authorName.isNullOrEmpty()) "评论：" else "@$authorName 的评论："
    val link = "<a href=\"${escape(url)}\">${escape(label)}</a>"
    if (content.isNullOrEmpty()) return CommentShareText(url, link)
    val htmlContent = escape(content).replace("\r\n", "\n").replace("\n", "<br>")
    return CommentShareText("$content\n$url", "$link<br><blockquote>$htmlContent</blockquote>")
}
