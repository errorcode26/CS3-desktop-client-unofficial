package com.nuvio.app.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun NuvioDesktopVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier,
) = Unit

@Composable
internal actual fun NuvioDesktopVerticalScrollbar(
    state: LazyGridState,
    modifier: Modifier,
) = Unit

@Composable
internal actual fun NuvioDesktopVerticalScrollbar(
    state: ScrollState,
    modifier: Modifier,
) = Unit
