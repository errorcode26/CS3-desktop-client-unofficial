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
        // Theme tab
        SettingsSearchEntry("Theme Presets", LeafTab.THEME, listOf("preset", "theme", "cyberpunk", "nord", "dracula", "catppuccin", "amoled", "color"), uiLabel = "Theme Presets"),
        SettingsSearchEntry("Accent Color", LeafTab.THEME, listOf("accent", "theme", "color", "purple", "blue", "green", "red", "orange", "hex"), uiLabel = "Accent Color"),
        SettingsSearchEntry("Light Theme", LeafTab.THEME, listOf("light", "white", "day", "bright", "theme", "mode"), uiLabel = "Light Theme"),
        SettingsSearchEntry("AMOLED Pure Black Mode", LeafTab.THEME, listOf("amoled", "oled", "pure black", "pitch black", "battery", "dark"), uiLabel = "AMOLED Pure Black Mode"),
        SettingsSearchEntry("App Background Theme", LeafTab.THEME, listOf("background", "navy", "midnight", "slate", "mocha", "pure black", "custom"), uiLabel = "Theme & Colors"),
        SettingsSearchEntry("Background Gradient", LeafTab.THEME, listOf("gradient", "radial", "linear", "background", "glow", "color"), uiLabel = "Background Gradient"),
        SettingsSearchEntry("App Font / Typography", LeafTab.THEME, listOf("font", "text", "typeface", "typography", "inter", "roboto", "custom font"), uiLabel = "App Font"),

        // Layout tab
        SettingsSearchEntry("Navigation Dock Position", LeafTab.LAYOUT, listOf("sidebar", "dock", "left", "right", "top", "bottom", "navigation"), uiLabel = "Dock Position"),
        SettingsSearchEntry("Hero Carousel / Slider", LeafTab.LAYOUT, listOf("hero", "carousel", "banner", "home", "slider", "featured"), uiLabel = "Enable Hero Slider"),
        SettingsSearchEntry("Details Page Sections & Layout", LeafTab.DETAILS, listOf("details", "order", "drag", "section", "layout", "modular", "time", "badges"), uiLabel = "Details Page Sections & Layout"),
        SettingsSearchEntry("Lock Unreleased Episodes", LeafTab.DETAILS, listOf("lock", "unreleased", "episodes", "future", "upcoming", "air date", "countdown", "schedule", "anime", "protect"), uiLabel = "Lock Unreleased Episodes"),
        SettingsSearchEntry("Anti-Spoiler Mode", LeafTab.DETAILS, listOf("spoiler", "anti-spoiler", "hide", "blur", "thumbnails", "descriptions", "episodes"), uiLabel = "Anti-Spoiler Mode"),
        SettingsSearchEntry("Details Current & End Time Badges", LeafTab.DETAILS, listOf("time", "current", "end time", "badges", "details", "clock"), uiLabel = "Show Current / End Time"),
        SettingsSearchEntry("Poster Layout Editor", LeafTab.LAYOUT, listOf("poster", "editor", "preview", "customize", "width", "spacing", "rounding"), uiLabel = "Poster Editor", subScreen = SettingsSubScreen.POSTER_EDITOR),
        SettingsSearchEntry("Show Rating Badge", LeafTab.LAYOUT, listOf("poster", "rating", "score", "star", "badge"), uiLabel = "Show Rating / Score"),
        SettingsSearchEntry("Show Quality Badge", LeafTab.LAYOUT, listOf("poster", "quality", "resolution", "hd", "4k", "badge"), uiLabel = "Show Quality (HD / 4K)"),
        SettingsSearchEntry("Show Language Badge", LeafTab.LAYOUT, listOf("poster", "language", "dub", "sub", "badge"), uiLabel = "Show Language (Sub / Dub)"),
        SettingsSearchEntry("UI Element Shadows", LeafTab.LAYOUT, listOf("element", "shadow", "drop", "ui", "depth", "elevation"), uiLabel = "Enable UI Element Shadows"),
        SettingsSearchEntry("Text Drop Shadow", LeafTab.LAYOUT, listOf("text", "shadow", "drop", "readability", "blur"), uiLabel = "Enable Text Shadows"),

        // Effects tab
        SettingsSearchEntry("Dynamic Backdrop Blur", LeafTab.EFFECTS, listOf("hero", "blur", "background", "backdrop", "frosted", "glass", "gaussian", "details"), uiLabel = "Header & Details Backdrop Blur"),
        SettingsSearchEntry("Backdrop Blur Softness", LeafTab.EFFECTS, listOf("blur", "softness", "radius", "diffusion", "intensity"), uiLabel = "Backdrop Blur Softness"),
        SettingsSearchEntry("Backdrop Darkening", LeafTab.EFFECTS, listOf("blur", "darkening", "overlay", "brightness", "contrast", "dim"), uiLabel = "Backdrop Darkening"),
        SettingsSearchEntry("UI Container & Card Opacity", LeafTab.EFFECTS, listOf("glassmorphism", "opacity", "translucency", "card", "container", "transparent"), uiLabel = "UI Container & Card Opacity"),
        SettingsSearchEntry("Ambient Glow", LeafTab.EFFECTS, listOf("ambient", "glow", "background", "aura", "position", "intensity"), uiLabel = "Ambient Glow"),
        SettingsSearchEntry("Background Wallpaper", LeafTab.EFFECTS, listOf("background", "wallpaper", "image", "custom", "vignette", "tint"), uiLabel = "Background Wallpaper"),
        SettingsSearchEntry("Screensaver / Clock & Date", LeafTab.EFFECTS, listOf("screensaver", "clock", "date", "time", "idle", "format"), uiLabel = "Clock & Date"),

        // Player tab
        SettingsSearchEntry("Hardware Decoding", LeafTab.PLAYER, listOf("hwdec", "hardware", "decoding", "acceleration", "gpu", "mpv"), uiLabel = "Hardware Acceleration"),
        SettingsSearchEntry("Preferred Stream Quality", LeafTab.PLAYER, listOf("quality", "resolution", "1080p", "720p", "4k", "stream"), uiLabel = "Preferred Stream Quality"),
        SettingsSearchEntry("Auto Play Next Episode", LeafTab.PLAYER, listOf("auto", "play", "next", "episode", "binge"), uiLabel = "Auto Play"),
        SettingsSearchEntry("Wait for Links before Auto Play", LeafTab.PLAYER, listOf("auto", "play", "wait", "links"), uiLabel = "Wait for links before Auto-playing"),
        SettingsSearchEntry("Auto Play Timeout", LeafTab.PLAYER, listOf("auto", "play", "timeout", "delay"), uiLabel = "Playback Timeout"),
        SettingsSearchEntry("Smooth Video / Interpolation", LeafTab.PLAYER, listOf("interpolation", "smooth", "motion", "video", "60fps"), uiLabel = "Smooth Video"),
        SettingsSearchEntry("Volume Normalization", LeafTab.PLAYER, listOf("audio", "normalization", "volume", "loudness", "night mode"), uiLabel = "Volume Normalization (Stable Audio)"),
        SettingsSearchEntry("Spatial Audio", LeafTab.PLAYER, listOf("audio", "spatializer", "surround", "3d", "stereo"), uiLabel = "Spatial Audio (Stereo Widener)"),
        SettingsSearchEntry("Equalizer Profile", LeafTab.PLAYER, listOf("audio", "equalizer", "eq", "preset", "bass", "treble"), uiLabel = "Equalizer Profile"),
        SettingsSearchEntry("Audio Sync / Delay Offset", LeafTab.PLAYER, listOf("audio", "delay", "sync", "offset", "lip sync"), uiLabel = "Audio Sync (Delay Offset)"),
        SettingsSearchEntry("Intro & Outro Skipping", LeafTab.PLAYER, listOf("skip", "intro", "outro", "openings", "endings", "aniskip"), uiLabel = "Intro & Outro Skipping"),
        SettingsSearchEntry("Subtitle Styling & Customization", LeafTab.PLAYER, listOf("subtitle", "font", "color", "size", "background", "border", "shadow", "ass", "srt"), uiLabel = "Subtitle Styling", subScreen = SettingsSubScreen.SUBTITLES),

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

        // Extensions > Integrations tab
        SettingsSearchEntry("TMDB API Key & Metadata", LeafTab.INTEGRATIONS, listOf("tmdb", "metadata", "api key", "backdrops", "logos", "cast", "posters"), uiLabel = "TMDB"),
        SettingsSearchEntry("AniList Metadata", LeafTab.INTEGRATIONS, listOf("anilist", "anime", "metadata", "voice", "cast", "characters"), uiLabel = "AniList Metadata"),
        SettingsSearchEntry("Kitsu Anime Metadata", LeafTab.INTEGRATIONS, listOf("kitsu", "anime", "metadata"), uiLabel = "Kitsu"),
        SettingsSearchEntry("Custom Stremio Addon", LeafTab.INTEGRATIONS, listOf("stremio", "addon", "manifest", "metadata", "catalog", "cinemeta"), uiLabel = "Custom Stremio Addon"),
        SettingsSearchEntry("Intro Skip Service API", LeafTab.INTEGRATIONS, listOf("skip", "intro", "outro", "aniskip", "introdb", "api"), uiLabel = "Intro & Outro Skipping"),

        // Network tab
        SettingsSearchEntry("DNS over HTTPS (DoH)", LeafTab.NETWORK, listOf("dns", "https", "doh", "cloudflare", "quad9", "adguard", "google", "network", "isp", "bypass"), uiLabel = "DNS over HTTPS (DoH)"),
        SettingsSearchEntry("Security & Browser Isolation", LeafTab.NETWORK, listOf("security", "browser", "isolation", "proxy", "vpn", "user agent", "cloudflare"), uiLabel = "Security & Browser Isolation"),

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
