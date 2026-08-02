package com.lagradost.runtime.executor

import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Health status of a plugin or scraper provider.
 */
enum class PluginHealthStatus {
    /** Operating normally with 0 or low error rates. */
    HEALTHY,

    /** Experienced 1-3 consecutive failures or high timeouts; still active. */
    DEGRADED,

    /** Tripped after exceeding consecutive failure threshold. Fast-fails to prevent stalling. */
    TRIPPED_AUTO_DISABLED,

    /** Cooldown period passed; allowing a single trial request to verify recovery. */
    HALF_OPEN
}

/**
 * Snapshot of real-time diagnostic and reliability metrics for a provider.
 */
data class PluginHealthStats(
    val providerName: String,
    val status: PluginHealthStatus = PluginHealthStatus.HEALTHY,
    val totalCalls: Long = 0,
    val successfulCalls: Long = 0,
    val failedCalls: Long = 0,
    val consecutiveFailures: Int = 0,
    val averageLatencyMs: Long = 0,
    val lastFailureReason: String? = null,
    val lastFailureTimestamp: Long = 0,
    val circuitTrippedTimestamp: Long? = null,
)

/**
 * Exception thrown when an execution is rejected because the provider's circuit breaker is OPEN.
 */
class PluginCircuitOpenException(
    val providerName: String,
    message: String = "Provider '$providerName' circuit breaker is OPEN. Fast-failing to protect app stability.",
) : Exception(message)

/**
 * Thread-safe Circuit Breaker and Health Monitoring Engine for CloudStream plugins.
 * Prevents cascading delays, infinite loops, and unhandled errors from degrading the user experience.
 */
object PluginCircuitBreaker {

    const val DEFAULT_FAILURE_THRESHOLD: Int = 4
    const val DEFAULT_COOLDOWN_MS: Long = 180_000L // 3 minutes cooldown before trial test

    private val statsMap = ConcurrentHashMap<String, PluginHealthStats>()
    private val _healthStatsFlow = MutableStateFlow<Map<String, PluginHealthStats>>(emptyMap())
    val healthStatsFlow: StateFlow<Map<String, PluginHealthStats>> = _healthStatsFlow.asStateFlow()

    /**
     * Checks if an execution is permitted for the given provider.
     * Returns a Pair: (isAllowed, rejectionReason)
     */
    fun isExecutionAllowed(
        providerName: String,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    ): Pair<Boolean, String?> {
        val current = statsMap[providerName] ?: return Pair(true, null)

        return when (current.status) {
            PluginHealthStatus.HEALTHY, PluginHealthStatus.DEGRADED -> Pair(true, null)

            PluginHealthStatus.TRIPPED_AUTO_DISABLED -> {
                val trippedAt = current.circuitTrippedTimestamp ?: 0L
                val elapsed = System.currentTimeMillis() - trippedAt
                if (elapsed >= cooldownMs) {
                    // Cooldown has elapsed -> transition to HALF_OPEN to attempt recovery
                    val halfOpenStats = current.copy(status = PluginHealthStatus.HALF_OPEN)
                    statsMap[providerName] = halfOpenStats
                    publishUpdates()
                    AppLogger.i(
                        "PluginCircuitBreaker",
                        "Provider '$providerName' entered HALF_OPEN state after ${elapsed / 1000}s cooldown. Testing connection."
                    )
                    Pair(true, null)
                } else {
                    val remainingSec = (cooldownMs - elapsed) / 1000
                    Pair(
                        false,
                        "Provider '$providerName' is auto-disabled (${current.consecutiveFailures} consecutive failures). Cooldown: ${remainingSec}s remaining."
                    )
                }
            }

            PluginHealthStatus.HALF_OPEN -> {
                // Already in trial mode, allow execution
                Pair(true, null)
            }
        }
    }

