package com.lagradost.cloudstream3.desktop.utils

import kotlin.math.min

object StringUtils {

    fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)

        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    fun similarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        val longerLength = longer.length
        if (longerLength == 0) return 1.0
        return (longerLength - levenshtein(longer, shorter)) / longerLength.toDouble()
    }

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "of", "in", "to", "for", "with",
        "on", "at", "by", "from", "is", "it", "this", "that", "my", "your",
    )

    fun hasContentWordMatch(query: String, target: String, minOverlapRatio: Double = 0.75): Boolean {
        val qClean = query.lowercase().replace(Regex("""[^a-z0-9\s]"""), " ")
        val tClean = target.lowercase().replace(Regex("""[^a-z0-9\s]"""), " ")

        val qWords = qClean.split(Regex("""\s+""")).filter { it.isNotBlank() && it !in STOP_WORDS }
        val tWords = tClean.split(Regex("""\s+""")).filter { it.isNotBlank() && it !in STOP_WORDS }

        if (qWords.isEmpty() || tWords.isEmpty()) return true

        // 1. First content word MUST match or prefix-match (prevents Law != Sex, Iron != Spider)
        val qFirst = qWords.first()
        val tFirst = tWords.first()
        val firstMatches = qFirst == tFirst || qFirst.startsWith(tFirst) || tFirst.startsWith(qFirst) ||
            similarity(qFirst, tFirst) >= 0.80
        if (!firstMatches) return false

        // 2. Token overlap ratio
        val qSet = qWords.toSet()
        val tSet = tWords.toSet()
        val overlap = qSet.intersect(tSet).size.toDouble()
        val minSize = kotlin.math.min(qSet.size, tSet.size).toDouble()
        return (overlap / minSize) >= minOverlapRatio
    }
}
