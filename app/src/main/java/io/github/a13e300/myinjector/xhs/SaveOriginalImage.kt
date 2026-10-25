package io.github.a13e300.myinjector.xhs

import android.graphics.Bitmap
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookReplace
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.bridge.HookParam
import io.github.a13e300.myinjector.logD
import io.github.a13e300.myinjector.logE
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.lang.reflect.Modifier

private val RVIC_PATTERN = byteArrayOf(
    // ftyp rvic
    0x66, 0x74, 0x79, 0x70, 0x72, 0x76, 0x69, 0x63
)

class SaveOriginalImageAbort(var f: File) : Throwable()

class SaveOriginalImage : MyDynHook("saveOriginalImage") {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.saveOriginalImage

    override fun onHook() {
        val savePicMethodInfo = XhsHandler.creator.create("savePicMethod") { bridge ->
            bridge.findMethod {
                matcher {
                    usingEqStrings(
                        "decode bitmap is null",
                        "save image failed without watermark"
                    )
                }
            }.single().toObfsInfo()
        }
        val savePicClass = findClass(savePicMethodInfo.className)
        val picCacheFieldInfo = XhsHandler.creator.create("picCacheClass") { bridge ->
            bridge.findField {
                matcher {
                    type = "java/io/File"
                    addReadMethod(savePicMethodInfo.descriptor)
                }
            }.single().toObfsInfo()
        }
        logD("picCache: $picCacheFieldInfo")
        val picDecoder = XhsHandler.creator.create("picDecoder") { bridge ->
            val decoderMethod = bridge.findMethod {
                matcher {
                    usingEqStrings("XYBitmapFactoryExtensionV2")
                }
            }.single()

            decoderMethod.declaredClass!!.findMethod {
                matcher {
                    modifiers(Modifier.STATIC)
                    invokeMethods {
                        add {
                            descriptor(decoderMethod.descriptor)
                        }
                    }
                }
            }.single().toObfsInfo()
        }
        logD("picDecoder: $picDecoder")
        val picDecoderClass = findClass(picDecoder.className)
        val picDecoderItselfField = picDecoderClass.declaredFields.single {
            it.type == it.declaringClass && Modifier.isStatic(it.modifiers)
        }
            .also { it.isAccessible = true }
        val picCacheClass = findClass(picCacheFieldInfo.className)
        val savePicMethod =
            savePicClass.declaredMethods.single { it.name == savePicMethodInfo.memberName }
        val picCacheFileField = picCacheClass.getDeclaredField(picCacheFieldInfo.memberName)
            .also { it.isAccessible = true }
        // 9.24+
        // 变化：原本两个方法在同一个类，现在在不同的类，且 savePicMethod 只有一个参数，
        // picCache 放在 this 的某个 field （此前作为参数 1）
        // 9.26
        // 变化：两个方法疑似被 r8 合并成一个
        var version = 0
        var version2TypeField: Field? = null
        val picCacheFileResolver = if (savePicMethod.parameters.size == 1) {
            val picCacheField = savePicMethod.declaringClass.fields.firstOrNull {
                it.type == picCacheClass
            }

            if (picCacheField != null) {
                version = 1
                val l: (HookParam) -> File = { param ->
                    picCacheFileField.get(picCacheField.get(param.thisObject)) as File
                }
                l
            } else {
                version = 2

                version2TypeField = savePicClass.declaredFields.single {
                    it.type == Integer.TYPE
                }

                val objFields = savePicClass.fields.filter { it.type == Object::class.java }
                var picCacheObjField: Field? = null

                val l: (HookParam) -> File = { param ->
                    if (picCacheObjField == null) {
                        for (f in objFields) {
                            f.isAccessible = true
                            if (f.get(param.thisObject)?.let {
                                    logD("trying $f: $it")
                                    picCacheClass.isInstance(it)
                                } == true) {
                                picCacheObjField = f
                                logD("found picCacheObjField $f")
                                break
                            }
                        }
                    }
                    picCacheFileField.get(picCacheObjField!!.get(param.thisObject)) as File
                }
                l
            }
        } else {
            version = 0
            val l: (HookParam) -> File = { param ->
                picCacheFileField.get(param.args[1]) as File
            }
            l
        }

        fun isRvcImage(f: File): Boolean {
            return runCatching {
                val arr = FileInputStream(f).use {
                    val arr = ByteArray(12)
                    it.read(arr)
                    arr
                }
                arr.sliceArray(4 until 12).contentEquals(RVIC_PATTERN)
            }.getOrElse {
                logE("check is RvcImage: ", it)
                false
            }
        }

        val kotlinPair = findClass("kotlin.Pair")

        fun saveAsPng(f: File): File? {
            val outf = File(f.parent, f.name + ".png")
            return runCatching {
                val bitmap = picDecoderClass.callS(
                    picDecoder.memberName,
                    picDecoderItselfField.get(null),
                    f.path,
                    null,
                    true,
                    2,
                    null
                ) as Bitmap
                logD("write to $outf")
                FileOutputStream(outf).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                outf
            }.onFailure {
                logE("saveAsPng bitmap $outf failed", it)
            }.getOrNull()
        }

        fun handleSaveCachedPicCommon(f: File, param: HookParam, withUrl: Boolean) {
            val realF = if (isRvcImage(f)) {
                logD("try save rvc as png")
                saveAsPng(f)
            } else {
                f
            }
            logD("save original $realF (withUrl=$withUrl)")
            param.args.last().apply {
                call("onNext", kotlinPair.newInst("success", realF))
                call("onComplete")
            }
            param.result = null
            param.throwable = null
        }

        fun handleSaveCachedPic(param: HookParam) {
            val f = picCacheFileResolver(param)
            handleSaveCachedPicCommon(f, param, false)
        }

        if (version < 2) {
            savePicMethod.hookReplace(
                cond = ::isEnabled
            ) { param ->
                handleSaveCachedPic(param)
            }
        }

        // 保存多图时可能会见到这个方法

        val savePicWithUrlInfo = if (version < 2) {
            XhsHandler.creator.create("savePicWithUrl") { bridge ->
                bridge.findMethod {
                    matcher {
                        if (version == 0)
                            declaredClass(savePicMethodInfo.className)
                        usingEqStrings(
                            "save image failed without watermark"
                        )
                    }
                }.single {
                    if (version == 0) it.methodName != savePicMethodInfo.memberName
                    else it.className != savePicMethodInfo.className // both of them should be `subscribe`
                }.toObfsInfo()
            }
        } else {
            XhsHandler.creator.obfsTable.remove("savePicWithUrl")
            null
        }

        val picCacheInterface = picCacheClass.interfaces.single()
        val getCacheMethod = XhsHandler.creator.create("getImageCache") { bridge ->
            val imageCache = bridge.findMethod {
                matcher {
                    addCaller {
                        declaredClass(savePicWithUrlInfo?.className ?: savePicMethodInfo.className)
                        name(savePicWithUrlInfo?.memberName ?: savePicMethodInfo.memberName)
                    }
                    returnType(picCacheInterface.name)
                }
            }.single()
            val info = imageCache.toObfsInfo()
            // 9.34.4
            if (Modifier.isInterface(imageCache.declaredClass!!.modifiers)) {
                val implementorClass = bridge.findClass {
                    matcher {
                        interfaces {
                            add(imageCache.declaredClassName)
                        }
                    }
                }.single()
                info.copy(className = implementorClass.name)
            } else {
                info
            }
        }

        val tls = ThreadLocal<Boolean>()
        findClass(getCacheMethod.className).hookAllAfter(
            getCacheMethod.memberName,
            cond = ::isEnabled
        ) { param ->
            val res = param.result
            if (tls.get() == true && picCacheClass.isInstance(res)) {
                val f = res.let { picCacheFileField.get(it) as File }
                logD("got cache $f")
                param.throwable = SaveOriginalImageAbort(f)
            }
        }

        fun handleSavePicWithUrlBefore() {
            tls.set(true)
        }

        fun handleSavePicWithUrlAfter(param: HookParam) {
            runCatching {
                (param.throwable as? SaveOriginalImageAbort)?.let {
                    param.throwable = null
                    param.result = null
                    val f = it.f
                    handleSaveCachedPicCommon(f, param, true)
                }
            }

            tls.set(false)
        }

        if (version < 2) {
            val savePicWithUrlClass =
                if (version == 1) findClass(savePicWithUrlInfo!!.className) else savePicClass

            savePicWithUrlClass.hookAll(
                savePicWithUrlInfo!!.memberName, cond = ::isEnabled,
                before = {
                    handleSavePicWithUrlBefore()
                },
                after = { param ->
                    handleSavePicWithUrlAfter(param)
                }
            )
        } else {
            savePicClass.hookAll(
                savePicMethodInfo.memberName, cond = ::isEnabled,
                before = { param ->
                    val type = version2TypeField!!.getInt(param.thisObject)
                    logD("savePic before type $type")
                    if (type == 0) {
                        handleSaveCachedPic(param)
                    } else {
                        handleSavePicWithUrlBefore()
                    }
                },
                after = { param ->
                    val type = version2TypeField!!.getInt(param.thisObject)
                    logD("savePic after type $type")
                    if (type != 0) {
                        handleSavePicWithUrlAfter(param)
                    }
                }
            )
        }
    }
}
