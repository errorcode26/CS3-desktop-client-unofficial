/*
   chat.js — Local Party Chat Module (Enhanced)
   Works in two modes:
     1. Embedded: loaded into Player.html and given the existing
        Watch Together socket via ChatModule.setSocket(socket).
     2. Standalone: loaded into chat.html; creates its own socket.

   Server protocol (from sheldor1510/local-party-api):
     Client → Server:  socket.emit('send', 'text')
     Server → Client:  socket.on('receive', { message, name, pfp })
   The server relays to *everyone else* in the room; the sender must
   echo their own message locally.
   */
(function (window, document) {
    'use strict';

    const STYLE_ID = 'lp-chat-styles';

    const CSS = `
    /* Chat container */
    .lp-chat {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 0;
        background: transparent;
        color: #eaeaea;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        position: relative;
        overflow: hidden;
    }

    /*  Status bar if mistake potato */
    .lp-chat-status {
        font-size: 11px;
        font-weight: 500;
        letter-spacing: 0.02em;
        padding: 7px 14px;
        text-align: center;
        color: rgba(255,255,255,0.5);
        border-bottom: 1px solid rgba(255,255,255,0.06);
        background: rgba(0,0,0,0.18);
        flex-shrink: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
    }
    .lp-chat-status::before {
        content: '';
        display: inline-block;
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #888;
    }
    .lp-chat-status.online  { color: #4ade80; }
    .lp-chat-status.online::before  { background: #4ade80; box-shadow: 0 0 6px #4ade80; }
    .lp-chat-status.offline { color: #f87171; }
    .lp-chat-status.offline::before { background: #f87171; }
    .lp-chat-status.connecting { color: #fbbf24; }
    .lp-chat-status.connecting::before {
        background: #fbbf24;
        animation: lpChatPulse 1.2s ease-in-out infinite;
    }
    @keyframes lpChatPulse {
        0%, 100% { opacity: 1; }
        50%      { opacity: 0.3; }
    }

    /* Message list (scroll area) */
    .lp-chat-list {
        flex: 1 1 auto;
        min-height: 0;
        overflow-y: auto;
        overflow-x: hidden;
        padding: 14px 16px 8px;
        display: flex;
        flex-direction: column;
        gap: 12px;
        scroll-behavior: smooth;
        scrollbar-width: thin;
        scrollbar-color: rgba(255,255,255,0.18) transparent;
        overscroll-behavior: contain;
    }
    .lp-chat-list::-webkit-scrollbar { width: 8px; }
    .lp-chat-list::-webkit-scrollbar-track {
        background: transparent;
    }
    .lp-chat-list::-webkit-scrollbar-thumb {
        background: rgba(255,255,255,0.15);
        border-radius: 4px;
        border: 2px solid transparent;
        background-clip: content-box;
    }
    .lp-chat-list::-webkit-scrollbar-thumb:hover {
        background: rgba(255,255,255,0.3);
        background-clip: content-box;
    }

    .lp-chat-empty {
        color: rgba(255,255,255,0.3);
        font-size: 12.5px;
        text-align: center;
        padding: 40px 16px;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8px;
    }
    .lp-chat-empty::before {
        content: '💬';
        font-size: 28px;
        opacity: 0.5;
    }

    /* Day separators */
    .lp-chat-daysep {
        align-self: center;
        font-size: 10.5px;
        font-weight: 600;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: rgba(255,255,255,0.35);
        padding: 6px 12px;
        border-radius: 999px;
        background: rgba(255,255,255,0.05);
        border: 1px solid rgba(255,255,255,0.06);
        margin: 4px 0;
    }

    /* Message rows */
    .lp-chat-row {
        display: flex;
        flex-direction: column;
        max-width: 78%;
        gap: 3px;
        animation: lpChatFade 0.2s ease;
    }
    .lp-chat-row.self  { align-self: flex-end; align-items: flex-end; }
    .lp-chat-row.other { align-self: flex-start; align-items: flex-start; }
    .lp-chat-row.system {
        align-self: center;
        max-width: 100%;
    }
    @keyframes lpChatFade {
        from { opacity: 0; transform: translateY(6px); }
        to   { opacity: 1; transform: translateY(0); }
    }

    /* Header line (name + time)  */
    .lp-chat-meta {
        display: flex;
        align-items: baseline;
        gap: 8px;
        padding: 0 10px;
        font-size: 10.5px;
        line-height: 1;
        user-select: none;
    }
    .lp-chat-name {
        font-weight: 700;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: rgba(255,255,255,0.55);
    }
    .lp-chat-row.self .lp-chat-name {
        color: rgba(var(--accent-color-rgb, 74,158,255), 0.95);
    }
    .lp-chat-time {
        color: rgba(255,255,255,0.35);
        font-variant-numeric: tabular-nums;
        font-weight: 500;
    }

    /*  Bubble  */
    .lp-chat-bubble {
        padding: 8px 13px;
        border-radius: 16px;
        font-size: 13.5px;
        line-height: 1.45;
        word-wrap: break-word;
        overflow-wrap: anywhere;
        white-space: pre-wrap;
        background: rgba(255,255,255,0.07);
        border: 1px solid rgba(255,255,255,0.06);
        color: #eaeaea;
        transition: background 0.15s, border-color 0.15s;
    }
    .lp-chat-row.other:hover .lp-chat-bubble {
        background: rgba(255,255,255,0.09);
        border-color: rgba(255,255,255,0.1);
    }
    .lp-chat-row.self .lp-chat-bubble {
        background: linear-gradient(135deg,
            var(--accent-color, #4a9eff),
            rgba(var(--accent-color-rgb, 74,158,255), 0.85));
        color: #fff;
        border-color: transparent;
        box-shadow: 0 2px 8px rgba(var(--accent-color-rgb, 74,158,255), 0.25);
    }
    .lp-chat-row.self .lp-chat-bubble:hover {
        filter: brightness(1.05);
    }

    /* System messages  */
    .lp-chat-row.system .lp-chat-bubble {
        background: rgba(255,255,255,0.04);
        border: 1px solid rgba(255,255,255,0.06);
        color: rgba(255,255,255,0.45);
        font-size: 11.5px;
        font-style: italic;
        padding: 5px 12px;
        border-radius: 999px;
    }

    /* "New messages" pill (visible when scrolled up)  */
    .lp-chat-newpill {
        position: absolute;
        bottom: 78px;
        left: 50%;
        transform: translateX(-50%) translateY(12px);
        background: var(--accent-color, #4a9eff);
        color: #fff;
        font-size: 12px;
        font-weight: 600;
        padding: 6px 14px;
        border-radius: 999px;
        border: none;
        cursor: pointer;
        box-shadow: 0 6px 20px rgba(0,0,0,0.4);
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.2s ease, transform 0.2s ease;
        z-index: 5;
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-family: inherit;
    }
    .lp-chat-newpill.show {
        opacity: 1;
        pointer-events: auto;
        transform: translateX(-50%) translateY(0);
    }
    .lp-chat-newpill:hover {
        filter: brightness(1.1);
    }
    .lp-chat-newpill::after {
        content: '↓';
        font-size: 14px;
        line-height: 1;
    }

    /* Composer  */
    .lp-chat-form {
        display: flex;
        gap: 8px;
        padding: 12px 14px;
        border-top: 1px solid rgba(255,255,255,0.06);
        background: rgba(0,0,0,0.22);
        flex-shrink: 0;
        align-items: flex-end;
    }
    .lp-chat-input {
        flex: 1;
        background: rgba(0,0,0,0.4);
        border: 1px solid rgba(255,255,255,0.1);
        border-radius: 12px;
        padding: 10px 14px;
        color: #fff;
        font-size: 13.5px;
        font-family: inherit;
        line-height: 1.4;
        outline: none;
        transition: border-color 0.18s, box-shadow 0.18s, background 0.18s;
        user-select: text;
        min-width: 0;
        resize: none;
        max-height: 120px;
    }
    .lp-chat-input:focus {
        border-color: var(--accent-color, #4a9eff);
        box-shadow: 0 0 0 3px rgba(var(--accent-color-rgb, 74,158,255), 0.15);
        background: rgba(0,0,0,0.55);
    }
    .lp-chat-input::placeholder { color: rgba(255,255,255,0.3); }
    .lp-chat-input:disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }

    .lp-chat-send {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 42px;
        height: 42px;
        border-radius: 12px;
        border: none;
        background: var(--accent-color, #4a9eff);
        color: #fff;
        cursor: pointer;
        transition: filter 0.15s, transform 0.08s, box-shadow 0.15s;
        flex-shrink: 0;
        padding: 0;
        box-shadow: 0 2px 10px rgba(var(--accent-color-rgb, 74,158,255), 0.35);
    }
    .lp-chat-send svg { display: block; }
    .lp-chat-send:hover:not(:disabled) {
        filter: brightness(1.12);
        box-shadow: 0 4px 14px rgba(var(--accent-color-rgb, 74,158,255), 0.5);
    }
    .lp-chat-send:active:not(:disabled) { transform: scale(0.94); }
    .lp-chat-send:disabled {
        opacity: 0.35;
        cursor: not-allowed;
        box-shadow: none;
        filter: none;
    }
    `;

    function injectStyles() {
        if (document.getElementById(STYLE_ID)) return;
        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = CSS;
        document.head.appendChild(style);
    }

    /*  Time helpers  */
    function formatTime(ts) {
        const d = new Date(ts || Date.now());
        let h = d.getHours();
        const m = d.getMinutes();
        const ampm = h >= 12 ? 'PM' : 'AM';
        h = h % 12 || 12;
        return h + ':' + String(m).padStart(2, '0') + ' ' + ampm;
    }

    function dayLabel(ts) {
        const d = new Date(ts);
        const now = new Date();
        const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const target = new Date(d.getFullYear(), d.getMonth(), d.getDate());
        const diffDays = Math.round((today - target) / 86400000);

        if (diffDays === 0) return 'Today';
        if (diffDays === 1) return 'Yesterday';
        if (diffDays < 7) {
            return d.toLocaleDateString(undefined, { weekday: 'long' });
        }
        return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
    }

    function sameDay(a, b) {
        const da = new Date(a), db = new Date(b);
        return da.getFullYear() === db.getFullYear()
            && da.getMonth() === db.getMonth()
            && da.getDate() === db.getDate();
    }

    const ChatModule = {
        socket: null,
        roomCode: null,
        userName: 'Guest',
        container: null,
        listEl: null,
        inputEl: null,
        sendBtn: null,
        statusEl: null,
        newPillEl: null,
        messages: [],
        _boundReceive: null,
        _boundConnect: null,
        _boundDisconnect: null,
        _maxMessages: 500,
        _showStatus: false,
        _shouldAutoScroll: true,
        _pendingNewCount: 0,

        init(opts) {
            opts = opts || {};
            this._showStatus = !!opts.showStatus;
            if (opts.container) this.attach(opts.container);
            if (opts.socket)   this.setSocket(opts.socket);
            if (opts.roomCode) this.roomCode = String(opts.roomCode).toUpperCase();
            if (opts.userName) this.userName = String(opts.userName);
            this._render();
            return this;
        },

        attach(containerEl) {
            if (typeof containerEl === 'string') containerEl = document.querySelector(containerEl);
            if (!containerEl) { console.warn('[Chat] container not found'); return this; }
            this.container = containerEl;

            containerEl.innerHTML =
                '<div class="lp-chat">' +
                    (this._showStatus ? '<div class="lp-chat-status connecting" id="lpChatStatus">Connecting…</div>' : '') +
                    '<div class="lp-chat-list" id="lpChatList">' +
                        '<div class="lp-chat-empty">No messages yet — say hi!</div>' +
                    '</div>' +
                    '<button type="button" class="lp-chat-newpill" id="lpChatNewPill" aria-label="Scroll to latest">New messages</button>' +
                    '<form class="lp-chat-form" id="lpChatForm" autocomplete="off">' +
                        '<input type="text" id="lpChatInput" class="lp-chat-input" placeholder="Type a message…" maxlength="500" />' +
                        '<button type="submit" class="lp-chat-send" title="Send (Enter)">' +
                            '<svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">' +
                                '<path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>' +
                            '</svg>' +
                        '</button>' +
                    '</form>' +
                '</div>';

            this.listEl    = containerEl.querySelector('#lpChatList');
            this.inputEl   = containerEl.querySelector('#lpChatInput');
            this.sendBtn   = containerEl.querySelector('.lp-chat-send');
            this.statusEl  = containerEl.querySelector('#lpChatStatus');
            this.newPillEl = containerEl.querySelector('#lpChatNewPill');

            const form = containerEl.querySelector('#lpChatForm');
            form.addEventListener('submit', (e) => { e.preventDefault(); this._submit(); });

            this.inputEl.addEventListener('keydown',   (e) => e.stopPropagation());
            this.inputEl.addEventListener('keypress',  (e) => e.stopPropagation());
            this.inputEl.addEventListener('keyup',     (e) => e.stopPropagation());

            // Track scroll position to decide whether to auto-scroll
            this.listEl.addEventListener('scroll', () => this._onScroll(), { passive: true });

            // Clicking the "new messages" pill scrolls to bottom
            if (this.newPillEl) {
                this.newPillEl.addEventListener('click', () => {
                    this._scrollToBottom(true);
                    this._clearNewPill();
                });
            }

            this._render();
            this._updateStatus();
            return this;
        },

        setSocket(socket) {
            if (this.socket) {
                try { if (this._boundReceive)    this.socket.off('receive',    this._boundReceive); }    catch (_) {}
                try { if (this._boundConnect)    this.socket.off('connect',    this._boundConnect); }    catch (_) {}
                try { if (this._boundDisconnect) this.socket.off('disconnect', this._boundDisconnect); } catch (_) {}
            }
            this.socket = socket || null;

            if (socket) {
                this._boundReceive    = (d) => this._onReceive(d);
                this._boundConnect    = ()  => this._updateStatus();
                this._boundDisconnect = ()  => this._updateStatus();
                socket.on('receive',    this._boundReceive);
                socket.on('connect',    this._boundConnect);
                socket.on('disconnect', this._boundDisconnect);
            }
            this._updateStatus();
            return this;
        },

        setRoom(roomCode, userName) {
            const newCode = roomCode ? String(roomCode).toUpperCase() : null;
            const changed = this.roomCode !== newCode;
            this.roomCode = newCode;
            if (userName) this.userName = String(userName);
            if (changed) {
                this.messages = [];
                this._pendingNewCount = 0;
                this._shouldAutoScroll = true;
                this._render();
                this._clearNewPill();
            }
            this._updateStatus();
            return this;
        },

        send(text) {
            text = (text || '').trim();
            if (!text) return false;

            if (!this.socket || !this.socket.connected) {
                this._addSystemMessage('Not connected to chat server.');
                return false;
            }
            if (!this.roomCode) {
                this._addSystemMessage('Join a room first.');
                return false;
            }

            try {
                this.socket.emit('send', text);
            } catch (e) {
                console.warn('[Chat] emit failed:', e);
                this._addSystemMessage('Failed to send.');
                return false;
            }

            this._addMessage({
                name: this.userName,
                message: text,
                self: true,
                time: Date.now()
            });
            return true;
        },

        clear() {
            this.messages = [];
            this._pendingNewCount = 0;
            this._render();
            this._clearNewPill();
            return this;
        },

        destroy() {
            this.setSocket(null);
            if (this.container) this.container.innerHTML = '';
            this.container = null;
            this.listEl = null;
            this.inputEl = null;
            this.sendBtn = null;
            this.statusEl = null;
            this.newPillEl = null;
            this.messages = [];
            this.roomCode = null;
            this._pendingNewCount = 0;
            return this;
        },

        /* Internal  */

        _submit() {
            if (!this.inputEl) return;
            const text = this.inputEl.value;
            if (this.send(text)) this.inputEl.value = '';
            this.inputEl.focus();
            // Force scroll when we send our own message
            this._scrollToBottom(true);
        },

        _onReceive(d) {
            if (!d || typeof d.message !== 'string') return;
            this._addMessage({
                name: d.name || 'Anonymous',
                message: d.message,
                pfp: d.pfp || '',
                self: false,
                time: Date.now()
            });
        },

        _addSystemMessage(text) {
            this._addMessage({ system: true, message: text, time: Date.now() });
        },

        _addMessage(m) {
            const wasAtBottom = this._shouldAutoScroll;
            this.messages.push(m);
            if (this.messages.length > this._maxMessages) {
                this.messages.splice(0, this.messages.length - this._maxMessages);
            }
            this._render();
            if (wasAtBottom || m.self) {
                this._scrollToBottom(false);
                this._clearNewPill();
            } else {
                this._pendingNewCount++;
                this._showNewPill();
            }
        },

        _render() {
            if (!this.listEl) return;

            if (!this.messages.length) {
                this.listEl.innerHTML = '<div class="lp-chat-empty">No messages yet — say hi!</div>';
                return;
            }

            const esc = (s) => String(s)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;');

            let html = '';
            let lastDay = null;

            for (const m of this.messages) {
                // Insert day separator when the day changes
                const msgDay = new Date(m.time || Date.now()).setHours(0, 0, 0, 0);
                if (lastDay !== msgDay) {
                    html += '<div class="lp-chat-daysep">' + esc(dayLabel(m.time)) + '</div>';
                    lastDay = msgDay;
                }

                if (m.system) {
                    html += '<div class="lp-chat-row system">' +
                                '<div class="lp-chat-bubble">' + esc(m.message) + '</div>' +
                            '</div>';
                    continue;
                }

                const cls = m.self ? 'self' : 'other';
                html += '<div class="lp-chat-row ' + cls + '">' +
                            '<div class="lp-chat-meta">' +
                                '<span class="lp-chat-name">' + esc(m.name) + '</span>' +
                                '<span class="lp-chat-time">' + esc(formatTime(m.time)) + '</span>' +
                            '</div>' +
                            '<div class="lp-chat-bubble">' + esc(m.message) + '</div>' +
                        '</div>';
            }

            this.listEl.innerHTML = html;
        },

        _onScroll() {
            if (!this.listEl) return;
            const el = this.listEl;
            const nearBottom = (el.scrollHeight - el.scrollTop - el.clientHeight) < 40;
            this._shouldAutoScroll = nearBottom;
            if (nearBottom) this._clearNewPill();
        },

        _scrollToBottom(smooth) {
            if (!this.listEl) return;
            const doScroll = () => {
                if (!this.listEl) return;
                if (smooth) {
                    this.listEl.scrollTo({ top: this.listEl.scrollHeight, behavior: 'smooth' });
                } else {
                    this.listEl.scrollTop = this.listEl.scrollHeight;
                }
                this._shouldAutoScroll = true;
            };
            // Defer to next frame so the DOM has time to lay out
            requestAnimationFrame(doScroll);
        },

        _showNewPill() {
            if (!this.newPillEl) return;
            if (this._pendingNewCount > 0) {
                this.newPillEl.textContent = this._pendingNewCount === 1
                    ? 'New message'
                    : this._pendingNewCount + ' new messages';
                this.newPillEl.classList.add('show');
            }
        },

        _clearNewPill() {
            this._pendingNewCount = 0;
            if (this.newPillEl) this.newPillEl.classList.remove('show');
        },

        _updateStatus() {
            const connected = !!(this.socket && this.socket.connected);
            const inRoom = !!this.roomCode;

            if (this.statusEl) {
                if (connected && inRoom) {
                    this.statusEl.textContent = 'Connected • Room ' + this.roomCode;
                    this.statusEl.className = 'lp-chat-status online';
                } else if (connected && !inRoom) {
                    this.statusEl.textContent = 'Connected • Not in a room';
                    this.statusEl.className = 'lp-chat-status';
                } else if (this.socket) {
                    this.statusEl.textContent = 'Connecting…';
                    this.statusEl.className = 'lp-chat-status connecting';
                } else {
                    this.statusEl.textContent = 'Disconnected';
                    this.statusEl.className = 'lp-chat-status offline';
                }
            }

            if (this.inputEl) {
                this.inputEl.disabled = !(connected && inRoom);
                this.inputEl.placeholder = (connected && inRoom)
                    ? 'Type a message…'
                    : (connected ? 'Join a room to chat' : 'Connecting…');
            }
            if (this.sendBtn) {
                this.sendBtn.disabled = !(connected && inRoom);
            }
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', injectStyles);
    } else {
        injectStyles();
    }

    window.ChatModule = ChatModule;
})(window, document);