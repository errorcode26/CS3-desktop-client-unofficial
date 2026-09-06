package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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

fun hasNetworks(
    uiState: DetailsUiState?,
): Boolean {
    if (!MetadataConfig.separateNetworks.value) return false
    return uiState?.enrichedNetworksList?.isNotEmpty() == true ||
        uiState?.enrichedNetworks?.isNotEmpty() == true
}

fun hasStudios(
    uiState: DetailsUiState?,
): Boolean {
    if (!MetadataConfig.separateNetworks.value) {
        return hasStudiosOrNetworks(uiState)
    }
    return uiState?.enrichedProductionCompanies?.isNotEmpty() == true ||
        uiState?.enrichedStudios?.isNotEmpty() == true
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

    val isTv = data.type == com.lagradost.cloudstream3.TvType.TvSeries ||
        data.type == com.lagradost.cloudstream3.TvType.Anime ||
        data.type == com.lagradost.cloudstream3.TvType.AsianDrama ||
        data.type == com.lagradost.cloudstream3.TvType.Cartoon ||
        data is TvSeriesLoadResponse ||
        data is AnimeLoadResponse

    val detailRows = remember(data, uiState, budget, revenue, country, lang, relDate, status, cert, runtimeStr, seasons, episodes, isTv) {
        val list = mutableListOf<Pair<String, String>>()
        if (!status.isNullOrBlank()) {
            list.add("Status" to status)
        }
        if (!relDate.isNullOrBlank()) {
            val dateLabel = if (isTv) "First Aired" else "Release Date"
            list.add(dateLabel to formatReleaseDate(relDate))
        }
        if (!runtimeStr.isNullOrBlank()) {
            val runtimeLabel = if (isTv) "Episode Runtime" else "Runtime"
            val runtimeVal = if (isTv) "~$runtimeStr / ep" else runtimeStr
            list.add(runtimeLabel to runtimeVal)
        }
        if (!cert.isNullOrBlank()) {
            list.add("Certification" to cert)
        }
        if (!country.isNullOrBlank()) {
            list.add("Origin Country" to formatCountry(country))
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

private fun consolidateSubBrands(companies: List<ProductionCompany>): List<ProductionCompany> {
    if (companies.size <= 1) return companies
    val toRemove = mutableSetOf<ProductionCompany>()
    for (i in companies.indices) {
        for (j in companies.indices) {
            if (i == j) continue
            val a = companies[i]
            val b = companies[j]
            val nameA = a.name.trim()
            val nameB = b.name.trim()
            val isElaboration = nameB.startsWith("$nameA ", ignoreCase = true) ||
                nameB.startsWith("$nameA -", ignoreCase = true) ||
                nameB.startsWith("$nameA:", ignoreCase = true)
            if (isElaboration) {
                if (!b.logoUrl.isNullOrBlank() || a.logoUrl.isNullOrBlank()) {
                    toRemove.add(a)
                } else {
                    toRemove.add(b)
                }
            }
        }
    }
    return companies.filterNot { it in toRemove }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductionCompanySectionLayout(
    title: String,
    companies: List<ProductionCompany>,
    modifier: Modifier = Modifier,
    onCompanyClick: ((ProductionCompany) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val canExpand = companies.size > 5
    val visibleCompanies = if (expanded || !canExpand) companies else companies.take(4)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
            )

            if (canExpand) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.08f),
                    ),
                    modifier = Modifier.clickable { expanded = !expanded },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (expanded) "Show less" else "Show all (${companies.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            visibleCompanies.forEach { company ->
                ProductionCompanyCard(
                    company = company,
                    onClick = if (onCompanyClick != null) { { onCompanyClick(company) } } else null,
                )
            }
        }
    }
}

@Composable
fun DetailsNetworksSection(
    uiState: DetailsUiState?,
    modifier: Modifier = Modifier,
    onCompanyClick: ((ProductionCompany) -> Unit)? = null,
) {
    val netCompanies = remember(uiState) {
        val list = mutableListOf<ProductionCompany>()
        if (uiState != null) {
            list.addAll(uiState.enrichedNetworksList)
            if (list.isEmpty()) {
                list.addAll(uiState.enrichedNetworks.map { ProductionCompany(name = it) })
            }
        }
        list.distinctBy { it.name.trim().lowercase() }
            .sortedWith(compareByDescending<ProductionCompany> { !it.logoUrl.isNullOrBlank() }.thenBy { it.name })
    }

    if (netCompanies.isEmpty()) return

    ProductionCompanySectionLayout(
        title = "BROADCAST NETWORKS",
        companies = netCompanies,
        modifier = modifier,
        onCompanyClick = onCompanyClick,
    )
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

    val prodCompanies = remember(uiState, netCompanies) {
        val list = mutableListOf<ProductionCompany>()
        if (uiState != null) {
            list.addAll(uiState.enrichedProductionCompanies)
            if (list.isEmpty()) {
                list.addAll(uiState.enrichedStudios.map { ProductionCompany(name = it) })
            }
        }
        val distinct = list.distinctBy { it.name.trim().lowercase() }

        // Cross-category deduplication: Exclude companies that are already listed as broadcast networks
        val netNames = netCompanies.map { it.name.trim().lowercase() }.toSet()
        val netIds = netCompanies.mapNotNull { if (it.id > 0) it.id else null }.toSet()
        val nonNetwork = distinct.filterNot { comp ->
            (comp.id > 0 && comp.id in netIds) || comp.name.trim().lowercase() in netNames
        }

        consolidateSubBrands(nonNetwork)
            .sortedWith(compareByDescending<ProductionCompany> { !it.logoUrl.isNullOrBlank() }.thenBy { it.name })
    }

    val displayCompanies = remember(separateNetworksPref, netCompanies, prodCompanies) {
        if (!separateNetworksPref && netCompanies.isNotEmpty()) {
            (prodCompanies + netCompanies).distinctBy { it.name.trim().lowercase() }
                .sortedWith(compareByDescending<ProductionCompany> { !it.logoUrl.isNullOrBlank() }.thenBy { it.name })
        } else {
            prodCompanies
        }
    }

    if (displayCompanies.isEmpty()) return

    val sectionTitle = if (!separateNetworksPref && netCompanies.isNotEmpty() && prodCompanies.isNotEmpty()) {
        "STUDIOS & NETWORKS"
    } else {
        "PRODUCTION STUDIOS"
    }

    ProductionCompanySectionLayout(
        title = sectionTitle,
        companies = displayCompanies,
        modifier = modifier,
        onCompanyClick = onCompanyClick,
    )
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

private fun formatReleaseDate(raw: String): String {
    val trimmed = raw.trim()
    return try {
        if (trimmed.length == 10 && trimmed[4] == '-' && trimmed[7] == '-') {
            val date = java.time.LocalDate.parse(trimmed)
            date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH))
        } else {
            trimmed
        }
    } catch (_: Exception) {
        trimmed
    }
}

private fun formatCountry(raw: String): String {
    return raw.split(",")
        .map { it.trim() }
        .map { code ->
            if (code.length == 2) {
                val display = java.util.Locale("", code).displayCountry
                if (display.isNotBlank() && !display.equals(code, ignoreCase = true)) display else code
            } else {
                code
            }
        }
        .joinToString(", ")
}

