@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.a13e300.myinjector.ui.settings

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.*

@Composable
internal fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        shape = RoundedCornerShape(50),
        placeholder = { Text(hint) },
        leadingIcon = { Icon(MaterialSymbols.RoundedFilled.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                Icon(MaterialSymbols.RoundedFilled.Close, "清除搜索")
            }
        },
    )
}

@Composable
internal fun SettingRow(
    title: CharSequence,
    summary: CharSequence? = null,
    checked: Boolean? = null,
    enabled: Boolean = true,
    first: Boolean = true,
    last: Boolean = true,
    query: String = "",
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) 24.dp else 4.dp,
        topEnd = if (first) 24.dp else 4.dp,
        bottomStart = if (last) 24.dp else 4.dp,
        bottomEnd = if (last) 24.dp else 4.dp,
    )
    val colors = MaterialTheme.colorScheme
    val modifier = if (checked != null) {
        Modifier.toggleable(checked, enabled = enabled, role = Role.Switch) { onClick() }
    } else Modifier
    Surface(
        shape = shape, color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            modifier = if (checked != null) modifier else Modifier.clickable(enabled = enabled, onClick = onClick),
            content = {
                Text(
                    highlighted(title, query, colors.primary), style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                )
            },
            supportingContent = summary?.takeIf { it.isNotBlank() }?.let { text ->
                {
                    Text(
                        highlighted(text, query, colors.primary),
                        color = (if (text.contains("存在问题")) colors.error else colors.onSurfaceVariant)
                            .copy(alpha = if (enabled) 1f else 0.38f),
                    )
                }
            },
            trailingContent = {
                if (checked != null) Switch(checked, onCheckedChange = null, enabled = enabled)
                else Icon(MaterialSymbols.RoundedFilled.Chevron_right, null, tint = colors.onSurfaceVariant)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun highlighted(text: CharSequence, query: String, accent: Color): AnnotatedString =
    buildAnnotatedString {
        append(text.toString())
        if (query.isNotBlank()) {
            var start = text.indexOf(query, ignoreCase = true)
            while (start >= 0) {
                addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), start, start + query.length)
                start = text.indexOf(query, start + query.length, ignoreCase = true)
            }
        }
    }
