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
        // Appearance Tab (Theme)
        SettingsSearchEntry("Theme Preset", SettingsTab.APPEARANCE, listOf("preset", "theme", "color"), uiLabel = "Theme Presets", subScreen = SettingsSubScreen.APPEARANCE_THEME),
        SettingsSearchEntry("Accent Color", SettingsTab.APPEARANCE, listOf("accent", "theme", "color"), uiLabel = "Theme & Colors", subScreen = SettingsSubScreen.APPEARANCE_THEME),
        SettingsSearchEntry("Light Mode", SettingsTab.APPEARANCE, listOf("light", "dark", "theme"), uiLabel = "Light Theme", subScreen = SettingsSubScreen.APPEARANCE_THEME),
        SettingsSearchEntry("App Background", SettingsTab.APPEARANCE, listOf("background", "wallpaper", "color"), uiLabel = "Theme & Colors", subScreen = SettingsSubScreen.APPEARANCE_THEME),
        SettingsSearchEntry("App Font", SettingsTab.APPEARANCE, listOf("font", "text", "typeface"), uiLabel = "App Font", subScreen = SettingsSubScreen.APPEARANCE_THEME),

        // Appearance Tab (Layout)
        SettingsSearchEntry("Home Screen Spacing", SettingsTab.APPEARANCE, listOf("home", "spacing", "gap"), uiLabel = "Home Page Spacing", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Sidebar Dock Position", SettingsTab.APPEARANCE, listOf("sidebar", "dock", "left", "right"), uiLabel = "Dock Position", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Hero Carousel Banner", SettingsTab.APPEARANCE, listOf("hero", "carousel", "banner", "home"), uiLabel = "Enable Hero Slider", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Hero Auto-Slide Delay", SettingsTab.APPEARANCE, listOf("hero", "slide", "delay"), uiLabel = "Hero Auto-Slide Delay", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Hero Background Blur", SettingsTab.APPEARANCE, listOf("hero", "blur", "background"), uiLabel = "Hero Background Blur", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Poster Title Position", SettingsTab.APPEARANCE, listOf("poster", "title", "text"), uiLabel = "Poster Title Position", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Poster Width", SettingsTab.APPEARANCE, listOf("poster", "width", "size"), uiLabel = "Poster Width", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Poster Corner Rounding", SettingsTab.APPEARANCE, listOf("poster", "corner", "rounding", "radius"), uiLabel = "Poster Corner Radius", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Show Rating on Poster", SettingsTab.APPEARANCE, listOf("poster", "rating", "score", "star"), uiLabel = "Show Rating / Score", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Show Quality on Poster", SettingsTab.APPEARANCE, listOf("poster", "quality", "resolution"), uiLabel = "Show Quality (HD / 4K)", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Show Language on Poster", SettingsTab.APPEARANCE, listOf("poster", "language", "dub", "sub"), uiLabel = "Show Language (Sub / Dub)", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Text Drop Shadow", SettingsTab.APPEARANCE, listOf("text", "shadow", "drop"), uiLabel = "Enable Text Shadows", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),
        SettingsSearchEntry("Element Drop Shadows", SettingsTab.APPEARANCE, listOf("element", "shadow", "drop"), uiLabel = "Enable UI Element Shadows", subScreen = SettingsSubScreen.APPEARANCE_LAYOUT),

        // Appearance Tab (Effects)
        SettingsSearchEntry("Background Image Wallpaper", SettingsTab.APPEARANCE, listOf("background", "wallpaper", "image"), uiLabel = "Background Wallpaper", subScreen = SettingsSubScreen.APPEARANCE_EFFECTS),
        SettingsSearchEntry("Screensaver", SettingsTab.APPEARANCE, listOf("screensaver", "clock", "idle"), uiLabel = "Clock & Date", subScreen = SettingsSubScreen.APPEARANCE_EFFECTS),
        SettingsSearchEntry("Screensaver Clock Mode", SettingsTab.APPEARANCE, listOf("clock", "time", "date"), uiLabel = "Display Mode", subScreen = SettingsSubScreen.APPEARANCE_EFFECTS),
        SettingsSearchEntry("Ambient Background Glow", SettingsTab.APPEARANCE, listOf("ambient", "glow", "background"), uiLabel = "Ambient Glow", subScreen = SettingsSubScreen.APPEARANCE_EFFECTS),

        // Player Tab (Main & Subtitle)
        SettingsSearchEntry("Hardware Decoding", SettingsTab.PLAYER, listOf("hwdec", "hardware", "decoding", "acceleration", "video"), uiLabel = "Hardware Acceleration"),
        SettingsSearchEntry("Preferred Quality", SettingsTab.PLAYER, listOf("quality", "resolution", "1080p", "720p"), uiLabel = "Preferred Stream Quality"),
        SettingsSearchEntry("Auto Play", SettingsTab.PLAYER, listOf("auto", "play", "next"), uiLabel = "Enable Download Buttons"),
        SettingsSearchEntry("Wait for Links before Auto Play", SettingsTab.PLAYER, listOf("auto", "play", "wait", "links"), uiLabel = "Wait for links before Auto-playing"),
        SettingsSearchEntry("Auto Play Timeout", SettingsTab.PLAYER, listOf("auto", "play", "timeout", "delay"), uiLabel = "Playback Timeout"),
        SettingsSearchEntry("Video Interpolation", SettingsTab.PLAYER, listOf("interpolation", "smooth", "motion"), uiLabel = "Smooth Video"),
        SettingsSearchEntry("Audio Normalization", SettingsTab.PLAYER, listOf("audio", "normalization", "volume", "loudness"), uiLabel = "Volume Normalization (Stable Audio)"),
        SettingsSearchEntry("Audio Spatializer", SettingsTab.PLAYER, listOf("audio", "spatializer", "surround", "3d"), uiLabel = "Spatial Audio (Stereo Widener)"),
        SettingsSearchEntry("Audio Equalizer Preset", SettingsTab.PLAYER, listOf("audio", "equalizer", "eq", "preset"), uiLabel = "Equalizer Profile"),
        SettingsSearchEntry("Audio Delay", SettingsTab.PLAYER, listOf("audio", "delay", "sync"), uiLabel = "Audio Sync (Delay Offset)"),
        SettingsSearchEntry("Subtitle Font", SettingsTab.PLAYER, listOf("subtitle", "font", "text"), uiLabel = "Subtitle Appearance", subScreen = SettingsSubScreen.SUBTITLE_EDITOR),
        SettingsSearchEntry("Subtitle Size", SettingsTab.PLAYER, listOf("subtitle", "size", "large", "small"), uiLabel = "Subtitle Appearance", subScreen = SettingsSubScreen.SUBTITLE_EDITOR),
        SettingsSearchEntry("Subtitle Color", SettingsTab.PLAYER, listOf("subtitle", "color", "text"), uiLabel = "Subtitle Appearance", subScreen = SettingsSubScreen.SUBTITLE_EDITOR),
        SettingsSearchEntry("Subtitle Background", SettingsTab.PLAYER, listOf("subtitle", "background", "bg"), uiLabel = "Subtitle Appearance", subScreen = SettingsSubScreen.SUBTITLE_EDITOR),

        // Network Tab
        SettingsSearchEntry("DNS over HTTPS", SettingsTab.NETWORK, listOf("dns", "https", "doh", "cloudflare"), uiLabel = "DNS over HTTPS (DoH)"),
        SettingsSearchEntry("VPN Workaround", SettingsTab.NETWORK, listOf("vpn", "workaround", "proxy"), uiLabel = "Allow Experimental Cloudflare Bypass"),

        // Advanced Tab
        SettingsSearchEntry("Clear Image Cache", SettingsTab.ADVANCED, listOf("clear", "image", "cache", "storage"), uiLabel = "Data Management"),
        SettingsSearchEntry("Factory Reset", SettingsTab.ADVANCED, listOf("clear", "watch", "history", "delete", "reset"), uiLabel = "Danger Zone"),

        // Developer Tab
        SettingsSearchEntry("Developer Mode", SettingsTab.DEVELOPER, listOf("developer", "mode", "debug")),
        SettingsSearchEntry("Logcat Viewer", SettingsTab.DEVELOPER, listOf("logcat", "viewer", "logs", "debug")),

        // Extensions Tab
        SettingsSearchEntry("Browse Extensions", SettingsTab.EXTENSIONS, listOf("extensions", "plugins", "browse")),
        SettingsSearchEntry("Installed Extensions", SettingsTab.EXTENSIONS, listOf("extensions", "plugins", "installed")),
        SettingsSearchEntry("Repositories", SettingsTab.EXTENSIONS, listOf("repositories", "repos", "plugins", "extensions")),

        // Trackers Tab
        SettingsSearchEntry("AniList Tracker", SettingsTab.TRACKERS, listOf("anilist", "tracker", "anime")),
        SettingsSearchEntry("MAL", SettingsTab.TRACKERS, listOf("mal", "myanimelist", "tracker", "anime")),
        SettingsSearchEntry("SIMKL", SettingsTab.TRACKERS, listOf("simkl", "tracker", "anime", "shows")),

        // Integrations Tab
        SettingsSearchEntry("Integrations & Services", SettingsTab.INTEGRATIONS, listOf("integrations", "metadata", "services", "tmdb", "anilist", "kitsu", "stremio", "skip", "aniskip", "introdb")),
        SettingsSearchEntry("The Movie Database (TMDB)", SettingsTab.INTEGRATIONS, listOf("tmdb", "metadata", "api key", "4k", "backdrops", "logos", "cast")),
        SettingsSearchEntry("AniList Metadata", SettingsTab.INTEGRATIONS, listOf("anilist", "anime", "metadata", "voice", "cast")),
        SettingsSearchEntry("Custom Stremio Addon", SettingsTab.INTEGRATIONS, listOf("stremio", "addon", "manifest", "metadata", "catalog")),
        SettingsSearchEntry("Intro & Outro Skipping", SettingsTab.INTEGRATIONS, listOf("skip", "intro", "outro", "aniskip", "introdb", "openings", "endings")),
    )
}
