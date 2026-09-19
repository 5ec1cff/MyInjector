package io.github.a13e300.myinjector.telegram

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.hookAllNopIf
import io.github.a13e300.myinjector.arch.setObj
import java.util.WeakHashMap

class DisableProfileAvatarBlur : DynHook() {

    override fun isFeatureEnabled(): Boolean =
        TelegramHandler.settings.disableProfileAvatarBlur

    private val extendAvatar: Boolean
        get() = TelegramHandler.settings.disableProfileAvatarBlurExtendAvatar

    /**
     * Exteragram 包名。
     *
     * 方案二只给 Exteragram 使用。
     * 其他官方或第三方 Telegram 继续使用原方案。
     */
    private val isExteragram: Boolean by lazy {
        getCurrentPackageName() == "com.exteragram.messenger"
    }

    private fun getCurrentPackageName(): String? {
        return runCatching {
            val activityThreadClass =
                Class.forName("android.app.ActivityThread")

            activityThreadClass
                .getMethod("currentPackageName")
                .invoke(null) as? String
        }.getOrNull()
            ?: runCatching {
                val activityThreadClass =
                    Class.forName("android.app.ActivityThread")

                val currentApplication = activityThreadClass
                    .getMethod("currentApplication")
                    .invoke(null)

                currentApplication
                    ?.javaClass
                    ?.getMethod("getPackageName")
                    ?.invoke(currentApplication) as? String
            }.getOrNull()
    }

    /**
     * 安全调用 AndroidUtilities.dpf2。
     */
    private fun dpf2(
        androidUtilities: Class<*>?,
        value: Float
    ): Float {
        return runCatching {
            androidUtilities?.callS("dpf2", value) as? Float
        }.getOrNull() ?: value
    }

    private fun lerp(
        start: Float,
        end: Float,
        progress: Float
    ): Float {
        return start + progress * (end - start)
    }

    /**
     * Telegram / Exteragram 原本的 actionsView 样式。
     */
    private data class ActionsOriginalStyle(
        val actionsColor: Int?,
        val paintColor: Int,
        val paintAlpha: Int
    )

    /**
     * 记录 actionsView 是否处于下拉/头像展开状态。
     */
    private val actionsPulledState =
        WeakHashMap<View, Boolean>()

    /**
     * 缓存 Exteragram 原本的 actionsView 样式。
     */
    private val originalActionsStyleMap =
        WeakHashMap<View, ActionsOriginalStyle>()

    /**
     * 记录 actionsView 是否被本模块强制设置了样式。
     */
    private val forcedActionsStyleMap =
        WeakHashMap<View, Boolean>()

    /**
     * 防止本模块调用 setActionsColor 时被自己的 Hook 再次处理。
     */
    private var changingActionsColor = false

    private fun markActionsPulled(
        actionsViewObj: Any?,
        pulled: Boolean
    ) {
        val actionsView = actionsViewObj as? View ?: return
        actionsPulledState[actionsView] = pulled
    }

    private fun isActionsPulled(
        actionsViewObj: Any?
    ): Boolean {
        val actionsView = actionsViewObj as? View ?: return false
        return actionsPulledState[actionsView] == true
    }

    private fun isForcedByUs(
        actionsViewObj: Any?
    ): Boolean {
        val actionsView = actionsViewObj as? View ?: return false
        return forcedActionsStyleMap[actionsView] == true
    }

    private fun setForcedByUs(
        actionsViewObj: Any?,
        forced: Boolean
    ) {
        val actionsView = actionsViewObj as? View ?: return
        forcedActionsStyleMap[actionsView] = forced
    }

    /**
     * 缓存当前 actionsView 原本样式。
     */
    private fun cacheOriginalActionsStyle(
        actionsViewObj: Any?,
        colorFromSetActionsColor: Int? = null
    ) {
        val actionsView = actionsViewObj as? View ?: return

        if (isForcedByUs(actionsViewObj)) {
            return
        }

        val paintInfo = runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            paint.color to paint.alpha
        }.getOrNull() ?: return

