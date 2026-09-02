package com.lagradost.cloudstream3.desktop.domain.tracking.repository

import com.lagradost.cloudstream3.syncproviders.AuthData
import kotlinx.coroutines.flow.StateFlow

interface TrackingRepository {
    val accounts: StateFlow<Map<String, Array<AuthData>>>

    fun getAccountsForProvider(idPrefix: String): Array<AuthData>?
    fun updateAccounts(idPrefix: String, accounts: Array<AuthData>)
}
