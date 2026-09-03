package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.desktop.metadata.MetadataConfig
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany

@OptIn(ExperimentalLayoutApi::class)
fun hasDetailsStats(
    uiState: DetailsUiState?,
    data: LoadResponse? = null,
): Boolean {
    if (uiState == null && data == null) return false
    val budget = uiState?.enrichedBudget
    val revenue = uiState?.enrichedRevenue
    val country = uiState?.enrichedCountry
    val lang = uiState?.enrichedOriginalLanguage
    val relDate = uiState?.enrichedReleaseDate ?: data?.year?.toString()
    val status = uiState?.enrichedStatus ?: (data as? TvSeriesLoadResponse)?.showStatus?.name ?: (data as? AnimeLoadResponse)?.showStatus?.name
    val cert = data?.contentRating
    val dur = data?.duration
    val seasons = uiState?.enrichedSeasonsCount
    val episodes = uiState?.enrichedEpisodesCount

    return budget != null || revenue != null ||
        !country.isNullOrBlank() || !lang.isNullOrBlank() || !status.isNullOrBlank() ||
        !relDate.isNullOrBlank() || !cert.isNullOrBlank() || (dur ?: 0) > 0 ||
        (seasons ?: 0) > 0 || (episodes ?: 0) > 0
}

fun hasStudiosOrNetworks(
    uiState: DetailsUiState?,
): Boolean {
    return uiState?.enrichedProductionCompanies?.isNotEmpty() == true ||
        uiState?.enrichedNetworksList?.isNotEmpty() == true ||
        uiState?.enrichedStudios?.isNotEmpty() == true ||
        uiState?.enrichedNetworks?.isNotEmpty() == true
}

