package io.github.a13e300.myinjector

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hookBefore

// resolver activity 都有 FLAG_EXCLUDE_FROM_RECENTS ，这会在 ActivityRecord 的时候加到 intent 上去
// 因此 resolver 会拿到带有这个 flag 的原始 intent ，显然我们不希望每个通过 resolver 启动的 intent 都带上这个 flag
// framework 的 resolver activity 的解决办法是直接去掉这个 flag ，并且不管本来就有这个 flag 的情形
// 而 intentresolver 模块根本没管这事情，所以开出来的 activity 全都从最近任务隐藏了
// https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java;l=7982;drc=61197364367c9e404c7da6900658f1b16c42d0da
// https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/wm/ActivityRecord.java;l=2104;drc=89c9d2e12b1ca6fca37257311d879950bc1c31f1
// https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/com/android/internal/app/ResolverActivity.java;l=373;drc=98cb5d8d00b287f5fb37b361ca51dffec93a9675
// https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/IntentResolver/java/src/com/android/intentresolver/ResolverActivity.java;l=229;drc=dc5cdc53ca218c912d9ffe0d3fcc0a99538cdb60

class IntentResolverHandler : IHook() {
    override fun onHook() {
        val ra = findClass("com.android.intentresolver.ResolverActivity")
        ra.hookBefore("onCreate", Bundle::class.java) { param ->
            (param.thisObject as Activity).intent.run {
                flags = flags.and(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS.inv())
            }
        }
        val ca = findClass("com.android.intentresolver.ChooserActivity")
        ca.hookBefore("onCreate", Bundle::class.java) { param ->
            (param.thisObject as Activity).intent.run {
                flags = flags.and(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS.inv())
            }
        }
    }
}
