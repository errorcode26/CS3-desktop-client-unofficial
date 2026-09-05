package com.lagradost.cloudstream3.desktop.ui.screens.settings

data class SettingsSearchEntry(
    val title: String,
    val tab: SettingsTab,
    val keywords: List<String> = emptyList(),
    val uiLabel: String = title,
    val subScreen: SettingsSubScreen? = null,
)

object SettingsSearchIndex {
    val searchIndex = listOf(
        // Appearance & Theme tab (Modular Sub-Screens)
        SettingsSearchEntry("Navigation Dock Style", LeafTab.APPEARANCE, listOf("dock", "island", "floating", "navbar", "seamless", "style", "rail"), uiLabel = "Navigation Dock Style", subScreen = SettingsSubScreen.APPEARANCE_NAV_DOCK),
        SettingsSearchEntry("Navigation Dock Position", LeafTab.APPEARANCE, listOf("sidebar", "dock", "left", "top", "bottom", "right", "navigation", "position"), uiLabel = "Dock & Sidebar Position", subScreen = SettingsSubScreen.APPEARANCE_NAV_DOCK),
        SettingsSearchEntry("Global UI Scale / Zoom", LeafTab.APPEARANCE, listOf("scale", "zoom", "ctrl", "magnify", "percent", "ui scale", "density"), uiLabel = "Global UI Scale / Zoom", subScreen = SettingsSubScreen.APPEARANCE_NAV_DOCK),
        SettingsSearchEntry("Dock Buttons & Custom Order", LeafTab.APPEARANCE, listOf("dock", "order", "buttons", "items", "reorder", "hide tabs", "sidebar items"), uiLabel = "Dock Buttons & Custom Order", subScreen = SettingsSubScreen.APPEARANCE_NAV_DOCK),
        SettingsSearchEntry("Top Bar Provider Button Style", LeafTab.APPEARANCE, listOf("topbar", "provider", "button", "icon only", "icon and name", "badge", "hide provider"), uiLabel = "Top Bar Provider Button Style", subScreen = SettingsSubScreen.APPEARANCE_NAV_DOCK),
        SettingsSearchEntry("Show Profile in Top Bar", LeafTab.APPEARANCE, listOf("profile", "avatar", "user", "topbar", "header", "name"), uiLabel = "Show Profile in Top Bar", subScreen = SettingsSubScreen.APPEARANCE_NAV_DOCK),
        SettingsSearchEntry("Clock & Date Display Mode", LeafTab.APPEARANCE, listOf("clock", "date", "time", "format", "topbar", "timer", "corner"), uiLabel = "Clock & Date Display Mode", subScreen = SettingsSubScreen.APPEARANCE_NAV_DOCK),
        
        SettingsSearchEntry("Provider Badges on Cards", LeafTab.APPEARANCE, listOf("provider", "badges", "cards", "posters", "plugins", "hide", "icon only", "full badge", "branding"), uiLabel = "Provider Badges on Cards", subScreen = SettingsSubScreen.APPEARANCE_POSTERS_BADGES),
        SettingsSearchEntry("Auto-Clean Messy Release Titles", LeafTab.APPEARANCE, listOf("clean", "titles", "strip", "1080p", "web-dl", "tags", "release", "names"), uiLabel = "Auto-Clean Messy Release Titles", subScreen = SettingsSubScreen.APPEARANCE_POSTERS_BADGES),
        SettingsSearchEntry("Quality Badges", LeafTab.APPEARANCE, listOf("poster", "quality", "resolution", "hd", "4k", "badge", "1080p"), uiLabel = "Quality Badges (4K / 1080p)", subScreen = SettingsSubScreen.APPEARANCE_POSTERS_BADGES),
        SettingsSearchEntry("SUB / DUB Language Badges", LeafTab.APPEARANCE, listOf("poster", "language", "dub", "sub", "badge", "audio"), uiLabel = "SUB / DUB Language Badges", subScreen = SettingsSubScreen.APPEARANCE_POSTERS_BADGES),
        SettingsSearchEntry("Rating Badges", LeafTab.APPEARANCE, listOf("poster", "rating", "score", "star", "badge", "gold pill"), uiLabel = "Rating Badges (★ Gold Pill)", subScreen = SettingsSubScreen.APPEARANCE_POSTERS_BADGES),
        SettingsSearchEntry("Continue Watching Card Style", LeafTab.APPEARANCE, listOf("continue watching", "style", "wide", "classic", "thumbnail", "card"), uiLabel = "Continue Watching Card Style", subScreen = SettingsSubScreen.APPEARANCE_POSTERS_BADGES),
        SettingsSearchEntry("UI Element Drop Shadows", LeafTab.APPEARANCE, listOf("shadow", "drop shadow", "depth", "elevation", "cards"), uiLabel = "UI Element Drop Shadows", subScreen = SettingsSubScreen.APPEARANCE_POSTERS_BADGES),
        SettingsSearchEntry("Text Legibility Shadows", LeafTab.APPEARANCE, listOf("text shadow", "legibility", "readability", "titles"), uiLabel = "Text Legibility Shadows", subScreen = SettingsSubScreen.APPEARANCE_POSTERS_BADGES),
        SettingsSearchEntry("Poster Visual Editor", LeafTab.APPEARANCE, listOf("poster", "editor", "preview", "customize", "width", "spacing", "rounding", "workshop"), uiLabel = "Poster Visual Editor", subScreen = SettingsSubScreen.POSTER_EDITOR),
        
        SettingsSearchEntry("Accent Color", LeafTab.APPEARANCE, listOf("accent", "theme", "color", "purple", "blue", "green", "red", "orange", "hex", "custom"), uiLabel = "Theme & Colors", subScreen = SettingsSubScreen.APPEARANCE_THEME_WALLPAPER),
        SettingsSearchEntry("Light Theme", LeafTab.APPEARANCE, listOf("light", "white", "day", "bright", "theme", "mode"), uiLabel = "Light Theme", subScreen = SettingsSubScreen.APPEARANCE_THEME_WALLPAPER),
        SettingsSearchEntry("AMOLED Pure Black Mode", LeafTab.APPEARANCE, listOf("amoled", "oled", "pure black", "pitch black", "battery", "dark"), uiLabel = "AMOLED Pure Black Mode", subScreen = SettingsSubScreen.APPEARANCE_THEME_WALLPAPER),
        SettingsSearchEntry("App Background Palette", LeafTab.APPEARANCE, listOf("background", "navy", "midnight", "slate", "mocha", "pure black", "tint", "custom hex"), uiLabel = "App Background Palette", subScreen = SettingsSubScreen.APPEARANCE_THEME_WALLPAPER),
        SettingsSearchEntry("App Typography & Font", LeafTab.APPEARANCE, listOf("font", "typography", "inter", "roboto", "poppins", "outfit", "text"), uiLabel = "App Typography & Font", subScreen = SettingsSubScreen.APPEARANCE_THEME_WALLPAPER),
        SettingsSearchEntry("Ambient Glow", LeafTab.APPEARANCE, listOf("ambient", "glow", "backlight", "lighting", "cinematic", "intensity"), uiLabel = "Ambient Glow", subScreen = SettingsSubScreen.APPEARANCE_THEME_WALLPAPER),
        SettingsSearchEntry("Background Wallpaper", LeafTab.APPEARANCE, listOf("wallpaper", "background image", "blur", "brightness", "vignette", "tint", "custom wallpaper"), uiLabel = "Background Wallpaper", subScreen = SettingsSubScreen.APPEARANCE_THEME_WALLPAPER),
        
        SettingsSearchEntry("Enable Hero Slider", LeafTab.APPEARANCE, listOf("hero", "carousel", "banner", "home", "slider", "featured", "spotlight"), uiLabel = "Enable Hero Slider", subScreen = SettingsSubScreen.APPEARANCE_HOME_FEED),
        SettingsSearchEntry("Hero Banner Layout Style", LeafTab.APPEARANCE, listOf("hero style", "cinema", "fullscreen", "filmstrip", "banner layout"), uiLabel = "Hero Banner Layout Style", subScreen = SettingsSubScreen.APPEARANCE_HOME_FEED),
        SettingsSearchEntry("Dynamic Backdrop Blur", LeafTab.APPEARANCE, listOf("hero", "blur", "background", "backdrop", "frosted", "glass", "gaussian", "details"), uiLabel = "Dynamic Backdrop Blur", subScreen = SettingsSubScreen.DETAILS_LAYOUT),
        SettingsSearchEntry("UI Container & Card Glass Opacity", LeafTab.APPEARANCE, listOf("glass", "opacity", "card opacity", "translucent", "transparency"), uiLabel = "UI Container & Card Glass Opacity", subScreen = SettingsSubScreen.APPEARANCE_THEME_WALLPAPER),
        
        SettingsSearchEntry("Details Page Sections & Layout", LeafTab.APPEARANCE, listOf("details", "order", "drag", "section", "layout", "modular", "reorder"), uiLabel = "Details Page Layout & Sections", subScreen = SettingsSubScreen.DETAILS_LAYOUT),
        SettingsSearchEntry("Lock Unreleased Episodes", LeafTab.DETAILS, listOf("lock", "unreleased", "episodes", "future", "upcoming", "air date", "countdown", "schedule", "anime", "protect"), uiLabel = "Lock Unreleased Episodes", subScreen = SettingsSubScreen.DETAILS_LAYOUT),
        SettingsSearchEntry("Anti-Spoiler Mode", LeafTab.DETAILS, listOf("spoiler", "anti-spoiler", "hide", "blur", "thumbnails", "descriptions", "episodes"), uiLabel = "Anti-Spoiler Mode", subScreen = SettingsSubScreen.DETAILS_LAYOUT),
        SettingsSearchEntry("Details Current & End Time Badges", LeafTab.DETAILS, listOf("time", "current", "end time", "badges", "details", "clock"), uiLabel = "Show Current / End Time", subScreen = SettingsSubScreen.DETAILS_LAYOUT),

        // Playback & Video Tab
        SettingsSearchEntry("Hardware Decoding", LeafTab.PLAYER, listOf("hwdec", "hardware", "decoding", "acceleration", "gpu", "mpv"), uiLabel = "Hardware Acceleration"),
        SettingsSearchEntry("Smooth Video / Interpolation", LeafTab.PLAYER, listOf("interpolation", "smooth", "motion", "video", "60fps", "display resample"), uiLabel = "Smooth Video (Display Resample)"),
        SettingsSearchEntry("Preferred Stream Quality", LeafTab.PLAYER, listOf("quality", "resolution", "1080p", "720p", "4k", "stream"), uiLabel = "Preferred Stream Quality"),
        SettingsSearchEntry("Source & Quality Priorities", LeafTab.PLAYER, listOf("priority", "quality", "source", "4k", "1080p", "ranking", "server", "sort"), uiLabel = "Source & Quality Priorities"),
        SettingsSearchEntry("yt-dlp Default Quality", LeafTab.PLAYER, listOf("ytdl", "youtube", "format", "resolution", "quality"), uiLabel = "yt-dlp Default Quality"),
        SettingsSearchEntry("Language & Track Defaults", LeafTab.PLAYER, listOf("language", "audio", "subtitle", "track", "dub", "sub", "english", "japanese"), uiLabel = "Language & Track Defaults"),
        SettingsSearchEntry("Open Local File", LeafTab.PLAYER, listOf("local", "file", "disk", "mp4", "mkv", "play file"), uiLabel = "Open Local Video File"),
        SettingsSearchEntry("Open Network Stream", LeafTab.PLAYER, listOf("network", "stream", "url", "m3u8", "http", "direct link"), uiLabel = "Open Network Stream URL"),
        SettingsSearchEntry("Auto-Play Streams", LeafTab.PLAYER, listOf("auto", "play", "next", "episode", "binge", "streams"), uiLabel = "Auto-Play Streams"),
        SettingsSearchEntry("Auto Play Timeout", LeafTab.PLAYER, listOf("auto", "play", "timeout", "delay", "fallback"), uiLabel = "Playback Timeout"),
        SettingsSearchEntry("Intro & Outro Skipping", LeafTab.PLAYER, listOf("skip", "intro", "outro", "openings", "endings", "aniskip"), uiLabel = "Enable Intro & Outro Discovery"),
        SettingsSearchEntry("Auto-Skip Openings", LeafTab.PLAYER, listOf("skip", "openings", "intros", "aniskip"), uiLabel = "Auto-Skip Openings & Intros"),
        SettingsSearchEntry("Auto-Skip Endings", LeafTab.PLAYER, listOf("skip", "endings", "outros", "credits"), uiLabel = "Auto-Skip Endings & Outros"),
        SettingsSearchEntry("Keyboard Shortcuts Reference", LeafTab.PLAYER, listOf("keyboard", "shortcuts", "hotkeys", "controls", "keys", "gestures"), uiLabel = "Keyboard Shortcuts Reference", subScreen = SettingsSubScreen.KEYBOARD_SHORTCUTS),

        // Audio & Equalizer Tab
        SettingsSearchEntry("Volume Normalization", LeafTab.AUDIO, listOf("audio", "normalization", "volume", "loudness", "night mode", "drc", "compression"), uiLabel = "Volume Normalization (Stable Audio)"),
        SettingsSearchEntry("Equalizer Profile", LeafTab.AUDIO, listOf("audio", "equalizer", "eq", "preset", "bass", "treble", "vocal"), uiLabel = "Equalizer Profile"),
        SettingsSearchEntry("Spatial Audio", LeafTab.AUDIO, listOf("audio", "spatializer", "surround", "3d", "stereo", "widener"), uiLabel = "3D Spatial Audio (Stereo Widener)"),
        SettingsSearchEntry("Audio Sync / Delay Offset", LeafTab.AUDIO, listOf("audio", "delay", "sync", "offset", "lip sync", "bluetooth"), uiLabel = "Audio Sync (Delay Offset)"),
        SettingsSearchEntry("Volume Overdrive", LeafTab.AUDIO, listOf("boost", "volume", "200", "overdrive", "loud"), uiLabel = "Volume Overdrive (Boost to 200%)"),
        SettingsSearchEntry("Preferred Audio Language", LeafTab.AUDIO, listOf("audio", "language", "dub", "track", "english", "japanese"), uiLabel = "Preferred Audio Language"),

        // Subtitles & Styling Tab
        SettingsSearchEntry("Default Subtitles", LeafTab.SUBTITLES, listOf("subtitle", "language", "sub", "track", "english"), uiLabel = "Default Subtitles"),
        SettingsSearchEntry("Subtitle Styling & Customization", LeafTab.SUBTITLES, listOf("subtitle", "font", "color", "size", "background", "border", "shadow", "ass", "srt"), uiLabel = "Subtitle Styling Studio"),

        // Downloads Engine Tab
        SettingsSearchEntry("Download Buttons", LeafTab.DOWNLOADS, listOf("download", "buttons", "offline", "storage"), uiLabel = "Show Download Buttons"),
        SettingsSearchEntry("Download Storage Location", LeafTab.DOWNLOADS, listOf("download", "directory", "folder", "path", "storage"), uiLabel = "Download Storage Directory"),
        SettingsSearchEntry("Parallel Turbo Download Threads", LeafTab.DOWNLOADS, listOf("download", "threads", "parallel", "chunks", "speed"), uiLabel = "Parallel Turbo Download Threads"),
        SettingsSearchEntry("Maximum Concurrent Downloads", LeafTab.DOWNLOADS, listOf("download", "concurrent", "queue", "tasks", "simultaneous"), uiLabel = "Maximum Concurrent Active Downloads"),

        // Extensions > Extensions tab
        SettingsSearchEntry("Browse Extensions", LeafTab.EXTENSIONS, listOf("extensions", "plugins", "browse", "install", "search", "add"), uiLabel = "Browse Extensions"),
        SettingsSearchEntry("Installed Extensions", LeafTab.EXTENSIONS, listOf("extensions", "plugins", "installed", "manage", "update", "uninstall"), uiLabel = "Installed Extensions"),
        SettingsSearchEntry("Extension Repositories", LeafTab.EXTENSIONS, listOf("repositories", "repos", "plugins", "extensions", "sources", "url"), uiLabel = "Repositories"),

        // Extensions > Accounts tab
        SettingsSearchEntry("AniList Tracker", LeafTab.ACCOUNTS, listOf("anilist", "tracker", "anime", "sync", "scrobble", "login"), uiLabel = "AniList"),
        SettingsSearchEntry("MAL / MyAnimeList Tracker", LeafTab.ACCOUNTS, listOf("mal", "myanimelist", "tracker", "anime", "sync", "login"), uiLabel = "MAL"),
        SettingsSearchEntry("SIMKL Tracker", LeafTab.ACCOUNTS, listOf("simkl", "tracker", "anime", "shows", "movies", "sync"), uiLabel = "SIMKL"),
        SettingsSearchEntry("Trakt Tracker", LeafTab.ACCOUNTS, listOf("trakt", "tracker", "movies", "shows", "sync", "scrobble"), uiLabel = "Trakt"),
        SettingsSearchEntry("Discord Rich Presence", LeafTab.ACCOUNTS, listOf("discord", "rpc", "rich presence", "status", "activity"), uiLabel = "Discord Rich Presence"),

        // Metadata & Integrations tab & Sub-screens
        SettingsSearchEntry("Metadata & Integrations Hub", LeafTab.INTEGRATIONS, listOf("metadata", "integrations", "tmdb", "anilist", "kitsu", "stremio"), uiLabel = "Metadata & Integrations Hub"),
        SettingsSearchEntry("The Movie Database (TMDB)", LeafTab.INTEGRATIONS, listOf("tmdb", "metadata", "api key", "backdrops", "logos", "cast", "posters", "language", "financials", "budget", "box office", "trailers", "networks", "studios"), subScreen = SettingsSubScreen.INTEGRATIONS_TMDB, uiLabel = "The Movie Database (TMDB)"),
        SettingsSearchEntry("TMDB API Key", LeafTab.INTEGRATIONS, listOf("tmdb", "api key", "v3", "token", "custom key"), subScreen = SettingsSubScreen.INTEGRATIONS_TMDB, uiLabel = "TMDB API Key"),
        SettingsSearchEntry("TMDB Metadata Language", LeafTab.INTEGRATIONS, listOf("tmdb", "language", "locale", "translation", "synopsis", "overviews", "titles"), subScreen = SettingsSubScreen.INTEGRATIONS_TMDB, uiLabel = "Overview & Title Language"),
        SettingsSearchEntry("Poster & Backdrop Language Priority", LeafTab.INTEGRATIONS, listOf("tmdb", "artwork", "poster", "backdrop", "image language", "textless"), subScreen = SettingsSubScreen.INTEGRATIONS_TMDB, uiLabel = "Poster & Backdrop Language Priority"),
        SettingsSearchEntry("Show Movie Financials", LeafTab.INTEGRATIONS, listOf("budget", "revenue", "box office", "financials", "gross", "earnings", "tmdb"), subScreen = SettingsSubScreen.INTEGRATIONS_TMDB, uiLabel = "Show Movie Financials (Budget & Box Office)"),
        SettingsSearchEntry("Separate TV Networks & Studios", LeafTab.INTEGRATIONS, listOf("networks", "studios", "hbo", "netflix", "warner bros", "broadcaster", "production", "tmdb"), subScreen = SettingsSubScreen.INTEGRATIONS_TMDB, uiLabel = "Separate TV Networks & Production Studios"),
        SettingsSearchEntry("Include Adult Content in TMDB", LeafTab.INTEGRATIONS, listOf("adult", "18+", "nsfw", "tmdb", "search", "filter"), subScreen = SettingsSubScreen.INTEGRATIONS_TMDB, uiLabel = "Include Adult (18+) Content in TMDB Search"),
        SettingsSearchEntry("TVmaze TV Metadata", LeafTab.INTEGRATIONS, listOf("tvmaze", "tv", "series", "schedules", "air dates", "prime video", "apple tv", "webchannel", "metadata"), subScreen = SettingsSubScreen.INTEGRATIONS_TMDB, uiLabel = "TVmaze TV Series Metadata"),
        SettingsSearchEntry("Max Trailers to Load", LeafTab.INTEGRATIONS, listOf("trailers", "clips", "videos", "limit", "tmdb", "youtube"), subScreen = SettingsSubScreen.INTEGRATIONS_TMDB, uiLabel = "Maximum Trailers to Fetch"),
        SettingsSearchEntry("Anime Engines Studio", LeafTab.INTEGRATIONS, listOf("anime", "anilist", "kitsu", "romaji", "voice actors", "characters", "simulcast", "countdown"), subScreen = SettingsSubScreen.INTEGRATIONS_ANIME, uiLabel = "Anime Engines Studio"),
        SettingsSearchEntry("Preferred Anime Title Language", LeafTab.INTEGRATIONS, listOf("romaji", "english", "native", "japanese", "title", "naming", "anime"), subScreen = SettingsSubScreen.INTEGRATIONS_ANIME, uiLabel = "Preferred Title Language"),
        SettingsSearchEntry("AniList Anime Metadata", LeafTab.INTEGRATIONS, listOf("anilist", "anime", "metadata", "voice", "cast", "characters", "simulcast", "air date", "graphql"), subScreen = SettingsSubScreen.INTEGRATIONS_ANIME, uiLabel = "AniList (Official GraphQL Engine)"),
        SettingsSearchEntry("Kitsu Anime Metadata", LeafTab.INTEGRATIONS, listOf("kitsu", "anime", "metadata", "backup", "fallback", "rest"), subScreen = SettingsSubScreen.INTEGRATIONS_ANIME, uiLabel = "Kitsu (REST API Fallback Engine)"),
        SettingsSearchEntry("Japanese Voice Cast & Photos", LeafTab.INTEGRATIONS, listOf("voice", "cast", "seiyuu", "characters", "portraits", "photos", "anime"), subScreen = SettingsSubScreen.INTEGRATIONS_ANIME, uiLabel = "Japanese Voice Cast & Character Photos"),
        SettingsSearchEntry("Simulcast Schedules & Countdowns", LeafTab.INTEGRATIONS, listOf("simulcast", "air date", "countdown", "schedule", "locked", "episodes", "anime"), subScreen = SettingsSubScreen.INTEGRATIONS_ANIME, uiLabel = "Simulcast Schedules & Airing Countdowns"),
        SettingsSearchEntry("Anime Animation Studios", LeafTab.INTEGRATIONS, listOf("studio", "mappa", "ufotable", "madhouse", "bones", "badges", "anime"), subScreen = SettingsSubScreen.INTEGRATIONS_ANIME, uiLabel = "Animation Studio Badges"),
        SettingsSearchEntry("Stremio Metadata & Catalogs", LeafTab.INTEGRATIONS, listOf("stremio", "addon", "manifest", "cinemeta", "cyberflix", "metadata", "catalogs"), uiLabel = "Stremio Community Addons"),

        // Network tab
        SettingsSearchEntry("DNS over HTTPS (DoH)", LeafTab.NETWORK, listOf("dns", "https", "doh", "cloudflare", "quad9", "adguard", "google", "network", "isp", "bypass"), uiLabel = "DNS over HTTPS (DoH)"),
        SettingsSearchEntry("Experimental & Scraper Engine", LeafTab.NETWORK, listOf("security", "cloudflare", "scraper", "solver", "captcha", "bypass"), uiLabel = "Experimental & Scraper Engine"),

        // Advanced tab
        SettingsSearchEntry("Storage Directories", LeafTab.ADVANCED, listOf("storage", "directory", "path", "files", "data", "appdata", "roaming"), uiLabel = "Storage Directories"),
        SettingsSearchEntry("Clear Image Cache", LeafTab.ADVANCED, listOf("clear", "image", "cache", "storage", "disk", "free space"), uiLabel = "Clear Image Cache"),
        SettingsSearchEntry("Cloned Sites & Custom Provider URLs", LeafTab.ADVANCED, listOf("clone", "provider", "custom", "url", "override", "mirror", "domain"), uiLabel = "Cloned Sites & Custom URLs"),
        SettingsSearchEntry("Factory Reset / Danger Zone", LeafTab.ADVANCED, listOf("reset", "wipe", "delete", "factory", "clear all", "reinstall"), uiLabel = "Danger Zone"),

        // Developer tab
        SettingsSearchEntry("Provider Testing & Benchmarking", LeafTab.DEVELOPER, listOf("developer", "provider", "test", "debug", "benchmark", "extractor"), uiLabel = "Provider Testing"),
        SettingsSearchEntry("Network Diagnostics", LeafTab.DEVELOPER, listOf("network", "diagnostics", "debug", "ping", "connectivity"), uiLabel = "Network Diagnostics"),
        SettingsSearchEntry("Logcat Live Viewer", LeafTab.DEVELOPER, listOf("logcat", "logs", "debug", "crash", "console", "f12"), uiLabel = "Logcat Viewer"),

        // About tab
        SettingsSearchEntry("Check for App Updates", LeafTab.ABOUT, listOf("update", "version", "check", "new", "release", "download"), uiLabel = "Check for Updates"),
        SettingsSearchEntry("About CloudStream Desktop", LeafTab.ABOUT, listOf("about", "version", "info", "license", "credits", "github"), uiLabel = "About"),
    )
}
