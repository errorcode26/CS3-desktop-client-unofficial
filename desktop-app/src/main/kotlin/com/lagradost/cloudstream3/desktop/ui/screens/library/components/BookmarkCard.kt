package com.lagradost.cloudstream3.desktop.ui.screens.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.posterHoverEffect
import com.lagradost.common.storage.DesktopBookmark

private val CARD_TITLE_SCRIM_BRUSH = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to Color.Transparent,
        0.35f to Color.Black.copy(alpha = 0.7f),
        1f to Color.Black.copy(alpha = 0.92f),
    ),
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BookmarkCard(
    bookmark: DesktopBookmark,
    isProviderMissing: Boolean,
    onClick: () -> Unit,
    onSecondaryClick: (Rect) -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val posterCornerRadius by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.posterRoundingDp.collectAsState()
    val shape = remember(posterCornerRadius) { RoundedCornerShape(posterCornerRadius.dp) }
    val primary = MaterialTheme.colorScheme.primary
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (isHovered) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(if (isProviderMissing) Color(0xFFE65100).copy(alpha = 0.65f) else primary.copy(alpha = 0.65f), shape),
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .posterHoverEffect(shape)
                .clip(shape)
                .hoverable(interactionSource)
                .onGloballyPositioned { coords ->
                    bounds = coords.boundsInWindow()
                }
                .pointerInput(bookmark) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) {
                                if (event.button == PointerButton.Secondary) {
                                    onSecondaryClick(bounds)
                                } else if (event.button == PointerButton.Primary) {
                                    onClick()
                                }
                            }
                        }
                    }
                },
            shape = shape,
            color = DesktopUi.SurfaceCard,
            tonalElevation = if (isHovered) 8.dp else 2.dp,
            border = if (isHovered) androidx.compose.foundation.BorderStroke(2.dp, if (isProviderMissing) Color(0xFFFFA726) else MaterialTheme.colorScheme.primary) else null,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            ) {
                if (bookmark.posterUrl != null) {
                    val enhancedPoster = remember(bookmark.posterUrl) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhancePosterUrl(bookmark.posterUrl) }
                    AsyncImage(
                        model = enhancedPoster,
                        contentDescription = bookmark.name,
                        contentScale = ContentScale.Crop,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DesktopUi.SurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            bookmark.name.take(2).uppercase(),
                            color = DesktopUi.Accent,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                }

                AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.8f, animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isProviderMissing) Color(0xFFE65100).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f))
                            .border(1.dp, if (isProviderMissing) Color(0xFFFFA726) else Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isProviderMissing) Icons.Default.Sync else Icons.Default.PlayArrow,
                            contentDescription = if (isProviderMissing) "Re-link" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isHovered || isProviderMissing,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CARD_TITLE_SCRIM_BRUSH)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Column {
                            if (isProviderMissing) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE65100).copy(alpha = 0.90f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                    ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                        Text(
                                            text = "${bookmark.apiName} (Missing)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.25f))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = bookmark.apiName,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = bookmark.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
