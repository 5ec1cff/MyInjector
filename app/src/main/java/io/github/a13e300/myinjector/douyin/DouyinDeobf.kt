package io.github.a13e300.myinjector.douyin

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldUsingType

internal fun locateDouyinEmojiDetail(bridge: DexKitBridge): ObfsInfo = bridge.findMethod {
    matcher {
        declaredClass("com.ss.android.ugc.aweme.emoji.service.EmojiServiceImpl")
        usingEqStrings("short_video_comment_emoji")
        paramCount(1)
        returnType("void")
    }
}.single().toObfsInfo()

internal fun locateDouyinImageDownload(bridge: DexKitBridge, downloadField: String): ObfsInfo = bridge.findMethod {
    matcher {
        declaredClass("com.ss.android.ugc.aweme.comment.model.CommentImageStruct")
        paramCount(0)
        returnType("com.ss.android.ugc.aweme.base.model.UrlModel")
    }
}.single { method ->
    method.usingFields.any {
        it.usingType == FieldUsingType.Read && it.field.className == method.className &&
            it.field.name == downloadField
    }
}.toObfsInfo()
