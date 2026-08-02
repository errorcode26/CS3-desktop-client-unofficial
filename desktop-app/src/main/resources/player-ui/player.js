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

    // State
    let isSeeking = false, durationMs = 0, currentPosMs = 0;
    let isMuted = false, currentVolume = 100;
    let isMenuOpen = false;
    let subDelaySec = 0, audioDelaySec = 0;
    let currentTitle = '', currentEpisodeId = '', resumeHandled = false, userDismissedProbing = false, pendingResumeMs = 0;
    let isAppLoading = false; // Moved up to global scope
    let linksData = [];

    // ── Hard-reset every overlay/timer atomically when a new playback session begins.
    // This is the single source of truth that kills race conditions on re-entry.
    function hardResetAllOverlays() {
        // Kill all pending timers that could fire from a stale session
        if (window.resumeDismissTimer) { clearTimeout(window.resumeDismissTimer); window.resumeDismissTimer = null; }
        if (window.probingDismissTimer) { clearTimeout(window.probingDismissTimer); window.probingDismissTimer = null; }
        clearTimeout(loadingTimer);
        loadingTimer = null;

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
        if (pOverlay)  { pOverlay.classList.remove('dismissing'); }
        if (pContent)  { pContent.classList.remove('dismissing'); }

        const videoEndedOvl = document.getElementById('videoEndedOverlay');
        if (videoEndedOvl) videoEndedOvl.style.display = 'none';
        if (endCountdownTimer) { clearInterval(endCountdownTimer); endCountdownTimer = null; }

        resumeOverlay.style.display = 'none';
        loadingContainer.classList.remove('show');

        const pauseOverlay = document.getElementById('pauseInfoOverlay');
        const pauseBackdrop = document.getElementById('pauseBackdrop');
        if (pauseOverlay) { pauseOverlay.classList.remove('visible'); }
        if (pauseBackdrop) { pauseBackdrop.classList.remove('visible'); }

        // Ensure the main player UI base state is restored internally (visibility handled by evaluateUIStates)
        const mainOverlay = document.getElementById('overlay');
        if (mainOverlay) { mainOverlay.style.display = ''; mainOverlay.style.opacity = ''; }

        seekFill.style.width = '0%';
        seekBar.value = 0;
        seekBuffer.style.width = '0%';
        timeDisplay.innerText = '0:00 / 0:00';
    }

    function evaluateResumeOverlay() {
        const pOverlay = document.getElementById('linkProbingOverlay');
        const isProbing = pOverlay && !pOverlay.classList.contains('dismissing');

        const shouldShow = pendingResumeMs > 0 && !resumeHandled && !isProbing && !isAppLoading;

        if (shouldShow) {
            document.getElementById('resumeTime').innerText = fmt(pendingResumeMs);
            // Move the resume bar inside the bottom-bar if not already there
            const bottomBar = document.getElementById('bottomBar');
            if (resumeOverlay.parentElement !== bottomBar) {
                bottomBar.insertBefore(resumeOverlay, bottomBar.firstChild);
            }
            if (resumeOverlay.style.display !== 'flex') {
                resumeOverlay.style.display = 'flex';
                // Re-trigger countdown animation by forcing reflow
                void resumeOverlay.offsetWidth;
            }
            document.body.classList.remove('hidden-controls');
            if (!window.resumeDismissTimer) {
                window.resumeDismissTimer = setTimeout(() => {
                    // Auto-dismiss: user ignored it, do nothing (default behaviour is continue)
                    resumeOverlay.style.display = 'none';
                    resumeHandled = true;
                    window.resumeDismissTimer = null;
                    send('play'); // Auto-unpause the player to actually continue!
                }, 10000);
            }
        } else {
            resumeOverlay.style.display = 'none';
        }
    }

    function evaluateUIStates() {
        const pOverlay = document.getElementById('linkProbingOverlay');
        const isProbing = pOverlay && !pOverlay.classList.contains('dismissing');
        const videoEndedOvl = document.getElementById('videoEndedOverlay');
        const isVideoEnded = videoEndedOvl && videoEndedOvl.style.display === 'flex';
        
        // Master visibility control for the main player UI
        const mainOverlay = document.getElementById('overlay');
        if (mainOverlay) {
            if (isProbing || isAppLoading) {
                // Hide controls during probing or loading to prevent bleeding/flashing.
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
        
        // 1. Pause Info Overlay
        const pauseOverlay = document.getElementById('pauseInfoOverlay');
        const pauseBackdrop = document.getElementById('pauseBackdrop');
        if (pauseOverlay) {
            if (globalIsPlaying || isProbing || isAppLoading || isVideoEnded) {
                pauseOverlay.classList.remove('visible');
                pauseBackdrop.classList.remove('visible');
            } else {
                pauseOverlay.classList.add('visible');
                pauseBackdrop.classList.add('visible');
            }
        }
        
        // 2. Loading Container (Spinner)
        const isNativeBuffering = globalIsLoading || isSeeking;
        // Do not show central spinner if probing (probing has its own)
        const shouldBeLoading = (isNativeBuffering || isAppLoading) && !isProbing && !isVideoEnded;
        
        const getCleanLoadingText = () => {
            if (isAppLoading) return 'Loading source...';
            if (isSeeking) return 'Seeking...';
            return 'Buffering...';
        };

        if (shouldBeLoading !== isCurrentlyLoading) {
            isCurrentlyLoading = shouldBeLoading;
            clearTimeout(loadingTimer);
            if (shouldBeLoading) {
                const delay = isSeeking ? 0 : 150;
                loadingTimer = setTimeout(() => {
                    loadingContainer.classList.add('show');
                    loadingStatus.innerText = getCleanLoadingText();
                }, delay);
            } else {
                loadingContainer.classList.remove('show');
                loadingStatus.innerText = '';
            }
        } else if (shouldBeLoading) {
             loadingStatus.innerText = getCleanLoadingText();
        }
    }

    let currentLinkIndex = -1;
    let seekLockTimer = null;
    let episodesData = [];

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
    
    // Toggle HW/SW Decoding
    let isHwDec = true;
    const hwToggleBtn = document.getElementById('hwToggleBtn');
    if (hwToggleBtn) {
        hwToggleBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            isHwDec = !isHwDec;
            hwToggleBtn.innerText = isHwDec ? 'HW' : 'SW';
            send('setMpvProperty', `hwdec:${isHwDec ? 'auto' : 'no'}`);
        });
    }
    
    const titleDisplay  = document.getElementById('titleDisplay');
    const nextEpBtn     = document.getElementById('nextEpBtn');
    const episodesBtn   = document.getElementById('episodesBtn');
    const episodesPanel = document.getElementById('episodesPanel');
    const resumeOverlay = document.getElementById('resumeOverlay');
    const seasonSelectWrap = document.getElementById('seasonSelectWrap');
    const seasonSelect     = document.getElementById('seasonSelect');

    // Zone references
    const zoneLeft          = document.getElementById('zoneLeft');
    const zoneCenter        = document.getElementById('zoneCenter');
    const zoneRight         = document.getElementById('zoneRight');

    // Panel toggles
    const panels = ['episodesPanel','serversPanel','subsPanel','settingsPanel','qualityPanel','audioPanel','speedPanel','aspectPanel'];

    // SVG Icons
    const SVGS = {
        play:  `<svg viewBox="0 0 24 24" fill="none" width="100%" height="100%"><path d="M4 2.691a1 1 0 0 1 1.482-.876l16.925 9.309a1 1 0 0 1 0 1.752L5.482 22.185A1 1 0 0 1 4 21.309V2.69Z" fill="currentColor"></path></svg>`,
        pause: `<svg viewBox="0 0 24 24" fill="none" width="100%" height="100%"><rect x="5" y="3" width="4" height="18" rx="1.5" fill="currentColor"/><rect x="15" y="3" width="4" height="18" rx="1.5" fill="currentColor"/></svg>`,
        rewind10: `<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path fill-rule="evenodd" clip-rule="evenodd" d="M11.02 2.048A10 10 0 1 1 2 12H0a12 12 0 1 0 5-9.747V1H3v4a1 1 0 0 0 1 1h4V4H6a10 10 0 0 1 5.02-1.952ZM2 4v3h3v2H1a1 1 0 0 1-1-1V4h2Zm12.125 12c-.578 0-1.086-.141-1.523-.424-.43-.29-.764-.694-.999-1.215-.235-.527-.353-1.148-.353-1.861 0-.707.118-1.324.353-1.851.236-.527.568-.932.999-1.215.437-.29.945-.434 1.523-.434s1.083.145 1.513.434c.437.283.774.688 1.009 1.215.235.527.353 1.144.353 1.851 0 .713-.118 1.334-.353 1.86-.235.522-.572.927-1.009 1.216-.43.283-.935.424-1.513.424Zm0-1.35c.39 0 .696-.186.918-.56.222-.378.333-.909.333-1.59s-.111-1.208-.333-1.581c-.222-.38-.528-.57-.918-.57s-.696.19-.918.57c-.222.373-.333.9-.333 1.581 0 .681.111 1.212.333 1.59.222.374.528.56.918.56Zm-5.521 1.205v-5.139L7 11.141V9.82l3.198-.8v6.835H8.604Z" fill="currentColor"></path></svg>`,
        forward10: `<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path fill-rule="evenodd" clip-rule="evenodd" d="M6.444 3.685A10 10 0 0 1 18 4h-2v2h4a1 1 0 0 0 1-1V1h-2v1.253A12 12 0 1 0 24 12h-2A10 10 0 1 1 6.444 3.685ZM22 4v3h-3v2h4a1 1 0 0 0 1-1V4h-2Zm-9.398 11.576c.437.283.945.424 1.523.424s1.083-.141 1.513-.424c.437-.29.774-.694 1.009-1.215.235-.527.353-1.148.353-1.861 0-.707-.118-1.324-.353-1.851-.235-.527-.572-.932-1.009-1.215-.43-.29-.935-.434-1.513-.434-.578 0-1.086.145-1.523.434-.43.283-.764.688-.999 1.215-.235.527-.353 1.144-.353 1.851 0 .713.118 1.334.353 1.86.236.522.568.927.999 1.216Zm2.441-1.485c-.222.373-.528.56-.918.56s-.696-.187-.918-.56c-.222-.38-.333-.91-.333-1.591 0-.681.111-1.208.333-1.581.222-.38.528-.57.918-.57s.696.19.918.57c.222.373.333.9.333 1.581 0 .681-.111 1.212-.333 1.59Zm-6.439-3.375v5.14h1.594V9.018L7 9.82v1.321l1.604-.424Z" fill="currentColor"></path></svg>`,
        volHigh:`<svg viewBox="0 0 24 24" fill="none" width="100%" height="100%"><path fill-rule="evenodd" clip-rule="evenodd" d="M24 12a14 14 0 0 0-4.1-9.9l-1.415 1.415a12 12 0 0 1 0 16.97L19.9 21.9A14 14 0 0 0 24 12ZM11 4a1 1 0 0 0-1.707-.707L4.586 8H1a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h3.586l4.707 4.707A1 1 0 0 0 11 20V4ZM5.707 9.707 9 6.414v11.172l-3.293-3.293L5.414 14H2v-4h3.414l.293-.293ZM16 12a6 6 0 0 0-1.757-4.243l-1.415 1.415a4 4 0 0 1 0 5.656l1.415 1.415A6 6 0 0 0 16 12ZM17.071 4.93a10 10 0 0 1 0 14.142l-1.414-1.414a8 8 0 0 0 0-11.314L17.07 4.93Z" fill="currentColor"/></svg>`,
        volLow: `<svg viewBox="0 0 24 24" fill="none" width="100%" height="100%"><path fill-rule="evenodd" clip-rule="evenodd" d="M11 4a1 1 0 0 0-1.707-.707L4.586 8H1a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h3.586l4.707 4.707A1 1 0 0 0 11 20V4ZM5.707 9.707 9 6.414v11.172l-3.293-3.293L5.414 14H2v-4h3.414l.293-.293ZM16 12a6 6 0 0 0-1.757-4.243l-1.415 1.415a4 4 0 0 1 0 5.656l1.415 1.415A6 6 0 0 0 16 12Z" fill="currentColor"/></svg>`,
        volMute:`<svg viewBox="0 0 24 24" fill="none" width="100%" height="100%"><path fill-rule="evenodd" clip-rule="evenodd" d="M11 4a1 1 0 0 0-1.707-.707L4.586 8H1a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h3.586l4.707 4.707A1 1 0 0 0 11 20V4ZM5.707 9.707 9 6.414v11.172l-3.293-3.293L5.414 14H2v-4h3.414l.293-.293ZM23.414 12l2.293-2.293-1.414-1.414L22 10.586 19.707 8.293l-1.414 1.414L20.586 12l-2.293 2.293 1.414 1.414L22 13.414l2.293 2.293 1.414-1.414L23.414 12Z" fill="currentColor"/></svg>`,
        check:  `<svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/></svg>`,
    };

    // Volume OSD
    let volumeOsdTimer = null;
    const showVolumeOsd = (vol) => {
        const osd = document.getElementById('volumeOsd');
        const txt = document.getElementById('volumeOsdText');
        const icon = document.getElementById('volumeOsdIcon');
        if (!osd || !txt || !icon) return;
        
        txt.innerText = `${Math.round(vol)}%`;
        icon.innerHTML = vol > 100 ? SVGS.volHigh : (vol > 0 && !isMuted ? SVGS.volLow : SVGS.volMute);
        
        osd.classList.add('show');
        clearTimeout(volumeOsdTimer);
        volumeOsdTimer = setTimeout(() => { osd.classList.remove('show'); }, 1500);
    };

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
    let globalIsPlaying = false;
    let lastMouseX = -1;
    let lastMouseY = -1;
    
    const showControls = (e) => {
        if (e && e.type === 'mousemove') {
            if (e.clientX === lastMouseX && e.clientY === lastMouseY) {
                return; // Ignore synthesized mousemove where mouse didn't actually move
            }
            lastMouseX = e.clientX;
            lastMouseY = e.clientY;
        }
        const pOverlay = document.getElementById('linkProbingOverlay');
        const isProbing = pOverlay && !pOverlay.classList.contains('dismissing');
        if (resumeOverlay.style.display === 'flex' || (isProbing && !userDismissedProbing)) {
            clearTimeout(hideTimer);
            document.body.classList.remove('hidden-controls');
            return;
        }
        if (overlay.style.opacity === '0' && videoEndedOverlay.style.display !== 'flex') {
            overlay.style.opacity = '';
        }
        overlay.classList.remove('hidden-controls');
        document.body.classList.remove('hidden-controls');
        clearTimeout(hideTimer);
        if (!isMenuOpen && globalIsPlaying) {
            hideTimer = setTimeout(() => {
                overlay.classList.add('hidden-controls');
                document.body.classList.add('hidden-controls');
            }, 3500);
        }
    };
    document.addEventListener('mousemove', showControls);
    document.addEventListener('click', showControls);
    document.addEventListener('keydown', showControls);

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
    panels.forEach(id => {
        const panel = document.getElementById(id);
        panel.addEventListener('click', e => e.stopPropagation());
    });

    // Close buttons
    document.getElementById('closeEpisodesBtn').addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeServersBtn').addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeSubsBtn').addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    


    document.getElementById('closeSettingsBtn').addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeQualityBtn').addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeAudioBtn').addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeSpeedBtn').addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });
    document.getElementById('closeAspectBtn').addEventListener('click', e => { e.stopPropagation(); closeAllPanels(); });

    // Season Dropdown Selector Change Event
    seasonSelect.addEventListener('change', e => {
        renderFilteredEpisodes(parseInt(e.target.value));
    });

    // Resume Bar (inline in bottom-bar)

    document.getElementById('btnStartOver').addEventListener('click', () => {
        if (window.resumeDismissTimer) { clearTimeout(window.resumeDismissTimer); window.resumeDismissTimer = null; }
        resumeOverlay.style.display = 'none';
        resumeHandled = true;
        send('seekTo', 0);
        send('play'); // Unpause immediately when starting over
    });

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
        forceShowLoading();
        // Hold the lock — release via incoming state update, not a timer
        clearTimeout(seekLockTimer);
        seekLockTimer = setTimeout(() => { isSeeking = false; }, 1500);
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
        const pct = (val / 200) * 100;
        volumeBar.style.setProperty('--vol-pct', pct + '%');
    };
    updateVolumeTrack(100); // Initialize

    volumeBar.addEventListener('input', e => {
        updateVolumeTrack(e.target.value);
        send('setVolume', e.target.value);
    });

    document.addEventListener('wheel', e => {
        if (isMenuOpen) return; // Don't scroll volume if a settings menu is open
        
        let newVol = currentVolume;
        // Scroll up (negative deltaY) increases volume, scroll down decreases
        if (e.deltaY < 0) {
            newVol = Math.min(200, newVol + 5);
        } else if (e.deltaY > 0) {
            newVol = Math.max(0, newVol - 5);
        }
        
        if (newVol !== currentVolume) {
            currentVolume = newVol;
            volumeBar.value = newVol;
            updateVolumeTrack(newVol);
            send('setVolume', newVol);
            showControls(); // Wake up controls so user can see the volume change
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
        if (durationMs > 0) {
            let pct = (currentPosMs / durationMs) * 100;
            pct = Math.max(0, Math.min(100, pct));
            seekFill.style.width = `${pct}%`;
            seekBar.value = pct * 10;
        }
        timeDisplay.innerText = `${fmt(currentPosMs)} / ${fmt(durationMs)}`;
        send('seekBy', deltaMs);
        forceShowLoading();
        clearTimeout(seekLockTimer);
        seekLockTimer = setTimeout(() => { isSeeking = false; }, 1500);
    };

    // Backend Messages
    let loadingTimer = null;
    let isCurrentlyLoading = false;
    // let isAppLoading = false; (now global)
    let globalIsLoading = false;   // set by C++ via state_update (core_idle || paused-for-cache)

    const forceShowLoading = () => {
        if (globalIsPlaying) {
            isCurrentlyLoading = true;
            clearTimeout(loadingTimer);
            loadingContainer.classList.add('show');
        }
    };

    const handleStateUpdate = (s) => {
        if (typeof s.durationMs === 'number') durationMs = s.durationMs;
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
                
                if (typeof s.bufferMs === 'number') {
                    let bufPct = (s.bufferMs / durationMs) * 100;
                    bufPct = Math.max(0, Math.min(100, bufPct));
                    seekBuffer.style.width = `${bufPct}%`;
                }
            }
        }
        timeDisplay.innerText = `${fmt(currentPosMs)} / ${fmt(durationMs)}`;
        
        const wasPlaying = globalIsPlaying;
        if (s.isPlaying !== undefined) {
            globalIsPlaying = !!s.isPlaying;
        }

        if (wasPlaying !== globalIsPlaying) {
            playPauseBtn.classList.toggle('is-playing', globalIsPlaying);


            // Only show controls on actual play/pause toggle, not on every rapid state_update
            // Throttled to prevent flicker when MPV toggles pause rapidly during buffering
            if (typeof s.positionMs === 'number' && s.positionMs > 500) {
                showControls();
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
        
        // (AUTO-PLAY FIX removed in favor of strict Kotlin state management)
        
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
            
            const qStr = String(l.quality).toLowerCase();
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
            
            const qStr = String(l.quality).toLowerCase();
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

        document.getElementById('serversList').innerHTML = filtered.map(l => {
            const qStr = l.quality ? String(l.quality) : '';
            const qStrLower = qStr.toLowerCase();
            const is4K = qStrLower.includes('2160') || qStrLower.includes('4k');
            const isHD = qStrLower.includes('1080') || qStrLower.includes('720') || qStrLower.includes('hd');
            
            let badgeText = 'SD';
            let badgeClass = 'sd';
            if (is4K) { badgeText = '4K'; badgeClass = 'hd'; }
            else if (isHD) { badgeText = 'HD'; badgeClass = 'hd'; }
            
            return `<div class="srv-item ${l.isActive ? 'active' : ''}" onclick="send('changeLink','${l.index}');closeAllPanels();">
                <span class="srv-check">${l.isActive ? SVGS.check : ''}</span>
                <svg class="srv-icon" viewBox="0 0 24 24" fill="currentColor"><path d="M4 1h16c.55 0 1 .45 1 1v4c0 .55-.45 1-1 1H4c-.55 0-1-.45-1-1V2c0-.55.45-1 1-1zm0 8h16c.55 0 1 .45 1 1v4c0 .55-.45 1-1 1H4c-.55 0-1-.45-1-1v-4c0-.55.45-1 1-1zm0 8h16c.55 0 1 .45 1 1v4c0 .55-.45 1-1 1H4c-.55 0-1-.45-1-1v-4c0-.55.45-1 1-1z"/><circle cx="19" cy="4" r="1" fill="currentColor"/><circle cx="19" cy="12" r="1" fill="currentColor"/><circle cx="19" cy="20" r="1" fill="currentColor"/></svg>
                <span class="srv-name">${l.name || 'Source ' + (l.index + 1)}</span>
                <span class="srv-quality">${qStr}</span>
                ${qStr ? `<span class="srv-badge ${badgeClass}">${badgeText}</span>` : ''}
            </div>`;
        }).join('');
    };
    window.renderFilteredServers = renderFilteredServers;

    const renderFilteredEpisodes = (selectedSeason) => {
        const filtered = episodesData.filter(ep => {
            const epSeason = ep.season !== undefined && ep.season !== null ? ep.season : 1;
            return epSeason === selectedSeason;
        });

        document.getElementById('episodesList').innerHTML = filtered.map((ep, i) => {
            const num = ep.episode || (i + 1);
            const thumb = ep.posterUrl ? `<img src="${ep.posterUrl}" class="ep-thumb" onerror="this.style.display='none'">` : '';
            const epIdEncoded = encodeURIComponent(ep.id || '');
            const epTitleEscaped = (ep.title || ('Episode ' + num)).replace(/</g, "&lt;").replace(/>/g, "&gt;");
            return `<div class="ep-card ${ep.isActive ? 'active' : ''}" onclick="send('loadEpisode', decodeURIComponent('${epIdEncoded}'));closeAllPanels();">
                <span class="ep-num">${num}</span>
                ${thumb}
                <div class="ep-info">
                    <div class="ep-title">${epTitleEscaped}</div>
                    <div class="ep-meta">Season ${ep.season || 1}${ep.runTime ? ' · ' + ep.runTime + 'm' : ''}</div>
                    ${ep.isActive ? '<div class="ep-playing">● Playing</div>' : ''}
                </div>
            </div>`;
        }).join('');

        setTimeout(() => {
            const active = document.querySelector('#episodesList .ep-card.active');
            if (active) active.scrollIntoView({ block: 'center', behavior: 'smooth' });
        }, 150);
    };

    const handleMetadataUpdate = (meta) => {
        // 1. Session & State Reset
        // Detect a genuinely new playback session (new title OR new episode OR new link set)
        const activeEp = (meta.episodes || []).find(e => e.isActive);
        const activeId = activeEp ? activeEp.id : '';
        if (meta.title) {
            const isNewSession = currentTitle !== meta.title
                || currentEpisodeId !== activeId
                || (meta.currentLinkIndex !== undefined && currentLinkIndex !== meta.currentLinkIndex);

            if (isNewSession) {
                // *** Atomically reset ALL state so stale timers/overlays can't race ***
                hardResetAllOverlays();
                currentTitle = meta.title;
                currentEpisodeId = activeId;
                if (meta.currentLinkIndex !== undefined) currentLinkIndex = meta.currentLinkIndex;

                // Immediately pre-fill the probing screen with the NEW episode's data
                // so there is zero window where old/stale data is visible on screen.
                const pTitle = document.getElementById('linkProbingTitle');
                const pLogo = document.getElementById('linkProbingLogo');
                const pStatus = document.getElementById('linkProbingStatus');
                const pBackdrop = document.getElementById('linkProbingBackdrop');
                const pList = document.getElementById('linkProbingList');

                // Clear stale link list immediately
                if (pList) pList.innerHTML = '';

                // Reset status text
                if (pStatus) pStatus.innerText = 'Finding sources\u2026';

                // Update title: show episode-aware title immediately
                if (pTitle) {
                    let displayTitle = meta.title;
                    if (activeEp) {
                        let epStr = '';
                        if (activeEp.season && activeEp.episode) epStr = `S${activeEp.season}:E${activeEp.episode}`;
                        else if (activeEp.episode) epStr = `Ep ${activeEp.episode}`;
                        if (epStr) displayTitle += ` \u2022 ${epStr}`;
                        const epTitle = activeEp.title || '';
                        if (epTitle && epTitle !== meta.title && !epTitle.toLowerCase().startsWith('episode')) {
                            displayTitle += ` - ${epTitle}`;
                        }
                    }
                    pTitle.innerText = displayTitle;
                    pTitle.style.display = 'block';
                }
                if (pLogo) pLogo.style.display = 'none';

                // Crossfade backdrop to the new episode's art immediately
                const newBackdrop = meta.backdropUrl || (activeEp ? activeEp.posterUrl : '');
                if (pBackdrop && newBackdrop && pBackdrop.dataset.lastSrc !== newBackdrop) {
                    pBackdrop.dataset.lastSrc = newBackdrop;
                    pBackdrop.classList.remove('loaded');
                    const tempImg = new Image();
                    tempImg.onload = () => {
                        pBackdrop.src = newBackdrop;
                        requestAnimationFrame(() => pBackdrop.classList.add('loaded'));
                    };
                    tempImg.src = newBackdrop;
                } else if (pBackdrop && !newBackdrop) {
                    pBackdrop.classList.remove('loaded');
                    pBackdrop.dataset.lastSrc = '';
                }
            }
            titleDisplay.innerText = meta.title;
            document.getElementById('resumeTitle').innerText = meta.title;
        }

        if (meta.startPositionMs !== undefined) {
            pendingResumeMs = meta.startPositionMs;
        }

        // Populate Pause Info Overlay
        const pauseLogo = document.getElementById('pauseInfoLogo');
        const pauseFallback = document.getElementById('pauseInfoTitleFallback');
        const pauseEp = document.getElementById('pauseInfoEpisode');
        const pausePlot = document.getElementById('pauseInfoPlot');
        const pauseYear = document.getElementById('pauseInfoYear');
        const pauseTags = document.getElementById('pauseInfoTags');
        const pauseMeta = document.getElementById('pauseInfoMeta');
        
        if (meta.logoUrl) {
            pauseLogo.src = meta.logoUrl;
            pauseLogo.style.display = 'block';
            pauseFallback.style.display = 'none';
        } else if (meta.title) {
            pauseLogo.style.display = 'none';
            pauseFallback.innerText = meta.title;
            pauseFallback.style.display = 'block';
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
            pausePlot.innerText = (activeEpInfo && activeEpInfo.description) ? activeEpInfo.description : meta.plot;
            pausePlot.style.display = '-webkit-box';
        } else {
            pausePlot.style.display = 'none';
        }

        let hasMeta = false;
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

        // Link Probing Overlay Logic
        const pOverlay = document.getElementById('linkProbingOverlay');
        const pBackdrop = document.getElementById('linkProbingBackdrop');
        const pLogo = document.getElementById('linkProbingLogo');
        const pTitle = document.getElementById('linkProbingTitle');
        const pList = document.getElementById('linkProbingList');
        const pContent = document.getElementById('linkProbingContent');
        const pStatus = document.getElementById('linkProbingStatus');

        if (meta.isProbing === true && !userDismissedProbing) {
            if (window.probingDismissTimer) clearTimeout(window.probingDismissTimer);
            clearTimeout(hideTimer);
            document.body.classList.remove('hidden-controls');
            pOverlay.classList.remove('dismissing');
            
            const resumeOvl = document.getElementById('resumeOverlay');
            if (resumeOvl) resumeOvl.style.display = 'none';

            pContent.classList.remove('dismissing');

            const activeEpInfo = (meta.episodes || []).find(e => e.isActive);
            const targetBackdropUrl = meta.backdropUrl || (activeEpInfo ? activeEpInfo.posterUrl : '');

            // Backdrop: only set src if it changed, use onload for smooth transition
            if (targetBackdropUrl && pBackdrop.dataset.lastSrc !== targetBackdropUrl) {
                pBackdrop.dataset.lastSrc = targetBackdropUrl;
                pBackdrop.classList.remove('loaded');
                const tempImg = new Image();
                tempImg.onload = () => {
                    pBackdrop.src = targetBackdropUrl;
                    // RAF to allow repaint before class triggers CSS transition
                    requestAnimationFrame(() => pBackdrop.classList.add('loaded'));
                };
                tempImg.onerror = () => {
                    pBackdrop.dataset.lastSrc = ''; 
                    pBackdrop.classList.remove('loaded');
                };
                tempImg.src = targetBackdropUrl;
            } else if (!targetBackdropUrl) {
                pBackdrop.classList.remove('loaded');
                pBackdrop.dataset.lastSrc = '';
            }

            // Logo or text hero
            if (meta.logoUrl && !activeEpInfo) {
                // Only use the show logo if it's a movie (no episodes), 
                // for episodes we prefer the text title to show the episode number.
                pLogo.src = meta.logoUrl;
                pLogo.style.display = 'block';
                pTitle.style.display = 'none';
            } else {
                pLogo.style.display = 'none';
                pTitle.style.display = 'block';
                
                let displayTitle = meta.title || '';
                if (activeEpInfo) {
                    let epStr = '';
                    if (activeEpInfo.season && activeEpInfo.episode) {
                        epStr = `S${activeEpInfo.season}:E${activeEpInfo.episode}`;
                    } else if (activeEpInfo.episode) {
                        epStr = `Ep ${activeEpInfo.episode}`;
                    }
                    if (epStr) {
                        displayTitle += ` • ${epStr}`;
                    }
                    
                    const epTitle = activeEpInfo.title || '';
                    if (epTitle && epTitle !== meta.title && !epTitle.toLowerCase().startsWith('episode')) {
                        displayTitle += ` - ${epTitle}`;
                    }
                }
                
                pTitle.innerText = displayTitle;
            }

            // Status label
            if (pStatus) {
                const totalLinks = (meta.links || []).length;
                const failedCount = (meta.failedLinks || []).length;
                if (totalLinks === 0) {
                    pStatus.innerText = 'Finding sources\u2026';
                } else if (failedCount >= totalLinks) {
                    pStatus.innerText = 'Waiting for more links...';
                } else if (failedCount > 0) {
                    pStatus.innerText = `Trying source ${failedCount + 1} of ${totalLinks}`;
                } else {
                    pStatus.innerText = `Trying source 1 of ${totalLinks}`;
                }
            }

            // Link list
            if (meta.links && meta.links.length > 0) {
                const failedArr = meta.failedLinks || [];
                const currIdx = typeof meta.currentLinkIndex === 'number' ? meta.currentLinkIndex : 0;

                pList.innerHTML = meta.links.map((l, i) => {
                    let st = 'waiting';
                    let icon = '';
                    let errorHtml = '';
                    const failedInfo = failedArr.find(f => f.index === l.index);

                    if (failedInfo) {
                        st = 'failed';
                        if (failedInfo.reason) {
                            errorHtml = `<div class="err-reason">${failedInfo.reason}</div>`;
                        }
                        icon = '<svg width="14" height="14" viewBox="0 0 24 24" fill="rgba(255,80,80,0.8)"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>';
                    } else if (l.index === currIdx) {
                        st = 'active';
                        icon = '<div class="link-spinner"><span></span></div>';
                    } else if (l.index > currIdx) {
                        st = 'waiting';
                        icon = '';
                    }
                    const quality = l.quality ? `<span style="font-size:11px;opacity:0.5;margin-left:8px;">${l.quality}</span>` : '';
                    return `<div class="link-probing-item ${st}" style="animation-delay:${Math.min(i * 0.06, 0.5)}s">
                        <div style="display: flex; flex-direction: column; align-items: flex-start;">
                            <div>${l.name}${quality}</div>
                            ${errorHtml}
                        </div>
                        <span>${icon}</span>
                    </div>`;
                }).join('');

                // Scroll current item into view
                setTimeout(() => {
                    const activeProb = pList.querySelector('.link-probing-item.active');
                    if (activeProb) activeProb.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }, 100);
            } else {
                pList.innerHTML = '';
            }
        } else if (meta.isProbing === false) {
            // Kotlin finished scraping/link selection.
            // Do NOT dismiss the overlay yet! Let C++ wait for the first frame (position > 0.1)
            const pStatus = document.getElementById('linkProbingStatus');
            if (pStatus) {
                pStatus.innerText = 'Connecting to source...';
            }
            
            // Highlight the successfully resolved link
            const pList = document.getElementById('linkProbingList');
            if (pList) {
                const activeProb = pList.querySelector('.link-probing-item.active');
                if (activeProb) {
                    activeProb.classList.remove('active');
                    activeProb.style.background = 'rgba(34, 197, 94, 0.15)';
                    activeProb.style.borderColor = 'rgba(34, 197, 94, 0.3)';
                    const iconSpan = activeProb.querySelector('span:last-child');
                    if (iconSpan) {
                        iconSpan.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="rgba(34, 197, 94, 0.9)"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/></svg>';
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

            if (seasons.length > 1) {
                seasonSelectWrap.style.display = 'block';
                // Populate options
                seasonSelect.innerHTML = seasons.map(s => `<option value="${s}">Season ${s}</option>`).join('');

                // Auto-select season of active episode
                const activeEp = meta.episodes.find(e => e.isActive);
                const activeSeason = activeEp && activeEp.season !== undefined && activeEp.season !== null ? activeEp.season : seasons[0];
                seasonSelect.value = activeSeason;

                renderFilteredEpisodes(activeSeason);
            } else {
                seasonSelectWrap.style.display = 'none';
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
        if (meta.audioTracks && meta.audioTracks.length > 0) {
            audioHtml += meta.audioTracks.map(t => `
                <div class="track-item ${t.isSelected ? 'active' : ''}" onclick="send('setAudioTrack','${t.id}');closeAllPanels();">
                    <span class="track-name">${t.name || ('Track ' + t.id)}</span>
                    <span class="track-check">${SVGS.check}</span>
                </div>`).join('');
        }
        if (meta.lazyAudioTracks && meta.lazyAudioTracks.length > 0) {
            audioHtml += meta.lazyAudioTracks.map(t => `
                <div class="track-item" onclick="send('loadLazyAudioTrack','${t.url}');closeAllPanels();">
                    <span class="track-name">${t.name}</span>
                    <span class="track-check"></span>
                </div>`).join('');
        }
        document.getElementById('audioList').innerHTML = audioHtml || `<div style="padding:10px 20px;font-size:13px;color:#666;">No audio tracks</div>`;

        // Video Tracks (Qualities)
        let videoHtml = '';
        if (meta.lazyVideoTracks && meta.lazyVideoTracks.length > 0) {
            videoHtml += meta.lazyVideoTracks.map(t => {
                const isHD = t.name.includes('1080') || t.name.includes('720');
                const resBadge = `<span class="srv-badge ${isHD ? 'hd' : 'sd'}">${isHD ? 'HD' : 'SD'}</span>`;
                
                const height = meta.resolution ? meta.resolution.split('x')[1] : null;
                const isActive = meta.activeLazyVideoTrackUrl 
                    ? (t.url === meta.activeLazyVideoTrackUrl)
                    : (height && t.name.includes(height));
                    
                return `
                <div class="track-item ${isActive ? 'active' : ''}" onclick="send('loadLazyVideoTrack','${t.url}');closeAllPanels();">
                    <span class="track-name">${t.name} ${resBadge}</span>
                    <span class="track-check">${SVGS.check}</span>
                </div>`;
            }).join('');
        }
        document.getElementById('videoList').innerHTML = videoHtml || `<div style="padding:10px 20px;font-size:13px;color:#666;">Auto (No qualities available)</div>`;

        // Subtitle Tracks
        let subHtml = '';
        const noSub = !(meta.subTracks && meta.subTracks.some(t => t.isSelected));
        subHtml += `<div class="sub-item ${noSub ? 'active' : ''}" onclick="send('setSubtitleTrack','');closeAllPanels();">
                    <span class="sub-name">Off</span>
                    <span class="check-icon">${SVGS.check}</span>
                </div>`;
        if (meta.subTracks && meta.subTracks.length > 0) {
            // Deduplicate tracks with the exact same name (MPV native vs CloudStream proxy overlap)
            const uniqueSubTracks = [];
            const seenNames = new Set();
            for (const t of meta.subTracks) {
                const displayName = t.name || ('Track ' + t.id);
                if (!seenNames.has(displayName)) {
                    seenNames.add(displayName);
                    uniqueSubTracks.push(t);
                } else if (t.isSelected) {
                    // If this duplicate is the currently selected one, swap it in so the checkmark shows correctly!
                    const existingIndex = uniqueSubTracks.findIndex(existing => (existing.name || ('Track ' + existing.id)) === displayName);
                    if (existingIndex !== -1) uniqueSubTracks[existingIndex] = t;
                }
            }

            subHtml += uniqueSubTracks.map(t => `
                <div class="sub-item ${t.isSelected ? 'active' : ''}" onclick="send('setSubtitleTrack','${t.id}');closeAllPanels();">
                    <span class="sub-name">${t.name || ('Track ' + t.id)}</span>
                    <span class="check-icon">${SVGS.check}</span>
                </div>`).join('');
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
                    btnOverride.innerText = 'On';
                } else {
                    btnOverride.classList.remove('active');
                    btnOverride.innerText = 'Off';
                }
            }
        }

        // Initialize Background Slider
        if (meta.activeSubtitleBackground) {
            const bgHex = meta.activeSubtitleBackground;
            if (bgHex.length === 9) {
                const alphaHex = bgHex.substring(1, 3);
                const alphaInt = parseInt(alphaHex, 16);
                if (!isNaN(alphaInt)) {
                    const bgSlider = document.getElementById('subBgSlider');
                    const bgVal = document.getElementById('subBgVal');
                    if (bgSlider && bgVal) {
                        bgSlider.value = alphaInt;
                        bgVal.innerText = alphaInt;
                    }
                }
            }
        }

        // Advanced Subtitle Styles
        if (meta.activeSubtitleBorderColor) {
            document.querySelectorAll('.border-dot').forEach(d => {
                if (d.dataset.color === meta.activeSubtitleBorderColor) {
                    document.querySelectorAll('.border-dot').forEach(x => x.classList.remove('active'));
                    d.classList.add('active');
                }
            });
        }
        if (meta.activeSubtitleShadowColor) {
            document.querySelectorAll('.shadow-dot').forEach(d => {
                if (d.dataset.color === meta.activeSubtitleShadowColor) {
                    document.querySelectorAll('.shadow-dot').forEach(x => x.classList.remove('active'));
                    d.classList.add('active');
                }
            });
        }
        if (meta.activeSubtitleBorderSize) {
            const slider = document.getElementById('subBorderSizeSlider');
            if (slider) {
                slider.value = meta.activeSubtitleBorderSize;
                document.getElementById('subBorderSizeVal').innerText = meta.activeSubtitleBorderSize;
            }
        }
        if (meta.activeSubtitleShadowOffset) {
            const slider = document.getElementById('subShadowOffsetSlider');
            if (slider) {
                slider.value = meta.activeSubtitleShadowOffset;
                document.getElementById('subShadowOffsetVal').innerText = meta.activeSubtitleShadowOffset;
            }
        }
        if (meta.activeSubtitleBlur) {
            const slider = document.getElementById('subBlurSlider');
            if (slider) {
                slider.value = meta.activeSubtitleBlur;
                document.getElementById('subBlurVal').innerText = meta.activeSubtitleBlur;
            }
        }
        
        let subBoldState = meta.activeSubtitleBold === 'yes';
        let subItalicState = meta.activeSubtitleItalic === 'yes';
        const btnBold = document.getElementById('btnSubBold');
        if (btnBold) btnBold.style.background = subBoldState ? 'rgba(255,255,255,0.3)' : 'rgba(255,255,255,0.1)';
        const btnItalic = document.getElementById('btnSubItalic');
        if (btnItalic) btnItalic.style.background = subItalicState ? 'rgba(255,255,255,0.3)' : 'rgba(255,255,255,0.1)';

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
    };

    const dismissProbingOverlay = (userInitiated = false) => {
        if (userInitiated) {
            userDismissedProbing = true;
        } else if (window.sessionStartTime && (Date.now() - window.sessionStartTime < 1000)) {
            // Ignore stale dismiss events from C++ that bleed over from the previous stream 
            // during the first second of a new playback session.
            return;
        }
        
        const pOverlay = document.getElementById('linkProbingOverlay');
        const pContent = document.getElementById('linkProbingContent');
        if (!pOverlay) return;
        
        // Fade content out immediately
        if (pContent) pContent.classList.add('dismissing');
        
        const finishDismissal = () => {
            pOverlay.classList.remove('active');
            pOverlay.classList.add('dismissing');
            
            const resumeOvl = document.getElementById('resumeOverlay');
            if (resumeOvl) resumeOvl.style.display = '';

            // Only show the player UI when the overlay is dismissed
            showControls();
            evaluateUIStates();
        };

        if (userInitiated) {
            finishDismissal();
        } else {
            // Give content 300ms to fade, then crossfade the whole overlay
            setTimeout(finishDismissal, 300);
        }
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
    window.openSubSearchModal = () => {
        const overlay = document.getElementById('subSearchOverlay');
        if (overlay) {
            overlay.style.display = 'flex';
            // Also close other things if open, e.g. panel
            closeAllPanels();
        }
    };

    window.closeSubSearchModal = () => {
        const overlay = document.getElementById('subSearchOverlay');
        if (overlay) overlay.style.display = 'none';
    };

    window.showToast = (msg) => {
        let overlay = document.getElementById('toastOverlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'toastOverlay';
            overlay.style.cssText = 'position: absolute; bottom: 80px; left: 0; right: 0; pointer-events: none; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; z-index: var(--z-toast); gap: 8px; padding-bottom: 20px;';
            document.body.appendChild(overlay);
        }
        const toast = document.createElement('div');
        toast.style.cssText = 'background: rgba(0,0,0,0.85); color: white; padding: 10px 16px; border-radius: 8px; font-size: 14px; font-weight: 600; backdrop-filter: blur(8px); border: 1px solid rgba(255,255,255,0.1); transform: translateY(20px); opacity: 0; transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);';
        toast.innerText = msg;
        overlay.appendChild(toast);
        
        toast.offsetHeight; // trigger reflow
        toast.style.transform = 'translateY(0)';
        toast.style.opacity = '1';
        
        setTimeout(() => {
            toast.style.transform = 'translateY(20px)';
            toast.style.opacity = '0';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    };

    // Subtitle Search
    window.doSubSearch = () => {
        const query   = document.getElementById('subSearchQuery')?.value?.trim() || '';
        const lang    = document.getElementById('subSearchLang')?.value || '';
        const season  = document.getElementById('subSearchSeason')?.value?.trim() || '';
        const episode = document.getElementById('subSearchEpisode')?.value?.trim() || '';
        if (!query) return;

        const resultsEl = document.getElementById('subSearchResults');
        const statusEl  = document.getElementById('subSearchStatus');
        if (resultsEl) resultsEl.innerHTML = '';
        if (statusEl)  { statusEl.style.display = 'block'; statusEl.textContent = 'Searching...'; }

        send('searchSubtitles', JSON.stringify({ query, lang, season, episode }));
    };

    const handleSubtitleSearchResults = (p) => {
        const statusEl  = document.getElementById('subSearchStatus');
        const resultsEl = document.getElementById('subSearchResults');
        if (!resultsEl) return;

        if (statusEl) statusEl.style.display = 'none';

        const results = p.results || [];
        if (results.length === 0) {
            resultsEl.innerHTML = '<div style="color:#888;font-size:12px;text-align:center;padding:16px;">No subtitles found.</div>';
            return;
        }

        resultsEl.innerHTML = results.map((r, i) => {
            const lang    = (r.lang || '??').substring(0, 3).toUpperCase();
            const name    = (r.name || 'Unknown');
            const source  = r.source || '';
            const epTag   = [
                r.seasonNumber ? `S${String(r.seasonNumber).padStart(2,'0')}` : '',
                r.epNumber     ? `E${String(r.epNumber).padStart(2,'0')}` : ''
            ].filter(Boolean).join(' ');
            return `
            <div class="sub-result-item" style="display:flex;align-items:center;gap:12px;background:rgba(255,255,255,0.03);border-radius:12px;padding:12px 16px;border:1px solid rgba(255,255,255,0.05);transition:all 0.2s ease;">
                <div style="background:rgba(229,57,53,0.15);border:1px solid rgba(229,57,53,0.3);border-radius:8px;padding:6px 10px;font-size:12px;font-weight:700;color:#ef9a9a;white-space:nowrap;box-shadow:inset 0 1px 3px rgba(0,0,0,0.2);">${lang}</div>
                <div style="flex:1;min-width:0;">
                    <div style="display:flex;align-items:center;gap:8px;">
                        <span style="color:#eee;font-size:14px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${name}</span>
                        ${epTag ? `<span style="color:#aaa;background:rgba(255,255,255,0.1);padding:2px 6px;border-radius:4px;font-size:11px;font-weight:600;white-space:nowrap;">${epTag}</span>` : ''}
                    </div>
                    <div style="color:#888;font-size:12px;margin-top:4px;font-style:normal;display:flex;align-items:center;gap:4px;">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><path d="M12 2v20"></path><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>
                        ${source}
                    </div>
                </div>
                <button class="sub-download-btn" onclick="downloadSubtitle(event, ${i})"
                    style="background:rgba(255,255,255,0.1);border:none;border-radius:8px;width:38px;height:38px;cursor:pointer;color:#fff;display:flex;align-items:center;justify-content:center;flex-shrink:0;transition:all 0.2s ease;">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                </button>
            </div>`;
        }).join('');

        // Store results for download handler
        window._subSearchResults = results;
    };

    window.downloadSubtitle = (e, index) => {
        const results = window._subSearchResults;
        if (!results || !results[index]) return;
        const r = results[index];
        
        const btn = e.currentTarget;
        btn.innerHTML = '...';
        btn.style.opacity = '0.5';
        btn.style.pointerEvents = 'none';

        send('downloadSubtitle', JSON.stringify({ idPrefix: r.idPrefix, data: r.data }));
        
        // Show loading state briefly then close
        setTimeout(() => closeSubSearchModal(), 800);
    };

    // Mark query as user-edited so we stop auto-filling it
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
    const handleZoneClick = (zoneName, e) => {
        e.stopPropagation();
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
            if (zoneName === 'left') {
                doRelativeSeek(-10000);
                triggerActionFeedback(SVGS.rewind10, 'left');
            } else if (zoneName === 'right') {
                doRelativeSeek(10000);
                triggerActionFeedback(SVGS.forward10, 'right');
            } else if (zoneName === 'center') {
                send('togglePlay');
                triggerActionFeedback(globalIsPlaying ? SVGS.pause : SVGS.play, 'center');
            }
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

    document.getElementById('probingPlayBtn').addEventListener('click', e => { 
        e.stopPropagation(); 
        const btn = e.currentTarget;
        btn.innerHTML = `<svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M12 4V2A10 10 0 0 0 2 12h2a8 8 0 0 1 8-8z"><animateTransform attributeName="transform" type="rotate" from="0 12 12" to="360 12 12" dur="1s" repeatCount="indefinite"/></path></svg> Loading...`;
        btn.style.opacity = '0.7';
        btn.style.pointerEvents = 'none';
        userDismissedProbing = true;
        send('skipScraping'); 
    });
    document.getElementById('probingCloseBtn').addEventListener('click', e => { e.stopPropagation(); triggerExit(); });

    fullscreenBtn.addEventListener('click', e => { e.stopPropagation(); send('toggleFullscreen'); });
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
    document.getElementById('btnAudioDelayInc').addEventListener('click', e => { e.stopPropagation(); audioDelaySec += 0.1; send('setMpvProperty', `audio-delay:${audioDelaySec.toFixed(1)}`); updateSyncUI(); });
    document.getElementById('btnAudioDelayDec').addEventListener('click', e => { e.stopPropagation(); audioDelaySec -= 0.1; send('setMpvProperty', `audio-delay:${audioDelaySec.toFixed(1)}`); updateSyncUI(); });
    // Sub Style
    document.querySelectorAll('.color-row').forEach(row => {
        const dots = row.querySelectorAll('.color-dot');
        dots.forEach(d => {
            d.addEventListener('click', e => {
                e.stopPropagation();
                dots.forEach(x => x.classList.remove('active'));
                d.classList.add('active');
                if (d.classList.contains('border-dot')) {
                    send('setSubtitleBorderColor', d.dataset.color);
                } else if (d.classList.contains('shadow-dot')) {
                    send('setSubtitleShadowColor', d.dataset.color);
                } else {
                    send('setMpvProperty', `sub-color:${d.dataset.color}`);
                }
            });
        });
    });
    document.getElementById('subSizeSlider').addEventListener('input', e => { 
        e.stopPropagation(); 
        document.getElementById('subSizeVal').innerText = e.target.value;
        send('setMpvProperty', `sub-font-size:${e.target.value}`); 
    });
    document.getElementById('subBgSlider').addEventListener('input', e => {
        e.stopPropagation();
        document.getElementById('subBgVal').innerText = e.target.value;
        const hex = parseInt(e.target.value).toString(16).padStart(2, '0').toUpperCase();
        send('setSubtitleBackground', `#${hex}000000`);
    });
    document.getElementById('subBorderSizeSlider').addEventListener('input', e => {
        e.stopPropagation();
        document.getElementById('subBorderSizeVal').innerText = e.target.value;
        send('setSubtitleBorderSize', e.target.value);
    });
    document.getElementById('subShadowOffsetSlider').addEventListener('input', e => {
        e.stopPropagation();
        document.getElementById('subShadowOffsetVal').innerText = e.target.value;
        send('setSubtitleShadowOffset', e.target.value);
    });
    document.getElementById('subBlurSlider').addEventListener('input', e => {
        e.stopPropagation();
        document.getElementById('subBlurVal').innerText = e.target.value;
        send('setSubtitleBlur', e.target.value);
    });

    let localSubBold = false;
    let localSubItalic = false;
    document.getElementById('btnSubBold').addEventListener('click', e => {
        e.stopPropagation();
        localSubBold = !localSubBold;
        e.target.style.background = localSubBold ? 'rgba(255,255,255,0.3)' : 'rgba(255,255,255,0.1)';
        send('setSubtitleBold', localSubBold ? 'yes' : 'no');
    });
    document.getElementById('btnSubItalic').addEventListener('click', e => {
        e.stopPropagation();
        localSubItalic = !localSubItalic;
        e.target.style.background = localSubItalic ? 'rgba(255,255,255,0.3)' : 'rgba(255,255,255,0.1)';
        send('setSubtitleItalic', localSubItalic ? 'yes' : 'no');
    });
    document.getElementById('subPosSlider').addEventListener('input', e => { 
        e.stopPropagation(); 
        document.getElementById('subPosVal').innerText = e.target.value;
        send('setMpvProperty', `sub-pos:${e.target.value}`); 
    });

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
    document.getElementById('btnToggleStats').addEventListener('click', e => {
        e.stopPropagation();
        statsVisible = !statsVisible;
        const btn = document.getElementById('btnToggleStats');
        if (statsVisible) {
            btn.classList.add('active');
            btn.innerText = 'On';
            statsOverlay.classList.add('show');
        } else {
            btn.classList.remove('active');
            btn.innerText = 'Off';
            statsOverlay.classList.remove('show');
        }
        send('toggleStats');
    });

    // Interpolation toggle
    let interpolationEnabled = false; // Initialized from Kotlin payload
    const btnToggleInterpolation = document.getElementById('btnToggleInterpolation');
    if (btnToggleInterpolation) {
        btnToggleInterpolation.addEventListener('click', e => {
            e.stopPropagation();
            interpolationEnabled = !interpolationEnabled;
            if (interpolationEnabled) {
                btnToggleInterpolation.classList.add('active');
                btnToggleInterpolation.innerText = 'On';
            } else {
                btnToggleInterpolation.classList.remove('active');
                btnToggleInterpolation.innerText = 'Off';
            }
            send('toggleInterpolation', interpolationEnabled.toString());
        });
    }

    // Subtitle Override toggle
    let subOverrideVisible = false;
    document.getElementById('btnToggleSubOverride')?.addEventListener('click', e => {
        e.stopPropagation();
        subOverrideVisible = !subOverrideVisible;
        const btn = document.getElementById('btnToggleSubOverride');
        if (subOverrideVisible) {
            btn.classList.add('active');
            btn.innerText = 'On';
        } else {
            btn.classList.remove('active');
            btn.innerText = 'Off';
        }
        send('setSubtitleOverrideEnabled', subOverrideVisible);
    });

    // Reset Subtitles
    document.getElementById('btnResetSubtitles')?.addEventListener('click', e => {
        e.stopPropagation();
        send('resetSubtitleSettings');
        // Instantly reset UI inputs to match defaults
        document.getElementById('subSizeSlider').value = 45;
        document.getElementById('subSizeVal').innerText = '45';
        document.getElementById('subBgSlider').value = 0;
        document.getElementById('subBgVal').innerText = '0';
        document.getElementById('subBorderSizeSlider').value = 3;
        document.getElementById('subBorderSizeVal').innerText = '3';
        document.getElementById('subShadowOffsetSlider').value = 0;
        document.getElementById('subShadowOffsetVal').innerText = '0';
        document.getElementById('subBlurSlider').value = 0;
        document.getElementById('subBlurVal').innerText = '0';
        document.getElementById('subPosSlider').value = 100;
        document.getElementById('subPosVal').innerText = '100';
        
        document.querySelectorAll('.border-dot').forEach(d => d.classList.remove('active'));
        document.querySelector('.border-dot[data-color="#000000"]')?.classList.add('active');
        
        document.querySelectorAll('.shadow-dot').forEach(d => d.classList.remove('active'));
        document.querySelector('.shadow-dot[data-color="#00000000"]')?.classList.add('active');
        
        document.getElementById('btnSubBold')?.classList.remove('active');
        document.getElementById('btnSubItalic')?.classList.remove('active');
        
        const fontInput = document.getElementById('subFontInput');
        if (fontInput) fontInput.value = '';

        subOverrideVisible = false;
        const btnOverride = document.getElementById('btnToggleSubOverride');
        if (btnOverride) {
            btnOverride.classList.remove('active');
            btnOverride.innerText = 'Off';
        }
    });

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

    // Keyboard Shortcuts
    // Prevent UI zooming
    document.addEventListener('wheel', e => { if (e.ctrlKey) e.preventDefault(); }, { passive: false });

    document.addEventListener('keydown', e => {
        if (e.ctrlKey && (e.key === '=' || e.key === '-' || e.key === '0')) { e.preventDefault(); return; }
        if (e.target.closest('input,textarea,[contenteditable]')) return;
        if (e.metaKey || e.ctrlKey || e.altKey) return;
        if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
        switch (e.code) {
            case 'Space': case 'KeyK': e.preventDefault(); send('togglePlay'); break;
            case 'KeyF': send('toggleFullscreen'); break;
            case 'KeyM': send('toggleMute'); break;
            case 'KeyI': if (e.shiftKey) { e.preventDefault(); document.getElementById('btnToggleStats').click(); } break;
            case 'ArrowLeft': case 'KeyJ': e.preventDefault(); doRelativeSeek(-10000); break;
            case 'ArrowRight': case 'KeyL': e.preventDefault(); doRelativeSeek(10000); break;
            case 'Escape': closeAllPanels(); break;
        }
    });

    // Video Ended Overlay Logic
    const videoEndedOverlay = document.getElementById('videoEndedOverlay');
    const videoEndedSubtext = document.getElementById('videoEndedSubtext');
    const btnNextEpisode = document.getElementById('btnNextEpisode');
    const btnReplay = document.getElementById('btnReplay');
    const btnExitPlayer = document.getElementById('btnExitPlayer');
    
    let endCountdownTimer = null;
    let currentEndCountdown = 5;

    window.showVideoEnded = (hasNextEpisode, autoPlayEnabled) => {
        closeAllPanels();
        document.getElementById('overlay').style.opacity = '0';
        videoEndedOverlay.style.display = 'flex';
        evaluateUIStates();
        
        if (endCountdownTimer) clearInterval(endCountdownTimer);
        
        if (hasNextEpisode) {
            btnNextEpisode.style.display = 'block';
            
            if (autoPlayEnabled) {
                currentEndCountdown = 5;
                btnNextEpisode.innerText = `Next Episode (${currentEndCountdown})`;
                videoEndedSubtext.innerText = 'Playing next episode soon...';
                
                endCountdownTimer = setInterval(() => {
                    currentEndCountdown--;
                    if (currentEndCountdown <= 0) {
                        triggerNextEpisode();
                    } else {
                        btnNextEpisode.innerText = `Next Episode (${currentEndCountdown})`;
                    }
                }, 1000);
            } else {
                btnNextEpisode.innerText = `Next Episode`;
                videoEndedSubtext.innerText = 'Autoplay is disabled.';
            }
            
            btnNextEpisode.onclick = () => {
                triggerNextEpisode();
            };
        } else {
            btnNextEpisode.style.display = 'none';
            videoEndedSubtext.innerText = 'All episodes watched.';
        }
        
        btnReplay.onclick = () => {
            if (endCountdownTimer) clearInterval(endCountdownTimer);
            send('hideVideoEnded', '1');
            videoEndedOverlay.style.display = 'none';
            evaluateUIStates();
            document.getElementById('overlay').style.opacity = '';
            forceShowLoading();
            send('replayEpisode');
        };
        
        btnExitPlayer.onclick = () => {
            if (endCountdownTimer) clearInterval(endCountdownTimer);
            triggerExit();
        };
    };

    // Close overlays when clicking outside
    videoEndedOverlay.addEventListener('click', e => {
        if (e.target === videoEndedOverlay) {
            // Keep it open
        }
    });

    // Ready
    const notifyReady = () => { send('ui_ready'); };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', notifyReady);
    else notifyReady();
