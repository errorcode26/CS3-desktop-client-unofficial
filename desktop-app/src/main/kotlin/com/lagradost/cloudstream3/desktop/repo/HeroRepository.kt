package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.di.AppContainerHolder
import com.lagradost.cloudstream3.desktop.domain.hero.repository.HeroRepository as DomainHeroRepository
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

data class HeroMeta(
    val title: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val tags: List<String>?,
    val plot: String?,
    val score: String?,
    val year: Int?,
    val type: com.lagradost.cloudstream3.TvType?,
    val contentRating: String?,
    val duration: Int?,
    val cachedAtMs: Long = 0L,
)

internal const val HERO_CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

object HeroCache {
    private val cache = ConcurrentHashMap<String, HeroMeta>()
    fun get(key: String): HeroMeta? = cache[key]
    fun put(key: String, meta: HeroMeta) {
        cache[key] = meta
    }
    fun remove(key: String) {
        cache.remove(key)
    }
}

/**
 * Backward-compatible delegation wrapper routing to the DI-managed instance in [AppContainerHolder].
 */
object HeroRepository : DomainHeroRepository {
    typealias HeroUpdate = DomainHeroRepository.HeroUpdate

    private val delegate: DomainHeroRepository
        get() = AppContainerHolder.container.heroRepository

    override fun cleanHeroTitle(title: String): String = delegate.cleanHeroTitle(title)

    override suspend fun prefetchTopHistory(topHistory: List<WatchHistory>, providers: List<MainAPI>) =
        delegate.prefetchTopHistory(topHistory, providers)

    override fun prefetchHeroItem(provider: MainAPI?, item: SearchResponse): Flow<DomainHeroRepository.HeroUpdate> =
        delegate.prefetchHeroItem(provider, item)
}
