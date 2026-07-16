package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi

class AccountManager {
    companion object {
        val aniListApi = com.lagradost.cloudstream3.syncproviders.providers.AniListApi()
        val simklApi = com.lagradost.cloudstream3.syncproviders.providers.SimklApi()
        val malApi = com.lagradost.cloudstream3.syncproviders.providers.MALApi()
        val kitsuApi = com.lagradost.cloudstream3.syncproviders.providers.KitsuApi()
        val localList = com.lagradost.cloudstream3.syncproviders.providers.LocalList()
    }
}
