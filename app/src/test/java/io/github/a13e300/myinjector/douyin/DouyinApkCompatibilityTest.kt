package io.github.a13e300.myinjector.douyin

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge

/** 可选的真实 APK 检查，样本和主机 JNI 库由环境变量提供。 */
class DouyinApkCompatibilityTest {
    @Test fun locatesCommentEmojiDetailIn412() {
        val apk = System.getenv("DOUYIN_APK")
        val library = System.getenv("DEXKIT_HOST_LIBRARY")
        assumeTrue("Requires Douyin 41.2.0 and a host DexKit JNI library", apk != null && library != null)
        System.load(library!!)
        DexKitBridge.create(apk!!).use { bridge ->
            val method = locateDouyinEmojiDetail(bridge)
            assertEquals("Lcom/ss/android/ugc/aweme/emoji/service/EmojiServiceImpl;->LJJIII(LX/0nGp;)V",
                method.descriptor)
            val params = bridge.getClassData("X.0nGp")!!
            assertEquals(1, params.fields.count { it.typeName == "android.content.Context" })
            assertEquals(1, params.fields.count { it.typeName == "com.ss.android.ugc.aweme.emoji.model.Emoji" })
            val route = bridge.getClassData("com.bytedance.router.SmartRoute")!!
            assertEquals(1, route.fields.count { it.typeName == "android.content.Intent" })
            assertEquals(1, route.fields.count { it.typeName == "java.lang.String" })
            assertEquals(1, route.fields.count { it.typeName == "android.content.Context" })
        }
    }
}
