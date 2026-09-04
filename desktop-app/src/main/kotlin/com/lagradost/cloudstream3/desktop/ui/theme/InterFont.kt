package com.lagradost.cloudstream3.desktop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

private fun loadFont(path: String): ByteArray =
    Thread.currentThread().contextClassLoader
        ?.getResourceAsStream(path)
        ?.readBytes()
        ?: ClassLoader.getSystemResourceAsStream(path)!!.readBytes()

// Font Families

val InterFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "Inter-Regular", data = loadFont("fonts/Inter-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "Inter-Medium", data = loadFont("fonts/Inter-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(identity = "Inter-SemiBold", data = loadFont("fonts/Inter-SemiBold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "Inter-Bold", data = loadFont("fonts/Inter-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val OutfitFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "Outfit-Regular", data = loadFont("fonts/Outfit-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "Outfit-Medium", data = loadFont("fonts/Outfit-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(identity = "Outfit-SemiBold", data = loadFont("fonts/Outfit-SemiBold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "Outfit-Bold", data = loadFont("fonts/Outfit-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val DMSansFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "DMSans-Regular", data = loadFont("fonts/DMSans-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "DMSans-Medium", data = loadFont("fonts/DMSans-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(identity = "DMSans-SemiBold", data = loadFont("fonts/DMSans-SemiBold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "DMSans-Bold", data = loadFont("fonts/DMSans-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val RobotoFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "Roboto-Regular", data = loadFont("fonts/Roboto-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "Roboto-Medium", data = loadFont("fonts/Roboto-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        // Roboto has no SemiBold — map Bold for both SemiBold and Bold weights
        Font(identity = "Roboto-Bold-sb", data = loadFont("fonts/Roboto-Bold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "Roboto-Bold", data = loadFont("fonts/Roboto-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val NunitoFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "Nunito-Regular", data = loadFont("fonts/Nunito-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "Nunito-Medium", data = loadFont("fonts/Nunito-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(identity = "Nunito-SemiBold", data = loadFont("fonts/Nunito-SemiBold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "Nunito-Bold", data = loadFont("fonts/Nunito-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val PoppinsFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "Poppins-Regular", data = loadFont("fonts/Poppins-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "Poppins-Medium", data = loadFont("fonts/Poppins-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(identity = "Poppins-SemiBold", data = loadFont("fonts/Poppins-SemiBold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "Poppins-Bold", data = loadFont("fonts/Poppins-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val PlusJakartaSansFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "PlusJakartaSans-Regular", data = loadFont("fonts/PlusJakartaSans-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "PlusJakartaSans-Medium", data = loadFont("fonts/PlusJakartaSans-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(identity = "PlusJakartaSans-SemiBold", data = loadFont("fonts/PlusJakartaSans-SemiBold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "PlusJakartaSans-Bold", data = loadFont("fonts/PlusJakartaSans-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val ManropeFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "Manrope-Regular", data = loadFont("fonts/Manrope-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "Manrope-Medium", data = loadFont("fonts/Manrope-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(identity = "Manrope-SemiBold", data = loadFont("fonts/Manrope-SemiBold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "Manrope-Bold", data = loadFont("fonts/Manrope-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

// Font registry: name shown in Settings -> FontFamily

val availableFonts: List<String>
    get() = CustomFontManager.getAvailableFonts()

fun getFontFamily(name: String): FontFamily {
    if (name.isBlank() || name.equals("Plus Jakarta Sans", ignoreCase = true)) return PlusJakartaSansFontFamily

    // 1. Built-in curated fonts with multi-weight definitions loaded from app resources
    when (name) {
        "Manrope" -> return ManropeFontFamily
        "Outfit" -> return OutfitFontFamily
        "Inter" -> return InterFontFamily
        "DM Sans" -> return DMSansFontFamily
        "Poppins" -> return PoppinsFontFamily
        "Roboto" -> return RobotoFontFamily
        "Nunito" -> return NunitoFontFamily
    }

    // 2. User-installed custom font file from fonts directory
    val customFontFile = CustomFontManager.getFontFile(name)
    if (customFontFile != null) {
        try {
            return FontFamily(
                androidx.compose.ui.text.platform.Font(customFontFile, weight = FontWeight.Normal),
                androidx.compose.ui.text.platform.Font(customFontFile, weight = FontWeight.Medium),
                androidx.compose.ui.text.platform.Font(customFontFile, weight = FontWeight.SemiBold),
                androidx.compose.ui.text.platform.Font(customFontFile, weight = FontWeight.Bold),
                androidx.compose.ui.text.platform.Font(customFontFile, weight = FontWeight.ExtraBold),
                androidx.compose.ui.text.platform.Font(customFontFile, weight = FontWeight.Light),
                androidx.compose.ui.text.platform.Font(customFontFile, weight = FontWeight.Thin),
                androidx.compose.ui.text.platform.Font(customFontFile, weight = FontWeight.ExtraLight),
                androidx.compose.ui.text.platform.Font(customFontFile, weight = FontWeight.Black),
            )
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("Failed to load custom font: $name", e)
        }
    }

    // 3. System font fallback
    return try {
        FontFamily(androidx.compose.ui.text.platform.Font(name))
    } catch (e: Exception) {
        com.lagradost.common.logging.AppLogger.e("Failed to load system font: $name", e)
        PlusJakartaSansFontFamily
    }
}

// Typography builder - call with any FontFamily

fun buildTypography(fontFamily: FontFamily): Typography = Typography(
    displayLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
)

// Keep this for backward compatibility with anything that already references DesktopTypography
val DesktopTypography: Typography by lazy { buildTypography(PlusJakartaSansFontFamily) }
