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

    fun hasContentWordMatch(query: String, target: String, minOverlapRatio: Double = 0.60): Boolean {
        val qClean = query.lowercase().replace(Regex("""[^a-z0-9\s]"""), " ")
        val tClean = target.lowercase().replace(Regex("""[^a-z0-9\s]"""), " ")

        val qWords = qClean.split(Regex("""\s+""")).filter { it.isNotBlank() && it !in STOP_WORDS }
        val tWords = tClean.split(Regex("""\s+""")).filter { it.isNotBlank() && it !in STOP_WORDS }

        if (qWords.isEmpty() || tWords.isEmpty()) return true

        // Asymmetric Cardinality Guard: Prevent a 1-word query/target from matching a 3+ word title
        if (qWords.size >= 3 && tWords.size <= 1) return false
        if (tWords.size >= 3 && qWords.size <= 1) return false

        // 1. First content word MUST match or prefix-match (prevents Law != Sex, Iron != Spider)
        val qFirst = qWords.first()
        val tFirst = tWords.first()
        val firstMatches = qFirst == tFirst || qFirst.startsWith(tFirst) || tFirst.startsWith(qFirst) ||
            similarity(qFirst, tFirst) >= 0.80
        if (!firstMatches) return false

        // 2. Token Recall & Precision
        val qSet = qWords.toSet()
        val tSet = tWords.toSet()
        val overlap = qSet.intersect(tSet).size.toDouble()

        val recall = overlap / qSet.size.toDouble()
        val precision = overlap / tSet.size.toDouble()

        // Both forward recall and backward precision must satisfy the confidence floor
        return recall >= minOverlapRatio && precision >= 0.50
    }

    fun isYearCompatible(
        canonicalYear: Int?,
        candidateYear: Int?,
        isTv: Boolean,
        titleWordCount: Int = 3,
    ): Boolean {
        if (canonicalYear == null || candidateYear == null) return true
        return if (isTv) {
            val maxPastGap = if (titleWordCount <= 2) 2 else 4
            candidateYear <= canonicalYear + 1 && (canonicalYear - candidateYear) <= maxPastGap
        } else {
            Math.abs(canonicalYear - candidateYear) <= 2
        }
    }

    /**
     * Mathematically rigorous verification of whether [candidateTitle] is a genuine match
     * for [canonicalTitle], preventing subset collisions (e.g. 'Monster' matching 'Monster: The Lizzie Borden Story').
     */
    fun isTitleMatch(
        canonicalTitle: String,
        candidateTitle: String,
        canonicalYear: Int? = null,
        candidateYear: Int? = null,
        isTv: Boolean = false,
        minRecall: Double = 0.60,
    ): Boolean {
        val cleanCanonical = canonicalTitle.lowercase().removePrefix("the ").trim()
        val cleanCandidate = candidateTitle.lowercase().removePrefix("the ").trim()

        val strippedCanonical = cleanCanonical.replace(Regex("[^a-zA-Z0-9]"), "")
        val strippedCandidate = cleanCandidate.replace(Regex("[^a-zA-Z0-9]"), "")
        val wordCount = cleanCanonical.split(Regex("""\s+""")).count { it.isNotBlank() }

        // Exact stripped alphanumeric match is always valid (assuming year matches)
        if (strippedCanonical.equals(strippedCandidate, ignoreCase = true)) {
            if (!isYearCompatible(canonicalYear, candidateYear, isTv, wordCount)) return false
            return true
        }

        // 1. Number / Roman Numeral Integrity
        val numbers1 = Regex("""\b\d+\b""").findAll(cleanCanonical).map { it.value }.toSet()
        val numbers2 = Regex("""\b\d+\b""").findAll(cleanCandidate).map { it.value }.toSet()
        val romanRegex = Regex("""\b(ii|iii|iv|v|vi|vii|viii|ix|x)\b""")
        val romans1 = romanRegex.findAll(cleanCanonical).map { it.value }.toSet()
        val romans2 = romanRegex.findAll(cleanCandidate).map { it.value }.toSet()
        val yearStrings = setOfNotNull(canonicalYear?.toString(), candidateYear?.toString())
        val normNumbers1 = numbers1 - yearStrings
        val normNumbers2 = numbers2 - yearStrings
        if (normNumbers1 != normNumbers2 || romans1 != romans2) return false

        // 2. Year Compatibility
        if (!isYearCompatible(canonicalYear, candidateYear, isTv, wordCount)) {
            return false
        }

        // 3. Token-Level Dual Coverage (Recall + Precision)
        if (!hasContentWordMatch(canonicalTitle, candidateTitle, minOverlapRatio = minRecall)) {
            return false
        }

        // 4. Character Levenshtein Floor
        val charSim = similarity(strippedCanonical, strippedCandidate)
        return charSim >= 0.55
    }
}
