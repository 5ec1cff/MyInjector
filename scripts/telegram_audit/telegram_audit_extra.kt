private fun auditKeptApi(bridge: DexKitBridge, feature: String) {
    fun cls(name: String) = bridge.getClassData(name) ?: error("Missing class $name")
    fun method(owner: String, name: String, params: List<String>? = null): MethodData {
        return cls(owner).methods.single { it.name == name && (params == null || it.paramTypeNames == params) }
    }
    fun field(owner: String, name: String) = cls(owner).fields.single { it.name == name }
    when (feature) {
        "UseSystemEmoji" -> { findSystemEmoji(bridge) }
        "NoGoogleMaps" -> telegramApiMethod(bridge, "org.telegram.messenger.AndroidUtilities", "isMapsInstalled", "org.telegram.ui.ActionBar.BaseFragment")
        "ShowExactLastSeenTime" -> {
            telegramApiMethod(bridge, "org.telegram.messenger.LocaleController", "formatDateOnline", "long", "boolean[]")
            telegramApiMethod(bridge, "org.telegram.messenger.LocaleController", "getString", "java.lang.String", "int")
        }
        "ChannelDetailNumbers" -> telegramApiMethod(bridge, "org.telegram.messenger.LocaleController", "formatShortNumber", "int", "int[]")
        "AlwaysShowDownloadManager" -> telegramApiMethod(bridge, "org.telegram.messenger.DownloadController", "hasUnviewedDownloads")
        "RemoveArchiveFolder" -> {
            telegramApiMethod(bridge, "org.telegram.messenger.MessagesController", "getDialogs", "int")
            telegramApiMethod(bridge, "org.telegram.messenger.MessagesController", "removeFolder", "int")
        }
        "StickerLoadGuard" -> { findStickerStream(bridge) }
    }
}

private fun auditProfileBlurMembers(bridge: DexKitBridge, members: Map<String, String>) {
    val actions = bridge.getFieldData(members.getValue("actionsView"))!!
    val blurActions = members.getValue("blurActionsView").takeIf { it.isNotEmpty() }?.let { bridge.getFieldData(it)!! }
    check(blurActions == null || actions.typeName == blurActions.typeName)
    val background = bridge.getMethodData(members.getValue("topUpdateBackgroundPaint"))!!
    // 未混淆版通过编译器生成的外部类字段 getter 访问 actionsView。
    val getters = background.invokes.filter {
        it.modifiers and 0x1000 != 0 && it.paramTypeNames == listOf(actions.className)
    }
    val reads = background.usingFields + getters.flatMap { it.usingFields }
    val candidates = reads.map { it.field }.distinctBy { it.descriptor }.filter {
        it.className == actions.className && it.type.superClass?.name == "android.view.View" &&
            it.type.fields.any { field -> field.typeName == "android.graphics.RadialGradient" }
    }
    check(candidates.any { it.descriptor == actions.descriptor })
    val setter = bridge.getClassData(blurActions?.className ?: actions.className)!!.methods.singleOrNull {
        it.name == "setActionsView" && it.paramCount == 1
    }
    if (setter != null) {
        check(setter.paramTypeNames.single() == actions.typeName)
        check(setter.usingFields.any { it.field.descriptor == blurActions?.descriptor })
    }
    members.getValue("blurMusicView").takeIf { it.isNotEmpty() }?.let {
        val music = bridge.getFieldData(it)!!
        check(music.typeName != actions.typeName)
        val draw = bridge.getMethodData(members.getValue("musicOnDraw"))!!
        val paint = bridge.getFieldData(members.getValue("musicBackgroundPaint"))!!
        val expanded = bridge.getFieldData(members.getValue("musicParentExpanded"))!!
        check(draw.className == music.typeName && draw.paramTypeNames == listOf("android.graphics.Canvas"))
        check(paint.className == music.typeName && paint.typeName == "android.graphics.Paint")
        check(expanded.className == music.typeName && expanded.typeName == "float")
        check(scanTelegramDex(bridge, draw).calls.any { call ->
            call.method.name == "drawRoundRect" && call.args.lastOrNull()?.field?.descriptor == paint.descriptor
        })
    }
    println("Profile blur: ${candidates.size} gradient candidates; actions=${actions.descriptor}")
}

