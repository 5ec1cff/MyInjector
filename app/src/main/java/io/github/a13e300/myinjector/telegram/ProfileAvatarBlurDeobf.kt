package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.getInsnWide
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.UsingType
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

private data class BlurCall(val method: MethodData, val fields: List<FieldData?>)
private data class BlurRead(val field: FieldData, val receiver: FieldData?)
private data class BlurWrite(val field: FieldData, val resultOf: MethodData?)
private data class BlurCode(val calls: List<BlurCall>, val reads: List<BlurRead>, val writes: List<BlurWrite>)

// These methods use straight-line field loads before the calls we inspect. Track their
// register origins rather than relying on field order (R8 can reorder those loads).
private fun blurCode(bridge: DexKitBridge, method: MethodData): BlurCode {
    val fields = mutableMapOf<Int, FieldData>()
    val results = mutableMapOf<Int, MethodData>()
    val calls = mutableListOf<BlurCall>()
    val reads = mutableListOf<BlurRead>()
    val writes = mutableListOf<BlurWrite>()
    val insns = method.insns
    var pending: MethodData? = null
    var pos = 0
    fun clear(reg: Int) {
        fields.remove(reg)
        results.remove(reg)
    }
    while (pos < insns.size) {
        val word = insns[pos].code
        val op = word and 0xff
        val a = (word ushr 8) and 0xf
        val b = word ushr 12
        val aa = word ushr 8
        if (op !in 0x0a..0x0c) pending = null
        when (op) {
            in 0x01..0x09 -> {
                val dst = if (op in listOf(1, 4, 7)) a else if (op in listOf(2, 5, 8)) aa else insns[pos + 1].code
                val src = if (op in listOf(1, 4, 7)) b else if (op in listOf(2, 5, 8)) insns[pos + 1].code else insns[pos + 2].code
                val field = fields[src]
                val result = results[src]
                clear(dst)
                field?.let { fields[dst] = it }
                result?.let { results[dst] = it }
            }
            in 0x0a..0x0c -> {
                clear(aa)
                pending?.let { results[aa] = it }
            }
            in 0x52..0x58 -> {
                val field = bridge.getFieldDataByDexAndId(method.dexId, insns[pos + 1].code)!!
                reads += BlurRead(field, fields[b])
                clear(a)
                fields[a] = field
            }
            in 0x59..0x5f -> {
                val field = bridge.getFieldDataByDexAndId(method.dexId, insns[pos + 1].code)!!
                writes += BlurWrite(field, results[a])
            }
            in 0x6e..0x72, in 0x74..0x78 -> {
                val invoked = bridge.getMethodDataByDexAndId(method.dexId, insns[pos + 1].code)!!
                val regs = if (op >= 0x74) {
                    List(aa) { insns[pos + 2].code + it }
                } else {
                    val packed = insns[pos + 2].code
                    listOf(packed and 0xf, (packed ushr 4) and 0xf, (packed ushr 8) and 0xf, packed ushr 12, a).take(b)
                }
                calls += BlurCall(invoked, regs.map { fields[it] })
                pending = invoked
            }
            0x12, 0x23, in 0x7b..0x8f, in 0xb0..0xd7 -> clear(a)
            0x0d, in 0x13..0x1c, in 0x20..0x22, in 0x2d..0x31, in 0x44..0x4a,
            in 0x60..0x66, in 0x90..0xaf, in 0xd8..0xe2 -> clear(aa)
        }
        pos += getInsnWide(insns, pos)
    }
    return BlurCode(calls, reads, writes)
}

