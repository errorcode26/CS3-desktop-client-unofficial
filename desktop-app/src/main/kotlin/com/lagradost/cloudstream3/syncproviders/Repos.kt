package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch

open class AuthRepo {
    open fun authUser(): AuthUser? = null
}

class SyncRepo(val api: SyncAPI) : AuthRepo() {
    fun library(): Result<SyncAPI.LibraryMetadata>? = null
}

class SubtitleRepo(val api: SubtitleAPI) : AuthRepo()

abstract class SubtitleAPI : AuthAPI() {
    open suspend fun search(auth: AuthData?, query: SubtitleSearch): List<SubtitleEntity>? = throw NotImplementedError()
    open suspend fun load(auth: AuthData?, subtitle: SubtitleEntity): String? = throw NotImplementedError()
}
