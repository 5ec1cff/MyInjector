package io.github.a13e300.myinjector.bili

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge

/** 可选的真实 APK 回归检查；样本不随仓库分发，环境变量见 docs/bilibili-compatibility.md。 */
class BiliApkCompatibilityTest {
    @Test
    fun locatesCoverViewerIn919() {
        val apk = System.getenv("BILI_NEW_SHARE_APK")
        val library = System.getenv("DEXKIT_HOST_LIBRARY")
        assumeTrue("Requires the 9.19.0 APK and a host DexKit JNI library", apk != null && library != null)
        System.load(library!!)
        DexKitBridge.create(apk!!).use { bridge ->
            assertEquals(mapOf(
                "intent" to "Lcom/bilibili/bplus/baseplus/activity/ImagesViewerActivity;->m9(Landroidx/fragment/app/FragmentActivity;Ljava/util/ArrayList;Ljava/util/ArrayList;Lcom/bilibili/bplus/imageviewer/CropType;Landroid/graphics/Rect;)Landroid/content/Intent;",
            ), locateBiliCover(bridge).mapValues { it.value.descriptor })
        }
    }

    @Test
    fun locatesSubtitlesIn919() {
        val apk = System.getenv("BILI_NEW_SHARE_APK")
        val library = System.getenv("DEXKIT_HOST_LIBRARY")
        assumeTrue("Requires the 9.19.0 APK and a host DexKit JNI library", apk != null && library != null)
        System.load(library!!)
        DexKitBridge.create(apk!!).use { bridge ->
            val members = locateBiliSubtitles(bridge)
            assertEquals("c", members.getValue("menu").memberName)
            assertEquals("a", members.getValue("click").memberName)
            assertEquals("e", members.getValue("dismiss").memberName)
            assertEquals("Lyud2;->a:Z", members.getValue("loading").descriptor)
            assertEquals("Lyud2;->n:Ljava/lang/Throwable;", members.getValue("error").descriptor)
            assertEquals("Lcom/bilibili/ship/theseus2/united/page/subtitle/TheseusSubtitleService;->d:Luqr1;",
                members.getValue("serviceMedia").descriptor)
            assertEquals("Lo062\$a;->a:Ljava/lang/String;", members.getValue("target").descriptor)
            assertEquals("e762", members.getValue("content").className)
            assertEquals("clickable-O2vRcR0\$default", members.getValue("clickable").memberName)
            assertEquals("Lt062;->a(Lo062;Ljava/util/List;)Lo062;", members.getValue("filter").descriptor)
            assertEquals("Lo062;->a:Ljava/util/List;", members.getValue("channelItems").descriptor)
            assertEquals("Lcom/bilibili/ship/theseus/united/page/toolbar/MenuService;->b:Landroidx/fragment/app/FragmentActivity;",
                members.getValue("toolbarContext").descriptor)
            assertEquals("Lcom/bilibili/ship/theseus/united/page/toolbar/MenuService;->n:Ltv/danmaku/biliplayerv2/PlayerContainer;",
                members.getValue("toolbarContainer").descriptor)
            assertEquals("Lcom/bilibili/ship/theseus/united/page/toolbar/MenuService\$createSettingGroup\$2;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;",
                members.getValue("toolbarGroup").descriptor)
            assertEquals("Lcom/bilibili/ship/theseus/united/page/toolbar/MenuService\$createSettingGroup\$2;->this$0:Lcom/bilibili/ship/theseus/united/page/toolbar/MenuService;",
                members.getValue("toolbarGroupOwner").descriptor)
            assertEquals("Lcom/bilibili/playerbizcommonv2/widget/setting/channel/VideoSettingButtonComponent;->a(Lgyo2;Landroidx/compose/runtime/Composer;I)V",
                members.getValue("toolbarItem").descriptor)
            assertEquals("clickableNoInteraction\$default", members.getValue("toolbarClickable").memberName)
            assertEquals("Ltv/danmaku/biliplayerv2/service/interact/biz/InteractLayerService;->g:Ltv/danmaku/biliplayerv2/PlayerContainer;",
                members.getValue("legacyContainer").descriptor)
        }
    }

    @Test
    fun locatesCommentRoutesIn919() {
        val apk = System.getenv("BILI_NEW_SHARE_APK")
        val library = System.getenv("DEXKIT_HOST_LIBRARY")
        assumeTrue("Requires the 9.19.0 APK and a host DexKit JNI library", apk != null && library != null)
        System.load(library!!)
        DexKitBridge.create(apk!!).use { bridge ->
            assertEquals(mapOf(
                "legacy" to "Ljok2;->intercept(Lcom/bilibili/lib/blrouter/RouteInterceptor\$Chain;)Lcom/bilibili/lib/blrouter/RouteResponse;",
                "theseus2" to "Lcom/bilibili/ship/theseus2/detail/route/ugc/b;->a(Lkntr/base/router/target/RouterTargetContext;Ljava/lang/String;Lpzf2;)Lkntr/base/router/target/RouterTarget;",
            ), locateBiliCommentRoutes(bridge).mapValues { it.value.descriptor })
        }
    }

