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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Premium, cinematic studio format badges.
 * Combines deep glassmorphic backdrops, glowing micro-rims, and distinctive typography.
 */
object DesktopBadgeComponents {

    // Language Accents (Ice Cyan for SUB, Royal Orchid for DUB)
    private val IceCyan = Color(0xFF38BDF8)
    private val OrchidViolet = Color(0xFFC084FC)

    // Rating & 4K Gold Palette
    private val GoldStar = Color(0xFFFFB800)
    private val GoldText = Color(0xFFFFE082)
    private val GoldBorder = Color(0xFFFFB800).copy(alpha = 0.50f)
    private val GoldGlassBg = Brush.verticalGradient(
        listOf(
            Color(0xDD1C1504),
            Color(0xF00F0B02),
        )
    )

    // Standard Neutral Slate Palette
    private val SlateGlassBg = Color(0xCC0F172A)
    private val SlateBorder = Color(0x4094A3B8)
    private val TextSilver = Color(0xFFE2E8F0)

    private val BadgeShape = RoundedCornerShape(5.5.dp)

    @Composable
    fun RatingGoldBadge(
        rating: Double,
        modifier: Modifier = Modifier,
    ) {
        if (rating <= 0.0) return

        val formatted = if (rating >= 10.0) "10" else String.format(Locale.US, "%.1f", rating)

        Box(
            modifier = modifier
                .shadow(elevation = 3.dp, shape = BadgeShape)
                .clip(BadgeShape)
                .background(GoldGlassBg)
                .border(0.8.dp, GoldBorder, BadgeShape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.5.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = GoldStar,
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = formatted,
                    color = GoldText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
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

        if (hasSub && hasDub) {
            // Dual-Tone Glass Split Capsule
            Box(
                modifier = modifier
                    .shadow(elevation = 3.dp, shape = BadgeShape)
                    .clip(BadgeShape)
                    .background(SlateGlassBg)
                    .border(
                        0.8.dp,
                        Brush.horizontalGradient(
                            listOf(
                                IceCyan.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.15f),
                                OrchidViolet.copy(alpha = 0.45f),
                            )
                        ),
                        BadgeShape,
                    )
                    .padding(horizontal = 5.5.dp, vertical = 2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                ) {
                    Text(
                        text = "SUB",
                        color = IceCyan,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp,
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(8.dp)
                            .background(Color.White.copy(alpha = 0.30f))
                    )
                    Text(
                        text = "DUB",
                        color = OrchidViolet,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp,
                    )
                }
            }
        } else if (hasSub) {
            SingleGlassBadge(
                text = "SUB",
                textColor = IceCyan,
                borderColor = IceCyan.copy(alpha = 0.45f),
                modifier = modifier,
            )
        } else {
            SingleGlassBadge(
                text = "DUB",
                textColor = OrchidViolet,
                borderColor = OrchidViolet.copy(alpha = 0.45f),
                modifier = modifier,
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

        if (is4k) {
            Box(
                modifier = modifier
                    .shadow(elevation = 3.dp, shape = BadgeShape)
                    .clip(BadgeShape)
                    .background(GoldGlassBg)
                    .border(0.8.dp, GoldBorder, BadgeShape)
                    .padding(horizontal = 5.5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = text,
                    color = GoldText,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp,
                )
            }
        } else {
            SingleGlassBadge(
                text = text,
                textColor = TextSilver,
                borderColor = SlateBorder,
                modifier = modifier,
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
                .shadow(elevation = 3.dp, shape = BadgeShape)
                .clip(BadgeShape)
                .background(SlateGlassBg)
                .border(0.8.dp, borderColor, BadgeShape)
                .padding(horizontal = 5.5.dp, vertical = 2.dp),
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp,
            )
        }
    }
}
