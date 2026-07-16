package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SyncAPI

class MalApi : SyncAPI() {
    override val name = "MyAnimeList"
    override val idPrefix = "mal"
    override val requiresLogin = true
}
