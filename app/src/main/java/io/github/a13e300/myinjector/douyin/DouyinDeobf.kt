package io.github.a13e300.myinjector.douyin

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge

internal fun locateDouyinEmojiDetail(bridge: DexKitBridge): ObfsInfo = bridge.findMethod {
    matcher {
        declaredClass("com.ss.android.ugc.aweme.emoji.service.EmojiServiceImpl")
        usingEqStrings("short_video_comment_emoji")
        paramCount(1)
        returnType("void")
    }
}.single().toObfsInfo()
