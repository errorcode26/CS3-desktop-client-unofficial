package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.MalApi
import com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesStremioApi
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi
import com.lagradost.cloudstream3.syncproviders.providers.SubDlApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccountManager {
    companion object {
        @JvmStatic
        val openSubtitlesStremioApi = OpenSubtitlesStremioApi()

        @JvmStatic
        val subDlApi = SubDlApi()

        @JvmStatic
        val aniListApi = AniListApi()

        @JvmStatic
        val malApi = MalApi()

        @JvmStatic
        val simklApi = SimklApi()

        val subtitleProviders = listOf(openSubtitlesStremioApi, subDlApi)
        val allApis = listOf(openSubtitlesStremioApi, subDlApi, aniListApi, malApi, simklApi)

        var cachedAccounts: MutableMap<String, Array<AuthData>> = mutableMapOf()

        private val _accountsFlow = MutableStateFlow<Map<String, Array<AuthData>>>(emptyMap())
        val accountsFlow: StateFlow<Map<String, Array<AuthData>>> = _accountsFlow.asStateFlow()

        const val ACCOUNT_TOKEN = "auth_tokens"

        // Defaulting to "default" account since Desktop might not have multiple profile switching yet.
        val currentAccount = "default"

        fun accounts(prefix: String): Array<AuthData> {
            require(prefix != "NONE")
            return com.lagradost.common.storage.DesktopDataStore.getKey<Array<AuthData>>(
                "${ACCOUNT_TOKEN}_${prefix}_$currentAccount",
            ) ?: arrayOf()
        }

        fun updateAccounts(prefix: String, array: Array<AuthData>) {
            require(prefix != "NONE")
            com.lagradost.common.storage.DesktopDataStore.setKey("${ACCOUNT_TOKEN}_${prefix}_$currentAccount", array)
            synchronized(cachedAccounts) {
                cachedAccounts[prefix] = array
                _accountsFlow.value = cachedAccounts.toMap()
            }
        }

        init {
            val data = mutableMapOf<String, Array<AuthData>>()
            for (api in allApis) {
                data[api.idPrefix] = accounts(api.idPrefix)
            }
            synchronized(cachedAccounts) {
                cachedAccounts = data
                _accountsFlow.value = data.toMap()
            }
        }
    }
}
