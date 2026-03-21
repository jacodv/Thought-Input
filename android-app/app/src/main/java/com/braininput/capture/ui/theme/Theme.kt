package com.braininput.capture.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = BrainPrimary,
    onPrimary = BrainOnPrimary,
    primaryContainer = BrainPrimaryContainer,
    secondary = BrainSecondary,
    background = BrainBackground,
    surface = BrainSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = BrainPrimaryDark,
    onPrimary = BrainOnPrimaryDark,
    primaryContainer = BrainPrimaryContainerDark,
    secondary = BrainSecondaryDark,
    background = BrainBackgroundDark,
    surface = BrainSurfaceDark
)

@Composable
fun BrainInputTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BrainTypography,
        content = content
    )
}
