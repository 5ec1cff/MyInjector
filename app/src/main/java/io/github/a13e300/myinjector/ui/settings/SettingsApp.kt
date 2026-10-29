@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.a13e300.myinjector.ui.settings

import android.content.SharedPreferences
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import io.github.a13e300.myinjector.scope.ScopeManager
import io.github.a13e300.myinjector.scope.ScopeCatalog
import io.github.a13e300.myinjector.scope.ScopeApplication
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.*
import io.github.a13e300.myinjector.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AppSettingsDestination(
    val app: ScopeApplication,
    val icon: ImageBitmap,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsApp(
    prefs: SharedPreferences,
    serviceConnected: Boolean,
    launcherIconVisible: Boolean = true,
    onLauncherIconToggle: (Boolean) -> Unit = {},
    syncStatus: String = "",
    scopeState: ScopeManager.State = ScopeManager.State(),
    onScopeToggle: (String, Boolean) -> Unit = { _, _ -> },
    appListVersion: Int = 0,
    loadApps: () -> List<AppSettingsDestination>,
    openApp: (String) -> Unit,
    onToggle: (SettingsItemSpec.Switch, Boolean) -> Unit,
    onEdit: (SettingsItemSpec.Text) -> Unit,
    onAction: (SettingsItemSpec.Action) -> Unit,
    onClose: () -> Unit,
) {
    val sections = remember { moduleSettingsSections() }
    // Navigation 3 按返回栈缓存 NavEntry；页面必须读取 State，避免捕获首次连接值。
    val currentServiceConnected by rememberUpdatedState(serviceConnected)
    val currentLauncherIconVisible by rememberUpdatedState(launcherIconVisible)
    val currentLauncherIconToggle by rememberUpdatedState(onLauncherIconToggle)
    val currentSyncStatus by rememberUpdatedState(syncStatus)
    val currentScope by rememberUpdatedState(scopeState)
    val currentScopeToggle by rememberUpdatedState(onScopeToggle)
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    var routes by rememberSaveable { mutableStateOf(listOf("home")) }
    val apps by produceState<List<AppSettingsDestination>?>(null, appListVersion) {
        value = withContext(Dispatchers.IO) { loadApps() }
    }
    fun navigateBack() { if (routes.size > 1) routes = routes.dropLast(1) else onClose() }

    NavDisplay(
        backStack = routes, onBack = ::navigateBack,
        // 横向避开刘海；顶部交给标题栏，让其背景覆盖状态栏、文字仍处于安全区。
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .clipToBounds()
            .semantics { testTagsAsResourceId = true },
        sizeTransform = null,
        transitionSpec = { settingsPageTransition(true, direction) },
        popTransitionSpec = { settingsPageTransition(false, direction) },
        predictivePopTransitionSpec = { edge ->
            settingsPageTransition(false, if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1)
        },
        entryProvider = { route ->
            NavEntry(route) {
                val section = sections.firstOrNull { it.key == route }
                val packageName = ScopeCatalog.components[route]
                val destination = apps?.firstOrNull { it.app.packageName == packageName }
                val isHome = route == "home"
                val title = if (isHome) "MyInjector" else destination?.app?.label ?: section?.title?.toString().orEmpty()
                val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Scaffold(
                    modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
                    // safeDrawing 包含 IME；列表的可用高度随键盘变化，底部取最大值而非重复累加。
                    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                    topBar = {
                        LargeTopAppBar(
                            modifier = Modifier.testTag("settings_top_bar"),
                            title = { Text(title, fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                if (!isHome) {
                                    IconButton(onClick = ::navigateBack) {
                                        Icon(MaterialSymbols.RoundedFilled.Arrow_back, "返回首页")
                                    }
                                }
                            },
                            scrollBehavior = behavior,
                            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                        )
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                        if (isHome) {
                            HomePage(
                                apps, currentServiceConnected, currentSyncStatus, currentScope, currentScopeToggle,
                                launcherIconVisible = currentLauncherIconVisible,
                                onLauncherIconToggle = currentLauncherIconToggle,
                                onOpenModuleSettings = { routes = routes + it },
                                onOpenApp = openApp,
                                onUpdate = { onAction(sections.first { it.key == "hotreload" }.items.single() as SettingsItemSpec.Action) },
                            )
                        } else if (destination != null && section != null) {
                            SectionPage(destination.app, section, prefs, onToggle, onEdit, onAction,
                                currentScope, currentScopeToggle, openApp)
                        } else if (apps == null) {
                            CircularProgressIndicator(Modifier.padding(24.dp))
                        } else {
                            Text("此应用已卸载或不再可用", Modifier.padding(24.dp))
                        }
                    }
                }
            }
        },
    )
}

/** 短距离滑动表达页面层级，复用 Compose 的标准缓动与淡入淡出，交由 Navigation 3 驱动与取消。 */
private fun settingsPageTransition(forward: Boolean, direction: Int): ContentTransform {
    val spatialSpec = tween<IntOffset>(durationMillis = 350, easing = FastOutSlowInEasing)
    val effectsSpec = tween<Float>(durationMillis = 220)
    val enter = slideInHorizontally(spatialSpec) { width ->
        if (forward) direction * width / 5 else -direction * width / 12
    } + fadeIn(effectsSpec, initialAlpha = if (forward) 0f else 0.9f)
    val exit = slideOutHorizontally(spatialSpec) { width ->
        if (forward) -direction * width / 12 else direction * width / 5
    } + fadeOut(effectsSpec, targetAlpha = if (forward) 0.9f else 0f)
    return enter togetherWith exit
}

@Composable
private fun HomePage(
    apps: List<AppSettingsDestination>?, connected: Boolean, syncStatus: String,
    scope: ScopeManager.State, onScopeToggle: (String, Boolean) -> Unit,
    launcherIconVisible: Boolean, onLauncherIconToggle: (Boolean) -> Unit,
    onOpenModuleSettings: (String) -> Unit, onOpenApp: (String) -> Unit, onUpdate: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalized = query.trim()
    val visibleApps = apps.orEmpty().filter {
        it.app.label.contains(normalized, true) || it.app.packageName.contains(normalized, true) ||
            ScopeCatalog.applicationPackage(it.app.packageName).contains(normalized, true)
    }
    val components = visibleApps.filter { it.app.isSystemApp }
    val applications = visibleApps.filterNot { it.app.isSystemApp }
    LazyColumn(
        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().testTag("settings_home"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item("overview") {
            Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(MaterialSymbols.RoundedFilled.Extension, null, Modifier.size(32.dp))
                    Text("应用与系统", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (connected) "模块服务已连接 · 按应用管理你的设置" else "模块服务未连接 · 请在框架中启用模块",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(syncStatus, style = MaterialTheme.typography.bodySmall)
                    Text(scope.message, style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = onUpdate, modifier = Modifier.testTag("hot_update")) {
                        Icon(MaterialSymbols.RoundedFilled.System_update, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("检查热更新")
                    }
                    Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item("launcher_icon") {
            SettingRow("显示桌面图标", "关闭后仍可从框架打开模块设置", checked = launcherIconVisible) {
                onLauncherIconToggle(!launcherIconVisible)
            }
        }
        stickyHeader(key = "search") {
            SettingsSearchHeader(query, { query = it }, "搜索应用或系统组件")
        }
        if (apps == null) {
            item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
        } else if (visibleApps.isEmpty()) {
            item("empty") {
                Text(
                    if (query.isBlank()) "尚未安装作用域列表中的应用" else "没有匹配的应用",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            if (components.isNotEmpty()) {
                item("components_label") { SectionLabel("系统组件") }
                itemsIndexed(components, key = { _, it -> it.app.packageName }) { index, destination ->
                    AppScopeCard(destination, scope, onScopeToggle, onOpenApp, onOpenModuleSettings,
                        index == 0, index == components.lastIndex)
                }
            }
            if (applications.isNotEmpty()) {
                item("apps_label") { SectionLabel("应用作用域") }
                itemsIndexed(applications, key = { _, it -> it.app.packageName }) { index, destination ->
                    AppScopeCard(destination, scope, onScopeToggle, onOpenApp, onOpenModuleSettings,
                        index == 0, index == applications.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun AppScopeCard(
    destination: AppSettingsDestination, scope: ScopeManager.State,
    onToggle: (String, Boolean) -> Unit, onSettings: (String) -> Unit, onOpenModuleSettings: (String) -> Unit,
    first: Boolean, last: Boolean,
) {
    val app = destination.app
    val sectionKey = app.sectionKey
    if (sectionKey != null) {
        DestinationCard(destination, first, last) { onOpenModuleSettings(sectionKey) }
        return
    }
    val checked = app.packageName in scope.enabled
    val pending = app.packageName in scope.pending
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = if (first) 28.dp else 4.dp,
            topEnd = if (first) 28.dp else 4.dp, bottomStart = if (last) 28.dp else 4.dp,
            bottomEnd = if (last) 28.dp else 4.dp),
    ) {
        Column {
            ListItem(
                content = { Text(app.label, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(app.packageName) },
                leadingContent = { Image(destination.icon, null, Modifier.size(44.dp)) },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(checked, { onToggle(app.packageName, it) },
                            enabled = scope.ready && scope.pending.isEmpty(),
                            modifier = Modifier.testTag("scope_${app.packageName}")
                                .semantics { contentDescription = "${app.label}作用域" })
                        if (pending) Text("处理中", style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            if (app.hasSettings) TextButton(onClick = { onSettings(app.packageName) },
                modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 4.dp)) {
                Icon(MaterialSymbols.RoundedFilled.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("应用内设置")
            }
        }
    }
}

@Composable
private fun DestinationCard(
    destination: AppSettingsDestination, first: Boolean, last: Boolean, onClick: () -> Unit,
) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = if (first) 28.dp else 4.dp, topEnd = if (first) 28.dp else 4.dp,
            bottomStart = if (last) 28.dp else 4.dp, bottomEnd = if (last) 28.dp else 4.dp,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            content = { Text(destination.app.label, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(ScopeCatalog.applicationPackage(destination.app.packageName)) },
            leadingContent = { Image(destination.icon, null, Modifier.size(44.dp)) },
            trailingContent = { Icon(MaterialSymbols.RoundedFilled.Chevron_right, null) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun SectionPage(
    app: ScopeApplication, section: SettingsSectionSpec, prefs: SharedPreferences,
    onToggle: (SettingsItemSpec.Switch, Boolean) -> Unit,
    onEdit: (SettingsItemSpec.Text) -> Unit, onAction: (SettingsItemSpec.Action) -> Unit,
    scope: ScopeManager.State, onScopeToggle: (String, Boolean) -> Unit, onOpenApp: (String) -> Unit,
) {
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val visible = section.items.filter {
        it.title.contains(query.trim(), true) || it.summary?.contains(query.trim(), true) == true ||
            it.key.contains(query.trim(), true) || (it as? SettingsItemSpec.Text)?.helpText?.contains(query.trim(), true) == true
    }
    LazyColumn(
        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().testTag("section_${section.key}"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item("description") {
            Text(
                ScopeCatalog.applicationPackage(app.packageName) + " · 修改自动同步，未连接时将在连接后补交",
                Modifier.padding(bottom = 16.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item("scope") {
            val packageName = app.packageName
            SettingRow("启用作用域", scope.message,
                checked = packageName in scope.enabled,
                enabled = scope.ready && scope.pending.isEmpty()) {
                onScopeToggle(packageName, packageName !in scope.enabled)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (app.hasSettings) item("app_settings") {
            TextButton(onClick = { onOpenApp(app.packageName) }) {
                Icon(MaterialSymbols.RoundedFilled.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("应用内设置")
            }
        }
        stickyHeader(key = "search") {
            SettingsSearchHeader(query, { query = it }, "搜索此页设置")
        }
        if (visible.isEmpty()) item("empty") { Text("没有匹配的设置", Modifier.padding(20.dp)) }
        itemsIndexed(visible, key = { _, it -> it.key }) { index, item ->
            val checked = if (item is SettingsItemSpec.Switch) remember(item.key, revision) {
                prefs.getBoolean(item.key, item.defaultValue)
            } else null
            SettingRow(item.title, item.summary, checked, first = index == 0, last = index == visible.lastIndex, query = query.trim()) {
                when (item) {
                    is SettingsItemSpec.Switch -> onToggle(item, checked != true)
                    is SettingsItemSpec.Text -> onEdit(item)
                    is SettingsItemSpec.Action -> onAction(item)
                }
            }
        }
    }
}

/** 由 LazyColumn 负责吸顶；不透明背景遮住从搜索栏下方滚过的列表内容。 */
@Composable
private fun SettingsSearchHeader(query: String, onQueryChange: (String) -> Unit, hint: String) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("settings_search_header")) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            SettingsSearchField(query, onQueryChange, hint)
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        title, Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    )
}
