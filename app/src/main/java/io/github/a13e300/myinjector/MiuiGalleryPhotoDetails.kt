package io.github.a13e300.myinjector

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAfter
import java.io.File
import java.io.InputStream
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.ln

/** Details of the launch photo; no gallery resources, overlay permission or root are used. */
class MiuiGalleryPhotoDetails : DynHook() {
    override fun isFeatureEnabled() = MiuiGalleryHandler.settings.photoDetails

    override fun onHook() {
        findClass("com.miui.gallery.activity.InternalPhotoPageActivity")
            .hookAfter("onCreate", Bundle::class.java) { param ->
                if (!isEnabled() || param.throwable != null) return@hookAfter
                val activity = param.thisObject as? Activity ?: return@hookAfter
                runCatching { addButton(activity) }
                    .onFailure { logW("Gallery details: cannot add button", it) }
            }
    }

    private fun addButton(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || readPhoto(activity) == null) return
        val decor = activity.window.decorView as? FrameLayout ?: return
        if (decor.findViewWithTag<View>(BUTTON_TAG) != null) return
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val button = ImageButton(activity).apply {
            tag = BUTTON_TAG
            contentDescription = "显示启动照片的详细信息"
            setImageDrawable(Entry.moduleRes.getDrawable(R.drawable.ic_gallery_details_info, activity.theme))
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF),
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xB3000000.toInt()) },
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
            backgroundTintList = null
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        val lp = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.BOTTOM or Gravity.LEFT).apply {
            leftMargin = dp(16)
            bottomMargin = dp(96)
        }
        decor.addView(button, lp)
        @Suppress("DEPRECATION")
        fun updateInsets(insets: WindowInsets) {
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).bottom
            } else insets.systemWindowInsetBottom
            val left = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).left
            } else insets.systemWindowInsetLeft
            (button.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.leftMargin = maxOf(dp(16), left)
                it.bottomMargin = bottom + dp(96)
                button.layoutParams = it
            }
        }
        button.setOnApplyWindowInsetsListener { _, insets -> updateInsets(insets); insets }
        decor.rootWindowInsets?.let { updateInsets(it) }
        button.requestApplyInsets()
        button.setOnClickListener {
            if (!isEnabled()) { button.visibility = View.GONE; return@setOnClickListener }
            val photo = readPhoto(activity)
            if (photo == null) { Toast.makeText(activity, "照片信息不可用", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            button.isEnabled = false
            val activityRef = WeakReference(activity)
            val buttonRef = WeakReference(button)
            val context = activity.applicationContext
            runCatching { executor.execute {
                val details = runCatching { readDetails(context, photo) }.getOrElse {
                    logW("Gallery details: metadata unavailable", it)
                    Details("无法读取完整信息（文件不存在、无权限或格式不支持）。\n路径：${photo.path ?: UNKNOWN}", null, null, photo.mime)
                }
                mainHandler.post {
                    val owner = activityRef.get() ?: return@post
                    if (owner.isFinishing || owner.isDestroyed) return@post
                    buttonRef.get()?.isEnabled = true
                    if (!isEnabled()) { buttonRef.get()?.visibility = View.GONE; return@post }
                    runCatching { showDetails(owner, details, buttonRef) }.onFailure { logW("Gallery details: cannot show dialog", it) }
                }
            } }.onFailure { button.isEnabled = true; logW("Gallery details: cannot schedule metadata read", it) }
        }
    }


    @Suppress("DEPRECATION")
    private fun readPhoto(activity: Activity): Photo? = runCatching {
        val extras = activity.intent?.extras ?: return null
        extras.classLoader = activity.classLoader
        val data = extras.get("photo_transition_data") ?: return null
        fun field(name: String) = runCatching { data.getObj(name) }.getOrNull()
        Photo(
            (field("path") as? String)?.takeIf { it.isNotBlank() },
            (field("mimeType") as? String)?.takeIf { it.isNotBlank() },
            (field("location") as? String)?.takeIf { it.isNotBlank() },
            (field("fileLength") as? Number)?.toLong(),
            (field("createTime") as? Number)?.toLong()
        )
    }.getOrElse {
        logW("Gallery details: missing transition data", it)
        null
    }

    private fun readDetails(context: Context, photo: Photo): Details {
        val values = linkedMapOf<String, String>()
        val notes = linkedSetOf<String>()
        val path = photo.path
        val uri = path?.let { Uri.parse(it) }
        val file = when {
            path == null -> null
            uri?.scheme == "file" -> uri.path?.let(::File)
            uri?.scheme == null -> File(path)
            else -> null
        }
        var name: String? = file?.name
        var mime = photo.mime
        var size = photo.length?.takeIf { it > 0 }
        var created = photo.created?.takeIf { it > 0 }
        var modified: Long? = null
        var mediaId: Long? = null
        var editUri: Uri? = uri?.takeIf { it.scheme == "content" }
        var width = 0
        var height = 0
        runCatching {
            if (file?.isFile == true) {
                size = file.length().takeIf { it > 0 } ?: size
                modified = file.lastModified().takeIf { it > 0 }
            }
        }.onFailure { notes.add("文件属性不可读") }

        // Query only metadata; every returned cursor is closed even on an empty result/error.
        runCatching {
            val contentUri = uri?.takeIf { it.scheme == "content" }
            val queryUri = contentUri ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            if (contentUri != null || file != null) {
                context.contentResolver.query(
                    queryUri,
                    arrayOf("_id", "_display_name", "mime_type", "_size", "datetaken", "date_modified", "width", "height"),
                    if (contentUri == null) "_data=?" else null,
                    if (contentUri == null) arrayOf(file!!.absolutePath) else null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        fun text(column: String): String? {
                            val index = cursor.getColumnIndex(column)
                            return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                        }
                        mediaId = text("_id")?.toLongOrNull()
                        if (contentUri == null) {
                            editUri = mediaId?.let { ContentUris.withAppendedId(queryUri, it) }
                        }
                        name = text("_display_name") ?: name
                        mime = mime ?: text("mime_type")
                        size = size ?: text("_size")?.toLongOrNull()?.takeIf { it > 0 }
                        created = created ?: text("datetaken")?.toLongOrNull()?.takeIf { it > 0 }
                        modified = modified ?: text("date_modified")?.toLongOrNull()
                            ?.takeIf { it > 0 }?.times(1000)
                        width = text("width")?.toIntOrNull() ?: 0
                        height = text("height")?.toIntOrNull() ?: 0
                    }
                }
            }
        }.onFailure { notes.add("MediaStore 信息不可用（可能无读取权限）") }

        fun open(): InputStream? = when {
            file != null -> file.inputStream()
            uri?.scheme == "content" -> context.contentResolver.openInputStream(uri)
            else -> null
        }
        val exif = runCatching {
            open()?.use { ExifInterface(it) }
        }.onFailure { notes.add("EXIF 不可用（无权限、无 EXIF 或格式不支持）") }.getOrNull()
        fun attribute(tag: String): String? = runCatching {
            exif?.getAttribute(tag)?.takeIf { it.isNotBlank() }
        }.getOrNull()
        fun number(tag: String): Double? = attribute(tag)?.let { raw ->
            val parts = raw.split('/')
            val result = if (parts.size == 2) {
                val numerator = parts[0].toDoubleOrNull()
                val denominator = parts[1].toDoubleOrNull()
                if (numerator != null && denominator != null && denominator != 0.0) numerator / denominator else null
            } else raw.toDoubleOrNull()
            result?.takeIf { it.isFinite() }
        }
        if (width <= 0) width = number("PixelXDimension")?.toInt() ?: number("ImageWidth")?.toInt() ?: 0
        if (height <= 0) height = number("PixelYDimension")?.toInt() ?: number("ImageLength")?.toInt() ?: 0
        if (width <= 0 || height <= 0) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    width = bounds.outWidth
                    height = bounds.outHeight
                }
            }.onFailure { notes.add("无法读取图片尺寸") }
        }
        val focal = number("FocalLength")?.takeIf { it > 0 }
        val equivalent = number("FocalLengthIn35mmFilm")?.takeIf { it > 0 }
        val exposure = number("ExposureTime")?.takeIf { it > 0 }
        val aperture = number("FNumber")?.takeIf { it > 0 }
        val flash = number("Flash")?.toInt()
        val gps = FloatArray(2)
        val location = photo.location ?: runCatching {
            if (exif?.getLatLong(gps) == true) "${gps[0]}, ${gps[1]}" else null
        }.getOrNull()
        fun decimal(value: Double) = String.format(Locale.getDefault(), "%.2f", value)
        fun date(value: Long?) = value?.takeIf { it > 0 }?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
        } ?: UNKNOWN
        values["名称"] = name ?: UNKNOWN
        values["MIME"] = mime ?: UNKNOWN
        values["路径"] = path ?: UNKNOWN
        values["大小"] = size?.let { "${Formatter.formatFileSize(context, it)} ($it bytes)" } ?: UNKNOWN
        values["ISO"] = attribute("PhotographicSensitivity") ?: attribute("ISOSpeedRatings") ?: UNKNOWN
        values["焦距"] = focal?.let { "${decimal(it)} mm" + (equivalent?.let { eq -> "（35mm 等效 ${decimal(eq)} mm）" } ?: "") } ?: UNKNOWN
        values["曝光时间"] = exposure?.let { "${decimal(it)} s（EXIF: ${attribute("ExposureTime")}）" } ?: UNKNOWN
        values["闪光灯"] = flash?.let { "${if (it and 1 != 0) "已闪光" else "未闪光"}（标志 $it）" } ?: UNKNOWN
        values["光圈"] = aperture?.let { "f/${decimal(it)}" } ?: UNKNOWN
        values["尺寸"] = if (width > 0 && height > 0) "$width × $height px" else UNKNOWN
        values["像素 MP"] = if (width > 0 && height > 0) decimal(width.toDouble() * height / 1_000_000) + " MP" else UNKNOWN
        // Camera exposure setting EV, not ISO-normalized scene EV or exposure compensation.
        values["EV（由光圈/曝光计算）"] = if (aperture != null && exposure != null) decimal(ln(aperture * aperture / exposure) / ln(2.0)) else UNKNOWN
        values["曝光补偿"] = number("ExposureBiasValue")?.let { decimal(it) + " EV" } ?: UNKNOWN
        values["位置"] = location ?: UNKNOWN
        values["创建时间"] = date(created) + if (created == null) attribute("DateTimeOriginal")?.let { "（EXIF: $it）" }.orEmpty() else ""
        values["修改时间"] = date(modified)
        values["Media ID"] = mediaId?.toString() ?: UNKNOWN
        if (path == null) notes.add("启动参数没有路径，仅显示已有信息")
        val text = values.entries.joinToString("\n") { "${it.key}：${it.value}" } +
            "\n注：启动照片的信息；滑动切换后不保证对应当前照片。" +
            if (notes.isEmpty()) "" else "\n" + notes.joinToString("\n")
        return Details(text, mediaId, editUri, mime)
    }

    private fun showDetails(activity: Activity, details: Details, trigger: WeakReference<ImageButton>) {
        val metrics = activity.resources.displayMetrics
        fun dp(value: Int) = (value * metrics.density + 0.5f).toInt()
        val dark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val foreground = if (dark) 0xFFF1F1F1.toInt() else 0xFF222222.toInt()
        val surface = if (dark) 0xDE242424.toInt() else 0xDEFFFFFF.toInt()
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(surface) }
        }
        val header = TextView(activity).apply {
            text = "id: ${details.mediaId ?: UNKNOWN}"
            textSize = 17f
            setTextColor(foreground)
            compoundDrawablePadding = dp(10)
            val icon = Entry.moduleRes.getDrawable(R.drawable.ic_gallery_details_info, activity.theme).mutate()
            icon.setTint(foreground)
            icon.setBounds(0, 0, dp(20), dp(20))
            setCompoundDrawables(icon, null, null, null)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        panel.addView(header)
        val textView = TextView(activity).apply {
            text = details.text
            textSize = 13f
            setTextColor(foreground)
            setLineSpacing(dp(2).toFloat(), 1f)
            setTextIsSelectable(true)
        }
        val scroll = object : ScrollView(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val limit = (metrics.heightPixels * 0.55f).toInt()
                val requested = View.MeasureSpec.getSize(heightMeasureSpec)
                val capped = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) limit else minOf(limit, requested)
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(capped, View.MeasureSpec.AT_MOST))
            }
        }.apply { addView(textView) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        fun action(label: String, onClick: () -> Unit) {
            val button = Button(activity).apply {
                text = label
                textSize = 14f
                isAllCaps = false
                setTextColor(foreground)
                minWidth = 0
                minimumWidth = 0
                minHeight = dp(40)
                minimumHeight = dp(40)
                setPadding(dp(4), 0, dp(4), 0)
                background = RippleDrawable(ColorStateList.valueOf(if (dark) 0x33FFFFFF else 0x22000000),
                    GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.TRANSPARENT) }, null)
                setOnClickListener { onClick() }
            }
            actions.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
        action("隐藏") { trigger.get()?.visibility = View.GONE; dialog.dismiss() }
        action("编辑") {
            val uri = details.editUri?.takeIf { it.scheme == "content" }
            if (uri == null) {
                Toast.makeText(activity, "没有可编辑的内容 URI", Toast.LENGTH_SHORT).show()
            } else {
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_EDIT).apply {
                        setDataAndType(uri, details.mime ?: "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newRawUri("照片", uri)
                    })
                    dialog.dismiss()
                }.onFailure {
                    logW("Gallery details: editor unavailable", it)
                    Toast.makeText(activity, "无法打开编辑器（无权限或没有可用应用）", Toast.LENGTH_SHORT).show()
                }
            }
        }
        action("复制") {
            runCatching {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: error("Clipboard service unavailable")
                clipboard.setPrimaryClip(ClipData.newPlainText("照片详情", details.text))
                Toast.makeText(activity, "已复制全部照片详情", Toast.LENGTH_SHORT).show()
            }.onFailure {
                logW("Gallery details: clipboard unavailable", it)
                Toast.makeText(activity, "复制失败", Toast.LENGTH_SHORT).show()
            }
        }
        panel.addView(actions)
        dialog.setContentView(panel)
        dialog.setCanceledOnTouchOutside(true)
        val window = dialog.window ?: return
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.22f }
        if (Build.VERSION.SDK_INT >= 31) {
            // Some devices disable cross-window blur; the translucent surface remains usable.
            runCatching {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 48 }
            }.onFailure { logW("Gallery details: window blur unavailable", it) }
        }
        dialog.show()
        window.setGravity(Gravity.CENTER)
        window.setLayout(minOf((metrics.widthPixels * 0.86f).toInt(), dp(440)), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private data class Details(val text: String, val mediaId: Long?, val editUri: Uri?, val mime: String?)

    private data class Photo(
        val path: String?, val mime: String?, val location: String?,
        val length: Long?, val created: Long?
    )

    companion object {
        private const val BUTTON_TAG = "io.github.a13e300.myinjector.photo_details"
        private const val UNKNOWN = "未知 / 不可用"
        private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
        private val executor by lazy { Executors.newSingleThreadExecutor { task ->
            Thread(task, "GalleryPhotoDetails").apply { isDaemon = true }
        } }
    }
}
