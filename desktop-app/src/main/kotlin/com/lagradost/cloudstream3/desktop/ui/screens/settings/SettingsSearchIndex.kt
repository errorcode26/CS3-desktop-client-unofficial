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
        // Interface & Design
        SettingsSearchEntry("Theme Preset", SettingsTab.APPEARANCE, listOf("preset", "theme", "color"), uiLabel = "Theme Presets", subScreen = SettingsSubScreen.THEME),
        SettingsSearchEntry("Accent Color", SettingsTab.APPEARANCE, listOf("accent", "theme", "color"), uiLabel = "Theme & Colors", subScreen = SettingsSubScreen.THEME),
        SettingsSearchEntry("Light Mode", SettingsTab.APPEARANCE, listOf("light", "dark", "theme"), uiLabel = "Light Theme", subScreen = SettingsSubScreen.THEME),
        SettingsSearchEntry("App Background", SettingsTab.APPEARANCE, listOf("background", "wallpaper", "color"), uiLabel = "Theme & Colors", subScreen = SettingsSubScreen.THEME),
        SettingsSearchEntry("App Font", SettingsTab.APPEARANCE, listOf("font", "text", "typeface"), uiLabel = "App Font", subScreen = SettingsSubScreen.THEME),

        // Display & Layout
        SettingsSearchEntry("Home Screen Spacing", SettingsTab.APPEARANCE, listOf("home", "spacing", "gap"), uiLabel = "Home Page Spacing", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Sidebar Dock Position", SettingsTab.APPEARANCE, listOf("sidebar", "dock", "left", "right"), uiLabel = "Dock Position", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Hero Carousel Banner", SettingsTab.APPEARANCE, listOf("hero", "carousel", "banner", "home"), uiLabel = "Enable Hero Slider", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Hero Auto-Slide Delay", SettingsTab.APPEARANCE, listOf("hero", "slide", "delay"), uiLabel = "Hero Auto-Slide Delay", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Poster Title Position", SettingsTab.APPEARANCE, listOf("poster", "title", "text"), uiLabel = "Poster Title Position", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Poster Width", SettingsTab.APPEARANCE, listOf("poster", "width", "size"), uiLabel = "Poster Width", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Poster Corner Rounding", SettingsTab.APPEARANCE, listOf("poster", "corner", "rounding", "radius"), uiLabel = "Poster Corner Radius", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Show Rating on Poster", SettingsTab.APPEARANCE, listOf("poster", "rating", "score", "star"), uiLabel = "Show Rating / Score", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Show Quality on Poster", SettingsTab.APPEARANCE, listOf("poster", "quality", "resolution"), uiLabel = "Show Quality (HD / 4K)", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Show Language on Poster", SettingsTab.APPEARANCE, listOf("poster", "language", "dub", "sub"), uiLabel = "Show Language (Sub / Dub)", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Text Drop Shadow", SettingsTab.APPEARANCE, listOf("text", "shadow", "drop"), uiLabel = "Enable Text Shadows", subScreen = SettingsSubScreen.LAYOUT),
        SettingsSearchEntry("Element Drop Shadows", SettingsTab.APPEARANCE, listOf("element", "shadow", "drop"), uiLabel = "Enable UI Element Shadows", subScreen = SettingsSubScreen.LAYOUT),

        // Details Page Layout
        SettingsSearchEntry("Details Page Section Reordering", SettingsTab.APPEARANCE, listOf("details", "order", "drag", "section", "layout"), uiLabel = "Details Page Sections & Layout", subScreen = SettingsSubScreen.DETAILS_LAYOUT),
        SettingsSearchEntry("Details Page Modular Sections", SettingsTab.APPEARANCE, listOf("details", "modular", "sections", "episodes", "cast", "trailer"), uiLabel = "Details Page Sections & Layout", subScreen = SettingsSubScreen.DETAILS_LAYOUT),

        // Visual Effects & Blur
        SettingsSearchEntry("Header & Details Backdrop Blur", SettingsTab.APPEARANCE, listOf("hero", "blur", "background", "backdrop", "frosted"), uiLabel = "Header & Details Backdrop Blur", subScreen = SettingsSubScreen.EFFECTS),
        SettingsSearchEntry("Backdrop Blur Softness", SettingsTab.APPEARANCE, listOf("blur", "softness", "radius", "diffusion"), uiLabel = "Backdrop Blur Softness", subScreen = SettingsSubScreen.EFFECTS),
        SettingsSearchEntry("Backdrop Darkening", SettingsTab.APPEARANCE, listOf("blur", "darkening", "overlay", "brightness"), uiLabel = "Backdrop Darkening", subScreen = SettingsSubScreen.EFFECTS),
        SettingsSearchEntry("UI Container Opacity", SettingsTab.APPEARANCE, listOf("glassmorphism", "opacity", "translucency", "card"), uiLabel = "UI Container Opacity", subScreen = SettingsSubScreen.EFFECTS),
        SettingsSearchEntry("Background Image Wallpaper", SettingsTab.APPEARANCE, listOf("background", "wallpaper", "image"), uiLabel = "Background Wallpaper", subScreen = SettingsSubScreen.EFFECTS),
        SettingsSearchEntry("Screensaver", SettingsTab.APPEARANCE, listOf("screensaver", "clock", "idle"), uiLabel = "Clock & Date", subScreen = SettingsSubScreen.EFFECTS),
        SettingsSearchEntry("Screensaver Clock Mode", SettingsTab.APPEARANCE, listOf("clock", "time", "date"), uiLabel = "Display Mode", subScreen = SettingsSubScreen.EFFECTS),
        SettingsSearchEntry("Ambient Background Glow", SettingsTab.APPEARANCE, listOf("ambient", "glow", "background"), uiLabel = "Ambient Glow", subScreen = SettingsSubScreen.EFFECTS),

        // Playback & Media
        SettingsSearchEntry("Hardware Decoding", SettingsTab.PLAYER, listOf("hwdec", "hardware", "decoding", "acceleration", "video"), uiLabel = "Hardware Acceleration", subScreen = SettingsSubScreen.PLAYER_CONTROLS),
        SettingsSearchEntry("Preferred Quality", SettingsTab.PLAYER, listOf("quality", "resolution", "1080p", "720p"), uiLabel = "Preferred Stream Quality", subScreen = SettingsSubScreen.PLAYER_CONTROLS),
        SettingsSearchEntry("Auto Play", SettingsTab.PLAYER, listOf("auto", "play", "next"), uiLabel = "Enable Download Buttons", subScreen = SettingsSubScreen.PLAYER_CONTROLS),
        SettingsSearchEntry("Wait for Links before Auto Play", SettingsTab.PLAYER, listOf("auto", "play", "wait", "links"), uiLabel = "Wait for links before Auto-playing", subScreen = SettingsSubScreen.PLAYER_CONTROLS),
        SettingsSearchEntry("Auto Play Timeout", SettingsTab.PLAYER, listOf("auto", "play", "timeout", "delay"), uiLabel = "Playback Timeout", subScreen = SettingsSubScreen.PLAYER_CONTROLS),
        SettingsSearchEntry("Video Interpolation", SettingsTab.PLAYER, listOf("interpolation", "smooth", "motion"), uiLabel = "Smooth Video", subScreen = SettingsSubScreen.PLAYER_CONTROLS),
        SettingsSearchEntry("Audio Normalization", SettingsTab.PLAYER, listOf("audio", "normalization", "volume", "loudness"), uiLabel = "Volume Normalization (Stable Audio)", subScreen = SettingsSubScreen.PLAYER_CONTROLS),
        SettingsSearchEntry("Audio Spatializer", SettingsTab.PLAYER, listOf("audio", "spatializer", "surround", "3d"), uiLabel = "Spatial Audio (Stereo Widener)", subScreen = SettingsSubScreen.PLAYER_CONTROLS),
        SettingsSearchEntry("Audio Equalizer Preset", SettingsTab.PLAYER, listOf("audio", "equalizer", "eq", "preset"), uiLabel = "Equalizer Profile", subScreen = SettingsSubScreen.PLAYER_CONTROLS),
        SettingsSearchEntry("Audio Delay", SettingsTab.PLAYER, listOf("audio", "delay", "sync"), uiLabel = "Audio Sync (Delay Offset)", subScreen = SettingsSubScreen.PLAYER_CONTROLS),

        // Subtitles
        SettingsSearchEntry("Subtitle Font", SettingsTab.PLAYER, listOf("subtitle", "font", "text"), uiLabel = "Subtitle Appearance", subScreen = SettingsSubScreen.SUBTITLES),
        SettingsSearchEntry("Subtitle Size", SettingsTab.PLAYER, listOf("subtitle", "size", "large", "small"), uiLabel = "Subtitle Appearance", subScreen = SettingsSubScreen.SUBTITLES),
        SettingsSearchEntry("Subtitle Color", SettingsTab.PLAYER, listOf("subtitle", "color", "text"), uiLabel = "Subtitle Appearance", subScreen = SettingsSubScreen.SUBTITLES),
        SettingsSearchEntry("Subtitle Background", SettingsTab.PLAYER, listOf("subtitle", "background", "bg"), uiLabel = "Subtitle Appearance", subScreen = SettingsSubScreen.SUBTITLES),

        // Services & Sync
        SettingsSearchEntry("AniList Tracker", SettingsTab.SERVICES, listOf("anilist", "tracker", "anime"), subScreen = SettingsSubScreen.TRACKERS),
        SettingsSearchEntry("MAL", SettingsTab.SERVICES, listOf("mal", "myanimelist", "tracker", "anime"), subScreen = SettingsSubScreen.TRACKERS),
        SettingsSearchEntry("SIMKL", SettingsTab.SERVICES, listOf("simkl", "tracker", "anime", "shows"), subScreen = SettingsSubScreen.TRACKERS),
        SettingsSearchEntry("Trakt Tracker", SettingsTab.SERVICES, listOf("trakt", "tracker", "movies", "shows"), subScreen = SettingsSubScreen.TRACKERS),

        // Metadata & Integrations
        SettingsSearchEntry("Integrations & Services", SettingsTab.SERVICES, listOf("integrations", "metadata", "services", "tmdb", "anilist", "kitsu", "stremio", "skip", "aniskip", "introdb"), subScreen = SettingsSubScreen.INTEGRATIONS),
        SettingsSearchEntry("The Movie Database (TMDB)", SettingsTab.SERVICES, listOf("tmdb", "metadata", "api key", "4k", "backdrops", "logos", "cast"), subScreen = SettingsSubScreen.INTEGRATIONS),
        SettingsSearchEntry("AniList Metadata", SettingsTab.SERVICES, listOf("anilist", "anime", "metadata", "voice", "cast"), subScreen = SettingsSubScreen.INTEGRATIONS),
        SettingsSearchEntry("Custom Stremio Addon", SettingsTab.SERVICES, listOf("stremio", "addon", "manifest", "metadata", "catalog"), subScreen = SettingsSubScreen.INTEGRATIONS),
        SettingsSearchEntry("Intro & Outro Skipping", SettingsTab.SERVICES, listOf("skip", "intro", "outro", "aniskip", "introdb", "openings", "endings"), subScreen = SettingsSubScreen.INTEGRATIONS),

        // Extensions
        SettingsSearchEntry("Browse Extensions", SettingsTab.SERVICES, listOf("extensions", "plugins", "browse"), subScreen = SettingsSubScreen.EXTENSIONS),
        SettingsSearchEntry("Installed Extensions", SettingsTab.SERVICES, listOf("extensions", "plugins", "installed"), subScreen = SettingsSubScreen.EXTENSIONS),
        SettingsSearchEntry("Repositories", SettingsTab.SERVICES, listOf("repositories", "repos", "plugins", "extensions"), subScreen = SettingsSubScreen.EXTENSIONS),

        // Network
        SettingsSearchEntry("DNS over HTTPS", SettingsTab.SYSTEM, listOf("dns", "https", "doh", "cloudflare"), uiLabel = "DNS over HTTPS (DoH)", subScreen = SettingsSubScreen.NETWORK),
        SettingsSearchEntry("VPN Workaround", SettingsTab.SYSTEM, listOf("vpn", "workaround", "proxy"), uiLabel = "Allow Experimental Cloudflare Bypass", subScreen = SettingsSubScreen.NETWORK),

        // Advanced & Storage
        SettingsSearchEntry("Clear Image Cache", SettingsTab.SYSTEM, listOf("clear", "image", "cache", "storage"), uiLabel = "Data Management", subScreen = SettingsSubScreen.ADVANCED),
        SettingsSearchEntry("Factory Reset", SettingsTab.SYSTEM, listOf("clear", "watch", "history", "delete", "reset"), uiLabel = "Danger Zone", subScreen = SettingsSubScreen.ADVANCED),

        // Developer
        SettingsSearchEntry("Developer Mode", SettingsTab.SYSTEM, listOf("developer", "mode", "debug"), subScreen = SettingsSubScreen.DEVELOPER),
        SettingsSearchEntry("Logcat Viewer", SettingsTab.SYSTEM, listOf("logcat", "viewer", "logs", "debug"), subScreen = SettingsSubScreen.DEVELOPER),

        // Updates & About
        SettingsSearchEntry("App Updates", SettingsTab.SYSTEM, listOf("update", "version", "check", "new"), subScreen = SettingsSubScreen.UPDATES),
        SettingsSearchEntry("About CloudStream", SettingsTab.SYSTEM, listOf("about", "version", "info", "license", "credits"), subScreen = SettingsSubScreen.ABOUT),
    )
}
