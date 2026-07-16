package com.lagradost.cloudstream3.syncproviders

open class AuthRepo {
    open fun authUser(): AuthUser? = null
}

class SyncRepo(val api: SyncAPI) : AuthRepo() {
    fun library(): Result<SyncAPI.LibraryMetadata>? = null
}

class SubtitleRepo(val api: SubtitleAPI) : AuthRepo()

abstract class SubtitleAPI : AuthAPI()
