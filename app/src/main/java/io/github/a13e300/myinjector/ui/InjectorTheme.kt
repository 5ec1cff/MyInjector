package io.github.a13e300.myinjector.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal fun injectorColorScheme(
    context: Context,
    dark: Boolean = context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
): ColorScheme = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
    dark -> darkColorScheme(
        primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
        background = Color(0xFF141218), surface = Color(0xFF141218),
        surfaceContainer = Color(0xFF211F26), surfaceContainerHigh = Color(0xFF2B2930),
    )
    else -> lightColorScheme(
        primary = Color(0xFF6750A4), onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
        background = Color(0xFFFEF7FF), surface = Color(0xFFFEF7FF),
        surfaceContainer = Color(0xFFF3EDF7), surfaceContainerHigh = Color(0xFFECE6F0),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InjectorTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = injectorColorScheme(LocalContext.current, isSystemInDarkTheme()),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
