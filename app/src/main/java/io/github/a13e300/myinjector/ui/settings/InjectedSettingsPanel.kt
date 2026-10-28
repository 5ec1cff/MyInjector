package io.github.a13e300.myinjector.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun InjectedSettingsPanel(
    settings: SettingsGroup,
    onChange: (SettingItem, Any?) -> Boolean,
    onAction: (SettingItem) -> Boolean,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = settings.search(query)
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSearchField(query, { query = it }, "搜索设置")
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (visible.isEmpty()) item { Text("没有匹配的设置", Modifier.padding(16.dp)) }
            itemsIndexed(visible, key = { _, setting -> setting.key }) { index, setting ->
                if (setting is SettingsGroup) {
                    Text(
                        setting.title.toString(), Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp),
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall,
                    )
                } else {
                    SettingRow(
                        setting.title, setting.summary, (setting as? ToggleSetting)?.isChecked,
                        enabled = setting.isEnabled,
                        first = index == 0 || visible[index - 1] is SettingsGroup,
                        last = index == visible.lastIndex || visible[index + 1] is SettingsGroup,
                        query = query.trim(),
                    ) {
                        if (setting is ToggleSetting) setting.requestChange(!setting.isChecked) { onChange(setting, it) }
                        else onAction(setting)
                    }
                }
            }
        }
    }
}
