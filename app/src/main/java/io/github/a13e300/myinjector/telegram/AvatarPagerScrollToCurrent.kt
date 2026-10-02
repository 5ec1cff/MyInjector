package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

// 个人资料头像如果存在多个且主头像非第一个时，下拉展示完整头像列表时自动切到当前头像（原行为是总是切到第一个）
class AvatarPagerScrollToCurrent : MyDynHook("avatarPageScrollToCurrent") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.avatarPageScrollToCurrent

    private fun deobf(creator: ObfsTableCreator): ObfsTable {
        val baseFragment = creator.obfsTable["BaseFragment"]!!
        val foreground = creator.create("ProfileActivitySetForegroundImage") { bridge ->
            bridge.findMethod {
                matcher {
                    declaredClass { superClass(baseFragment.className) }
                    usingEqStrings("avatar")
                    returnType("void")
                    addInvoke {
                        descriptor("Lorg/telegram/messenger/ImageReceiver;->getDrawable()Landroid/graphics/drawable/Drawable;")
                    }
                }
            }.single {
                // R8 can turn the instance method into a static (ProfileActivity, boolean) method.
                it.usingStrings.size == 1 &&
                    (it.paramTypeNames == listOf("boolean") ||
                        (Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(it.className, "boolean")))
            }.toObfsInfo()
        }

        val imageLocation = creator.create("ProfileGalleryViewGetImageLocation") { bridge ->
            bridge.findMethod {
                matcher {
                    addCaller { descriptor(foreground.descriptor) }
                    paramTypes("int")
                    returnType("org.telegram.messenger.ImageLocation")
                }
            }.single().toObfsInfo()
        }

        val reset = creator.create("ProfileGalleryViewResetCurrentItem") { bridge ->
            // setCurrentItem(adapter.getExtraCount(), false): two calls and one field read.
            bridge.findMethod {
                matcher {
                    declaredClass(imageLocation.className)
                    paramTypes()
                    returnType("void")
                    addInvoke {
                        paramTypes("int", "boolean")
                        returnType("void")
                    }
                    addInvoke {
                        paramTypes()
                        returnType("int")
                    }
                }
            }.single { it.invokes.size == 2 && it.usingFields.size == 1 }.toObfsInfo()
        }

        creator.create("AvatarViewPagerSetCurrentItem") { bridge ->
            bridge.getMethodData(reset.descriptor)!!.invokes.single {
                it.paramTypeNames == listOf("int", "boolean") && it.returnTypeName == "void"
            }.toObfsInfo()
        }

        val adapter = creator.create("ProfileGalleryViewAdapter") { bridge ->
            bridge.getMethodData(reset.descriptor)!!.usingFields.single().field.toObfsInfo()
        }

        creator.create("AvatarCircularViewPagerAdapterGetRealPosition") { bridge ->
            // The gallery's getRealPosition(int) wrapper can be inlined away. Its no-arg
            // overload still calls adapter.getRealPosition(getCurrentItem()).
            bridge.findMethod {
                matcher {
                    declaredClass(imageLocation.className)
                    paramTypes()
                    returnType("int")
                    addUsingField { descriptor(adapter.descriptor) }
                    addInvoke {
                        paramTypes("int")
                        returnType("int")
                    }
                }
            }.single().invokes.single {
                it.paramTypeNames == listOf("int") && it.returnTypeName == "int"
            }.toObfsInfo()
        }

        creator.create("ProfileGalleryViewProfileActivity") { bridge ->
            // The anonymous subclass holds its enclosing ProfileActivity; both names can change.
            bridge.findField {
                matcher {
                    declaredClass { superClass(imageLocation.className) }
                    type(foreground.className)
                }
            }.single().toObfsInfo()
        }

        creator.create("ProfileActivityUserInfo") { bridge ->
            bridge.findField {
                matcher {
                    declaredClass(foreground.className)
                    type("org.telegram.tgnet.TLRPC\$UserFull")
                }
            }.single().toObfsInfo()
        }

        creator.create("ProfileActivityChatInfo") { bridge ->
            bridge.findField {
                matcher {
                    declaredClass(foreground.className)
                    type("org.telegram.tgnet.TLRPC\$ChatFull")
                }
            }.single().toObfsInfo()
        }

        creator.create("ProfileGalleryViewPhotos") { bridge ->
            bridge.findField {
                matcher {
                    declaredClass(imageLocation.className)
                    type("java.util.ArrayList")
                    addReadMethod {
                        declaredClass(imageLocation.className)
                        paramTypes("int")
                        returnType("org.telegram.tgnet.TLRPC\$Photo")
                    }
                }
            }.single().toObfsInfo()
        }

        // Telegram keeps org.telegram.tgnet.** and its members in proguard-rules.pro.
        creator.create("AvatarUserFullProfilePhoto") { bridge ->
            bridge.findField {
                matcher {
                    descriptor("Lorg/telegram/tgnet/TLRPC\$UserFull;->profile_photo:Lorg/telegram/tgnet/TLRPC\$Photo;")
                }
            }.single().toObfsInfo()
        }
        creator.create("AvatarChatFullChatPhoto") { bridge ->
            bridge.findField {
                matcher {
                    descriptor("Lorg/telegram/tgnet/TLRPC\$ChatFull;->chat_photo:Lorg/telegram/tgnet/TLRPC\$Photo;")
                }
            }.single().toObfsInfo()
        }
        creator.create("AvatarPhotoId") { bridge ->
            bridge.findField {
                matcher {
                    descriptor("Lorg/telegram/tgnet/TLRPC\$Photo;->id:J")
                }
            }.single().toObfsInfo()
        }
        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        fun method(key: String) = DexMethod(table[key]!!.descriptor)
            .getMethodInstance(classLoader).also { it.isAccessible = true }
        fun field(key: String) = DexField(table[key]!!.descriptor)
            .getFieldInstance(classLoader).also { it.isAccessible = true }

        val resetCurrentItem = method("ProfileGalleryViewResetCurrentItem")
        val setCurrentItem = method("AvatarViewPagerSetCurrentItem")
        val getRealPosition = method("AvatarCircularViewPagerAdapterGetRealPosition")
        val setForegroundImage = method("ProfileActivitySetForegroundImage")
        val getImageLocation = method("ProfileGalleryViewGetImageLocation")
        val adapterField = field("ProfileGalleryViewAdapter")
        val profileActivityField = field("ProfileGalleryViewProfileActivity")
        val userInfoField = field("ProfileActivityUserInfo")
        val chatInfoField = field("ProfileActivityChatInfo")
        val photosField = field("ProfileGalleryViewPhotos")
        val profilePhotoField = field("AvatarUserFullProfilePhoto")
        val chatPhotoField = field("AvatarChatFullChatPhoto")
        val photoIdField = field("AvatarPhotoId")

        fun currentPhotoIndex(gallery: Any): Int {
            if (!profileActivityField.declaringClass.isInstance(gallery)) return -1
            val pa = profileActivityField.get(gallery) ?: return -1
            val currentPhoto = userInfoField.get(pa)?.let { profilePhotoField.get(it) }
                ?: chatInfoField.get(pa)?.let { chatPhotoField.get(it) } ?: return -1
            val id = photoIdField.getLong(currentPhoto)
            val photos = photosField.get(gallery) as List<*>
            return photos.indexOfFirst { it != null && photoIdField.getLong(it) == id }
        }

        resetCurrentItem.hookBefore(cond = ::isEnabled) { param ->
            val idx = currentPhotoIndex(param.thisObject)
            if (idx == -1) return@hookBefore
            val adapter = adapterField.get(param.thisObject) ?: return@hookBefore
            var exactIdx = 0
            // CircularViewPager has extra pages at both ends; preserve the first matching page.
            while (getRealPosition.invoke(adapter, exactIdx) != idx) {
                exactIdx++
                // I believe no one can set over 300 photos
                if (exactIdx > 300) return@hookBefore
            }
            setCurrentItem.invoke(param.thisObject, exactIdx, false)
            param.result = null
        }

        // Fix the transition image. In the static R8 variant, the boolean is the last argument.
        val inSetFgImg = ThreadLocal<Boolean>()
        val secondParentArg = setForegroundImage.parameterCount - 1
        setForegroundImage.hook(
            cond = ::isEnabled,
            before = { param ->
                if (param.args[secondParentArg] == false) {
                    inSetFgImg.set(true)
                }
            },
            after = { _ ->
                inSetFgImg.remove()
            }
        )
        getImageLocation.hookBefore(cond = ::isEnabled) { param ->
            if (inSetFgImg.get() == true) {
                val idx = currentPhotoIndex(param.thisObject)
                if (idx != -1) param.args[0] = idx
            }
        }
    }
}
