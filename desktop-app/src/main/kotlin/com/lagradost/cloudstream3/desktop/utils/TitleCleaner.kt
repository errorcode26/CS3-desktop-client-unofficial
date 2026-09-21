package com.lagradost.cloudstream3.desktop.utils

object TitleCleaner {


    // remove all potato etc text
    // Zero-width, directional, BOM, etc. Replaced with a plain space.
    private val INVISIBLE = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]")

    // Any Unicode whitespace.
    private val UNICODE_WS = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+")

    // Pictographs / tofu glyphs.
    private val PICTOGRAPHS = Regex(
        "[" +
            "\\uFFFD" +
            "\\u25A0-\\u25FF" +
            "\\u2600-\\u27BF" +
            "\\u2B00-\\u2BFF" +
            "\\uFE00-\\uFE0F" +
            "\\uE000-\\uF8FF" +
            "]"
    )

    // potato Emoji (surrogate pairs).
    private val EMOJI = Regex("[\\uD83C-\\uD83F][\\uDC00-\\uDFFF]")

    private val AMPERSAND = Regex("""\s*&\s*""")
    private val ORG_SUFFIX = Regex("""(?i)(?:\.\s*org|\borg)\s*$""")
    private val STANDALONE_TAGS = Regex("""\b(?:ORG|TRUE|UNRATED|THEATRICAL|REMASTERED)\b""")

    private val YEAR_PAREN = Regex("""[\(\[]\s*(?:19|20)\d{2}\s*[\)\]]""")
    private val YEAR_TOKEN = Regex("""(?<=^|\s)(?:19|20)\d{2}(?=\s|$)""")
    private val YEAR_ANY   = Regex("""\b(?:19|20)\d{2}\b""")

    private val SEASON_EPISODE = Regex("""(?i)\bS\d{1,2}\s*[.\-_ ]?\s*E\d{1,3}\b""")
    private val SEASON_EPISODE_EXTRACT = Regex("""(?i)\bS(\d{1,2})\s*[.\-_ ]?\s*E\d{1,3}\b""")
    private val SEASON_WORD    = Regex("""(?i)\b(?:Season|Series|S)\s*(\d{1,2})\b""")
    private val EPISODE_WORD   = Regex("""(?i)\b(?:Episode|Ep)\s*\d{1,3}\b""")
    private val TRAILING_SERIES = Regex("""(?i)\b(?:Series|Show|Serial)\s*$""")

    private val LANGUAGE_TAGS = Regex(
        """(?i)\b(?:""" +
            """Hindi|Tamil|Telugu|Malayalam|Kannada|Bengali|Marathi|Punjabi|Gujarati|""" +
            """Urdu|Bhojpuri|Nepali|Sinhala|Oriya|Assamese|""" +
            """English|Japanese|Korean|Chinese|Mandarin|Cantonese|Thai|Indonesian|""" +
            """Vietnamese|Filipino|Tagalog|Turkish|Arabic|Hebrew|Persian|Farsi|""" +
            """Russian|Spanish|French|German|Italian|Portuguese|Dutch|Polish|""" +
            """Swedish|Norwegian|Danish|Finnish|Greek|Czech|Hungarian|Romanian|""" +
            """Ukrainian|Multi[\s\-_]?Audio|Dual[\s\-_]?Audio|Original[\s\-_]?Audio|""" +
            """ESub|ESubs|Subbed|Dubbed|Sub|Dub""" +
            """)\b"""
    )

    private val QUALITY_TAGS = Regex(
        """(?i)\b(?:""" +
            """4K|UHD|2160p|1440p|1080p|720p|576p|480p|360p|""" +
            """WEB[\s\-_]?DL|WEB[\s\-_]?Rip|WEBRip|WEB|""" +
            """Blu[\s\-_]?Ray|BDRip|BRRip|BRip|HDRip|DVDRip|DVDScr|DVDSCR|""" +
            """HDTS|HDTC|HDCAM|PreDVD|PDVD|CAM|TS|TC|""" +
            """HDR10\+?|HDR|DV|DoVi|""" +
            """HEVC|x265|x264|H\.?265|H\.?264|AVC|""" +
            """10[\s\-_]?bit|8[\s\-_]?bit|""" +
            """REMUX|""" +
            """AMZN|NF|Netflix|HULU|DSNP|Disney\+?|ATVP|AppleTV|iT|iTunes|MAX|HBO|""" +
            """PCOK|Peacock|PMTP|Paramount\+?|""" +
            """Voot|Zee5|SonyLiv|SonyLIV|Hotstar|JioCinema|JioHotstar|Jio|MX|""" +
            """MX[\s\-_]?Player|Prime[\s\-_]?Video|Prime|""" +
            """LionsgatePlay|Lionsgate|Aha|SunNXT|Hoichoi|ALTBalaji|Ullu|""" +
            """ErosNow|Eros|Chaupal|Addatimes|Discovery\+?|""" +
            """EAC3|DDP|DD[\+\s]?\d|AC3|AAC|DTS|TrueHD|Atmos""" +
            """)\b"""
    )

    private val GENRE_TAGS = Regex(
        """(?i)\b(?:""" +
            """Reality[\s\-_]?Show|Talk[\s\-_]?Show|Game[\s\-_]?Show|""" +
            """Web[\s\-_]?Series|TV[\s\-_]?Series|Web[\s\-_]?Show|""" +
            """Complete[\s\-_]?Series|Full[\s\-_]?Series|All[\s\-_]?Episodes|""" +
            """Documentary|Anthology""" +
            """)\b"""
    )

    private val RELEASE_TAGS = Regex(
        """(?i)\b(?:""" +
            """Extended|Uncut|Remastered|""" +
            """Director'?s[\s\-_]?Cut|Theatrical[\s\-_]?Cut|Ultimate[\s\-_]?Cut|""" +
            """Final[\s\-_]?Cut|Special[\s\-_]?Edition|Anniversary[\s\-_]?Edition|""" +
            """Repack|Proper|Retail|Internal""" +
            """)\b"""
    )

    private val BRACKETED = Regex("""[\(\[]\s*[^\)\]]*\s*[\)\]]""")

    private val RELEASE_GROUP = Regex(
        """(?i)[\-\s]+(?:""" +
            """RARBG|YTS(?:\.MX|\.LT)?|PSA|EVO|FGT|GALAXY(?:RG)?|""" +
            """HDHub4u|MkvHub|MkvCinemas|1TamilMV|Tamilrockers|""" +
            """MKVCage|Zoink|KickAss|YIFY|SHOWTiME|NTb|TEPES|SE7EN""" +
            """)\s*$"""
    )

    private val ORPHAN_TRAILING_CONNECTOR =
        Regex("""(?i)\s*\b(?:and|or|with|feat\.?|ft\.?|presents?)\b\s*$""")
    private val ORPHAN_LEADING_CONNECTOR =
        Regex("""^\s*\b(?:and|or|with|feat\.?|ft\.?|presents?)\b\s+""")

    private val LEADING_SEP  = Regex("""^[\s\-–—:|·•]+""")
    private val TRAILING_SEP = Regex("""[\s\-–—:|·•]+$""")
    private val MULTI_SEP    = Regex("""\s*[-–—:|·•]\s*[-–—:|·•]\s*""")
    private val MULTI_SPACE  = Regex("""\s{2,}""")

    private val TRAILING_MOVIE_AFTER_PUNCT =
        Regex("""([!?])\s+(?:the\s+)?(?:movie|film)\s*$""", RegexOption.IGNORE_CASE)


    // "The Movie" / " Movie" / " Film" / "- The Movie" at the tail.
    private val TRAILING_MOVIE_SUFFIX =
        Regex("""(?i)[\s\-–—:]+(?:the\s+)?(?:movie|film)\s*$""")

    // Trailing punctuation that TMDB is lenient about but we want to test both ways.
    private val TRAILING_PUNCT = Regex("""[!?.,;:\-–—]+\s*$""")

    // Split a "Foo: Bar" title into its component parts so we can try each.
    private val COLON_SPLIT = Regex("""\s*[:]\s*""")



    fun extractYear(raw: String): Int? =
        YEAR_ANY.find(raw)?.value?.toIntOrNull()

    fun extractSeason(raw: String): Int? {
        SEASON_EPISODE_EXTRACT.find(raw)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.let { return it }
        return SEASON_WORD.find(raw)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun clean(rawTitle: String, keepYear: Boolean = false): String {
        if (rawTitle.isBlank()) return ""

        var t = rawTitle

        t = t.replace(PICTOGRAPHS, " ")
        t = t.replace(EMOJI, " ")

        t = t.replace(INVISIBLE, " ")
        t = t.replace(UNICODE_WS, " ")

        t = t.replace(YEAR_PAREN, " ")
        t = t.replace(BRACKETED, " ")

        t = t.replace(SEASON_EPISODE, " ")
        t = t.replace(SEASON_WORD, " ")
        t = t.replace(EPISODE_WORD, " ")

        if (!keepYear) t = t.replace(YEAR_TOKEN, " ")

        t = t.replace(LANGUAGE_TAGS, " ")
        t = t.replace(QUALITY_TAGS, " ")
        t = t.replace(GENRE_TAGS, " ")
        t = t.replace(TRAILING_SERIES, " ")
        t = t.replace(RELEASE_TAGS, " ")
        t = t.replace(RELEASE_GROUP, " ")
        t = t.replace(STANDALONE_TAGS, " ")

        t = t.replace(AMPERSAND, " and ")
        t = t.replace(ORG_SUFFIX, " ")

        // Strip trailing "Movie" / "The Movie" / "Film" ONLY when preceded by "!" or "?".
        // This turns "Cells at Work! Movie" into "Cells at Work!" and leaves
        // "Scary Movie" / "The Lego Movie" untouched.
        t = t.replace(TRAILING_MOVIE_AFTER_PUNCT) { m -> m.groupValues[1] }

        t = t.replace(MULTI_SEP, " - ")
        t = t.replace(MULTI_SPACE, " ")
        t = t.replace(LEADING_SEP, "")
        t = t.replace(TRAILING_SEP, "")
        t = t.replace(UNICODE_WS, " ")

        repeat(3) {
            t = t.replace(ORPHAN_TRAILING_CONNECTOR, " ")
            t = t.replace(ORPHAN_LEADING_CONNECTOR, " ")
            t = t.replace(MULTI_SPACE, " ")
        }
        t = t.replace(LEADING_SEP, "")
        t = t.replace(TRAILING_SEP, "")
        t = t.replace(UNICODE_WS, " ")

        return t.trim { ch ->
            ch.isWhitespace() ||
                ch == '\u200B' || ch == '\u200C' || ch == '\u200D' ||
                ch == '\u200E' || ch == '\u200F' ||
                ch == '\u2060' || ch == '\uFEFF' ||
                ch == '\uFFFD'
        }
    }


    fun searchVariants(cleanTitle: String, year: Int?): List<String> {
        if (cleanTitle.isBlank()) return emptyList()

        val out = LinkedHashSet<String>()

        // 1. Exactly as given — always try first.
        out.add(cleanTitle)

        // 2. Without a trailing "Movie" / "Film" / "The Movie" (any form).
        val noMovie = cleanTitle.replace(TRAILING_MOVIE_SUFFIX, "").trim()
        if (noMovie.length >= 3 && noMovie != cleanTitle) out.add(noMovie)

        // 3. Without a trailing "!" / "?" / ".".
        val noPunct = cleanTitle.replace(TRAILING_PUNCT, "").trim()
        if (noPunct.length >= 3 && noPunct != cleanTitle) out.add(noPunct)

        // 4. Both stripped.
        val noMovieNoPunct = noMovie.replace(TRAILING_PUNCT, "").trim()
        if (noMovieNoPunct.length >= 3 && noMovieNoPunct !in out) out.add(noMovieNoPunct)

        // 5. Franchise-style titles with a subtitle: "Foo: Bar" -> "Foo".
        val base = cleanTitle.substringBefore(":").trim()
        if (base.length >= 3 && base != cleanTitle && base !in out) out.add(base)

        // 6. Year-appended variants.
        if (year != null && year in 1900..2100) {
            val y = year.toString()
            val baseList = listOf(cleanTitle, noMovie, noPunct, noMovieNoPunct, base)
            for (b in baseList) {
                if (b.length < 3) continue
                out.add("$b $y")
            }
        }

        return out.take(4).toList()
    }
}