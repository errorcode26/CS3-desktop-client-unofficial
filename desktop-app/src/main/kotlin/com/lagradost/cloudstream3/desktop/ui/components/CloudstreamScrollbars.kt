package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun defaultScrollbarStyle(): ScrollbarStyle {
    val isAmoled = LocalDesktopTheme.current.isAmoled
    return ScrollbarStyle(
        minimalHeight = 36.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        hoverDurationMillis = 200,
        unhoverColor = if (isAmoled) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
        hoverColor = if (isAmoled) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
    )
}

@Composable
fun DesktopVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    style: ScrollbarStyle = defaultScrollbarStyle(),
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        modifier = modifier,
        style = style,
    )
}

@Composable
fun DesktopVerticalScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    style: ScrollbarStyle = defaultScrollbarStyle(),
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(gridState),
        modifier = modifier,
        style = style,
    )
}

@Composable
fun DesktopVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    style: ScrollbarStyle = defaultScrollbarStyle(),
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier,
        style = style,
    )
}
