package io.github.a13e300.myinjector.telegram

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.a13e300.myinjector.Entry
import io.github.a13e300.myinjector.R
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD

// 标记双向联系人（↑↓图标）
class MutualContact : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.mutualContact

    fun deobf(
        creator: ObfsTableCreator
    ): ObfsTable {
        val userCellUpdate = creator.create("UserCellUpdate") { bridge ->
            bridge.findMethod {
                matcher {
                    usingEqStrings("existing_chats", "50_50", "#")
                }
            }.single().toObfsInfo()
        }

        // NaGram has mutualView, which is also used in UserCell.update, so we don't need to locate the image view to add mutual icon
        // https://github.com/NextAlone/Nagram/blob/b8db62a65e1e4dee34d92bff412548ef628ddb06/TMessagesProj/src/main/java/org/telegram/ui/Cells/UserCell.java#L766
        val userCellImageView = creator.create("UserCellImageView") { bridge ->
            bridge.findField {
                matcher {
                    addReadMethod {
                        descriptor(userCellUpdate.descriptor)
                    }
                    type("android.widget.ImageView")
                    declaredClass(userCellUpdate.className)
                }
            }.let {
                if (it.size == 1) it[0].toObfsInfo()
                else if (it.size == 2) ObfsInfo("", "")
                else error("invalid userCellImageView count: ${it.size}")
            }
        }
        return creator.obfsTable
    }

    @Suppress("DEPRECATION")
    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val userCellInfo = table["UserCellUpdate"]!!
        val userCellImageViewInfo = table["UserCellImageView"]!!
        if (userCellImageViewInfo.memberName.isEmpty()) {
            logD("skip MutualContact since this version may have support")
            return
        }
        val drawable = Entry.moduleRes.getDrawable(R.drawable.ic_mutual_contact)
        val tlUser = findClass("org.telegram.tgnet.TLRPC\$TL_user")
        val userCellClass = findClass(userCellInfo.className)
        val imageViewField = userCellClass.getDeclaredField(userCellImageViewInfo.memberName)
            .also { it.isAccessible = true }
        val currentObjectField =
            userCellClass.declaredFields.single { it.type == Object::class.java }
                .also { it.isAccessible = true }
        userCellClass.hookAllAfter(
            userCellInfo.memberName,
            cond = ::isEnabled
        ) { param ->
            /*
            val d = param.thisObject.getObjAs<Int>("currentDrawable")
            if (d != 0) {
                return@hookAllAfter
            }*/
            val current = currentObjectField.get(param.thisObject)
            if (!tlUser.isInstance(current)) return@hookAllAfter
            val imageView = imageViewField.get(param.thisObject) as ImageView
            val mutual = current.getObjAs<Boolean>("mutual_contact")
            if (mutual) {
                imageView.setImageDrawable(drawable)
                imageView.visibility = View.VISIBLE
                (imageView.layoutParams as FrameLayout.LayoutParams).apply {
                    val resource = imageView.context.resources
                    gravity =
                        (gravity and Gravity.HORIZONTAL_GRAVITY_MASK.inv()) or Gravity.RIGHT
                    rightMargin =
                        TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            8f,
                            resource.displayMetrics
                        ).toInt()
                    leftMargin
                }
            }
        }
    }
}
