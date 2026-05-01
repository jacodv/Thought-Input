package com.thoughtinput.capture.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ThoughtPrimary,
    onPrimary = ThoughtOnPrimary,
    primaryContainer = ThoughtPrimaryContainer,
    onPrimaryContainer = ThoughtOnPrimaryContainer,
    secondary = ThoughtSecondary,
    onSecondary = ThoughtOnSecondary,
    background = ThoughtBackground,
    onBackground = ThoughtOnBackground,
    surface = ThoughtSurface,
    onSurface = ThoughtOnSurface,
    surfaceVariant = ThoughtSurfaceVariant,
    onSurfaceVariant = ThoughtOnSurfaceVariant,
    surfaceContainerHigh = ThoughtSurfaceContainerHigh,
    outline = ThoughtOutline,
    error = ThoughtError,
    onError = ThoughtOnError
)

private val DarkColorScheme = darkColorScheme(
    primary = ThoughtPrimaryDark,
    onPrimary = ThoughtOnPrimaryDark,
    primaryContainer = ThoughtPrimaryContainerDark,
    onPrimaryContainer = ThoughtOnPrimaryContainerDark,
    secondary = ThoughtSecondaryDark,
    onSecondary = ThoughtOnSecondaryDark,
    background = ThoughtBackgroundDark,
    onBackground = ThoughtOnBackgroundDark,
    surface = ThoughtSurfaceDark,
    onSurface = ThoughtOnSurfaceDark,
    surfaceVariant = ThoughtSurfaceVariantDark,
    onSurfaceVariant = ThoughtOnSurfaceVariantDark,
    surfaceContainerHigh = ThoughtSurfaceContainerHighDark,
    outline = ThoughtOutlineDark,
    error = ThoughtErrorDark,
    onError = ThoughtOnErrorDark
)

@Composable
fun ThoughtInputTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ThoughtTypography,
        content = content
    )
}
