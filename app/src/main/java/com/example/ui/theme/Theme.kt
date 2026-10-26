package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VelourDarkColorScheme = darkColorScheme(
    primary = VelourRoseDark,
    onPrimary = VelourOnRoseDark,
    primaryContainer = VelourRoseContainerDark,
    onPrimaryContainer = VelourOnRoseContainerDark,
    secondary = VelourPlumDark,
    onSecondary = VelourOnPlumDark,
    secondaryContainer = VelourPlumContainerDark,
    onSecondaryContainer = VelourOnPlumContainerDark,
    tertiary = VelourGoldDark,
    onTertiary = VelourOnGoldDark,
    tertiaryContainer = VelourGoldContainerDark,
    onTertiaryContainer = VelourOnGoldContainerDark,
    background = VelourBackgroundDark,
    onBackground = VelourOnBackgroundDark,
    surface = VelourSurfaceDark,
    onSurface = VelourOnSurfaceDark,
    surfaceVariant = VelourSurfaceVariantDark,
    onSurfaceVariant = VelourOnSurfaceVariantDark,
    outline = VelourOutlineDark
)

private val VelourLightColorScheme = lightColorScheme(
    primary = VelourRoseLight,
    onPrimary = VelourOnRoseLight,
    primaryContainer = VelourRoseContainerLight,
    onPrimaryContainer = VelourOnRoseContainerLight,
    secondary = VelourPlumLight,
    onSecondary = VelourOnPlumLight,
    secondaryContainer = VelourPlumContainerLight,
    onSecondaryContainer = VelourOnPlumContainerLight,
    tertiary = VelourGoldLight,
    onTertiary = VelourOnGoldLight,
    tertiaryContainer = VelourGoldContainerLight,
    onTertiaryContainer = VelourOnGoldContainerLight,
    background = VelourBackgroundLight,
    onBackground = VelourOnBackgroundLight,
    surface = VelourSurfaceLight,
    onSurface = VelourOnSurfaceLight,
    surfaceVariant = VelourSurfaceVariantLight,
    onSurfaceVariant = VelourOnSurfaceVariantLight,
    outline = VelourOutlineLight
)

val VelourShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun VelourTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) VelourDarkColorScheme else VelourLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VelourShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    VelourTheme(darkTheme = darkTheme, content = content)
}