    @Test
    fun locatesCommentMenuIn919() {
        val apk = System.getenv("BILI_NEW_SHARE_APK")
        val library = System.getenv("DEXKIT_HOST_LIBRARY")
        assumeTrue("Requires the 9.19.0 APK and a host DexKit JNI library", apk != null && library != null)
        System.load(library!!)
        DexKitBridge.create(apk!!).use { bridge ->
            val members = locateBiliCommentLink(bridge)
            assertEquals("kntr.common.comment.page.ui.CommentMoreMenuKt", members.getValue("menu").className)
            assertEquals(mapOf(
                "commentId" to "Lc1f1;->b:J",
                "rootCommentId" to "Lc1f1;->c:J",
                "oid" to "Lc1f1;->x:Ljava/lang/Long;",
                "type" to "Lc1f1;->y:Ljava/lang/Integer;",
                "entryTitle" to "Lv0f1;->a:Ljava/lang/String;",
                "entryIcon" to "Lv0f1;->b:Landroidx/compose/ui/graphics/painter/Painter;",
                "panel" to "Lkntr/common/comment/common/ui/moremenu/j;->a(ILandroidx/compose/runtime/Composer;Landroidx/compose/ui/Modifier;Ljava/util/List;Lkotlin/jvm/functions/Function0;)V",
                "sheet" to "Lkntr/common/comment/common/ui/moremenu/k;->f(ILandroidx/compose/runtime/Composer;Ljava/util/List;Lkotlin/jvm/functions/Function0;)V",
                "bubble" to "Lkntr/common/comment/common/ui/moremenu/MoreMenuBubbleKt;->c(Ljava/util/List;Landroidx/compose/ui/unit/IntRect;Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;I)V",
            ), members.filterKeys { it != "menu" }.mapValues { it.value.descriptor })
        }
    }

    @Test
    fun locatesNewShareIn919() {
        val apk = System.getenv("BILI_NEW_SHARE_APK")
        val library = System.getenv("DEXKIT_HOST_LIBRARY")
        assumeTrue("Requires the 9.19.0 APK and a host DexKit JNI library", apk != null && library != null)
        System.load(library!!)
        DexKitBridge.create(apk!!).use { bridge ->
            assertEquals("Lez52;->a()Z", locateBiliNewShareEnabled(bridge).descriptor)
            assertEquals(mapOf(
                "engine" to "Lkntr/common/share/core/ShareEngine;->a(Lwwe;Luwe;ZLkntr/common/share/core/model/ShareLocalContext;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
                "contentProvider" to "Lj162;->a(Lwwe;)Luwe;",
                "providerField" to "Lj162;->a:Lq162;",
                "findFactory" to "Lq162;->a(Lwwe;)Lkotlin/jvm/functions/Function0;",
                "baseInfoTitle" to "La062;->a:Ljava/lang/String;",
                "baseInfoContent" to "La062;->b:Ljava/lang/String;",
                "baseInfoLink" to "La062;->c:Ljava/lang/String;",
                "copyText" to "Ldr10;->g:Ljava/lang/String;",
            ), locateBiliNewShare(bridge).mapValues { it.value.descriptor })
        }
    }

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
            assertEquals(setOf(
                "Lcom/bilibili/ship/theseus/united/player/mediaplay/network/m;->f(Lkotlin/coroutines/jvm/internal/ContinuationImpl;)Ljava/lang/Object;",
                "Lcom/bilibili/ship/theseus2/united/player/mediaplay/network/NetworkShowToastService;->g(Lkotlin/coroutines/jvm/internal/ContinuationImpl;)Ljava/lang/Object;",
            ), locateBiliFreeDataGuides(bridge).map { it.descriptor }.toSet())
            assertEquals("java.lang.String", bridge.getMethodData(
                "Lcom/bapis/bilibili/app/view/v1/TFToast;->getTitle()Ljava/lang/String;"
            )?.returnTypeName)
            assertEquals("Lcom/bapis/bilibili/community/service/dm/v1/KDmViewReply;->r:Ljava/util/List;",
                locateBiliActivityMetaField(bridge).descriptor)
            assertEquals("Ltv/danmaku/bili/MainActivitySplashComponentExtKt;->d(Ltv/danmaku/bili/MainActivityV2;Ltv/danmaku/bili/splash/ad/model/SplashOrder;Z)Z",
                locateBiliSplash(bridge).descriptor)
            assertEquals("Lkntr/srcs/app/splash/core/f;->a(Lfo1/o;Ljava/util/List;Lkntr/srcs/app/splash/model/SplashSource;Ljava/lang/String;Z)Lfo1/q;",
                locateBiliNewSplash(bridge).descriptor)
        }
    }
}
