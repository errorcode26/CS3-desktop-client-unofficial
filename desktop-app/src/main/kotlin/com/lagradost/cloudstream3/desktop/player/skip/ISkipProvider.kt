package com.lagradost.cloudstream3.desktop.player.skip

interface ISkipProvider {
    val id: String
    val name: String
    suspend fun getSkipIntervals(query: SkipQuery): List<SkipInterval>
}
