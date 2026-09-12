package com.lagradost.cloudstream3.desktop.domain.hero.repository

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.repo.HeroMeta
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract for hero carousel item enrichment and prefetching.
 */
interface HeroRepository {
    sealed interface HeroUpdate {
        data class Meta(val url: String, val meta: HeroMeta) : HeroUpdate
        data class ColorTarget(val url: String, val posterUrl: String) : HeroUpdate
    }

    fun cleanHeroTitle(title: String): String
    suspend fun prefetchTopHistory(topHistory: List<WatchHistory>, providers: List<MainAPI>)
    fun prefetchHeroItem(provider: MainAPI?, item: SearchResponse): Flow<HeroUpdate>
}
