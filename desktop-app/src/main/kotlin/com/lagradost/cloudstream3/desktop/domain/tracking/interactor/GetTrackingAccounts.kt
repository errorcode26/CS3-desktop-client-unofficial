package com.lagradost.cloudstream3.desktop.domain.tracking.interactor

import com.lagradost.cloudstream3.desktop.domain.tracking.repository.TrackingRepository
import com.lagradost.cloudstream3.syncproviders.AuthData
import kotlinx.coroutines.flow.StateFlow

class GetTrackingAccounts(
    private val repository: TrackingRepository,
) {
    fun subscribe(): StateFlow<Map<String, Array<AuthData>>> = repository.accounts

    fun get(idPrefix: String): Array<AuthData>? = repository.getAccountsForProvider(idPrefix)
}
