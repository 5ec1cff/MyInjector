package io.github.a13e300.myinjector.telegram

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookNopIf
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

class DisableProfileAvatarBlur : MyDynHook("disableProfileAvatarBlur") {

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.disableProfileAvatarBlur

    private val extendAvatar: Boolean
        get() = TelegramHandler.settings.disableProfileAvatarBlurExtendAvatar

    /**
     * Exteragram 包名。
     *
     * 方案二只给 Exteragram 使用。
     * 其他官方 / 第三方 Telegram 继续使用原方案一。
     */
    private val isExteragram: Boolean by lazy {
        loadPackageParam.appInfo.packageName == "com.exteragram.messenger"
    }

    private lateinit var memberFields: Map<String, Field>
    private lateinit var memberMethods: Map<String, Method>

    private fun read(obj: Any?, key: String): Any? = memberFields[key]?.get(obj)

    private inline fun <reified T> readAs(obj: Any?, key: String): T = read(obj, key) as T

    private fun invoke(key: String, obj: Any?, vararg args: Any?): Any? {
        val method = memberMethods.getValue(key)
        return if (Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.firstOrNull() == method.declaringClass
        ) {
            // R8 may prepend the former receiver to a static method's arguments.
            method.invoke(null, obj, *args)
        } else {
            method.invoke(obj, *args)
        }
    }

    private fun setActionsColor(actionsView: Any, color: Int, hasColorById: Boolean) {
        if ("actionsSetActionsColor" in memberMethods) {
            invoke("actionsSetActionsColor", actionsView, color, hasColorById)
        } else {
            // setActionsColor can be inlined into TopView.onDraw; checkPaints is empty.
            memberFields.getValue("actionsColor").setInt(actionsView, color)
            memberFields.getValue("actionsHasColorById").setBoolean(actionsView, hasColorById)
            invoke("actionsCreateColorShader", actionsView)
        }
    }

    /**
     * =========================
     * Exteragram 方案二相关状态
     * =========================
     */

    /**
     * Telegram / Exteragram 原本的 actionsView 样式。
     *
     * 不同主题、不同 accent、大会员背景、个人主页背景下，
     * actionsView 的背景和图标颜色都可能不同。
     *
     * 所以这里必须缓存原样，不能写死白色背景或蓝绿色图标。
     */
    private data class ActionsOriginalStyle(
        val actionsColor: Int?,
        val paintColor: Int,
        val paintAlpha: Int
    )

    /**
     * 记录 actionsView 是否处于下拉/头像展开状态。
     */
    private val actionsPulledState = WeakHashMap<View, Boolean>()

    /**
     * 缓存 Exteragram 原本的 actionsView 样式。
     */
    private val originalActionsStyleMap = WeakHashMap<View, ActionsOriginalStyle>()

    /**
     * 记录某个 actionsView 当前是否被我们强制改成了头像浮层样式。
     */
    private val forcedActionsStyleMap = WeakHashMap<View, Boolean>()

    /**
     * 防止我们自己调用 setActionsColor(...) 时，被 hook 误缓存。
     */
    private var changingActionsColor = false
    private val inTopViewDraw = ThreadLocal<Boolean>()

    private fun markActionsPulled(actionsViewObj: Any?, pulled: Boolean) {
        val actionsView = actionsViewObj as? View ?: return
        actionsPulledState[actionsView] = pulled
    }

    private fun isActionsPulled(actionsViewObj: Any?): Boolean {
        val actionsView = actionsViewObj as? View ?: return false
        return actionsPulledState[actionsView] == true
    }

    private fun isForcedByUs(actionsViewObj: Any?): Boolean {
        val actionsView = actionsViewObj as? View ?: return false
        return forcedActionsStyleMap[actionsView] == true
    }

    private fun setForcedByUs(actionsViewObj: Any?, forced: Boolean) {
        val actionsView = actionsViewObj as? View ?: return
        forcedActionsStyleMap[actionsView] = forced
    }

