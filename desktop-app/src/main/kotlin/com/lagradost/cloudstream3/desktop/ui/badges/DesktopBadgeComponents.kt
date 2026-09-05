package com.lagradost.cloudstream3.desktop.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Badges for media cards and detail headers.
 */
object DesktopBadgeComponents {

    private val GoldStar = Color(0xFFFBBF24)
    private val GoldText = Color(0xFFFEF08A)
    private val GoldBorder = Color(0x60FBBF24)

    private val GlassBg = Color.Black.copy(alpha = 0.55f)
    private val GlassBorder = Color.White.copy(alpha = 0.16f)
    private val TextSilver = Color(0xFFF1F5F9)

    private val BadgeShape = RoundedCornerShape(4.dp)

    @Composable
    fun RatingGoldBadge(
        rating: Double,
        modifier: Modifier = Modifier,
    ) {
        if (rating <= 0.0) return

        val formatted = if (rating >= 10.0) "10" else String.format(Locale.US, "%.1f", rating)

        Box(
            modifier = modifier
                .clip(BadgeShape)
                .background(GlassBg)
                .border(0.5.dp, GoldBorder, BadgeShape)
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = GoldStar,
                    modifier = Modifier.size(9.5.dp),
                )
                Text(
                    text = formatted,
                    color = GoldText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }

    @Composable
    fun SubDubBadge(
        hasSub: Boolean,
        hasDub: Boolean,
        modifier: Modifier = Modifier,
    ) {
        if (!hasSub && !hasDub) return

        val text = when {
            hasSub && hasDub -> "SUB • DUB"
            hasSub -> "SUB"
            else -> "DUB"
        }

        Box(
            modifier = modifier
                .clip(BadgeShape)
                .background(GlassBg)
                .border(0.5.dp, GlassBorder, BadgeShape)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = TextSilver,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            )
        }
    }

    @Composable
    fun QualityBadge(
        quality: String?,
        modifier: Modifier = Modifier,
    ) {
        if (quality.isNullOrBlank()) return

        val is4k = quality.equals("4K", ignoreCase = true) || quality.equals("2160p", ignoreCase = true) || quality.contains("UHD", ignoreCase = true)
        val text = if (is4k) "4K UHD" else if (quality.contains("1080", ignoreCase = true)) "1080p" else quality

        val borderColor = if (is4k) GoldBorder else GlassBorder
        val textColor = if (is4k) GoldText else TextSilver

        Box(
            modifier = modifier
                .clip(BadgeShape)
                .background(GlassBg)
                .border(0.5.dp, borderColor, BadgeShape)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            )
        }
    }

    fun normalizeContentRating(raw: String?, isSeries: Boolean = false): String? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim().uppercase()
            .replace("_", "-")
            .replace(" ", "-")

        return when {
            // TV classification ratings
            clean in listOf("TV-MA", "TVMA", "MA") -> "TV-MA"
            clean in listOf("TV-14", "TV14", "14") -> if (isSeries) "TV-14" else "14+"
            clean in listOf("TV-PG", "TVPG") -> "TV-PG"
            clean in listOf("TV-G", "TVG") -> "TV-G"
            clean in listOf("TV-Y7", "TVY7", "Y7") -> "TV-Y7"
            clean in listOf("TV-Y", "TVY") -> "TV-Y"

            // Movie / MPAA ratings
            clean == "R" -> "Rated R"
            clean in listOf("PG-13", "PG13") -> "PG-13"
            clean == "PG" -> "PG"
            clean == "G" -> "G"
            clean in listOf("NC-17", "NC17") -> "NC-17"

            // International age ratings (18+, 16+, 13+, 12+, 6+)
            clean in listOf("18", "18+", "+18", "R18", "R18+", "R-18") -> "18+"
            clean in listOf("16", "16+", "+16") -> "16+"
            clean in listOf("13", "13+", "+13") -> "13+"
            clean in listOf("12", "12+", "+12") -> "12+"
            clean in listOf("6", "6+", "+6") -> "6+"

            // Filter out redundant unrated flags to avoid visual noise
            clean in listOf("NR", "NOT-RATED", "UNRATED", "NONE", "UNKNOWN") -> null
            else -> clean
        }
    }

    @Composable
    fun ContentRatingBadge(
        rating: String?,
        modifier: Modifier = Modifier,
        isLarge: Boolean = false,
        isSeries: Boolean = false,
    ) {
        val cleanRating = normalizeContentRating(rating, isSeries) ?: return
        val shape = RoundedCornerShape(if (isLarge) 5.dp else 4.dp)

        Box(
            modifier = modifier
                .clip(shape)
                .background(GlassBg)
                .border(0.5.dp, GlassBorder, shape)
                .padding(
                    horizontal = if (isLarge) 6.dp else 4.5.dp,
                    vertical = if (isLarge) 2.dp else 1.5.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cleanRating,
                color = TextSilver,
                fontSize = if (isLarge) 11.5.sp else 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            )
        }
    }

    private val badgeBitmapCache = ConcurrentHashMap<String, ImageBitmap>()

    @Composable
    fun rememberSharpBadgePainter(resourcePath: String): Painter {
        val density = LocalDensity.current
        return remember(resourcePath, density) {
            if (resourcePath.endsWith(".svg", ignoreCase = true)) {
                runCatching {
                    useResource(resourcePath) { stream ->
                        loadSvgPainter(stream, density)
                    }
                }.getOrElse { ColorPainter(Color.Transparent) }
            } else {
                val bitmap = badgeBitmapCache[resourcePath] ?: runCatching {
                    useResource(resourcePath) { stream ->
                        loadImageBitmap(stream)
                    }
                }.getOrNull()?.also { badgeBitmapCache[resourcePath] = it }
                if (bitmap != null) {
                    BitmapPainter(
                        image = bitmap,
                        filterQuality = FilterQuality.Medium,
                    )
                } else {
                    ColorPainter(Color.Transparent)
                }
            }
        }
    }

    @Composable
    fun BrandedRatingBadge(
        logoRes: String,
        scoreText: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        logoWidth: androidx.compose.ui.unit.Dp = 36.dp,
        logoHeight: androidx.compose.ui.unit.Dp = 18.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            androidx.compose.foundation.Image(
                painter = rememberSharpBadgePainter(logoRes),
                contentDescription = null,
                modifier = Modifier.size(width = logoWidth, height = logoHeight),
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = scoreText,
                color = textColor,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.sp,
            )
        }
    }

    @Composable
    private fun SingleGlassBadge(
        text: String,
        textColor: Color,
        borderColor: Color,
        modifier: Modifier = Modifier,
    ) {
        Box(
            modifier = modifier
                .clip(BadgeShape)
                .background(GlassBg)
                .border(0.5.dp, borderColor, BadgeShape)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            )
        }
    }
}
