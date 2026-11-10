package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = AmberGold,
  onPrimary = Color(0xFF1A1100),
  primaryContainer = AmberContainer,
  onPrimaryContainer = OnAmberContainer,
  secondary = StudioCyan,
  onSecondary = Color(0xFF002A36),
  secondaryContainer = CyanContainer,
  onSecondaryContainer = Color(0xFFBCEBFF),
  tertiary = AmberGoldLight,
  background = StudioBg,
  onBackground = TextPrimary,
  surface = StudioSurface,
  onSurface = TextPrimary,
  surfaceVariant = StudioCard,
  onSurfaceVariant = TextSecondary,
  outline = StudioBorder,
  outlineVariant = Color(0xFF1E232E)
)

@Composable
fun BooDaBeatTheme(
  content: @Composable () -> Unit,
) {
  // Enforce consistent high-performance studio console dark styling
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}
