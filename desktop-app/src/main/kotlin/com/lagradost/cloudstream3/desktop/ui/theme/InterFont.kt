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

val LatoFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "Lato-Regular", data = loadFont("fonts/Lato-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "Lato-Medium", data = loadFont("fonts/Lato-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(identity = "Lato-SemiBold", data = loadFont("fonts/Lato-SemiBold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "Lato-Bold", data = loadFont("fonts/Lato-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val UbuntuFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "Ubuntu-Regular", data = loadFont("fonts/Ubuntu-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "Ubuntu-Medium", data = loadFont("fonts/Ubuntu-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(identity = "Ubuntu-Bold-sb", data = loadFont("fonts/Ubuntu-Bold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "Ubuntu-Bold", data = loadFont("fonts/Ubuntu-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val FiraSansFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "FiraSans-Regular", data = loadFont("fonts/FiraSans-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "FiraSans-Medium", data = loadFont("fonts/FiraSans-Medium.ttf"), weight = FontWeight.Medium, style = FontStyle.Normal),
        Font(identity = "FiraSans-SemiBold", data = loadFont("fonts/FiraSans-SemiBold.ttf"), weight = FontWeight.SemiBold, style = FontStyle.Normal),
        Font(identity = "FiraSans-Bold", data = loadFont("fonts/FiraSans-Bold.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val PacificoFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "Pacifico-Regular", data = loadFont("fonts/Pacifico-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "Pacifico-Bold", data = loadFont("fonts/Pacifico-Regular.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

val LobsterFontFamily: FontFamily by lazy {
    FontFamily(
        Font(identity = "Lobster-Regular", data = loadFont("fonts/Lobster-Regular.ttf"), weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(identity = "Lobster-Bold", data = loadFont("fonts/Lobster-Regular.ttf"), weight = FontWeight.Bold, style = FontStyle.Normal),
    )
}

// Font registry: name shown in Settings -> FontFamily

val availableFonts: List<String> = listOf("Inter", "Outfit", "DM Sans", "Roboto", "Nunito", "Poppins", "Lato", "Ubuntu", "Fira Sans", "Pacifico", "Lobster")

fun getFontFamily(name: String): FontFamily = when (name) {
    "Outfit" -> OutfitFontFamily
    "DM Sans" -> DMSansFontFamily
    "Roboto" -> RobotoFontFamily
    "Nunito" -> NunitoFontFamily
    "Poppins" -> PoppinsFontFamily
    "Lato" -> LatoFontFamily
    "Ubuntu" -> UbuntuFontFamily
    "Fira Sans" -> FiraSansFontFamily
    "Pacifico" -> PacificoFontFamily
    "Lobster" -> LobsterFontFamily
    else -> InterFontFamily // "Inter" is the default
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
val DesktopTypography: Typography by lazy { buildTypography(InterFontFamily) }