private fun findProfileAvatarBlur(bridge: DexKitBridge, baseFragment: String): Map<String, String> {
    val found = linkedMapOf<String, String>()
    fun save(key: String, method: MethodData?) {
        found[key] = method?.descriptor.orEmpty()
    }
    fun save(key: String, field: FieldData?) {
        found[key] = field?.descriptor.orEmpty()
    }
    val foreground = bridge.findMethod {
        matcher {
            declaredClass { superClass(baseFragment) }
            usingEqStrings("avatar")
            returnType("void")
            addInvoke { descriptor("Lorg/telegram/messenger/ImageReceiver;->getDrawable()Landroid/graphics/drawable/Drawable;") }
        }
    }.single {
        it.usingStrings.size == 1 && (it.paramTypeNames == listOf("boolean") ||
            (Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(it.className, "boolean")))
    }
    val pa = foreground.declaredClass!!
    val gallery = foreground.invokes.single { it.paramTypeNames == listOf("int") && it.returnTypeName == "org.telegram.messenger.ImageLocation" }.declaredClass!!
    found["galleryClass"] = gallery.name
    val pager = pa.fields.single { it.typeName == gallery.name || it.type.superClass?.name == gallery.name }
    save("avatarsViewPager", pager)

    val blur = bridge.findClass {
        matcher {
            superClass("android.view.View")
            usingEqStrings("profileBlurNode", "profileActionsBlurNode")
        }
    }.single()
    val draw = blur.findMethod {
        matcher {
            paramCount(7)
            returnType("void")
            addInvoke { descriptor("Landroid/graphics/Canvas;->drawRenderNode(Landroid/graphics/RenderNode;)V") }
        }
    }.single()
    require(draw.paramTypeNames[0] == "android.graphics.Canvas" &&
        draw.paramTypeNames.drop(2) == listOf("float", "float", "boolean", "float", "float")
    )
    save("blurDraw", draw)
    val top = bridge.findClass {
        matcher {
            superClass("android.widget.FrameLayout")
            addField { type(pa.name) }
            addField { type("android.graphics.RadialGradient") }
            addMethod {
                name("setBackgroundColor")
                paramTypes("int")
            }
        }
    }.single()
    save("topSetBackgroundColor", top.methods.single { it.name == "setBackgroundColor" && it.paramTypeNames == listOf("int") })
    save("topProfileActivity", top.fields.single { it.typeName == pa.name })
    // updateBackgroundPaint is sometimes inlined into onDraw.
    val topBackground = top.findMethod {
        matcher { addInvoke { declaredClass("android.graphics.RadialGradient"); name("<init>") } }
    }.single()
    save("topUpdateBackgroundPaint", topBackground)
    val actions = topBackground.usingFields.map { it.field }.distinctBy { it.descriptor }.single {
        it.className == pa.name && it.type.superClass?.name == "android.view.View" &&
            it.type.fields.any { f -> f.typeName == "android.graphics.RadialGradient" }
    }
    save("actionsView", actions)
    val actionsClass = actions.type
    save("blurActionsView", blur.fields.single { it.typeName == actions.typeName })
    val shader = actionsClass.findMethod {
        matcher {
            paramTypes()
            returnType("void")
            addInvoke { declaredClass("android.graphics.RadialGradient"); name("<init>") }
        }
    }.single()
    save("actionsCreateColorShader", shader)
    val shaderCode = blurCode(bridge, shader)
    val setPaintColor = shaderCode.calls.single { it.method.descriptor == "Landroid/graphics/Paint;->setColor(I)V" }
    val paint = setPaintColor.fields[0]!!
    val color = setPaintColor.fields[1]!!
    save("actionsPaint", paint)
    save("actionsColor", color)
    val hasColor = shader.usingFields.map { it.field }.distinctBy { it.descriptor }.single { it.className == actionsClass.name && it.typeName == "boolean" }
    save("actionsHasColorById", hasColor)
    save("actionsRadialGradient", actionsClass.fields.single { it.typeName == "android.graphics.RadialGradient" })
    val setter = actionsClass.findMethod {
        matcher {
            returnType("void")
            addUsingField { descriptor(color.descriptor); usingType(UsingType.Write) }
        }
    }.singleOrNull {
        it.paramTypeNames == listOf("int", "boolean") ||
            (Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(it.className, "int", "boolean"))
    }
    save("actionsSetActionsColor", setter)

    val extra = pa.findMethod {
        matcher {
            paramTypes("float")
            addInvoke { descriptor("Landroid/graphics/Matrix;->setTranslate(FF)V") }
            addInvoke { descriptor("Landroid/graphics/Shader;->setLocalMatrix(Landroid/graphics/Matrix;)V") }
        }
    }.single()
    save("updateExtraViews", extra)
    val overlay = extra.usingFields.map { it.field }.distinctBy { it.descriptor }.single {
        it.className == pa.name && it.type.superClass?.name == "android.view.View"
    }
    save("overlaysView", overlay)
    save("isPulledDown", extra.usingFields.single { it.field.className == pa.name && it.field.typeName == "boolean" }.field)
    val onSize = overlay.type.methods.single { it.name == "onSizeChanged" && it.paramTypeNames == List(4) { "int" } }
    save("overlaysOnSizeChanged", onSize)
    val sizeCode = blurCode(bridge, onSize)
    val bottomRect = sizeCode.reads.single { it.field.descriptor == "Landroid/graphics/Rect;->top:I" }.receiver!!
    save("bottomOverlayRect", bottomRect)
    // The bottom gradient's bounds end at bottomOverlayRect.top; the top gradient uses bottom.
    val bottomGradient = sizeCode.calls.single {
        it.method.name == "setBounds" && it.fields.lastOrNull()?.descriptor == "Landroid/graphics/Rect;->top:I"
    }.fields[0]!!
    save("bottomOverlayGradient", bottomGradient)
    save("overlaysProfileActivity", overlay.type.fields.single { it.typeName == pa.name })
    val actionsHeight = onSize.invokes.single { it.className == pa.name && it.paramCount == 0 && it.returnTypeName == "int" }
    save("getActionsExtraHeight", actionsHeight)

    // setAvatarExpandProgress stores the result of lerp(expandAnimatorValues, fraction).
    val expand = pa.findMethod {
        matcher {
            paramTypes("float")
            addInvoke { descriptor("Lorg/telegram/messenger/AndroidUtilities;->lerp([FF)F") }
        }
    }.single()
    save("setAvatarExpandProgress", expand)
    val progress = blurCode(bridge, expand).writes.single {
        it.field.className == pa.name && it.resultOf?.descriptor == "Lorg/telegram/messenger/AndroidUtilities;->lerp([FF)F"
    }.field
    save("currentExpandAnimatorValue", progress)
    val layout = pa.findMethod {
        matcher {
            paramTypes("boolean")
            usingNumbers(26.5f, 1.3f, 7f)
        }
    }.single()
    save("needLayout", layout)
    val fix = pa.findMethod {
        matcher {
            paramTypes()
            returnType("void")
            usingNumbers(0.5f)
            addInvoke { name("getLayoutParams") }
            addInvoke { name("setTranslationX") }
        }
    }.single()
    save("fixAvatarImageInCenter", fix)
    val fixCode = blurCode(bridge, fix)
    save("avatarContainer", fixCode.calls.single { it.method.name == "setTranslationX" }.fields[0]!!)
    // avatarScale is the float read by both centering and expand animation; the other
    // centering floats describe translation or the separate profile opening animation.
    val expandReads = expand.usingFields.filter { it.usingType == FieldUsingType.Read }.map { it.field.descriptor }.toSet()
    val scale = fix.usingFields.filter { it.usingType == FieldUsingType.Read }.map { it.field }.distinctBy { it.descriptor }.single {
        it.className == pa.name && it.typeName == "float" && it.descriptor in expandReads
    }
    save("avatarScale", scale)
    save("openAnimationInProgress", fix.usingFields.map { it.field }.distinctBy { it.descriptor }.single { it.className == pa.name && it.typeName == "boolean" })
    // TopView.onDraw reads searchTransitionOffset too. Intersect with the animation
    // that writes currentExpandAnimatorValue to isolate playProfileAnimation.
    val topDraw = top.methods.single { it.name == "onDraw" }
    val playCandidates = topDraw.usingFields.map { it.field }.distinctBy { it.descriptor }.filter { it.className == pa.name && it.typeName == "int" }
    val animation = pa.findMethod {
        matcher {
            paramTypes("float")
            addUsingField { descriptor(progress.descriptor); usingType(UsingType.Write) }
            addInvoke {
                name("setBackgroundColor")
                paramTypes("int")
            }
        }
    }.single()
    save("playProfileAnimation", playCandidates.single { field -> animation.usingFields.any { it.field.descriptor == field.descriptor } })
    val dpf2 = bridge.findMethod {
        matcher {
            declaredClass("org.telegram.messenger.AndroidUtilities")
            name("dpf2")
            paramTypes("float")
            returnType("float")
        }
    }.single()
    save("dpf2", dpf2)

    val music = blur.fields.singleOrNull { field ->
        field.type.superClass?.name == "android.view.View" && field.type.methods.any { method ->
            method.usingFields.any {
                it.field.descriptor == "Lorg/telegram/messenger/R\$string;->AccDescrProfileMusic:I"
            }
        }
    }
    save("blurMusicView", music)
    val suggestion = blur.fields.singleOrNull { it.type.superClass?.name == "android.widget.FrameLayout" }
    save("blurSuggestionView", suggestion)
    for ((key, view) in listOf("music" to music, "suggestion" to suggestion)) {
        // R8 can remove the always-false boolean and even the entire empty method.
        val node = view?.type?.fields?.singleOrNull { it.typeName == "android.graphics.RenderNode" }
        val disable = if (node == null) null else view.type.findMethod {
            matcher {
                returnType("void")
                addUsingField { descriptor(node.descriptor); usingType(UsingType.Write) }
                addUsingField { descriptor(node.descriptor); usingType(UsingType.Read) }
            }
        }.singleOrNull { it.paramTypeNames == listOf("boolean") || it.paramCount == 0 }
        save("${key}DrawingBlur", disable)
    }
    return found
}