    /**
     * Exteragram：缓存当前 actionsView 原本样式。
     *
     * 如果当前样式已经是我们强制设置的黑色半透明，就不能缓存；
     * 否则会把错误状态缓存进去。
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
            val paint = readAs<Paint>(actionsViewObj, "actionsPaint")
            paint.color to paint.alpha
        }.getOrNull() ?: return

        originalActionsStyleMap[actionsView] = ActionsOriginalStyle(
            actionsColor = colorFromSetActionsColor ?: readAs<Int>(actionsViewObj, "actionsColor"),
            paintColor = paintInfo.first,
            paintAlpha = paintInfo.second
        )
    }

    /**
     * Exteragram：恢复原本 actionsView 样式。
     */
    private fun restoreActionsOriginalStyle(actionsViewObj: Any?, invalidate: Boolean = true) {
        val actionsView = actionsViewObj as? View ?: return
        val originalStyle = originalActionsStyleMap[actionsView]

        runCatching {
            memberFields.getValue("actionsRadialGradient").set(actionsViewObj, null)
        }

        if (originalStyle != null) {
            runCatching {
                val paint = readAs<Paint>(actionsViewObj, "actionsPaint")
                paint.color = originalStyle.paintColor
                paint.alpha = originalStyle.paintAlpha
            }

            val originalColor = originalStyle.actionsColor
            if (originalColor != null) {
                runCatching {
                    changingActionsColor = true
                    setActionsColor(actionsViewObj, originalColor, false)
                }.also {
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
     * Exteragram：下拉/头像展开状态，强制按钮在头像图上可读。
     */
    private fun forceActionsReadableOnAvatar(actionsViewObj: Any?, invalidate: Boolean = true) {
        val actionsView = actionsViewObj as? View ?: return

        if (!isForcedByUs(actionsViewObj)) {
            cacheOriginalActionsStyle(actionsViewObj)
        }

        runCatching {
            memberFields.getValue("actionsRadialGradient").set(actionsViewObj, null)
        }

        runCatching {
            val paint = readAs<Paint>(actionsViewObj, "actionsPaint")
            paint.color = Color.BLACK
            paint.alpha = 88
        }

        runCatching {
            changingActionsColor = true
            setActionsColor(actionsViewObj, Color.WHITE, false)
        }.also {
            changingActionsColor = false
        }

        setForcedByUs(actionsViewObj, true)

        if (invalidate) {
            actionsView.invalidate()
        }
    }

    /**
     * Exteragram：根据状态应用样式。
     */
    private fun applyActionsStyleByState(actionsViewObj: Any?, invalidate: Boolean = true) {
        if (isActionsPulled(actionsViewObj)) {
            forceActionsReadableOnAvatar(actionsViewObj, invalidate)
        } else {
            if (isForcedByUs(actionsViewObj)) {
                restoreActionsOriginalStyle(actionsViewObj, invalidate)
            }
        }
    }

    private fun disableNonActionBlurView(viewObj: Any?, key: String) {
        if (viewObj == null) return
        memberMethods[key]?.let { method ->
            if (method.parameterCount == 0) invoke(key, viewObj)
            else invoke(key, viewObj, false)
        }
        (viewObj as? View)?.invalidate()
    }

    override fun onHook() {
        val members = profileAvatarBlurMembers(TelegramHandler.creator)
        memberFields = members.filterValues { it.descriptor.isNotEmpty() && '(' !in it.descriptor }
            .mapValues { DexField(it.value.descriptor).getFieldInstance(classLoader).also { it.isAccessible = true } }
        memberMethods = members.filterValues { '(' in it.descriptor }
            .mapValues { DexMethod(it.value.descriptor).getMethodInstance(classLoader).also { it.isAccessible = true } }

        /*
         * =========================
         * 头像模糊禁用逻辑
         * =========================
         *
         * 方案一：非 Exteragram
         *   直接 NOP draw，保持原版逻辑。
         *
         * 方案二：Exteragram
         *   不能直接 NOP 后不管，否则 actionsView 样式会异常；
         *   需要先处理 actionsView / musicView / suggestionView，再阻止 draw。
         */
        if (isExteragram) {
            hookExteragramBlurDraw()
        } else {
            memberMethods.getValue("blurDraw").hookNopIf(::isEnabled)
        }
        hookProfileActionsView()

        /*
         * move shadow up
         */
        memberMethods.getValue("updateExtraViews").hookAfter(
            cond = ::isEnabled
        ) { param ->
            if (extendAvatar) {
                return@hookAfter
            }

            val pa = param.thisObject
            val overlaysView = (read(pa, "overlaysView") as? View) ?: return@hookAfter
            val actionsView = (read(pa, "actionsView") as? View) ?: return@hookAfter
            val isPulledDown = readAs<Boolean>(pa, "isPulledDown")

            if (isExteragram) {
                if (!isForcedByUs(actionsView)) {
                    cacheOriginalActionsStyle(actionsView)
                }

                markActionsPulled(actionsView, isPulledDown)
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
         */
        memberMethods.getValue("topSetBackgroundColor").hookBefore(cond = ::isEnabled) { param ->
            if (extendAvatar) {
                return@hookBefore
            }

            if (param.args[0] == Color.BLACK) {
                if (Throwable().stackTrace.any { it.methodName == "onAnimationEnd" }) {
                    param.result = null
                }
            }
        }

        /*
         * let avatar gallery expand to actions area
         */
        findClass(members.getValue("galleryClass").className)
            .hookAllCAfter(cond = ::isEnabled) { param ->
                if (!extendAvatar) {
                    return@hookAllCAfter
                }

                (param.thisObject as View).setPadding(0, 0, 0, 0)
            }

        /*
         * set proper shadow
         */
        memberMethods.getValue("overlaysOnSizeChanged").hookAfter(
            cond = ::isEnabled
        ) { param ->
            if (!extendAvatar) {
                return@hookAfter
            }

            val bottomOverlayGradient =
                readAs<GradientDrawable>(param.thisObject, "bottomOverlayGradient")
            val bottomOverlayRect = readAs<Rect>(param.thisObject, "bottomOverlayRect")
            val actionsExtraHeight =
                invoke("getActionsExtraHeight", read(param.thisObject, "overlaysProfileActivity")) as Int

            bottomOverlayRect.top -= actionsExtraHeight

            val newBounds = Rect(bottomOverlayGradient.bounds)
            newBounds.top -= actionsExtraHeight
            newBounds.bottom = bottomOverlayRect.top
            bottomOverlayGradient.bounds = newBounds
        }

        fun lerp(a: Float, b: Float, f: Float): Float {
            return a + f * (b - a)
        }

        /*
         * fix animation of expanding avatar
         */
        memberMethods.getValue("setAvatarExpandProgress").hookAfter(cond = ::isEnabled) { param ->
            if (!extendAvatar) {
                return@hookAfter
            }

            val pa = param.thisObject
            val avatarsViewPager = readAs<View>(pa, "avatarsViewPager")
            val value = readAs<Float>(pa, "currentExpandAnimatorValue")
            val avatarContainer = readAs<View>(pa, "avatarContainer")
            val avatarScale = readAs<Float>(pa, "avatarScale")
            val lp = avatarContainer.layoutParams as ViewGroup.MarginLayoutParams
            val realSize = avatarsViewPager.height

            val nh = lerp(
                invoke("dpf2", null, 100f) as Float,
                realSize / avatarScale,
                value
            ).toInt()

            lp.height = nh
            lp.width = nh
            lp.leftMargin = 0

            invoke("fixAvatarImageInCenter", pa)
            avatarContainer.requestLayout()

            if (isExteragram) {
                val actionsView = runCatching {
                    read(pa, "actionsView")
                }.getOrNull()

                val isPulledDown = runCatching {
                    readAs<Boolean>(pa, "isPulledDown")
                }.getOrDefault(false)

                if (!isForcedByUs(actionsView)) {
                    cacheOriginalActionsStyle(actionsView)
                }

                markActionsPulled(actionsView, isPulledDown)
                applyActionsStyleByState(actionsView)
            }
        }

        memberMethods.getValue("needLayout").hookAfter(cond = ::isEnabled) { param ->
            if (!extendAvatar) {
                return@hookAfter
            }

            val pa = param.thisObject
            val openAnimationInProgress =
                readAs<Boolean>(pa, "openAnimationInProgress")
            val playProfileAnimation = readAs<Int>(pa, "playProfileAnimation")

            val actionsView = if (isExteragram) {
                runCatching {
                    read(pa, "actionsView")
                }.getOrNull()
            } else {
                null
            }

            val isPulledDown = if (isExteragram) {
                runCatching {
                    readAs<Boolean>(pa, "isPulledDown")
                }.getOrDefault(false)
            } else {
                false
            }

            if (isExteragram) {
                if (!isForcedByUs(actionsView)) {
                    cacheOriginalActionsStyle(actionsView)
                }

                markActionsPulled(actionsView, isPulledDown)
            }

            if (openAnimationInProgress && playProfileAnimation == 2) {
                val avatarsViewPager = readAs<View>(pa, "avatarsViewPager")
                val value = readAs<Float>(pa, "currentExpandAnimatorValue")
                val avatarContainer = readAs<View>(pa, "avatarContainer")
                val avatarScale = readAs<Float>(pa, "avatarScale")
                val lp = avatarContainer.layoutParams as ViewGroup.MarginLayoutParams
                val realSize = avatarsViewPager.height

                val nh = lerp(
                    invoke("dpf2", null, 100f) as Float,
                    realSize / avatarScale,
                    value
                ).toInt()

                lp.height = nh
                lp.width = nh
                lp.leftMargin = 0

                invoke("fixAvatarImageInCenter", pa)
                avatarContainer.requestLayout()
            }

            if (isExteragram) {
                applyActionsStyleByState(actionsView)
            }
        }

        /*
         * updateBackgroundPaint
         *
         * 方案一：非 Exteragram
         *   保持原逻辑：
         *   extendAvatar 时，下拉后把 actions 颜色设为黑色，paint alpha = 40。
         *
         * 方案二：Exteragram
         *   缓存原样；
         *   下拉时强制可读；
         *   回来时恢复原样；
         *   不写死普通状态背景，避免大会员按钮变白盒。
         */
        // In this APK updateBackgroundPaint is inlined into TopView.onDraw. Run before
        // drawing so the adjusted action paint is used by the same frame.
        memberMethods.getValue("topUpdateBackgroundPaint").hookBefore(
            cond = ::isEnabled
        ) { param ->
            val pa = read(param.thisObject, "topProfileActivity")
            val actionsView = read(pa, "actionsView") ?: return@hookBefore
            val isPulledDown = readAs<Boolean>(pa, "isPulledDown")

            if (isExteragram) {
                if (!isForcedByUs(actionsView)) {
                    cacheOriginalActionsStyle(actionsView)
                }

                markActionsPulled(actionsView, isPulledDown)
                applyActionsStyleByState(actionsView)
            } else {
                inTopViewDraw.set(extendAvatar && isPulledDown)
            }
        }
        memberMethods.getValue("topUpdateBackgroundPaint").hookAfter(cond = ::isEnabled) {
            inTopViewDraw.remove()
        }
    }

    /**
     * 方案二：Exteragram 专用 draw hook。
     */
    private fun hookExteragramBlurDraw() {
        memberMethods.getValue("blurDraw").hookBefore(cond = ::isEnabled) { param ->
            val blurView = param.thisObject

            val actionsView = runCatching {
                read(blurView, "blurActionsView")
            }.getOrNull()

            val musicView = runCatching {
                read(blurView, "blurMusicView")
            }.getOrNull()

            val suggestionView = runCatching {
                read(blurView, "blurSuggestionView")
            }.getOrNull()

            applyActionsStyleByState(actionsView)

            disableNonActionBlurView(musicView, "musicDrawingBlur")
            disableNonActionBlurView(suggestionView, "suggestionDrawingBlur")

            param.result = null
        }
    }

    /**
     * 方案二：Exteragram 专用 ProfileActionsView hook。
     *
     * hook setActionsColor，或内联后调用的 createColorShader：
     * - 缓存 Exteragram 原本动态主题色；
     * - 下拉状态时保持可读。
     *
     * 不 hook onDraw。
     * 不 hook drawingBlur。
     */
    private fun hookProfileActionsView() {
        // Hook createColorShader when setActionsColor has been inlined.
        val setter = memberMethods["actionsSetActionsColor"]
        val hookMethod = setter ?: memberMethods.getValue("actionsCreateColorShader")
        hookMethod.hook(
            cond = ::isEnabled,
            before = { param ->
                if (!changingActionsColor) {
                    val actionsView = if (Modifier.isStatic(hookMethod.modifiers)) param.args[0] else param.thisObject
                    if (isExteragram && !isForcedByUs(actionsView)) {
                        val color = if (setter != null) {
                            param.args[hookMethod.parameterCount - 2] as Int
                        } else readAs<Int>(actionsView, "actionsColor")
                        cacheOriginalActionsStyle(actionsView, color)
                    }
                }
            },
            after = { param ->
                if (!changingActionsColor) {
                    val actionsView = if (Modifier.isStatic(hookMethod.modifiers)) param.args[0] else param.thisObject
                    if (isExteragram && isActionsPulled(actionsView)) {
                        forceActionsReadableOnAvatar(actionsView)
                    } else if (!isExteragram && inTopViewDraw.get() == true) {
                        memberFields.getValue("actionsRadialGradient").set(actionsView, null)
                        readAs<Paint>(actionsView, "actionsPaint").apply {
                            color = Color.BLACK
                            alpha = 40
                        }
                        (actionsView as? View)?.invalidate()
                    }
                }
            }
        )
    }
}
