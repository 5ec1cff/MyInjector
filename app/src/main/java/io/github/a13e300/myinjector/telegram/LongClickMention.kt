package io.github.a13e300.myinjector.telegram

import android.text.SpannableString
import android.text.Spanned
import io.github.a13e300.myinjector.arch.IInstanceOpType
import io.github.a13e300.myinjector.arch.InvokeType
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.decodeIInstanceOp
import io.github.a13e300.myinjector.arch.decodeInvoke
import io.github.a13e300.myinjector.arch.getInsnWide
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logE
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Proxy

// 在 at 列表中，长按以强制使用无用户名的 at 形式
class LongClickMention : MyDynHook("longClickMention") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.longClickMention

    private fun deobf(creator: ObfsTableCreator): ObfsTable {
        var chatActivityCreateView: MethodData? = null
        var urlSpanUserMention: ClassData? = null
        var recyclerListView: ClassData? = null
        var chatActivityMentionContainer: FieldData? = null
        var mentionsContainerViewListView: FieldData? = null
        var mentionsContainerViewAdapter: FieldData? = null
        var mentionsAdapterResultStartPosition: FieldData? = null
        var mentionsAdapterResultLength: FieldData? = null
        var mentionsAdapterGetItem: MethodData? = null
        var chatActivityChatEnterViewField: FieldData? = null
        var recyclerListViewOnItemLongClickListener: MethodData? = null

        var called = false
        fun doDeobf() {
            if (called) return
            called = true
            val bridge = creator.bridge
            chatActivityCreateView = bridge.findMethod {
                matcher {
                    usingEqStrings("ChatActivity.createView")
                }
            }.single()

            urlSpanUserMention = bridge.findMethod {
                matcher {
                    usingNumbers(1, 2, 3, 0xffffffff.toInt())
                    name("updateDrawState")
                    declaredClass {
                        superClass {
                            superClass("android.text.style.URLSpan")
                        }
                    }
                }
            }.single().declaredClass

            recyclerListView = bridge.findMethod {
                matcher {
                    usingEqStrings("initializeScrollbars")
                }
            }.single().declaredClass!!

            val mentionsContainerView = bridge.findClass {
                matcher {
                    addField {
                        type {
                            superClass(recyclerListView.name)
                        }
                    }
                    addMethod {
                        name("<init>")
                        usingNumbers(0.22f)
                    }
                    superClass("android.widget.FrameLayout")
                }
            }.single()

            chatActivityMentionContainer =
                chatActivityCreateView.declaredClass!!.fields.single {
                    // r8 convert the field type to the real implementation type
                    it.typeName == mentionsContainerView.name || it.type.superClass?.name == mentionsContainerView.name
                }

            mentionsContainerViewListView = mentionsContainerView.fields.single {
                it.type.superClass?.name == recyclerListView.name
            }

            mentionsContainerViewAdapter = mentionsContainerView.findField {
                matcher {
                    type {
                        usingEqStrings(" !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~\n")
                    }
                }
            }.single()

            mentionsAdapterGetItem = mentionsContainerViewAdapter.type.findMethod {
                matcher {
                    paramTypes("int")
                    returnType("java.lang.Object")
                }
            }.maxBy { it.usingFields.size }
            println("MentionsAdapter.getItem ${mentionsAdapterGetItem.descriptor}")

            val f =
                locateMentionsAdapterPositionAndLength(mentionsContainerViewAdapter.type, bridge)
            mentionsAdapterResultStartPosition = f.first
            mentionsAdapterResultLength = f.second

            chatActivityChatEnterViewField =
                chatActivityCreateView.declaredClass!!.fields.single {
                    it.typeName == "org.telegram.ui.Components.ChatActivityEnterView"
                            || it.type.superClass?.name == "org.telegram.ui.Components.ChatActivityEnterView"
                }

            // we locate the OnItemLongClickListener's implementation in SelfStoryViewsPage
            recyclerListViewOnItemLongClickListener = bridge.findMethod {
                matcher {
                    addUsingField {
                        descriptor("Lorg/telegram/messenger/R\$string;->StoryHideFrom:I")
                    }
                }
            }.single().declaredClass!!.interfaces.single().methods.single()
        }

        creator.create("chatActivityCreateView") {
            if (chatActivityCreateView == null) {
                doDeobf()
            }
            require(chatActivityCreateView != null) { "chatActivityCreateView not found!" }
            chatActivityCreateView.toObfsInfo()
        }
        creator.create("urlSpanUserMention") {
            if (urlSpanUserMention == null) {
                doDeobf()
            }
            require(urlSpanUserMention != null) { "urlSpanUserMention not found!" }
            urlSpanUserMention!!.toObfsInfo()
        }
        creator.create("recyclerListView") {
            if (recyclerListView == null) {
                doDeobf()
            }
            require(recyclerListView != null) { "recyclerListView not found!" }
            recyclerListView.toObfsInfo()
        }
        creator.create("chatActivityMentionContainer") {
            if (chatActivityMentionContainer == null) {
                doDeobf()
            }
            require(chatActivityMentionContainer != null) { "chatActivityMentionContainer not found!" }
            chatActivityMentionContainer.toObfsInfo()
        }
        creator.create("mentionsContainerViewListView") {
            if (mentionsContainerViewListView == null) {
                doDeobf()
            }
            require(mentionsContainerViewListView != null) { "mentionsContainerViewListView not found!" }
            mentionsContainerViewListView.toObfsInfo()
        }
        creator.create("mentionsContainerViewAdapter") {
            if (mentionsContainerViewAdapter == null) {
                doDeobf()
            }
            require(mentionsContainerViewAdapter != null) { "mentionsContainerViewAdapter not found!" }
            mentionsContainerViewAdapter.toObfsInfo()
        }
        creator.create("mentionsAdapterResultStartPosition") {
            if (mentionsAdapterResultStartPosition == null) {
                doDeobf()
            }
            require(mentionsAdapterResultStartPosition != null) { "mentionsAdapterResultStartPosition not found!" }
            mentionsAdapterResultStartPosition.toObfsInfo()
        }
        creator.create("mentionsAdapterResultLength") {
            if (mentionsAdapterResultLength == null) {
                doDeobf()
            }
            require(mentionsAdapterResultLength != null) { "mentionsAdapterResultLength not found!" }
            mentionsAdapterResultLength.toObfsInfo()
        }
        creator.create("mentionsAdapterGetItem") {
            if (mentionsAdapterGetItem == null) {
                doDeobf()
            }
            require(mentionsAdapterGetItem != null) { "mentionsAdapterGetItem not found!" }
            mentionsAdapterGetItem.toObfsInfo()
        }
        creator.create("chatActivityChatEnterViewField") {
            if (chatActivityChatEnterViewField == null) {
                doDeobf()
            }
            require(chatActivityChatEnterViewField != null) { "chatActivityChatEnterViewField not found!" }
            chatActivityChatEnterViewField.toObfsInfo()
        }
        creator.create("recyclerListViewOnItemLongClickListener") {
            if (recyclerListViewOnItemLongClickListener == null) {
                doDeobf()
            }
            require(recyclerListViewOnItemLongClickListener != null) { "recyclerListViewOnItemLongClickListener not found!" }
            recyclerListViewOnItemLongClickListener.toObfsInfo()
        }

        return creator.obfsTable
    }

    private fun locateMentionsAdapterPositionAndLength(
        mentionsAdapterClass: ClassData,
        bridge: DexKitBridge
    ): Pair<FieldData, FieldData> {
        val searchUsernameOrHashtag = mentionsAdapterClass.findMethod {
            matcher {
                usingEqStrings("^[#$][\\p{L}_-]+$")
            }
        }.single()
        println("MentionsAdapter.searchUsernameOrHashtag: ${searchUsernameOrHashtag.descriptor}")

        val insns = searchUsernameOrHashtag.insns

        var pos = 0
        val poss = mutableListOf<Int>()
        while (pos < insns.size) {
            val w = getInsnWide(insns, pos)
            poss.add(pos)
            pos += w
        }

        // locate the resultStartPosition and resultLength
        //        if (usernameOnly) {
        //            result.append(text.substring(1));
        //            resultStartPosition = 0;
        //            resultLength = result.length();
        //            foundType = 0;

        //    009e8e20: 120a                    03bc: const/4             v10, 0
        //    009e8e22: 591a fcfa               03bd: iput                v10, v1, Lxe/u0;->W:I # field@fafc
        //    009e8e26: 6e10 9d16 0e00          03bf: invoke-virtual      {v14}, Ljava/lang/StringBuilder;->length()I # method@169d
        //    009e8e2c: 0a03                    03c2: move-result         v3
        //    009e8e2e: 5913 fdfa               03c3: iput                v3, v1, Lxe/u0;->X:I # field@fafd
        for (i in 0 until poss.size - 5) {
            // const/4 0
            if (insns[poss[i]].code != 0x0a12) continue
            val iput = decodeIInstanceOp(insns, poss[i + 1]) ?: continue
            if (iput.type != IInstanceOpType.IPut) continue
            val iv = decodeInvoke(insns, poss[i + 2]) ?: continue
            if (iv.type != InvokeType.Virtual) continue
            val ivm =
                bridge.getMethodDataByDexAndId(searchUsernameOrHashtag.dexId, iv.ref) ?: continue
            if (ivm.descriptor != "Ljava/lang/StringBuilder;->length()I") continue
            val iput2 = decodeIInstanceOp(insns, poss[i + 4]) ?: continue
            if (iput2.type != IInstanceOpType.IPut) continue

            val resultStartPositionField =
                bridge.getFieldDataByDexAndId(searchUsernameOrHashtag.dexId, iput.ref) ?: continue
            val resultLengthField =
                bridge.getFieldDataByDexAndId(searchUsernameOrHashtag.dexId, iput2.ref) ?: continue

            return Pair(resultStartPositionField, resultLengthField)
        }

        error("MentionsAdapter resultStartPosition & resultLength not found!")
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)

        val chatActivityCreateView = table["chatActivityCreateView"]!!
        val urlSpanUserMention = table["urlSpanUserMention"]!!
        val recyclerListView = table["recyclerListView"]!!
        val chatActivityMentionContainer = table["chatActivityMentionContainer"]!!
        val mentionsContainerViewListView = table["mentionsContainerViewListView"]!!
        val mentionsContainerViewAdapter = table["mentionsContainerViewAdapter"]!!
        val mentionsAdapterResultStartPosition = table["mentionsAdapterResultStartPosition"]!!
        val mentionsAdapterResultLength = table["mentionsAdapterResultLength"]!!
        val mentionsAdapterGetItem = table["mentionsAdapterGetItem"]!!
        val chatActivityChatEnterViewField = table["chatActivityChatEnterViewField"]!!
        val recyclerListViewOnItemLongClickListener =
            table["recyclerListViewOnItemLongClickListener"]!!

        val longClickListenerClass =
            findClass(recyclerListViewOnItemLongClickListener.className)
        val longClickMethod =
            longClickListenerClass.declaredMethods.single { it.name == recyclerListViewOnItemLongClickListener.memberName }
        val longClickListenerOnClickPositionIdx = longClickMethod
            .parameterTypes.indexOfFirst { it == Integer.TYPE }
        require(longClickListenerOnClickPositionIdx >= 0) { "no int param found in LongClickListener: $longClickMethod" }
        val tlUser = findClass("org.telegram.tgnet.TLRPC\$TL_user")
        val userObjectClass = findClass("org.telegram.messenger.UserObject")
        val classURLSpanUserMention = findClass(urlSpanUserMention.className)

        // I'm too lazy to write dexkit for them
        val chatActivityClass = findClass(chatActivityCreateView.className)
        val chatActivityEnterView = findClass("org.telegram.ui.Components.ChatActivityEnterView")
        val types =
            arrayOf(Integer.TYPE, Integer.TYPE, CharSequence::class.java, java.lang.Boolean.TYPE)
        val chatActivityEnterViewReplaceWithText = chatActivityEnterView.declaredMethods.single {
            it.parameterTypes.contentEquals(types)
        }.also { it.isAccessible = true }

        val recyclerListViewClass = findClass(recyclerListView.className)
        // its name is not obfuscated
        val recyclerListViewSetOnItemLongClickMethod =
            recyclerListViewClass.declaredMethods.single {
                it.parameterCount == 1 && it.parameterTypes[0] == longClickListenerClass
            }.also { it.isAccessible = true }

        chatActivityClass.hookAllAfter(
            chatActivityCreateView.memberName,
            cond = ::isEnabled
        ) { param ->
            val obj = Object()
            val thiz = param.thisObject
            val mentionContainer = thiz.getObj(chatActivityMentionContainer.memberName)
            val listView =
                mentionContainer.getObj(mentionsContainerViewListView.memberName) // RecyclerListView
            val proxy = Proxy.newProxyInstance(
                classLoader, arrayOf(longClickListenerClass)
            ) { _, method, args ->
                if (method.name == recyclerListViewOnItemLongClickListener.memberName) {
                    runCatching {
                        var position = args[longClickListenerOnClickPositionIdx] as Int
                        if (position == 0) return@newProxyInstance false
                        position--
                        val adapter =
                            mentionContainer.getObj(mentionsContainerViewAdapter.memberName)
                        val item = adapter.call(mentionsAdapterGetItem.memberName, position)
                        if (!tlUser.isInstance(item)) return@newProxyInstance false
                        val start =
                            adapter.getObjAs<Int>(mentionsAdapterResultStartPosition.memberName)
                        val len = adapter.getObjAs<Int>(mentionsAdapterResultLength.memberName)
                        val name = userObjectClass.callS(
                            "getFirstName",
                            item,
                            false
                        )
                        val spannable = SpannableString("$name ")
                        val span = classURLSpanUserMention.newInst(
                            item.getObj("id").toString(),
                            3, null // r8 only keeps the 3-args init
                        )
                        spannable.setSpan(
                            span,
                            0,
                            spannable.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        val chatActivityEnterView =
                            thiz.getObj(chatActivityChatEnterViewField.memberName)
                        chatActivityEnterViewReplaceWithText.invoke(
                            chatActivityEnterView,
                            start,
                            len,
                            spannable,
                            false
                        )
                        return@newProxyInstance true
                    }.onFailure { logE("onItemLongClicked: error", it) }
                    return@newProxyInstance false
                }
                return@newProxyInstance method.invoke(obj, args)
            }
            recyclerListViewSetOnItemLongClickMethod.invoke(listView, proxy)
        }
    }
}
