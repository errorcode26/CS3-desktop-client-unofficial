    // Bridge: JS -> Kotlin
    // Use window.send so inline onclick="send(...)" attributes can call it
    window.send = (type, value) => {
        const payload = { type, value: value === undefined ? '' : String(value) };
        const bridge = window.chrome && window.chrome.webview;
        if (bridge) bridge.postMessage(payload);
        else console.log('[JS→Kotlin]', payload);
    };
    const send = window.send; // local alias for script-internal use

    const fmt = (ms) => {
        if (!ms || ms < 0 || isNaN(ms)) return '0:00';
        const s = Math.floor(ms / 1000);
        const h = Math.floor(s / 3600);
        const m = Math.floor((s % 3600) / 60);
        const sec = s % 60;
        const mm = String(m).padStart(h > 0 ? 2 : 1, '0');
        const ss = String(sec).padStart(2, '0');
        return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
    };

    const escapeHtml = (str) => {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    };

    const normalizeAudioName = (name) => {
        if (!name) return '';
        return String(name)
            .replace(/\s*\([^)]*(?:stereo|surround|mono|\d+\.\d+|ch)[^)]*\)/gi, '')
            .replace(/\s*\[[^\]]*\]/g, '')
            .toLowerCase()
            .trim();
    };
    window.normalizeAudioName = normalizeAudioName;

    const isTrackMatchingResolution = (trackName, trackUrl, meta) => {
        if (!meta) return false;
        if (meta.activeLazyVideoTrackUrl && trackUrl) {
            return trackUrl === meta.activeLazyVideoTrackUrl;
        }
        if (!trackName) return false;

        const resParts = meta.resolution ? meta.resolution.split('x') : [];
        const w = resParts[0] ? parseInt(resParts[0].trim(), 10) : 0;
        const h = resParts[1] ? parseInt(resParts[1].trim(), 10) : 0;

        const nameLower = String(trackName).toLowerCase().trim();

        // 1. Direct height matching: e.g. "1080p", "720p", "480p", "2160p"
        if (h > 0) {
            if (nameLower.includes(h + 'p') || nameLower.startsWith(h.toString())) return true;
            // Cinemascope / ultrawide letterbox aspect ratio tolerance:
            // 1080p stream cropped to 2.40:1 (1920x800, 1920x804, 1920x816) matches 1080p track
            if (w === 1920 && nameLower.includes('1080')) return true;
            // 720p ultrawide (1280x534..1280x720) matches 720p track
            if (w === 1280 && nameLower.includes('720')) return true;
            // 4K ultrawide (3840x1600..3840x2160) matches 4k/2160p track
            if (w === 3840 && (nameLower.includes('2160') || nameLower.includes('4k'))) return true;
            // Standard height ranges
            if (h >= 700 && h <= 1080 && nameLower.includes('1080')) return true;
            if (h >= 480 && h < 700 && nameLower.includes('720')) return true;
        }

        // 2. Direct width matching: e.g. "1920", "1280"
        if (w > 0 && (nameLower.includes(w + 'p') || nameLower.startsWith(w.toString()))) {
            return true;
        }

        return false;
    };

    const isTrackHD = (trackName, meta) => {
        const nameLower = String(trackName || '').toLowerCase();
        if (nameLower.includes('1080') || nameLower.includes('720') || nameLower.includes('2160') || nameLower.includes('4k')) {
            return true;
        }
        if (meta && meta.resolution) {
            const resParts = meta.resolution.split('x');
            const w = resParts[0] ? parseInt(resParts[0].trim(), 10) : 0;
            const h = resParts[1] ? parseInt(resParts[1].trim(), 10) : 0;
            if (h >= 720 || w >= 1280) return true;
        }
        return false;
    };

    // State
    let currentSpeed = 1.0;
    window.currentSpeed = 1.0;
    let isSeeking = false, durationMs = 0, currentPosMs = 0;
    let isMuted = false, currentVolume = 100;
    let isMenuOpen = false;
    let subDelaySec = 0, audioDelaySec = 0;
    let currentTitle = '', currentEpisodeId = '', resumeHandled = false, userDismissedProbing = false, pendingResumeMs = 0;
    let isAppLoading = false;
    let linksData = [];
    let maturityAdvisoryTimer = null;
    let lastShownAdvisoryMedia = '';
    let currentLinkIndex = -1;
    let seekLockTimer = null;
    let episodesData = [];
    let loadingTimer = null;
    let isCurrentlyLoading = false;
    let globalIsLoading = false;   // set by C++ via state_update (core_idle || paused-for-cache)
    let globalIsPlaying = false;
    let endCountdownTimer = null;
    window.autoPlayEnabled = true;
    let _cachedChapters = [];
    let _cachedSkipIntervals = [];
    let _activeChapterIndex = -1;
    let pauseInfoMode = 'delay_5s'; // 'delay_5s', 'delay_10s', 'delay_20s', 'immediate', 'off'
    let showPauseCast = true;
    let currentCastList = [];
    let pauseInfoTimer = null;

    // Element References
    const overlay       = document.getElementById('overlay');
    const playPauseBtn  = document.getElementById('playPauseBtn');
    const muteBtn       = document.getElementById('muteBtn');
    const seekBar       = document.getElementById('seekBar');
    const seekFill      = document.getElementById('seekFill');
    const seekBuffer    = document.getElementById('seekBuffer');
    const volumeBar     = document.getElementById('volumeBar');
    const timeDisplay   = document.getElementById('timeDisplay');
    const fullscreenBtn = document.getElementById('fullscreenBtn');
    const backBtn       = document.getElementById('backBtn');
    const loadingContainer = document.getElementById('loadingContainer');
    const loadingStatus = document.getElementById('loadingStatus');
    const titleDisplay  = document.getElementById('titleDisplay');
    const nextEpBtn     = document.getElementById('nextEpBtn');
    const episodesBtn   = document.getElementById('episodesBtn');
    const episodesPanel = document.getElementById('episodesPanel');
    const chaptersBtn   = document.getElementById('chaptersBtn');
    const chaptersPanel = document.getElementById('chaptersPanel');
    const chaptersList  = document.getElementById('chaptersList');
    const chaptersSubtitle = document.getElementById('chaptersSubtitle');
    const resumeOverlay = document.getElementById('resumeOverlay');
    const videoEndedOverlay = document.getElementById('videoEndedOverlay');
    const seasonSelectWrap = document.getElementById('seasonSelectWrap');
    const seasonSelect     = document.getElementById('seasonSelect');
    const seekWrap      = document.getElementById('seekWrap');
    const seekChapters  = document.getElementById('seekChapters');
    const seekTooltip   = document.getElementById('seekTooltip');


    // Zone references
    const zoneLeft          = document.getElementById('zoneLeft');
    const zoneCenter        = document.getElementById('zoneCenter');
    const zoneRight         = document.getElementById('zoneRight');
    const ctxMenu           = document.getElementById('contextMenuOverlay');

    // ── Hard-reset every overlay/timer atomically when a new playback session begins.
    // This is the single source of truth that kills race conditions on re-entry.
    function hardResetAllOverlays() {
        // Kill all pending timers that could fire from a stale session
        if (window.resumeDismissTimer) { clearTimeout(window.resumeDismissTimer); window.resumeDismissTimer = null; }
        if (window.probingDismissTimer) { clearTimeout(window.probingDismissTimer); window.probingDismissTimer = null; }
        if (loadingTimer) { clearTimeout(loadingTimer); loadingTimer = null; }
        if (endCountdownTimer) { clearInterval(endCountdownTimer); endCountdownTimer = null; }

        // Reset all session-scoped JS state
        resumeHandled = false;
        userDismissedProbing = false;
        pendingResumeMs = 0;
        durationMs = 0;
        currentPosMs = 0;
        isSeeking = false;
        isCurrentlyLoading = false;
        globalIsLoading = false;
        globalIsPlaying = false;
        window.sessionStartTime = Date.now();

        // Force all overlays to their correct initial state
        // The probing screen should ALWAYS act as the loading screen for new sessions.
        const pOverlay = document.getElementById('linkProbingOverlay');
        const pContent = document.getElementById('linkProbingContent');
        if (pOverlay)  { pOverlay.classList.add('active'); pOverlay.classList.remove('dismissing'); }
        if (pContent)  { pContent.classList.remove('dismissing'); }

        const videoEndedOvl = document.getElementById('videoEndedOverlay');
        if (videoEndedOvl) videoEndedOvl.style.display = 'none';

        if (resumeOverlay) resumeOverlay.style.display = 'none';
        if (loadingContainer) loadingContainer.classList.remove('show');

        clearPauseInfoTimer();
        const pauseOverlay = document.getElementById('pauseInfoOverlay');
        const pauseBackdrop = document.getElementById('pauseBackdrop');
        if (pauseOverlay) { pauseOverlay.classList.remove('visible'); }
        if (pauseBackdrop) { pauseBackdrop.classList.remove('visible'); }

        const banner = document.getElementById('maturityAdvisoryBanner');
        if (banner) banner.classList.remove('active');
        if (maturityAdvisoryTimer) {
            clearTimeout(maturityAdvisoryTimer);
            maturityAdvisoryTimer = null;
        }
        hasShownMaturityAdvisoryThisSession = false;

        // Ensure the main player UI base state is restored internally (visibility handled by evaluateUIStates)
        const mainOverlay = document.getElementById('overlay');
        if (mainOverlay) { mainOverlay.style.display = ''; mainOverlay.style.opacity = ''; }

        if (seekFill) seekFill.style.width = '0%';
        if (seekBar) seekBar.value = 0;
        if (seekBuffer) seekBuffer.style.width = '0%';
        if (timeDisplay) timeDisplay.innerText = '0:00 / 0:00';
        chaptersBtn?.classList.add('hidden');
    }

    function updatePauseInfoBadge() {
        const badge = document.getElementById('ctxPauseInfoBadge');
        if (!badge) return;
        switch (pauseInfoMode) {
            case 'delay_5s': badge.innerText = '5s'; break;
            case 'delay_10s': badge.innerText = '10s'; break;
            case 'delay_20s': badge.innerText = '20s'; break;
            case 'immediate': badge.innerText = '0s'; break;
            case 'off': badge.innerText = 'Off'; break;
            default: badge.innerText = '5s'; break;
        }
        badge.style.color = (pauseInfoMode === 'off') ? 'rgba(255, 255, 255, 0.45)' : '#9D4EDD';
    }

    function clearPauseInfoTimer() {
        if (pauseInfoTimer) {
            clearTimeout(pauseInfoTimer);
            pauseInfoTimer = null;
        }
    }

    function hidePauseInfoOverlay() {
        clearPauseInfoTimer();
        const pauseOverlay = document.getElementById('pauseInfoOverlay');
        const pauseBackdrop = document.getElementById('pauseBackdrop');
        const pauseCast = document.getElementById('pauseInfoCast');
        const castShowcase = document.getElementById('pauseCastShowcase');
        if (pauseOverlay) pauseOverlay.classList.remove('visible');
        if (pauseBackdrop) pauseBackdrop.classList.remove('visible');
        if (pauseCast) pauseCast.classList.remove('visible');
        if (castShowcase) castShowcase.classList.remove('active');
    }

    function schedulePauseInfoOverlay() {
        clearPauseInfoTimer();
        const pOverlay = document.getElementById('linkProbingOverlay');
        const isProbing = pOverlay && pOverlay.classList.contains('active');
        const videoEndedOvl = document.getElementById('videoEndedOverlay');
        const isVideoEnded = videoEndedOvl && videoEndedOvl.style.display === 'flex';

        if (globalIsPlaying || isProbing || isAppLoading || isVideoEnded || pauseInfoMode === 'off') {
            hidePauseInfoOverlay();
            return;
        }

        const pauseOverlay = document.getElementById('pauseInfoOverlay');
        const pauseBackdrop = document.getElementById('pauseBackdrop');
        const pauseCast = document.getElementById('pauseInfoCast');
        if (!pauseOverlay) return;

        const showOverlays = () => {
            pauseOverlay.classList.add('visible');
            if (pauseBackdrop) pauseBackdrop.classList.add('visible');
            if (showPauseCast && currentCastList && currentCastList.length > 0 && pauseCast) {
                pauseCast.classList.add('visible');
            }
            const advisoryBanner = document.getElementById('maturityAdvisoryBanner');
            if (advisoryBanner) advisoryBanner.classList.remove('active');
        };

        if (pauseInfoMode === 'immediate') {
            showOverlays();
            return;
        }

        let delayMs = 5000;
        if (pauseInfoMode === 'delay_10s') delayMs = 10000;
        else if (pauseInfoMode === 'delay_20s') delayMs = 20000;

        pauseInfoTimer = setTimeout(() => {
            const currentProbing = document.getElementById('linkProbingOverlay')?.classList.contains('active');
            const currentEnded = document.getElementById('videoEndedOverlay')?.style.display === 'flex';
            if (!globalIsPlaying && !currentProbing && !isAppLoading && !currentEnded && pauseInfoMode !== 'off') {
                showOverlays();
            }
        }, delayMs);
    }

    function dismissResumeOverlay() {
        if (window.resumeDismissTimer) {
            clearTimeout(window.resumeDismissTimer);
            window.resumeDismissTimer = null;
        }
        if (resumeOverlay && resumeOverlay.style.display !== 'none' && !resumeOverlay.classList.contains('dismissing')) {
            resumeOverlay.classList.add('dismissing');
            setTimeout(() => {
                resumeOverlay.style.display = 'none';
                resumeOverlay.classList.remove('dismissing');
            }, 280);
        }
        resumeHandled = true;
    }

    function evaluateResumeOverlay() {
        const pOverlay = document.getElementById('linkProbingOverlay');
        const isProbing = pOverlay && pOverlay.classList.contains('active');
        const videoEndedOvl = document.getElementById('videoEndedOverlay');
        const isVideoEnded = videoEndedOvl && videoEndedOvl.style.display === 'flex';

        // Only display resume pill if at least 10s into video and not at the very end
        const isEligible = pendingResumeMs >= 10000 && (durationMs <= 0 || pendingResumeMs < (durationMs - 15000));
        const shouldShow = isEligible && !resumeHandled && !isProbing && !isAppLoading && !isVideoEnded;

        if (shouldShow) {
            const timeElem = document.getElementById('resumeTime');
            if (timeElem) timeElem.innerText = fmt(pendingResumeMs);
            if (resumeOverlay && resumeOverlay.style.display !== 'flex') {
                resumeOverlay.classList.remove('dismissing');
                resumeOverlay.style.display = 'flex';
                void resumeOverlay.offsetWidth;
            }
            if (!window.resumeDismissTimer) {
                window.resumeDismissTimer = setTimeout(() => {
                    dismissResumeOverlay();
                }, 7000);
            }
        } else {
            if (resumeOverlay && !resumeOverlay.classList.contains('dismissing')) {
                resumeOverlay.style.display = 'none';
            }
        }
    }

    function evaluateUIStates() {
        const pOverlay = document.getElementById('linkProbingOverlay');
        const isProbing = pOverlay && pOverlay.classList.contains('active');
        const videoEndedOvl = document.getElementById('videoEndedOverlay');
        const isVideoEnded = videoEndedOvl && videoEndedOvl.style.display === 'flex';
        
        // Master visibility control for the main player UI
        const mainOverlay = document.getElementById('overlay');
        if (mainOverlay) {
            if (isProbing) {
                // Hide controls during probing to prevent bleeding/flashing.
                // 350ms buffer timer prevents jarring flashes if loading is ultra-fast.
                if (!window.hideMainUiTimer && !mainOverlay.classList.contains('hide-main-ui')) {
                    window.hideMainUiTimer = setTimeout(() => {
                        mainOverlay.classList.add('hide-main-ui');
                    }, 350);
                }
            } else {
                // Both probing and loading are finished; first frame is ready. Unhide smoothly.
                if (window.hideMainUiTimer) { clearTimeout(window.hideMainUiTimer); window.hideMainUiTimer = null; }
                if (mainOverlay.classList.contains('hide-main-ui')) {
                    mainOverlay.classList.remove('hide-main-ui');
                }
            }
        }
        
        // 1. Pause Info Overlay (Configurable Delay / Immediate / Off)
        if (globalIsPlaying || isProbing || isAppLoading || isVideoEnded || pauseInfoMode === 'off') {
            hidePauseInfoOverlay();
        } else {
            const pauseOverlay = document.getElementById('pauseInfoOverlay');
            if (!pauseInfoTimer && pauseOverlay && !pauseOverlay.classList.contains('visible')) {
                schedulePauseInfoOverlay();
            }
        }
        
        // 2. Loading Container (Spinner)
        // If the video is actively playing, only genuine native buffering (stalled cache) can show the spinner.
        // Micro-seeks should NEVER force the center loading spinner to flash.
        const isNativeBuffering = globalIsLoading && !isSeeking;
        const isScrapingPhase = isAppLoading && !globalIsPlaying && (currentPosMs <= 100);
        const shouldBeLoading = (isNativeBuffering || isScrapingPhase) && !isProbing && !isVideoEnded && !isSeeking;
        
        const getCleanLoadingText = () => {
            if (isNativeBuffering) return 'Buffering...';
            if (isScrapingPhase) return 'Loading source...';
            return 'Buffering...';
        };

        if (shouldBeLoading !== isCurrentlyLoading) {
            isCurrentlyLoading = shouldBeLoading;
            clearTimeout(loadingTimer);
            if (shouldBeLoading) {
                loadingTimer = setTimeout(() => {
                    if (loadingContainer) loadingContainer.classList.add('show');
                    if (loadingStatus) loadingStatus.innerText = getCleanLoadingText();
                }, 200);
            } else {
                if (loadingContainer) loadingContainer.classList.remove('show');
                if (loadingStatus) loadingStatus.innerText = '';
            }
        } else if (shouldBeLoading) {
             if (loadingStatus) loadingStatus.innerText = getCleanLoadingText();
        }
    }

    // Panel toggles
    const panels = ['episodesPanel','chaptersPanel','serversPanel','subsPanel','settingsPanel','qualityPanel','audioPanel','speedPanel','aspectPanel'];

    // SVG Icons
    const SVGS = {
        play:  `<svg viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M8 5v14l11-7z"/></svg>`,
        pause: `<svg viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>`,
        rewind10: `<svg viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M11.99 5V1l-5 5 5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6h-2c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"/><path d="M10.89 16h-.85v-3.26l-1.01.31v-.69l1.77-.63h.09V16zm4.28-1.76c0 .32-.03.6-.1.82s-.17.42-.29.57-.28.26-.45.33-.37.1-.59.1-.41-.03-.59-.1-.33-.18-.46-.33-.23-.34-.3-.57-.11-.5-.11-.82v-.74c0-.32.03-.6.1-.82s.17-.42.29-.57.28-.26.45-.33.37-.1.59-.1.41.03.59.1.33.18.46.33.23.34.3.57.11.5.11.82zm-.85-.86c0-.19-.01-.35-.04-.48s-.07-.23-.12-.31-.11-.14-.19-.17-.16-.05-.25-.05-.18.02-.25.05-.14.09-.19.17-.09.18-.12.31-.04.29-.04.48v.97c0 .19.01.35.04.48s.07.24.12.32.11.14.19.17.16.05.25.05.18-.02.25-.05.14-.09.19-.17.09-.19.11-.32.04-.29.04-.48v-.97z"/></svg>`,
        forward10: `<svg viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M18 13c0 3.31-2.69 6-6 6s-6-2.69-6-6 2.69-6 6-6v4l5-5-5-5v4c-4.42 0-8 3.58-8 8s3.58 8 8 8 8-3.58 8-8h-2z"/><polygon points="10.86 15.94 10.86 11.67 10.77 11.67 9 12.3 9 12.99 10.01 12.68 10.01 15.94"/><path d="M14.28 14.24c0 .32-.03.6-.1.82s-.17.42-.29.57-.28.26-.45.33-.37.1-.59.1-.41-.03-.59-.1-.33-.18-.46-.33-.23-.34-.3-.57-.11-.5-.11-.82v-.74c0-.32.03-.6.1-.82s.17-.42.29-.57.28-.26.45-.33.37-.1.59-.1.41.03.59.1.33.18.46.33.23.34.3.57.11.5.11.82zm-.85-.86c0-.19-.01-.35-.04-.48s-.07-.23-.12-.31-.11-.14-.19-.17-.16-.05-.25-.05-.18.02-.25.05-.14.09-.19.17-.09.18-.12.31-.04.29-.04.48v.97c0 .19.01.35.04.48s.07.24.12.32.11.14.19.17.16.05.25.05.18-.02.25-.05.14-.09.19-.17.09-.19.11-.32.04-.29.04-.48v-.97z"/></svg>`,
        volHigh:`<svg viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>`,
        volMed: `<svg viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z"/></svg>`,
        volLow: `<svg viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M7 9v6h4l5 5V4L11 9H7z"/></svg>`,
        volMute:`<svg viewBox="0 0 24 24" fill="currentColor" width="100%" height="100%"><path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/></svg>`,
        check:  `<svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/></svg>`,
    };

    // Volume OSD (Single unified HUD for all volume changes)
    let volumeOsdTimer = null;
    const showVolumeOsd = (vol, isMutedState = null) => {
        const osd = document.getElementById('volumeOsd');
        const txt = document.getElementById('volumeOsdText');
        const icon = document.getElementById('volumeOsdIcon');
        const fill = document.getElementById('volumeOsdBarFill');
        if (!osd || !txt || !icon) return;
        
        const effectiveMuted = (isMutedState !== null) ? isMutedState : (isMuted || vol <= 0);
        const clampedVol = Math.round(Math.max(0, Math.min(100, vol)));
        
        txt.innerText = effectiveMuted ? 'Muted' : `${clampedVol}%`;
        if (fill) {
            fill.style.width = effectiveMuted ? '0%' : `${clampedVol}%`;
        }
        if (effectiveMuted) {
            icon.innerHTML = SVGS.volMute;
        } else if (clampedVol < 34) {
            icon.innerHTML = SVGS.volLow;
        } else if (clampedVol < 67) {
            icon.innerHTML = SVGS.volMed;
        } else {
            icon.innerHTML = SVGS.volHigh;
        }
        
        osd.classList.add('show');
        clearTimeout(volumeOsdTimer);
        volumeOsdTimer = setTimeout(() => { osd.classList.remove('show'); }, 1200);
    };
    window.showVolumeOsd = showVolumeOsd;

    // Action Feedback
    let feedbackTimer;
    const triggerActionFeedback = (svgHtml, align = 'center') => {
        const fb = document.getElementById('actionFeedback');
        const fbIcon = document.getElementById('actionFeedbackIcon');
        const lc = document.getElementById('loadingContainer');
        fbIcon.innerHTML = svgHtml;
        fb.classList.remove('animate');
        void fb.offsetWidth; // Force reflow
        
        if (align === 'left') {
            fb.style.left = '25%'; fb.style.top = '50%';
        } else if (align === 'right') {
            fb.style.left = '75%'; fb.style.top = '50%';
        } else {
            fb.style.left = '50%'; fb.style.top = '50%';
        }
        
        fb.classList.add('animate');
        lc.style.opacity = '0';
        clearTimeout(feedbackTimer);
        feedbackTimer = setTimeout(() => { 
            fb.classList.remove('animate'); 
            lc.style.opacity = '1';
        }, 500);
    };

    // Auto-hide Controls
    let hideTimer;
    let lastMouseX = -1;
    let lastMouseY = -1;
    let isHoveringControls = false;

    // Do not hide controls if the user's mouse is actively resting on the top bar, bottom bar, or open panels
    document.querySelectorAll('.top-bar, .bottom-bar, .panel').forEach(el => {
        el.addEventListener('mouseenter', () => { isHoveringControls = true; clearTimeout(hideTimer); });
        el.addEventListener('mouseleave', () => { isHoveringControls = false; showControls(); });
    });
    
    const showControls = (e, forceHide = false) => {
        if (document.body.classList.contains('keyboard-seeking')) {
            return; // Never show full controls while keyboard seeking is active!
        }
        if (forceHide) {
            isHoveringControls = false;
        }
        if (e && e.type === 'mousemove') {
            const dist = Math.hypot(e.clientX - lastMouseX, e.clientY - lastMouseY);
            if (dist < 3) {
                return; // Ignore synthesized mousemove where mouse didn't actually move
            }
            lastMouseX = e.clientX;
            lastMouseY = e.clientY;
        }
        const pOverlay = document.getElementById('linkProbingOverlay');
        const isProbing = pOverlay && pOverlay.classList.contains('active');
        if (isProbing && !userDismissedProbing) {
            clearTimeout(hideTimer);
            document.body.classList.remove('hidden-controls');
            return;
        }
        if (overlay.style.opacity === '0' && videoEndedOverlay.style.display !== 'flex') {
            overlay.style.opacity = '';
        }
        overlay.classList.remove('hidden-controls');
        document.body.classList.remove('hidden-controls');
        const sBtn = document.getElementById('skipBtn');
        if (sBtn) {
            sBtn.classList.remove('idle-faded');
            window._skipBtnActiveSince = Date.now();
        }
        clearTimeout(hideTimer);
        if (!globalIsPlaying && pauseInfoMode !== 'immediate' && pauseInfoMode !== 'off') {
            hidePauseInfoOverlay();
            schedulePauseInfoOverlay();
        }
        if (!isMenuOpen && !isSeeking && (forceHide || !isHoveringControls)) {
            hideTimer = setTimeout(() => {
                if (!isHoveringControls && !isSeeking && !isMenuOpen) {
                    overlay.classList.add('hidden-controls');
                    document.body.classList.add('hidden-controls');
                }
            }, 3500);
        }
    };
    window.showControls = showControls;
    window.onNativeKeyActivity = (isSeeking = false) => {
        isHoveringControls = false;
        if (document.body.classList.contains('hidden-controls')) {
            if (isSeeking) {
                triggerKeyboardSeekingHud();
            }
            return;
        }
        showControls(null, true);
    };
    window.triggerSeekFeedback = (dir) => {
        if (document.body.classList.contains('hidden-controls')) {
            triggerKeyboardSeekingHud();
        }
    };

    window.addEventListener('pointerdown', (e) => {
        if (e.button === 3) {
            // Mouse 4 (Back) -> Seek -10s
            e.preventDefault();
            e.stopPropagation();
            doRelativeSeek(-10000);
        } else if (e.button === 4) {
            // Mouse 5 (Forward) -> Seek +10s
            e.preventDefault();
            e.stopPropagation();
            doRelativeSeek(10000);
        }
    });

    document.addEventListener('mousemove', showControls);
    
    // Global Focus Lock Prevention: Never allow buttons, sliders, or panels to trap keyboard focus.
    // Use capture phase (true) so it executes before any stopPropagation() in child elements.
    document.addEventListener('focusin', (e) => {
        if (!e.target.closest('input[type="text"], input[type="search"], textarea, [contenteditable]')) {
            if (e.target instanceof HTMLElement) {
                e.target.blur();
            }
        }
    }, true);

    document.addEventListener('pointerdown', (e) => {
        if (!e.target.closest('input[type="text"], input[type="search"], textarea, [contenteditable]')) {
            if (document.activeElement && document.activeElement instanceof HTMLElement && document.activeElement !== document.body) {
                document.activeElement.blur();
            }
        }
    }, true);

    document.addEventListener('click', e => {
        // Blur active element so buttons don't retain focus when UI hides
        if (!e.target.closest('input[type="text"], input[type="search"], textarea, [contenteditable]') && document.activeElement instanceof HTMLElement) {
            document.activeElement.blur();
        }
        showControls(e);
    });
    
    let keyboardSeekingTimer = null;
    function triggerKeyboardSeekingHud() {
        if (keyboardSeekingTimer) {
            clearTimeout(keyboardSeekingTimer);
            keyboardSeekingTimer = null;
        }
        document.body.classList.add('keyboard-seeking');
        keyboardSeekingTimer = setTimeout(() => {
            document.body.classList.remove('keyboard-seeking');
            keyboardSeekingTimer = null;
        }, 1400);
    }
    window.triggerKeyboardSeekingHud = triggerKeyboardSeekingHud;

    document.addEventListener('keydown', (e) => {
        isHoveringControls = false;
        // Never flash full UI on keyboard shortcut actions when controls are hidden
        if (document.body.classList.contains('hidden-controls')) {
            const isSeekKey = e.code === 'ArrowLeft' || e.code === 'ArrowRight' || e.code === 'KeyJ' || e.code === 'KeyL' || (!e.ctrlKey && !e.altKey && !e.metaKey && e.key >= '0' && e.key <= '9');
            if (isSeekKey) {
                triggerKeyboardSeekingHud();
            }
            return;
        }
        showControls(e, true);
    });

    // Panel Management
    const closeAllPanels = () => {
        panels.forEach(id => document.getElementById(id).classList.remove('open'));
        isMenuOpen = false;
        showControls();
    };
    // Expose on window so inline onclick="closeAllPanels()" attributes work
    window.closeAllPanels = closeAllPanels;
    const togglePanel = (id) => {
        const el = document.getElementById(id);
        const wasOpen = el.classList.contains('open');
        closeAllPanels();
        if (!wasOpen) { el.classList.add('open'); isMenuOpen = true; }
    };
    // Expose so inline onclick and context menu items can call it
    window.togglePanel = togglePanel;
    panels.forEach(id => {
        const panel = document.getElementById(id);
        panel.addEventListener('click', e => e.stopPropagation());
    });

    // Close buttons
    document.getElementById('closeEpisodesBtn')?.addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeChaptersBtn')?.addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeServersBtn')?.addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeSubsBtn')?.addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeSettingsBtn')?.addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeQualityBtn')?.addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeAudioBtn')?.addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeSpeedBtn')?.addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeAspectBtn')?.addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });

    // Season Dropdown Selector Change Event
    seasonSelect?.addEventListener('change', e => {
        renderFilteredEpisodes(parseInt(e.target.value));
    });

    // Resume Floating Pill
    const btnStartOverElem = document.getElementById('btnStartOver');
    if (btnStartOverElem) {
        btnStartOverElem.addEventListener('click', (e) => {
            if (e) { e.preventDefault(); e.stopPropagation(); }
            dismissResumeOverlay();
            send('seekTo', 0);
            send('play');
            triggerActionFeedback(SVGS.rewind10, 'center');
        });
    }
    const btnDismissResumeElem = document.getElementById('btnDismissResume');
    if (btnDismissResumeElem) {
        btnDismissResumeElem.addEventListener('click', (e) => {
            if (e) { e.preventDefault(); e.stopPropagation(); }
            dismissResumeOverlay();
        });
    }

    // Seek Bar
    seekBar.addEventListener('mousedown', () => { 
        if (durationMs <= 0) return;
        isSeeking = true; 
    });
    seekBar.addEventListener('input', e => {
        if (durationMs <= 0) return;
        let pct = e.target.value / 10;
        pct = Math.max(0, Math.min(100, pct));
        seekFill.style.width = `${pct}%`;
        currentPosMs = (pct / 100) * durationMs;
        timeDisplay.innerText = `${fmt(currentPosMs)} / ${fmt(durationMs)}`;
    });
    seekBar.addEventListener('mouseup', e => {
        if (durationMs <= 0) {
            // Live stream or unknown duration. Ignore seek!
            isSeeking = false;
            return;
        }
        let pct = e.target.value / 10;
        pct = Math.max(0, Math.min(100, pct));
        const targetMs = Math.round((pct / 100) * durationMs);
        currentPosMs = targetMs;
        send('seekTo', targetMs);
        // Hold the lock — release via incoming state update, not a timer
        clearTimeout(seekLockTimer);
        seekLockTimer = setTimeout(() => { isSeeking = false; }, 800);
    });
    seekBar.addEventListener('change', () => {
        // Fallback release if mouseup didn't fire (e.g. touch or drag-out)
        if (durationMs > 0) {
            clearTimeout(seekLockTimer);
            seekLockTimer = setTimeout(() => { isSeeking = false; }, 1500);
        }
    });

    // Volume
    const updateVolumeTrack = (val) => {
        const pct = Math.max(0, Math.min(100, (val / 100) * 100));
        volumeBar.style.setProperty('--vol-pct', pct + '%');
        const pipVolFill = document.getElementById('pipVolumeFill');
        if (pipVolFill) pipVolFill.style.height = pct + '%';
    };
    updateVolumeTrack(100); // Initialize

    volumeBar.addEventListener('input', e => {
        const v = Math.max(0, Math.min(100, parseInt(e.target.value) || 0));
        currentVolume = v;
        updateVolumeTrack(v);
        send('setVolume', v);
        showVolumeOsd(v);
    });

    document.addEventListener('wheel', e => {
        const isCtxOpen = ctxMenu && ctxMenu.style.display === 'block';
        if (isMenuOpen || isCtxOpen || (e.target && e.target.closest('#contextMenuOverlay'))) return;

        let newVol = currentVolume;
        // Scroll up increases volume, scroll down decreases
        if (e.deltaY < 0) {
            newVol = Math.min(100, newVol + 5);
        } else if (e.deltaY > 0) {
            newVol = Math.max(0, newVol - 5);
        }

        if (newVol !== currentVolume) {
            currentVolume = newVol;
            volumeBar.value = newVol;
            updateVolumeTrack(newVol);
            send('setVolume', newVol);
            showVolumeOsd(newVol);
        }
    });

    // Mute Icon
    const updateMuteIcon = () => {
        const w1 = document.getElementById('volWave1');
        const w2 = document.getElementById('volWave2');
        const w3 = document.getElementById('volWave3');
        const cross = document.getElementById('volCross');
        if (!w1 || !w2 || !w3 || !cross) return;

        if (isMuted || currentVolume === 0) {
            w1.style.opacity = '0'; w1.style.transform = 'scale(0.5)';
            w2.style.opacity = '0'; w2.style.transform = 'scale(0.5)';
            w3.style.opacity = '0'; w3.style.transform = 'scale(0.5)';
            cross.style.opacity = '1'; cross.style.transform = 'scale(1)';
        } else {
            cross.style.opacity = '0'; cross.style.transform = 'scale(0.5)';
            w1.style.opacity = '1'; w1.style.transform = 'scale(1)';

            if (currentVolume < 33) {
                w2.style.opacity = '0'; w2.style.transform = 'scale(0.8)';
                w3.style.opacity = '0'; w3.style.transform = 'scale(0.8)';
            } else if (currentVolume < 66) {
                w2.style.opacity = '1'; w2.style.transform = 'scale(1)';
                w3.style.opacity = '0'; w3.style.transform = 'scale(0.8)';
            } else {
                w2.style.opacity = '1'; w2.style.transform = 'scale(1)';
                w3.style.opacity = '1'; w3.style.transform = 'scale(1)';
            }
        }
    };

    // Helper: seek relative
    const doRelativeSeek = (deltaMs) => {
        if (durationMs <= 0) return; // Prevent relative seek on live/unknown duration
        isSeeking = true;
        currentPosMs = Math.max(0, Math.min(durationMs || Infinity, currentPosMs + deltaMs));
        let pct = 0;
        if (durationMs > 0) {
            pct = (currentPosMs / durationMs) * 100;
            pct = Math.max(0, Math.min(100, pct));
            seekFill.style.width = `${pct}%`;
            seekBar.value = pct * 10;
        }
        const timeFormatted = `${fmt(currentPosMs)} / ${fmt(durationMs)}`;
        timeDisplay.innerText = timeFormatted;
        const timeBadge = document.getElementById('seekingTimeBadge');
        if (timeBadge) {
            timeBadge.innerText = timeFormatted;
        }
        const seekTooltip = document.getElementById('seekTooltip');
        if (seekTooltip && durationMs > 0) {
            seekTooltip.innerText = timeFormatted;
            seekTooltip.style.left = `${pct}%`;
        }
        if (document.body.classList.contains('hidden-controls')) {
            triggerKeyboardSeekingHud();
        }
        send('seekBy', deltaMs);
        clearTimeout(seekLockTimer);
        seekLockTimer = setTimeout(() => { isSeeking = false; }, 800);
    };

    // Backend Messages
    const forceShowLoading = () => {
        if (globalIsPlaying) {
            isCurrentlyLoading = true;
            clearTimeout(loadingTimer);
            loadingContainer.classList.add('show');
        }
    };

    const handleStateUpdate = (s) => {
        if (typeof s.durationMs === 'number' && s.durationMs > 0 && s.durationMs !== durationMs) {
            durationMs = s.durationMs;
            renderSeekbarChapters(_cachedChapters, _cachedSkipIntervals);
        } else if (typeof s.durationMs === 'number') {
            durationMs = s.durationMs;
        }
        const incomingPos = (typeof s.positionMs === 'number') ? s.positionMs : currentPosMs;

        if (isSeeking) {
            // While seeking, ignore MPV position updates (they lag behind)
            // But if MPV sends a position close to what we requested, release the lock
            if (Math.abs(incomingPos - currentPosMs) < 2000 && incomingPos >= 0) {
                clearTimeout(seekLockTimer);
                isSeeking = false;
                currentPosMs = incomingPos;
                if (durationMs > 0) {
                    let pct = (currentPosMs / durationMs) * 100;
                    pct = Math.max(0, Math.min(100, pct));
                    seekFill.style.width = `${pct}%`;
                    seekBar.value = pct * 10;
                    const pipProg = document.getElementById('pipProgressFill');
                    if (pipProg) pipProg.style.width = `${pct}%`;
                    
                    if (typeof s.bufferMs === 'number') {
                        let bufPct = (s.bufferMs / durationMs) * 100;
                        bufPct = Math.max(0, Math.min(100, bufPct));
                        seekBuffer.style.width = `${bufPct}%`;
                    }
                }
            }
            // Otherwise keep ignoring (MPV is still catching up)
        } else {
            currentPosMs = incomingPos;
            if (durationMs > 0) {
                let pct = (currentPosMs / durationMs) * 100;
                pct = Math.max(0, Math.min(100, pct));
                seekFill.style.width = `${pct}%`;
                seekBar.value = pct * 10;
                const pipProg = document.getElementById('pipProgressFill');
                if (pipProg) pipProg.style.width = `${pct}%`;
                
                if (typeof s.bufferMs === 'number') {
                    let bufPct = (s.bufferMs / durationMs) * 100;
                    bufPct = Math.max(0, Math.min(100, bufPct));
                    seekBuffer.style.width = `${bufPct}%`;
                }
            }
        }
        timeDisplay.innerText = `${fmt(currentPosMs)} / ${fmt(durationMs)}`;

        // Update active chapter in real time
        if (_cachedChapters && _cachedChapters.length > 0) {
            let activeIdx = -1;
            for (let i = _cachedChapters.length - 1; i >= 0; i--) {
                if (currentPosMs >= _cachedChapters[i].timeMs) {
                    activeIdx = i;
                    break;
                }
            }
            if (activeIdx !== _activeChapterIndex) {
                _activeChapterIndex = activeIdx;
                const items = chaptersList?.querySelectorAll('.chapter-item');
                if (items) {
                    items.forEach((item, idx) => {
                        if (idx === activeIdx) item.classList.add('active');
                        else item.classList.remove('active');
                    });
                }
            }
        }

        // ── Real-time Active Skip Button Evaluation ─────────────────────
        const skipBtn = document.getElementById('skipBtn');
        const skipBtnLabel = document.getElementById('skipBtnLabel');
        if (skipBtn) {
            if (_cachedSkipIntervals && _cachedSkipIntervals.length > 0 && !isSeeking) {
                const activeInv = _cachedSkipIntervals.find(inv =>
                    inv.startMs >= 0 &&
                    inv.endMs > inv.startMs &&
                    (inv.endMs - inv.startMs >= 3000) &&
                    currentPosMs >= inv.startMs &&
                    currentPosMs < inv.endMs
                );
                if (activeInv) {
                    if (skipBtnLabel) {
                        const typeName = (activeInv.type || '').toUpperCase();
                        let label = activeInv.label;
                        if (!label || label === 'Intro') {
                            if (typeName === 'RECAP') label = 'Skip Recap';
                            else if (typeName === 'ENDING' || typeName === 'OUTRO') label = 'Skip Outro';
                            else if (typeName === 'PREVIEW') label = 'Skip Preview';
                            else label = 'Skip Intro';
                        }
                        skipBtnLabel.innerText = label;
                    }
                    if (skipBtn.style.display !== 'inline-flex') {
                        skipBtn.style.display = 'inline-flex';
                    }
                } else {
                    skipBtn.style.display = 'none';
                }
            } else {
                skipBtn.style.display = 'none';
            }
        }
        
        if (!window._clockTimerAdded) {
            window._clockTimerAdded = true;
            setInterval(() => { if (typeof updateClockDisplay === 'function') updateClockDisplay(); }, 1000);
        }

        const updateClockDisplay = () => {
            const endTimeContainer = document.getElementById('endTimeContainer');
            const clockSegment = document.getElementById('clockSegment');
            const clockValue = document.getElementById('clockValue');
            const timeDivider1 = document.getElementById('timeDivider1') || document.getElementById('timeDivider');
            const endTimeSegment = document.getElementById('endTimeSegment');
            const endTimeValue = document.getElementById('endTimeValue');
            const timeDivider2 = document.getElementById('timeDivider2');
            const serverQualitySegment = document.getElementById('serverQualitySegment');
            const serverQualityValue = document.getElementById('serverQualityValue');

            const showEndTime = document.getElementById('btnToggleEndTime')?.classList.contains('active') ?? false;
            const showClock = document.getElementById('btnToggleClock')?.classList.contains('active') ?? false;
            const showServerQuality = document.getElementById('btnToggleServerQuality')?.classList.contains('active') ?? false;
            
            if (!endTimeContainer) return;

            let hasClock = false;
            let hasEnd = false;
            let hasServer = false;
            
            if (showClock && clockSegment && clockValue) {
                let clockStr = new Date().toLocaleTimeString([], {hour: 'numeric', minute:'2-digit', hour12: true});
                clockValue.innerText = clockStr;
                clockSegment.style.display = 'inline-flex';
                hasClock = true;
            } else if (clockSegment) {
                clockSegment.style.display = 'none';
            }
            
            if (showEndTime && durationMs > 0 && currentPosMs < durationMs && endTimeSegment && endTimeValue) {
                let currentSpeedStr = document.getElementById('speedBtn')?.innerText.replace('×', '') || "1";
                let currentSpeed = parseFloat(currentSpeedStr) || 1.0;
                
                const holdSpeedHud = document.getElementById('holdSpeedHud');
                if (holdSpeedHud && holdSpeedHud.classList.contains('show')) {
                    const hudText = document.getElementById('holdSpeedHudText')?.innerText || "";
                    if (hudText.includes('0.5x')) currentSpeed = 0.5;
                    else if (hudText.includes('2x')) currentSpeed = 2.0;
                }
                
                let msLeft = (durationMs - currentPosMs) / currentSpeed;
                let endStr = new Date(Date.now() + msLeft).toLocaleTimeString([], {hour: 'numeric', minute:'2-digit', hour12: true});
                endTimeValue.innerText = endStr;
                endTimeSegment.style.display = 'inline-flex';
                hasEnd = true;
            } else if (endTimeSegment) {
                endTimeSegment.style.display = 'none';
            }

            if (showServerQuality && serverQualitySegment && serverQualityValue) {
                const meta = window.lastMeta;
                const links = (meta && meta.links) || [];
                const currIdx = typeof currentLinkIndex === 'number' && currentLinkIndex >= 0 ? currentLinkIndex : 0;
                const activeLink = links[currIdx] || links.find(l => l.isActive);

                let rawName = activeLink ? (activeLink.name || `Source ${activeLink.index + 1}`) : '';
                const siteName = (activeLink && activeLink.source) ? activeLink.source.trim() : '';

                // Extract file size if present (e.g. [1.92 GB], 1.92GB, [850 MB])
                let sizeStr = '';
                const sizeMatch = rawName.match(/(?:\[\s*)?(\d+(?:\.\d+)?\s*(?:GB|MB|KB|GiB|MiB))\b(?:\s*\])?/i);
                if (sizeMatch && sizeMatch[1]) {
                    sizeStr = sizeMatch[1].toUpperCase().replace(/\s+/, ' ');
                }

                // Clean scraper artifacts (file size, rip types, codecs, resolution brackets)
                let serverName = rawName
                    .replace(/\[\s*\d+(\.\d+)?\s*(?:GB|MB|KB|G|M)\s*\]/gi, '')
                    .replace(/\b\d+(\.\d+)?\s*(?:GB|MB|KB)\b/gi, '')
                    .replace(/\[\s*(?:WEB-DL|WEBRip|BluRay|BDRip|BRRip|HDRip|HDTV|DVDRip|REMUX|CAM|TS)\b[^\]]*\]/gi, '')
                    .replace(/\b(?:WEB-DL|WEBRip|BluRay|BDRip|BRRip|HDRip|HDTV|DVDRip|REMUX)\b/gi, '')
                    .replace(/\[\s*(?:DDP\d*(\.\d+)?|DD\d*(\.\d+)?|AAC\d*|AC3|EAC3|HEVC|H\.?26[45]|x26[45]|10bit|HDR\d*|Atmos|TrueHD)\b[^\]]*\]/gi, '')
                    .replace(/\[\s*(?:2160p|1080p|720p|480p|360p|4K|UHD|FHD|HD|SD)\s*\]/gi, '')
                    .replace(/\b(?:2160p|1080p|720p|480p|360p|4k|uhd|fhd|hd|sd)\b/gi, '')
                    .trim();

                // Format Provider [Server] into Provider · Server
                const bracketMatch = serverName.match(/^([^[]*?)\[([^\]]+)\](.*)$/);
                if (bracketMatch) {
                    const prefix = bracketMatch[1].trim();
                    const inside = bracketMatch[2].trim();
                    const suffix = bracketMatch[3].trim();
                    serverName = prefix ? `${prefix} · ${inside} ${suffix}`.trim() : `${inside} ${suffix}`.trim();
                }
                serverName = serverName.replace(/[\s\-_•/]+$/g, '').replace(/^[\s\-_•/]+/g, '').trim();

                // Incorporate site/provider name if available and not already in serverName (normalized comparison)
                if (siteName) {
                    const normSite = siteName.toLowerCase().replace(/[^a-z0-9]/g, '');
                    const normServer = serverName.toLowerCase().replace(/[^a-z0-9]/g, '');

                    if (normSite && normServer) {
                        if (normServer.includes(normSite)) {
                            // serverName already contains siteName; keep serverName
                        } else if (normSite.includes(normServer)) {
                            // siteName already contains serverName; use siteName
                            serverName = siteName;
                        } else {
                            // Distinct site and server
                            serverName = serverName ? `${siteName} · ${serverName}` : siteName;
                        }
                    } else if (!serverName) {
                        serverName = siteName;
                    }
                }
                if (!serverName && activeLink) serverName = `Source ${activeLink.index + 1}`;

                // Standard quality resolution matching Android Qualities standard
                let qualityStr = '';
                const declaredQuality = (activeLink && typeof activeLink.quality === 'number' && activeLink.quality > 0 && activeLink.quality !== 400)
                    ? activeLink.quality
                    : 0;

                if (declaredQuality > 0) {
                    qualityStr = declaredQuality >= 2160 ? '4K' : `${declaredQuality}p`;
                } else if (meta && meta.resolution) {
                    // Fallback for adaptive HLS/DASH or undeclared streams (aspect-ratio aware)
                    const parts = meta.resolution.split('x');
                    const w = parts[0] ? parseInt(parts[0].trim(), 10) : 0;
                    const h = parts[1] ? parseInt(parts[1].trim(), 10) : 0;

                    if (w >= 3800 || h >= 1600) qualityStr = '4K';
                    else if (w >= 2500 || h >= 1300) qualityStr = '1440p';
                    else if (w >= 1900 || h >= 800) qualityStr = '1080p';
                    else if (w >= 1200 || h >= 530) qualityStr = '720p';
                    else if (w >= 700 || h >= 400) qualityStr = '480p';
                    else if (h > 0) qualityStr = `${h}p`;
                }

                if (!qualityStr) qualityStr = 'Auto';

                let displayText = serverName ? `${serverName} • ${qualityStr}` : qualityStr;
                if (sizeStr) displayText += ` • ${sizeStr}`;

                serverQualityValue.innerText = displayText;
                serverQualitySegment.style.display = 'inline-flex';
                hasServer = true;
            } else if (serverQualitySegment) {
                serverQualitySegment.style.display = 'none';
            }

            if (timeDivider1) {
                timeDivider1.style.display = (hasClock && hasEnd) ? 'block' : 'none';
            }
            if (timeDivider2) {
                timeDivider2.style.display = ((hasClock || hasEnd) && hasServer) ? 'block' : 'none';
            }
            
            if (hasClock || hasEnd || hasServer) {
                endTimeContainer.style.display = 'inline-flex';
            } else {
                endTimeContainer.style.display = 'none';
            }
        };

        window.updateClockDisplay = updateClockDisplay;
        updateClockDisplay();
        
        const wasPlaying = globalIsPlaying;
        if (s.isPlaying !== undefined) {
            globalIsPlaying = !!s.isPlaying;
        }

        if (wasPlaying !== globalIsPlaying) {
            playPauseBtn.classList.toggle('is-playing', globalIsPlaying);
            if (typeof window.updatePipPlayPauseIcon === 'function') {
                window.updatePipPlayPauseIcon();
            }
        }

        if (s.volume !== undefined) { 
            currentVolume = s.volume; 
            volumeBar.value = s.volume; 
            updateVolumeTrack(s.volume);
        }
        if (s.isMuted !== undefined) isMuted = s.isMuted;
        updateMuteIcon();
        if (s.isLoading !== undefined) {
            globalIsLoading = s.isLoading;
        }

        if (globalIsBuffering) {
            if (Date.now() - globalBufferingStartTime > 2000) {
                dismissProbingOverlay();
            }
        }
        
        evaluateUIStates();

        evaluateResumeOverlay();
    };

    // Expose globally so input handlers can use it
    window.renderFilteredEpisodes = (selectedSeason) => renderFilteredEpisodes(selectedSeason);

    let activeServerFilter = 'All';

    window.setServerFilter = (filterVal) => {
        activeServerFilter = filterVal;
        renderFilteredServers();
    };

    const renderFilteredServers = () => {
        const filtered = linksData.filter(l => {
            if (activeServerFilter === 'All') return true;
            
            const isHls = l.isM3u8 || (l.name || '').toLowerCase().includes('hls') || (l.url || '').includes('.m3u8');
            const isDash = l.isDash || (l.name || '').toLowerCase().includes('dash') || (l.url || '').includes('.mpd');
            
            if (activeServerFilter === 'Auto / HLS') return isHls || isDash;
            if (activeServerFilter === 'MP4 (Downloadable)') return !isHls && !isDash;
            
            const qVal = l.quality;
            const isAutoQuality = !qVal || qVal === 400 || qVal <= 0;
            const qStr = isAutoQuality ? '' : String(qVal).toLowerCase();
            if (activeServerFilter === '4K') return qStr === '2160' || qStr === '4k';
            if (activeServerFilter === '1080p') return qStr === '1080';
            if (activeServerFilter === '720p') return qStr === '720';
            if (activeServerFilter === '480p / SD') return qStr === '480' || qStr.includes('sd');
            
            return false;
        });

        document.getElementById('serverSubtitle').innerText = `${filtered.length} source${filtered.length !== 1 ? 's' : ''} available`;
        
        // Build available chips based on raw linksData
        const availableChips = new Set(['All']);
        linksData.forEach(l => {
            const isHls = l.isM3u8 || (l.name || '').toLowerCase().includes('hls') || (l.url || '').includes('.m3u8');
            const isDash = l.isDash || (l.name || '').toLowerCase().includes('dash') || (l.url || '').includes('.mpd');
            if (isHls || isDash) availableChips.add('Auto / HLS');
            else availableChips.add('MP4 (Downloadable)');
            
            const qVal = l.quality;
            const isAutoQuality = !qVal || qVal === 400 || qVal <= 0;
            const qStr = isAutoQuality ? '' : String(qVal).toLowerCase();
            if (qStr === '2160' || qStr === '4k') availableChips.add('4K');
            else if (qStr === '1080') availableChips.add('1080p');
            else if (qStr === '720') availableChips.add('720p');
            else if (qStr === '480' || qStr.includes('sd')) availableChips.add('480p / SD');
        });
        
        const order = ['All', '4K', '1080p', '720p', '480p / SD', 'Auto / HLS', 'MP4 (Downloadable)'];
        const chipsHtml = order.filter(c => availableChips.has(c)).map(c => 
            `<div class="filter-chip ${activeServerFilter === c ? 'active' : ''}" onclick="setServerFilter('${c}')">${c}</div>`
        ).join('');
        
        document.getElementById('filterChipsContainer').innerHTML = chipsHtml;

        if (filtered.length === 0) {
            document.getElementById('serversList').innerHTML = `<div style="padding: 20px; text-align: center; color: rgba(255,255,255,0.5);">No sources match your filter.</div>`;
            return;
        }

        const failedArr = (window.lastMeta && window.lastMeta.failedLinks) || [];

        document.getElementById('serversList').innerHTML = filtered.map(l => {
            const failedInfo = failedArr.find(f => f.index === l.index || (f.url && f.url === l.url));
            const isFailed = !!failedInfo;

            const qVal = l.quality;
            const isAutoQuality = !qVal || qVal === 400 || qVal <= 0;
            const isHls = l.isM3u8 || (l.name || '').toLowerCase().includes('hls') || (l.url || '').includes('.m3u8');
            const isDash = l.isDash || (l.name || '').toLowerCase().includes('dash') || (l.url || '').includes('.mpd');
            
            const qStr = isAutoQuality ? (isHls ? 'HLS' : isDash ? 'DASH' : 'Auto') : (String(qVal) + 'p');
            const qStrLower = String(qVal || '').toLowerCase();
            const is4K = qStrLower.includes('2160') || qStrLower.includes('4k') || qVal >= 2160;
            const isHD = qStrLower.includes('1080') || qVal >= 1080;
            
            let badgeText = 'SD';
            let badgeClass = 'sd';
            if (isFailed) {
                badgeText = failedInfo.reason ? (failedInfo.reason.length > 10 ? failedInfo.reason.slice(0, 8) + '…' : failedInfo.reason) : 'FAILED';
                badgeClass = 'error';
            } else if (l.isTorrent) {
                badgeText = l.seeds ? `P2P · ${l.seeds}s` : 'P2P';
                badgeClass = 'p2p';
            } else if (is4K) {
                badgeText = '4K';
                badgeClass = 'uhd';
            } else if (isHD) {
                badgeText = '1080p';
                badgeClass = 'fhd';
            } else if (isAutoQuality) {
                badgeText = isHls ? 'HLS' : isDash ? 'DASH' : 'AUTO';
                badgeClass = 'hd';
            }
            
            const urlEncoded = encodeURIComponent(l.url || '');
            const itemClasses = ['srv-item'];
            if (l.isActive) itemClasses.push('active');
            if (isFailed) itemClasses.push('failed');

            const tooltip = isFailed ? (failedInfo.reason || 'Playback Failed') : '';

            return `<div class="${itemClasses.join(' ')}" onclick="send('changeLink', decodeURIComponent('${urlEncoded}'));closeAllPanels();" title="${tooltip}">
                <span class="srv-check">${l.isActive ? SVGS.check : ''}</span>
                <svg class="srv-icon" viewBox="0 0 24 24" fill="currentColor"><path d="M4 1h16c.55 0 1 .45 1 1v4c0 .55-.45 1-1 1H4c-.55 0-1-.45-1-1V2c0-.55.45-1 1-1zm0 8h16c.55 0 1 .45 1 1v4c0 .55-.45 1-1 1H4c-.55 0-1-.45-1-1v-4c0-.55.45-1 1-1zm0 8h16c.55 0 1 .45 1 1v4c0 .55-.45 1-1 1H4c-.55 0-1-.45-1-1v-4c0-.55.45-1 1-1z"/><circle cx="19" cy="4" r="1" fill="currentColor"/><circle cx="19" cy="12" r="1" fill="currentColor"/><circle cx="19" cy="20" r="1" fill="currentColor"/></svg>
                <span class="srv-name">${l.name || 'Source ' + (l.index + 1)}</span>
                <span class="srv-quality">${qStr}</span>
                <span class="srv-badge ${badgeClass}">${badgeText}</span>
            </div>`;
        }).join('');
    };
    window.renderFilteredServers = renderFilteredServers;

    let currentSelectedSeason = 1;
    let currentSelectedChunk = 0;
    const EPISODE_CHUNK_SIZE = 50;

    const renderFilteredEpisodes = (selectedSeason, targetChunk = -1) => {
        currentSelectedSeason = selectedSeason;
        const filtered = episodesData.filter(ep => {
            const epSeason = ep.season !== undefined && ep.season !== null ? ep.season : 1;
            return epSeason === selectedSeason;
        });

        const chunkWrap = document.getElementById('chunkSelectWrap');
        const chunkBar = document.getElementById('chunkChipsBar');

        let episodesToRender = filtered;
        if (filtered.length > EPISODE_CHUNK_SIZE) {
            if (chunkWrap) chunkWrap.style.display = 'block';
            const totalChunks = Math.ceil(filtered.length / EPISODE_CHUNK_SIZE);
            
            if (targetChunk < 0) {
                const activeIdx = filtered.findIndex(e => e.isActive);
                currentSelectedChunk = activeIdx >= 0 ? Math.floor(activeIdx / EPISODE_CHUNK_SIZE) : 0;
            } else {
                currentSelectedChunk = targetChunk;
            }

            if (chunkBar) {
                chunkBar.innerHTML = Array.from({ length: totalChunks }, (_, cIdx) => {
                    const start = cIdx * EPISODE_CHUNK_SIZE + 1;
                    const end = Math.min((cIdx + 1) * EPISODE_CHUNK_SIZE, filtered.length);
                    const isChunkActive = cIdx === currentSelectedChunk;
                    return `<button class="chunk-chip ${isChunkActive ? 'active' : ''}" onclick="renderFilteredEpisodes(${selectedSeason}, ${cIdx})">
                        ${start}–${end}
                    </button>`;
                }).join('');
            }

            const startIdx = currentSelectedChunk * EPISODE_CHUNK_SIZE;
            episodesToRender = filtered.slice(startIdx, startIdx + EPISODE_CHUNK_SIZE);
        } else {
            if (chunkWrap) chunkWrap.style.display = 'none';
            currentSelectedChunk = 0;
        }

        const listEl = document.getElementById('episodesList');
        if (listEl) {
            listEl.innerHTML = episodesToRender.map((ep, i) => {
                const num = ep.episode || (i + 1 + (currentSelectedChunk * EPISODE_CHUNK_SIZE));
                const thumbImg = ep.posterUrl ? `<img src="${ep.posterUrl}" class="ep-thumb" onload="this.classList.add('loaded')" onerror="this.remove()">` : '';
                const ratingBadge = (ep.score && ep.score > 0) ? `<div class="ep-thumb-rating"><svg viewBox="0 0 24 24"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>${ep.score.toFixed(1)}</div>` : '';
                const epIdEncoded = encodeURIComponent(ep.id || '');
                const epTitleEscaped = (ep.title || ('Episode ' + num)).replace(/</g, "&lt;").replace(/>/g, "&gt;");
                return `<div class="ep-card ${ep.isActive ? 'active' : ''}" onclick="send('loadEpisode', decodeURIComponent('${epIdEncoded}'));closeAllPanels();">
                    <span class="ep-num">${num}</span>
                    <div class="ep-thumb-wrapper">
                        <svg class="ep-thumb-fallback" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                        ${thumbImg}
                        ${ratingBadge}
                    </div>
                    <div class="ep-info">
                        <div class="ep-title">${epTitleEscaped}</div>
                        <div class="ep-meta">Season ${ep.season || 1}${ep.runTime ? ' · ' + ep.runTime + 'm' : ''}</div>
                        ${ep.isActive ? '<div class="ep-playing">● Playing</div>' : ''}
                    </div>
                </div>`;
            }).join('');
        }

        setTimeout(() => {
            const active = document.querySelector('#episodesList .ep-card.active');
            if (active) active.scrollIntoView({ block: 'center', behavior: 'smooth' });
        }, 150);
    };
    window.renderFilteredEpisodes = renderFilteredEpisodes;

    const onSeasonChipClick = (s) => {
        document.querySelectorAll('#seasonChipsBar .season-chip').forEach(btn => btn.classList.remove('active'));
        event?.target?.closest('.season-chip')?.classList.add('active');
        renderFilteredEpisodes(s, -1);
    };
    window.onSeasonChipClick = onSeasonChipClick;

    let hasShownMaturityAdvisoryThisSession = false;

    const triggerMaturityAdvisory = () => {
        if (hasShownMaturityAdvisoryThisSession) return;
        const meta = window.lastMeta;
        if (!meta) return;
        const banner = document.getElementById('maturityAdvisoryBanner');
        const badge = document.getElementById('advisoryRatingBadge');
        const reasons = document.getElementById('advisoryReasonsText');
        const subtext = document.getElementById('advisorySubtext');
        if (!banner || !badge || !reasons || !subtext) return;

        const rating = (meta.contentRating && meta.contentRating.trim().length > 0) ? meta.contentRating.trim().toUpperCase() : '';
        const tags = Array.isArray(meta.tags) ? meta.tags : [];

        // Relevant content advisory keywords
        const advisoryKeywords = ['GORE', 'VIOLENCE', 'HORROR', 'PSYCHOLOGICAL', 'THRILLER', 'CRIME', 'LANGUAGE', 'NUDITY', 'SUBSTANCE', 'MATURE', 'DARK', 'ACTION', 'DRAMA', 'SCI-FI', 'FANTASY', 'ADVENTURE', 'ROMANCE'];
        const matchedAdvisories = tags.map(t => String(t).toUpperCase()).filter(t => advisoryKeywords.some(k => t.includes(k)));

        let reasonsList = matchedAdvisories.length > 0 ? matchedAdvisories.slice(0, 3) : tags.slice(0, 3).map(t => String(t).toUpperCase());

        // Only show if we have either a rating or genres/tags
        if (!rating && reasonsList.length === 0) return;

        const displayRating = rating || '16+';
        badge.innerText = displayRating;

        const isMature = ['18+', 'TV-MA', 'R', 'RATED R', 'NC-17', 'MA', '18', 'GORE', 'HORROR'].includes(displayRating);
        if (reasonsList.length > 0) {
            reasons.innerText = reasonsList.join(' • ');
        } else {
            reasons.innerText = isMature ? 'VIOLENCE • LANGUAGE • MATURE THEMES' : 'GENERAL THEMES';
        }

        subtext.innerText = isMature ? 'Viewer discretion is advised' : 'Suitable for broad audiences';

        hasShownMaturityAdvisoryThisSession = true;

        if (maturityAdvisoryTimer) {
            clearTimeout(maturityAdvisoryTimer);
            maturityAdvisoryTimer = null;
        }

        banner.classList.add('active');

        maturityAdvisoryTimer = setTimeout(() => {
            banner.classList.remove('active');
            maturityAdvisoryTimer = null;
        }, 5500);
    };
    window.triggerMaturityAdvisory = triggerMaturityAdvisory;

    const handleMetadataUpdate = (meta) => {
        window.lastMeta = meta;
        // 1. Session & State Reset
        // Detect a genuinely new playback session (new title OR new episode).
        // IMPORTANT: currentLinkIndex changes are NOT a new session — they happen
        // during error recovery (trying next source) within the same episode.
        const activeEp = (meta.episodes || []).find(e => e.isActive);
        const activeId = activeEp ? activeEp.id : '';
        if (meta.title) {
            const isNewSession = currentTitle !== meta.title
                || currentEpisodeId !== activeId;

            if (isNewSession) {
                // *** Atomically reset ALL state so stale timers/overlays can't race ***
                hardResetAllOverlays();
                currentTitle = meta.title;
                currentEpisodeId = activeId;
            }
            // Always track the link index, but it doesn't trigger a session reset.
            if (meta.currentLinkIndex !== undefined) currentLinkIndex = meta.currentLinkIndex;

            if (isNewSession) {

                // Immediately pre-fill the probing screen with the NEW episode's data
                // so there is zero window where old/stale data is visible on screen.
                const pTitle = document.getElementById('linkProbingTitle');
                const pSubtitle = document.getElementById('linkProbingSubtitle');
                const pLogo = document.getElementById('linkProbingLogo');
                const pStatus = document.getElementById('linkProbingStatus');
                const pBackdrop = document.getElementById('linkProbingBackdrop');
                const pList = document.getElementById('linkProbingList');

                // Clear stale link list immediately
                if (pList) pList.innerHTML = '';

                // Reset status text
                if (pStatus) pStatus.innerText = 'Finding sources\u2026';

                // Update title, subtitle, and logo immediately
                if (activeEp) {
                    const s = activeEp.season !== undefined && activeEp.season !== null ? activeEp.season : 1;
                    const ep = activeEp.episode;
                    const epTitle = activeEp.title ? activeEp.title : `Episode ${ep}`;
                    let showTitle = meta.title || '';
                    if (showTitle.includes(' - ')) {
                        showTitle = showTitle.split(' - ')[0].trim();
                    }
                    if (meta.logoUrl && meta.logoUrl.trim().length > 0) {
                        if (pLogo) {
                            pLogo.onerror = () => {
                                pLogo.style.display = 'none';
                                if (pTitle) { pTitle.innerText = showTitle; pTitle.style.display = 'block'; }
                            };
                            pLogo.src = meta.logoUrl;
                            pLogo.style.display = 'block';
                        }
                        if (pTitle) pTitle.style.display = 'none';
                    } else {
                        if (pLogo) pLogo.style.display = 'none';
                        if (pTitle) { pTitle.innerText = showTitle; pTitle.style.display = 'block'; }
                    }
                    if (pSubtitle) {
                        pSubtitle.innerText = `S${s}:E${ep} • ${epTitle}`;
                        pSubtitle.style.display = 'block';
                    }
                } else {
                    if (meta.logoUrl && meta.logoUrl.trim().length > 0) {
                        if (pLogo) {
                            pLogo.onerror = () => {
                                pLogo.style.display = 'none';
                                if (pTitle) { pTitle.innerText = meta.title || ''; pTitle.style.display = 'block'; }
                            };
                            pLogo.src = meta.logoUrl;
                            pLogo.style.display = 'block';
                        }
                        if (pTitle) pTitle.style.display = 'none';
                    } else {
                        if (pLogo) pLogo.style.display = 'none';
                        if (pTitle) { pTitle.innerText = meta.title || ''; pTitle.style.display = 'block'; }
                    }
                    if (pSubtitle) pSubtitle.style.display = 'none';
                }

                // Update backdrop to the new episode's art directly without flashing
                const newBackdrop = meta.backdropUrl || (activeEp ? activeEp.posterUrl : '');
                if (pBackdrop && newBackdrop && pBackdrop.src !== newBackdrop) {
                    pBackdrop.src = newBackdrop;
                }
            }
              let niceTitle = meta.title;
            if (niceTitle) {
                const parts = niceTitle.split(' - ');
                // If it looks like "Show Name - S1E1 - Show Name", drop the repeated end part.
                if (parts.length >= 3 && parts[0].trim() === parts[parts.length - 1].trim()) {
                    parts.pop();
                    niceTitle = parts.join(' - ');
                }
            }
            titleDisplay.innerText = niceTitle || "CloudStream Player";
            document.title = meta.title;
            if (typeof window.updateClockDisplay === 'function') window.updateClockDisplay();
        }

        if (meta.startPositionMs !== undefined) {
            pendingResumeMs = meta.startPositionMs;
            if (pendingResumeMs > 0) {
                resumeHandled = false;
            }
            evaluateResumeOverlay();
        }

        // Populate Pause Info Overlay
        const pauseLogo = document.getElementById('pauseInfoLogo');
        const pauseFallback = document.getElementById('pauseInfoTitleFallback');
        const pauseEp = document.getElementById('pauseInfoEpisode');
        const pausePlot = document.getElementById('pauseInfoPlot');
        const pauseYear = document.getElementById('pauseInfoYear');
        const pauseTags = document.getElementById('pauseInfoTags');
        const pauseMeta = document.getElementById('pauseInfoMeta');
        
        if (meta.logoUrl && meta.logoUrl.trim().length > 0) {
            if (pauseLogo) {
                pauseLogo.onerror = () => {
                    pauseLogo.style.display = 'none';
                    if (pauseFallback) { pauseFallback.innerText = meta.title || ''; pauseFallback.style.display = 'block'; }
                };
                pauseLogo.src = meta.logoUrl;
                pauseLogo.style.display = 'block';
            }
            if (pauseFallback) pauseFallback.style.display = 'none';
        } else if (meta.title) {
            if (pauseLogo) pauseLogo.style.display = 'none';
            if (pauseFallback) { pauseFallback.innerText = meta.title; pauseFallback.style.display = 'block'; }
        }

        const activeEpInfo = (meta.episodes || []).find(ep => ep.isActive);
        if (activeEpInfo) {
            const s = activeEpInfo.season !== undefined && activeEpInfo.season !== null ? activeEpInfo.season : 1;
            const ep = activeEpInfo.episode;
            const epTitle = activeEpInfo.title ? activeEpInfo.title : `Episode ${ep}`;
            pauseEp.innerText = `S${s}:E${ep} • ${epTitle}`;
            pauseEp.style.display = 'block';
        } else {
            pauseEp.style.display = 'none';
        }

        if (meta.plot || (activeEpInfo && activeEpInfo.description)) {
            const rawPlot = (activeEpInfo && activeEpInfo.description) ? activeEpInfo.description : meta.plot;
            pausePlot.innerText = rawPlot.replace(/\|\|DATE:.*?\|\|/g, '').trim();
            pausePlot.style.display = '-webkit-box';
        } else {
            pausePlot.style.display = 'none';
        }

        let hasMeta = false;

        const pauseRating = document.getElementById('pauseInfoRating');
        if (pauseRating) {
            if (meta.contentRating && meta.contentRating.trim().length > 0) {
                pauseRating.innerText = meta.contentRating.trim().toUpperCase();
                pauseRating.style.display = 'inline-block';
                hasMeta = true;
            } else {
                pauseRating.style.display = 'none';
            }
        }

        const pauseScore = document.getElementById('pauseInfoScore');
        const pauseScoreText = document.getElementById('pauseInfoScoreText');
        const activeEpForScore = (meta.episodes || []).find(e => e.isActive);
        const epScoreForPause = activeEpForScore ? activeEpForScore.score : null;
        const effectiveScoreForPause = (epScoreForPause && epScoreForPause > 0) ? epScoreForPause : (meta.rating && meta.rating > 0 ? meta.rating : null);

        if (effectiveScoreForPause && pauseScore && pauseScoreText) {
            pauseScoreText.innerText = effectiveScoreForPause.toFixed(1);
            pauseScore.style.display = 'inline-flex';
            hasMeta = true;
        } else if (pauseScore) {
            pauseScore.style.display = 'none';
        }

        if (meta.year) {
            pauseYear.innerText = meta.year;
            pauseYear.style.display = 'inline-block';
            hasMeta = true;
        } else {
            pauseYear.style.display = 'none';
        }

        if (meta.tags && meta.tags.length > 0) {
            pauseTags.innerText = meta.tags.slice(0, 3).join(' • ');
            pauseTags.style.display = 'inline-block';
            hasMeta = true;
        } else {
            pauseTags.style.display = 'none';
        }

        pauseMeta.style.display = hasMeta ? 'flex' : 'none';

        // Populate Starring Cast Overlay
        currentCastList = (meta.actors || []).filter(actor => {
            const r = (actor.role || '').trim().toLowerCase();
            return r !== 'director' && r !== 'creator' && r !== 'writer' && r !== 'producer' && r !== 'executive producer';
        });
        const pauseCast = document.getElementById('pauseInfoCast');
        const castListEl = document.getElementById('pauseInfoCastList');
        if (castListEl) {
            castListEl.innerHTML = '';

            const showcaseEl = document.getElementById('pauseCastShowcase');
            const posterEl = document.getElementById('pauseCastShowcasePoster');
            const fallbackEl = document.getElementById('pauseCastShowcaseFallback');
            const nameEl = document.getElementById('pauseCastShowcaseName');
            const roleEl = document.getElementById('pauseCastShowcaseRole');

            if (showcaseEl && !showcaseEl.dataset.bound) {
                showcaseEl.dataset.bound = 'true';
                showcaseEl.addEventListener('mouseenter', () => {
                    if (window._castShowcaseHideTimer) {
                        clearTimeout(window._castShowcaseHideTimer);
                        window._castShowcaseHideTimer = null;
                    }
                });
                showcaseEl.addEventListener('mouseleave', () => {
                    if (window._castShowcaseHideTimer) clearTimeout(window._castShowcaseHideTimer);
                    window._castShowcaseHideTimer = setTimeout(() => {
                        if (showcaseEl) showcaseEl.classList.remove('active');
                    }, 120);
                });
            }

            const showActorInShowcase = (actor) => {
                if (!actor || !showcaseEl || !nameEl) return;
                if (window._castShowcaseHideTimer) {
                    clearTimeout(window._castShowcaseHideTimer);
                    window._castShowcaseHideTimer = null;
                }

                const safeName = actor.name || 'Unknown';
                const initial = (safeName[0] || '?').toUpperCase();

                nameEl.textContent = safeName;
                if (roleEl) {
                    roleEl.textContent = actor.role || '';
                    roleEl.style.display = actor.role ? 'block' : 'none';
                }

                if (actor.image && actor.image.trim().length > 0) {
                    if (posterEl) {
                        posterEl.src = actor.image;
                        posterEl.alt = safeName;
                        posterEl.style.display = 'block';
                        posterEl.onerror = () => {
                            posterEl.style.display = 'none';
                            if (fallbackEl) {
                                fallbackEl.textContent = initial;
                                fallbackEl.style.display = 'flex';
                            }
                        };
                    }
                    if (fallbackEl) fallbackEl.style.display = 'none';
                } else {
                    if (posterEl) {
                        posterEl.removeAttribute('src');
                        posterEl.style.display = 'none';
                    }
                    if (fallbackEl) {
                        fallbackEl.textContent = initial;
                        fallbackEl.style.display = 'flex';
                    }
                }

                showcaseEl.classList.add('active');
            };

            if (currentCastList.length > 0) {
                currentCastList.slice(0, 6).forEach(actor => {
                    const card = document.createElement('div');
                    card.className = 'pause-cast-card';

                    const safeName = escapeHtml(actor.name || 'Unknown');
                    const initial = (actor.name || '?')[0].toUpperCase();

                    let avatarHtml = '';
                    if (actor.image && actor.image.trim().length > 0) {
                        avatarHtml = `<img class="pause-cast-avatar" src="${actor.image}" alt="${safeName}" onerror="this.outerHTML='<div class=\\\'pause-cast-avatar-fallback\\\'>${initial}</div>'" />`;
                    } else {
                        avatarHtml = `<div class="pause-cast-avatar-fallback">${initial}</div>`;
                    }

                    const roleText = actor.role ? `<div class="pause-cast-role">${escapeHtml(actor.role)}</div>` : '';

                    card.innerHTML = `
                        ${avatarHtml}
                        <div class="pause-cast-info">
                            <div class="pause-cast-name">${safeName}</div>
                            ${roleText}
                        </div>
                    `;

                    card.addEventListener('mouseenter', () => {
                        showActorInShowcase(actor);
                    });
                    card.addEventListener('mouseleave', () => {
                        if (window._castShowcaseHideTimer) clearTimeout(window._castShowcaseHideTimer);
                        window._castShowcaseHideTimer = setTimeout(() => {
                            if (showcaseEl) showcaseEl.classList.remove('active');
                        }, 120);
                    });

                    castListEl.appendChild(card);
                });
                if (pauseCast) pauseCast.style.display = 'flex';
            } else {
                if (pauseCast) pauseCast.style.display = 'none';
                if (showcaseEl) showcaseEl.classList.remove('active');
            }
        }

        // Link Probing Overlay Logic
        const pOverlay = document.getElementById('linkProbingOverlay');
        const isProbingActive = pOverlay && pOverlay.classList.contains('active') && !pOverlay.classList.contains('dismissing');
        if (!isProbingActive) {
            triggerMaturityAdvisory();
        }
        const pBackdrop = document.getElementById('linkProbingBackdrop');
        const pLogo = document.getElementById('linkProbingLogo');
        const pTitle = document.getElementById('linkProbingTitle');
        const pList = document.getElementById('linkProbingList');
        const pContent = document.getElementById('linkProbingContent');
        const pStatus = document.getElementById('linkProbingStatus');
        const pToggleBtn = document.getElementById('probingToggleDetailsBtn');

        // Setup Persistent Eye Toggle if not already bound
        if (pToggleBtn && !pToggleBtn.dataset.bound) {
            pToggleBtn.dataset.bound = 'true';
            pToggleBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const currState = localStorage.getItem('cs3_show_probing_details') !== 'false';
                const newState = !currState;
                localStorage.setItem('cs3_show_probing_details', newState ? 'true' : 'false');
                applyProbingListVisibility(newState);
            });
        }

        const applyProbingListVisibility = (showDetails) => {
            const listEl = document.getElementById('linkProbingList');
            const eyeBtn = document.getElementById('probingToggleDetailsBtn');
            const iconOpen = document.getElementById('eyeIconOpen');
            const iconClosed = document.getElementById('eyeIconClosed');

            if (listEl) {
                if (showDetails) {
                    listEl.classList.remove('collapsed');
                } else {
                    listEl.classList.add('collapsed');
                }
            }
            if (eyeBtn) {
                if (showDetails) {
                    eyeBtn.classList.add('active');
                    if (iconOpen) iconOpen.style.display = 'block';
                    if (iconClosed) iconClosed.style.display = 'none';
                } else {
                    eyeBtn.classList.remove('active');
                    if (iconOpen) iconOpen.style.display = 'none';
                    if (iconClosed) iconClosed.style.display = 'block';
                }
            }
        };

        // Initialize visibility based on stored preference
        const isDetailsVisible = localStorage.getItem('cs3_show_probing_details') !== 'false';
        applyProbingListVisibility(isDetailsVisible);

        if (meta.isProbing === true && !userDismissedProbing) {
            // Only re-show the overlay if it isn't already mid-dismiss or dismissed.
            if (!pOverlay.classList.contains('dismissing')) {
                if (window.probingDismissTimer) clearTimeout(window.probingDismissTimer);
                clearTimeout(hideTimer);
                document.body.classList.remove('hidden-controls');
                pOverlay.classList.add('active');
            }
            
            const resumeOvl = document.getElementById('resumeOverlay');
            if (resumeOvl) resumeOvl.style.display = 'none';

            pContent.classList.remove('dismissing');

            const activeEpInfo = (meta.episodes || []).find(e => e.isActive);
            const targetBackdropUrl = meta.backdropUrl || (activeEpInfo ? activeEpInfo.posterUrl : '');

            // Backdrop: immediate display of cached backdrop
            if (pBackdrop && targetBackdropUrl && pBackdrop.src !== targetBackdropUrl) {
                pBackdrop.src = targetBackdropUrl;
            }

            // Logo or text hero (stable, fixed top position)
            const pSubtitle = document.getElementById('linkProbingSubtitle');
            if (activeEpInfo) {
                const s = activeEpInfo.season !== undefined && activeEpInfo.season !== null ? activeEpInfo.season : 1;
                const ep = activeEpInfo.episode;
                const epTitle = activeEpInfo.title ? activeEpInfo.title : `Episode ${ep}`;
                let showTitle = meta.title || '';
                if (showTitle.includes(' - ')) {
                    showTitle = showTitle.split(' - ')[0].trim();
                }
                if (meta.logoUrl) {
                    if (pLogo) {
                        if (pLogo.getAttribute('data-src') !== meta.logoUrl) {
                            pLogo.setAttribute('data-src', meta.logoUrl);
                            pLogo.src = meta.logoUrl;
                        }
                        pLogo.style.display = 'block';
                    }
                    if (pTitle) pTitle.style.display = 'none';
                } else {
                    if (pLogo) {
                        pLogo.removeAttribute('data-src');
                        pLogo.style.display = 'none';
                    }
                    if (pTitle) { pTitle.innerText = showTitle; pTitle.style.display = 'block'; }
                }
                if (pSubtitle) {
                    pSubtitle.innerText = `S${s}:E${ep} • ${epTitle}`;
                    pSubtitle.style.display = 'inline-block';
                }
            } else {
                if (meta.logoUrl) {
                    if (pLogo) {
                        if (pLogo.getAttribute('data-src') !== meta.logoUrl) {
                            pLogo.setAttribute('data-src', meta.logoUrl);
                            pLogo.src = meta.logoUrl;
                        }
                        pLogo.style.display = 'block';
                    }
                    if (pTitle) pTitle.style.display = 'none';
                } else {
                    if (pLogo) {
                        pLogo.removeAttribute('data-src');
                        pLogo.style.display = 'none';
                    }
                    if (pTitle) { pTitle.innerText = meta.title || ''; pTitle.style.display = 'block'; }
                }
                if (pSubtitle) pSubtitle.style.display = 'none';
            }

            // Dynamic Step-by-Step Live Status label
            const totalLinks = (meta.links || []).length;
            const failedArr = meta.failedLinks || [];
            const failedCount = failedArr.length;
            const lastFailed = failedArr[failedArr.length - 1];
            const currIdx = typeof meta.currentLinkIndex === 'number' ? meta.currentLinkIndex : 0;
            const currentLinkObj = (meta.links || [])[currIdx] || (meta.links || [])[failedCount];
            const currQualityLabel = (currentLinkObj && currentLinkObj.quality && currentLinkObj.quality > 0 && currentLinkObj.quality !== 400) ? ` (${currentLinkObj.quality}p)` : '';

            if (pStatus) {
                if (failedCount >= totalLinks && totalLinks > 0) {
                    pStatus.innerText = `All ${totalLinks} sources failed • Waiting for fallback…`;
                } else if (failedCount > 0 && lastFailed) {
                    const failReason = lastFailed.reason ? ` (${lastFailed.reason})` : '';
                    pStatus.innerText = `Source ${failedCount} failed${failReason} • Switching to source ${currIdx + 1} of ${totalLinks}${currQualityLabel}…`;
                } else if (meta.isScraping === true) {
                    if (totalLinks === 0) {
                        pStatus.innerText = 'Discovering streaming sources…';
                    } else {
                        pStatus.innerText = `Found ${totalLinks} source${totalLinks === 1 ? '' : 's'} • Searching for best quality…`;
                    }
                } else if (totalLinks > 0) {
                    pStatus.innerText = `Connecting to source ${currIdx + 1} of ${totalLinks}${currQualityLabel}…`;
                } else {
                    pStatus.innerText = 'Discovering streaming sources…';
                }
            }

            // High-detail Glassmorphic Link cards
            if (meta.links && meta.links.length > 0) {
                pList.innerHTML = meta.links.map((l, i) => {
                    let st = 'waiting';
                    let statusBadgeHtml = '';
                    const failedInfo = failedArr.find(f => f.index === l.index);

                    const qVal = l.quality;
                    let qBadgeClass = 'fhd';
                    let qLabel = '';
                    if (qVal && qVal >= 2160) {
                        qBadgeClass = 'uhd';
                        qLabel = '4K UHD';
                    } else if (qVal && qVal >= 1080) {
                        qBadgeClass = 'fhd';
                        qLabel = '1080p FHD';
                    } else if (qVal && qVal >= 720) {
                        qBadgeClass = 'fhd';
                        qLabel = '720p HD';
                    } else if (qVal && qVal > 0 && qVal !== 400) {
                        qBadgeClass = '';
                        qLabel = `${qVal}p`;
                    }
                    const qualityHtml = qLabel ? `<span class="link-quality-badge ${qBadgeClass}">${qLabel}</span>` : '';

                    if (failedInfo) {
                        st = 'failed';
                        const err = failedInfo.reason ? `FAILED (${failedInfo.reason})` : 'FAILED';
                        statusBadgeHtml = `<span class="link-status-badge failed"><svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg> ${err}</span>`;
                    } else if (l.index === currIdx) {
                        st = 'active';
                        statusBadgeHtml = `<span class="link-status-badge testing"><div class="link-spinner"><span></span></div> CONNECTING</span>`;
                    } else if (l.index > currIdx) {
                        st = 'waiting';
                        statusBadgeHtml = `<span class="link-status-badge queue">IN QUEUE</span>`;
                    } else {
                        st = 'waiting';
                        statusBadgeHtml = `<span class="link-status-badge queue">SKIPPED</span>`;
                    }

                    return `<div class="link-probing-item ${st}">
                        <div style="display: flex; align-items: center; gap: 4px; min-width: 0;">
                            <span style="font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 240px;">${l.name}</span>
                            ${qualityHtml}
                        </div>
                        <div>${statusBadgeHtml}</div>
                    </div>`;
                }).join('');

                // Scroll current item into view
                setTimeout(() => {
                    const activeProb = pList.querySelector('.link-probing-item.active');
                    if (activeProb) activeProb.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }, 80);
            } else {
                pList.innerHTML = `<div class="probing-initial-spinner">
                    <div class="link-spinner"><span></span></div>
                    <span>Discovering high-speed playback sources…</span>
                </div>`;
            }

            const pPlayBtn = document.getElementById('probingPlayBtn');
            if (pPlayBtn) {
                pPlayBtn.style.display = (meta.links && meta.links.length > 0) ? 'inline-flex' : 'none';
            }
        } else if (meta.isProbing === false) {
            // Kotlin finished scraping/link selection.
            const pStatus = document.getElementById('linkProbingStatus');
            if (pStatus) {
                pStatus.innerText = 'Connected • Launching player…';
            }
            
            // Highlight the successfully resolved link
            const pList = document.getElementById('linkProbingList');
            if (pList) {
                const activeProb = pList.querySelector('.link-probing-item.active');
                if (activeProb) {
                    activeProb.classList.remove('active');
                    activeProb.classList.add('success');
                    const badgeContainer = activeProb.querySelector('div:last-child');
                    if (badgeContainer) {
                        badgeContainer.innerHTML = '<span class="link-status-badge ready"><svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/></svg> READY</span>';
                    }
                }
            }
        }

        evaluateResumeOverlay();
        evaluateUIStates();
        // Episodes
        if (meta.episodes && meta.episodes.length > 0) {
            episodesBtn.classList.remove('hidden');
            episodesData = meta.episodes;
            document.getElementById('epPanelSub').innerText = `${meta.episodes.length} episodes`;

            // Detect unique seasons
            const seasons = [...new Set(meta.episodes.map(ep => ep.season !== undefined && ep.season !== null ? ep.season : 1))];
            seasons.sort((a, b) => a - b);

            const sChipsBar = document.getElementById('seasonChipsBar');
            const activeEp = meta.episodes.find(e => e.isActive);
            const activeSeason = activeEp && activeEp.season !== undefined && activeEp.season !== null ? activeEp.season : seasons[0];

            if (seasons.length > 1) {
                if (seasonSelectWrap) seasonSelectWrap.style.display = 'block';
                if (sChipsBar) {
                    sChipsBar.innerHTML = seasons.map(s => {
                        const count = meta.episodes.filter(ep => (ep.season !== undefined && ep.season !== null ? ep.season : 1) === s).length;
                        const isSActive = s === activeSeason;
                        return `<button class="season-chip ${isSActive ? 'active' : ''}" onclick="onSeasonChipClick(${s})">
                            Season ${s} <span class="season-chip-count">(${count})</span>
                        </button>`;
                    }).join('');
                }
                renderFilteredEpisodes(activeSeason);
            } else {
                if (seasonSelectWrap) seasonSelectWrap.style.display = 'none';
                renderFilteredEpisodes(seasons[0] || 1);
            }

            const activeIdx = meta.episodes.findIndex(e => e.isActive);
            if (activeIdx !== -1 && activeIdx < meta.episodes.length - 1) nextEpBtn.classList.remove('hidden');
            else nextEpBtn.classList.add('hidden');
        } else {
            episodesData = [];
            episodesBtn.classList.add('hidden');
            nextEpBtn.classList.add('hidden');
        }
        // Links / Servers
        if (meta.links && meta.links.length > 0) {
            linksData = meta.links;
            const filterWrap = document.getElementById('serversFilterWrap');
            if (meta.links.length > 1) {
                filterWrap.style.display = 'block';
            } else {
                filterWrap.style.display = 'none';
            }
            renderFilteredServers();
        } else {
            linksData = [];
            document.getElementById('serverSubtitle').innerText = '0 sources available';
            document.getElementById('serversList').innerHTML = '';
        }
        // Audio Tracks
        let audioHtml = '';
        const renderedAudioNames = new Set();

        // 1. Native MPV Audio Tracks (already attached to MPV engine)
        if (meta.audioTracks && meta.audioTracks.length > 0) {
            for (const t of meta.audioTracks) {
                const displayName = t.name || ('Track ' + t.id);
                renderedAudioNames.add(displayName.toLowerCase().trim());
                const normalized = normalizeAudioName(displayName);
                if (normalized) renderedAudioNames.add(normalized);

                audioHtml += `
                    <div class="track-item ${t.isSelected ? 'active' : ''}" onclick="send('setAudioTrack','${t.id}');closeAllPanels();">
                        <span class="track-name">${displayName}</span>
                        <span class="track-check">${t.isSelected ? SVGS.check : ''}</span>
                    </div>`;
            }
        }

        // 2. Lazy Proxy Audio Tracks (available alternative languages)
        if (meta.lazyAudioTracks && meta.lazyAudioTracks.length > 0) {
            for (const t of meta.lazyAudioTracks) {
                const displayName = t.name;
                const normalized = normalizeAudioName(displayName);
                const isAlreadyAttached = (meta.activeLazyAudioTrackUrl && meta.activeLazyAudioTrackUrl === t.url);
                const isNameRendered = renderedAudioNames.has(displayName.toLowerCase().trim()) || (normalized && renderedAudioNames.has(normalized));

                if (!isAlreadyAttached && !isNameRendered) {
                    audioHtml += `
                        <div class="track-item" onclick="send('loadLazyAudioTrack','${t.url}');closeAllPanels();">
                            <span class="track-name">${displayName}</span>
                            <span class="track-check"></span>
                        </div>`;
                }
            }
        }
        document.getElementById('audioList').innerHTML = audioHtml || `<div style="padding:10px 20px;font-size:13px;color:#666;">No audio tracks</div>`;

        // Video Tracks (Qualities)
        let videoHtml = '';
        const renderedVideoNames = new Set();

        // 1. Lazy Video Quality Variants (from HLS / DASH manifests)
        if (meta.lazyVideoTracks && meta.lazyVideoTracks.length > 0) {
            const anyMatched = meta.lazyVideoTracks.some(t => isTrackMatchingResolution(t.name, t.url, meta));

            meta.lazyVideoTracks.forEach((t, index) => {
                const displayName = t.name;
                renderedVideoNames.add(displayName.toLowerCase().trim());
                const hd = isTrackHD(displayName, meta);
                const resBadge = `<span class="srv-badge ${hd ? 'hd' : 'sd'}">${hd ? 'HD' : 'SD'}</span>`;
                
                const isActive = anyMatched 
                    ? isTrackMatchingResolution(t.name, t.url, meta)
                    : (index === 0);
                    
                videoHtml += `
                    <div class="track-item ${isActive ? 'active' : ''}" onclick="send('loadLazyVideoTrack','${t.url}');closeAllPanels();">
                        <span class="track-name">${displayName} ${resBadge}</span>
                        <span class="track-check">${isActive ? SVGS.check : ''}</span>
                    </div>`;
            });
        }

        // 2. Native MPV Video Tracks (if multiple native video tracks exist)
        if (meta.videoTracks && meta.videoTracks.length > 1) {
            for (const t of meta.videoTracks) {
                const displayName = t.name || ('Track ' + t.id);
                if (!renderedVideoNames.has(displayName.toLowerCase().trim())) {
                    const hd = isTrackHD(displayName, meta);
                    const resBadge = `<span class="srv-badge ${hd ? 'hd' : 'sd'}">${hd ? 'HD' : 'SD'}</span>`;
                    videoHtml += `
                        <div class="track-item ${t.isSelected ? 'active' : ''}" onclick="send('setVideoTrack','${t.id}');closeAllPanels();">
                            <span class="track-name">${displayName} ${resBadge}</span>
                            <span class="track-check">${t.isSelected ? SVGS.check : ''}</span>
                        </div>`;
                }
            }
        } else if (meta.videoTracks && meta.videoTracks.length === 1 && videoHtml === '') {
            const t = meta.videoTracks[0];
            const displayName = meta.resolution || t.name || 'Auto';
            const hd = isTrackHD(displayName, meta);
            const resBadge = `<span class="srv-badge ${hd ? 'hd' : 'sd'}">${hd ? 'HD' : 'SD'}</span>`;
            videoHtml += `
                <div class="track-item active" onclick="send('setVideoTrack','${t.id}');closeAllPanels();">
                    <span class="track-name">${displayName} ${resBadge}</span>
                    <span class="track-check">${SVGS.check}</span>
                </div>`;
        }
        document.getElementById('videoList').innerHTML = videoHtml || `<div style="padding:10px 20px;font-size:13px;color:#666;">Auto (Default)</div>`;

        // Subtitle Tracks
        let subHtml = '';
        const noSub = !(meta.subTracks && meta.subTracks.some(t => t.isSelected));
        subHtml += `<div class="sub-item ${noSub ? 'active' : ''}" onclick="send('setSubtitleTrack','');closeAllPanels();">
                    <span class="sub-name">Off</span>
                    <span class="check-icon">${SVGS.check}</span>
                </div>`;
        if (meta.subTracks && meta.subTracks.length > 0) {
            subHtml += meta.subTracks.map(t => {
                const displayName = t.name || ('Track ' + t.id);
                return `
                    <div class="sub-item ${t.isSelected ? 'active' : ''}" onclick="send('setSubtitleTrack','${t.id}');closeAllPanels();">
                        <span class="sub-name">${displayName}</span>
                        <span class="check-icon">${t.isSelected ? SVGS.check : ''}</span>
                    </div>`;
            }).join('');
        }
        if (meta.lazySubTracks && meta.lazySubTracks.length > 0) {
            // Deduplicate against the already rendered native tracks to prevent lazy duplicates
            const lazyUnique = meta.lazySubTracks.filter(t => !subHtml.includes(t.name));
            subHtml += lazyUnique.map(t => `
                <div class="sub-item" onclick="send('loadLazySubtitleTrack','${t.url}');closeAllPanels();">
                    <span class="sub-name">${t.name}</span>
                    <span class="check-icon"></span>
                </div>`).join('');
        }

        subHtml += `
            <div style="border-top: 1px solid rgba(255,255,255,0.1); margin-top: 5px; padding-top: 5px;"></div>
            <div class="sub-item" onclick="openSubSearchModal();closeAllPanels();">
                <span class="sub-name" style="color: #64B5F6;">Search Online Subtitles...</span>
                <span class="check-icon"></span>
            </div>
            <div class="sub-item" onclick="send('openLocalSubtitlePicker','');closeAllPanels();">
                <span class="sub-name" style="color: #64B5F6;">Load Local Subtitle...</span>
                <span class="check-icon"></span>
            </div>
        `;

        document.getElementById('subList').innerHTML = subHtml;

        // Auto-populate search query with title whenever metadata arrives
        const searchInput = document.getElementById('subSearchQuery');
        if (searchInput && !searchInput._userEdited && meta.title) {
            searchInput.value = meta.title;
            searchInput._defaultTitle = meta.title;
        }
        // Update custom font input
        const subFontInput = document.getElementById('subFontInput');
        if (subFontInput && meta.availableSubtitleFonts !== undefined) {
            let optionsHtml = '<option value="" style="background:#222;">Default</option>';
            meta.availableSubtitleFonts.forEach(font => {
                optionsHtml += `<option value="${font}" style="background:#222;">${font}</option>`;
            });
            subFontInput.innerHTML = optionsHtml;
            subFontInput.value = meta.activeSubtitleFont || '';
        }

        // Subtitle Override Toggle State
        if (meta.activeSubtitleOverrideEnabled !== undefined) {
            subOverrideVisible = meta.activeSubtitleOverrideEnabled === true;
            const btnOverride = document.getElementById('btnToggleSubOverride');
            if (btnOverride) {
                if (subOverrideVisible) {
                    btnOverride.classList.add('active');
                } else {
                    btnOverride.classList.remove('active');
                }
            }
        }

        // Initialize Background Select
        if (meta.activeSubtitleBackground) {
            const bgSelect = document.getElementById('subBgInput');
            if (bgSelect) {
                bgSelect.value = meta.activeSubtitleBackground;
            }
        }

        // Advanced Subtitle Styles
        if (meta.activeSubtitleBorderColor) {
            document.querySelectorAll('#subBorderColorPalette .border-dot').forEach(d => {
                if (d.dataset.color === meta.activeSubtitleBorderColor) {
                    document.querySelectorAll('#subBorderColorPalette .border-dot').forEach(x => x.classList.remove('active'));
                    d.classList.add('active');
                }
            });
        }
        if (meta.activeSubtitleShadowColor) {
            document.querySelectorAll('#subShadowColorPalette .shadow-dot').forEach(d => {
                if (d.dataset.color === meta.activeSubtitleShadowColor) {
                    document.querySelectorAll('#subShadowColorPalette .shadow-dot').forEach(x => x.classList.remove('active'));
                    d.classList.add('active');
                }
            });
        }
        if (meta.activeSubtitleBorderSize) {
            const slider = document.getElementById('subBorderSizeSlider');
            if (slider) {
                slider.value = meta.activeSubtitleBorderSize;
                const badge = document.getElementById('subBorderSizeVal');
                if (badge) badge.innerText = `${meta.activeSubtitleBorderSize}px`;
            }
        }
        if (meta.activeSubtitleShadowOffset) {
            const slider = document.getElementById('subShadowOffsetSlider');
            if (slider) {
                slider.value = meta.activeSubtitleShadowOffset;
                const badge = document.getElementById('subShadowOffsetVal');
                if (badge) badge.innerText = `${meta.activeSubtitleShadowOffset}px`;
            }
        }
        if (meta.activeSubtitleBlur) {
            const slider = document.getElementById('subBlurSlider');
            if (slider) {
                slider.value = meta.activeSubtitleBlur;
                const badge = document.getElementById('subBlurVal');
                if (badge) badge.innerText = `${meta.activeSubtitleBlur}px`;
            }
        }
        
        let subBoldState = meta.activeSubtitleBold === 'yes';
        let subItalicState = meta.activeSubtitleItalic === 'yes';
        const btnBold = document.getElementById('btnSubBold');
        if (btnBold) {
            if (subBoldState) btnBold.classList.add('active');
            else btnBold.classList.remove('active');
        }
        const btnItalic = document.getElementById('btnSubItalic');
        if (btnItalic) {
            if (subItalicState) btnItalic.classList.add('active');
            else btnItalic.classList.remove('active');
        }

        if (window.updateSubtitlePreview) {
            window.updateSubtitlePreview();
        }

        // Shaders
        let shaderHtml = '';
        const noShaderActive = !meta.activeShader || meta.activeShader === 'None';
        shaderHtml += `<div class="sub-item ${noShaderActive ? 'active' : ''}" onclick="send('selectShader','None');closeAllPanels();">
            <span class="sub-name">None (Default)</span>
            <span class="check-icon">${SVGS.check}</span>
        </div>`;
        if (meta.shaders && meta.shaders.length > 0) {
            shaderHtml += meta.shaders.map(s => `
                <div class="sub-item ${s === meta.activeShader ? 'active' : ''}" onclick="send('selectShader','${s}');closeAllPanels();">
                    <span class="sub-name">${s.replace('.glsl', '')}</span>
                    <span class="check-icon">${SVGS.check}</span>
                </div>
            `).join('');
        }
        document.getElementById('shaderList').innerHTML = shaderHtml;

        // ── Chapters Handling ─────────────────────────────────────────
        _cachedChapters = meta.chapters || [];
        _activeChapterIndex = typeof meta.currentChapterIndex === 'number' ? meta.currentChapterIndex : -1;
        
        if (_cachedChapters && _cachedChapters.length > 0) {
            chaptersBtn?.classList.remove('hidden');
        } else {
            chaptersBtn?.classList.add('hidden');
            if (document.getElementById('chaptersPanel')?.classList.contains('open')) {
                closeAllPanels();
            }
        }
        
        // ── Skip Intervals & Active Skip Button ─────────────────────────
        _cachedSkipIntervals = meta.skipIntervals || [];
        const skipBtn = document.getElementById('skipBtn');
        const skipBtnLabel = document.getElementById('skipBtnLabel');
        if (skipBtn) {
            const activeInv = meta.activeSkipInterval || (_cachedSkipIntervals.find(inv => currentPosMs >= inv.startMs && currentPosMs < inv.endMs));
            if (activeInv) {
                if (skipBtnLabel) skipBtnLabel.innerText = activeInv.label || 'Skip Intro';
                skipBtn.style.display = 'flex';
            } else {
                skipBtn.style.display = 'none';
            }
        }

        renderChaptersList(_cachedChapters, _activeChapterIndex);
        renderSeekbarChapters(_cachedChapters, meta.skipIntervals);
    };

    let _lastRenderedChaptersJson = '';
    const renderChaptersList = (chapters, activeIndex) => {
        if (!chaptersList) return;
        if (!chapters || chapters.length === 0) {
            _lastRenderedChaptersJson = '';
            chaptersList.innerHTML = '<div style="padding: 24px; text-align: center; color: rgba(255,255,255,0.4); font-size: 13px;">No chapters found for this video</div>';
            if (chaptersSubtitle) chaptersSubtitle.innerText = 'No chapters available';
            return;
        }
        
        if (chaptersSubtitle) {
            chaptersSubtitle.innerText = `${chapters.length} chapter${chapters.length > 1 ? 's' : ''}`;
        }
        
        const currentJson = JSON.stringify(chapters);
        if (_lastRenderedChaptersJson !== currentJson) {
            _lastRenderedChaptersJson = currentJson;
            chaptersList.innerHTML = chapters.map((ch, idx) => {
                const isActive = idx === activeIndex;
                return `
                    <div class="chapter-item ${isActive ? 'active' : ''}" onclick="send('seekTo', ${ch.timeMs}); closeAllPanels();">
                        <div class="chapter-info">
                            <div class="chapter-num">${idx + 1}</div>
                            <div class="chapter-title">${escapeHtml(ch.title || `Chapter ${idx + 1}`)}</div>
                        </div>
                        <div class="chapter-time">${fmt(ch.timeMs)}</div>
                    </div>
                `;
            }).join('');
        } else {
            // Update active state in-place without rebuilding DOM under hover cursor
            const items = chaptersList.querySelectorAll('.chapter-item');
            if (items) {
                items.forEach((item, idx) => {
                    if (idx === activeIndex) item.classList.add('active');
                    else item.classList.remove('active');
                });
            }
        }
    };

    const renderSeekbarChapters = (chapters, skipIntervals) => {
        if (!seekChapters) return;
        seekChapters.innerHTML = '';
        
        // Render skip intervals (highlighted zones on seekbar)
        if (skipIntervals && skipIntervals.length > 0 && durationMs > 0) {
            skipIntervals.forEach(inv => {
                const leftPct = Math.max(0, Math.min(100, (inv.startMs / durationMs) * 100));
                const rightPct = Math.max(0, Math.min(100, (inv.endMs / durationMs) * 100));
                const widthPct = Math.max(0.5, rightPct - leftPct);

                const div = document.createElement('div');
                div.className = `seek-skip-interval ${inv.type ? inv.type.toLowerCase() : ''}`;
                div.style.left = `${leftPct}%`;
                div.style.width = `${widthPct}%`;
                div.title = `${inv.label || 'Skip'} (${fmt(inv.startMs)} - ${fmt(inv.endMs)})`;
                seekChapters.appendChild(div);
            });
        }

        if (!chapters || chapters.length <= 1 || durationMs <= 0) return;
        
        chapters.forEach((ch, idx) => {
            if (idx === 0 && ch.timeMs === 0) return; // Skip zero start position
            const pct = Math.max(0, Math.min(100, (ch.timeMs / durationMs) * 100));
            const notch = document.createElement('div');
            notch.className = 'chapter-notch';
            notch.style.left = `${pct}%`;
            notch.title = `${ch.title || `Chapter ${idx + 1}`} (${fmt(ch.timeMs)})`;
            seekChapters.appendChild(notch);
        });
    };

    // Seekbar Hover Tooltip
    if (seekWrap && seekTooltip) {
        seekWrap.addEventListener('mousemove', e => {
            if (durationMs <= 0) return;
            const rect = seekWrap.getBoundingClientRect();
            const offsetX = Math.max(0, Math.min(rect.width, e.clientX - rect.left));
            const pct = offsetX / rect.width;
            const hoverTimeMs = pct * durationMs;
            
            let chapterText = '';
            if (_cachedChapters && _cachedChapters.length > 0) {
                let matchingCh = null;
                for (let i = _cachedChapters.length - 1; i >= 0; i--) {
                    if (hoverTimeMs >= _cachedChapters[i].timeMs) {
                        matchingCh = _cachedChapters[i];
                        break;
                    }
                }
                if (matchingCh && matchingCh.title) {
                    chapterText = `<span class="seek-tooltip-chapter">${escapeHtml(matchingCh.title)}</span>`;
                }
            }
            
            seekTooltip.innerHTML = `${fmt(hoverTimeMs)}${chapterText}`;
            seekTooltip.style.left = `${offsetX}px`;
            seekTooltip.classList.add('visible');
        });
        
        seekWrap.addEventListener('mouseleave', () => {
            seekTooltip.classList.remove('visible');
        });
    }

    const dismissProbingOverlay = (userInitiated = false) => {
        // Once dismissed (by user click OR by onPlaybackReady), it stays dismissed
        // for the entire session. Only hardResetAllOverlays() on a new episode can bring it back.
        userDismissedProbing = true;
        
        const pOverlay = document.getElementById('linkProbingOverlay');
        const pContent = document.getElementById('linkProbingContent');
        if (!pOverlay) return;
        
        // Ensure minimum visual buffer time (500ms) so fast links don't flash jarringly
        const elapsed = Date.now() - (window.sessionStartTime || 0);
        const minBufferWait = userInitiated ? 0 : Math.max(0, 500 - elapsed);

        setTimeout(() => {
            // Fade content out first
            if (pContent) pContent.classList.add('dismissing');
            
            setTimeout(() => {
                // Crossfade the dark backdrop out and disable overlay
                pOverlay.classList.add('dismissing');
                pOverlay.classList.remove('active');
                
                // Keep controls cleanly hidden on playback start (Nuvio / Netflix / Apple TV standard)
                if (overlay) overlay.classList.add('hidden-controls');
                document.body.classList.add('hidden-controls');
                evaluateUIStates();

                // Trigger maturity advisory on clean playback start
                triggerMaturityAdvisory();
            }, 250);
        }, minBufferWait);
    };
    // Expose globally so Kotlin can call via executeScript("window.__dismissProbingOverlay()")
    window.__dismissProbingOverlay = dismissProbingOverlay;

    // Handles volume/mute/app-loading-status sent by Kotlin (separate from C++ state_update)
    const handleAppStateUpdate = (s) => {
        if (s.debugWait !== undefined) window.debugWait = s.debugWait;
        if (s.debugHasEver !== undefined) window.debugHasEver = s.debugHasEver;
        if (s.debugPos !== undefined) window.debugPos = s.debugPos;

        if (typeof s.volume === 'number') {
            currentVolume = s.volume;
            volumeBar.value = s.volume;
            updateVolumeTrack(s.volume);
        }
        if (s.isMuted !== undefined) {
            isMuted = s.isMuted === true;
        }
        updateMuteIcon();
        
        if (s.isAppLoading !== undefined) {
            isAppLoading = s.isAppLoading === true;
        }
        if (s.loadingStatusText !== undefined && s.loadingStatusText !== null) {
            document.getElementById('loadingStatus').innerText = s.loadingStatusText;
            const pStatus = document.getElementById('linkProbingStatus');
            const pOverlay = document.getElementById('linkProbingOverlay');
            if (pStatus && pOverlay && pOverlay.classList.contains('active')) {
                pStatus.innerText = s.loadingStatusText;
            }
        }

        if (s.interpolationEnabled !== undefined) {
            interpolationEnabled = s.interpolationEnabled === true;
            const btn = document.getElementById('btnToggleInterpolation');
            if (btn) {
                if (interpolationEnabled) {
                    btn.classList.add('active');
                    btn.innerText = 'On';
                } else {
                    btn.classList.remove('active');
                    btn.innerText = 'Off';
                }
            }
        }

        if (s.autoPlayEnabled !== undefined) {
            window.autoPlayEnabled = s.autoPlayEnabled === true;
            const btn = document.getElementById('btnToggleAutoPlay');
            if (btn) {
                if (window.autoPlayEnabled) {
                    btn.classList.add('active');
                    btn.innerText = 'On';
                } else {
                    btn.classList.remove('active');
                    btn.innerText = 'Off';
                }
            }
        }

        if (s.showEndTime !== undefined) {
            const btn = document.getElementById('btnToggleEndTime');
            if (btn) {
                if (s.showEndTime === true) {
                    btn.classList.add('active');
                    btn.innerText = 'On';
                } else {
                    btn.classList.remove('active');
                    btn.innerText = 'Off';
                }
            }
        }

        if (s.showClock !== undefined) {
            const btn = document.getElementById('btnToggleClock');
            if (btn) {
                if (s.showClock === true) {
                    btn.classList.add('active');
                    btn.innerText = 'On';
                } else {
                    btn.classList.remove('active');
                    btn.innerText = 'Off';
                }
            }
        }

        if (s.showServerQuality !== undefined) {
            const btn = document.getElementById('btnToggleServerQuality');
            if (btn) {
                if (s.showServerQuality === true) {
                    btn.classList.add('active');
                } else {
                    btn.classList.remove('active');
                }
            }
        }

        if (s.pauseInfoMode !== undefined && s.pauseInfoMode !== null) {
            pauseInfoMode = s.pauseInfoMode;
            updatePauseInfoBadge();
        }

        if (s.showPauseCast !== undefined && s.showPauseCast !== null) {
            showPauseCast = !!s.showPauseCast;
        }

        evaluateUIStates();
        evaluateResumeOverlay();
    };

    // Handles live MPV stats sent by C++ when stats panel is open
    const handleStatsUpdate = (s) => {
        const fmtBitrate = (bps) => {
            if (!bps || bps <= 0) return '0 Kbps';
            if (bps >= 1e6) return (bps / 1e6).toFixed(2) + ' Mbps';
            return (bps / 1e3).toFixed(0) + ' Kbps';
        };
        const fps = s.fps || 0;
        const dropped = (s.droppedFrames || 0) + (s.voDroppedFrames || 0);

        const badge = document.getElementById('statsBadgeFps');
        if (badge) badge.innerText = fps > 0 ? fps.toFixed(1) + ' fps' : '-- fps';

        const setVal = (id, v, cls) => {
            const el = document.getElementById(id);
            if (!el) return;
            el.innerText = (v !== undefined && v !== null && v !== 'N/A') ? v : 'N/A';
            el.className = 'stats-val' + (cls ? ' ' + cls : '');
        };

        const vId = window.sessionId ? window.sessionId.substring(0, 16) : 'Unknown';
        const vcpn = Math.random().toString(36).substring(2, 6).toUpperCase();
        setVal('sVideoId', `${vId} / ${vcpn}`);

        const width = window.innerWidth * window.devicePixelRatio;
        const height = window.innerHeight * window.devicePixelRatio;
        setVal('sViewportFrames', `${Math.round(width)}x${Math.round(height)}*${window.devicePixelRatio.toFixed(2)} / ${dropped} dropped`);

        const vWidth = s.width || 0;
        const vHeight = s.height || 0;
        const resStr = (vWidth && vHeight) ? `${vWidth}x${vHeight}@${fps > 0 ? Math.round(fps) : 30}` : 'Unknown';
        setVal('sCurrentOptimalRes', `${resStr} / ${resStr}`);

        const vol = Math.round((window.currentVolume || 1.0) * 100);
        setVal('sVolumeNormalized', `${vol}% / ${vol}% (content loudness -- dB)`);

        const vCodec = s.videoCodec || 'unknown';
        const aCodec = s.audioCodec || 'unknown';
        const cProfile = s.videoFormat || '';
        setVal('sCodecs', `${vCodec} (${cProfile}) / ${aCodec} (${s.audioChannels || '2'})`);

        const colorMatrix = s.colorMatrix && s.colorMatrix !== 'N/A' ? s.colorMatrix : '';
        const colorPrimaries = s.colorPrimaries && s.colorPrimaries !== 'N/A' ? s.colorPrimaries : '';
        const colorLevels = s.colorLevels && s.colorLevels !== 'N/A' ? s.colorLevels : '';
        const colorInfo = [colorMatrix, colorPrimaries, colorLevels].filter(x => x).join(' / ') || 'bt709 / bt709';
        setVal('sColorInfo', colorInfo);

        let host = 'localhost';
        try {
            if (s.path && s.path.startsWith('http')) {
                host = new URL(s.path).host;
            }
        } catch(e){}
        setVal('sHost', host);

        setVal('sConnectionSpeed', fmtBitrate((s.videoBitrate || 0) + (s.audioBitrate || 0)));
        setVal('sNetworkActivity', '0 KB');

        const bufAhead = durationMs > 0 ? ((s.bufferMs || 0) - currentPosMs) / 1000 : null;
        setVal('sBuffer', bufAhead !== null && bufAhead >= 0 ? bufAhead.toFixed(1) + ' s' : '0.0 s');

        setVal('sLiveLatency', durationMs > 0 ? 'N/A' : '0.00 s');

        const sRate = s.audioSampleRate || 0;
        setVal('sAudioCodec', `${aCodec} (${sRate > 0 ? (sRate/1000).toFixed(1) + 'kHz' : ''})`);

        const sync = s.avsync !== undefined && s.avsync !== null ? s.avsync : 0;
        setVal('sAvSync', sync !== 0 ? (sync * 1000).toFixed(1) + ' ms' : '0.0 ms', Math.abs(sync) > 0.05 ? 'warn' : '');

        const hwdec = (s.hwdec && s.hwdec !== 'no' && s.hwdec !== 'N/A') ? s.hwdec : 'Software (CPU)';
        setVal('sHwdec', hwdec, hwdec !== 'Software (CPU)' ? 'good' : '');

        setVal('sMysteryText', `vd: ${vWidth} / ad: ${s.audioChannels || 2} / s: ${Math.round(fps)}`);

        const pathEl = document.getElementById('sPath');
        if (pathEl) pathEl.innerText = s.path || '--';
    };

    const formatP2pSpeed = (bytesPerSec) => {
        if (!bytesPerSec || bytesPerSec <= 0) return '0 KB/s';
        if (bytesPerSec >= 1024 * 1024) {
            return (bytesPerSec / (1024 * 1024)).toFixed(1) + ' MB/s';
        }
        return (bytesPerSec / 1024).toFixed(0) + ' KB/s';
    };

    const handleP2pStatsUpdate = (p) => {
        const hudPill = document.getElementById('p2pHudPill');
        const hudSpeed = document.getElementById('p2pHudSpeed');
        const hudSeeds = document.getElementById('p2pHudSeeds');
        const loadingStats = document.getElementById('p2pLoadingStats');
        const preloadFill = document.getElementById('p2pPreloadFill');
        const preloadDetail = document.getElementById('p2pPreloadDetail');

        if (p && p.active) {
            if (hudPill) hudPill.style.display = 'inline-flex';
            if (hudSpeed) hudSpeed.innerText = '↓ ' + formatP2pSpeed(p.downloadSpeed);
            if (hudSeeds) hudSeeds.innerText = `${p.seeds || 0} Seeds · ${p.peers || 0} Peers`;

            const loadingContainer = document.getElementById('loadingContainer');
            const isBuffering = loadingContainer && loadingContainer.classList.contains('show');

            if (loadingStats) {
                if (isBuffering || (p.progressPercent > 0 && p.progressPercent < 100)) {
                    loadingStats.style.display = 'flex';
                    if (preloadFill) preloadFill.style.width = Math.min(100, Math.max(0, p.progressPercent)) + '%';
                    if (preloadDetail) {
                        const stateMsg = p.statusText || 'Preloading Buffer';
                        preloadDetail.innerText = `${stateMsg} • ${p.seeds || 0} Seeds • ${formatP2pSpeed(p.downloadSpeed)}`;
                    }
                } else {
                    loadingStats.style.display = 'none';
                }
            }
        } else {
            if (hudPill) hudPill.style.display = 'none';
            if (loadingStats) loadingStats.style.display = 'none';
        }
    };

    const handleMessage = (data) => {
        try {
            // WebView2 PostWebMessageAsJson delivers the message as EITHER:
            //   - a string (when received via window.chrome.webview 'message' event e.data)
            //   - an already-parsed object (in some WebView2 versions / paths)
            // We must handle both cases.
            const p = (typeof data === 'string') ? JSON.parse(data) :
                      (data && typeof data === 'object') ? data :
                      JSON.parse(String(data));
            if (!p || !p.type) return;
            if (p.type === 'state_update') handleStateUpdate(p);
            else if (p.type === 'app_state_update') handleAppStateUpdate(p);
            else if (p.type === 'stats_update') handleStatsUpdate(p);
            else if (p.type === 'p2p_stats_update') handleP2pStatsUpdate(p);
            else if (p.type === 'metadata_update') handleMetadataUpdate(p.value);
            else if (p.type === 'dismiss_probing') dismissProbingOverlay();
            else if (p.type === 'subtitle_search_results') handleSubtitleSearchResults(p);
            else if (p.type === 'show_toast') showToast(p.message);
        } catch(e) {
            // If JSON.parse fails on raw string, try treating it as JSON directly
            try {
                if (typeof data === 'string' && data.includes('dismiss_probing')) {
                    dismissProbingOverlay();
                }
            } catch(_) {}
        }
    };

    // window.playerUpdate is the legacy path used by some Kotlin callers that
    // call executeScript("window.playerUpdate(...)") directly
    window.playerUpdate = handleMessage;

    if (window.chrome && window.chrome.webview) {
        window.chrome.webview.addEventListener('message', e => {
            // e.data from PostWebMessageAsJson is already parsed in WebView2
            handleMessage(e.data);
        });
    }

    // Subtitle Search Modal
    // ── Subtitle Search Modal & Custom Dropdown ──────────────────────────────
    const ALL_SUB_LANGUAGES = [
        { code: '', label: 'All Languages', badge: 'ALL', popular: true },
        { code: 'en', label: 'English', badge: 'ENG', popular: true },
        { code: 'es', label: 'Spanish', badge: 'ESP', popular: true },
        { code: 'fr', label: 'French', badge: 'FRA', popular: true },
        { code: 'de', label: 'German', badge: 'DEU', popular: true },
        { code: 'pt', label: 'Portuguese', badge: 'POR', popular: true },
        { code: 'it', label: 'Italian', badge: 'ITA', popular: true },
        { code: 'ar', label: 'Arabic', badge: 'ARA', popular: true },
        { code: 'hi', label: 'Hindi', badge: 'HIN', popular: true },
        { code: 'ru', label: 'Russian', badge: 'RUS', popular: true },
        { code: 'ja', label: 'Japanese', badge: 'JPN', popular: true },
        { code: 'ko', label: 'Korean', badge: 'KOR', popular: true },
        { code: 'zh', label: 'Chinese', badge: 'ZHO', popular: true },
        { code: 'tr', label: 'Turkish', badge: 'TUR', popular: true },
        { code: 'nl', label: 'Dutch', badge: 'NLD', popular: true },
        { code: 'pl', label: 'Polish', badge: 'POL', popular: true },
        { code: 'id', label: 'Indonesian', badge: 'IND', popular: true },
        { code: 'vi', label: 'Vietnamese', badge: 'VIE', popular: true },
        { code: 'th', label: 'Thai', badge: 'THA', popular: true },
        { code: 'af', label: 'Afrikaans', badge: 'AFR' },
        { code: 'sq', label: 'Albanian', badge: 'ALB' },
        { code: 'am', label: 'Amharic', badge: 'AMH' },
        { code: 'hy', label: 'Armenian', badge: 'ARM' },
        { code: 'az', label: 'Azerbaijani', badge: 'AZE' },
        { code: 'eu', label: 'Basque', badge: 'BAQ' },
        { code: 'be', label: 'Belarusian', badge: 'BEL' },
        { code: 'bn', label: 'Bengali', badge: 'BEN' },
        { code: 'bs', label: 'Bosnian', badge: 'BOS' },
        { code: 'bg', label: 'Bulgarian', badge: 'BUL' },
        { code: 'my', label: 'Burmese', badge: 'MYA' },
        { code: 'ca', label: 'Catalan', badge: 'CAT' },
        { code: 'hr', label: 'Croatian', badge: 'HRV' },
        { code: 'cs', label: 'Czech', badge: 'CZE' },
        { code: 'da', label: 'Danish', badge: 'DAN' },
        { code: 'et', label: 'Estonian', badge: 'EST' },
        { code: 'tl', label: 'Filipino', badge: 'FIL' },
        { code: 'fi', label: 'Finnish', badge: 'FIN' },
        { code: 'gl', label: 'Galician', badge: 'GLG' },
        { code: 'ka', label: 'Georgian', badge: 'GEO' },
        { code: 'el', label: 'Greek', badge: 'ELL' },
        { code: 'gu', label: 'Gujarati', badge: 'GUJ' },
        { code: 'he', label: 'Hebrew', badge: 'HEB' },
        { code: 'hu', label: 'Hungarian', badge: 'HUN' },
        { code: 'is', label: 'Icelandic', badge: 'ISL' },
        { code: 'kn', label: 'Kannada', badge: 'KAN' },
        { code: 'kk', label: 'Kazakh', badge: 'KAZ' },
        { code: 'km', label: 'Khmer', badge: 'KHM' },
        { code: 'ku', label: 'Kurdish', badge: 'KUR' },
        { code: 'lo', label: 'Lao', badge: 'LAO' },
        { code: 'lv', label: 'Latvian', badge: 'LAV' },
        { code: 'lt', label: 'Lithuanian', badge: 'LIT' },
        { code: 'mk', label: 'Macedonian', badge: 'MKD' },
        { code: 'ms', label: 'Malay', badge: 'MAY' },
        { code: 'ml', label: 'Malayalam', badge: 'MAL' },
        { code: 'mr', label: 'Marathi', badge: 'MAR' },
        { code: 'mn', label: 'Mongolian', badge: 'MON' },
        { code: 'ne', label: 'Nepali', badge: 'NEP' },
        { code: 'no', label: 'Norwegian', badge: 'NOR' },
        { code: 'fa', label: 'Persian', badge: 'PER' },
        { code: 'pa', label: 'Punjabi', badge: 'PAN' },
        { code: 'ro', label: 'Romanian', badge: 'RON' },
        { code: 'sr', label: 'Serbian', badge: 'SRP' },
        { code: 'si', label: 'Sinhala', badge: 'SIN' },
        { code: 'sk', label: 'Slovak', badge: 'SLK' },
        { code: 'sl', label: 'Slovenian', badge: 'SLV' },
        { code: 'so', label: 'Somali', badge: 'SOM' },
        { code: 'sw', label: 'Swahili', badge: 'SWA' },
        { code: 'sv', label: 'Swedish', badge: 'SWE' },
        { code: 'ta', label: 'Tamil', badge: 'TAM' },
        { code: 'te', label: 'Telugu', badge: 'TEL' },
        { code: 'uk', label: 'Ukrainian', badge: 'UKR' },
        { code: 'ur', label: 'Urdu', badge: 'URD' },
        { code: 'uz', label: 'Uzbek', badge: 'UZB' },
        { code: 'yi', label: 'Yiddish', badge: 'YID' },
    ];

    window.toggleCustomLangDropdown = (e) => {
        if (e) e.stopPropagation();
        const popover = document.getElementById('customLangPopover');
        const trigger = document.getElementById('customLangTrigger');
        if (!popover) return;
        const isOpen = popover.style.display === 'flex';
        if (isOpen) {
            popover.style.display = 'none';
        } else {
            popover.style.display = 'flex';
            renderCustomLangOptions('');
            const searchInp = document.getElementById('customLangSearchInput');
            if (searchInp) {
                searchInp.value = '';
                searchInp.focus();
            }
        }
    };

    window.filterCustomLanguages = (query) => {
        renderCustomLangOptions(query.trim().toLowerCase());
    };

    window.selectCustomLanguage = (code, label) => {
        const hiddenInp = document.getElementById('subSearchLang');
        const labelEl = document.getElementById('selectedLangLabel');
        const popover = document.getElementById('customLangPopover');
        if (hiddenInp) hiddenInp.value = code;
        if (labelEl) labelEl.innerText = label;
        if (popover) popover.style.display = 'none';
    };

    const renderCustomLangOptions = (filterText) => {
        const listEl = document.getElementById('customLangOptionsList');
        if (!listEl) return;
        const currentCode = document.getElementById('subSearchLang')?.value || '';

        let filtered = ALL_SUB_LANGUAGES;
        if (filterText) {
            filtered = ALL_SUB_LANGUAGES.filter(l => 
                l.label.toLowerCase().includes(filterText) ||
                l.badge.toLowerCase().includes(filterText) ||
                l.code.toLowerCase().includes(filterText)
            );
        }

        if (filtered.length === 0) {
            listEl.innerHTML = '<div style="color:#777;font-size:12px;text-align:center;padding:12px;">No languages found</div>';
            return;
        }

        listEl.innerHTML = filtered.map(l => {
            const isSelected = l.code === currentCode;
            return `
            <div class="custom-lang-option ${isSelected ? 'selected' : ''}" onclick="selectCustomLanguage('${l.code}', '${l.label}')">
                <span>${l.label}</span>
                <span class="custom-lang-option-badge">${l.badge}</span>
            </div>`;
        }).join('');
    };

    // Close language popover if clicking outside
    document.addEventListener('click', (e) => {
        const wrap = document.getElementById('customLangSelectWrap');
        const popover = document.getElementById('customLangPopover');
        if (popover && popover.style.display === 'flex' && wrap && !wrap.contains(e.target)) {
            popover.style.display = 'none';
        }
    });

    // Subtitle Search Modal
    window.openSubSearchModal = () => {
        const overlay = document.getElementById('subSearchOverlay');
        if (overlay) {
            overlay.style.display = 'flex';
            closeAllPanels();

            // Auto-populate Title if present and not user-edited
            const queryInput = document.getElementById('subSearchQuery');
            const clearBtn = document.getElementById('subSearchClearBtn');
            const seasonInput = document.getElementById('subSearchSeason');
            const episodeInput = document.getElementById('subSearchEpisode');
            const typeBadge = document.getElementById('subSearchTypeBadge');

            if (queryInput && !queryInput._userEdited && currentTitle) {
                // Strip episode parts e.g. "Breaking Bad - S01E01" -> "Breaking Bad"
                let cleanTitle = currentTitle.split(' - ')[0].trim();
                queryInput.value = cleanTitle;
                if (clearBtn) clearBtn.style.display = cleanTitle ? 'flex' : 'none';
            }

            // Populate Season/Episode if active episode exists
            const s = (activeEpInfo && activeEpInfo.season) ? activeEpInfo.season : '';
            const ep = (activeEpInfo && activeEpInfo.episode) ? activeEpInfo.episode : '';
            if (seasonInput) seasonInput.value = s || '';
            if (episodeInput) episodeInput.value = ep || '';

            if (typeBadge) {
                if (s || ep) {
                    typeBadge.innerText = `S${String(s || 1).padStart(2,'0')} E${String(ep || 1).padStart(2,'0')}`;
                    typeBadge.style.color = '#90caf9';
                    typeBadge.style.borderColor = 'rgba(33,150,243,0.3)';
                } else {
                    typeBadge.innerText = 'Movie';
                    typeBadge.style.color = '#aaa';
                    typeBadge.style.borderColor = 'rgba(255,255,255,0.12)';
                }
            }

            queryInput?.focus();
        }
    };

    window.closeSubSearchModal = () => {
        const overlay = document.getElementById('subSearchOverlay');
        if (overlay) overlay.style.display = 'none';
        const popover = document.getElementById('customLangPopover');
        if (popover) popover.style.display = 'none';
    };

    window.showToast = (msg) => {
        if (!msg) return;
        let overlay = document.getElementById('toastOverlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'toastOverlay';
            document.body.appendChild(overlay);
        }

        const lower = msg.toLowerCase();
        let toastType = 'default';
        let iconHtml = '';

        if (lower.startsWith('now playing') || lower.includes('success') || lower.includes('ready')) {
            toastType = 'success';
            iconHtml = '<div class="hud-toast-icon"><svg viewBox="0 0 24 24" width="16" height="16" fill="#10b981"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/></svg></div>';
        } else if (lower.startsWith('switching') || lower.startsWith('reconnecting') || lower.includes('loading') || lower.includes('probing')) {
            toastType = 'info';
            iconHtml = '<div class="hud-toast-icon spin"><svg viewBox="0 0 24 24" width="16" height="16" fill="#38bdf8"><path d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"/></svg></div>';
        } else if (lower.includes('failed') || lower.includes('error') || lower.includes('falling back') || lower.includes('timeout')) {
            toastType = 'warning';
            iconHtml = '<div class="hud-toast-icon"><svg viewBox="0 0 24 24" width="16" height="16" fill="#f59e0b"><path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/></svg></div>';
        }

        const toast = document.createElement('div');
        toast.className = `hud-toast ${toastType}`;
        toast.innerHTML = `${iconHtml}<span>${msg}</span>`;
        overlay.appendChild(toast);

        requestAnimationFrame(() => {
            toast.classList.add('show');
        });

        const displayDuration = toastType === 'warning' ? 4000 : 3000;
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 400);
        }, displayDuration);
    };

    // Subtitle Search Execution
    window.doSubSearch = () => {
        const query   = document.getElementById('subSearchQuery')?.value?.trim() || '';
        const lang    = document.getElementById('subSearchLang')?.value || '';
        const season  = document.getElementById('subSearchSeason')?.value?.trim() || '';
        const episode = document.getElementById('subSearchEpisode')?.value?.trim() || '';
        if (!query) {
            document.getElementById('subSearchQuery')?.focus();
            return;
        }

        const resultsEl  = document.getElementById('subSearchResults');
        const statusEl   = document.getElementById('subSearchStatus');
        const chipsEl    = document.getElementById('subSearchChipsContainer');
        const searchBtn  = document.getElementById('subSearchBtn');
        const btnText    = document.getElementById('subSearchBtnText');
        const spinner    = document.getElementById('subSearchSpinner');

        if (resultsEl) resultsEl.innerHTML = '';
        if (chipsEl) { chipsEl.innerHTML = ''; chipsEl.style.display = 'none'; }
        if (statusEl) {
            statusEl.style.display = 'block';
            statusEl.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;gap:10px;"><div class="subsearch-spinner"></div><span>Searching online providers...</span></div>';
        }

        if (searchBtn) searchBtn.style.pointerEvents = 'none';
        if (btnText) btnText.style.display = 'none';
        if (spinner) spinner.style.display = 'block';

        send('searchSubtitles', JSON.stringify({ query, lang, season, episode }));
    };

    // Render Subtitle Results with Quick Filters
    let _cachedSearchResults = [];
    let _activeFilterLang = 'all';

    const handleSubtitleSearchResults = (p) => {
        const statusEl   = document.getElementById('subSearchStatus');
        const resultsEl  = document.getElementById('subSearchResults');
        const chipsEl    = document.getElementById('subSearchChipsContainer');
        const searchBtn  = document.getElementById('subSearchBtn');
        const btnText    = document.getElementById('subSearchBtnText');
        const spinner    = document.getElementById('subSearchSpinner');

        if (searchBtn) searchBtn.style.pointerEvents = 'auto';
        if (btnText) btnText.style.display = 'block';
        if (spinner) spinner.style.display = 'none';

        if (!resultsEl) return;
        if (statusEl) statusEl.style.display = 'none';

        const results = p.results || [];
        _cachedSearchResults = results;
        _activeFilterLang = 'all';

        if (results.length === 0) {
            resultsEl.innerHTML = `
            <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;padding:36px;color:#777;gap:12px;">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="currentColor" style="opacity:0.6;"><path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S16.33 8 15.5 8 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11zm3.5 3c-2.33 0-4.31 1.46-5.11 3.5h10.22c-.8-2.04-2.78-3.5-5.11-3.5z"/></svg>
                <div style="font-size:14px;font-weight:500;">No subtitles found</div>
                <div style="font-size:12px;color:#666;">Try adjusting the title or switching language filter to "All".</div>
            </div>`;
            return;
        }

        // Build Language Counts for Quick Filter Chips
        const langCounts = {};
        results.forEach(r => {
            const l = (r.langName || r.lang || 'Other');
            langCounts[l] = (langCounts[l] || 0) + 1;
        });

        if (chipsEl && Object.keys(langCounts).length > 1) {
            chipsEl.style.display = 'flex';
            let chipHtml = `<div class="subsearch-quick-chip active" data-lang="all" onclick="filterSubResults('all')">All (${results.length})</div>`;
            for (const [langName, count] of Object.entries(langCounts)) {
                chipHtml += `<div class="subsearch-quick-chip" data-lang="${langName}" onclick="filterSubResults('${langName}')">${langName} (${count})</div>`;
            }
            chipsEl.innerHTML = chipHtml;
        }

        renderFilteredResults();
    };

    window.filterSubResults = (langName) => {
        _activeFilterLang = langName;
        const chips = document.querySelectorAll('.subsearch-quick-chip');
        chips.forEach(c => {
            if (c.dataset.lang === langName) c.classList.add('active');
            else c.classList.remove('active');
        });
        renderFilteredResults();
    };

    const renderFilteredResults = () => {
        const resultsEl = document.getElementById('subSearchResults');
        if (!resultsEl) return;

        let filtered = _cachedSearchResults;
        if (_activeFilterLang !== 'all') {
            filtered = _cachedSearchResults.filter(r => (r.langName || r.lang || 'Other') === _activeFilterLang);
        }

        if (filtered.length === 0) {
            resultsEl.innerHTML = '<div style="color:#888;font-size:13px;text-align:center;padding:24px;">No subtitles for selected filter.</div>';
            return;
        }

        resultsEl.innerHTML = filtered.map((r, i) => {
            const originalIndex = _cachedSearchResults.indexOf(r);
            const badge = (r.langBadge || (r.lang || '??').substring(0, 3)).toUpperCase();
            const name = (r.name || 'Unknown Subtitle');
            const source = r.source || 'Online Provider';
            const epTag = [
                r.seasonNumber ? `S${String(r.seasonNumber).padStart(2,'0')}` : '',
                r.epNumber     ? `E${String(r.epNumber).padStart(2,'0')}` : ''
            ].filter(Boolean).join(' ');

            // Badge Color Class
            let badgeClass = 'other';
            if (badge === 'ENG') badgeClass = 'eng';
            else if (badge === 'ESP' || badge === 'SPA' || badge === 'SPL') badgeClass = 'esp';
            else if (badge === 'FRA' || badge === 'FRE') badgeClass = 'fra';
            else if (badge === 'DEU' || badge === 'GER') badgeClass = 'deu';
            else if (badge === 'ARA') badgeClass = 'ara';
            else if (badge === 'HIN') badgeClass = 'hin';
            else if (badge === 'POR' || badge === 'POB') badgeClass = 'por';

            return `
            <div class="sub-result-item" onclick="downloadSubtitle(event, ${originalIndex})">
                <div class="sub-lang-pill ${badgeClass}">${badge}</div>
                <div style="flex:1;min-width:0;">
                    <div style="display:flex;align-items:center;gap:8px;">
                        <span style="color:#f0f0f0;font-size:14px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${name}</span>
                        ${epTag ? `<span style="color:#90caf9;background:rgba(33,150,243,0.12);border:1px solid rgba(33,150,243,0.25);padding:1px 6px;border-radius:5px;font-size:11px;font-weight:700;white-space:nowrap;">${epTag}</span>` : ''}
                    </div>
                    <div style="color:#777;font-size:12px;margin-top:3px;display:flex;align-items:center;gap:5px;">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2 0 .68.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56zm2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2 0-.68.07-1.35.16-2h4.68c.09.65.16 1.32.16 2 0 .68-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2 0-.68-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z"/></svg>
                        <span>${source}</span>
                    </div>
                </div>
                <button class="sub-download-btn" id="subDlBtn_${originalIndex}" onclick="downloadSubtitle(event, ${originalIndex})" title="Download and Apply Subtitle">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
                </button>
            </div>`;
        }).join('');
    };

    window.downloadSubtitle = (e, index) => {
        if (e) e.stopPropagation();
        const results = _cachedSearchResults;
        if (!results || !results[index]) {
            console.error('[SubSearch] Invalid index:', index);
            return;
        }
        const r = results[index];
        console.log('[SubSearch] Downloading subtitle:', r);
        
        const btn = document.getElementById(`subDlBtn_${index}`) || (e ? e.currentTarget : null);
        if (btn) {
            btn.classList.add('downloading');
            btn.innerHTML = '<div class="subsearch-spinner" style="width:16px;height:16px;border-width:2px;"></div>';
        }

        send('downloadSubtitle', JSON.stringify({
            idPrefix: r.idPrefix || '',
            data: r.data || '',
            name: r.name || '',
            lang: r.lang || '',
            source: r.source || '',
        }));
        
        // Brief success feedback then close modal
        setTimeout(() => {
            if (btn) {
                btn.classList.remove('downloading');
                btn.classList.add('success');
                btn.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/></svg>';
            }
            setTimeout(() => {
                closeSubSearchModal();
            }, 500);
        }, 400);
    };

    // Mark query as user-edited
    document.getElementById('subSearchQuery')?.addEventListener('input', function() {
        this._userEdited = true;
    });

    // Button Listeners
    playPauseBtn.addEventListener('click', e => { 
        e.stopPropagation(); 
        send('togglePlay'); 
        triggerActionFeedback(globalIsPlaying ? SVGS.pause : SVGS.play, 'center');
    });

    // Screen Click/Double-Click Zones logic
    let clickCount = 0;
    let clickTimer = null;
    
    // Hold-to-speed logic
    let holdSpeedTimer = null;
    let isHoldingSpeed = false;
    let wasHoldingSpeed = false;
    let originalSpeed = 1.0;
    const holdSpeedHud = document.getElementById('holdSpeedHud');
    const holdSpeedHudText = document.getElementById('holdSpeedHudText');
    // speedMapping must match the one defined later
    const holdSpeedMapping = [0.25, 0.35, 0.5, 0.65, 0.75, 0.9, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0];

    const restoreHoldSpeed = () => {
        if (!isHoldingSpeed) return;
        isHoldingSpeed = false;
        holdSpeedHud.classList.remove('show');
        send('setMpvProperty', `speed:${originalSpeed}`);
    };

    const handleZoneHoldStart = (zoneName) => {
        if (durationMs <= 0 || !globalIsPlaying) return; // Don't allow holding if paused or live stream
        clearTimeout(holdSpeedTimer);
        holdSpeedTimer = setTimeout(() => {
            isHoldingSpeed = true;
            const slider = document.getElementById('speedSlider');
            originalSpeed = slider ? (holdSpeedMapping[parseInt(slider.value) + 6] || 1.0) : 1.0;
            const newSpeed = zoneName === 'left' ? 0.5 : 2.0;
            send('setMpvProperty', `speed:${newSpeed}`);
            holdSpeedHudText.innerText = zoneName === 'left' ? '0.5x Speed' : '2x Speed';
            holdSpeedHud.classList.add('show');
            clickCount = 0; // Prevent the release from triggering a double-tap seek
        }, 400); // Trigger after 400ms of holding
    };

    const handleZoneHoldEnd = () => {
        clearTimeout(holdSpeedTimer);
        if (isHoldingSpeed) {
            wasHoldingSpeed = true;
            setTimeout(() => { wasHoldingSpeed = false; }, 100);
            restoreHoldSpeed();
        }
    };

    const handleZoneClick = (zoneName, e) => {
        e.stopPropagation();
        if (wasHoldingSpeed) return;
        clickCount++;
        if (clickCount === 1) {
            clickTimer = setTimeout(() => {
                clickCount = 0;
                send('togglePlay');
                triggerActionFeedback(globalIsPlaying ? SVGS.pause : SVGS.play, 'center');
                showControls();
            }, 250);
        } else if (clickCount === 2) {
            clearTimeout(clickTimer);
            clickCount = 0;
            send('toggleFullscreen');
        }
    };

    let isExiting = false;
    function triggerExit() {
        if (isExiting) return;
        isExiting = true;
        closeAllPanels();
        document.getElementById('overlay').style.opacity = '0';
        
        const exitFade = document.createElement('div');
        exitFade.style.position = 'fixed';
        exitFade.style.top = '0'; exitFade.style.bottom = '0'; exitFade.style.left = '0'; exitFade.style.right = '0';
        exitFade.style.backgroundColor = 'black';
        exitFade.style.opacity = '0';
        exitFade.style.pointerEvents = 'none';
        exitFade.style.zIndex = '99999';
        exitFade.style.transition = 'opacity 0.6s ease';
        document.body.appendChild(exitFade);
        
        // Force reflow to start transition
        void exitFade.offsetWidth;
        exitFade.style.opacity = '1';
        
        setTimeout(() => {
            send('exitPlayer');
        }, 600);
    }

    zoneLeft.addEventListener('click', e => handleZoneClick('left', e));
    zoneCenter.addEventListener('click', e => handleZoneClick('center', e));
    zoneRight.addEventListener('click', e => handleZoneClick('right', e));

    zoneLeft.addEventListener('mousedown', () => handleZoneHoldStart('left'));
    zoneRight.addEventListener('mousedown', () => handleZoneHoldStart('right'));
    zoneLeft.addEventListener('touchstart', () => handleZoneHoldStart('left'), {passive: true});
    zoneRight.addEventListener('touchstart', () => handleZoneHoldStart('right'), {passive: true});

    ['mouseup', 'mouseleave', 'touchend', 'touchcancel'].forEach(evt => {
        zoneLeft.addEventListener(evt, handleZoneHoldEnd);
        zoneRight.addEventListener(evt, handleZoneHoldEnd);
    });

    document.getElementById('probingPlayBtn').addEventListener('click', e => { 
        e.stopPropagation(); 
        const btn = e.currentTarget;
        btn.innerHTML = `<svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M12 4V2A10 10 0 0 0 2 12h2a8 8 0 0 1 8-8z"><animateTransform attributeName="transform" type="rotate" from="0 12 12" to="360 12 12" dur="1s" repeatCount="indefinite"/></path></svg> Skipping...`;
        btn.style.opacity = '0.7';
        btn.style.pointerEvents = 'none';
        const pStatus = document.getElementById('linkProbingStatus');
        if (pStatus) pStatus.innerText = 'Skipping discovery • Connecting to best source…';
        send('skipScraping'); 
    });
    document.getElementById('probingCloseBtn').addEventListener('click', e => { e.stopPropagation(); triggerExit(); });

    fullscreenBtn.addEventListener('click', e => { e.stopPropagation(); send('toggleFullscreen'); });
    const pipBtn = document.getElementById('pipBtn');
    if (pipBtn) pipBtn.addEventListener('click', e => { e.stopPropagation(); send('togglePip'); });
    backBtn.addEventListener('click', e => { e.stopPropagation(); triggerExit(); });
    muteBtn.addEventListener('click', e => { e.stopPropagation(); send('toggleMute'); });
    document.getElementById('skipBackwardBtn').addEventListener('click', e => {
        e.stopPropagation();
        doRelativeSeek(-10000);
        triggerActionFeedback(SVGS.rewind10, 'left');
    });
    document.getElementById('skipForwardBtn').addEventListener('click', e => {
        e.stopPropagation();
        doRelativeSeek(10000);
        triggerActionFeedback(SVGS.forward10, 'right');
    });

    const performSkipInterval = (e) => {
        if (e) {
            e.preventDefault();
            e.stopPropagation();
        }
        const r = document.getElementById('resumeOverlay');
        if (r) r.style.display = 'none';
        const sBtn = document.getElementById('skipBtn');
        if (sBtn) {
            sBtn.style.display = 'none';
            sBtn.classList.remove('idle-faded');
            window._skipBtnActiveSince = 0;
        }

        send('skipInterval');
    };

    const skipBtnElem = document.getElementById('skipBtn');
    if (skipBtnElem) {
        skipBtnElem.addEventListener('click', performSkipInterval);
        skipBtnElem.addEventListener('pointerdown', e => e.stopPropagation());
    }

    const triggerNextEpisode = () => {
        if (endCountdownTimer) clearInterval(endCountdownTimer);
        send('hideVideoEnded', '1');
        videoEndedOverlay.style.display = 'none';
        evaluateUIStates();
        document.getElementById('overlay').style.opacity = '';
        forceShowLoading();
        send('loadNextEpisode');
    };
    nextEpBtn.addEventListener('click', e => { e.stopPropagation(); triggerNextEpisode(); });

    episodesBtn.addEventListener('click', e => { e.stopPropagation(); togglePanel('episodesPanel'); });
    chaptersBtn?.addEventListener('click', e => { e.stopPropagation(); togglePanel('chaptersPanel'); });
    document.getElementById('serversBtn').addEventListener('click', e => { e.stopPropagation(); togglePanel('serversPanel'); });
    document.getElementById('subtitlesBtn').addEventListener('click', e => { e.stopPropagation(); togglePanel('subsPanel'); });
    document.getElementById('settingsBtn').addEventListener('click', e => { e.stopPropagation(); togglePanel('settingsPanel'); });
    document.getElementById('qualityBtn').addEventListener('click', e => { e.stopPropagation(); togglePanel('qualityPanel'); });
    document.getElementById('audioBtn').addEventListener('click', e => { e.stopPropagation(); togglePanel('audioPanel'); });
    document.getElementById('speedBtn').addEventListener('click', e => { e.stopPropagation(); togglePanel('speedPanel'); });
    document.getElementById('aspectBtn').addEventListener('click', e => { e.stopPropagation(); togglePanel('aspectPanel'); });

    // Settings Controls
    // Speed
    const speedMapping = [0.25, 0.35, 0.5, 0.65, 0.75, 0.9, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0];
    const updateSpeedUI = (sliderVal) => {
        const idx = parseInt(sliderVal) + 6;
        const val = speedMapping[idx];
        const valStr = Number.isInteger(val) ? val.toFixed(1) : val.toString();
        document.getElementById('speedSliderVal').innerText = valStr + "×";
        document.getElementById('speedBtn').innerText = val === 1.0 ? "1×" : valStr + "×";
        document.getElementById('speedSlider').value = sliderVal;
        send('setMpvProperty', `speed:${val}`);
    };
    document.getElementById('speedSlider').addEventListener('input', e => {
        e.stopPropagation();
        updateSpeedUI(e.target.value);
    });
    document.getElementById('speedDecPanelBtn').addEventListener('click', e => {
        e.stopPropagation();
        let val = parseInt(document.getElementById('speedSlider').value);
        if (val > -6) updateSpeedUI(val - 1);
    });
    document.getElementById('speedIncPanelBtn').addEventListener('click', e => {
        e.stopPropagation();
        let val = parseInt(document.getElementById('speedSlider').value);
        if (val < 6) updateSpeedUI(val + 1);
    });
    document.getElementById('speedSliderVal').addEventListener('click', e => {
        e.stopPropagation();
        updateSpeedUI(0);
    });
    // Sync
    const updateSyncUI = () => {
        document.getElementById('valSubDelay').innerText = `${subDelaySec > 0 ? '+' : ''}${Math.round(subDelaySec * 1000)}ms`;
        document.getElementById('valAudioDelay').innerText = `${audioDelaySec > 0 ? '+' : ''}${Math.round(audioDelaySec * 1000)}ms`;
    };
    document.getElementById('btnSubDelayInc').addEventListener('click', e => { e.stopPropagation(); subDelaySec += 0.1; send('setMpvProperty', `sub-delay:${subDelaySec.toFixed(1)}`); updateSyncUI(); });
    document.getElementById('btnSubDelayDec').addEventListener('click', e => { e.stopPropagation(); subDelaySec -= 0.1; send('setMpvProperty', `sub-delay:${subDelaySec.toFixed(1)}`); updateSyncUI(); });
    // ── Live Subtitle Preview Engine ──────────────────────────────────────────
    window.updateSubtitlePreview = () => {
        const previewText = document.getElementById('subPreviewText');
        const previewBox = document.getElementById('subPreviewBox');
        if (!previewText || !previewBox) return;

        // Font family
        const fontInput = document.getElementById('subFontInput');
        const fontVal = fontInput ? fontInput.value : '';
        previewText.style.fontFamily = fontVal ? `"${fontVal}", system-ui, sans-serif` : 'system-ui, -apple-system, sans-serif';

        // Font Size (scaled for preview card)
        const sizeInput = document.getElementById('subSizeSlider');
        const sizeVal = sizeInput ? parseInt(sizeInput.value, 10) : 45;
        const previewFontSize = Math.max(14, Math.min(28, Math.round(sizeVal * 0.4)));
        previewText.style.fontSize = `${previewFontSize}px`;

        // Text Color
        const activeColorDot = document.querySelector('#subTextColorPalette .desktop-color-dot.active');
        const textColor = activeColorDot ? (activeColorDot.dataset.color || '#FFFFFF') : '#FFFFFF';
        previewText.style.color = textColor;

        // Font Style (Bold / Italic)
        const isBold = document.getElementById('btnSubBold')?.classList.contains('active');
        const isItalic = document.getElementById('btnSubItalic')?.classList.contains('active');
        previewText.style.fontWeight = isBold ? '700' : '500';
        previewText.style.fontStyle = isItalic ? 'italic' : 'normal';

        // Border & Shadow
        const activeBorderDot = document.querySelector('#subBorderColorPalette .desktop-color-dot.active');
        const borderColor = activeBorderDot ? (activeBorderDot.dataset.color || '#000000') : '#000000';
        const borderSizeInput = document.getElementById('subBorderSizeSlider');
        const borderSize = borderSizeInput ? parseInt(borderSizeInput.value, 10) : 3;

        const activeShadowDot = document.querySelector('#subShadowColorPalette .desktop-color-dot.active');
        const shadowColor = activeShadowDot ? (activeShadowDot.dataset.color || '#000000') : '#000000';
        const shadowOffsetInput = document.getElementById('subShadowOffsetSlider');
        const shadowOffset = shadowOffsetInput ? parseInt(shadowOffsetInput.value, 10) : 0;
        const blurInput = document.getElementById('subBlurSlider');
        const blurVal = blurInput ? parseInt(blurInput.value, 10) : 0;

        let shadows = [];
        if (borderSize > 0) {
            const b = Math.max(1, Math.round(borderSize * 0.5));
            shadows.push(`-${b}px -${b}px 0 ${borderColor}`);
            shadows.push(`${b}px -${b}px 0 ${borderColor}`);
            shadows.push(`-${b}px ${b}px 0 ${borderColor}`);
            shadows.push(`${b}px ${b}px 0 ${borderColor}`);
            shadows.push(`0 ${b}px 0 ${borderColor}`);
            shadows.push(`0 -${b}px 0 ${borderColor}`);
            shadows.push(`${b}px 0 0 ${borderColor}`);
            shadows.push(`-${b}px 0 0 ${borderColor}`);
        }
        if (shadowOffset > 0 || blurVal > 0) {
            shadows.push(`${shadowOffset}px ${shadowOffset}px ${blurVal}px ${shadowColor}`);
        }
        previewText.style.textShadow = shadows.length > 0 ? shadows.join(', ') : 'none';

        // Background Style
        const bgInput = document.getElementById('subBgInput');
        const bgVal = bgInput ? bgInput.value : '#00000000';
        if (bgVal === '#FF000000' || bgVal === 'solid' || bgVal === '0.0/0.0/0.0/1.0') {
            previewText.style.backgroundColor = 'rgba(0, 0, 0, 0.95)';
            previewText.style.padding = '4px 10px';
        } else if (bgVal === '#80000000' || bgVal === 'semi-transparent' || bgVal === '0.0/0.0/0.0/0.5') {
            previewText.style.backgroundColor = 'rgba(0, 0, 0, 0.55)';
            previewText.style.padding = '4px 10px';
        } else {
            previewText.style.backgroundColor = 'transparent';
            previewText.style.padding = '0';
        }
    };

    // Color Palettes
    const bindPalette = (containerId, onSelect) => {
        const container = document.getElementById(containerId);
        if (!container) return;
        const dots = container.querySelectorAll('.desktop-color-dot');
        dots.forEach(d => {
            d.addEventListener('click', e => {
                e.stopPropagation();
                dots.forEach(x => x.classList.remove('active'));
                d.classList.add('active');
                onSelect(d.dataset.color);
                window.updateSubtitlePreview();
            });
        });
    };

    bindPalette('subTextColorPalette', color => send('setMpvProperty', `sub-color:${color}`));
    bindPalette('subBorderColorPalette', color => send('setSubtitleBorderColor', color));
    bindPalette('subShadowColorPalette', color => send('setSubtitleShadowColor', color));

    // Subtitle Sliders
    const subSizeSlider = document.getElementById('subSizeSlider');
    const subSizeVal = document.getElementById('subSizeVal');
    if (subSizeSlider && subSizeVal) {
        subSizeSlider.addEventListener('input', e => {
            e.stopPropagation();
            subSizeVal.innerText = `${e.target.value}px`;
            send('setMpvProperty', `sub-font-size:${e.target.value}`);
            window.updateSubtitlePreview();
        });
    }

    const subBorderSizeSlider = document.getElementById('subBorderSizeSlider');
    const subBorderSizeVal = document.getElementById('subBorderSizeVal');
    if (subBorderSizeSlider && subBorderSizeVal) {
        subBorderSizeSlider.addEventListener('input', e => {
            e.stopPropagation();
            subBorderSizeVal.innerText = `${e.target.value}px`;
            send('setSubtitleBorderSize', e.target.value);
            window.updateSubtitlePreview();
        });
    }

    const subShadowOffsetSlider = document.getElementById('subShadowOffsetSlider');
    const subShadowOffsetVal = document.getElementById('subShadowOffsetVal');
    if (subShadowOffsetSlider && subShadowOffsetVal) {
        subShadowOffsetSlider.addEventListener('input', e => {
            e.stopPropagation();
            subShadowOffsetVal.innerText = `${e.target.value}px`;
            send('setSubtitleShadowOffset', e.target.value);
            window.updateSubtitlePreview();
        });
    }

    const subBlurSlider = document.getElementById('subBlurSlider');
    const subBlurVal = document.getElementById('subBlurVal');
    if (subBlurSlider && subBlurVal) {
        subBlurSlider.addEventListener('input', e => {
            e.stopPropagation();
            subBlurVal.innerText = `${e.target.value}px`;
            send('setSubtitleBlur', e.target.value);
            window.updateSubtitlePreview();
        });
    }

    const subPosSlider = document.getElementById('subPosSlider');
    const subPosVal = document.getElementById('subPosVal');
    if (subPosSlider && subPosVal) {
        subPosSlider.addEventListener('input', e => {
            e.stopPropagation();
            subPosVal.innerText = `${e.target.value}%`;
            send('setMpvProperty', `sub-pos:${e.target.value}`);
        });
    }

    // Bold & Italic Pill Buttons
    const btnSubBold = document.getElementById('btnSubBold');
    if (btnSubBold) {
        btnSubBold.addEventListener('click', e => {
            e.stopPropagation();
            const isActive = btnSubBold.classList.toggle('active');
            send('setSubtitleBold', isActive ? 'yes' : 'no');
            window.updateSubtitlePreview();
        });
    }

    const btnSubItalic = document.getElementById('btnSubItalic');
    if (btnSubItalic) {
        btnSubItalic.addEventListener('click', e => {
            e.stopPropagation();
            const isActive = btnSubItalic.classList.toggle('active');
            send('setSubtitleItalic', isActive ? 'yes' : 'no');
            window.updateSubtitlePreview();
        });
    }

    // Mouse-wheel fine-tuning for all desktop sliders
    document.querySelectorAll('.desktop-slider').forEach(slider => {
        slider.addEventListener('wheel', e => {
            e.preventDefault();
            const step = parseFloat(slider.step) || 1;
            const min = parseFloat(slider.min);
            const max = parseFloat(slider.max);
            let current = parseFloat(slider.value);
            if (e.deltaY < 0) {
                current = Math.min(max, current + step);
            } else {
                current = Math.max(min, current - step);
            }
            slider.value = current;
            slider.dispatchEvent(new Event('input'));
        });
    });

    // Subtitle Override toggle
    let subOverrideVisible = false;
    const btnToggleSubOverride = document.getElementById('btnToggleSubOverride');
    if (btnToggleSubOverride) {
        btnToggleSubOverride.addEventListener('click', e => {
            e.stopPropagation();
            subOverrideVisible = !subOverrideVisible;
            if (subOverrideVisible) {
                btnToggleSubOverride.classList.add('active');
            } else {
                btnToggleSubOverride.classList.remove('active');
            }
            send('setSubtitleOverrideEnabled', subOverrideVisible);
            window.updateSubtitlePreview();
        });
    }

    // Reset Subtitles
    const btnResetSubtitles = document.getElementById('btnResetSubtitles');
    if (btnResetSubtitles) {
        btnResetSubtitles.addEventListener('click', e => {
            e.stopPropagation();
            send('resetSubtitleSettings');
            
            if (subSizeSlider && subSizeVal) { subSizeSlider.value = 45; subSizeVal.innerText = '45px'; }
            const subBgInput = document.getElementById('subBgInput');
            if (subBgInput) subBgInput.value = '#00000000';
            if (subBorderSizeSlider && subBorderSizeVal) { subBorderSizeSlider.value = 3; subBorderSizeVal.innerText = '3px'; }
            if (subShadowOffsetSlider && subShadowOffsetVal) { subShadowOffsetSlider.value = 0; subShadowOffsetVal.innerText = '0px'; }
            if (subBlurSlider && subBlurVal) { subBlurSlider.value = 0; subBlurVal.innerText = '0px'; }
            if (subPosSlider && subPosVal) { subPosSlider.value = 100; subPosVal.innerText = '100%'; }
            
            document.querySelectorAll('#subTextColorPalette .desktop-color-dot').forEach(d => d.classList.remove('active'));
            document.querySelector('#subTextColorPalette .desktop-color-dot[data-color="#FFFFFF"]')?.classList.add('active');

            document.querySelectorAll('#subBorderColorPalette .desktop-color-dot').forEach(d => d.classList.remove('active'));
            document.querySelector('#subBorderColorPalette .desktop-color-dot[data-color="#000000"]')?.classList.add('active');
            
            document.querySelectorAll('#subShadowColorPalette .desktop-color-dot').forEach(d => d.classList.remove('active'));
            document.querySelector('#subShadowColorPalette .desktop-color-dot[data-color="#000000"]')?.classList.add('active');

            if (btnSubBold) btnSubBold.classList.remove('active');
            if (btnSubItalic) btnSubItalic.classList.remove('active');
            
            const fontInput = document.getElementById('subFontInput');
            if (fontInput) fontInput.value = '';

            subOverrideVisible = false;
            if (btnToggleSubOverride) btnToggleSubOverride.classList.remove('active');

            window.updateSubtitlePreview();
        });
    }

    // Video Aspect & Zoom
    document.querySelectorAll('#aspectChips .chip').forEach(c => {
        c.addEventListener('click', e => {
            e.stopPropagation();
            document.querySelectorAll('#aspectChips .chip').forEach(x => x.classList.remove('active'));
            c.classList.add('active');
            const aspect = c.dataset.aspect;
            if (aspect === 'original') {
                send('setMpvProperty', 'keepaspect:yes');
                send('setMpvProperty', 'video-aspect-override:no');
            } else if (aspect === '16:9') {
                send('setMpvProperty', 'keepaspect:yes');
                send('setMpvProperty', 'video-aspect-override:16:9');
            } else if (aspect === '4:3') {
                send('setMpvProperty', 'keepaspect:yes');
                send('setMpvProperty', 'video-aspect-override:4:3');
            } else if (aspect === 'stretch') {
                send('setMpvProperty', 'keepaspect:no');
                send('setMpvProperty', 'video-aspect-override:no');
            }
        });
    });
    document.getElementById('zoomSlider').addEventListener('input', e => {
        e.stopPropagation();
        document.getElementById('zoomVal').innerText = e.target.value;
        send('setMpvProperty', `video-zoom:${e.target.value}`);
    });

    document.getElementById('btnResetVideo').addEventListener('click', e => {
        e.stopPropagation();
        // Reset Aspect
        document.querySelectorAll('#aspectChips .chip').forEach(c => c.classList.remove('active'));
        document.querySelector('#aspectChips .chip[data-aspect="original"]').classList.add('active');
        send('setMpvProperty', 'keepaspect:yes');
        send('setMpvProperty', 'video-aspect-override:no');
        
        // Reset Zoom
        document.getElementById('zoomSlider').value = 0;
        document.getElementById('zoomVal').innerText = '0';
        send('setMpvProperty', 'video-zoom:0');
        
        // Reset Scale
        document.querySelectorAll('#scaleChips .chip').forEach(c => c.classList.remove('active'));
        document.querySelector('#scaleChips .chip[data-scale="spline36"]').classList.add('active');
        send('setMpvProperty', 'scale:spline36');
    });

    // Scale filter
    document.querySelectorAll('#scaleChips .chip').forEach(c => {
        c.addEventListener('click', e => {
            e.stopPropagation();
            document.querySelectorAll('#scaleChips .chip').forEach(x => x.classList.remove('active'));
            c.classList.add('active');
            send('setMpvProperty', `scale:${c.dataset.scale}`);
        });
    });

    // Stats for Nerds toggle
    let statsVisible = false;
    const statsOverlay = document.getElementById('statsOverlay');
    const toggleStatsForNerds = (forcedState) => {
        if (typeof forcedState === 'boolean') {
            statsVisible = forcedState;
        } else {
            statsVisible = !statsVisible;
        }
        const btn = document.getElementById('btnToggleStats');
        if (statsVisible) {
            if (btn) btn.classList.add('active');
            if (statsOverlay) statsOverlay.classList.add('show');
        } else {
            if (btn) btn.classList.remove('active');
            if (statsOverlay) statsOverlay.classList.remove('show');
        }
        send('toggleStats');
    };
    window.toggleStatsForNerds = toggleStatsForNerds;

    document.getElementById('btnToggleStats')?.addEventListener('click', e => {
        e.stopPropagation();
        toggleStatsForNerds();
    });

    // Interpolation toggle
    let interpolationEnabled = false;
    const btnToggleInterpolation = document.getElementById('btnToggleInterpolation');
    if (btnToggleInterpolation) {
        btnToggleInterpolation.addEventListener('click', e => {
            e.stopPropagation();
            interpolationEnabled = !interpolationEnabled;
            if (interpolationEnabled) {
                btnToggleInterpolation.classList.add('active');
            } else {
                btnToggleInterpolation.classList.remove('active');
            }
            send('toggleInterpolation', interpolationEnabled.toString());
        });
    }

    // Tabs Navigation (scoped to parent panel)
    function setupTabs(panelId) {
        const panel = document.getElementById(panelId);
        if (!panel) return;
        const btns = panel.querySelectorAll('[data-tab-target]');
        btns.forEach(btn => {
            btn.addEventListener('click', e => {
                e.stopPropagation();
                btns.forEach(b => b.classList.remove('active'));
                panel.querySelectorAll('.settings-tab-content').forEach(c => c.classList.remove('active'));
                btn.classList.add('active');
                const targetId = btn.getAttribute('data-tab-target');
                const content = panel.querySelector('#' + targetId);
                if (content) content.classList.add('active');
            });
        });
    }
    setupTabs('subsPanel');
    setupTabs('settingsPanel');

    // ── Floating HUD Toast ──────────────────────────────────────────
    let hudToastTimer = null;
    window.showHudToast = (text) => {
        const toast = document.getElementById('playerHudToast');
        const textEl = document.getElementById('playerHudText');
        if (!toast || !textEl) return;
        textEl.innerText = text;
        toast.classList.add('visible');
        if (hudToastTimer) clearTimeout(hudToastTimer);
        hudToastTimer = setTimeout(() => {
            toast.classList.remove('visible');
        }, 1600);
    };

    // ── Context Menu Logic ──────────────────────────────────────────
    let isLooping = false;
    let ctxActiveSubmenu = null;
    let ctxSubmenuCloseTimer = null;

    function closeAllSubmenus() {
        if (ctxSubmenuCloseTimer) {
            clearTimeout(ctxSubmenuCloseTimer);
            ctxSubmenuCloseTimer = null;
        }
        document.querySelectorAll('.ctx-submenu').forEach(sub => {
            sub.classList.remove('open');
            sub.style.display = 'none';
        });
        ctxActiveSubmenu = null;
    }

    function populateContextSubmenus() {
        const meta = window.lastMeta || {};
        try {

        // 1. Audio Submenu
        const audioSub = document.getElementById('ctxSubmenuAudio');
        if (audioSub) {
            let html = '<div class="ctx-sub-header">Audio Streams</div>';
            const rendered = new Set();

            if (meta.audioTracks && meta.audioTracks.length > 0) {
                for (const t of meta.audioTracks) {
                    const name = t.name || ('Track ' + t.id);
                    rendered.add(name.toLowerCase().trim());
                    const norm = normalizeAudioName(name);
                    if (norm) rendered.add(norm);

                    html += `
                        <div class="ctx-sub-item ${t.isSelected ? 'active' : ''}" onclick="send('setAudioTrack','${t.id}');closeContextMenu();">
                            <span>${name}</span>
                            <span class="ctx-sub-check">${t.isSelected ? SVGS.check : ''}</span>
                        </div>`;
                }
            }

            if (meta.lazyAudioTracks && meta.lazyAudioTracks.length > 0) {
                for (const t of meta.lazyAudioTracks) {
                    const name = t.name;
                    const norm = normalizeAudioName(name);
                    const isAttached = (meta.activeLazyAudioTrackUrl && meta.activeLazyAudioTrackUrl === t.url);
                    const isRendered = rendered.has(name.toLowerCase().trim()) || (norm && rendered.has(norm));

                    if (!isAttached && !isRendered) {
                        html += `
                            <div class="ctx-sub-item" onclick="send('loadLazyAudioTrack','${t.url}');closeContextMenu();">
                                <span>${name}</span>
                                <span class="ctx-sub-check"></span>
                            </div>`;
                    }
                }
            }

            html += `
                <div class="ctx-divider"></div>
                <div class="ctx-sub-header">Audio Timing</div>
                <div class="ctx-sub-item" onclick="send('setAudioDelay', -0.1);showHudToast('Audio Delay: -100ms');closeContextMenu();">
                    <span>-100 ms</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-sub-item" onclick="send('setAudioDelay', -0.05);showHudToast('Audio Delay: -50ms');closeContextMenu();">
                    <span>-50 ms</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-sub-item" onclick="send('setAudioDelay', 0.0);showHudToast('Audio Delay: Reset');closeContextMenu();">
                    <span>Reset (0s)</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-sub-item" onclick="send('setAudioDelay', 0.05);showHudToast('Audio Delay: +50ms');closeContextMenu();">
                    <span>+50 ms</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-sub-item" onclick="send('setAudioDelay', 0.1);showHudToast('Audio Delay: +100ms');closeContextMenu();">
                    <span>+100 ms</span><span class="ctx-sub-check"></span>
                </div>
            `;

            audioSub.innerHTML = html;
        }

        // 2. Subtitles Submenu
        const subsSub = document.getElementById('ctxSubmenuSubs');
        if (subsSub) {
            let html = '<div class="ctx-sub-header">Subtitles</div>';
            const hasActiveSub = meta.subTracks && meta.subTracks.some(t => t.isSelected);
            const isOff = !hasActiveSub;

            html += `
                <div class="ctx-sub-item ${isOff ? 'active' : ''}" onclick="send('setSubtitleTrack','');closeContextMenu();">
                    <span>Off</span>
                    <span class="ctx-sub-check">${isOff ? SVGS.check : ''}</span>
                </div>`;

            if (meta.subTracks && meta.subTracks.length > 0) {
                for (const t of meta.subTracks) {
                    const name = t.name || ('Track ' + t.id);
                    html += `
                        <div class="ctx-sub-item ${t.isSelected ? 'active' : ''}" onclick="send('setSubtitleTrack','${t.id}');closeContextMenu();">
                            <span>${name}</span>
                            <span class="ctx-sub-check">${t.isSelected ? SVGS.check : ''}</span>
                        </div>`;
                }
            }

            if (meta.lazySubTracks && meta.lazySubTracks.length > 0) {
                const renderedSubNames = new Set(meta.subTracks ? meta.subTracks.map(t => (t.name || '').toLowerCase().trim()) : []);
                for (const t of meta.lazySubTracks) {
                    const name = t.name;
                    if (!renderedSubNames.has(name.toLowerCase().trim())) {
                        html += `
                            <div class="ctx-sub-item" onclick="send('loadLazySubtitleTrack','${t.url}');closeContextMenu();">
                                <span>${name}</span>
                                <span class="ctx-sub-check"></span>
                            </div>`;
                    }
                }
            }

            html += `
                <div class="ctx-divider"></div>
                <div class="ctx-sub-header">Subtitle Timing</div>
                <div class="ctx-sub-item" onclick="send('setSubDelay', -0.1);showHudToast('Sub Delay: -100ms');closeContextMenu();">
                    <span>-100 ms</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-sub-item" onclick="send('setSubDelay', 0.0);showHudToast('Sub Delay: Reset');closeContextMenu();">
                    <span>Reset (0s)</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-sub-item" onclick="send('setSubDelay', 0.1);showHudToast('Sub Delay: +100ms');closeContextMenu();">
                    <span>+100 ms</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-divider"></div>
                <div class="ctx-sub-item" onclick="closeContextMenu();openSubSearchModal();">
                    <span style="color: #64B5F6;">Search Online Subtitles...</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-sub-item" onclick="closeContextMenu();send('openLocalSubtitlePicker','');">
                    <span style="color: #64B5F6;">Load Local Subtitle...</span><span class="ctx-sub-check"></span>
                </div>
            `;

            subsSub.innerHTML = html;
        }

        // 3. Quality & Video Submenu
        const qualSub = document.getElementById('ctxSubmenuQuality');
        if (qualSub) {
            let html = '<div class="ctx-sub-header">Stream Quality</div>';
            let activeRes = meta.resolution ? meta.resolution.split('x')[1] + 'p' : 'Auto';

            if (meta.lazyVideoTracks && meta.lazyVideoTracks.length > 0) {
                const anyMatched = meta.lazyVideoTracks.some(t => isTrackMatchingResolution(t.name, t.url, meta));
                meta.lazyVideoTracks.forEach((t, index) => {
                    const isActive = anyMatched ? isTrackMatchingResolution(t.name, t.url, meta) : (index === 0);
                    html += `
                        <div class="ctx-sub-item ${isActive ? 'active' : ''}" onclick="send('loadLazyVideoTrack','${t.url}');closeContextMenu();">
                            <span>${t.name}</span>
                            <span class="ctx-sub-check">${isActive ? SVGS.check : ''}</span>
                        </div>`;
                });
            } else if (meta.videoTracks && meta.videoTracks.length > 0) {
                const validVideoTracks = meta.videoTracks.filter(t => !/\.(png|jpe?g|webp|bmp|gif)$/i.test(t.name || ''));
                if (validVideoTracks.length > 0) {
                    for (const t of validVideoTracks) {
                        const name = t.name || ('Track ' + t.id);
                        html += `
                            <div class="ctx-sub-item ${t.isSelected ? 'active' : ''}" onclick="send('setVideoTrack','${t.id}');closeContextMenu();">
                                <span>${name}</span>
                                <span class="ctx-sub-check">${t.isSelected ? SVGS.check : ''}</span>
                            </div>`;
                    }
                } else {
                    html += `<div class="ctx-sub-item active"><span>Default (${activeRes})</span><span class="ctx-sub-check">${SVGS.check}</span></div>`;
                }
            } else {
                html += `<div class="ctx-sub-item active"><span>Default (${activeRes})</span><span class="ctx-sub-check">${SVGS.check}</span></div>`;
            }

            html += `
                <div class="ctx-divider"></div>
                <div class="ctx-sub-header">Aspect Ratio</div>
                <div class="ctx-sub-item" onclick="send('setMpvProperty','keepaspect:yes');send('setMpvProperty','video-aspect-override:no');showHudToast('Aspect: Original');closeContextMenu();">
                    <span>Original (Auto)</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-sub-item" onclick="send('setMpvProperty','keepaspect:yes');send('setMpvProperty','video-aspect-override:16:9');showHudToast('Aspect: 16:9');closeContextMenu();">
                    <span>16:9 Widescreen</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-sub-item" onclick="send('setMpvProperty','keepaspect:yes');send('setMpvProperty','video-aspect-override:4:3');showHudToast('Aspect: 4:3');closeContextMenu();">
                    <span>4:3 Standard</span><span class="ctx-sub-check"></span>
                </div>
                <div class="ctx-sub-item" onclick="send('setMpvProperty','keepaspect:no');send('setMpvProperty','video-aspect-override:no');showHudToast('Aspect: Fill Window');closeContextMenu();">
                    <span>Stretch to Fill</span><span class="ctx-sub-check"></span>
                </div>
            `;

            if (meta.shaders && meta.shaders.length > 0) {
                html += `<div class="ctx-divider"></div><div class="ctx-sub-header">Video Shaders</div>`;
                const noShaderActive = !meta.activeShader || meta.activeShader === 'None';
                html += `
                    <div class="ctx-sub-item ${noShaderActive ? 'active' : ''}" onclick="send('selectShader','None');closeContextMenu();">
                        <span>None (Native)</span><span class="ctx-sub-check">${noShaderActive ? SVGS.check : ''}</span>
                    </div>`;
                for (const s of meta.shaders) {
                    const isAct = (s === meta.activeShader);
                    html += `
                        <div class="ctx-sub-item ${isAct ? 'active' : ''}" onclick="send('selectShader','${s}');closeContextMenu();">
                            <span>${s}</span><span class="ctx-sub-check">${isAct ? SVGS.check : ''}</span>
                        </div>`;
                }
            }

            qualSub.innerHTML = html;
        }

        // 4. Chapters Submenu
        const chapItem = document.getElementById('ctxItemChapters');
        const chapSub = document.getElementById('ctxSubmenuChapters');
        if (chapItem && chapSub) {
            if (meta.chapters && meta.chapters.length > 0) {
                chapItem.style.display = 'flex';
                let html = '<div class="ctx-sub-header">Chapters</div>';
                meta.chapters.forEach((ch, idx) => {
                    const isAct = (idx === meta.currentChapterIndex);
                    const time = fmt(ch.timeMs);
                    html += `
                        <div class="ctx-sub-item ${isAct ? 'active' : ''}" onclick="send('seekToChapter',${ch.index});closeContextMenu();">
                            <span>${ch.title} <span style="opacity: 0.5; font-size: 11px;">(${time})</span></span>
                            <span class="ctx-sub-check">${isAct ? SVGS.check : ''}</span>
                        </div>`;
                });
                chapSub.innerHTML = html;
            } else {
                chapItem.style.display = 'none';
            }
        }

        // 5. Playback Speed Submenu
        const speedSub = document.getElementById('ctxSubmenuSpeed');
        if (speedSub) {
            let html = '<div class="ctx-sub-header">Playback Speed</div>';
            const speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
            speeds.forEach(sp => {
                const isAct = Math.abs(currentSpeed - sp) < 0.05;
                html += `
                    <div class="ctx-sub-item ${isAct ? 'active' : ''}" onclick="currentSpeed=${sp};send('setSpeed',${sp});showHudToast('Speed: ${sp}x');closeContextMenu();">
                        <span>${sp === 1.0 ? '1.0x (Normal)' : sp + 'x'}</span>
                        <span class="ctx-sub-check">${isAct ? SVGS.check : ''}</span>
                    </div>`;
            });
            speedSub.innerHTML = html;
        }
        } catch (err) {
            console.error('[ContextMenu] populateContextSubmenus error:', err);
        }
    }

    function setupContextSubmenus() {
        document.querySelectorAll('.ctx-parent').forEach(item => {
            if (item._ctxBound) return;
            item._ctxBound = true;

            item.addEventListener('mouseenter', () => {
                if (ctxSubmenuCloseTimer) {
                    clearTimeout(ctxSubmenuCloseTimer);
                    ctxSubmenuCloseTimer = null;
                }

                const sub = item.querySelector('.ctx-submenu');
                if (!sub) return;

                document.querySelectorAll('.ctx-submenu').forEach(s => {
                    if (s !== sub) {
                        s.classList.remove('open');
                        s.style.display = 'none';
                    }
                });

                sub.style.display = 'block';
                sub.classList.add('open');
                ctxActiveSubmenu = sub;

                // Viewport boundary and quadrant collision
                const zoom = parseFloat(getComputedStyle(document.body).zoom) || 1;
                const itemRect = item.getBoundingClientRect();
                const winW = window.innerWidth / zoom;
                const winH = window.innerHeight / zoom;

                const subW = sub.offsetWidth || 230;
                const subH = sub.offsetHeight || 250;

                // Horizontal inversion: flip left if near right viewport edge
                if ((itemRect.right / zoom) + subW > winW - 12) {
                    sub.style.left = 'auto';
                    sub.style.right = '100%';
                    sub.style.marginRight = '6px';
                    sub.style.marginLeft = '0px';
                } else {
                    sub.style.left = '100%';
                    sub.style.right = 'auto';
                    sub.style.marginLeft = '6px';
                    sub.style.marginRight = '0px';
                }

                // Vertical clamping: if overflowing bottom, shift upward
                const scaledTop = itemRect.top / zoom;
                if (scaledTop + subH > winH - 12) {
                    const overflow = (scaledTop + subH) - (winH - 12);
                    const clampedTop = Math.max(-(scaledTop - 12), -overflow);
                    sub.style.top = `${clampedTop}px`;
                } else {
                    sub.style.top = '0px';
                }
            });

            item.addEventListener('mouseleave', () => {
                const sub = item.querySelector('.ctx-submenu');
                if (!sub) return;
                ctxSubmenuCloseTimer = setTimeout(() => {
                    sub.classList.remove('open');
                    sub.style.display = 'none';
                    if (ctxActiveSubmenu === sub) ctxActiveSubmenu = null;
                }, 120);
            });
        });
    }

    window.showContextMenu = (x, y) => {
        if (!ctxMenu) return;
        closeAllPanels();
        closeAllSubmenus();

        try {
            populateContextSubmenus();
            setupContextSubmenus();
        } catch (err) {
            console.error('[ContextMenu] Preparation error:', err);
        }

        const loopBadge = document.getElementById('ctxLoopBadge');
        if (loopBadge) {
            loopBadge.innerText = isLooping ? 'On' : 'Off';
            loopBadge.style.color = isLooping ? '#9D4EDD' : 'rgba(255, 255, 255, 0.6)';
        }

        updatePauseInfoBadge();

        ctxMenu.style.display = 'block';
        
        const zoom = parseFloat(getComputedStyle(document.body).zoom) || 1;
        const scaledX = x / zoom;
        const scaledY = y / zoom;
        const winW = window.innerWidth / zoom;
        const winH = window.innerHeight / zoom;
        
        const w = ctxMenu.offsetWidth || 230;
        const h = ctxMenu.offsetHeight || 340;

        const posX = (scaledX + w > winW - 12) ? Math.max(12, scaledX - w) : scaledX;
        const posY = (scaledY + h > winH - 12) ? Math.max(12, scaledY - h) : scaledY;

        ctxMenu.style.left = `${posX}px`;
        ctxMenu.style.top = `${posY}px`;
    };

    window.closeContextMenu = () => {
        if (ctxMenu) ctxMenu.style.display = 'none';
        closeAllSubmenus();
    };

    window.toggleLoopFromContext = () => {
        closeContextMenu();
        isLooping = !isLooping;
        send('toggleLoop', isLooping);
        showHudToast(isLooping ? 'Loop: Enabled' : 'Loop: Disabled');
    };

    window.openPauseInfoModal = () => {
        closeContextMenu();
        const modal = document.getElementById('pauseInfoModalOverlay');
        if (!modal) return;
        // Update selected class on options
        document.querySelectorAll('.pause-modal-option[data-mode]').forEach(el => {
            if (el.getAttribute('data-mode') === pauseInfoMode) {
                el.classList.add('selected');
            } else {
                el.classList.remove('selected');
            }
        });
        const castOpt = document.getElementById('pauseModalOptionCast');
        if (castOpt) {
            if (showPauseCast) castOpt.classList.add('checked');
            else castOpt.classList.remove('checked');
        }
        modal.style.display = 'flex';
    };

    window.closePauseInfoModal = (e) => {
        if (e && e.target !== e.currentTarget && !e.target.classList.contains('shortcuts-close-btn')) return;
        const modal = document.getElementById('pauseInfoModalOverlay');
        if (modal) modal.style.display = 'none';
    };

    window.togglePauseShowCast = (e) => {
        if (e) e.stopPropagation();
        showPauseCast = !showPauseCast;
        send('set_pause_show_cast', showPauseCast);
        const castOpt = document.getElementById('pauseModalOptionCast');
        if (castOpt) {
            if (showPauseCast) castOpt.classList.add('checked');
            else castOpt.classList.remove('checked');
        }
        showHudToast(showPauseCast ? 'Starring Cast: Enabled' : 'Starring Cast: Disabled');
        const pauseCast = document.getElementById('pauseInfoCast');
        if (showPauseCast && currentCastList && currentCastList.length > 0 && !globalIsPlaying && pauseInfoMode !== 'off') {
            if (pauseCast) pauseCast.classList.add('visible');
        } else {
            if (pauseCast) pauseCast.classList.remove('visible');
        }
    };

    window.selectPauseInfoMode = (mode) => {
        pauseInfoMode = mode;
        updatePauseInfoBadge();
        send('set_pause_info_mode', mode);
        const modal = document.getElementById('pauseInfoModalOverlay');
        if (modal) modal.style.display = 'none';
        const labelMap = {
            'delay_5s': 'Pause Info: 5s Delay',
            'delay_10s': 'Pause Info: 10s Delay',
            'delay_20s': 'Pause Info: 20s Delay',
            'immediate': 'Pause Info: Immediate',
            'off': 'Pause Info: Disabled'
        };
        showHudToast(labelMap[mode] || 'Pause Info Updated');
        hidePauseInfoOverlay();
        if (!globalIsPlaying) {
            schedulePauseInfoOverlay();
        }
    };

    window.togglePipFromContext = () => {
        closeContextMenu();
        send('togglePip');
    };

    window.takeScreenshotFromContext = () => {
        closeContextMenu();
        send('screenshot');
        showHudToast('Screenshot Saved');
    };

    window.copyCurrentTimecode = () => {
        closeContextMenu();
        const timeStr = fmt(currentPosMs);
        if (navigator.clipboard) {
            navigator.clipboard.writeText(timeStr);
            showHudToast(`Copied timecode: ${timeStr}`);
        }
    };

    window.copyStreamUrlFromContext = () => {
        closeContextMenu();
        const activeLink = linksData.find(l => l.isActive);
        const url = activeLink?.url || (window.currentStreamUrl || '');
        if (url && navigator.clipboard) {
            navigator.clipboard.writeText(url);
            showHudToast('Copied stream URL');
        } else {
            showHudToast('No active stream URL');
        }
    };

    // Context Menu Event Listeners
    document.addEventListener('contextmenu', e => {
        if (e.target.closest('input,textarea,select')) return;
        e.preventDefault();
        showContextMenu(e.clientX, e.clientY);
    });

    // Stop mousedown and wheel events inside the menu from bubbling up
    if (ctxMenu) {
        ctxMenu.addEventListener('mousedown', e => e.stopPropagation());
        ctxMenu.addEventListener('wheel', e => e.stopPropagation(), { passive: true });
    }

    // Dismiss when clicking anywhere outside the menu
    document.addEventListener('mousedown', e => {
        if (ctxMenu && ctxMenu.style.display === 'block' && !ctxMenu.contains(e.target)) {
            closeContextMenu();
        }
    });

    // Keyboard Shortcuts
    // Prevent UI zooming
    document.addEventListener('wheel', e => { if (e.ctrlKey) e.preventDefault(); }, { passive: false });

    document.addEventListener('keydown', e => {
        if (e.ctrlKey && (e.key === '=' || e.key === '-' || e.key === '0')) { e.preventDefault(); return; }
        if (e.target.closest('input[type="text"], input[type="search"], textarea, [contenteditable]')) return;
        if (document.activeElement && document.activeElement instanceof HTMLElement && document.activeElement !== document.body) {
            document.activeElement.blur();
        }

        // Dismiss context menu first on any keypress
        if (ctxMenu && ctxMenu.style.display === 'block') {
            closeContextMenu();
            if (e.key === 'Escape') return;
        }

        const shortcutsModal = document.getElementById('shortcutsModalOverlay');
        if (shortcutsModal && shortcutsModal.style.display === 'flex') {
            if (e.key === 'Escape') { closeShortcutsModal(); }
            return;
        }

        const pauseModal = document.getElementById('pauseInfoModalOverlay');
        if (pauseModal && pauseModal.style.display === 'flex') {
            if (e.key === 'Escape') { closePauseInfoModal(); }
            return;
        }

        // Percentage seek (0-9)
        if (!e.ctrlKey && !e.altKey && !e.metaKey && e.key >= '0' && e.key <= '9') {
            const pct = parseInt(e.key) * 0.1;
            if (durationMs > 0) {
                const targetMs = durationMs * pct;
                send('seekTo', targetMs);
                showHudToast(`Seek: ${Math.round(pct * 100)}% (${fmt(targetMs)})`);
                if (document.body.classList.contains('hidden-controls')) {
                    triggerKeyboardSeekingHud();
                }
            }
            return;
        }

        switch (e.code) {
            case 'Space': case 'KeyK':
                e.preventDefault();
                send('togglePlay');
                triggerActionFeedback(globalIsPlaying ? SVGS.pause : SVGS.play, 'center');
                break;
            case 'KeyS':
                if (e.shiftKey || e.ctrlKey) {
                    e.preventDefault();
                    send('screenshot');
                } else {
                    const sBtn = document.getElementById('skipBtn');
                    if (sBtn && sBtn.style.display !== 'none') {
                        e.preventDefault();
                        performSkipInterval(e);
                    }
                }
                break;
            case 'KeyF':
                send('toggleFullscreen');
                break;
            case 'KeyM':
                e.preventDefault();
                isMuted = !isMuted;
                send('toggleMute');
                updateMuteIcon();
                showVolumeOsd(currentVolume, isMuted);
                break;
            case 'KeyN':
                e.preventDefault();
                triggerNextEpisode();
                break;
            case 'KeyD':
            case 'KeyI':
                if (e.shiftKey) { e.preventDefault(); toggleStatsForNerds(); }
                break;
            case 'ArrowLeft':
                e.preventDefault();
                if (e.shiftKey) { doRelativeSeek(-2000); }
                else { doRelativeSeek(-10000); }
                break;
            case 'ArrowRight':
                e.preventDefault();
                if (e.ctrlKey) { doRelativeSeek(85000); }
                else if (e.shiftKey) { doRelativeSeek(2000); }
                else { doRelativeSeek(10000); }
                break;
            case 'ArrowUp':
                e.preventDefault();
                currentVolume = Math.min(100, (currentVolume || 0) + 5);
                volumeBar.value = currentVolume;
                updateVolumeTrack(currentVolume);
                send('setVolume', currentVolume);
                showVolumeOsd(currentVolume);
                break;
            case 'ArrowDown':
                e.preventDefault();
                currentVolume = Math.max(0, (currentVolume || 0) - 5);
                volumeBar.value = currentVolume;
                updateVolumeTrack(currentVolume);
                send('setVolume', currentVolume);
                showVolumeOsd(currentVolume);
                break;
            case 'PageUp':
                if (_cachedChapters && _cachedChapters.length > 0) {
                    e.preventDefault();
                    send('previousChapter');
                }
                break;
            case 'PageDown':
                if (_cachedChapters && _cachedChapters.length > 0) {
                    e.preventDefault();
                    send('nextChapter');
                }
                break;
            case 'Equal': case 'NumpadAdd': case 'BracketRight':
                currentSpeed = Math.min(3.0, Math.round((currentSpeed + 0.25) * 100) / 100);
                send('setSpeed', currentSpeed);
                showHudToast(`Speed: ${currentSpeed}x`);
                break;
            case 'Minus': case 'NumpadSubtract': case 'BracketLeft':
                currentSpeed = Math.max(0.25, Math.round((currentSpeed - 0.25) * 100) / 100);
                send('setSpeed', currentSpeed);
                showHudToast(`Speed: ${currentSpeed}x`);
                break;
            case 'Backspace':
                currentSpeed = 1.0;
                send('setSpeed', 1.0);
                showHudToast('Speed: 1.0x (Normal)');
                break;
            case 'KeyZ':
                subDelaySec = Math.round((subDelaySec - 0.1) * 10) / 10;
                send('setSubDelay', -0.1);
                showHudToast(`Sub Delay: ${subDelaySec > 0 ? '+' : ''}${Math.round(subDelaySec * 1000)}ms`);
                break;
            case 'KeyX':
                subDelaySec = Math.round((subDelaySec + 0.1) * 10) / 10;
                send('setSubDelay', 0.1);
                showHudToast(`Sub Delay: ${subDelaySec > 0 ? '+' : ''}${Math.round(subDelaySec * 1000)}ms`);
                break;
            case 'KeyC':
                send('cycleSubtitles');
                showHudToast('Cycled Subtitle Track');
                break;
            case 'KeyV':
                send('toggleSubVisibility');
                showHudToast('Toggled Subtitles');
                break;
            case 'Slash': case 'F1': case 'KeyH':
                if (e.key === '?' || e.code === 'F1' || e.code === 'KeyH') {
                    openShortcutsModal();
                }
                break;
            case 'Escape':
                closeContextMenu();
                closeShortcutsModal();
                closeAllPanels();
                break;
        }
    });

    const btnToggleAutoPlay = document.getElementById('btnToggleAutoPlay');
    if (btnToggleAutoPlay) {
        btnToggleAutoPlay.addEventListener('click', () => {
            window.autoPlayEnabled = !window.autoPlayEnabled;
            if (window.autoPlayEnabled) {
                btnToggleAutoPlay.classList.add('active');
            } else {
                btnToggleAutoPlay.classList.remove('active');
            }
            send('toggleAutoPlay', String(window.autoPlayEnabled));
        });
    }

    const btnToggleEndTime = document.getElementById('btnToggleEndTime');
    if (btnToggleEndTime) {
        btnToggleEndTime.addEventListener('click', () => {
            const isActive = btnToggleEndTime.classList.toggle('active');
            send('setPrefShowEndTime', String(isActive));
            if (typeof window.updateClockDisplay === 'function') window.updateClockDisplay();
        });
    }

    const btnToggleClock = document.getElementById('btnToggleClock');
    if (btnToggleClock) {
        btnToggleClock.addEventListener('click', () => {
            const isActive = btnToggleClock.classList.toggle('active');
            send('setPrefShowClock', String(isActive));
            if (typeof window.updateClockDisplay === 'function') window.updateClockDisplay();
        });
    }

    const btnToggleServerQuality = document.getElementById('btnToggleServerQuality');
    if (btnToggleServerQuality) {
        btnToggleServerQuality.addEventListener('click', () => {
            const isActive = btnToggleServerQuality.classList.toggle('active');
            send('setPrefShowServerQuality', String(isActive));
            if (typeof window.updateClockDisplay === 'function') window.updateClockDisplay();
        });
    }

    const btnToggleAudioNorm = document.getElementById('btnToggleAudioNorm');
    if (btnToggleAudioNorm) {
        let isAudioNormOn = false;
        btnToggleAudioNorm.addEventListener('click', () => {
            isAudioNormOn = btnToggleAudioNorm.classList.toggle('active');
            send('setAudioNormalization', String(isAudioNormOn));
        });
    }

    const audioNormChips = document.querySelectorAll('#audioNormChips .chip');
    audioNormChips.forEach(chip => {
        chip.addEventListener('click', (e) => {
            e.stopPropagation();
            audioNormChips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            send('setAudioNormStrength', chip.getAttribute('data-val'));
        });
    });

    const audioEqChips = document.querySelectorAll('#audioEqChips .chip');
    audioEqChips.forEach(chip => {
        chip.addEventListener('click', (e) => {
            e.stopPropagation();
            audioEqChips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            send('setAudioEqPreset', chip.getAttribute('data-val'));
        });
    });

    const btnToggleAudioSpatial = document.getElementById('btnToggleAudioSpatial');
    if (btnToggleAudioSpatial) {
        let isAudioSpatialOn = false;
        btnToggleAudioSpatial.addEventListener('click', () => {
            isAudioSpatialOn = btnToggleAudioSpatial.classList.toggle('active');
            send('setAudioSpatial', String(isAudioSpatialOn));
        });
    }

    const audioDelaySlider = document.getElementById('audioDelaySlider');
    const audioDelayVal = document.getElementById('audioDelayVal');
    if (audioDelaySlider && audioDelayVal) {
        audioDelaySlider.addEventListener('input', (e) => {
            e.stopPropagation();
            const val = parseFloat(audioDelaySlider.value).toFixed(2);
            audioDelayVal.innerText = val + 's';
        });
        audioDelaySlider.addEventListener('change', (e) => {
            e.stopPropagation();
            send('setAudioDelay', audioDelaySlider.value);
        });
    }
    const btnToggleVolumeMax = document.getElementById('btnToggleVolumeMax');
    if (btnToggleVolumeMax) {
        let isVolumeMaxOn = false;
        btnToggleVolumeMax.addEventListener('click', () => {
            isVolumeMaxOn = btnToggleVolumeMax.classList.toggle('active');
            send('setAudioVolumeMax', String(isVolumeMaxOn));
        });
    }

    // Video Ended Overlay Logic
    const videoEndedSubtext = document.getElementById('videoEndedSubtext');
    const btnNextEpisode = document.getElementById('btnNextEpisode');
    const btnReplay = document.getElementById('btnReplay');
    const btnExitPlayer = document.getElementById('btnExitPlayer');
    const btnDismissEnded = document.getElementById('btnDismissEnded');
    let currentEndCountdown = 5;

    window.showVideoEnded = (hasNextEpisode, autoPlayEnabled) => {
        closeAllPanels();
        document.getElementById('overlay').style.opacity = '0';
        videoEndedOverlay.style.display = 'flex';
        evaluateUIStates();
        
        if (endCountdownTimer) {
            clearInterval(endCountdownTimer);
            endCountdownTimer = null;
        }
        
        const videoEndedNextCard = document.getElementById('videoEndedNextCard');
        const videoEndedThumb = document.getElementById('videoEndedThumb');
        const videoEndedNextEp = document.getElementById('videoEndedNextEp');
        const videoEndedNextTitle = document.getElementById('videoEndedNextTitle');
        const videoEndedNextDesc = document.getElementById('videoEndedNextDesc');
        const btnNextEpisodeLabel = document.getElementById('btnNextEpisodeLabel');
        const videoEndedHeaderTitle = document.getElementById('videoEndedHeaderTitle');

        if (hasNextEpisode) {
            btnNextEpisode.style.display = 'flex';
            const activeIdx = (episodesData || []).findIndex(e => e.isActive);
            const nextEp = (activeIdx !== -1 && activeIdx < (episodesData || []).length - 1) ? episodesData[activeIdx + 1] : null;

            if (nextEp) {
                if (videoEndedNextCard) videoEndedNextCard.style.display = 'flex';
                if (videoEndedThumb) {
                    const backdropEl = document.getElementById('linkProbingBackdrop') || document.getElementById('pauseBackdrop');
                    const fallbackSrc = backdropEl ? (backdropEl.src || '') : '';
                    videoEndedThumb.onerror = function() {
                        if (fallbackSrc && this.src !== fallbackSrc) {
                            this.src = fallbackSrc;
                        } else {
                            this.style.display = 'none';
                        }
                    };
                    videoEndedThumb.style.display = 'block';
                    videoEndedThumb.src = nextEp.posterUrl || fallbackSrc || '';
                }
                if (videoEndedNextEp) {
                    videoEndedNextEp.innerText = (nextEp.season !== undefined && nextEp.season !== null)
                        ? `S${nextEp.season}:E${nextEp.episode}`
                        : `Episode ${nextEp.episode}`;
                }
                if (videoEndedNextTitle) videoEndedNextTitle.innerText = nextEp.title || ('Episode ' + nextEp.episode);
                if (videoEndedNextDesc) videoEndedNextDesc.innerText = (nextEp.description || '').replace(/\|\|DATE:.*?\|\|/g, '').trim();
            } else {
                if (videoEndedNextCard) videoEndedNextCard.style.display = 'none';
            }
            
            const ringSvg = btnNextEpisode.querySelector('.video-ended-ring-svg');
            const ringFill = btnNextEpisode.querySelector('.ring-fill');

            if (autoPlayEnabled) {
                currentEndCountdown = 5;
                if (btnNextEpisodeLabel) btnNextEpisodeLabel.innerText = 'Play Next Episode';
                if (videoEndedSubtext) videoEndedSubtext.innerText = `Playing automatically in ${currentEndCountdown}s`;
                if (ringSvg) ringSvg.style.display = 'block';
                if (ringFill) {
                    ringFill.style.animation = 'none';
                    ringFill.offsetHeight; /* trigger reflow */
                    ringFill.style.animation = 'endedRingAnim 5s linear forwards';
                }
                
                endCountdownTimer = setInterval(() => {
                    currentEndCountdown--;
                    if (currentEndCountdown <= 0) {
                        clearInterval(endCountdownTimer);
                        endCountdownTimer = null;
                        triggerNextEpisode();
                    } else {
                        if (videoEndedSubtext) videoEndedSubtext.innerText = `Playing automatically in ${currentEndCountdown}s`;
                    }
                }, 1000);
            } else {
                if (btnNextEpisodeLabel) btnNextEpisodeLabel.innerText = 'Play Next Episode';
                if (videoEndedSubtext) videoEndedSubtext.innerText = 'Autoplay is off';
                if (ringSvg) ringSvg.style.display = 'none';
            }
            
            btnNextEpisode.onclick = () => {
                if (endCountdownTimer) {
                    clearInterval(endCountdownTimer);
                    endCountdownTimer = null;
                }
                triggerNextEpisode();
            };
            if (videoEndedNextCard) {
                videoEndedNextCard.onclick = () => {
                    if (endCountdownTimer) {
                        clearInterval(endCountdownTimer);
                        endCountdownTimer = null;
                    }
                    triggerNextEpisode();
                };
            }
        } else {
            btnNextEpisode.style.display = 'none';
            if (videoEndedNextCard) videoEndedNextCard.style.display = 'none';
            if (videoEndedHeaderTitle) videoEndedHeaderTitle.innerText = 'COMPLETED';
            if (videoEndedSubtext) videoEndedSubtext.innerText = 'All episodes watched';
        }

        if (btnDismissEnded) {
            btnDismissEnded.onclick = () => {
                if (endCountdownTimer) {
                    clearInterval(endCountdownTimer);
                    endCountdownTimer = null;
                }
                send('hideVideoEnded', '1');
                videoEndedOverlay.style.display = 'none';
                evaluateUIStates();
                document.getElementById('overlay').style.opacity = '';
            };
        }
        
        btnReplay.onclick = () => {
            if (endCountdownTimer) {
                clearInterval(endCountdownTimer);
                endCountdownTimer = null;
            }
            send('hideVideoEnded', '1');
            videoEndedOverlay.style.display = 'none';
            evaluateUIStates();
            document.getElementById('overlay').style.opacity = '';
            forceShowLoading();
            send('replayEpisode');
        };
        
        btnExitPlayer.onclick = () => {
            if (endCountdownTimer) {
                clearInterval(endCountdownTimer);
                endCountdownTimer = null;
            }
            triggerExit();
        };
    };

    // Close overlays when clicking outside
    videoEndedOverlay.addEventListener('click', e => {
        if (e.target === videoEndedOverlay) {
            // Keep it open
        }
    });


    // ── PiP Mode UI Logic ────────────────────────────────────────────────
    const pipPlayIcon = document.getElementById('pipPlayIcon');
    const pipPauseIcon = document.getElementById('pipPauseIcon');
    const updatePipPlayPauseIcon = () => {
        if (pipPlayIcon && pipPauseIcon) {
            if (globalIsPlaying) {
                pipPlayIcon.style.display = 'none';
                pipPauseIcon.style.display = 'block';
            } else {
                pipPlayIcon.style.display = 'block';
                pipPauseIcon.style.display = 'none';
            }
        }
    };
    window.updatePipPlayPauseIcon = updatePipPlayPauseIcon;

    window.setPipUi = (active) => {
        if (active) {
            document.body.classList.add('is-pip');
            updatePipPlayPauseIcon();
        } else {
            document.body.classList.remove('is-pip');
        }
    };

    // PiP overlay button listeners
    const pipPlayPauseBtn = document.getElementById('pipPlayPauseBtn');
    const pipRestoreBtn = document.getElementById('pipRestoreBtn');
    if (pipPlayPauseBtn) {
        pipPlayPauseBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            send('togglePlay');
        });
    }
    if (pipRestoreBtn) {
        pipRestoreBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            send('togglePip'); // Restores normal mode
        });
    }

    const pipRewindBtn = document.getElementById('pipRewindBtn');
    const pipForwardBtn = document.getElementById('pipForwardBtn');
    if (pipRewindBtn) {
        pipRewindBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            doRelativeSeek(-10000);
            triggerActionFeedback(SVGS.rewind10, 'left');
        });
    }
    if (pipForwardBtn) {
        pipForwardBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            doRelativeSeek(10000);
            triggerActionFeedback(SVGS.forward10, 'right');
        });
    }

    // ── Window Dragging via IPC ──────────────────────────────────────────
    const pipOverlay = document.getElementById('pipOverlay');
    if (pipOverlay) {
        pipOverlay.addEventListener('mousedown', (e) => {
            // Do not start drag if clicking on a button or an edge
            if (e.target.closest('button')) return;
            if (e.target.closest('.pip-resize-edge')) return;
            // Tell the backend to natively start dragging the window
            send('startWindowDrag');
        });
    }

    document.querySelectorAll('.pip-resize-edge').forEach(edge => {
        edge.addEventListener('mousedown', (e) => {
            e.stopPropagation();
            send('startWindowResize', edge.getAttribute('data-edge'));
        });
    });

    // Ready
    const notifyReady = () => { send('ui_ready'); };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', notifyReady);
    else notifyReady();