    /**
     * Records a successful execution for a provider.
     */
    fun recordSuccess(providerName: String, latencyMs: Long) {
        val existing = statsMap[providerName] ?: PluginHealthStats(providerName = providerName)
        val newTotal = existing.totalCalls + 1
        val newSuccess = existing.successfulCalls + 1

        // Running average latency calculation
        val newAvgLatency = if (existing.averageLatencyMs == 0L) {
            latencyMs
        } else {
            ((existing.averageLatencyMs * existing.successfulCalls) + latencyMs) / newSuccess
        }

        val wasTripped = existing.status == PluginHealthStatus.TRIPPED_AUTO_DISABLED ||
                existing.status == PluginHealthStatus.HALF_OPEN

        val updated = existing.copy(
            status = PluginHealthStatus.HEALTHY,
            totalCalls = newTotal,
            successfulCalls = newSuccess,
            consecutiveFailures = 0,
            averageLatencyMs = newAvgLatency,
            lastFailureReason = null,
            circuitTrippedTimestamp = null,
        )

        statsMap[providerName] = updated
        publishUpdates()

        if (wasTripped) {
            AppLogger.i("PluginCircuitBreaker", "Provider '$providerName' successfully recovered! Circuit reset to HEALTHY.")
        }
    }

    /**
     * Records a failed execution (error or timeout) for a provider.
     */
    fun recordFailure(
        providerName: String,
        reason: String,
        throwable: Throwable? = null,
        failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    ) {
        val existing = statsMap[providerName] ?: PluginHealthStats(providerName = providerName)
        val newTotal = existing.totalCalls + 1
        val newFailures = existing.failedCalls + 1
        val newConsecutive = existing.consecutiveFailures + 1
        val now = System.currentTimeMillis()

        val shouldTrip = newConsecutive >= failureThreshold || existing.status == PluginHealthStatus.HALF_OPEN
        val newStatus = when {
            shouldTrip -> PluginHealthStatus.TRIPPED_AUTO_DISABLED
            newConsecutive >= 2 -> PluginHealthStatus.DEGRADED
            else -> existing.status
        }

        val updated = existing.copy(
            status = newStatus,
            totalCalls = newTotal,
            failedCalls = newFailures,
            consecutiveFailures = newConsecutive,
            lastFailureReason = reason,
            lastFailureTimestamp = now,
            circuitTrippedTimestamp = if (shouldTrip) now else existing.circuitTrippedTimestamp,
        )

        statsMap[providerName] = updated
        publishUpdates()

        if (shouldTrip) {
            AppLogger.w(
                "PluginCircuitBreaker",
                "⚠️ CIRCUIT TRIPPED for '$providerName'! Auto-disabling scraper due to $newConsecutive consecutive failures. Reason: $reason",
                throwable
            )
        } else {
            AppLogger.d(
                "PluginCircuitBreaker",
                "Recorded failure for '$providerName' ($newConsecutive/$failureThreshold failures): $reason"
            )
        }
    }

    /**
     * Manually resets a provider back to HEALTHY state.
     */
    fun resetProvider(providerName: String) {
        val existing = statsMap[providerName] ?: return
        val updated = existing.copy(
            status = PluginHealthStatus.HEALTHY,
            consecutiveFailures = 0,
            circuitTrippedTimestamp = null,
            lastFailureReason = null,
        )
        statsMap[providerName] = updated
        publishUpdates()
        AppLogger.i("PluginCircuitBreaker", "Manually reset circuit breaker for '$providerName'.")
    }

    /**
     * Resets all provider health stats to clean state.
     */
    fun resetAll() {
        statsMap.clear()
        publishUpdates()
        AppLogger.i("PluginCircuitBreaker", "All plugin circuit breakers have been reset.")
    }

    /**
     * Gets current snapshot stats for a given provider.
     */
    fun getStats(providerName: String): PluginHealthStats? = statsMap[providerName]

    /**
     * Gets a read-only map of all tracked provider stats.
     */
    fun getAllStats(): Map<String, PluginHealthStats> = HashMap(statsMap)

    private fun publishUpdates() {
        _healthStatsFlow.value = HashMap(statsMap)
    }
}
