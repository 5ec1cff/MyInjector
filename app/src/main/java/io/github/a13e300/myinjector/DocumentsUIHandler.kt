package io.github.a13e300.myinjector

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookAllBefore
import java.lang.ref.WeakReference

class DocumentsUIHandler : IHook() {
    override fun onHook() {
        hookShowDrawerAndScroll()
        hookForceShowAppItem()
    }

    private fun hookShowDrawerAndScroll() = runCatching {
        val pickActivity = findClass("com.android.documentsui.picker.PickActivity")
        val unchangedDrawer = mutableListOf<WeakReference<Any>>()
        pickActivity.hookAfter("onCreate", Bundle::class.java) { param ->
            val action = (param.thisObject as Activity).intent.action
            if (action != Intent.ACTION_GET_CONTENT && action != Intent.ACTION_OPEN_DOCUMENT) {
                return@hookAfter
            }
            param.thisObject.call("setRootsDrawerOpen", true)
            unchangedDrawer.removeIf { it.get() == null }
            unchangedDrawer.add(WeakReference(param.thisObject.getObj("mDrawer")))
        }

        val rootsFragment = findClass("com.android.documentsui.sidebar.RootsFragment")
        val spacerItem = findClass("com.android.documentsui.sidebar.SpacerItem")
        rootsFragment.hookAllBefore("onCurrentRootChanged") { param ->
            val list = param.thisObject.getObjAsN<ListView>("mList") ?: return@hookAllBefore
            val adapter =
                param.thisObject.getObjAsN<ArrayAdapter<*>>("mAdapter") ?: return@hookAllBefore


            var i = adapter.count - 1
            while (i >= 0) {
                if (spacerItem.isInstance(adapter.getItem(i))) break
                i--
            }
            list.setSelection(i + 1)
        }

        // when first onBack, just finish the activity
        val inOnBack = ThreadLocal<Boolean>()
        val sharedInputHandler = findClass("com.android.documentsui.SharedInputHandler")
        sharedInputHandler.hookAll(
            "onBack",
            before = { inOnBack.set(true) },
            after = { inOnBack.set(false) },
        )
        val runtimeDrawerController =
            findClass("com.android.documentsui.DrawerController\$RuntimeDrawerController")
        // skip hide drawer when onBack
        runtimeDrawerController.hookAllBefore("isOpen") { param ->
            if (inOnBack.get() == true && unchangedDrawer.any { it.get() == param.thisObject }) {
                unchangedDrawer.removeIf { it.get() == null || it.get() == param.thisObject }
                param.result = false
            }
        }
        runtimeDrawerController.hookAllBefore("setOpen") { param ->
            unchangedDrawer.removeIf { it.get() == null || it.get() == param.thisObject }
        }
    }.onFailure {
        logE("hookDrawer", it)
    }

    private fun hookForceShowAppItem() = runCatching {
        val inOpenDocument = ThreadLocal<Boolean>()

        // force add AppItem if actions == OPEN_DOCUMENT
        // https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/DocumentsUI/src/com/android/documentsui/picker/PickActivity.java;l=249;drc=28f1c8ab5c2439138de6935d632ed52f6c09fc8d
        val pickActivity = findClass("com.android.documentsui.picker.PickActivity")
        pickActivity.hookAll(
            "setupLayout",
            before = { param ->
                val intent = param.args[0] as Intent
                if (intent.action == Intent.ACTION_OPEN_DOCUMENT) {
                    inOpenDocument.set(true)
                }
            },
            after = {
                inOpenDocument.set(false)
            }
        )

        val rootsFragment = findClass("com.android.documentsui.sidebar.RootsFragment")
        rootsFragment.hookAllBefore("show") { param ->
            if (inOpenDocument.get() != true) return@hookAllBefore
            param.args[1] = true
            (param.args[2] as Intent).apply {
                action = Intent.ACTION_GET_CONTENT
            }
        }

        val inOpenRoot = ThreadLocal<Boolean>()

        // fix startActivity for AppItem
        // https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/DocumentsUI/src/com/android/documentsui/picker/ActionHandler.java;l=377;drc=61197364367c9e404c7da6900658f1b16c42d0da
        val actionHandler = findClass("com.android.documentsui.picker.ActionHandler")
        actionHandler.hook(
            "openRoot",
            ResolveInfo::class.java,
            findClass("com.android.documentsui.base.UserId"),
            before = { inOpenRoot.set(true) },
            after = { inOpenRoot.set(false) },
        )

        Instrumentation::class.java.hookAllBefore("execStartActivity") { param ->
            if (inOpenRoot.get() != true) return@hookAllBefore
            val intent = param.args[4] as Intent
            val newIntent = Intent(intent)
            newIntent.action = Intent.ACTION_GET_CONTENT
            param.args[4] = newIntent
        }
    }.onFailure {
        logE("hookForceShowAppItem", it)
    }
}
