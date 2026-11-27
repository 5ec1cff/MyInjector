package io.github.a13e300.myinjector.xhs

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.luckypray.dexkit.DexKitBridge

/** 可选的真实 APK 检查，XHS_APK 提供 9.55.0 样本，DEXKIT_HOST_LIBRARY 提供主机 JNI 库。 */
class XhsApkCompatibilityTest {
    @Test fun locatesHomeTabScrollIn955() {
        val apk = System.getenv("XHS_APK")
        val library = System.getenv("DEXKIT_HOST_LIBRARY")
        assumeTrue("Requires XHS 9.55.0 and a host DexKit JNI library", apk != null && library != null)
        System.load(library!!)
        DexKitBridge.create(apk!!).use { bridge ->
            assertEquals(mapOf(
                "scrollAndRefresh" to "Lsmd/m0;->D0(Lsmd/m0;ZLcom/xingin/entities/e2;Ln2/g;Ln2/f;Ljava/lang/Integer;ILjava/lang/Object;)V",
                "scroll" to "Lyld/a;->b(Lyld/a;ZILjava/lang/Object;)V",
                "helper" to "Lsmd/m0;->H0:Lyld/a;",
                "recycler" to "Lyld/a;->a:Landroidx/recyclerview/widget/RecyclerView;",
            ), locateXhsHomeTabScroll(bridge).mapValues { it.value.descriptor })
        }
    }

    @Test fun locatesHomeCachePathsIn955() {
        val apk = System.getenv("XHS_APK")
        val library = System.getenv("DEXKIT_HOST_LIBRARY")
        assumeTrue("Requires XHS 9.55.0 and a host DexKit JNI library", apk != null && library != null)
        System.load(library!!)
        DexKitBridge.create(apk!!).use { bridge ->
            assertEquals(mapOf(
                "showCache" to "Lsmd/m0;->E0(Ljava/lang/String;)V",
                "refresh" to "Lsmd/m0;->v0(Lcom/xingin/entities/e2;ZLn2/g;Ln2/f;Ljava/lang/Integer;)V",
                "aheadLoad" to "Lmmd/t;->M4(Ljava/lang/String;IZ)V",
                "modelGetter" to "Lsmd/m0;->getMExplorePresenter()Lmmd/t;",
                "cachedNotes" to "Lmmd/t;->g:Ljava/util/ArrayList;",
                "view" to "Lmmd/t;->e:Lsmd/m0;",
                "channel" to "Lsmd/m0;->B:Lcom/xingin/entities/BaseChannelData;",
            ), locateXhsHomeCache(bridge).mapValues { it.value.descriptor })
            assertEquals("boolean", bridge.getClassData("com.xingin.entities.NoteItemBean")!!
                .fields.single { it.fieldName == "isFromCache" }.typeName)
            for (name in listOf("setHasLoadData", "setLoadFinish")) {
                assertEquals(listOf("boolean"), bridge.getClassData("smd.m0")!!
                    .methods.single { it.methodName == name }.paramTypeNames)
            }
        }
    }

    @Test fun locatesDoubleTapLikePathsIn955() {
        val apk = System.getenv("XHS_APK")
        val library = System.getenv("DEXKIT_HOST_LIBRARY")
        assumeTrue("Requires XHS 9.55.0 and a host DexKit JNI library", apk != null && library != null)
        System.load(library!!)
        DexKitBridge.create(apk!!).use { bridge ->
            val targets = locateXhsDoubleTapLike(bridge).mapValues { it.value.descriptor }
            assertEquals(mapOf(
                "imageClickType" to "Lcb2/b0;-><clinit>()V",
                "videoClickType" to "Lsi9/a;-><clinit>()V",
                "imageDispatch" to "Ltke/e;->onNext(Ljava/lang/Object;)V",
                "followPhoto0" to "Ljca/s;->onDoubleTap(Landroid/view/MotionEvent;)Z",
                "followPhoto1" to "Lkca/p;->onDoubleTap(Landroid/view/MotionEvent;)Z",
                "imageBrowser0" to "Lfy9/l\$c;->onDoubleTap(Landroid/view/MotionEvent;)Z",
                "imageBrowser1" to "Lfy9/o;->onDoubleTap(Landroid/view/MotionEvent;)Z",
                "imageBrowser2" to "Lfy9/s;->onDoubleTap(Landroid/view/MotionEvent;)Z",
                "imageBrowser3" to "Lkx9/u\$c;->onDoubleTap(Landroid/view/MotionEvent;)Z",
                "element" to "Los6/e;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;",
                "podcast" to "Ll0a/k0;->invokeSuspend(Ljava/lang/Object;)Ljava/lang/Object;",
            ), targets)
            // 这些回调在发送点击事件前播放动画，必须直接拦截
            val animation = bridge.findMethod {
                matcher { usingEqStrings("ImageMatrixPresenter", "showLikeLottie") }
            }.single()
            val animationCallers = animation.callers.map { it.descriptor }.toSet()
            assertEquals(setOf(targets.getValue("imageBrowser1"), targets.getValue("imageBrowser2")),
                animationCallers)
            val sources = locateXhsVideoDoubleTapSources(bridge, "si9.a").map { it.descriptor }.toSet()
            val classes = listOf("fy8/b1", "gab/t1", "jab/m2", "li9/z2", "si9/l4", "u6d/a1",
                "v39/r0", "xvb/t2", "y4d/k1", "yge/s3", "z3d/v3")
            assertEquals(classes.flatMap { name ->
                listOf("b", "d").map { "L$name;->$it(Landroid/view/MotionEvent;)V" }
            }.toSet(), sources)
        }
    }
}
