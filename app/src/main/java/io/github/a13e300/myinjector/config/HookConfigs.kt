package io.github.a13e300.myinjector.config

import io.github.a13e300.myinjector.*
import java.util.Arrays
import java.util.stream.Collectors

/** 从同一份不可变快照解析，保留原规则语法和默认值。 */
class ConfigValues(private val values: Map<String, *>) {
    fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    fun getString(key: String, default: String): String = values[key] as? String ?: default
}

object HookConfigs {
    fun system(prefs: ConfigValues): SystemServerConfig {
        return SystemServerConfig.newBuilder()
            .setNoWakePath(prefs.getBoolean("noWakePath", false))
            .setNoMiuiIntent(prefs.getBoolean("noMiuiIntent", false))
            .setClipboardWhitelist(prefs.getBoolean("clipboardWhitelist", false))
            .setFixSync(prefs.getBoolean("fixSync", false))
            .setXSpace(prefs.getBoolean("xSpace", false))
            .setBypassShellDexOptRestriction(
                prefs.getBoolean(
                    "bypassShellDexOptRestriction",
                    false,
                )
            )
            .setNoSwipeToKillLockedProcess(prefs.getBoolean("noSwipeToKillLockedProcess", false))
            .setNoSwipeToKillNoRestrictProcess(
                prefs.getBoolean(
                    "noSwipeToKillNoRestrictProcess",
                    false,
                )
            )
            .setFixHyperOSAvoidMoveToFromt(
                prefs.getBoolean(
                    "fixHyperOSAvoidMoveToFront",
                    false,
                )
            )
            .addAllClipboardWhitelistPackages(
                Arrays.stream<String?>(
                    prefs.getString("clipboardWhitelistPackages", "")!!.trim { it <= ' ' }
                        .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                )
                    .collect(
                        Collectors.toList()
                    )
            )
            .setForceNewTask(prefs.getBoolean("forceNewTask", false))
            .addAllForceNewTaskRules(
                Arrays.stream<String?>(
                    prefs.getString(
                        "forceNewTaskRules",
                        "",
                    )!!.trim { it <= ' ' }.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                ).map<NewTaskRule?> { x: String? ->
                    val parts: Array<String?> =
                        x!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    var ignoreResult = false
                    var useNewDoc = false
                    if (parts.size == 3) {
                        val options: Array<String?> =
                            parts[2]!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        for (o in options) {
                            if ("ir" == o) {
                                ignoreResult = true
                            } else if ("nd" == o) {
                                useNewDoc = true
                            }
                        }
                    } else if (parts.size != 2) return@map null
                    var sourcePackage: String = parts[0]!!
                    var targetPackage: String = parts[1]!!
                    var sourceComponent = ""
                    var targetComponent = ""
                    if (sourcePackage.contains("/")) {
                        val l: Array<String?> =
                            sourcePackage.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        if (l.size == 2) {
                            sourcePackage = l[0]!!
                            sourceComponent = l[1]!!
                            if (sourceComponent.startsWith(".")) sourceComponent = l[0] + l[1]
                        }
                    }
                    if (targetPackage.contains("/")) {
                        val l: Array<String?> =
                            targetPackage.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        if (l.size == 2) {
                            targetPackage = l[0]!!
                            targetComponent = l[1]!!
                            if (targetComponent.startsWith(".")) targetComponent = l[0] + l[1]
                        }
                    }
                    NewTaskRule.newBuilder()
                        .setSourcePackage(sourcePackage)
                        .setTargetPackage(targetPackage)
                        .setSourceComponent(sourceComponent)
                        .setTargetComponent(targetComponent)
                        .setUseNewDocument(useNewDoc)
                        .setIgnoreResult(ignoreResult)
                        .build()
                }.filter { it != null }.collect(Collectors.toList())
            )
            .setOverrideStatusBar(prefs.getBoolean("overrideStatusBar", false))
            .addAllOverrideStatusBarRules(
                Arrays.stream<String?>(
                    prefs.getString("overrideStatusBarRules", "")!!
                        .trim { it <= ' ' }
                        .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                ).map<OverrideStatusBarRule?> { x: String? ->
                    val parts: Array<String?> =
                        x!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (parts.size != 2) return@map null
                    var pkg: String = parts[0]!!
                    var light = false
                    if ("light" == parts[1]) {
                        light = true
                    } else if ("dark" != parts[1]) {
                        return@map null
                    }
                    var component = ""
                    val pkgSplit: Array<String?> =
                        pkg.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (pkgSplit.size == 2) {
                        pkg = pkgSplit[0]!!
                        component = pkgSplit[1]!!
                        if (component.startsWith(".")) component = pkg + component
                    } else if (pkgSplit.size != 1) return@map null
                    OverrideStatusBarRule.newBuilder()
                        .setPackage(pkg)
                        .setComponent(component)
                        .setLight(light)
                        .build()
                }.filter { it != null }.collect(Collectors.toList())
            )
            .setForceNewTaskDebug(prefs.getBoolean("forceNewTaskDebug", false))
            .build()
    }

    fun systemUI(prefs: ConfigValues): SystemUIConfig {
        return SystemUIConfig.newBuilder()
            .setAlwaysExpandNotification(prefs.getBoolean("alwaysExpandNotification", false))
            .setNoDndNotification(prefs.getBoolean("noDndNotification", false))
            .setShowNotificationDetail(prefs.getBoolean("showNotificationDetail", false))
            .setFixWhiteSplash(prefs.getBoolean("fixWhiteSplash", false))
            .setLoopNotificationControlCenter(prefs.getBoolean("loopNotificationControlCenter", false))
            .setTopPullSwitchNotificationControlCenter(prefs.getBoolean("topPullSwitchNotificationControlCenter", false))
            .build()
    }

    fun miuiHome(prefs: ConfigValues): MiuiHomeConfig {
        return MiuiHomeConfig.newBuilder()
            .setOpenAospSettings(prefs.getBoolean("miuiHomeOpenAospSettings", false))
            .setDragKill(prefs.getBoolean("miuiHomeDragKill", false))
            .setDisablePreLaunch(prefs.getBoolean("miuiHomeDisablePreLaunch", false))
            .build()
    }
}
