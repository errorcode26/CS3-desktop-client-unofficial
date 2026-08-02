# Changelogs - v2.6.0

### ✨ *New Features*

#### 📺 Media Player & Subtitles (PR #62 by @starlight5234 & PR #64 by @likhithkrishna1103-tech)
- **Subtitle Styling & Customization** – Added subtitle appearance settings, custom shadows, and text scaling support.
- **Custom Player Seek Bar** – Refactored the player progress bar into a custom seek bar component and tuned the seek step to 30 seconds.
- **Netflix-Style Scrim** – Implemented a subtle player overlay scrim on TV/Desktop to improve readability of player controls and metadata.
- **Dub Status Filtering** – Filter episodes by sub/dub status in the side panel, and added a visual dub badge to the episode rows.

#### 🔗 Integrations & Tracking (PR #62 by @starlight5234)
- **AniList Sync & Metadata** – Full integration for AniList tracking and TMDB metadata enrichment.
- **Improved Continuing Watch** – Better watch progress updates and layout improvements for Continue Watching cards.

#### 🖥️ Desktop & Window Experience (PR #64 by @likhithkrishna1103-tech)
- **Desktop Window Optimization** – Configurable default window startup size and improved sidebar contrasts.
- **Custom Titlebar & Always on Top** – Initial support for custom titlebars, always-on-top toggle, and OS window styling.

---

### 🐞 *Bug Fixes*
- 🛠️ Fixed vertical scroll jitter on recommendations carousel and resolved search screen scroll bugs.
- 🛠️ Fixed AniList logo asset links, player padding issues, and Anilist enrichment failures.
- 🛠️ Resolved Cloudflare cookie bypass and proxy header injection headers.

