package io.github.a13e300.myinjector.bili

import android.app.Activity
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.bridge.Unhook
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

internal class BiliSettingsEntry(
    private val loader: ClassLoader,
    private val show: (Activity) -> Unit,
    private val onError: (String, Throwable) -> Unit,
) {
    fun install(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        runCatching { installLegacy(creator, hooks) }
            .onFailure { onError("安装旧版我的设置入口", it) }
        runCatching { installNew(creator, hooks) }
            .onFailure { onError("安装新版我的设置入口", it) }
    }

    private fun installLegacy(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        val members = biliLegacySettingsEntryMembers(creator)
        val model = MenuItem(loader.loadClass(BILI_MINE_ITEM))
        val itemList = loader.loadClass("com.bilibili.lib.homepage.mine.MenuGroup").getField("itemList")
        val groups = DexField(members.getValue("groups").descriptor).getFieldInstance(loader)
            .apply { isAccessible = true }
        val fragment = DexField(members.getValue("fragment").descriptor).getFieldInstance(loader)
            .apply { isAccessible = true }
        val getActivity = loader.loadClass("androidx.fragment.app.Fragment").getMethod("getActivity")
        // 在查找原生菜单代理之前接管点击，自定义项无需注册原生业务代理。
        hooks += DexMethod(members.getValue("click").descriptor).getMethodInstance(loader).hookBefore { param ->
            runCatching {
                if (model.isSettings(param.args[0])) {
                    param.result = null
                    show(getActivity.invoke(fragment.get(param.thisObject)) as Activity)
                }
            }.onFailure { onError("打开旧版我的设置", it) }
        }
        hooks += DexMethod(members.getValue("render").descriptor).getMethodInstance(loader).hookAfter { param ->
            if (param.throwable != null) return@hookAfter
            runCatching {
                // 原生通知后、下一帧布局前，修改与 Adapter 共用的菜单组。
                (groups.get(param.thisObject) as List<*>).filterNotNull().forEach { group ->
                    val items = itemList.get(group) as? List<*> ?: return@forEach
                    val updated = model.insert(items)
                    if (updated !== items) itemList.set(group, updated)
                }
            }.onFailure { onError("插入旧版我的设置入口", it) }
        }
    }

    private fun installNew(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        val model = MenuItem(loader.loadClass(BILI_MINE_V2_ITEM))
        val click = creator.create("settingsEntry.new.click") { locateBiliNewSettingsClick(it) }
        hooks += DexMethod(click.descriptor).getMethodInstance(loader).hookBefore { param ->
            runCatching {
                if (model.isSettings(param.args[1])) {
                    param.result = null
                    show(param.args[0] as Activity)
                }
            }.onFailure { onError("打开新版我的设置", it) }
        }
        hooks += loader.loadClass("com.bilibili.lib.homepage.mine.MenuGroupV2")
            .getMethod("getItemList").hookAfter { param ->
                if (param.throwable != null) return@hookAfter
                runCatching {
                    val items = param.result as? List<*> ?: return@runCatching
                    // Compose 从模型读取列表，不修改响应缓存，也覆盖宿主本地补出的设置项。
                    param.result = model.insert(items)
                }.onFailure { onError("插入新版我的设置入口", it) }
            }
    }

    private class MenuItem(type: Class<*>) {
        private val constructor = type.getConstructor()
        private val fields = listOf("id", "title", "uri", "icon", "iconResId", "visible",
            "localDefault", "localShow", "needLogin", "type").associateWith {
            type.getDeclaredField(it).apply { isAccessible = true }
        }

        private fun uri(item: Any?) = item?.let { fields.getValue("uri").get(it) as? String }
        fun isSettings(item: Any?) = uri(item) == URI

        fun insert(items: List<*>): List<*> {
            if (items.any(::isSettings)) return items
            val index = items.indexOfFirst {
                uri(it) in listOf("activity://main/preference", "bilibili://main/preference")
            }
            if (index < 0) return items
            val template = items[index]!!
            val item = constructor.newInstance()
            mapOf("id" to 0x4d59494eL, "title" to "MyInjector 设置", "uri" to URI,
                "visible" to 1, "localDefault" to true, "localShow" to true, "needLogin" to 0)
                .forEach { (key, value) -> fields.getValue(key).set(item, value) }
            listOf("icon", "iconResId", "type").forEach { key ->
                fields.getValue(key).let { it.set(item, it.get(template)) }
            }
            return items.toMutableList().apply { add(index, item) }
        }
    }

    companion object {
        private const val URI = "bilibili://myinjector/settings"
    }
}
