package com.remotebot.app.ui.theme

import androidx.compose.ui.graphics.Color

// Primary tones (Teal / Cyber-Forest palette for remote bot)
val PrimaryLight = Color(0xFF006C4C)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFF8CF8C7)
val OnPrimaryContainerLight = Color(0xFF002114)

val PrimaryDark = Color(0xFF6FDBAC)
val OnPrimaryDark = Color(0xFF003825)
val PrimaryContainerDark = Color(0xFF005138)
val OnPrimaryContainerDark = Color(0xFF8CF8C7)

// Neutral surfaces
val SurfaceLight = Color(0xFFFBFDFA)
val OnSurfaceLight = Color(0xFF191C1B)
val SurfaceVariantLight = Color(0xFFDBE5DF)
val OnSurfaceVariantLight = Color(0xFF3F4945)

val SurfaceDark = Color(0xFF191C1B)
val OnSurfaceDark = Color(0xFFE1E3DF)
val SurfaceVariantDark = Color(0xFF3F4945)
val OnSurfaceVariantDark = Color(0xFFBFC9C3)

// Semantic colors across all screens
object AppColors {
    val success = Color(0xFF2E7D32)
    val successLight = Color(0xFF4CAF50)
    val successContainerLight = Color(0xFFE8F5E9)
    val successContainerDark = Color(0xFF1B5E20)
    val onSuccessContainerLight = Color(0xFF1B5E20)
    val onSuccessContainerDark = Color(0xFFC8E6C9)

    val warning = Color(0xFFE65100)
    val warningLight = Color(0xFFFF9800)
    val warningContainerLight = Color(0xFFFFF3E0)
    val warningContainerDark = Color(0xFFE65100)

    val info = Color(0xFF1976D2)
    val infoLight = Color(0xFF2196F3)
    val infoContainerLight = Color(0xFFE3F2FD)
    val infoContainerDark = Color(0xFF0D47A1)

    val error = Color(0xFFC62828)
    val errorContainerLight = Color(0xFFFFEBEE)
    val errorContainerDark = Color(0xFF8C1D18)

    // Log console colors
    val logTrace = Color(0xFFBA68C8)
    val logDebug = Color(0xFF64B5F6)
    val logInfo = Color(0xFF81C784)
    val logWarn = Color(0xFFFFB74D)
    val logError = Color(0xFFEF5350)
    val logConsoleBackground = Color(0xFF121212)
}