private fun auditReflectionShapes(bridge: DexKitBridge) {
    val media = bridge.getClassData("org.telegram.tgnet.TLRPC\$MessageMedia")!!
    check(media.fields.single { it.name == "spoiler" }.typeName == "boolean")
    check(bridge.getClassData("org.telegram.tgnet.TLRPC\$Message")!!.fields.single { it.name == "media" }.typeName == media.name)
    auditContactRows(bridge, (bridge.getClassData("org.telegram.ui.ActionBar.BaseFragment") ?: bridge.findClass { matcher { addField { name("fragmentView"); type("android.view.View") } } }.single()).name)
    // Validate the reflection predicates executed after the DexKit searches.
    val base = (bridge.getClassData("org.telegram.ui.ActionBar.BaseFragment") ?: bridge.findClass { matcher { addField { name("fragmentView"); type("android.view.View") } } }.single()).name
    val emoji = findEmojiStickerMenu(bridge, base)
    val sticker = bridge.getMethodData(emoji.getValue("stickerClick"))!!
    val owner = emoji.getValue("stickerOwner")
    check(owner.isEmpty() || bridge.getFieldData(owner)!!.className == sticker.className)
    val menu = findCopyPrivateChatLink(bridge)
    val sub = bridge.getClassData(menu.getValue("subClass"))!!
    val provider = bridge.getMethodData(menu.getValue("provider"))!!.returnTypeName
    check(sub.methods.count { it.isConstructor && it.paramCount == 4 &&
        it.paramTypeNames.count { t -> t == "boolean" } == 2 &&
        it.paramTypeNames.containsAll(listOf("android.content.Context", provider)) } == 1)
    val info = findInfoContainer(bridge, findProfileMenu(bridge).getValue("menu"))
    val setter = bridge.getMethodData(info.getValue("setAdminText"))!!
    check(setter.paramTypeNames.count { it == "java.lang.CharSequence" } == 2)
    check(bridge.getFieldData(info.getValue("infoContainer"))!!.typeName == "android.widget.LinearLayout")
    val separator = bridge.getClassData(bridge.getFieldData(info.getValue("settingsTopSection"))!!.typeName)!!
    check(separator.superClass?.name == "android.view.View")
    check(findProfileMenu(bridge).getValue("discussionId") == "22")
    val chat = bridge.getMethodData(menu.getValue("createMenu"))!!.className
    val longClick = AuditLongClickMention.deobf(ObfsTableCreator(bridge, ObfsInfo(base, "")))
    val span = bridge.getClassData(longClick.getValue("urlSpanUserMention").className)!!
    check(span.methods.count { it.isConstructor && it.paramCount == 3 && it.paramTypeNames.take(2) == listOf("java.lang.String", "int") } == 1)
    val replace = bridge.getClassData("org.telegram.ui.Components.ChatActivityEnterView")!!.methods
        .single { it.paramTypeNames == listOf("int", "int", "java.lang.CharSequence", "boolean") }
    check(!Modifier.isStatic(replace.modifiers))
    val repeat = findRepeatMessage(bridge, chat, menu.getValue("createMenu"), menu.getValue("options"))
    auditRepeatPermissionApis(bridge, base, repeat)
    if (bridge.getClassData("org.telegram.messenger.R\$string") == null) return
    val input = bridge.getFieldData(repeat.getValue("input"))!!
    val overlay = bridge.getFieldData(repeat.getValue("restrictionOverlay"))!!
    check(input.descriptor == longClick.getValue("chatActivityChatEnterViewField").descriptor)
    check(input.className == chat && overlay.className == chat && input != overlay)
    check((overlay.typeName == "android.widget.FrameLayout" || overlay.type.superClass?.name == "android.widget.FrameLayout"))
    val update = bridge.findMethod { matcher {
        declaredClass(chat); returnType("void"); paramTypes()
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("EncryptionProcessing") }
    } }.single()
    // The same overlay blocks group bans and pending/rejected encryption;
    // established secret chats and normal chats clear it again.
    check(update.invokes.any { it.className == "org.telegram.messenger.ChatObject" && it.name == "canSendMessages" })
    val visibility = scanTelegramDex(bridge, update).calls.filter { it.method.name == "setVisibility" }
    for (field in listOf(input, overlay)) {
        check(visibility.any { it.receiver?.field?.descriptor == field.descriptor && it.args.single()?.number == 0 })
        check(visibility.any { it.receiver?.field?.descriptor == field.descriptor && it.args.single()?.number == 4 })
    }
    val forward = bridge.getMethodData(repeat.getValue("forward"))!!
    check(!Modifier.isStatic(forward.modifiers) && forward.paramCount in listOf(6, 7, 8))
}

private fun auditRepeatPermissionApis(bridge: DexKitBridge, base: String, repeat: Map<String, String>) {
    // New official builds rename and inline these helpers; the production
    // search resolves them through TelegramApiDeobf instead of this legacy
    // shape-only fixture.
    if (bridge.getClassData("org.telegram.messenger.ChatObject") == null) return
    fun method(owner: String, name: String, vararg params: String) =
        bridge.getClassData(owner)!!.methods.single { it.name == name && it.paramTypeNames == params.toList() }
    val chat = "org.telegram.tgnet.TLRPC\$Chat"
    val user = "org.telegram.tgnet.TLRPC\$User"
    val topic = "org.telegram.tgnet.TLRPC\$TL_forumTopic"
    val chatObject = "org.telegram.messenger.ChatObject"
    val userObject = "org.telegram.messenger.UserObject"
    for (name in listOf("canSendMessages", "canSendAnyMedia", "isMonoForum", "isNotInChat", "canWriteToChat", "isForum")) {
        check(Modifier.isStatic(method(chatObject, name, chat).modifiers))
    }
    method(chatObject, "canManageMonoForum", "int", chat)
    method(chatObject, "canManageTopic", "int", chat, topic)
    for (name in listOf("isDeleted", "isReplyUser", "isBotForum")) method(userObject, name, user)
    check(bridge.getClassData(userObject)!!.fields.single { it.name == "VERIFY" }.typeName == "long")
    val blocked = bridge.getClassData("org.telegram.messenger.MessagesController")!!.fields.single { it.name == "blockePeers" }
    check(method(blocked.typeName, "indexOfKey", "long").returnTypeName == "int")
    check(method(base, "getCurrentAccount").returnTypeName == "int")
    check(bridge.getFieldData(repeat.getValue("searchVisible"))!!.typeName == "boolean")
    check(bridge.getFieldData(repeat.getValue("currentUser"))!!.typeName == user)
    check(bridge.getFieldData(repeat.getValue("forumTopic"))!!.typeName == topic)
    val forumReply = bridge.getMethodData(repeat.getValue("forumReplyRequired"))!!
    check(forumReply.returnTypeName == "boolean" && forumReply.paramCount == 0 && !Modifier.isStatic(forumReply.modifiers))
}

