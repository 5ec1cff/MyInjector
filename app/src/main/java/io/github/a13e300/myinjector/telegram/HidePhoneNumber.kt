package io.github.a13e300.myinjector.telegram

import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.widget.TextView
import io.github.a13e300.myinjector.Entry
import io.github.a13e300.myinjector.R
import io.github.a13e300.myinjector.arch.IInstanceOpType
import io.github.a13e300.myinjector.arch.InvokeType
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.SInstanceOpType
import io.github.a13e300.myinjector.arch.addModuleAssets
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.decodeConstHigh16
import io.github.a13e300.myinjector.arch.decodeConstString
import io.github.a13e300.myinjector.arch.decodeIInstanceOp
import io.github.a13e300.myinjector.arch.decodeInvoke
import io.github.a13e300.myinjector.arch.decodeSInstanceOp
import io.github.a13e300.myinjector.arch.extraField
import io.github.a13e300.myinjector.arch.getInsnWide
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import kotlin.math.min

class HidePhoneNumber : MyDynHook("hidePhoneNumber") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.hidePhoneNumber

    private fun deobf(creator: ObfsTableCreator): ObfsTable {

        val settingsActivitySetInfo = creator.create("SettingsActivitySetInfo") { bridge ->
            bridge.findMethod {
                matcher {
                    usingEqStrings(" • @")
                }
            }.single().toObfsInfo()
        }

        var located = false

        var subtitleView: FieldData? = null
        var titleView: FieldData? = null

        fun locateViews() {
            val bridge = creator.bridge
            if (located) return
            located = true

            val createView =
                bridge.findMethod {
                    matcher {
                        name("createView")
                        declaredClass(settingsActivitySetInfo.className)
                    }
                }.single()

            val insns = createView.insns
            var pos = 0
            val poss = mutableListOf<Int>()
            while (pos < insns.size) {
                val w = getInsnWide(insns, pos)
                poss.add(pos)
                pos += w
            }


            for (i in 0 until poss.size - 3) {
                val op1 = insns[poss[i]].code
                val op2 = insns[poss[i + 1]].code
                val op3 = insns[poss[i + 2]].code
                //
                //    0096dac4: 0c02                    028a: move-result-object  v2
                //                             .line 652
                //    0096dac6: 5b02 7fee               028b: iput-object         v2, v0, Lorg/telegram/ui/y91;->J:Landroid/widget/TextView; # field@ee7f
                //                             .line 654
                //    0096daca: 1506 5041               028d: const/high16        v6, 0x41500000 (13.0f)
                //                             .line 656
                //    0096dace: 6e30 f60a 3206          028f: invoke-virtual      {v2, v3, v6}, Landroid/widget/TextView;->setTextSize(I, F)V # method@0af6
                if (op1.and(0xff) == 0x5b && op2.and(0xff) == 0x15 && op3.and(0xff) == 0x6e) {
                    val iput = decodeIInstanceOp(insns, poss[i]) ?: continue
                    if (iput.type != IInstanceOpType.IPutObject) continue
                    val iputField =
                        bridge.getFieldDataByDexAndId(createView.dexId, iput.ref) ?: continue
                    if (iputField.typeName != "android.widget.TextView") {
                        continue
                    }
                    val constHigh = decodeConstHigh16(insns, poss[i + 1]) ?: continue
                    val invoke = decodeInvoke(insns, poss[i + 2]) ?: continue
                    if (invoke.regs.size != 3) {
                        continue
                    }
                    val invokeMethod =
                        bridge.getMethodDataByDexAndId(createView.dexId, invoke.ref) ?: continue
                    if (invokeMethod.descriptor != "Landroid/widget/TextView;->setTextSize(IF)V") {
                        continue
                    }
                    if (invoke.regs[0] != iput.srcReg) {
                        continue
                    }
                    if (invoke.regs[2] != constHigh.dstReg) {
                        continue
                    }
                    when (java.lang.Float.intBitsToFloat(constHigh.value)) {
                        13f -> {
                            subtitleView = iputField
                        }

                        22f -> {
                            titleView = iputField
                        }
                    }
                    if (subtitleView != null && titleView != null) break
                }
            }
        }

        creator.create("SettingsActivitySubtitleView") {
            if (subtitleView == null) {
                locateViews()
            }
            require(subtitleView != null) { "could not locate subtitleView" }
            subtitleView.toObfsInfo()
        }

        creator.create("SettingsActivityTitleView") {
            if (titleView == null) {
                locateViews()
            }
            require(titleView != null) { "could not locate titleView" }
            titleView.toObfsInfo()
        }

        locateProfileActivityOnBindViewHolderAndRowFields(creator)

        creator.create("TextDetailCell") { bridge ->
            bridge.findClass {
                matcher {
                    // onInitializeAccessibilityNodeInfo
                    usingEqStrings(": ")
                    superClass("android.widget.FrameLayout")
                    addField {
                        type {
                            superClass("android.widget.TextView")
                        }
                    }
                    addField {
                        type("android.widget.ImageView")
                    }
                }
            }.single().toObfsInfo()
        }

        creator.create("ProfileActivityUserId") { bridge ->
            bridge.findMethod {
                matcher {
                    declaredClass("org.telegram.ui.ProfileActivity")
                    name("onFragmentCreate")
                }
            }.single().let {
                val insns = it.insns
                var pos = 0
                val poss = mutableListOf<Int>()
                while (pos < insns.size) {
                    val w = getInsnWide(insns, pos)
                    poss.add(pos)

                    pos += w
                }

                val searchUserIdMax = 10

                // the first iput-wide in onFragmentCreated
                //     0092757c: 5ae0 41b9               000a: iput-wide           v0, v14, Lorg/telegram/ui/ProfileActivity;->d1:J # field@b941
                var userIdField: FieldData? = null

                for (i in 0 until min(poss.size, searchUserIdMax)) {
                    val iop = decodeIInstanceOp(insns, poss[i]) ?: continue
                    if (iop.type != IInstanceOpType.IPutWide) continue
                    userIdField = bridge.getFieldDataByDexAndId(it.dexId, iop.ref) ?: continue
                    break
                }

                userIdField!!.toObfsInfo()
            }
        }

        return creator.obfsTable
    }

    private fun locateProfileActivityOnBindViewHolderAndRowFields(creator: ObfsTableCreator) {
        val bridge = creator.bridge
        var onBindViewHolder: MethodData? = null
        var getNumberRowMethod: MethodData? = null
        var getPhoneRowMethod: MethodData? = null
        var theme_key_switch2TrackChecked: FieldData? = null

        var located = false

        fun doLocate() {
            if (located) return
            located = true
            // ProfileActivity has some keep fields, so its class name won't be obfuscated
            // org/telegram/messenger/* are also not obfuscated because of proguard rules, so MessageController/UserConfig is visible


            // numberRow

            //    0091cc68: 7110 08a4 0800          123c: invoke-static       {v8}, Lorg/telegram/ui/ProfileActivity;->F1(Lorg/telegram/ui/ProfileActivity;)I # method@a408
            //    0091cc6e: 0a00                    123f: move-result         v0
            //    0091cc70: 3307 3f00               1240: if-ne               v7, v0, :cond_127f
            //                             .line 473
            //    0091cc74: 7110 0da4 0800          1242: invoke-static       {v8}, Lorg/telegram/ui/ProfileActivity;->G1(Lorg/telegram/ui/ProfileActivity;)I # method@a40d
            //    0091cc7a: 0a00                    1245: move-result         v0
            //    0091cc7c: 7110 842b 0000          1246: invoke-static       {v0}, Lorg/telegram/messenger/UserConfig;->getInstance(I)Lorg/telegram/messenger/UserConfig; # method@2b84
            //    0091cc82: 0c00                    1249: move-result-object  v0
            //    0091cc84: 6e10 822b 0000          124a: invoke-virtual      {v0}, Lorg/telegram/messenger/UserConfig;->getCurrentUser()Lorg/telegram/tgnet/TLRPC$User; # method@2b82
            //    0091cc8a: 0c00                    124d: move-result-object  v0
            //    0091cc8c: 3800 1c00               124e: if-eqz              v0, :cond_126a
            //                             .line 474
            //    0091cc90: 5403 6d52               1250: iget-object         v3, v0, Lorg/telegram/tgnet/TLRPC$User;->phone:Ljava/lang/String; # field@526d

            // phoneRow

            //    0091c71e: 7110 36a5 0800          0f97: invoke-static       {v8}, Lorg/telegram/ui/ProfileActivity;->y1(Lorg/telegram/ui/ProfileActivity;)I # method@a536 (synthetic method to get phoneRow)
            //    0091c724: 0a00                    0f9a: move-result         v0
            //    0091c726: 1a02 2301               0f9b: const-string        v2, "+" # string@0123
            //    0091c72a: 3307 7a00               0f9d: if-ne               v7, v0, :cond_1017
            //                             .line 400
            //    0091c72e: 6e10 215d 0800          0f9f: invoke-virtual      {v8}, Lorg/telegram/ui/ActionBar/r2;->getMessagesController()Lorg/telegram/messenger/MessagesController; # method@5d21
            //    0091c734: 0c00                    0fa2: move-result-object  v0
            //    0091c736: 7110 eba4 0800          0fa3: invoke-static       {v8}, Lorg/telegram/ui/ProfileActivity;->o0(Lorg/telegram/ui/ProfileActivity;)J # method@a4eb
            //    0091c73c: 0b03                    0fa6: move-result-wide    v3
            //    0091c73e: 7120 1a16 4300          0fa7: invoke-static       {v3, v4}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long; # method@161a
            //    0091c744: 0c03                    0faa: move-result-object  v3
            //    0091c746: 6e20 b628 3000          0fab: invoke-virtual      {v0, v3}, Lorg/telegram/messenger/MessagesController;->getUser(Ljava/lang/Long;)Lorg/telegram/tgnet/TLRPC$User; # method@28b6

            // ProfileActivity.ListAdapter#onBindViewHolder
            onBindViewHolder = bridge.findMethod {
                matcher {
                    usingEqStrings("ProfileBirthdayTodayValueYear")
                }
            }.single()

            val insns = onBindViewHolder.insns
            var pos = 0
            val poss = mutableListOf<Int>()
            while (pos < insns.size) {
                val w = getInsnWide(insns, pos)
                poss.add(pos)
                pos += w
            }

            val searchMax = 13

            for (i in 0 until poss.size - searchMax) {
                val invoke = decodeInvoke(insns, poss[i]) ?: continue
                if (invoke.type != InvokeType.Static) continue
                val fieldAccessMethod =
                    bridge.getMethodDataByDexAndId(onBindViewHolder.dexId, invoke.ref) ?: continue
                if (fieldAccessMethod.className != "org.telegram.ui.ProfileActivity" || fieldAccessMethod.returnTypeName != "int") continue
                if (getNumberRowMethod == null) {
                    val status_begin = 0
                    val status_meet_if_ne = 1
                    val status_meet_invoke_getCurrentUser = 2
                    val status_meet_iget_phone = 3
                    var status = status_begin
                    for (j in i + 1 until i + searchMax) {
                        when (status) {
                            status_begin -> {
                                val op = insns[poss[j]].code.and(0xff)
                                if (op == 0x33) { // if-ne
                                    status = status_meet_if_ne
                                }
                            }

                            status_meet_if_ne -> {
                                val invoke2 = decodeInvoke(insns, poss[j]) ?: continue
                                if (invoke2.type != InvokeType.Virtual) continue
                                val m = bridge.getMethodDataByDexAndId(
                                    onBindViewHolder.dexId,
                                    invoke2.ref
                                ) ?: continue
                                if (m.descriptor == "Lorg/telegram/messenger/UserConfig;->getCurrentUser()Lorg/telegram/tgnet/TLRPC\$User;") {
                                    status = status_meet_invoke_getCurrentUser
                                }
                            }

                            status_meet_invoke_getCurrentUser -> {
                                val iget = decodeIInstanceOp(insns, poss[j]) ?: continue
                                if (iget.type != IInstanceOpType.IGetObject) continue
                                val f =
                                    bridge.getFieldDataByDexAndId(onBindViewHolder.dexId, iget.ref)
                                        ?: continue
                                if (f.descriptor == "Lorg/telegram/tgnet/TLRPC\$User;->phone:Ljava/lang/String;") {
                                    status = status_meet_iget_phone
                                    break
                                }
                            }
                        }
                    }
                    if (status == status_meet_iget_phone) {
                        getNumberRowMethod = fieldAccessMethod
                    }
                }
                if (getPhoneRowMethod == null) {
                    var hasConstStringPlus = false
                    var hasInvokeVirtualGetUser = false
                    var hasIfNe = false
                    for (j in i + 1 until i + searchMax) {
                        if (!hasIfNe) {
                            val op = insns[poss[j]].code.and(0xff)
                            if (op == 0x33) { // if-ne
                                hasIfNe = true
                            }
                        }
                        if (!hasConstStringPlus) {
                            val cs = decodeConstString(insns, poss[j])
                            if (cs != null) {
                                val s = bridge.getStringByDexAndId(onBindViewHolder.dexId, cs.ref)
                                if (s == "+") {
                                    hasConstStringPlus = true
                                }
                            }
                        }
                        if (!hasInvokeVirtualGetUser) {
                            val iv = decodeInvoke(insns, poss[j])
                            if (iv?.type == InvokeType.Virtual) {
                                val m =
                                    bridge.getMethodDataByDexAndId(onBindViewHolder.dexId, iv.ref)
                                if (m?.descriptor == "Lorg/telegram/messenger/MessagesController;->getUser(Ljava/lang/Long;)Lorg/telegram/tgnet/TLRPC\$User;") {
                                    hasInvokeVirtualGetUser = true
                                }
                            }
                        }
                        if (hasIfNe && hasInvokeVirtualGetUser && hasConstStringPlus) break
                    }
                    if (hasIfNe && hasInvokeVirtualGetUser && hasConstStringPlus) {
                        getPhoneRowMethod = fieldAccessMethod
                    }
                }
                if (getNumberRowMethod != null && getPhoneRowMethod != null) break
            }
            //    0091ce08: 6002 2919               130c: sget                v2, Lorg/telegram/messenger/R$drawable;->msg_input_gift:I # field@1929
            //    0091ce0c: 7120 020e 2000          130e: invoke-static       {v0, v2}, Le0/c;->c(Landroid/content/Context;, I)Landroid/graphics/drawable/Drawable; # method@0e02
            //    0091ce12: 0c00                    1311: move-result-object  v0
            //                             .line 500
            //    0091ce14: 2202 ea00               1312: new-instance        v2, Landroid/graphics/PorterDuffColorFilter; # type@00ea
            //    0091ce18: 6003 5d68               1314: sget                v3, Lorg/telegram/ui/ActionBar/o6;->V6:I # field@685d (Theme.key_switch2TrackChecked)

            val searchThemeKeyMax = 8
            for (i in 0 until poss.size - searchThemeKeyMax) {
                val sget1 = decodeSInstanceOp(insns, poss[i]) ?: continue
                if (sget1.type != SInstanceOpType.SGet) continue
                val sget1f =
                    bridge.getFieldDataByDexAndId(onBindViewHolder.dexId, sget1.ref) ?: continue
                if (sget1f.descriptor != "Lorg/telegram/messenger/R\$drawable;->msg_input_gift:I") continue
                for (j in i + 1 until i + searchThemeKeyMax) {
                    val sget2 = decodeSInstanceOp(insns, poss[j]) ?: continue
                    if (sget2.type != SInstanceOpType.SGet) continue
                    theme_key_switch2TrackChecked =
                        bridge.getFieldDataByDexAndId(onBindViewHolder.dexId, sget2.ref) ?: continue
                    break
                }
            }
        }

        creator.create("ProfileActivityListAdapterOnBindViewHolder") {
            if (onBindViewHolder == null) {
                doLocate()
            }
            require(onBindViewHolder != null) { "could not locate onBindViewHolder" }
            onBindViewHolder.toObfsInfo()
        }

        creator.create("ProfileActivityPhoneRowGetter") {
            if (getPhoneRowMethod == null) {
                doLocate()
            }
            require(getPhoneRowMethod != null) { "could not locate getPhoneRowMethod" }
            getPhoneRowMethod.toObfsInfo()
        }

        creator.create("ProfileActivityNumberRowGetter") {
            if (getNumberRowMethod == null) {
                doLocate()
            }
            require(getNumberRowMethod != null) { "could not locate getNumberRowMethod" }
            getNumberRowMethod.toObfsInfo()
        }

        creator.create("ThemeKeySwitch2TrackChecked") {
            if (theme_key_switch2TrackChecked == null) {
                doLocate()
            }
            require(theme_key_switch2TrackChecked != null) { "could not locate getNumberRowMethod" }
            theme_key_switch2TrackChecked.toObfsInfo()
        }

    }

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

        val table = deobf(TelegramHandler.creator)
        val SettingsActivitySetInfo = table["SettingsActivitySetInfo"]!!
        val SettingsActivitySubtitleView = table["SettingsActivitySubtitleView"]!!
        val SettingsActivityTitleView = table["SettingsActivityTitleView"]!!


        // hide phone number in settings
        // exists in 12.4.0

        val settingsActivity = findClass(SettingsActivitySetInfo.className)
        settingsActivity.hookAllAfter(
            SettingsActivitySetInfo.memberName,
            cond = ::isEnabled
        ) { param ->
            val subtitleView =
                param.thisObject.getObjAs<TextView>(SettingsActivitySubtitleView.memberName)
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
            val titleView =
                param.thisObject.getObjAs<TextView>(SettingsActivityTitleView.memberName)
            titleView.setOnClickListener {
                show = !show
                subtitleView.text =
                    (if (show) currentNumber else "点击显示电话号码") + textAfterPhone
            }
        }

        val ProfileActivityListAdapterOnBindViewHolder =
            table["ProfileActivityListAdapterOnBindViewHolder"]!!
        val ProfileActivityPhoneRowGetter = table["ProfileActivityPhoneRowGetter"]!!
        val ProfileActivityNumberRowGetter = table["ProfileActivityNumberRowGetter"]!!
        val TextDetailCell = table["TextDetailCell"]!!
        val ThemeKeySwitch2TrackChecked = table["ThemeKeySwitch2TrackChecked"]!!
        val ProfileActivityUserId = table["ProfileActivityUserId"]!!

        val themeClass = findClass(ThemeKeySwitch2TrackChecked.className)
        val key_switch2TrackChecked by lazy { themeClass.getObjS(ThemeKeySwitch2TrackChecked.memberName) }

        val classProfileActivity_ListAdapter =
            findClass(ProfileActivityListAdapterOnBindViewHolder.className)
        // not obfuscated
        val classLocaleController = findClass("org.telegram.messenger.LocaleController")
        val profileActivityClass = findClass("org.telegram.ui.ProfileActivity")
        val profileActivityUserid =
            profileActivityClass.getDeclaredField(ProfileActivityUserId.memberName)
                .also { it.isAccessible = true }
        val listAdapterThis0Field =
            classProfileActivity_ListAdapter.declaredFields.single { it.type == profileActivityClass }
                .also { it.isAccessible = true }
        val numberRowGetter = profileActivityClass.getDeclaredMethod(
            ProfileActivityNumberRowGetter.memberName,
            profileActivityClass
        )
            .also { it.isAccessible = true }
        val phoneRowGetter = profileActivityClass.getDeclaredMethod(
            ProfileActivityPhoneRowGetter.memberName,
            profileActivityClass
        )
            .also { it.isAccessible = true }
        val onBindViewHolder =
            classProfileActivity_ListAdapter.declaredMethods.single { it.name == ProfileActivityListAdapterOnBindViewHolder.memberName }
        // onBindViewHolder(RecyclerView.Holder, int)
        val holderClass = onBindViewHolder.parameterTypes[0]
        // the only View field
        val holderItemViewField = holderClass.declaredFields.single { it.type == View::class.java }
            .also { it.isAccessible = true }

        val textDetailClass = findClass(TextDetailCell.className)
        // the only field which type is TextView's subclass and different form any other one
        val textDetailCellTextViewField = textDetailClass.declaredFields
            .filter { TextView::class.java.isAssignableFrom(it.type) && it.type != TextView::class.java }
            .groupBy { it.type }
            .toList()
            .single { it.second.size == 1 }
            .second.single().also { it.isAccessible = true }
        logD("TextDetailCell.textView = $textDetailCellTextViewField")

        onBindViewHolder.hookAfter(
            cond = ::isEnabled
        ) { param ->
            val profileActivity = listAdapterThis0Field.get(param.thisObject)!!
            val phoneRow = phoneRowGetter.invoke(null, profileActivity) as Int
            val numberRow = numberRowGetter.invoke(null, profileActivity) as Int
            if (param.args[1] != phoneRow && param.args[1] != numberRow) return@hookAfter
            val cell = holderItemViewField.get(param.args[0]) as View
            val ctx = cell.context
            ctx.addModuleAssets(Entry.modulePath)
            val tv = textDetailCellTextViewField.get(cell) as TextView
            val phoneNumber = tv.text
            val phoneHidden = classLocaleController.callS(
                "getString",
                ctx.resources.getIdentifier("PhoneHidden", "string", ctx.packageName)
            )
            if (phoneNumber == phoneHidden) {
                return@hookAfter
            }

            var show by extraField(
                profileActivity,
                "showPhoneNumber",
                if (TelegramHandler.settings.hidePhoneNumberForSelfOnly) {
                    val currentUserId = profileActivity.call("getUserConfig").getObj("clientUserId")
                    val userId = profileActivityUserid.getLong(profileActivity)
                    currentUserId != userId
                } else false
            )
            tv.text = if (show) phoneNumber else "号码已隐藏"
            fun setIcon() {
                val icon =
                    (if (show) ctx.getDrawable(R.drawable.ic_visible) else ctx.getDrawable(R.drawable.ic_invisible))!!
                // dontApplyPeerColor simply returns the color
                val filter = PorterDuffColorFilter(
                    // profileActivity.call(
                    // "dontApplyPeerColor",
                    profileActivity.call("getThemedColor", key_switch2TrackChecked) as Int //, false
                    // ) as Int,
                    ,
                    PorterDuff.Mode.MULTIPLY
                )
                icon.colorFilter = filter
                // not obfuscated
                cell.call("setImage", icon)
            }
            setIcon()
            // not obfuscated
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