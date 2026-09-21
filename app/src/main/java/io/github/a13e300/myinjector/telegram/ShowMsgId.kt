package io.github.a13e300.myinjector.telegram

import android.text.TextPaint
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.IInstanceOpType
import io.github.a13e300.myinjector.arch.InvokeType
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.decodeConstHigh16
import io.github.a13e300.myinjector.arch.decodeIInstanceOp
import io.github.a13e300.myinjector.arch.decodeInvoke
import io.github.a13e300.myinjector.arch.getInsnWide
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logE
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.UsingType
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier
import kotlin.math.ceil
import kotlin.math.min

class ShowMsgId : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.showMsgId

    private fun deobf(creator: ObfsTableCreator): ObfsTable {
        var chatMessageCellMeasureTime: MethodData? = null
        var chatMessageCellTimeWidth: FieldData? = null
        var chatMessageCellTimeTextWidth: FieldData? = null
        var chatMessageCellCurrentTimeString: FieldData? = null
        var themeChatTimePaint: FieldData? = null
        var chatMessageCellIsMegaGroup: FieldData? = null

        var called = false

        fun doDeobf() {
            if (called) return
            called = true
            val bridge = creator.bridge
            chatMessageCellMeasureTime = bridge.findMethod {
                matcher {
                    usingEqStrings("MessageScheduledRepeatMonthlyMany")
                }
            }.single()

            chatMessageCellCurrentTimeString = bridge.findField {
                matcher {
                    addWriteMethod {
                        descriptor(chatMessageCellMeasureTime.descriptor)
                    }
                    type("java.lang.CharSequence")
                }
            }.single()

            themeChatTimePaint = bridge.findField {
                matcher {
                    addReadMethod {
                        descriptor(chatMessageCellMeasureTime.descriptor)
                    }
                    type("android.text.TextPaint")
                    modifiers(Modifier.STATIC)
                }
            }.single {
                // both chat_msgInViewsDrawable and chat_timePaint are referenced from measureTime, but only chat_timePaint has 2 writers
                it.writers.size == 2
            }

            val p = locateTimeWidth(bridge, chatMessageCellMeasureTime)
            chatMessageCellTimeWidth = p.first
            chatMessageCellTimeTextWidth = p.second

            // any method that have `messageCell.isMegagroup = ChatObject.isChannel(chat) && chat.megagroup`
            val setMegaGroupMethods = bridge.findMethod {
                matcher {
                    addUsingField {
                        descriptor("Lorg/telegram/tgnet/TLRPC\$Chat;->megagroup:Z")
                        usingType(UsingType.Read)
                    }
                    addUsingField {
                        declaredClass(chatMessageCellMeasureTime.declaredClassName)
                        type("boolean")
                        usingType(UsingType.Write)
                    }
                }
            }
            for (m in setMegaGroupMethods) {
                // may be many methods, just try one by one
                chatMessageCellIsMegaGroup =
                    locateMegaGroup(bridge, m, chatMessageCellMeasureTime.className)
                if (chatMessageCellIsMegaGroup != null) break
            }
            require(chatMessageCellIsMegaGroup != null) { "ChatMessageCell.isMegagroup not found!" }
        }


        creator.create("chatMessageCellMeasureTime") {
            if (chatMessageCellMeasureTime == null) {
                doDeobf()
            }
            require(chatMessageCellMeasureTime != null) { "chatMessageCellMeasureTime not found" }
            chatMessageCellMeasureTime.toObfsInfo()
        }
        creator.create("chatMessageCellTimeWidth") {
            if (chatMessageCellTimeWidth == null) {
                doDeobf()
            }
            require(chatMessageCellTimeWidth != null) { "chatMessageCellTimeWidth not found" }
            chatMessageCellTimeWidth.toObfsInfo()
        }
        creator.create("chatMessageCellTimeTextWidth") {
            if (chatMessageCellTimeTextWidth == null) {
                doDeobf()
            }
            require(chatMessageCellTimeTextWidth != null) { "chatMessageCellTimeTextWidth not found" }
            chatMessageCellTimeTextWidth.toObfsInfo()
        }
        creator.create("chatMessageCellCurrentTimeString") {
            if (chatMessageCellCurrentTimeString == null) {
                doDeobf()
            }
            require(chatMessageCellCurrentTimeString != null) { "chatMessageCellCurrentTimeString not found" }
            chatMessageCellCurrentTimeString.toObfsInfo()
        }
        creator.create("themeChatTimePaint") {
            if (themeChatTimePaint == null) {
                doDeobf()
            }
            require(themeChatTimePaint != null) { "themeChatTimePaint not found" }
            themeChatTimePaint.toObfsInfo()
        }
        creator.create("chatMessageCellIsMegaGroup") {
            if (chatMessageCellIsMegaGroup == null) {
                doDeobf()
            }
            require(chatMessageCellIsMegaGroup != null) { "chatMessageCellIsMegaGroup not found" }
            chatMessageCellIsMegaGroup!!.toObfsInfo()
        }

        return creator.obfsTable
    }

    private fun locateMegaGroup(
        bridge: DexKitBridge,
        methodData: MethodData,
        chatMessageCellCls: String
    ): FieldData? {
        val insns = methodData.insns

        var pos = 0
        val poss = mutableListOf<Int>()
        while (pos < insns.size) {
            val w = getInsnWide(insns, pos)
            poss.add(pos)
            pos += w
        }

        var count = 0
        var firstGetMegaGroup = 0
        for (i in 0 until poss.size) {
            val iget = decodeIInstanceOp(insns, poss[i]) ?: continue
            if (iget.type != IInstanceOpType.IGetBoolean) continue
            val igetf = bridge.getFieldDataByDexAndId(methodData.dexId, iget.ref) ?: continue
            if (igetf.descriptor != "Lorg/telegram/tgnet/TLRPC\$Chat;->megagroup:Z") continue
            firstGetMegaGroup = i
            count += 1
        }
        println("count: $count")

        //    0049e4f2: 5c59 4e76               0163: iput-boolean        v9, v5, Lorg/telegram/ui/Cells/v1;->M7:Z # field@764e
        //                             .line 358
        //    0049e4f6: 7110 3322 0800          0165: invoke-static       {v8}, Lorg/telegram/messenger/ChatObject;->isChannel(Lorg/telegram/tgnet/TLRPC$Chat;)Z # method@2233
        //                             .line 361
        //    0049e4fc: 0a09                    0168: move-result         v9
        //                             .line 362
        //    0049e4fe: 3809 0900               0169: if-eqz              v9, :cond_0172
        //                             .line 364
        //    0049e502: 5588 1b3a               016b: iget-boolean        v8, v8, Lorg/telegram/tgnet/TLRPC$Chat;->megagroup:Z # field@3a1b
        //                             .line 366
        //    0049e506: 3808 0500               016d: if-eqz              v8, :cond_0172
        //                             .line 368
        //    0049e50a: 0208 1000               016f: move/from16         v8, v16
        //                             .line 370
        //    0049e50e: 2802                    0171: goto                :goto_0173
        //                             .line 371
        //                            cond_0172: # 2 refs
        //    0049e510: 0168                    0172: move                v8, v6
        //                             .line 372
        //                            goto_0173:
        //    0049e512: 5c58 9976               0173: iput-boolean        v8, v5, Lorg/telegram/ui/Cells/v1;->R7:Z # field@7699
        // we choose the simplest one
        if (count == 1) {
            val searchWindow = 10
            for (i in firstGetMegaGroup until min(firstGetMegaGroup + searchWindow, poss.size)) {
                val iput = decodeIInstanceOp(insns, poss[i]) ?: continue
                if (iput.type != IInstanceOpType.IPutBoolean) continue
                val iputf = bridge.getFieldDataByDexAndId(methodData.dexId, iput.ref) ?: continue
                if (iputf.declaredClassName != chatMessageCellCls) {
                    logE("??? not ChatMessageCell field ${iputf.descriptor}")
                } else {
                    // println("ChatMessageCell.isMegagroup ${iputf.descriptor}")
                    return iputf
                }
                break
            }
        }

        return null
    }

    // timeWidth, timeTextWidth
    private fun locateTimeWidth(
        bridge: DexKitBridge,
        chatMessageCellMeasureTime: MethodData
    ): Pair<FieldData, FieldData> {
        val onLayout = chatMessageCellMeasureTime.declaredClass!!.findMethod {
            matcher {
                name("onLayout")
            }
        }.single()
        val insns = onLayout.insns

        var pos = 0
        val poss = mutableListOf<Int>()
        while (pos < insns.size) {
            val w = getInsnWide(insns, pos)
            poss.add(pos)
            pos += w
        }
        // timeWidth: onLayout
        // timeX = layoutWidth - timeWidth - dp(42.0f);
        //    004143ec: 5202 3176               009a: iget                v2, v0, Lorg/telegram/ui/Cells/v1;->K8:I # field@7631
        //    004143f0: 151a 2842               009c: const/high16        v26, 0x42280000 [42.0f]
        //    004143f4: 5203 f577               009e: iget                v3, v0, Lorg/telegram/ui/Cells/v1;->ob:I # field@77f5 [timeWidth]
        //    004143f8: b132                    00a0: sub-int/2addr       v2, v3
        //    004143fa: 7701 5e20 1a00          00a1: invoke-static/range {v26 .. v26}, Lorg/telegram/messenger/AndroidUtilities;->dp(F)I # method@205e
        //    00414400: 0a03                    00a4: move-result         v3
        //    00414402: b132                    00a5: sub-int/2addr       v2, v3
        //    00414404: 5902 1378               00a6: iput                v2, v0, Lorg/telegram/ui/Cells/v1;->qb:I # field@7813

        val searchWindow = 8
        var timeWidth: FieldData? = null
        for (i in 0 until poss.size - searchWindow) {
            val iget1 = decodeIInstanceOp(insns, poss[i]) ?: continue
            if (iget1.type != IInstanceOpType.IGet) continue
            val iget1Field = bridge.getFieldDataByDexAndId(onLayout.dexId, iget1.ref) ?: continue
            if (iget1Field.declaredClassName != onLayout.className) continue
            var timeWidthField: FieldData? = null
            var hasConstHigh16_42f = false
            for (j in i + 1 until i + searchWindow) {
                val iget2 = decodeIInstanceOp(insns, poss[j])
                if (iget2 != null && iget2.type == IInstanceOpType.IGet) {
                    val iget2Field = bridge.getFieldDataByDexAndId(onLayout.dexId, iget2.ref)
                    if (iget2Field != null) {
                        // iget more than 2 ?
                        if (timeWidthField != null) {
                            timeWidthField = null
                            break
                        } else if (iget2Field.declaredClassName == onLayout.className) {
                            timeWidthField = iget2Field
                        } else {
                            break
                        }
                    }
                }

                val constHigh = decodeConstHigh16(insns, poss[j])
                if (constHigh != null) {
                    if (java.lang.Float.intBitsToFloat(constHigh.value) == 42f) {
                        hasConstHigh16_42f = true
                    }
                }

                if (timeWidthField != null && hasConstHigh16_42f) {
                    break
                }
            }

            if (timeWidthField != null && hasConstHigh16_42f) {
                // println("found timeWidth: ${timeWidthField.descriptor}")
                timeWidth = timeWidthField
                break
            }
        }

        // timeTextWidth: onLayout
        //            if (timeTextWidth < 0) {
        //                timeTextWidth = dp(10);
        //            }
        //    00414300: 150e 2041               0024: const/high16        v14, 0x41200000 (10f)
        //    ......
        //    00414366: 5202 0478               0057: iget                v2, v0, Lorg/telegram/ui/Cells/v1;->pb:I # field@7804
        //    0041436a: 3b02 0800               0059: if-gez              v2, :cond_0061
        //                               .line 7
        //    0041436e: 7110 5e20 0e00          005b: invoke-static       {v14}, Lorg/telegram/messenger/AndroidUtilities;->dp(F)I # method@205e
        //    00414374: 0a02                    005e: move-result         v2
        //    00414376: 5902 0478               005f: iput                v2, v0, Lorg/telegram/ui/Cells/v1;->pb:I # field@7804
        val searchTimeTextWidthWindow = 5
        var timeTextWidth: FieldData? = null
        for (i in 0 until poss.size - searchTimeTextWidthWindow) {
            val iget = decodeIInstanceOp(insns, poss[i]) ?: continue
            if (iget.type != IInstanceOpType.IGet) continue
            val igetField = bridge.getFieldDataByDexAndId(onLayout.dexId, iget.ref) ?: continue
            if (igetField.declaredClassName != onLayout.className) continue
            val ifgez = insns[poss[i + 1]].code.and(0xff)
            // if-gez
            if (ifgez != 0x3b) continue
            val invoke = decodeInvoke(insns, poss[i + 2]) ?: continue
            if (invoke.type != InvokeType.Static) continue
            val invokeMethod =
                bridge.getMethodDataByDexAndId(onLayout.dexId, invoke.ref) ?: continue
            if (invokeMethod.descriptor != "Lorg/telegram/messenger/AndroidUtilities;->dp(F)I") continue
            val iput = decodeIInstanceOp(insns, poss[i + 4]) ?: continue
            if (iput.type != IInstanceOpType.IPut) continue
            if (iput.ref != iget.ref) continue
            // println("found timeTextWidth ${igetField.descriptor}")
            timeTextWidth = igetField
            break
        }

        require(timeWidth != null) { "timeWidth not found!" }
        require(timeTextWidth != null) { "timeTextWidth not found!" }

        return Pair(timeWidth, timeTextWidth)
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val chatMessageCellMeasureTime = table["chatMessageCellMeasureTime"]!!
        val chatMessageCellTimeWidth = table["chatMessageCellTimeWidth"]!!
        val chatMessageCellTimeTextWidth = table["chatMessageCellTimeTextWidth"]!!
        val chatMessageCellCurrentTimeString = table["chatMessageCellCurrentTimeString"]!!
        val themeChatTimePaint = table["themeChatTimePaint"]!!
        val chatMessageCellIsMegaGroup = table["chatMessageCellIsMegaGroup"]!!

        val chatTimePaint =
            findClass(themeChatTimePaint.className).getDeclaredField(themeChatTimePaint.memberName)
                .also { it.isAccessible = true }
        val timeWidth =
            findClass(chatMessageCellTimeWidth.className).getDeclaredField(chatMessageCellTimeWidth.memberName)
                .also { it.isAccessible = true }
        val timeTextWidth = findClass(chatMessageCellTimeTextWidth.className).getDeclaredField(
            chatMessageCellTimeTextWidth.memberName
        )
            .also { it.isAccessible = true }

        findClass(chatMessageCellMeasureTime.className).hookAllAfter(
            chatMessageCellMeasureTime.memberName,
            cond = ::isEnabled
        ) { param ->
            var time =
                param.thisObject.getObjAs<CharSequence>(chatMessageCellCurrentTimeString.memberName)
            val messageObject = param.args[0]
            val owner = messageObject.getObj("messageOwner")
            val id = owner.getObjAs<Int>("id")
            val real = owner.getObjAs<Int>("realId")
            var delta = "${(if (real == 0) id else real)} "
            val isMegaGroup =
                param.thisObject.getObjAs<Boolean>(chatMessageCellIsMegaGroup.memberName)
            val fromChatId = messageObject.call("getFromChatId") as Long
            val dialogId = messageObject.call("getDialogId") as Long
            if (isMegaGroup && fromChatId == dialogId) {
                val postAuthor = owner.getObjAsN<String?>("post_author")
                if (postAuthor != null) delta += postAuthor.replace("\n", "") + " "
            }
            time = delta + time
            param.thisObject.setObj(chatMessageCellCurrentTimeString.memberName, time)
            val paint = chatTimePaint.get(null) as TextPaint
            val deltaWidth = ceil(paint.measureText(delta)).toInt()
            timeTextWidth.setInt(
                param.thisObject,
                deltaWidth + timeTextWidth.getInt(param.thisObject)
            )
            timeWidth.setInt(param.thisObject, deltaWidth + timeWidth.getInt(param.thisObject))
        }
    }
}