private fun auditContactRows(bridge: DexKitBridge, base: String) {
    val permissions = bridge.findMethod { matcher {
        usingStrings("android.permission.READ_CONTACTS")
        declaredClass { superClass(base) }
        addInvoke { descriptor("Landroid/content/Context;->checkSelfPermission(Ljava/lang/String;)I") }
        addInvoke { descriptor("Landroid/app/Activity;->shouldShowRequestPermissionRationale(Ljava/lang/String;)Z") }
    } }
    check(permissions.size <= 1) { "ContactPermission must not silently skip ambiguous matches" }
    println("CONTACT Permission candidates: ${permissions.size}")
    val shared = AuditAutoUncheckSharePhoneNumber.deobf(ObfsTableCreator(bridge, ObfsInfo(base, "")))
    val callback = bridge.getMethodData(shared.getValue("ContactAddActivityFillItems").descriptor)!!
    val activity = shared.getValue("ContactAddActivity").className
    if (callback.className != activity) {
        check(callback.declaredClass!!.fields.count { it.typeName == "java.lang.Object" } == 1)
    }
    val rowId = shared.getValue("ContactAddActivityShareItem.id").memberName
    if (rowId.isNotEmpty()) {
        check(rowId == "2") { "Share-number row ID: $rowId" }
        val id = bridge.getFieldData(shared.getValue("ContactAddActivityShareItem.idField").descriptor)!!
        val checked = bridge.getFieldData(shared.getValue("ContactAddActivityShareItem.checkedField").descriptor)!!
        check(id.typeName == "int" && checked.typeName == "boolean" && id.className == checked.className)
        check(callback.usingFields.any { it.usingType == FieldUsingType.Write && it.field.descriptor == shared.getValue("ContactAddActivityCheckShare").descriptor })
    }
}

private fun auditStickerPackRefresh(bridge: DexKitBridge, members: Map<String, String>) {
    fun method(key: String) = bridge.getMethodData(members.getValue(key))!!
    val load = method("load")
    val full = "org.telegram.tgnet.TLRPC\$TL_messages_stickerSet"
    val input = "org.telegram.tgnet.TLRPC\$InputStickerSet"
    val request = "org.telegram.tgnet.TLRPC\$TL_messages_getStickerSet"
    check(load.paramCount == 0 || load.paramTypeNames == listOf("boolean"))
    for (key in listOf("getName", "getId")) {
        check(load.invokes.any { it.descriptor == members.getValue(key) })
        check(method(key).returnTypeName == full && !Modifier.isStatic(method(key).modifiers))
    }
    for ((key, type) in listOf("input" to input, "set" to full)) {
        val field = bridge.getFieldData(members.getValue(key))!!
        check(field.className == load.className && field.typeName == type)
    }
    val get = method("get")
    val fetch = method("fetch")
    check(get.paramTypeNames.first() == input && !Modifier.isStatic(get.modifiers))
    check(get.paramTypes.last().methods.count {
        Modifier.isAbstract(it.modifiers) && it.paramCount == 1
    } == 1)
    check(fetch.paramTypeNames.first() == input && !Modifier.isStatic(fetch.modifiers))
    check(fetch.paramTypes.last().methods.count {
        Modifier.isAbstract(it.modifiers) && it.paramCount == 2
    } == 1)
    // 两条网络分支都沿用请求对象默认的 hash=0。
    for (m in listOf(load, fetch)) {
        check(m.usingFields.any { it.field.className == request && it.field.name == "stickerset" })
        check(m.usingFields.none {
            it.field.className == request && it.field.name == "hash" && it.usingType == FieldUsingType.Write
        })
    }
    check(method("put").paramTypeNames == listOf(full, "boolean"))
    val init = members.getValue("emojiInit").takeIf { it.isNotEmpty() }?.let { bridge.getMethodData(it)!! }
    check((method("emojiShow").invokes + init?.invokes.orEmpty()).any { it.descriptor == get.descriptor })
    val listener = method("emojiNotification")
    check(listener.paramTypeNames == listOf("int", "int", "java.lang.Object[]"))
    check(listener.usingFields.any { it.field.descriptor == members.getValue("event") })
    check(bridge.getFieldData(members.getValue("event"))!!.className == method("post").className)
}
