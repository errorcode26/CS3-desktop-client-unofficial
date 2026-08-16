package com.lagradost.cloudstream3.desktop.metadata

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType

/**
 * Standard interface contract for all metadata providers.
 *
 * Implementations can act as fast ID resolvers, deep artwork/cast enrichers, or both.
 */
interface MetadataProvider {
    val id: String
    val displayName: String
    val priority: Int
    val supportedTypes: Set<TvType>

    /**
     * Resolves the media identity from title and year.
     * Returns a [MetadataMatch] or null if no confident match is found.
     */
    suspend fun resolve(
        title: String,
        year: Int?,
        type: TvType,
        rawUrl: String?,
    ): MetadataMatch?

    /**
     * Enriches the [loaded] response with metadata and invokes callbacks progressively.
     *
     * @return true if enrichment succeeded, false if it failed or was skipped.
     */
    suspend fun enrich(
        loaded: LoadResponse,
        match: MetadataMatch?,
        context: MetadataEnrichmentContext,
        callbacks: MetadataEnrichmentCallbacks,
    ): Boolean
}
