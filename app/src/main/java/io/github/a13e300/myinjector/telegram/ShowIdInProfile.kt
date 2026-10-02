package io.github.a13e300.myinjector.telegram

// Feature ported from https://github.com/cinit/TMoe (ShowIdInProfile).
class ShowIdInProfile : MyDynHook("showIdInProfile") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.showIdInProfile

    override fun onHook() {
        val fields = ProfileActivityRowHook.initialize(classLoader)
        ProfileActivityRowHook.register(0, ::isEnabled) { profile ->
            val userId = fields.getValue("user_id").getLong(profile)
            if (userId != 0L) {
                ProfileRowContent(userId.toString(), "用户 ID")
            } else {
                val chatId = fields.getValue("chat_id").getLong(profile)
                val topic = fields.getValue("topic_id")
                val topicId = (topic.get(profile) as Number).toLong()
                if (topicId == 0L) ProfileRowContent(chatId.toString(), "群组 / 频道 ID")
                else ProfileRowContent("$chatId/$topicId", "群组 / 话题 ID")
            }
        }
    }
}