        val oldStyle = originalActionsStyleMap[actionsView]

        originalActionsStyleMap[actionsView] = ActionsOriginalStyle(
            actionsColor = colorFromSetActionsColor
                ?: oldStyle?.actionsColor,
            paintColor = paintInfo.first,
            paintAlpha = paintInfo.second
        )
    }

    /**
     * 恢复 actionsView 原本样式。
     */
    private fun restoreActionsOriginalStyle(
        actionsViewObj: Any?,
        invalidate: Boolean = true
    ) {
        val actionsView = actionsViewObj as? View ?: return
        val originalStyle = originalActionsStyleMap[actionsView]

        runCatching {
            actionsViewObj.setObj("radialGradient", null)
        }

        if (originalStyle != null) {
            runCatching {
                val paint = actionsViewObj.getObjAs<Paint>("paint")
                paint.color = originalStyle.paintColor
                paint.alpha = originalStyle.paintAlpha
            }

            val originalColor = originalStyle.actionsColor

            if (originalColor != null) {
                changingActionsColor = true

                try {
                    runCatching {
                        actionsViewObj.call(
                            "setActionsColor",
                            originalColor,
                            false
                        )
                    }
                } finally {
                    changingActionsColor = false
                }
            }
        }

        setForcedByUs(actionsViewObj, false)

        if (invalidate) {
            actionsView.invalidate()
        }
    }

    /**
     * 下拉时强制按钮在头像背景上保持可读。
     */
    private fun forceActionsReadableOnAvatar(
        actionsViewObj: Any?,
        invalidate: Boolean = true
    ) {
        val actionsView = actionsViewObj as? View ?: return

        if (!isForcedByUs(actionsViewObj)) {
            cacheOriginalActionsStyle(actionsViewObj)
        }

        runCatching {
            actionsViewObj.setObj("radialGradient", null)
        }

        runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            paint.color = Color.BLACK
            paint.alpha = 88
        }

        changingActionsColor = true

        try {
            runCatching {
                actionsViewObj.call(
                    "setActionsColor",
                    Color.WHITE,
                    false
                )
            }
        } finally {
            changingActionsColor = false
        }

        setForcedByUs(actionsViewObj, true)

        if (invalidate) {
            actionsView.invalidate()
        }
    }

    /**
     * 根据下拉状态应用 actionsView 样式。
     */
    private fun applyActionsStyleByState(
        actionsViewObj: Any?,
        invalidate: Boolean = true
    ) {
        if (isActionsPulled(actionsViewObj)) {
            forceActionsReadableOnAvatar(
                actionsViewObj,
                invalidate
            )
        } else if (isForcedByUs(actionsViewObj)) {
            restoreActionsOriginalStyle(
                actionsViewObj,
                invalidate
            )
        }
    }

    /**
     * 禁用 musicView / suggestionView 的模糊绘制。
     */
    private fun disableNonActionBlurView(viewObj: Any?) {
        runCatching {
            viewObj?.call("drawingBlur", false)
        }

        if (viewObj is View) {
            viewObj.invalidate()
        }
    }

    override fun onHook() {
        /*
         * 第一阶段：优先安装真正负责禁用头像模糊的 Hook。
         *
         * 即使后面的 TopView 或 OverlaysView 不存在，
         * 也不能影响这个主 Hook。
         */
        val profileGalleryBlurViewClass = findClassOrNull(
            "org.telegram.ui.Components.ProfileGalleryBlurView"
        )

        if (isExteragram) {
            if (profileGalleryBlurViewClass != null) {
                hookExteragramBlurDraw(profileGalleryBlurViewClass)
            }

            hookExteragramProfileActionsView()
        } else {
            profileGalleryBlurViewClass?.hookAllNopIf(
                "draw",
                ::isEnabled
            )
        }

        /*
         * 第二阶段：ProfileActivity 相关辅助修复。
         */
        val profileActivity = findClassOrNull(
            "org.telegram.ui.ProfileActivity"
        ) ?: return

        val topViewClass = findClassOrNull(
            "org.telegram.ui.ProfileActivity\$TopView"
        )

        val overlaysViewClass = findClassOrNull(
            "org.telegram.ui.ProfileActivity\$OverlaysView"
        )

        val profileGalleryViewClass = findClassOrNull(
            "org.telegram.ui.Components.ProfileGalleryView"
        )

        val androidUtilities = findClassOrNull(
            "org.telegram.messenger.AndroidUtilities"
        )

        /*
         * move shadow up
         */
        profileActivity.hookAllAfter(
            "updateExtraViews",
            cond = ::isEnabled
        ) { param ->
            if (extendAvatar) {
                return@hookAllAfter
            }

            val pa = param.thisObject

            val overlaysView = runCatching {
                pa.getObjAsN<View>("overlaysView")
            }.getOrNull() ?: return@hookAllAfter

            val actionsView = runCatching {
                pa.getObjAsN<View>("actionsView")
            }.getOrNull() ?: return@hookAllAfter

            val isPulledDown = runCatching {
                pa.getObjAs<Boolean>("isPulledDown")
            }.getOrDefault(false)

            if (isExteragram) {
                if (!isForcedByUs(actionsView)) {
                    cacheOriginalActionsStyle(actionsView)
                }

                markActionsPulled(
                    actionsView,
                    isPulledDown
                )
            }

            if (isPulledDown) {
                val overlaysLp = overlaysView.layoutParams
                overlaysLp.height -= actionsView.height
                overlaysView.requestLayout()
            }

            if (isExteragram) {
                applyActionsStyleByState(actionsView)
            }
        }

        /*
         * fix background turn black
         *
         * TopView 在部分 Telegram 版本中可能不存在。
         */
        topViewClass?.hookAllBefore(
            "setBackgroundColor",
            cond = ::isEnabled
        ) { param ->
            if (extendAvatar) {
                return@hookAllBefore
            }

            val color = param.args.getOrNull(0) as? Int
                ?: return@hookAllBefore

            if (color == Color.BLACK) {
                val calledFromAnimationEnd =
                    Throwable().stackTrace.any {
                        it.methodName == "onAnimationEnd"
                    }

                if (calledFromAnimationEnd) {
                    param.result = null
                }
            }
        }

        /*
         * let avatar gallery expand to actions area
         */
        profileGalleryViewClass?.hookAllCAfter(
            cond = ::isEnabled
        ) { param ->
            if (!extendAvatar) {
                return@hookAllCAfter
            }

            val profileGalleryView =
                param.thisObject as? View
                    ?: return@hookAllCAfter

            profileGalleryView.setPadding(0, 0, 0, 0)
        }

        /*
         * set proper shadow
         */
        overlaysViewClass?.hookAllAfter(
            "onSizeChanged",
            cond = ::isEnabled
        ) { param ->
            if (!extendAvatar) {
                return@hookAllAfter
            }

            val bottomOverlayGradient = runCatching {
                param.thisObject.getObjAs<GradientDrawable>(
                    "bottomOverlayGradient"
                )
            }.getOrNull() ?: return@hookAllAfter

            val bottomOverlayRect = runCatching {
                param.thisObject.getObjAs<Rect>(
                    "bottomOverlayRect"
                )
            }.getOrNull() ?: return@hookAllAfter

            val actionsExtraHeight = runCatching {
                val pa = param.thisObject.getObj("this\$0")
                pa.call("getActionsExtraHeight") as? Int
            }.getOrNull() ?: return@hookAllAfter

            bottomOverlayRect.top -= actionsExtraHeight

            val newBounds = Rect(bottomOverlayGradient.bounds)
            newBounds.top -= actionsExtraHeight
            newBounds.bottom = bottomOverlayRect.top
            bottomOverlayGradient.bounds = newBounds
        }

        /*
         * fix animation of expanding avatar
         */
        profileActivity.hookAllAfter(
            "setAvatarExpandProgress",
            cond = ::isEnabled
        ) { param ->
            if (!extendAvatar) {
                return@hookAllAfter
            }

            val pa = param.thisObject

            val avatarsViewPager = runCatching {
                pa.getObjAs<View>("avatarsViewPager")
            }.getOrNull() ?: return@hookAllAfter

            val value = runCatching {
                pa.getObjAs<Float>("currentExpandAnimatorValue")
            }.getOrNull() ?: return@hookAllAfter

            val avatarContainer = runCatching {
                pa.getObjAs<View>("avatarContainer")
            }.getOrNull() ?: return@hookAllAfter

            val avatarScale = runCatching {
                pa.getObjAs<Float>("avatarScale")
            }.getOrNull() ?: return@hookAllAfter

            if (avatarScale == 0f) {
                return@hookAllAfter
            }

            val layoutParams = avatarContainer.layoutParams
                as? ViewGroup.MarginLayoutParams
                ?: return@hookAllAfter

            val realSize = avatarsViewPager.height

            val newSize = lerp(
                dpf2(androidUtilities, 100f),
                realSize / avatarScale,
                value
            ).toInt()

            if (newSize > 0) {
                layoutParams.height = newSize
                layoutParams.width = newSize
                layoutParams.leftMargin = 0

                runCatching {
                    pa.call("fixAvatarImageInCenter")
                }

                avatarContainer.requestLayout()
            }

            if (isExteragram) {
                val actionsView = runCatching {
                    pa.getObj("actionsView")
                }.getOrNull()

                val isPulledDown = runCatching {
                    pa.getObjAs<Boolean>("isPulledDown")
                }.getOrDefault(false)

                if (!isForcedByUs(actionsView)) {
                    cacheOriginalActionsStyle(actionsView)
                }

                markActionsPulled(
                    actionsView,
                    isPulledDown
                )

                applyActionsStyleByState(actionsView)
            }
        }

        /*
         * needLayout 期间同步头像尺寸。
         */
        profileActivity.hookAllAfter(
            "needLayout",
            cond = ::isEnabled
        ) { param ->
            if (!extendAvatar) {
                return@hookAllAfter
            }

            val pa = param.thisObject

            val openAnimationInProgress = runCatching {
                pa.getObjAs<Boolean>("openAnimationInProgress")
            }.getOrDefault(false)

            val playProfileAnimation = runCatching {
                pa.getObjAs<Int>("playProfileAnimation")
            }.getOrDefault(0)

            val actionsView = if (isExteragram) {
                runCatching {
                    pa.getObj("actionsView")
                }.getOrNull()
            } else {
                null
            }

            val isPulledDown = if (isExteragram) {
                runCatching {
                    pa.getObjAs<Boolean>("isPulledDown")
                }.getOrDefault(false)
            } else {
                false
            }

            if (isExteragram) {
                if (!isForcedByUs(actionsView)) {
                    cacheOriginalActionsStyle(actionsView)
                }

                markActionsPulled(
                    actionsView,
                    isPulledDown
                )
            }

            if (
                openAnimationInProgress &&
                playProfileAnimation == 2
            ) {
                val avatarsViewPager = runCatching {
                    pa.getObjAs<View>("avatarsViewPager")
                }.getOrNull()

                val value = runCatching {
                    pa.getObjAs<Float>(
                        "currentExpandAnimatorValue"
                    )
                }.getOrNull()

                val avatarContainer = runCatching {
                    pa.getObjAs<View>("avatarContainer")
                }.getOrNull()

                val avatarScale = runCatching {
                    pa.getObjAs<Float>("avatarScale")
                }.getOrNull()

                if (
                    avatarsViewPager != null &&
                    value != null &&
                    avatarContainer != null &&
                    avatarScale != null &&
                    avatarScale != 0f
                ) {
                    val layoutParams =
                        avatarContainer.layoutParams
                            as? ViewGroup.MarginLayoutParams

                    if (layoutParams != null) {
                        val realSize = avatarsViewPager.height

                        val newSize = lerp(
                            dpf2(androidUtilities, 100f),
                            realSize / avatarScale,
                            value
                        ).toInt()

                        if (newSize > 0) {
                            layoutParams.height = newSize
                            layoutParams.width = newSize
                            layoutParams.leftMargin = 0

                            runCatching {
                                pa.call(
                                    "fixAvatarImageInCenter"
                                )
                            }

                            avatarContainer.requestLayout()
                        }
                    }
                }
            }

            if (isExteragram) {
                applyActionsStyleByState(actionsView)
            }
        }

        /*
         * updateBackgroundPaint
         *
         * TopView 不存在时自动跳过。
         */
        topViewClass?.hookAllAfter(
            "updateBackgroundPaint",
            cond = ::isEnabled
        ) { param ->
            val pa = runCatching {
                param.thisObject.getObj("this\$0")
            }.getOrNull() ?: return@hookAllAfter

            val actionsView = runCatching {
                pa.getObj("actionsView")
            }.getOrNull() ?: return@hookAllAfter

            val isPulledDown = runCatching {
                pa.getObjAs<Boolean>("isPulledDown")
            }.getOrDefault(false)

            if (isExteragram) {
                if (!isForcedByUs(actionsView)) {
                    cacheOriginalActionsStyle(actionsView)
                }

                markActionsPulled(
                    actionsView,
                    isPulledDown
                )

                applyActionsStyleByState(actionsView)
            } else {
                if (!extendAvatar) {
                    return@hookAllAfter
                }

                if (isPulledDown) {
                    runCatching {
                        actionsView.setObj(
                            "radialGradient",
                            null
                        )
                    }

                    runCatching {
                        actionsView.call(
                            "setActionsColor",
                            Color.BLACK,
                            false
                        )
                    }

                    runCatching {
                        actionsView
                            .getObjAs<Paint>("paint")
                            .alpha = 40
                    }

                    (actionsView as? View)?.invalidate()
                }
            }
        }
    }

    /**
     * Exteragram 专用 draw Hook。
     */
    private fun hookExteragramBlurDraw(
        blurViewClass: Class<*>
    ) {
        blurViewClass.hookAllBefore(
            "draw",
            cond = ::isEnabled
        ) { param ->
            val blurView = param.thisObject

            val actionsView = runCatching {
                blurView.getObj("actionsView")
            }.getOrNull()

            val musicView = runCatching {
                blurView.getObj("musicView")
            }.getOrNull()

            val suggestionView = runCatching {
                blurView.getObj("suggestionView")
            }.getOrNull()

            applyActionsStyleByState(actionsView)

            disableNonActionBlurView(musicView)
            disableNonActionBlurView(suggestionView)

            param.result = null
        }
    }

    /**
     * Exteragram 专用 ProfileActionsView Hook。
     */
    private fun hookExteragramProfileActionsView() {
        val profileActionsViewClass = findClassOrNull(
            "org.telegram.ui.Components.ProfileActionsView"
        ) ?: return

        profileActionsViewClass.hookAllBefore(
            "setActionsColor",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) {
                return@hookAllBefore
            }

            val actionsViewObj = param.thisObject

            val color = param.args.getOrNull(0) as? Int
                ?: return@hookAllBefore

            if (!isForcedByUs(actionsViewObj)) {
                cacheOriginalActionsStyle(
                    actionsViewObj,
                    color
                )
            }
        }

        profileActionsViewClass.hookAllAfter(
            "setActionsColor",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) {
                return@hookAllAfter
            }

            val actionsViewObj = param.thisObject

            if (isActionsPulled(actionsViewObj)) {
                forceActionsReadableOnAvatar(actionsViewObj)
            }
        }
    }
}
