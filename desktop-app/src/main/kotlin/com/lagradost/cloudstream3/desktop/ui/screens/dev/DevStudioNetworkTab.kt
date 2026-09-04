package com.lagradost.cloudstream3.desktop.ui.screens.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.common.net.NetworkRequestEntry

// -------------------------------------------------------------------------------------------------
// TAB 2: Network Inspector (Chrome DevTools Style)
// -------------------------------------------------------------------------------------------------

@Composable
internal fun NetworkInspectorTabContent(
    state: DevStudioUiState,
    onEvent: (DevStudioUiEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Network Toolbar
        Surface(
            color = Color(0xFF13151F),
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Search
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(DevBgDark, RoundedCornerShape(6.dp))
                            .border(1.dp, DevBorderDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (state.networkSearchQuery.isEmpty()) {
                                Text(
                                    "Filter URL, host, path, status...",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = state.networkSearchQuery,
                                onValueChange = { onEvent(DevStudioUiEvent.UpdateNetworkSearchQuery(it)) },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(DevAccentCyan),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Pause Network recording
                    Button(
                        onClick = { onEvent(DevStudioUiEvent.ToggleNetworkPause) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (state.isNetworkPaused) DevLevelWarn.copy(alpha = 0.25f) else Color(0xFF23273A)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(
                            if (state.isNetworkPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = if (state.isNetworkPaused) DevLevelWarn else Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.isNetworkPaused) "Resume" else "Pause", fontSize = 11.sp, color = Color.White)
                    }

                    // Export
                    OutlinedButton(
                        onClick = { onEvent(DevStudioUiEvent.ExportNetworkLogs) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text("Export", fontSize = 11.sp, color = Color.LightGray)
                    }

                    // Clear
                    OutlinedButton(
                        onClick = { onEvent(DevStudioUiEvent.ClearNetworkLogs) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text("Clear", fontSize = 11.sp, color = Color.LightGray)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Method filter chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Method:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    val methods = listOf(null, "GET", "POST", "HEAD", "PUT", "DELETE")
                    methods.forEach { m ->
                        val isSelected = state.networkMethodFilter == m
                        val label = m ?: "ALL"
                        Surface(
                            color = if (isSelected) DevAccentCyan.copy(alpha = 0.25f) else DevBgDark,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) DevAccentCyan else DevBorderDark),
                            modifier = Modifier.clickable { onEvent(DevStudioUiEvent.SelectNetworkMethodFilter(m)) },
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) DevAccentCyan else Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Surface(
                        color = if (state.networkErrorsOnly) DevLevelError else DevBgDark,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DevLevelError.copy(alpha = if (state.networkErrorsOnly) 1f else 0.5f)),
                        modifier = Modifier.clickable { onEvent(DevStudioUiEvent.ToggleNetworkErrorsOnly) },
                    ) {
                        Text(
                            "ERRORS ONLY (${state.networkErrorCount})",
                            color = if (state.networkErrorsOnly) Color.White else DevLevelError,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        // Main Table + Inspector Drawer
        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NetworkRequestsTable(
                    requests = state.networkRequests,
                    selectedRequest = state.selectedNetworkRequest,
                    onSelect = { onEvent(DevStudioUiEvent.SelectNetworkRequest(it)) },
                    onCopyCurl = { onEvent(DevStudioUiEvent.CopyCurlCommand(it)) },
                )
            }

            val req = state.selectedNetworkRequest
            if (state.isNetworkInspectorOpen && req != null) {
                Box(
                    modifier = Modifier
                        .width(460.dp)
                        .fillMaxHeight()
                        .background(DevSurfaceDark)
                        .border(1.dp, DevBorderDark),
                ) {
                    NetworkRequestInspector(
                        request = req,
                        onClose = { onEvent(DevStudioUiEvent.CloseNetworkInspector) },
                        onCopyCurl = { onEvent(DevStudioUiEvent.CopyCurlCommand(req)) },
                        onCopyPayload = { text, label -> onEvent(DevStudioUiEvent.CopyTextPayload(text, label)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkRequestsTable(
    requests: List<NetworkRequestEntry>,
    selectedRequest: NetworkRequestEntry?,
    onSelect: (NetworkRequestEntry) -> Unit,
    onCopyCurl: (NetworkRequestEntry) -> Unit,
) {
    if (requests.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No network traffic matching filters", color = Color.Gray, fontSize = 13.sp)
        }
    } else {
        SelectionContainer {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(requests, key = { it.id }) { req ->
                    NetworkRequestRow(
                        request = req,
                        isSelected = req.id == selectedRequest?.id,
                        onClick = { onSelect(req) },
                        onCopyCurl = { onCopyCurl(req) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkRequestRow(
    request: NetworkRequestEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onCopyCurl: () -> Unit,
) {
    val methodColor = when (request.method.uppercase()) {
        "GET" -> DevMethodGet
        "POST" -> DevMethodPost
        else -> DevMethodOther
    }

    val statusColor = when {
        request.isPending -> DevLevelWarn
        request.isSuccess -> DevLevelDebug
        request.isRedirect -> DevAccentCyan
        request.isClientError -> DevLevelWarn
        request.isServerError -> DevLevelError
        else -> Color.LightGray
    }

    val rowBg = if (isSelected) {
        Color(0xFF23283E)
    } else if (request.isError) {
        DevLevelError.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Status Code Pill
        Surface(
            color = statusColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
            modifier = Modifier.width(42.dp).height(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (request.statusCode == -1) "FAIL" else request.statusCode.toString(),
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // Method Pill
        Surface(
            color = methodColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.width(42.dp).height(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = request.method.uppercase(),
                    color = methodColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // URL Host & Path
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.url,
                color = if (request.isError) DevLevelError else Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (request.error != null) {
                Text(
                    text = "Error: ${request.error}",
                    color = DevLevelWarn,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Latency
        Text(
            text = "${request.durationMs}ms",
            color = if (request.durationMs > 2000) DevLevelWarn else Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(55.dp),
        )

        // Size
        Text(
            text = request.formattedSize,
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(60.dp),
        )

        // Timestamp
        Text(
            text = request.formattedTime,
            color = Color.DarkGray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )

        // Copy cURL button
        IconButton(onClick = onCopyCurl, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Default.Terminal, contentDescription = "Copy cURL", tint = Color.Gray, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun NetworkRequestInspector(
    request: NetworkRequestEntry,
    onClose: () -> Unit,
    onCopyCurl: () -> Unit,
    onCopyPayload: (String, String) -> Unit,
) {
    var selectedSection by remember { mutableStateOf(0) } // 0: Headers, 1: Request Body, 2: Response Body

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Request Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Summary Card
        Surface(
            color = DevBgDark,
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                InspectorField("URL", request.url)
                InspectorField("Method", request.method)
                InspectorField("Status", "${request.statusCode} ${request.statusMessage}")
                InspectorField("Latency", "${request.durationMs} ms")
                InspectorField("Size", request.formattedSize)
                request.contentType?.let {
                    InspectorField("Content-Type", it)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // cURL Button
        Button(
            onClick = onCopyCurl,
            colors = ButtonDefaults.buttonColors(containerColor = DevAccentCyan.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(32.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = DevAccentCyan, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy as cURL Command", color = DevAccentCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))

        // Section Tabs: Headers | Request Body | Response Body
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Headers", "Request Body", "Response Body").forEachIndexed { index, title ->
                val isSel = selectedSection == index
                Surface(
                    color = if (isSel) DevCardDark else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                    border = if (isSel) androidx.compose.foundation.BorderStroke(1.dp, DevAccentCyan.copy(alpha = 0.5f)) else null,
                    modifier = Modifier.weight(1f).clickable { selectedSection = index },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = title,
                            color = if (isSel) DevAccentCyan else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (selectedSection) {
            0 -> {
                // Headers Section
                Text("Request Headers (${request.requestHeaders.size}):", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    HeadersTable(request.requestHeaders)
                }

                Spacer(Modifier.height(12.dp))
                Text("Response Headers (${request.responseHeaders.size}):", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    HeadersTable(request.responseHeaders)
                }
            }
            1 -> {
                // Request Body Section
                val body = request.requestBody
                if (body.isNullOrBlank()) {
                    Text("No request body present", color = Color.Gray, fontSize = 11.sp)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Payload:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onCopyPayload(body, "Request Body") }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(13.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Surface(
                            color = DevBgDark,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = body,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
            2 -> {
                // Response Body Section
                val resp = request.responseBody
                if (resp.isNullOrBlank()) {
                    Text("No response body captured", color = Color.Gray, fontSize = 11.sp)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Response Data:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onCopyPayload(resp, "Response Body") }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(13.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Surface(
                            color = DevBgDark,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = resp,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadersTable(headers: Map<String, String>) {
    Surface(
        color = DevBgDark,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DevBorderDark),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (headers.isEmpty()) {
            Text("Empty headers", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
        } else {
            Column(modifier = Modifier.padding(8.dp)) {
                headers.forEach { (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(k, color = DevAccentCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.widthIn(max = 140.dp))
                        Text(v, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
