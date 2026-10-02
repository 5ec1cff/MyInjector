package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.wrap.DexField

// Feature ported from https://github.com/cinit/TMoe (ShowDCInProfile).
class ShowDCInProfile : MyDynHook("showDCInProfile") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.showDCInProfile

    override fun onHook() {
        val profile = ProfileActivityRowHook.initialize(classLoader).getValue("user_id").declaringClass
        val fields = profileDataCenterMembers(TelegramHandler.creator, profile.name).mapValues {
            DexField(it.value.descriptor).getFieldInstance(classLoader).apply { isAccessible = true }
        }
        ProfileActivityRowHook.register(1, ::isEnabled) { activity ->
            val userInfo = fields.getValue("userInfo").get(activity)
            val chatInfo = fields.getValue("chatInfo").get(activity)
            val photo = userInfo?.let { fields.getValue("profilePhoto").get(it) }
                ?: chatInfo?.let { fields.getValue("chatPhoto").get(it) }
            // Empty/missing photos do not reveal a DC. Use the same text for
            // display and copying, including the dc_id == 0 case.
            val dc = photo?.let { fields.getValue("dcId").getInt(it) } ?: 0
            ProfileRowContent(if (dc > 0) "DC$dc" else "未知", "数据中心")
        }
    }
}

internal fun findProfileDataCenter(bridge: DexKitBridge, profile: String): Map<String, FieldData> {
    // UI fields are obfuscated; tgnet protocol classes and members are kept.
    fun field(owner: String, type: String, name: String? = null) = bridge.findField { matcher {
        declaredClass(owner); type(type)
        name?.let { name(it) }
    } }.single()
    return linkedMapOf(
        "userInfo" to field(profile, "org.telegram.tgnet.TLRPC\$UserFull"),
        "chatInfo" to field(profile, "org.telegram.tgnet.TLRPC\$ChatFull"),
        "profilePhoto" to field("org.telegram.tgnet.TLRPC\$UserFull", "org.telegram.tgnet.TLRPC\$Photo", "profile_photo"),
        "chatPhoto" to field("org.telegram.tgnet.TLRPC\$ChatFull", "org.telegram.tgnet.TLRPC\$Photo", "chat_photo"),
        "dcId" to field("org.telegram.tgnet.TLRPC\$Photo", "int", "dc_id"),
    )
}

private fun profileDataCenterMembers(creator: ObfsTableCreator, profile: String) = run {
    val found by lazy { findProfileDataCenter(creator.bridge, profile) }
    listOf("userInfo", "chatInfo", "profilePhoto", "chatPhoto", "dcId").associateWith { key ->
        creator.create("ProfileDC.$key") { found.getValue(key).toObfsInfo() }
    }
}
