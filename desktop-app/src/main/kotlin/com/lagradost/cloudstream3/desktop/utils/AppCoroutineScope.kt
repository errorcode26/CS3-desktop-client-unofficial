package com.lagradost.cloudstream3.desktop.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * An application-wide coroutine scope that replaces the delicate GlobalScope API.
 * It uses a SupervisorJob so that if one coroutine fails, it doesn't cancel the entire scope.
 */
val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
