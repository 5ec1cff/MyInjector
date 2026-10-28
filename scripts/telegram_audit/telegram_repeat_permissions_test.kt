// The generator inserts the production permission methods into Gate. These
// fixtures model native API results and UI states; they do not copy the gate.
private object RepeatPermissionTest {
    class View(var visibility: Int = 0) { companion object { const val VISIBLE = 0 } }
    class Peer {
        @JvmField var join_to_send = false
        var joined = true
        var writable = true
        var messages = true
        var media = true
        var forum = false
        var mono = false
        var manageMono = false
        var manageTopic = false
    }
    class User(@JvmField var id: Long = 100) {
        var deleted = false
        var reply = false
        var botForum = false
    }
    class Topic(@JvmField var closed: Boolean = false)
    class Blocked {
        var blocked = false
        fun indexOfKey(id: Long) = if (blocked) 0 else -1
    }
    class Controller { @JvmField val blockePeers = Blocked() }
    class Conversation {
        @JvmField val input = View()
        @JvmField val restrictionOverlay = View(4)
        @JvmField val channelButtons = View(4)
        @JvmField var searchVisible = false
        @JvmField var currentChat: Peer? = null
        @JvmField var currentUser: User? = User()
        @JvmField var forumTopic: Topic? = null
        @JvmField var thread: Any? = null
        val controller = Controller()
        var needsReply = false
        fun getMessagesController() = controller
        fun getCurrentAccount() = 0
        fun forumReplyRequired() = needsReply
    }
    object ChatApi {
        @JvmStatic fun canSendMessages(peer: Peer) = peer.messages
        @JvmStatic fun canSendAnyMedia(peer: Peer) = peer.media
        @JvmStatic fun isMonoForum(peer: Peer) = peer.mono
        @JvmStatic fun canManageMonoForum(account: Int, peer: Peer) = peer.manageMono
        @JvmStatic fun isNotInChat(peer: Peer) = !peer.joined
        @JvmStatic fun canWriteToChat(peer: Peer) = peer.writable
        @JvmStatic fun isForum(peer: Peer) = peer.forum
        @JvmStatic fun canManageTopic(account: Int, peer: Peer, topic: Topic) = peer.manageTopic
    }
    object UserApi {
        @JvmStatic fun isDeleted(user: User) = user.deleted
        @JvmStatic fun isReplyUser(user: User) = user.reply
        @JvmStatic fun isBotForum(user: User?) = user?.botForum == true
    }
    private fun Any?.getObj(name: String): Any? = this!!.javaClass.getField(name).get(this)
    private fun Any?.call(name: String, vararg args: Any?): Any? = this!!.javaClass.methods
        .single { it.name == name && it.parameterCount == args.size }.invoke(this, *args)
    private fun Class<*>.callS(name: String, vararg args: Any?): Any? = methods
        .single { it.name == name && it.parameterCount == args.size }.invoke(null, *args)
    class Gate {
        private val fields = Conversation::class.java.fields.associateBy { it.name }
        private val methods = mapOf("forumReplyRequired" to Conversation::class.java.getMethod("forumReplyRequired"))
        private val chatObject = ChatApi::class.java
        private val userObject = UserApi::class.java
        private val verifyUser = 489000L
        // PRODUCTION_METHODS
    }
    fun run() {
        val gate = Gate()
        var count = 0
        fun scenario(name: String, expected: Boolean, setup: Conversation.() -> Unit) {
            val chat = Conversation().apply(setup)
            check(gate.canSend(chat) == expected) { "Repeat permission: $name" }
            count++
        }
        fun Conversation.search() { searchVisible = true; input.visibility = 4 }
        fun Conversation.group() { currentUser = null; currentChat = Peer() }
        scenario("normal composer", true) {}
        scenario("hidden without search", false) { input.visibility = 4 }
        scenario("private search", true) { search() }
        scenario("group search", true) { search(); group() }
        scenario("channel subscriber search", false) { search(); group(); currentChat!!.writable = false }
        scenario("channel publisher search", true) { search(); group() }
        scenario("group ban without visible overlay", false) { search(); group(); currentChat!!.messages = false; currentChat!!.media = false }
        scenario("media-only permission", true) { search(); group(); currentChat!!.messages = false }
        scenario("native restriction overlay", false) { search(); restrictionOverlay.visibility = 0 }
        scenario("premium or frozen overlay", false) { search(); channelButtons.visibility = 0 }
        scenario("closed topic", false) { search(); group(); forumTopic = Topic(true) }
        scenario("topic manager", true) { search(); group(); forumTopic = Topic(true); currentChat!!.manageTopic = true }
        scenario("general topic requires reply", false) { search(); group(); needsReply = true }
        scenario("left group", false) { search(); group(); currentChat!!.joined = false }
        scenario("open comments without membership", true) { search(); group(); currentChat!!.joined = false; thread = Any() }
        scenario("comments require joining", false) { search(); group(); currentChat!!.joined = false; currentChat!!.join_to_send = true; thread = Any() }
        scenario("blocked user", false) { search(); controller.blockePeers.blocked = true }
        scenario("deleted user", false) { search(); currentUser!!.deleted = true }
        scenario("replies service", false) { search(); currentUser!!.reply = true }
        scenario("verification service", false) { search(); currentUser!!.id = 489000 }
        scenario("subscriber suggestions", true) { search(); group(); currentChat!!.mono = true; currentChat!!.writable = false }
        val live = Conversation().apply { search(); group() }
        check(gate.canSend(live))
        live.currentChat!!.writable = false
        check(!gate.canSend(live))
        live.currentChat!!.writable = true
        check(gate.canSend(live))
        println("Repeat permission scenarios: $count passed; live permission changes passed")
    }
}
