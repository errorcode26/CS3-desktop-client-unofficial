package com.lagradost.cloudstream3.desktop.subtitles

object LanguageNormalizer {
    data class NormalizedLanguage(
        val code3: String,
        val code2: String,
        val displayName: String,
        val badge: String,
    )

    private val LANGUAGE_MAP = mapOf(
        // English
        "eng" to NormalizedLanguage("eng", "en", "English", "ENG"),
        "en" to NormalizedLanguage("eng", "en", "English", "ENG"),
        "english" to NormalizedLanguage("eng", "en", "English", "ENG"),

        // Spanish & Dialects
        "spa" to NormalizedLanguage("spa", "es", "Spanish", "ESP"),
        "es" to NormalizedLanguage("spa", "es", "Spanish", "ESP"),
        "spl" to NormalizedLanguage("spa", "es-419", "Spanish (Latin America)", "ESP"),
        "spn" to NormalizedLanguage("spa", "es", "Spanish", "ESP"),
        "spanish" to NormalizedLanguage("spa", "es", "Spanish", "ESP"),

        // Portuguese & Dialects
        "por" to NormalizedLanguage("por", "pt", "Portuguese", "POR"),
        "pt" to NormalizedLanguage("por", "pt", "Portuguese", "POR"),
        "pob" to NormalizedLanguage("pob", "pt-br", "Portuguese (Brazil)", "POB"),
        "portuguese" to NormalizedLanguage("por", "pt", "Portuguese", "POR"),

        // French
        "fre" to NormalizedLanguage("fre", "fr", "French", "FRA"),
        "fra" to NormalizedLanguage("fre", "fr", "French", "FRA"),
        "fr" to NormalizedLanguage("fre", "fr", "French", "FRA"),
        "french" to NormalizedLanguage("fre", "fr", "French", "FRA"),

        // German
        "ger" to NormalizedLanguage("ger", "de", "German", "DEU"),
        "deu" to NormalizedLanguage("ger", "de", "German", "DEU"),
        "de" to NormalizedLanguage("ger", "de", "German", "DEU"),
        "german" to NormalizedLanguage("ger", "de", "German", "DEU"),

        // Italian
        "ita" to NormalizedLanguage("ita", "it", "Italian", "ITA"),
        "it" to NormalizedLanguage("ita", "it", "Italian", "ITA"),
        "italian" to NormalizedLanguage("ita", "it", "Italian", "ITA"),

        // Arabic
        "ara" to NormalizedLanguage("ara", "ar", "Arabic", "ARA"),
        "ar" to NormalizedLanguage("ara", "ar", "Arabic", "ARA"),
        "arabic" to NormalizedLanguage("ara", "ar", "Arabic", "ARA"),

        // Hindi & South Asian
        "hin" to NormalizedLanguage("hin", "hi", "Hindi", "HIN"),
        "hi" to NormalizedLanguage("hin", "hi", "Hindi", "HIN"),
        "hindi" to NormalizedLanguage("hin", "hi", "Hindi", "HIN"),
        "ben" to NormalizedLanguage("ben", "bn", "Bengali", "BEN"),
        "bn" to NormalizedLanguage("ben", "bn", "Bengali", "BEN"),
        "tam" to NormalizedLanguage("tam", "ta", "Tamil", "TAM"),
        "ta" to NormalizedLanguage("tam", "ta", "Tamil", "TAM"),
        "tel" to NormalizedLanguage("tel", "te", "Telugu", "TEL"),
        "te" to NormalizedLanguage("tel", "te", "Telugu", "TEL"),
        "kan" to NormalizedLanguage("kan", "kn", "Kannada", "KAN"),
        "kn" to NormalizedLanguage("kan", "kn", "Kannada", "KAN"),
        "mal" to NormalizedLanguage("mal", "ml", "Malayalam", "MAL"),
        "ml" to NormalizedLanguage("mal", "ml", "Malayalam", "MAL"),
        "mar" to NormalizedLanguage("mar", "mr", "Marathi", "MAR"),
        "mr" to NormalizedLanguage("mar", "mr", "Marathi", "MAR"),
        "pan" to NormalizedLanguage("pan", "pa", "Punjabi", "PAN"),
        "pa" to NormalizedLanguage("pan", "pa", "Punjabi", "PAN"),
        "guj" to NormalizedLanguage("guj", "gu", "Gujarati", "GUJ"),
        "gu" to NormalizedLanguage("guj", "gu", "Gujarati", "GUJ"),
        "urd" to NormalizedLanguage("urd", "ur", "Urdu", "URD"),
        "ur" to NormalizedLanguage("urd", "ur", "Urdu", "URD"),
        "sin" to NormalizedLanguage("sin", "si", "Sinhala", "SIN"),
        "si" to NormalizedLanguage("sin", "si", "Sinhala", "SIN"),

        // Japanese & Korean
        "jpn" to NormalizedLanguage("jpn", "ja", "Japanese", "JPN"),
        "ja" to NormalizedLanguage("jpn", "ja", "Japanese", "JPN"),
        "japanese" to NormalizedLanguage("jpn", "ja", "Japanese", "JPN"),
        "kor" to NormalizedLanguage("kor", "ko", "Korean", "KOR"),
        "ko" to NormalizedLanguage("kor", "ko", "Korean", "KOR"),
        "korean" to NormalizedLanguage("kor", "ko", "Korean", "KOR"),

        // Chinese
        "chi" to NormalizedLanguage("zho", "zh", "Chinese", "ZHO"),
        "zho" to NormalizedLanguage("zho", "zh", "Chinese", "ZHO"),
        "zh" to NormalizedLanguage("zho", "zh", "Chinese", "ZHO"),
        "zht" to NormalizedLanguage("zht", "zh-tw", "Chinese (Traditional)", "ZHT"),
        "chinese" to NormalizedLanguage("zho", "zh", "Chinese", "ZHO"),

        // Russian & Slavic
        "rus" to NormalizedLanguage("rus", "ru", "Russian", "RUS"),
        "ru" to NormalizedLanguage("rus", "ru", "Russian", "RUS"),
        "russian" to NormalizedLanguage("rus", "ru", "Russian", "RUS"),
        "ukr" to NormalizedLanguage("ukr", "uk", "Ukrainian", "UKR"),
        "uk" to NormalizedLanguage("ukr", "uk", "Ukrainian", "UKR"),
        "bel" to NormalizedLanguage("bel", "be", "Belarusian", "BEL"),
        "be" to NormalizedLanguage("bel", "be", "Belarusian", "BEL"),
        "pol" to NormalizedLanguage("pol", "pl", "Polish", "POL"),
        "pl" to NormalizedLanguage("pol", "pl", "Polish", "POL"),
        "polish" to NormalizedLanguage("pol", "pl", "Polish", "POL"),
        "cze" to NormalizedLanguage("cze", "cs", "Czech", "CZE"),
        "ces" to NormalizedLanguage("cze", "cs", "Czech", "CZE"),
        "cs" to NormalizedLanguage("cze", "cs", "Czech", "CZE"),
        "slk" to NormalizedLanguage("slk", "sk", "Slovak", "SLK"),
        "slo" to NormalizedLanguage("slk", "sk", "Slovak", "SLK"),
        "sk" to NormalizedLanguage("slk", "sk", "Slovak", "SLK"),
        "bul" to NormalizedLanguage("bul", "bg", "Bulgarian", "BUL"),
        "bg" to NormalizedLanguage("bul", "bg", "Bulgarian", "BUL"),
        "hrv" to NormalizedLanguage("hrv", "hr", "Croatian", "HRV"),
        "scr" to NormalizedLanguage("hrv", "hr", "Croatian", "HRV"),
        "hr" to NormalizedLanguage("hrv", "hr", "Croatian", "HRV"),
        "srp" to NormalizedLanguage("srp", "sr", "Serbian", "SRP"),
        "scc" to NormalizedLanguage("srp", "sr", "Serbian", "SRP"),
        "sr" to NormalizedLanguage("srp", "sr", "Serbian", "SRP"),
        "slv" to NormalizedLanguage("slv", "sl", "Slovenian", "SLV"),
        "sl" to NormalizedLanguage("slv", "sl", "Slovenian", "SLV"),
        "bos" to NormalizedLanguage("bos", "bs", "Bosnian", "BOS"),
        "bs" to NormalizedLanguage("bos", "bs", "Bosnian", "BOS"),
        "mkd" to NormalizedLanguage("mkd", "mk", "Macedonian", "MKD"),
        "mac" to NormalizedLanguage("mkd", "mk", "Macedonian", "MKD"),
        "mk" to NormalizedLanguage("mkd", "mk", "Macedonian", "MKD"),

        // Turkish, Persian, Hebrew
        "tur" to NormalizedLanguage("tur", "tr", "Turkish", "TUR"),
        "tr" to NormalizedLanguage("tur", "tr", "Turkish", "TUR"),
        "turkish" to NormalizedLanguage("tur", "tr", "Turkish", "TUR"),
        "per" to NormalizedLanguage("per", "fa", "Persian", "PER"),
        "fas" to NormalizedLanguage("per", "fa", "Persian", "PER"),
        "fa" to NormalizedLanguage("per", "fa", "Persian", "PER"),
        "heb" to NormalizedLanguage("heb", "he", "Hebrew", "HEB"),
        "he" to NormalizedLanguage("heb", "he", "Hebrew", "HEB"),
        "yid" to NormalizedLanguage("yid", "yi", "Yiddish", "YID"),
        "yi" to NormalizedLanguage("yid", "yi", "Yiddish", "YID"),

        // Nordic & Dutch
        "nld" to NormalizedLanguage("nld", "nl", "Dutch", "NLD"),
        "dut" to NormalizedLanguage("nld", "nl", "Dutch", "NLD"),
        "nl" to NormalizedLanguage("nld", "nl", "Dutch", "NLD"),
        "swe" to NormalizedLanguage("swe", "sv", "Swedish", "SWE"),
        "sv" to NormalizedLanguage("swe", "sv", "Swedish", "SWE"),
        "dan" to NormalizedLanguage("dan", "da", "Danish", "DAN"),
        "da" to NormalizedLanguage("dan", "da", "Danish", "DAN"),
        "nor" to NormalizedLanguage("nor", "no", "Norwegian", "NOR"),
        "no" to NormalizedLanguage("nor", "no", "Norwegian", "NOR"),
        "fin" to NormalizedLanguage("fin", "fi", "Finnish", "FIN"),
        "fi" to NormalizedLanguage("fin", "fi", "Finnish", "FIN"),
        "isl" to NormalizedLanguage("isl", "is", "Icelandic", "ISL"),
        "ice" to NormalizedLanguage("isl", "is", "Icelandic", "ISL"),
        "is" to NormalizedLanguage("isl", "is", "Icelandic", "ISL"),

        // Baltic & Other European
        "est" to NormalizedLanguage("est", "et", "Estonian", "EST"),
        "et" to NormalizedLanguage("est", "et", "Estonian", "EST"),
        "lav" to NormalizedLanguage("lav", "lv", "Latvian", "LAV"),
        "lv" to NormalizedLanguage("lav", "lv", "Latvian", "LAV"),
        "lit" to NormalizedLanguage("lit", "lt", "Lithuanian", "LIT"),
        "lt" to NormalizedLanguage("lit", "lt", "Lithuanian", "LIT"),
        "hun" to NormalizedLanguage("hun", "hu", "Hungarian", "HUN"),
        "hu" to NormalizedLanguage("hun", "hu", "Hungarian", "HUN"),
        "ron" to NormalizedLanguage("ron", "ro", "Romanian", "RON"),
        "rum" to NormalizedLanguage("ron", "ro", "Romanian", "RON"),
        "ro" to NormalizedLanguage("ron", "ro", "Romanian", "RON"),
        "ell" to NormalizedLanguage("ell", "el", "Greek", "ELL"),
        "gre" to NormalizedLanguage("ell", "el", "Greek", "ELL"),
        "el" to NormalizedLanguage("ell", "el", "Greek", "ELL"),
        "sqi" to NormalizedLanguage("sqi", "sq", "Albanian", "ALB"),
        "alb" to NormalizedLanguage("sqi", "sq", "Albanian", "ALB"),
        "sq" to NormalizedLanguage("sqi", "sq", "Albanian", "ALB"),

        // Southeast & East Asian
        "ind" to NormalizedLanguage("ind", "id", "Indonesian", "IND"),
        "id" to NormalizedLanguage("ind", "id", "Indonesian", "IND"),
        "vie" to NormalizedLanguage("vie", "vi", "Vietnamese", "VIE"),
        "vi" to NormalizedLanguage("vie", "vi", "Vietnamese", "VIE"),
        "tha" to NormalizedLanguage("tha", "th", "Thai", "THA"),
        "th" to NormalizedLanguage("tha", "th", "Thai", "THA"),
        "fil" to NormalizedLanguage("fil", "tl", "Filipino", "FIL"),
        "tgl" to NormalizedLanguage("fil", "tl", "Filipino", "FIL"),
        "tl" to NormalizedLanguage("fil", "tl", "Filipino", "FIL"),
        "may" to NormalizedLanguage("may", "ms", "Malay", "MAY"),
        "msa" to NormalizedLanguage("may", "ms", "Malay", "MAY"),
        "ms" to NormalizedLanguage("may", "ms", "Malay", "MAY"),
        "khm" to NormalizedLanguage("khm", "km", "Khmer", "KHM"),
        "km" to NormalizedLanguage("khm", "km", "Khmer", "KHM"),
        "lao" to NormalizedLanguage("lao", "lo", "Lao", "LAO"),
        "lo" to NormalizedLanguage("lao", "lo", "Lao", "LAO"),
        "mya" to NormalizedLanguage("mya", "my", "Burmese", "MYA"),
        "bur" to NormalizedLanguage("mya", "my", "Burmese", "MYA"),
        "my" to NormalizedLanguage("mya", "my", "Burmese", "MYA"),

        // Caucasian & Central Asian
        "kat" to NormalizedLanguage("kat", "ka", "Georgian", "GEO"),
        "geo" to NormalizedLanguage("kat", "ka", "Georgian", "GEO"),
        "ka" to NormalizedLanguage("kat", "ka", "Georgian", "GEO"),
        "hye" to NormalizedLanguage("hye", "hy", "Armenian", "ARM"),
        "arm" to NormalizedLanguage("hye", "hy", "Armenian", "ARM"),
        "hy" to NormalizedLanguage("hye", "hy", "Armenian", "ARM"),
        "aze" to NormalizedLanguage("aze", "az", "Azerbaijani", "AZE"),
        "az" to NormalizedLanguage("aze", "az", "Azerbaijani", "AZE"),
        "kaz" to NormalizedLanguage("kaz", "kk", "Kazakh", "KAZ"),
        "kk" to NormalizedLanguage("kaz", "kk", "Kazakh", "KAZ"),
        "uzb" to NormalizedLanguage("uzb", "uz", "Uzbek", "UZB"),
        "uz" to NormalizedLanguage("uzb", "uz", "Uzbek", "UZB"),
        "mon" to NormalizedLanguage("mon", "mn", "Mongolian", "MON"),
        "mn" to NormalizedLanguage("mon", "mn", "Mongolian", "MON"),

        // African & Other
        "afr" to NormalizedLanguage("afr", "af", "Afrikaans", "AFR"),
        "af" to NormalizedLanguage("afr", "af", "Afrikaans", "AFR"),
        "swh" to NormalizedLanguage("swh", "sw", "Swahili", "SWA"),
        "swa" to NormalizedLanguage("swh", "sw", "Swahili", "SWA"),
        "sw" to NormalizedLanguage("swh", "sw", "Swahili", "SWA"),
    )