@Composable
fun DetailsStatsSection(
    data: LoadResponse,
    uiState: DetailsUiState?,
    modifier: Modifier = Modifier,
) {
    val budget = uiState?.enrichedBudget
    val revenue = uiState?.enrichedRevenue
    val country = uiState?.enrichedCountry
    val lang = uiState?.enrichedOriginalLanguage
    val relDate = uiState?.enrichedReleaseDate ?: data.year?.toString()
    val status = uiState?.enrichedStatus ?: (data as? TvSeriesLoadResponse)?.showStatus?.name ?: (data as? AnimeLoadResponse)?.showStatus?.name
    val cert = data.contentRating
    val seasons = uiState?.enrichedSeasonsCount
    val episodes = uiState?.enrichedEpisodesCount

    val dur = data.duration
    val runtimeStr = if (dur != null && dur > 0) {
        val mins = if (dur > 360) dur / 60 else dur
        if (mins >= 60) {
            val h = mins / 60
            val m = mins % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        } else {
            "${mins}m"
        }
    } else null

    val detailRows = remember(data, uiState, budget, revenue, country, lang, relDate, status, cert, runtimeStr, seasons, episodes) {
        val list = mutableListOf<Pair<String, String>>()
        if (!status.isNullOrBlank()) {
            list.add("Status" to status)
        }
        if (!relDate.isNullOrBlank()) {
            list.add("Release Info" to relDate)
        }
        if (!runtimeStr.isNullOrBlank()) {
            list.add("Runtime" to runtimeStr)
        }
        if (!cert.isNullOrBlank()) {
            list.add("Certification" to cert)
        }
        if (!country.isNullOrBlank()) {
            list.add("Origin Country" to country)
        }
        if (!lang.isNullOrBlank()) {
            list.add("Original Language" to lang)
        }
        if (seasons != null && seasons > 0) {
            val epStr = if (episodes != null && episodes > 0) " ($episodes Episodes)" else ""
            list.add("Seasons" to "$seasons ${if (seasons == 1) "Season" else "Seasons"}$epStr")
        }
        if (budget != null && budget > 0) {
            list.add("Budget" to formatCurrency(budget))
        }
        if (revenue != null && revenue > 0) {
            list.add("Box Office" to formatCurrency(revenue))
        }
        list
    }

    if (detailRows.isNotEmpty()) {
        Column(
            modifier = modifier.widthIn(max = 920.dp).fillMaxWidth(),
        ) {
            val half = (detailRows.size + 1) / 2
            val leftCol = detailRows.take(half)
            val rightCol = detailRows.drop(half)

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isTwoColumns = maxWidth >= 600.dp
                if (isTwoColumns) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            leftCol.forEachIndexed { index, (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                    )
                                }
                                if (index < leftCol.lastIndex) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.08f),
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            rightCol.forEachIndexed { index, (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                    )
                                }
                                if (index < rightCol.lastIndex) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.08f),
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        detailRows.forEachIndexed { index, (label, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                )
                            }
                            if (index < detailRows.lastIndex) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.08f),
                                    thickness = 0.5.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsStudiosSection(
    uiState: DetailsUiState?,
    modifier: Modifier = Modifier,
    onCompanyClick: ((ProductionCompany) -> Unit)? = null,
) {
    val separateNetworksPref by MetadataConfig.separateNetworks.collectAsState()

    val netCompanies = remember(uiState) {
        val list = mutableListOf<ProductionCompany>()
        if (uiState != null) {
            list.addAll(uiState.enrichedNetworksList)
            if (list.isEmpty()) {
                list.addAll(uiState.enrichedNetworks.map { ProductionCompany(name = it) })
            }
        }
        list.distinctBy { it.name.trim().lowercase() }
    }

    val prodCompanies = remember(uiState) {
        val list = mutableListOf<ProductionCompany>()
        if (uiState != null) {
            list.addAll(uiState.enrichedProductionCompanies)
            if (list.isEmpty()) {
                list.addAll(uiState.enrichedStudios.map { ProductionCompany(name = it) })
            }
        }
        list.distinctBy { it.name.trim().lowercase() }
    }

    val allCompanies = remember(netCompanies, prodCompanies) {
        (prodCompanies + netCompanies).distinctBy { it.name.trim().lowercase() }
    }

    if (allCompanies.isNotEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            if (separateNetworksPref && netCompanies.isNotEmpty() && prodCompanies.isNotEmpty()) {
                // Subsection 1: Broadcast Networks
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "BROADCAST NETWORKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        netCompanies.forEach { company ->
                            ProductionCompanyCard(
                                company = company,
                                onClick = if (onCompanyClick != null) { { onCompanyClick(company) } } else null,
                            )
                        }
                    }
                }

                // Subsection 2: Production Studios
                Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Text(
                        text = "PRODUCTION STUDIOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        prodCompanies.forEach { company ->
                            ProductionCompanyCard(
                                company = company,
                                onClick = if (onCompanyClick != null) { { onCompanyClick(company) } } else null,
                            )
                        }
                    }
                }
            } else {
                val sectionTitle = if (netCompanies.isNotEmpty() && prodCompanies.isEmpty()) "BROADCAST NETWORKS" else if (prodCompanies.isNotEmpty() && netCompanies.isEmpty()) "PRODUCTION STUDIOS" else "STUDIOS & NETWORKS"
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        allCompanies.forEach { company ->
                            ProductionCompanyCard(
                                company = company,
                                onClick = if (onCompanyClick != null) { { onCompanyClick(company) } } else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductionCompanyCard(
    company: ProductionCompany,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val hasLogo = !company.logoUrl.isNullOrBlank()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isHovered && onClick != null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isHovered && onClick != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f),
        ),
        modifier = modifier
            .height(84.dp)
            .widthIn(min = 200.dp, max = 320.dp)
            .hoverable(interactionSource)
            .run {
                if (onClick != null) {
                    this.clickable(interactionSource = interactionSource, indication = null) { onClick() }
                } else {
                    this
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (hasLogo) {
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.96f))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = company.logoUrl,
                        contentDescription = company.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = company.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!company.originCountry.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = company.originCountry.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
        }
    }
}

private fun formatCurrency(amount: Long): String {
    return when {
        amount >= 1_000_000_000 -> "$${String.format("%.1f", amount.toDouble() / 1_000_000_000)} Billion"
        amount >= 1_000_000 -> "$${String.format("%.1f", amount.toDouble() / 1_000_000)} Million"
        else -> "$${java.text.NumberFormat.getIntegerInstance().format(amount)}"
    }
}
