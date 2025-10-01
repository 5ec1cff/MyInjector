package io.github.a13e300.myinjector

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAllBefore

class MiuiGalleryHandler : IHook() {
    override fun onHook() {
        val galleryOpenProviderClass = findClass("com.miui.gallery.provider.GalleryOpenProvider")
        findClass("com.miui.gallery.ui.photoPage.bars.menuitem.Send").hookAllBefore("onClick") { param ->
            val item = param.args[0]
            if (item == null) {
                logE("no item")
                return@hookAllBefore
            }
            val path = item.getObjAsN<String>("mFilePath")
            if (path == null) {
                logE("no mFilePath: $item")
                return@hookAllBefore
            }
            val uri = galleryOpenProviderClass.callS("translateToContent", path) as Uri
            logD("uri=$uri")
            val activity = param.thisObject.call("getBaseActivity") as Activity
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "image/jpeg"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(Intent.createChooser(intent, null).also {
                // allow intentresolver to preview
                it.clipData = ClipData.newRawUri("", uri)
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            param.result = null
        }
        // TODO: multiple send
    }
}