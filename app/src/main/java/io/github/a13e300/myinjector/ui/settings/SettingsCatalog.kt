package io.github.a13e300.myinjector.ui.settings

internal data class SettingsSectionSpec(val key: String, val title: CharSequence, val items: List<SettingsItemSpec>)

internal enum class ActionStyle { Row, Button }

internal sealed class SettingsItemSpec {
    abstract val sectionKey: String
    abstract val key: String
    abstract val title: CharSequence
    abstract val summary: CharSequence?

    data class Switch(override val sectionKey: String, override val key: String, override val title: CharSequence, override val summary: CharSequence?, val defaultValue: Boolean) : SettingsItemSpec()
    data class Text(override val sectionKey: String, override val key: String, override val title: CharSequence, override val summary: CharSequence?, val helpText: CharSequence?) : SettingsItemSpec()
    data class Action(override val sectionKey: String, override val key: String, override val title: CharSequence, override val summary: CharSequence?, val style: ActionStyle, val action: (() -> Unit)? = null) : SettingsItemSpec()
}

/** 原配置键、默认值与说明保持兼容；由明确的数据定义替代废弃的 Preference XML。 */
internal fun moduleSettingsSections(): List<SettingsSectionSpec> = listOf(
    SettingsSectionSpec("hotreload", "模块更新", listOf(
        SettingsItemSpec.Action("hotreload", "checkHotUpdate", "检查更新", "热更新", ActionStyle.Row),
    )),
    SettingsSectionSpec("system", "系统框架", listOf(
        SettingsItemSpec.Switch("system", "noWakePath", "禁用 MIUI wake path", "不要在 app 启动其他 app 的 activity 的时候询问", false),
        SettingsItemSpec.Switch("system", "noMiuiIntent", "禁用 MIUI intent 替换", "阻止某些打开方式被 MIUI 替换为通过系统应用打开", false),
        SettingsItemSpec.Switch("system", "clipboardWhitelist", "剪切板白名单", "允许特定 app 在后台访问剪切板", false),
        SettingsItemSpec.Text("system", "clipboardWhitelistPackages", "剪切板白名单列表", "用换行分隔", null),
        SettingsItemSpec.Switch("system", "fixSync", "修复 WMS blast sync", "可能可以解决无障碍窗口存在的情况下深色模式切换缓慢的问题", false),
        SettingsItemSpec.Switch("system", "xSpace", "MIUI 多开修复", "允许 shell 和 xmsf 打开多开用户 app", false),
        SettingsItemSpec.Switch("system", "forceNewTask", "强制 NEW_TASK", "强制某些 activity 通过 NEW_TASK 启动", false),
        SettingsItemSpec.Text("system", "forceNewTaskRules", "强制 NEW_TASK 规则", null, "一行一个规则，形如<source>:<target>[:options]，source/target可以是包名或组件名(用/分隔)，可以用*表示除了自己之外的所有包。options用逗号分割，支持的选项包含ir(ignoreResult，允许startActivityForResult也使用NEW_TASK，这会导致返回值失效)和nd(newDocument，默认使用新窗口打开)"),
        SettingsItemSpec.Switch("system", "forceNewTaskDebug", "调试 NEW_TASK 规则", "见日志 TAG MyInjector", false),
        SettingsItemSpec.Switch("system", "overrideStatusBar", "修改状态栏属性", "修改某些应用(在某些 Activity 下)的状态栏属性，如是否亮色", false),
        SettingsItemSpec.Text("system", "overrideStatusBarRules", "修改状态栏属性规则", null, "一行一个规则，形如<package>[/activity]:<light/dark>，表示包 package 的 activity （如空则为所有 activity）总是使用 light/dark 状态栏"),
        SettingsItemSpec.Switch("system", "noSwipeToKillLockedProcess", "划卡不杀上锁", "划卡不杀最近任务中上锁应用（仅小米）", false),
        SettingsItemSpec.Switch("system", "noSwipeToKillNoRestrictProcess", "划卡不杀无限制", "划卡不杀电量管控无限制应用（仅小米）", false),
        SettingsItemSpec.Switch("system", "bypassShellDexOptRestriction", "绕过 pm compile 限制", "绕过小米低版本的 pm compile 命令限制（允许强制编译）", false),
        SettingsItemSpec.Switch("system", "fixHyperOSAvoidMoveToFront", "修复 avoidMoveToFront", "修复 HyperOS 下快捷方式 App 拉起其他 App 后阻止该 App 拉起自身 Activity 的问题", false),
    )),
    SettingsSectionSpec("systemui", "系统界面", listOf(
        SettingsItemSpec.Switch("systemui", "loopNotificationControlCenter", "通知与控制中心循环滑动", "允许向两侧连续切换通知与控制中心（HyperOS）", false),
        SettingsItemSpec.Switch("systemui", "topPullSwitchNotificationControlCenter", "顶部下拉切换通知与控制中心", "面板展开后，仍可从顶部状态栏的通知或控制中心区域下拉切换（HyperOS）", false),
        SettingsItemSpec.Switch("systemui", "showNotificationDetail", "显示通知详情（小米）", "在小米手机上，长按通知可显示详情，点击详情可打开相应的通知渠道管理界面", false),
        SettingsItemSpec.Switch("systemui", "noDndNotification", "勿扰模式无通知（小米）", "阻止小米手机上通过磁贴外的方式开关勿扰模式开关后显示 Toast 或者灵动额头", false),
        SettingsItemSpec.Switch("systemui", "alwaysExpandNotification", "总是展开通知", "以便使用按钮", false),
        SettingsItemSpec.Switch("systemui", "fixWhiteSplash", "修复纯白 Splash", "夜间模式将具有纯白背景的 Splash 变为黑色", false),
    )),
    SettingsSectionSpec("market", "应用商店", listOf(
        SettingsItemSpec.Switch("market", "marketRemoveSplashAd", "移除开屏广告", "跳过应用商店冷启动、后台返回及外部详情开屏广告", true),
        SettingsItemSpec.Switch("market", "marketSuperMinimalMode", "超级极简模式", "极简模式下只保留“我的”，隐藏底栏，并将原生搜索框放到“我的”顶部；重启应用商店后生效", true),
        SettingsItemSpec.Switch("market", "marketSimplifyUpdates", "简化应用升级页面", "移除“精选推荐”“大家还安装了”等推广内容，升级列表始终展开；重新打开升级页后生效", true),
    )),
    SettingsSectionSpec("miuihome", "小米桌面", listOf(
        SettingsItemSpec.Switch("miuihome", "miuiHomeOpenAospSettings", "打开原生应用信息", "最近任务长按应用菜单里的设置按钮时打开原生应用信息页", false),
        SettingsItemSpec.Switch("miuihome", "miuiHomeDragKill", "划卡强杀进程", "最近任务卡片拖动悬停 700ms 后继续划动时强制停止应用进程", false),
        SettingsItemSpec.Switch("miuihome", "miuiHomeDisablePreLaunch", "禁用应用预启动", "禁用 MIUI 桌面的应用预启动逻辑", false),
    )),
)
