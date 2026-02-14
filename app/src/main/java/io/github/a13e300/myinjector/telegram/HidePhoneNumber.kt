package io.github.a13e300.myinjector.telegram

import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.widget.TextView
import io.github.a13e300.myinjector.Entry
import io.github.a13e300.myinjector.R
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.addModuleAssets
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.extraField
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hookAllAfter

class HidePhoneNumber : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.hidePhoneNumber

    @SuppressLint("DiscouragedApi", "SetTextI18n")
    override fun onHook() {
        var show = false
        // hide phone number in drawer
        // this only exists in older version
        findClassOrNull("org.telegram.ui.Cells.DrawerProfileCell")?.let { classDrawerProfileCell ->
            classDrawerProfileCell.hookAllAfter("setUser", cond = ::isEnabled) { param ->
                val phoneTextView = param.thisObject.getObjAs<TextView>("phoneTextView")
                val currentNumber = phoneTextView.text
                phoneTextView.text = if (show) currentNumber else "点击显示电话号码"
                phoneTextView.setOnClickListener {
                    show = !show
                    phoneTextView.text = if (show) currentNumber else "点击显示电话号码"
                }
            }
        }

        // hide phone number in settings
        // exists in 12.4.0
        val settingsActivity = findClass("org.telegram.ui.SettingsActivity")
        if (settingsActivity.declaredFields.any { it.name == "subtitleView" }) {
            settingsActivity.hookAllAfter("setInfo", cond = ::isEnabled) { param ->
                val subtitleView = param.thisObject.getObjAs<TextView>("subtitleView")
                val oldText = subtitleView.text.toString()
                val indexAfterPhone = oldText.indexOf(" • @")
                val currentNumber =
                    if (indexAfterPhone >= 0) oldText.substring(0 until indexAfterPhone) else oldText
                val textAfterPhone =
                    if (indexAfterPhone >= 0) oldText.substring(indexAfterPhone until oldText.length) else ""

                subtitleView.text =
                    (if (show) currentNumber else "点击显示电话号码") + textAfterPhone
                subtitleView.setOnClickListener {
                    show = !show
                    subtitleView.text =
                        (if (show) currentNumber else "点击显示电话号码") + textAfterPhone
                }
                // subtitleView is too thin, so also allow click title to switch state
                val titleView = param.thisObject.getObjAs<TextView>("titleView")
                titleView.setOnClickListener {
                    show = !show
                    subtitleView.text =
                        (if (show) currentNumber else "点击显示电话号码") + textAfterPhone
                }
            }
        }

        val themeClass = findClass("org.telegram.ui.ActionBar.Theme")
        val key_switch2TrackChecked by lazy { themeClass.getObjS("key_switch2TrackChecked") }

        val classProfileActivity_ListAdapter =
            findClass("org.telegram.ui.ProfileActivity\$ListAdapter")
        val classLocaleController = findClass("org.telegram.messenger.LocaleController")
        classProfileActivity_ListAdapter.hookAllAfter(
            "onBindViewHolder",
            cond = ::isEnabled
        ) { param ->
            val profileActivity = param.thisObject.getObj("this$0")!!
            val phoneRow = profileActivity.getObj("phoneRow")
            val numberRow = profileActivity.getObj("numberRow")
            if (param.args[1] != phoneRow && param.args[1] != numberRow) return@hookAllAfter
            val cell = param.args[0].getObjAs<View>("itemView")
            val ctx = cell.context
            ctx.addModuleAssets(Entry.modulePath)
            val tv = cell.getObjAs<TextView>("textView")
            val phoneNumber = tv.text
            val phoneHidden = classLocaleController.callS(
                "getString",
                ctx.resources.getIdentifier("PhoneHidden", "string", ctx.packageName)
            )
            if (phoneNumber == phoneHidden) {
                return@hookAllAfter
            }

            var show by extraField(
                profileActivity,
                "showPhoneNumber",
                if (TelegramHandler.settings.hidePhoneNumberForSelfOnly) {
                    val currentUserId = profileActivity.call("getUserConfig").getObj("clientUserId")
                    val userId = profileActivity.getObj("userId")
                    currentUserId != userId
                } else false
            )
            tv.text = if (show) phoneNumber else "号码已隐藏"
            fun setIcon() {
                val icon =
                    (if (show) ctx.getDrawable(R.drawable.ic_visible) else ctx.getDrawable(R.drawable.ic_invisible))!!
                val filter = PorterDuffColorFilter(
                    profileActivity.call(
                        "dontApplyPeerColor",
                        profileActivity.call("getThemedColor", key_switch2TrackChecked), false
                    ) as Int,
                    PorterDuff.Mode.MULTIPLY
                )
                icon.colorFilter = filter
                cell.call("setImage", icon)
            }
            setIcon()
            cell.call("setImageClickListener", object : View.OnClickListener {
                override fun onClick(v: View) {
                    val newShow = !show
                    show = newShow
                    setIcon()
                    tv.text = if (newShow) phoneNumber else "号码已隐藏"
                }
            })
        }
    }
}