    fun normalize(raw: String?): NormalizedLanguage {
        if (raw.isNullOrBlank()) {
            return NormalizedLanguage("unk", "un", "Unknown", "UNK")
        }
        val clean = raw.trim().lowercase()
        return LANGUAGE_MAP[clean] ?: run {
            val shortCode = clean.take(3).uppercase()
            NormalizedLanguage(clean.take(3), clean.take(2), raw.trim().replaceFirstChar { it.uppercase() }, shortCode)
        }
    }

    fun isMatch(langA: String?, langB: String?): Boolean {
        if (langA.isNullOrBlank() || langB.isNullOrBlank()) return true
        if (langA.equals("all", ignoreCase = true) || langB.equals("all", ignoreCase = true)) return true
        val normA = normalize(langA)
        val normB = normalize(langB)
        return normA.code3.equals(normB.code3, ignoreCase = true) ||
                normA.code2.equals(normB.code2, ignoreCase = true) ||
                normA.displayName.equals(normB.displayName, ignoreCase = true)
    }

    val POPULAR_LANGUAGES = listOf(
        NormalizedLanguage("all", "", "All Languages", "ALL"),
        NormalizedLanguage("eng", "en", "English", "ENG"),
        NormalizedLanguage("spa", "es", "Spanish", "ESP"),
        NormalizedLanguage("fre", "fr", "French", "FRA"),
        NormalizedLanguage("ger", "de", "German", "DEU"),
        NormalizedLanguage("por", "pt", "Portuguese", "POR"),
        NormalizedLanguage("ita", "it", "Italian", "ITA"),
        NormalizedLanguage("ara", "ar", "Arabic", "ARA"),
        NormalizedLanguage("hin", "hi", "Hindi", "HIN"),
        NormalizedLanguage("rus", "ru", "Russian", "RUS"),
        NormalizedLanguage("jpn", "ja", "Japanese", "JPN"),
        NormalizedLanguage("kor", "ko", "Korean", "KOR"),
        NormalizedLanguage("zho", "zh", "Chinese", "ZHO"),
        NormalizedLanguage("tur", "tr", "Turkish", "TUR"),
        NormalizedLanguage("pol", "pl", "Polish", "POL"),
        NormalizedLanguage("nld", "nl", "Dutch", "NLD"),
        NormalizedLanguage("ind", "id", "Indonesian", "IND"),
        NormalizedLanguage("vie", "vi", "Vietnamese", "VIE"),
        NormalizedLanguage("tha", "th", "Thai", "THA"),
    )
}
