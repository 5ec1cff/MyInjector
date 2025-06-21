package io.github.a13e300.myinjector

import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ResolveInfo
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookAllBefore

class DocumentsUIHandler : IHook() {
    override fun onHook() {
        val inOpenDocument = ThreadLocal<Boolean>()

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
    }
}
