package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.MalApi
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi
import com.lagradost.cloudstream3.syncproviders.providers.SubDlApi
import com.lagradost.cloudstream3.syncproviders.AuthData

class AccountManager {
    companion object {
        @JvmStatic
        val subDlApi = SubDlApi()
        @JvmStatic
        val aniListApi = AniListApi()
        @JvmStatic
        val malApi = MalApi()
        @JvmStatic
        val simklApi = SimklApi()
        
        val subtitleProviders = listOf(subDlApi)
        val allApis = listOf(subDlApi, aniListApi, malApi, simklApi)

        var cachedAccounts: MutableMap<String, Array<AuthData>> = mutableMapOf()

        const val ACCOUNT_TOKEN = "auth_tokens"

        // Defaulting to "default" account since Desktop might not have multiple profile switching yet.
        val currentAccount = "default"

        fun accounts(prefix: String): Array<AuthData> {
            require(prefix != "NONE")
            return com.lagradost.common.storage.DesktopDataStore.getKey<Array<AuthData>>(
                "${ACCOUNT_TOKEN}_${prefix}_${currentAccount}"
            ) ?: arrayOf()
        }

        fun updateAccounts(prefix: String, array: Array<AuthData>) {
            require(prefix != "NONE")
            com.lagradost.common.storage.DesktopDataStore.setKey("${ACCOUNT_TOKEN}_${prefix}_${currentAccount}", array)
            synchronized(cachedAccounts) {
                cachedAccounts[prefix] = array
            }
        }

        init {
            val data = mutableMapOf<String, Array<AuthData>>()
            for (api in allApis) {
                data[api.idPrefix] = accounts(api.idPrefix)
            }
            synchronized(cachedAccounts) {
                cachedAccounts = data
            }
        }
    }
}
