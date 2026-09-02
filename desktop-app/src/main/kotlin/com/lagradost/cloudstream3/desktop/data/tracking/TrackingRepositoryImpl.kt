package com.lagradost.cloudstream3.desktop.data.tracking

import com.lagradost.cloudstream3.desktop.domain.tracking.repository.TrackingRepository
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AuthData
import kotlinx.coroutines.flow.StateFlow

class TrackingRepositoryImpl : TrackingRepository {
    override val accounts: StateFlow<Map<String, Array<AuthData>>>
        get() = AccountManager.accountsFlow

    override fun getAccountsForProvider(idPrefix: String): Array<AuthData>? {
        return AccountManager.cachedAccounts[idPrefix] ?: AccountManager.accounts(idPrefix)
    }

    override fun updateAccounts(idPrefix: String, accounts: Array<AuthData>) {
        AccountManager.updateAccounts(idPrefix, accounts)
    }
}
