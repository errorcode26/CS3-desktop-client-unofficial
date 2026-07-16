package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

fun getCountryCode(languageCode: String?): String? {
    if (languageCode.isNullOrBlank() || languageCode == "none" || languageCode == "any") return null
    return when (languageCode.lowercase()) {
        "en" -> "gb"
        "hi" -> "in"
        "ar" -> "sa"
        "zh" -> "cn"
        "ja" -> "jp"
        "ko" -> "kr"
        "vi" -> "vn"
        "ur" -> "pk"
        "es" -> "es"
        "pt" -> "pt"
        "fr" -> "fr"
        "de" -> "de"
        "it" -> "it"
        "tr" -> "tr"
        "ru" -> "ru"
        "th" -> "th"
        "id" -> "id"
        "uk" -> "ua"
        "bn" -> "bd"
        "tl", "fil" -> "ph"
        "ro" -> "ro"
        "ml", "ta", "te", "kn", "mr", "gu", "pa" -> "in"
        "az" -> "az"
        "mx" -> "mx"
        else -> {
            val code = languageCode.lowercase()
            if (code.length == 2) code else null
        }
    }
}

@Composable
fun FlagImage(languageCode: String?, modifier: Modifier = Modifier) {
    val countryCode = getCountryCode(languageCode)
    if (countryCode != null) {
        coil3.compose.AsyncImage(
            model = "https://flagcdn.com/w40/$countryCode.png",
            contentDescription = "Flag",
            modifier = modifier.height(16.dp).widthIn(max = 24.dp).clip(RoundedCornerShape(2.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Icon(
            imageVector = Icons.Default.Public,
            contentDescription = "Globe",
            modifier = modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
