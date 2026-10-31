package io.github.a13e300.myinjector.bili

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge

/** 可选的真实 APK 回归检查；样本不随仓库分发，环境变量见 docs/bilibili-compatibility.md。 */
class BiliApkCompatibilityTest {
    @Test
    fun locatesBothPlayerImplementationsIn917() {
        val apk = System.getenv("BILI_917_APK")
        val library = System.getenv("DEXKIT_HOST_LIBRARY")
        assumeTrue("Requires the 9.17.0 APK and a host DexKit JNI library", apk != null && library != null)
        System.load(library!!)
        DexKitBridge.create(apk!!).use { bridge ->
            assertEquals(setOf(
                "Lcom/bilibili/ship/theseus/ugc/ad/DetailAdService\$1\$2;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;",
                "Lcom/bilibili/ship/theseus2/ugc/ad/AndroidUnderPlayerAdService\$observeAd\$4;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;",
            ), locateBiliUnderPlayerAds(bridge).map { it.descriptor }.toSet())
            assertEquals("Lcom/bilibili/lib/sharewrapper/a;->shareTo(Ljava/lang/String;Landroid/os/Bundle;)V",
                locateBiliShare(bridge).descriptor)
            assertEquals("Lkntr/app/ad/biz/videodetail/pausedpage/h;->getEnabled()Z",
                locateBiliPausePageConfig(bridge).descriptor)
            assertEquals("Lcom/bapis/bilibili/community/service/dm/v1/KDmViewReply;->r:Ljava/util/List;",
                locateBiliActivityMetaField(bridge).descriptor)
            assertEquals("Ltv/danmaku/bili/MainActivitySplashComponentExtKt;->d(Ltv/danmaku/bili/MainActivityV2;Ltv/danmaku/bili/splash/ad/model/SplashOrder;Z)Z",
                locateBiliSplash(bridge).descriptor)
            assertEquals("Lkntr/srcs/app/splash/core/f;->a(Lfo1/o;Ljava/util/List;Lkntr/srcs/app/splash/model/SplashSource;Ljava/lang/String;Z)Lfo1/q;",
                locateBiliNewSplash(bridge).descriptor)
        }
    }
}
