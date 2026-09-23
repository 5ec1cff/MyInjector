package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.IInstanceOpType
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.SInstanceOpType
import io.github.a13e300.myinjector.arch.decodeIInstanceOp
import io.github.a13e300.myinjector.arch.decodeSInstanceOp
import io.github.a13e300.myinjector.arch.getInsnWide
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData

class AlwaysShowStorySaveIcon : MyDynHook("alwaysShowStorySaveIcon") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.alwaysShowStorySaveIcon

    private fun deobf(creator: ObfsTableCreator): ObfsTable {
        var peerStoriesViewUpdatePosition: MethodData? = null
        var peerStoriesAllowShare: FieldData? = null
        var peerStoriesAllowShareLink: FieldData? = null
        var called = false
        fun doDeobf() {
            if (called) return
            called = true
            val bridge = creator.bridge
            peerStoriesViewUpdatePosition = bridge.findMethod {
                matcher {
                    usingEqStrings("_pframe", ".mp4")
                }
            }.single {
                // exclude StoriesUtilities
                !it.usingStrings.contains("_")
            }

            val peerStoriesViewCustomPopupMenuOnCreate = bridge.findMethod {
                matcher {
                    addUsingField {
                        descriptor("Lorg/telegram/messenger/R\$drawable;->msg_link2:I")
                    }
                    addUsingField {
                        declaredClass(peerStoriesViewUpdatePosition.className)
                        type("boolean")
                    }
                }
            }.single()

            val insns = peerStoriesViewCustomPopupMenuOnCreate.insns

            var pos = 0
            val poss = mutableListOf<Int>()
            while (pos < insns.size) {
                val w = getInsnWide(insns, pos)
                poss.add(pos)
                pos += w
            }
            // allowShare:
            // if (!unsupported && allowShare && !currentStory.isLive) {
            //    00124cd2: 55b0 e625               029b: iget-boolean        v0, v11, Lsg/j3;->b3:Z # field@25e6
            //    00124cd6: 3900 3200               029d: if-nez              v0, :cond_02cf
            //                              .line 84
            //    00124cda: 55b0 be25               029f: iget-boolean        v0, v11, Lsg/j3;->R2:Z # field@25be
            //    00124cde: 3800 2e00               02a1: if-eqz              v0, :cond_02cf
            //                              .line 85
            //    00124ce2: 55c0 5e25               02a3: iget-boolean        v0, v12, Lsg/i3;->f:Z # field@255e
            //    00124ce6: 3900 2a00               02a5: if-nez              v0, :cond_02cf
            //                              .line 86
            //    00124cea: 52b0 7e25               02a7: iget                v0, v11, Lsg/j3;->B2:I # field@257e
            //                              .line 87
            //    00124cee: 7110 2416 0000          02a9: invoke-static       {v0}, Lorg/telegram/messenger/UserConfig;->getInstance(I)Lorg/telegram/messenger/UserConfig; # method@1624
            //    00124cf4: 0c00                    02ac: move-result-object  v0
            //    00124cf6: 6e10 2716 0000          02ad: invoke-virtual      {v0}, Lorg/telegram/messenger/UserConfig;->isPremium()Z # method@1627
            //    00124cfc: 0a00                    02b0: move-result         v0
            //    00124cfe: 3800 2100               02b1: if-eqz              v0, :cond_02d2
            //                              .line 88
            //    00124d02: 6003 ee09               02b3: sget                v3, Lorg/telegram/messenger/R$drawable;->msg_gallery:I # field@09ee
            //    00124d06: 6000 3a0d               02b5: sget                v0, Lorg/telegram/messenger/R$string;->SaveToGallery:I # field@0d3a


            val searchWindow = 20
            for (i in 0 until poss.size - searchWindow) {
                val iget1 = decodeIInstanceOp(insns, poss[i]) ?: continue
                if (iget1.type != IInstanceOpType.IGetBoolean) continue
                val if1op = insns[poss[i + 1]].code.and(0xff)
                // if-nez
                if (if1op != 0x39) continue
                val iget2 = decodeIInstanceOp(insns, poss[i + 2]) ?: continue
                if (iget2.type != IInstanceOpType.IGetBoolean) continue
                val if2op = insns[poss[i + 3]].code.and(0xff)
                // if-eqz
                if (if2op != 0x38) continue
                val iget3 = decodeIInstanceOp(insns, poss[i + 4]) ?: continue
                if (iget3.type != IInstanceOpType.IGetBoolean) continue
                val if3op = insns[poss[i + 5]].code.and(0xff)
                // if-nez
                if (if3op != 0x39) continue
                var hasDrawableMsgGallery = false
                for (j in i + 6 until i + searchWindow - 6) {
                    val sget = decodeSInstanceOp(insns, poss[j]) ?: continue
                    if (sget.type != SInstanceOpType.SGet) continue
                    val field = bridge.getFieldDataByDexAndId(
                        peerStoriesViewCustomPopupMenuOnCreate.dexId,
                        sget.ref
                    ) ?: continue
                    if (field.descriptor == "Lorg/telegram/messenger/R\$drawable;->msg_gallery:I") {
                        hasDrawableMsgGallery = true
                        break
                    }
                }
                if (!hasDrawableMsgGallery) {
                    continue
                }
                val allowShareField = bridge.getFieldDataByDexAndId(
                    peerStoriesViewCustomPopupMenuOnCreate.dexId,
                    iget2.ref
                ) ?: continue
                if (allowShareField.declaredClassName != peerStoriesViewUpdatePosition.className) {
                    continue
                }
                peerStoriesAllowShare = allowShareField
                break
            }

            // allowShareLink: if (allowShareLink) {
            //                            ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_link2
            //    00124e00: 55b0 c625               0332: iget-boolean        v0, v11, Lsg/j3;->T2:Z # field@25c6
            //    00124e04: 3800 1c00               0334: if-eqz              v0, :cond_0350
            //                             .line 107
            //    00124e08: 6003 fa09               0336: sget                v3, Lorg/telegram/messenger/R$drawable;->msg_link2:I # field@09fa
            for (i in 0 until poss.size - 3) {
                val iget = decodeIInstanceOp(insns, poss[i]) ?: continue
                if (iget.type != IInstanceOpType.IGetBoolean) continue
                val igetField = bridge.getFieldDataByDexAndId(
                    peerStoriesViewCustomPopupMenuOnCreate.dexId,
                    iget.ref
                ) ?: continue
                if (igetField.declaredClassName != peerStoriesViewUpdatePosition.className) continue
                val ifop = insns[poss[i + 1]].code.and(0xff)
                // if-eqz
                if (ifop != 0x38) continue
                val sget = decodeSInstanceOp(insns, poss[i + 2]) ?: continue
                if (sget.type != SInstanceOpType.SGet) continue
                val sgetField = bridge.getFieldDataByDexAndId(
                    peerStoriesViewCustomPopupMenuOnCreate.dexId,
                    sget.ref
                ) ?: continue
                if (sgetField.descriptor != "Lorg/telegram/messenger/R\$drawable;->msg_link2:I") continue
                peerStoriesAllowShareLink = igetField
                break
            }
        }

        creator.create("PeerStoriesViewUpdatePosition") {
            if (peerStoriesViewUpdatePosition == null) {
                doDeobf()
            }
            require(peerStoriesViewUpdatePosition != null) { "PeerStoriesViewUpdatePosition not found!" }
            peerStoriesViewUpdatePosition.toObfsInfo()
        }

        creator.create("PeerStoriesAllowShare") {
            if (peerStoriesAllowShare == null) {
                doDeobf()
            }
            require(peerStoriesAllowShare != null) { "PeerStoriesAllowShare not found!" }
            peerStoriesAllowShare.toObfsInfo()
        }

        creator.create("PeerStoriesAllowShareLink") {
            if (peerStoriesAllowShareLink == null) {
                doDeobf()
            }
            require(peerStoriesAllowShareLink != null) { "PeerStoriesAllowShareLink not found!" }
            peerStoriesAllowShareLink.toObfsInfo()
        }

        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)

        val PeerStoriesViewUpdatePosition = table["PeerStoriesViewUpdatePosition"]!!
        val PeerStoriesAllowShare = table["PeerStoriesAllowShare"]!!
        val PeerStoriesAllowShareLink = table["PeerStoriesAllowShareLink"]!!

        findClass(PeerStoriesViewUpdatePosition.className).hookAllAfter(
            PeerStoriesViewUpdatePosition.memberName,
            cond = ::isEnabled
        ) { param ->
            param.thisObject.setObj(PeerStoriesAllowShare.memberName, true)
            // param.thisObject.setObj("allowRepost", true)
            param.thisObject.setObj(PeerStoriesAllowShareLink.memberName, true)
        }
    }
}
