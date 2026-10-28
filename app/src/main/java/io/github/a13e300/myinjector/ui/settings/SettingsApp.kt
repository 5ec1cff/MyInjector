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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
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
    val packageName: String,
    val label: CharSequence,
    val icon: ImageBitmap,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsApp(
    prefs: SharedPreferences,
    serviceConnected: Boolean,
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
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    var routes by rememberSaveable { mutableStateOf(listOf("home")) }
    val apps by produceState<List<AppSettingsDestination>?>(null) {
        value = withContext(Dispatchers.IO) { loadApps() }
    }
    fun navigateBack() { if (routes.size > 1) routes = routes.dropLast(1) else onClose() }

    NavDisplay(
        backStack = routes, onBack = ::navigateBack,
        modifier = Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.background)
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
                val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Scaffold(
                    modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(section?.title?.toString() ?: "MyInjector", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                if (section != null) {
                                    IconButton(onClick = ::navigateBack) {
                                        Icon(MaterialSymbols.RoundedFilled.Arrow_back, "返回首页")
                                    }
                                }
                            },
                            scrollBehavior = behavior,
                        )
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                        if (section == null) {
                            HomePage(
                                sections, apps, currentServiceConnected,
                                onOpenSection = { routes = routes + it },
                                onOpenApp = openApp,
                                onUpdate = { onAction(sections.first { it.key == "hotreload" }.items.single() as SettingsItemSpec.Action) },
                            )
                        } else SectionPage(section, prefs, onToggle, onEdit, onAction)
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
    sections: List<SettingsSectionSpec>, apps: List<AppSettingsDestination>?, connected: Boolean,
    onOpenSection: (String) -> Unit, onOpenApp: (String) -> Unit, onUpdate: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalized = query.trim()
    val components = sections.filter { it.key != "hotreload" &&
        (it.title.contains(normalized, true) || sectionPackage(it.key).contains(normalized, true)) }
    val visibleApps = apps.orEmpty().filter {
        it.label.contains(normalized, true) || it.packageName.contains(normalized, true)
    }
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
        item("search") { SettingsSearchField(query, { query = it }, "搜索应用或系统组件") }
        if (components.isNotEmpty()) {
            item("components_label") { SectionLabel("系统组件") }
            itemsIndexed(components, key = { _, it -> it.key }) { index, section ->
                DestinationCard(
                    section.title.toString(), sectionPackage(section.key), sectionIcon(section.key),
                    index == 0, index == components.lastIndex,
                    onClick = { onOpenSection(section.key) },
                )
            }
        }
        item("apps_label") { SectionLabel("应用设置") }
        if (apps == null) {
            item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
        } else if (visibleApps.isEmpty()) {
            item("empty") {
                Text(
                    if (query.isBlank()) "尚未安装带有设置入口的应用" else "没有匹配的应用",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            itemsIndexed(visibleApps, key = { _, it -> it.packageName }) { index, app ->
                DestinationCard(
                    app.label.toString(), app.packageName, MaterialSymbols.RoundedFilled.Apps,
                    index == 0, index == visibleApps.lastIndex, app.icon,
                    onClick = { onOpenApp(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun DestinationCard(
    title: String, summary: String, icon: ImageVector, first: Boolean, last: Boolean,
    appIcon: ImageBitmap? = null, onClick: () -> Unit,
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
            content = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(summary) },
            leadingContent = {
                if (appIcon != null) Image(appIcon, null, Modifier.size(44.dp))
                else Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(icon, null, Modifier.padding(12.dp).size(24.dp))
                }
            },
            trailingContent = { Icon(MaterialSymbols.RoundedFilled.Chevron_right, null) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun SectionPage(
    section: SettingsSectionSpec, prefs: SharedPreferences,
    onToggle: (SettingsItemSpec.Switch, Boolean) -> Unit,
    onEdit: (SettingsItemSpec.Text) -> Unit, onAction: (SettingsItemSpec.Action) -> Unit,
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
                sectionPackage(section.key) + if (section.key == "miuihome") " · 修改会自动同步到桌面" else " · 修改自动同步，也可在页末手动提交",
                Modifier.padding(bottom = 16.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item("search") {
            SettingsSearchField(query, { query = it }, "搜索此页设置")
            Spacer(Modifier.height(12.dp))
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

@Composable
private fun SectionLabel(title: String) {
    Text(
        title, Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    )
}

private fun sectionPackage(key: String): String = when (key) {
    "system" -> "android"
    "systemui" -> "com.android.systemui"
    "miuihome" -> "com.miui.home"
    else -> "MyInjector"
}

private fun sectionIcon(key: String): ImageVector = when (key) {
    "system" -> MaterialSymbols.RoundedFilled.Tune
    "systemui" -> MaterialSymbols.RoundedFilled.Notifications
    else -> MaterialSymbols.RoundedFilled.Home
}
