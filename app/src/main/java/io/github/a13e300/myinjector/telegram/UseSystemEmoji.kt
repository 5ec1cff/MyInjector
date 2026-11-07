package io.github.a13e300.myinjector.telegram

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.wrap.DexMethod
import org.luckypray.dexkit.wrap.DexField
import java.io.File

class UseSystemEmoji : MyDynHook("useSystemEmoji") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.useSystemEmoji
    private val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    // https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/Emoji.java#L292
    // https://github.com/PreviousAlone/Nnngram/blob/main/TMessagesProj/src/main/java/org/telegram/messenger/Emoji.java#L297
    // https://github.com/5ec1cff/TMoe/blob/6030ff04ad0ab268e96cb461b662b3c698d0a582/app/src/main/java/cc/ioctl/tmoe/hook/func/UseSystemEmoji.kt
    override fun onHook() {
        val creator = TelegramHandler.creator
        val found by lazy { findSystemEmoji(creator.bridge) }
        fun descriptor(key: String) = creator.create("SystemEmoji.$key") { bridge ->
            val value = found.getValue(key)
            if ('(' in value) bridge.getMethodData(value)!!.toObfsInfo()
            else bridge.getFieldData(value)!!.toObfsInfo()
        }.descriptor
        fun method(key: String) = DexMethod(descriptor(key)).getMethodInstance(classLoader).apply { isAccessible = true }
        fun field(key: String) = DexField(descriptor(key)).getFieldInstance(classLoader).apply { isAccessible = true }
        val fixEmojiM = method("fix")
        val bigSizeF = field("size")
        val fullSizeF = field("fullSize")
        val drawableInfoF = field("info")
        val pageF = field("page")
        val emojiIndexF = field("index")
        val data1 = field("data").get(null) as Array<Array<String>>


        method("draw").hookBefore(cond = ::isEnabled) {
            val canvas = it.args[0] as Canvas
            val emojiDrawable = it.thisObject as Drawable

            val fullSize = fullSizeF.get(it.thisObject) as Boolean


            val b: Rect
            if (fullSize) {
                val bounds = emojiDrawable.bounds
                val half = bigSizeF.getInt(null) / 2
                b = Rect(bounds.centerX() - half, bounds.centerY() - half, bounds.centerX() + half, bounds.centerY() + half)
            } else {
                b = emojiDrawable.bounds
            }

            val textPaint = textPaint


            val info = drawableInfoF.get(emojiDrawable)
            val page = pageF.get(info) as Byte
            val emojiIndex = (emojiIndexF.get(info) as Number).toInt()


            val data: String =
                data1[page.toInt()][emojiIndex] //EmojiData.data[info.page][info.emojiIndex]

            val emoji1 = fixEmojiM.invoke(null, data) as String
            textPaint.textSize = b.height() * 0.8f
            textPaint.typeface = getSystemEmojiTypeface()

            canvas.drawText(
                emoji1,
                0,
                emoji1.length,
                b.left + 0f,
                b.bottom - b.height() * 0.225f,
                textPaint
            )
            it.result = canvas
        }
    }

    var loadSystemEmojiFailed = false
    private var systemEmojiTypeface: Typeface? = null
    private fun getSystemEmojiTypeface(): Typeface? {
        if (!loadSystemEmojiFailed && systemEmojiTypeface == null) {
            val font: File? = getSystemEmojiFontPath()
            if (font != null) {
                systemEmojiTypeface = Typeface.createFromFile(font)
            }
            if (systemEmojiTypeface == null) {
                loadSystemEmojiFailed = true
            }
        }
        return systemEmojiTypeface
    }

    private fun getSystemEmojiFontPath(): File? {
        val fileAOSP = File("/system/fonts/NotoColorEmoji.ttf")
        if (fileAOSP.exists()) {
            return fileAOSP
        }
        return null
    }
}