// A missing optional member is cached as an empty ObfsInfo so warm starts skip DexKit.
internal fun profileAvatarBlurMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy {
        findProfileAvatarBlur(creator.bridge, creator.obfsTable.getValue("BaseFragment").className)
    }
    val keys = listOf(
        "galleryClass",
        "avatarsViewPager",
        "blurDraw",
        "topSetBackgroundColor",
        "topProfileActivity",
        "topUpdateBackgroundPaint",
        "actionsView",
        "blurActionsView",
        "actionsCreateColorShader",
        "actionsPaint",
        "actionsColor",
        "actionsHasColorById",
        "actionsRadialGradient",
        "actionsSetActionsColor",
        "updateExtraViews",
        "overlaysView",
        "isPulledDown",
        "overlaysOnSizeChanged",
        "bottomOverlayRect",
        "bottomOverlayGradient",
        "overlaysProfileActivity",
        "getActionsExtraHeight",
        "setAvatarExpandProgress",
        "currentExpandAnimatorValue",
        "needLayout",
        "fixAvatarImageInCenter",
        "avatarContainer",
        "avatarScale",
        "openAnimationInProgress",
        "playProfileAnimation",
        "dpf2",
        "blurMusicView",
        "blurSuggestionView",
        "musicDrawingBlur",
        "suggestionDrawingBlur",
    )
    return keys.associateWith { key ->
        creator.create("DisableProfileAvatarBlur_$key") { bridge ->
            val descriptor = found.getValue(key)
            when {
                descriptor.isEmpty() -> ObfsInfo("", "")
                key == "galleryClass" -> ObfsInfo(descriptor, "")
                '(' in descriptor -> bridge.getMethodData(descriptor)!!.toObfsInfo()
                else -> bridge.getFieldData(descriptor)!!.toObfsInfo()
            }
        }
    }
}
