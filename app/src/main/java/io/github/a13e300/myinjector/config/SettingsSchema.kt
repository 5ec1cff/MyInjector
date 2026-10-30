package io.github.a13e300.myinjector.config

/** 仅包含模块 Activity 管理的配置；宿主内设置不参与同步。 */
object SettingsSchema {
    const val VERSION_KEY = "__schema_version"
    val groups: Map<String, Map<String, Any>> = linkedMapOf(
        "system" to linkedMapOf<String, Any>(
            "noWakePath" to false,
            "noMiuiIntent" to false,
            "clipboardWhitelist" to false,
            "clipboardWhitelistPackages" to "",
            "fixSync" to false,
            "xSpace" to false,
            "forceNewTask" to false,
            "forceNewTaskRules" to "",
            "forceNewTaskDebug" to false,
            "overrideStatusBar" to false,
            "overrideStatusBarRules" to "",
            "noSwipeToKillLockedProcess" to false,
            "noSwipeToKillNoRestrictProcess" to false,
            "bypassShellDexOptRestriction" to false,
            "fixHyperOSAvoidMoveToFront" to false
        ),
        "systemui" to linkedMapOf<String, Any>(
            "showNotificationDetail" to false,
            "noDndNotification" to false,
            "alwaysExpandNotification" to false,
            "fixWhiteSplash" to false,
            "loopNotificationControlCenter" to false,
            "topPullSwitchNotificationControlCenter" to false
        ),
        "miuihome" to linkedMapOf<String, Any>(
            "miuiHomeOpenAospSettings" to false,
            "miuiHomeDragKill" to false,
            "miuiHomeDisablePreLaunch" to false
        )
    )
    val defaults = groups.values.flatMap { it.entries }.associate { it.key to it.value }
}
