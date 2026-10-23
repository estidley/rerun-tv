/**
 * Rerun Tizen Application — TunerEngine, TvController, Screens, and Samsung Remote Handling
 */
(function (global) {
    'use strict';

    const { Catalog, Resolver, Facets, validateDraft, createRules, GRID_MS, gridFloor, locate, guideBlocks, Playback } = global.RerunCore;
    const { Settings, RerunDb, PlexApi, ScheduleRepo, LibrarySync, uuid } = global.RerunStorage;
    const { NoiseBurst, StaticNoiseRenderer, CrtOverlay, VerticalRoll, PowerTransition } = global.RerunEffects;

    const EFFECTS = {
        STATIC: 'fx_static',
        SOUND: 'fx_sound',
        ROLL: 'fx_roll',
        POWER: 'fx_power',
    };

    function clockTime(ms) {
        const d = new Date(ms);
        let h = d.getHours();
        const m = String(d.getMinutes()).padStart(2, '0');
        h = h % 12 || 12;
        return `${h}:${m}`;
    }

    function describeItem(item) {
        if (!item) return "Coming up";
        if (item.kind === 'EPISODE') {
            const se = (item.season != null && item.episode != null) ? `  S${item.season}E${item.episode}` : '';
            return `${item.showTitle || item.title}${se}`;
        }
        return item.title + (item.year ? ` (${item.year})` : '');
    }

    function formatHours(ms) {
        const h = ms / 3600000;
        if (h < 1) return `${Math.floor(ms / 60000)} min of content`;
        return `${h.toFixed(1)} hours of content`;
    }

    function posterUrl(item) {
        if (!item || !item.thumb) return null;
        const base = Settings.serverBase;
        const token = Settings.serverToken || Settings.accountToken;
        if (!base || !token) return null;
        return `${base}/photo/:/transcode?width=480&height=720&minSize=1&upscale=1&url=${encodeURIComponent(item.thumb)}&X-Plex-Token=${encodeURIComponent(token)}`;
    }

    class TunerEngine {
        constructor(videoEl, repo, onStateChange) {
            this.video = videoEl;
            this.repo = repo;
            this.onStateChange = onStateChange;
            this.session = uuid();
            this.tuneSeq = 0;
            this.rolloverTimer = null;
            this.retryTimer = null;
            this._fallbackUrl = null;
            this.state = {
                channel: null,
                located: null,
                item: null,
                nextItem: null,
                phase: 'TUNING',
                tuneId: 0,
            };

            this.video.addEventListener('playing', () => {
                if (this.state.phase === 'TUNING') {
                    this._updateState({ phase: 'PLAYING' });
                }
            });

            this.video.addEventListener('ended', () => {
                if (this.state.phase === 'PLAYING') {
                    this._updateState({ phase: 'UP_NEXT' });
                }
            });

            this.video.addEventListener('error', () => {
                if (this._fallbackUrl) {
                    const fb = this._fallbackUrl;
                    this._fallbackUrl = null;
                    this.video.src = fb;
                    this.video.load();
                    this.video.play().catch(() => this._difficulties());
                    return;
                }
                this._difficulties();
            });
        }

        _updateState(patch) {
            this.state = Object.assign({}, this.state, patch);
            this.onStateChange(this.state);
        }

        async tune(channelId) {
            this._clearTimers();
            const seq = ++this.tuneSeq;
            const t0 = Date.now();
            const channel = await this.repo.channel(channelId);
            if (seq !== this.tuneSeq) return;

            this._updateState({ channel, located: null, item: null, nextItem: null, phase: 'TUNING', tuneId: seq });
            if (!channel) return this._difficulties();
            Settings.lastChannel = channelId;

            try {
                await this.repo.ensureChannel(channelId, t0);
                if (seq !== this.tuneSeq) return;

                const now = Date.now();
                const entries = await this.repo.entries(channelId, now - 1, now + 6 * 3600000);
                if (seq !== this.tuneSeq) return;

                const loc = locate(entries, now);
                if (!loc) return this._difficulties();

                const item = await this.repo.item(loc.entry.itemKey);
                let nextItem = null;
                if (loc.next) {
                    if (loc.next.kind === 'PROGRAM') {
                        nextItem = await this.repo.item(loc.next.itemKey);
                    } else {
                        const after = entries.find(e => e.startMs >= loc.next.endMs && e.kind === 'PROGRAM');
                        if (after) nextItem = await this.repo.item(after.itemKey);
                    }
                }
                if (seq !== this.tuneSeq) return;

                this._updateState({ located: loc, item, nextItem });
                this._scheduleRollover(channelId, loc.entry.endMs);

                if (loc.entry.kind === 'FILLER') {
                    this.stopVideo();
                    this._updateState({ phase: 'UP_NEXT' });
                    return;
                }

                const base = Settings.serverBase;
                const token = Settings.serverToken || Settings.accountToken;
                if (!base || !token) return this._difficulties();

                const lag = Date.now() - now;
                const plan = Playback.planPlayback(item, base, token, Settings.clientId, this.session, loc.offsetMs + lag);
                if (plan.kind === 'UP_NEXT') {
                    this.stopVideo();
                    this._updateState({ phase: 'UP_NEXT' });
                    return;
                }

                this._fallbackUrl = plan.fallbackUrl || null;
                this.video.src = plan.url;
                this.video.load();
                if (plan.kind === 'DIRECT' && plan.startMs > 0) {
                    const targetSec = (plan.startMs + (Date.now() - now - lag)) / 1000;
                    const seekOnce = () => {
                        this.video.removeEventListener('loadedmetadata', seekOnce);
                        try { this.video.currentTime = targetSec; } catch (_) { }
                    };
                    this.video.addEventListener('loadedmetadata', seekOnce);
                }
                await this.video.play();
            } catch (_) {
                if (seq === this.tuneSeq) this._difficulties();
            }
        }

        _scheduleRollover(channelId, endMs) {
            const delay = Math.max(0, endMs - Date.now());
            this.rolloverTimer = setTimeout(() => this.tune(channelId), delay);
        }

        _difficulties() {
            this.stopVideo();
            this._updateState({ phase: 'DIFFICULTIES' });
            const id = this.state.channel && this.state.channel.id;
            if (!id) return;
            this._clearTimers();
            this.retryTimer = setTimeout(() => this.tune(id), 15000);
        }

        _clearTimers() {
            if (this.rolloverTimer) clearTimeout(this.rolloverTimer);
            if (this.retryTimer) clearTimeout(this.retryTimer);
            this.rolloverTimer = null;
            this.retryTimer = null;
        }

        stopVideo() {
            this._fallbackUrl = null;
            try {
                this.video.pause();
                this.video.removeAttribute('src');
                this.video.load();
            } catch (_) { }
        }

        stop() {
            this._clearTimers();
            this.stopVideo();
        }
    }

    class App {
        constructor() {
            this.db = new RerunDb();
            this.repo = new ScheduleRepo(this.db);
            this.channels = [];
            this.currentId = null;
            this.previousId = null;
            this.screen = Settings.isSignedIn ? 'PLAYER' : 'ONBOARDING';
            this.editorChannelId = null;
            this.guideClosedAt = 0;
            this.bannerUntil = 0;
            this.osdUntil = 0;
            this.lastTuneId = 0;

            this.stageEl = document.getElementById('stage');
            this.playerScreenEl = document.getElementById('screen-player');
            this.modalScreenEl = document.getElementById('screen-modal');
            this.videoEl = document.getElementById('video-player');
            this.staticCanvas = document.getElementById('static-canvas');
            this.rollCanvas = document.getElementById('roll-canvas');
            this.crtCanvas = document.getElementById('crt-canvas');
            this.powerCanvas = document.getElementById('power-canvas');

            this.staticRenderer = new StaticNoiseRenderer(this.staticCanvas);
            this.engine = new TunerEngine(this.videoEl, this.repo, (st) => this.renderPlayerState(st));

            this._keyHandler = null;
            this._okDownAt = 0;
            this._okLongFired = false;
        }

        async init() {
            this._setupViewportScale();
            window.addEventListener('resize', () => this._setupViewportScale());
            this._registerTizenKeys();
            this._setupGlobalKeys();

            CrtOverlay.draw(this.crtCanvas, Settings.crtLevel);
            if (Settings.flag(EFFECTS.POWER, true)) {
                PowerTransition.powerOn(this.powerCanvas);
            }

            setInterval(() => this._tickPlayerClock(), 250);

            if (Settings.isSignedIn && Settings.serverBase) {
                await this.startWatching();
            } else {
                this.go('ONBOARDING');
            }
        }

        _setupViewportScale() {
            const sx = window.innerWidth / 1920;
            const sy = window.innerHeight / 1080;
            this.stageEl.style.transform = `scale(${sx}, ${sy})`;
        }

        _registerTizenKeys() {
            try {
                if (window.tizen && window.tizen.tvinputdevice) {
                    const keys = [
                        'ChannelUp', 'ChannelDown', 'PreviousChannel', 'Info', 'Guide',
                        'Menu', 'Tools', 'ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue',
                        'MediaPlay', 'MediaPause', 'MediaStop', 'Exit'
                    ];
                    for (const k of keys) {
                        try { window.tizen.tvinputdevice.registerKey(k); } catch (_) { }
                    }
                }
            } catch (_) { }
        }

        getEffects() {
            return {
                static: Settings.flag(EFFECTS.STATIC, true),
                sound: Settings.flag(EFFECTS.SOUND, true),
                roll: Settings.flag(EFFECTS.ROLL, true),
                power: Settings.flag(EFFECTS.POWER, true),
            };
        }

        cycleCrt() {
            const order = ['OFF', 'SUBTLE', 'FULL'];
            const next = order[(order.indexOf(Settings.crtLevel) + 1) % order.length];
            Settings.crtLevel = next;
            CrtOverlay.draw(this.crtCanvas, next);
            if (this.screen === 'SETTINGS') this.renderSettings();
        }

        setCrt(level) {
            Settings.crtLevel = level;
            CrtOverlay.draw(this.crtCanvas, level);
            if (this.screen === 'SETTINGS') this.renderSettings();
        }

        setEffect(key, on) {
            Settings.setFlag(key, on);
            this.renderPlayerState(this.engine.state);
            if (this.screen === 'SETTINGS') this.renderSettings();
        }

        setAllEffects(on) {
            Object.values(EFFECTS).forEach(k => Settings.setFlag(k, on));
            this.setCrt(on ? 'SUBTLE' : 'OFF');
            this.renderPlayerState(this.engine.state);
            if (this.screen === 'SETTINGS') this.renderSettings();
        }

        async startWatching() {
            if (!Settings.serverBase) {
                this.go('ONBOARDING');
                return;
            }
            this.repo.invalidateLibrary();
            this.channels = await this.repo.activeChannels();
            const last = Settings.lastChannel;
            const pick = this.channels.find(c => c.id === last) || this.channels[0] || null;
            this.go('PLAYER');
            if (pick) {
                this.tuneTo(pick.id, false);
            } else {
                this.renderPlayerState(this.engine.state);
            }
        }

        async tuneTo(id, sound = true) {
            if (id !== this.currentId) this.previousId = this.currentId;
            this.currentId = id;
            const fx = this.getEffects();
            if (sound && fx.sound) NoiseBurst.play();
            this.osdUntil = Date.now() + 3000;
            this.showBanner(5000);
            await this.engine.tune(id);
        }

        channelUp() { this._stepChannel(+1); }
        channelDown() { this._stepChannel(-1); }

        _stepChannel(dir) {
            if (this.channels.length === 0) return;
            const idx = this.channels.findIndex(c => c.id === this.currentId);
            const nextIdx = idx < 0 ? 0 : ((idx + dir) % this.channels.length + this.channels.length) % this.channels.length;
            this.tuneTo(this.channels[nextIdx].id, true);
        }

        lastChannel() {
            if (this.previousId) this.tuneTo(this.previousId, true);
        }

        showBanner(ms = 5000) {
            this.bannerUntil = Date.now() + ms;
            this._tickPlayerClock();
        }

        closeGuideWithBack() {
            this.guideClosedAt = Date.now();
            this.go('PLAYER');
        }

        backFromPlayer() {
            if (Date.now() - this.guideClosedAt < 1500) {
                this.exitApp();
            } else {
                this.go('GUIDE');
            }
        }

        exitApp() {
            const finish = () => {
                this.engine.stop();
                try {
                    if (window.tizen && window.tizen.application) {
                        window.tizen.application.getCurrentApplication().exit();
                        return;
                    }
                } catch (_) { }
                window.close();
            };
            if (this.getEffects().power) {
                PowerTransition.powerOff(this.powerCanvas, finish);
            } else {
                finish();
            }
        }

        go(screen, param = null) {
            this.screen = screen;
            this.editorChannelId = param;
            this._keyHandler = null;

            if (screen === 'PLAYER') {
                this.playerScreenEl.style.display = 'block';
                this.modalScreenEl.style.display = 'none';
                this.modalScreenEl.innerHTML = '';
                this.renderPlayerState(this.engine.state);
            } else {
                this.playerScreenEl.style.display = 'block';
                this.modalScreenEl.style.display = 'block';
                if (screen === 'ONBOARDING') this.renderOnboarding();
                else if (screen === 'GUIDE') this.renderGuide();
                else if (screen === 'CHANNELS') this.renderChannels();
                else if (screen === 'EDITOR') this.renderEditor(param);
                else if (screen === 'SETTINGS') this.renderSettings();
            }
        }

        renderPlayerState(st) {
            const fx = this.getEffects();
            if (st.tuneId !== this.lastTuneId) {
                this.lastTuneId = st.tuneId;
                this.osdUntil = Date.now() + 3000;
                if (fx.roll && st.phase === 'TUNING') {
                    VerticalRoll.play(this.rollCanvas);
                }
            }

            const isTuning = st.phase === 'TUNING';
            this.staticRenderer.setActive(isTuning && fx.static);

            const upNextEl = document.getElementById('upnext-card');
            const diffEl = document.getElementById('difficulties-card');
            const emptyEl = document.getElementById('empty-channels-card');

            upNextEl.style.display = st.phase === 'UP_NEXT' ? 'flex' : 'none';
            diffEl.style.display = st.phase === 'DIFFICULTIES' ? 'flex' : 'none';
            emptyEl.style.display = (this.channels.length === 0 && !st.channel) ? 'flex' : 'none';

            if (st.phase === 'UP_NEXT') {
                const entry = st.located && st.located.entry;
                const nextItem = (entry && entry.kind === 'FILLER') ? st.nextItem : (st.item || st.nextItem);
                document.getElementById('upnext-ch').textContent = st.channel ? st.channel.name.toUpperCase() : '';
                document.getElementById('upnext-title').textContent = describeItem(nextItem);
                const pUrl = posterUrl(nextItem);
                const posterEl = document.getElementById('upnext-poster');
                if (pUrl) {
                    posterEl.src = pUrl;
                    posterEl.style.display = 'block';
                } else {
                    posterEl.style.display = 'none';
                }
            }

            if (st.channel) {
                document.getElementById('osd-num').textContent = String(st.channel.number);
                document.getElementById('osd-name').textContent = st.channel.name.toUpperCase();
                document.getElementById('banner-num').textContent = String(st.channel.number);
                document.getElementById('banner-ch').textContent = st.channel.name;
            }

            const entry = st.located && st.located.entry;
            const curItem = (entry && entry.kind === 'PROGRAM') ? st.item : null;
            document.getElementById('banner-title').textContent = describeItem(curItem);
            const bits = [];
            if (curItem && curItem.kind === 'EPISODE' && curItem.title) bits.push(curItem.title);
            if (curItem && curItem.contentRating) bits.push(curItem.contentRating);
            if (entry && entry.kind === 'PROGRAM') bits.push(`ends ${clockTime(entry.endMs)}`);
            document.getElementById('banner-meta').textContent = bits.join('  ·  ');
            document.getElementById('banner-next').textContent = st.nextItem ? `UP NEXT  ${describeItem(st.nextItem)}` : '';

            this._tickPlayerClock();
        }

        _tickPlayerClock() {
            const now = Date.now();
            const st = this.engine.state;
            const osdEl = document.getElementById('channel-osd');
            const bannerEl = document.getElementById('info-banner');

            const showOsd = !!st.channel && (now < this.osdUntil || now < this.bannerUntil);
            osdEl.classList.toggle('hidden', !showOsd);

            const showBanner = !!st.channel && now < this.bannerUntil && st.phase !== 'DIFFICULTIES';
            bannerEl.classList.toggle('hidden', !showBanner);

            if (st.phase === 'UP_NEXT' && st.located && st.located.entry) {
                const left = Math.max(0, Math.floor((st.located.entry.endMs - now) / 1000));
                const mins = Math.floor(left / 60);
                const secs = String(left % 60).padStart(2, '0');
                document.getElementById('upnext-countdown').textContent = `${mins}:${secs}`;
            }
        }

        _setupGlobalKeys() {
            const normalizeKey = (e) => {
                const code = e.keyCode || e.which;
                if (code === 38 || e.key === 'ArrowUp') return 'UP';
                if (code === 40 || e.key === 'ArrowDown') return 'DOWN';
                if (code === 37 || e.key === 'ArrowLeft') return 'LEFT';
                if (code === 39 || e.key === 'ArrowRight') return 'RIGHT';
                if (code === 13 || code === 65376 || e.key === 'Enter') return 'OK';
                if (code === 10009 || code === 27 || code === 8 || e.key === 'Escape' || e.key === 'Backspace') return 'BACK';
                if (code === 427 || e.key === 'ChannelUp' || e.key === 'PageUp') return 'CH_UP';
                if (code === 428 || e.key === 'ChannelDown' || e.key === 'PageDown') return 'CH_DOWN';
                if (code === 10190 || e.key === 'PreviousChannel') return 'LAST_CH';
                if (code === 457 || e.key === 'Info' || e.key === 'i') return 'INFO';
                if (code === 458 || code === 18 || code === 10133 || code === 403 || e.key === 'Guide' || e.key === 'ContextMenu' || e.key === 'm' || e.key === 'g') return 'MENU';
                return null;
            };

            window.addEventListener('keydown', (e) => {
                if (document.activeElement && document.activeElement.tagName === 'INPUT') {
                    const k = normalizeKey(e);
                    if (k === 'UP' || k === 'DOWN' || k === 'OK') {
                        document.activeElement.blur();
                    } else if (e.keyCode === 10009 || e.key === 'Escape') {
                        document.activeElement.blur();
                        e.preventDefault();
                        return;
                    } else {
                        return;
                    }
                }

                const k = normalizeKey(e);
                if (!k) return;

                if (this.screen === 'PLAYER') {
                    e.preventDefault();
                    if (k === 'OK') {
                        if (!e.repeat && this._okDownAt === 0) {
                            this._okDownAt = Date.now();
                            this._okLongFired = false;
                        } else if (!this._okLongFired && this._okDownAt > 0 && Date.now() - this._okDownAt >= 2000) {
                            this._okLongFired = true;
                            this.cycleCrt();
                        }
                        return;
                    }
                    if (e.repeat) return;
                    if (k === 'UP' || k === 'CH_UP') this.channelUp();
                    else if (k === 'DOWN' || k === 'CH_DOWN') this.channelDown();
                    else if (k === 'LEFT' || k === 'LAST_CH') this.lastChannel();
                    else if (k === 'RIGHT' || k === 'INFO') this.showBanner();
                    else if (k === 'MENU') this.go('GUIDE');
                    else if (k === 'BACK') this.backFromPlayer();
                    return;
                }

                if (this._keyHandler) {
                    e.preventDefault();
                    this._keyHandler(k, e);
                }
            });

            window.addEventListener('keyup', (e) => {
                const k = normalizeKey(e);
                if (this.screen === 'PLAYER' && k === 'OK') {
                    e.preventDefault();
                    if (!this._okLongFired && this._okDownAt > 0) {
                        this.showBanner();
                    }
                    this._okDownAt = 0;
                    this._okLongFired = false;
                }
            });
        }

        // ==================== ONBOARDING SCREEN ====================
        async renderOnboarding() {
            let cancelled = false;
            const setBody = (html) => {
                if (this.screen !== 'ONBOARDING') return;
                this.modalScreenEl.innerHTML = `
                    <div class="tv-frame">
                        <div class="tv-frame-header">
                            <div class="text-osd">RERUN</div>
                            <div class="text-title" style="color:var(--yellow);">SETUP</div>
                        </div>
                        <div class="tv-frame-body" id="onboarding-body">${html}</div>
                        <div class="tv-frame-footer text-caption">Rerun only reads your library. Nothing you watch here is reported to Plex.</div>
                    </div>
                `;
            };

            const chooseServer = async (server) => {
                setBody(`
                    <div style="display:flex; flex-direction:column; gap:16px;">
                        <div class="text-title">Tuning in your library…</div>
                        <div class="text-body">Connecting to ${server.name}</div>
                    </div>
                `);
                const base = await PlexApi.pickConnection(server);
                if (!base) {
                    showFailed(`Couldn't reach ${server.name}. Is it on and on this network?`);
                    return;
                }
                Settings.serverBase = base;
                Settings.serverName = server.name;
                Settings.serverId = server.clientId;
                Settings.serverToken = server.accessToken || Settings.accountToken;

                await LibrarySync.sync(this.repo, (p) => {
                    let barHtml = '';
                    if (p.total > 0) {
                        const pct = Math.min(100, Math.max(0, Math.floor((p.done * 100) / p.total)));
                        const filled = Math.floor(pct / 4);
                        const bar = '█'.repeat(filled) + '░'.repeat(25 - filled);
                        barHtml = `<div class="text-osd" style="font-size:28px;">${bar}  ${pct}%</div>`;
                    }
                    setBody(`
                        <div style="display:flex; flex-direction:column; gap:16px;">
                            <div class="text-title">Tuning in your library…</div>
                            <div class="text-body">${p.stage}</div>
                            ${barHtml}
                        </div>
                    `);
                });
                await this.startWatching();
            };

            const showFailed = (msg) => {
                setBody(`
                    <div style="display:flex; flex-direction:column; gap:16px; align-items:flex-start;">
                        <div class="text-title">${msg}</div>
                        <div class="tv-btn focused" id="btn-retry"><div class="tv-btn-label">Try again</div></div>
                    </div>
                `);
                document.getElementById('btn-retry').onclick = () => this.renderOnboarding();
                this._keyHandler = (k) => {
                    if (k === 'OK') this.renderOnboarding();
                };
            };

            setBody(`<div class="text-title">Contacting Plex…</div>`);

            try {
                if (!Settings.accountToken) {
                    const pin = await PlexApi.createPin();
                    setBody(`
                        <div style="display:flex; flex-direction:column; gap:16px;">
                            <div class="text-title">On your phone or computer, go to</div>
                            <div class="text-osd" style="color:var(--cyan);">plex.tv/link</div>
                            <div class="text-title">and enter this code:</div>
                            <div class="text-huge">${pin.code}</div>
                        </div>
                    `);
                    this._keyHandler = (k) => { if (k === 'BACK') this.exitApp(); };

                    let token = null;
                    while (!token && this.screen === 'ONBOARDING' && !cancelled) {
                        await new Promise(r => setTimeout(r, 2000));
                        const checked = await PlexApi.checkPin(pin.id);
                        token = checked.authToken;
                    }
                    if (!token) return;
                    Settings.accountToken = token;
                }

                const servers = await PlexApi.servers(Settings.accountToken);
                if (servers.length === 0) {
                    showFailed("No Plex servers found on this account.");
                } else if (servers.length === 1) {
                    await chooseServer(servers[0]);
                } else {
                    let focusIdx = 0;
                    const renderServers = () => {
                        const listHtml = servers.map((s, i) => `
                            <div class="tv-btn ${i === focusIdx ? 'focused' : ''}" data-idx="${i}" style="width:640px;">
                                <div class="tv-btn-label">${s.name}</div>
                            </div>
                        `).join('');
                        setBody(`
                            <div style="display:flex; flex-direction:column; gap:16px;">
                                <div class="text-title">Which Plex server?</div>
                                <div style="display:flex; flex-direction:column; gap:12px;">${listHtml}</div>
                            </div>
                        `);
                        this.modalScreenEl.querySelectorAll('.tv-btn').forEach(el => {
                            el.onclick = () => chooseServer(servers[Number(el.dataset.idx)]);
                        });
                    };
                    renderServers();
                    this._keyHandler = (k) => {
                        if (k === 'UP') { focusIdx = Math.max(0, focusIdx - 1); renderServers(); }
                        else if (k === 'DOWN') { focusIdx = Math.min(servers.length - 1, focusIdx + 1); renderServers(); }
                        else if (k === 'OK') chooseServer(servers[focusIdx]);
                    };
                }
            } catch (e) {
                showFailed(e.message || "Something went wrong");
            }
        }

        // ==================== GUIDE SCREEN ====================
        async renderGuide() {
            const WINDOW_MS = 3 * 3600000;
            const VISIBLE_ROWS = 7;
            const TIMELINE_W = 1920 - 96 - 230; // 1594px

            const now = Date.now();
            await this.repo.ensure(now);
            this.channels = await this.repo.activeChannels();
            const rows = this.channels;

            let windowStart = gridFloor(now);
            let focusRow = Math.max(0, rows.findIndex(r => r.id === this.currentId));
            let focusTime = now;
            let headerIndex = 0;
            let blocks = {};
            let items = {};

            const loadWindow = async () => {
                const all = await this.repo.allEntries(windowStart, windowStart + WINDOW_MS);
                blocks = {};
                const keys = new Set();
                for (const ch of rows) {
                    const b = guideBlocks(all[ch.id] || [], windowStart, windowStart + WINDOW_MS);
                    blocks[ch.id] = b;
                    b.forEach(cell => { if (cell.itemKey) keys.add(cell.itemKey); });
                }
                items = {};
                for (const k of keys) {
                    const it = await this.repo.item(k);
                    if (it) items[k] = it;
                }
            };

            await loadWindow();

            const draw = () => {
                if (this.screen !== 'GUIDE') return;
                const curNow = Date.now();
                const header = focusRow < 0;
                const focusedCh = !header ? rows[focusRow] : null;
                const focusedBlock = focusedCh ? (blocks[focusedCh.id] || []).find(b => focusTime >= b.startMs && focusTime < b.endMs) : null;
                const focusedItem = (focusedBlock && focusedBlock.itemKey) ? items[focusedBlock.itemKey] : null;
                const pUrl = posterUrl(focusedItem);

                const detailBits = [];
                if (focusedCh) detailBits.push(focusedCh.name);
                if (focusedBlock) detailBits.push(`${clockTime(focusedBlock.startMs)} – ${clockTime(focusedBlock.endMs)}`);
                if (focusedItem && focusedItem.contentRating) detailBits.push(focusedItem.contentRating);
                if (focusedItem && focusedItem.genres && focusedItem.genres.length > 0) detailBits.push(focusedItem.genres.slice(0, 3).join(', '));

                const cols = Math.floor(WINDOW_MS / GRID_MS);
                let timeSlotsHtml = '';
                for (let i = 0; i < cols; i++) {
                    timeSlotsHtml += `<div class="guide-time-slot">${clockTime(windowStart + i * GRID_MS)}</div>`;
                }

                const first = Math.max(0, Math.min(focusRow - Math.floor(VISIBLE_ROWS / 2), Math.max(0, rows.length - VISIBLE_ROWS)));
                const visible = rows.slice(first, first + VISIBLE_ROWS);
                const pxPerMs = TIMELINE_W / WINDOW_MS;

                let rowsHtml = '';
                visible.forEach((ch, idx) => {
                    const rowIndex = first + idx;
                    const chBlocks = blocks[ch.id] || [];
                    let cellsHtml = '';
                    for (const b of chBlocks) {
                        const x = (b.startMs - windowStart) * pxPerMs;
                        const w = Math.max(2, (b.endMs - b.startMs) * pxPerMs);
                        const sel = !header && rowIndex === focusRow && focusTime >= b.startMs && focusTime < b.endMs;
                        const it = b.itemKey ? items[b.itemKey] : null;
                        const label = !it ? "Up Next" : (it.kind === 'EPISODE' ? `${it.showTitle} — S${it.season}E${it.episode}` : it.title);
                        cellsHtml += `<div class="guide-cell ${sel ? 'selected' : ''}" style="left:${x}px; width:${w}px;">${label}</div>`;
                    }
                    let nowLineHtml = '';
                    if (curNow >= windowStart && curNow < windowStart + WINDOW_MS) {
                        const nx = (curNow - windowStart) * pxPerMs;
                        nowLineHtml = `<div class="guide-now-line" style="left:${nx}px;"></div>`;
                    }
                    rowsHtml += `
                        <div class="guide-row">
                            <div class="guide-ch-cell">
                                <div class="guide-ch-num">${ch.number}</div>
                                <div class="guide-ch-name">${ch.name}</div>
                            </div>
                            <div class="guide-timeline">${cellsHtml}${nowLineHtml}</div>
                        </div>
                    `;
                });

                this.modalScreenEl.innerHTML = `
                    <div class="guide-screen">
                        <div class="guide-header">
                            <div class="text-osd">RERUN</div>
                            <div style="width:24px;"></div>
                            <div class="text-title" style="color:var(--yellow);">${clockTime(curNow)}</div>
                            <div style="flex:1;"></div>
                            <div class="tv-btn ${header && headerIndex === 0 ? 'focused' : ''}" id="guide-btn-ch"><div class="tv-btn-label">CHANNELS</div></div>
                            <div style="width:16px;"></div>
                            <div class="tv-btn ${header && headerIndex === 1 ? 'focused' : ''}" id="guide-btn-set"><div class="tv-btn-label">SETTINGS</div></div>
                        </div>
                        <div class="guide-detail">
                            ${pUrl ? `<img class="guide-detail-poster" src="${pUrl}" alt="">` : ''}
                            <div style="display:flex; flex-direction:column; gap:4px; overflow:hidden;">
                                <div class="text-title" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${describeItem(focusedItem)}</div>
                                <div class="text-body" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${detailBits.join('  ·  ')}</div>
                                ${focusedItem && focusedItem.kind === 'EPISODE' ? `<div class="text-caption" style="color:var(--cyan);">${focusedItem.title}</div>` : ''}
                            </div>
                        </div>
                        <div class="guide-grid-wrap">
                            <div class="guide-time-header">
                                <div class="guide-time-spacer"></div>
                                ${timeSlotsHtml}
                            </div>
                            ${rowsHtml}
                        </div>
                        <div class="text-caption">▲▼ channels   ◀▶ time   OK watch   ☰ edit channel   BACK return</div>
                    </div>
                `;

                document.getElementById('guide-btn-ch').onclick = () => this.go('CHANNELS');
                document.getElementById('guide-btn-set').onclick = () => this.go('SETTINGS');
            };

            draw();

            this._keyHandler = async (k) => {
                const header = focusRow < 0;
                const focusedCh = !header ? rows[focusRow] : null;
                const focusedBlock = focusedCh ? (blocks[focusedCh.id] || []).find(b => focusTime >= b.startMs && focusTime < b.endMs) : null;

                if (k === 'UP') {
                    if (rows.length > 0) focusRow = Math.max(-1, focusRow - 1);
                    draw();
                } else if (k === 'DOWN') {
                    if (rows.length > 0) focusRow = Math.min(rows.length - 1, focusRow + 1);
                    draw();
                } else if (k === 'RIGHT') {
                    if (header) { headerIndex = 1; draw(); }
                    else {
                        focusTime = focusedBlock ? focusedBlock.endMs : (focusTime + GRID_MS);
                        if (focusTime >= windowStart + WINDOW_MS - GRID_MS) {
                            windowStart += GRID_MS;
                            await loadWindow();
                        }
                        draw();
                    }
                } else if (k === 'LEFT') {
                    if (header) { headerIndex = 0; draw(); }
                    else {
                        const minTime = Math.max(Date.now(), gridFloor(Date.now()));
                        focusTime = Math.max(minTime, (focusedBlock ? focusedBlock.startMs : focusTime) - 1);
                        if (focusTime < windowStart) {
                            windowStart = Math.max(gridFloor(Date.now()), windowStart - GRID_MS);
                            await loadWindow();
                        }
                        draw();
                    }
                } else if (k === 'OK') {
                    if (header) this.go(headerIndex === 0 ? 'CHANNELS' : 'SETTINGS');
                    else if (focusedCh) {
                        this.go('PLAYER');
                        this.tuneTo(focusedCh.id, true);
                    }
                } else if (k === 'MENU') {
                    if (focusedCh) this.go('EDITOR', focusedCh.id);
                } else if (k === 'BACK') {
                    this.closeGuideWithBack();
                }
            };
        }

        // ==================== CHANNEL MANAGER SCREEN ====================
        async renderChannels() {
            const all = await this.repo.allChannels();
            const active = all.filter(c => !c.dormant && !c.hidden);
            const hidden = all.filter(c => c.hidden);
            const dormant = all.filter(c => c.dormant && !c.hidden);

            const actions = [{ label: '＋  New channel', sub: null, id: null }];
            active.forEach(c => actions.push({ label: `${c.number}   ${c.name}`, sub: formatHours(c.poolMs), id: c.id, group: 'ACTIVE' }));
            hidden.forEach(c => actions.push({ label: `${c.number}   ${c.name}`, sub: `hidden · ${formatHours(c.poolMs)}`, id: c.id, group: 'HIDDEN' }));
            dormant.forEach(c => actions.push({ label: `${c.number}   ${c.name}`, sub: formatHours(c.poolMs), id: c.id, group: 'DORMANT' }));

            let focusIdx = 0;

            const draw = () => {
                if (this.screen !== 'CHANNELS') return;
                let idx = 0;
                const btnHtml = (item) => {
                    const i = idx++;
                    return `
                        <div class="tv-btn ${i === focusIdx ? 'focused' : ''}" data-idx="${i}" style="width:100%; margin-bottom:8px;">
                            <div class="tv-btn-label">${item.label}</div>
                            ${item.sub ? `<div class="tv-btn-sub">${item.sub}</div>` : ''}
                        </div>
                    `;
                };

                let html = btnHtml(actions[0]);
                html += `<div class="text-guide-num" style="margin:12px 0 8px;">ON THE AIR (${active.length})</div>`;
                active.forEach((_, i) => { html += btnHtml(actions[1 + i]); });

                if (hidden.length > 0) {
                    html += `<div class="text-guide-num" style="margin:12px 0 8px;">HIDDEN (${hidden.length})</div>`;
                    hidden.forEach((_, i) => { html += btnHtml(actions[1 + active.length + i]); });
                }

                html += `<div class="text-guide-num" style="margin:12px 0 8px;">WAITING FOR CONTENT (${dormant.length}) — needs 4 hours</div>`;
                dormant.forEach((_, i) => { html += btnHtml(actions[1 + active.length + hidden.length + i]); });

                this.modalScreenEl.innerHTML = `
                    <div class="tv-frame">
                        <div class="tv-frame-header">
                            <div class="text-osd">RERUN</div>
                            <div class="text-title" style="color:var(--yellow);">CHANNELS</div>
                        </div>
                        <div class="tv-frame-body" id="ch-scroll">${html}</div>
                        <div class="tv-frame-footer text-caption">OK edit   BACK guide</div>
                    </div>
                `;

                const focusedEl = this.modalScreenEl.querySelector('.tv-btn.focused');
                if (focusedEl) focusedEl.scrollIntoView({ block: 'nearest' });

                this.modalScreenEl.querySelectorAll('.tv-btn').forEach(el => {
                    el.onclick = () => this.go('EDITOR', actions[Number(el.dataset.idx)].id);
                });
            };

            draw();

            this._keyHandler = (k) => {
                if (k === 'UP') { focusIdx = Math.max(0, focusIdx - 1); draw(); }
                else if (k === 'DOWN') { focusIdx = Math.min(actions.length - 1, focusIdx + 1); draw(); }
                else if (k === 'OK') this.go('EDITOR', actions[focusIdx].id);
                else if (k === 'BACK') this.go('GUIDE');
            };
        }

        // ==================== CHANNEL EDITOR SCREEN ====================
        async renderEditor(channelId) {
            const rows = await this.repo.allChannels();
            const existing = channelId ? rows.find(r => r.id === channelId) : null;
            const library = await this.repo.libraryItems();
            const facets = Facets.from(library);

            const usedNums = new Set(rows.map(r => r.number));
            let nextNum = 401;
            while (usedNums.has(nextNum)) nextNum++;

            let draft = existing ? JSON.parse(JSON.stringify(existing.def)) : {
                id: 'custom:' + uuid().slice(0, 8),
                number: nextNum,
                name: '',
                kind: 'CUSTOM',
                rules: createRules(),
                order: 'SHUFFLE',
                blocks: [],
                kidsSafe: false,
                hidden: false,
                movieEveryMs: 6 * 3600000,
            };
            let errorMsg = null;
            let focusRow = 0;
            let focusCol = 0;

            const toggleInArr = (arr, val) => arr.includes(val) ? arr.filter(x => x !== val) : arr.concat([val]);

            const buildNavRows = () => {
                const nav = [];
                // Row 0: Name & Number inputs
                nav.push([
                    { type: 'INPUT_NAME', label: 'Name' },
                    { type: 'INPUT_NUMBER', label: 'Number' },
                ]);
                // Row 1: Media kinds
                const kSet = new Set(draft.rules.kinds || ['MOVIE', 'EPISODE']);
                const isBoth = kSet.has('MOVIE') && kSet.has('EPISODE');
                const isMovieOnly = kSet.has('MOVIE') && !kSet.has('EPISODE');
                const isShowOnly = !kSet.has('MOVIE') && kSet.has('EPISODE');
                nav.push([
                    { label: 'Movies + shows', selected: isBoth, action: () => { draft.rules.kinds = ['MOVIE', 'EPISODE']; } },
                    { label: 'Movies only', selected: isMovieOnly, action: () => { draft.rules.kinds = ['MOVIE']; } },
                    { label: 'Shows only', selected: isShowOnly, action: () => { draft.rules.kinds = ['EPISODE']; } },
                ]);
                // Row 2: Order & flags
                nav.push([
                    { label: 'Shuffle', selected: draft.order === 'SHUFFLE', action: () => { draft.order = 'SHUFFLE'; } },
                    { label: 'In order', selected: draft.order === 'IN_ORDER', action: () => { draft.order = 'IN_ORDER'; } },
                    { label: 'Kids-safe', selected: !!draft.kidsSafe, action: () => { draft.kidsSafe = !draft.kidsSafe; } },
                    { label: 'Hidden', selected: !!draft.hidden, action: () => { draft.hidden = !draft.hidden; } },
                ]);
                // Networks
                if (facets.networks.length > 0) {
                    nav.push(facets.networks.map(id => {
                        const net = Catalog.networks.find(n => n.id === id);
                        return {
                            section: 'NETWORKS',
                            label: net ? net.name : id,
                            selected: draft.rules.networks.includes(id),
                            action: () => { draft.rules.networks = toggleInArr(draft.rules.networks, id); }
                        };
                    }));
                }
                // Genres
                if (facets.genres.length > 0) {
                    nav.push(facets.genres.map(g => ({
                        section: 'GENRES',
                        label: g,
                        selected: draft.rules.genres.includes(g),
                        action: () => { draft.rules.genres = toggleInArr(draft.rules.genres, g); }
                    })));
                    nav.push(facets.genres.map(g => ({
                        section: 'LEAVE OUT GENRES',
                        label: g,
                        selected: draft.rules.excludeGenres.includes(g),
                        action: () => { draft.rules.excludeGenres = toggleInArr(draft.rules.excludeGenres, g); }
                    })));
                }
                // Shows
                if (facets.shows.length > 0) {
                    nav.push(facets.shows.map(s => ({
                        section: 'SHOWS',
                        label: s,
                        selected: draft.rules.titles.includes(s),
                        action: () => { draft.rules.titles = toggleInArr(draft.rules.titles, s); }
                    })));
                }
                // Decades
                if (facets.decades.length > 0) {
                    nav.push(facets.decades.map(y => ({
                        section: 'DECADES',
                        label: `${y}s`,
                        selected: draft.rules.decades.includes(y),
                        action: () => { draft.rules.decades = toggleInArr(draft.rules.decades, y); }
                    })));
                }
                // Ratings
                if (facets.ratings.length > 0) {
                    nav.push(facets.ratings.map(r => ({
                        section: 'RATINGS',
                        label: r,
                        selected: draft.rules.ratings.includes(r),
                        action: () => { draft.rules.ratings = toggleInArr(draft.rules.ratings, r); }
                    })));
                }
                // Actions row
                const actRow = [
                    {
                        section: 'ACTIONS',
                        label: 'Save',
                        action: async () => {
                            const errs = validateDraft(draft, rows.map(r => r.def));
                            if (errs.length > 0) {
                                errorMsg = errs.join('   ');
                                draw();
                                return;
                            }
                            const pool = Resolver.pool(draft, library);
                            const poolMs = pool.reduce((a, b) => a + b.durationMs, 0);
                            await this.repo.saveChannel({
                                id: draft.id,
                                number: draft.number,
                                name: draft.name,
                                def: draft,
                                builtIn: existing ? existing.builtIn : false,
                                dormant: Resolver.isDormant(pool),
                                poolMs,
                                hidden: !!draft.hidden,
                            });
                            await this.repo.resetFuture(draft.id, Date.now());
                            this.channels = await this.repo.activeChannels();
                            this.go('CHANNELS');
                        }
                    },
                    { label: 'Cancel', action: () => this.go('CHANNELS') },
                ];
                if (existing && !existing.builtIn) {
                    actRow.push({
                        label: 'Delete channel',
                        action: async () => {
                            await this.repo.deleteChannel(draft.id);
                            this.channels = await this.repo.activeChannels();
                            this.go('CHANNELS');
                        }
                    });
                }
                nav.push(actRow);
                return nav;
            };

            const draw = () => {
                if (this.screen !== 'EDITOR') return;
                const nav = buildNavRows();
                focusRow = Math.max(0, Math.min(nav.length - 1, focusRow));
                focusCol = Math.max(0, Math.min(nav[focusRow].length - 1, focusCol));

                const pool = Resolver.pool(draft, library);
                const poolMs = pool.reduce((a, b) => a + b.durationMs, 0);
                const poolText = `${(poolMs / 3600000).toFixed(1)} hours of content` + (Resolver.isDormant(pool) ? '  (needs 4 to go on the air)' : '');

                let bodyHtml = `
                    <div style="display:flex; align-items:flex-end; gap:24px; margin-bottom:18px;">
                        <div>
                            <div class="text-caption">Name</div>
                            <input id="ed-name" class="tv-input ${focusRow === 0 && focusCol === 0 ? 'focused' : ''}" style="width:520px;" value="${draft.name.replace(/"/g, '&quot;')}">
                        </div>
                        <div>
                            <div class="text-caption">Number</div>
                            <input id="ed-num" class="tv-input ${focusRow === 0 && focusCol === 1 ? 'focused' : ''}" style="width:160px;" value="${draft.number}">
                        </div>
                        <div class="text-caption" style="color:var(--cyan); padding-bottom:12px;">${poolText}</div>
                    </div>
                    <div class="editor-section">
                        <div class="text-guide-num">PLAYS</div>
                `;

                for (let r = 1; r < nav.length; r++) {
                    const row = nav[r];
                    const secTitle = row[0] && row[0].section;
                    if (secTitle && secTitle !== 'ACTIONS') {
                        bodyHtml += `</div><div class="editor-section"><div class="text-guide-num">${secTitle}</div>`;
                    } else if (secTitle === 'ACTIONS') {
                        bodyHtml += `</div><div class="editor-section" style="margin-top:20px;">`;
                        if (errorMsg) bodyHtml += `<div class="text-body" style="color:#FF6B6B; margin-bottom:10px;">${errorMsg}</div>`;
                    }
                    bodyHtml += `<div class="editor-chips">`;
                    row.forEach((cell, c) => {
                        const isFocused = r === focusRow && c === focusCol;
                        const isSel = !!cell.selected;
                        bodyHtml += `
                            <div class="tv-btn ${isSel ? 'selected' : ''} ${isFocused ? 'focused' : ''}" data-r="${r}" data-c="${c}">
                                <div class="tv-btn-label">${isSel ? '■ ' : ''}${cell.label}</div>
                            </div>
                        `;
                    });
                    bodyHtml += `</div>`;
                }
                bodyHtml += `</div>`;

                this.modalScreenEl.innerHTML = `
                    <div class="tv-frame">
                        <div class="tv-frame-header">
                            <div class="text-osd">RERUN</div>
                            <div class="text-title" style="color:var(--yellow);">${existing ? 'EDIT ' + draft.name.toUpperCase() : 'NEW CHANNEL'}</div>
                        </div>
                        <div class="tv-frame-body">${bodyHtml}</div>
                        <div class="tv-frame-footer text-caption">OK toggle   ▲▼ move   BACK cancel</div>
                    </div>
                `;

                const nameInput = document.getElementById('ed-name');
                const numInput = document.getElementById('ed-num');
                nameInput.oninput = () => { draft.name = nameInput.value; };
                numInput.oninput = () => {
                    const clean = numInput.value.replace(/\D/g, '').slice(0, 3);
                    numInput.value = clean;
                    if (clean) draft.number = Number(clean);
                };

                const focusedEl = this.modalScreenEl.querySelector('.focused');
                if (focusedEl) focusedEl.scrollIntoView({ block: 'nearest' });

                this.modalScreenEl.querySelectorAll('.tv-btn').forEach(el => {
                    el.onclick = () => {
                        const r = Number(el.dataset.r);
                        const c = Number(el.dataset.c);
                        focusRow = r;
                        focusCol = c;
                        const res = nav[r][c].action();
                        if (!(res instanceof Promise)) draw();
                    };
                });
            };

            draw();

            this._keyHandler = (k) => {
                const nav = buildNavRows();
                if (k === 'UP') {
                    focusRow = Math.max(0, focusRow - 1);
                    focusCol = Math.min(focusCol, nav[focusRow].length - 1);
                    draw();
                } else if (k === 'DOWN') {
                    focusRow = Math.min(nav.length - 1, focusRow + 1);
                    focusCol = Math.min(focusCol, nav[focusRow].length - 1);
                    draw();
                } else if (k === 'LEFT') {
                    focusCol = Math.max(0, focusCol - 1);
                    draw();
                } else if (k === 'RIGHT') {
                    focusCol = Math.min(nav[focusRow].length - 1, focusCol + 1);
                    draw();
                } else if (k === 'OK') {
                    if (focusRow === 0) {
                        const inputId = focusCol === 0 ? 'ed-name' : 'ed-num';
                        const el = document.getElementById(inputId);
                        if (el) el.focus();
                    } else {
                        const cell = nav[focusRow][focusCol];
                        const res = cell.action();
                        if (!(res instanceof Promise)) draw();
                    }
                } else if (k === 'BACK') {
                    this.go('CHANNELS');
                }
            };
        }

        // ==================== SETTINGS SCREEN ====================
        renderSettings() {
            let syncStatus = null;
            let focusRow = 2; // Start on CRT row like SettingsScreen.kt
            let focusCol = 0;

            const buildGrid = () => {
                const fx = this.getEffects();
                const crt = Settings.crtLevel;
                return [
                    [
                        { label: 'All effects on', action: () => this.setAllEffects(true) },
                        { label: 'All effects off', action: () => this.setAllEffects(false) },
                    ],
                    [
                        { label: 'Static', selected: fx.static, sub: fx.static ? 'on' : 'off', action: () => this.setEffect(EFFECTS.STATIC, !fx.static) },
                        { label: 'Static sound', selected: fx.sound, sub: fx.sound ? 'on' : 'off', action: () => this.setEffect(EFFECTS.SOUND, !fx.sound) },
                        { label: 'Picture roll', selected: fx.roll, sub: fx.roll ? 'on' : 'off', action: () => this.setEffect(EFFECTS.ROLL, !fx.roll) },
                        { label: 'Power on/off', selected: fx.power, sub: fx.power ? 'on' : 'off', action: () => this.setEffect(EFFECTS.POWER, !fx.power) },
                    ],
                    [
                        { label: 'CRT Off', selected: crt === 'OFF', action: () => this.setCrt('OFF') },
                        { label: 'CRT Subtle', selected: crt === 'SUBTLE', action: () => this.setCrt('SUBTLE') },
                        { label: 'CRT Full', selected: crt === 'FULL', action: () => this.setCrt('FULL') },
                    ],
                    [
                        {
                            label: 'Re-sync now',
                            action: async () => {
                                if (syncStatus && syncStatus !== 'Done' && !syncStatus.startsWith('Sync failed')) return;
                                try {
                                    await LibrarySync.sync(this.repo, (p) => {
                                        syncStatus = p.finished ? 'Done' : `${p.stage} ${p.done}/${p.total}`;
                                        draw();
                                    });
                                    this.channels = await this.repo.activeChannels();
                                    draw();
                                } catch (e) {
                                    syncStatus = `Sync failed: ${e.message}`;
                                    draw();
                                }
                            }
                        },
                        { label: 'Channels', action: () => this.go('CHANNELS') },
                        {
                            label: 'Sign out',
                            action: () => {
                                this.engine.stop();
                                Settings.signOut();
                                this.go('ONBOARDING');
                            }
                        },
                    ],
                ];
            };

            const draw = () => {
                if (this.screen !== 'SETTINGS') return;
                const grid = buildGrid();
                focusRow = Math.max(0, Math.min(grid.length - 1, focusRow));
                focusCol = Math.max(0, Math.min(grid[focusRow].length - 1, focusCol));

                const renderRow = (r) => grid[r].map((btn, c) => {
                    const isFocused = r === focusRow && c === focusCol;
                    const isSel = !!btn.selected;
                    return `
                        <div class="tv-btn ${isSel ? 'selected' : ''} ${isFocused ? 'focused' : ''}" data-r="${r}" data-c="${c}">
                            <div class="tv-btn-label">${isSel ? '■ ' : ''}${btn.label}</div>
                            ${btn.sub ? `<div class="tv-btn-sub">${btn.sub}</div>` : ''}
                        </div>
                    `;
                }).join('');

                const server = Settings.serverName || '—';
                const lastSync = Settings.lastSyncMs;
                const serverLine = `Plex server: ${server}` + (lastSync > 0 ? `   ·   last synced ${clockTime(lastSync)}` : '');

                this.modalScreenEl.innerHTML = `
                    <div class="tv-frame">
                        <div class="tv-frame-header">
                            <div class="text-osd">RERUN</div>
                            <div class="text-title" style="color:var(--yellow);">SETTINGS</div>
                        </div>
                        <div class="tv-frame-body" style="display:flex; flex-direction:column; gap:18px;">
                            <div class="text-guide-num">EFFECTS</div>
                            <div style="display:flex; gap:12px;">${renderRow(0)}</div>
                            <div style="display:flex; gap:12px;">${renderRow(1)}</div>

                            <div class="text-guide-num">CRT LOOK</div>
                            <div style="display:flex; gap:12px;">${renderRow(2)}</div>
                            <div class="text-caption">Tip: hold OK for 2 seconds while watching to flip between these.</div>

                            <div class="text-guide-num">LIBRARY</div>
                            <div class="text-body">${serverLine}</div>
                            <div style="display:flex; gap:12px;">${renderRow(3)}</div>
                            ${syncStatus ? `<div class="text-body" style="color:var(--cyan);">${syncStatus}</div>` : ''}

                            <div class="text-guide-num">ABOUT</div>
                            <div class="text-caption">Rerun 0.1 · plays your own Plex library like 90s cable. Nothing you watch is reported to Plex.</div>
                            <div class="text-caption">VT323 font © The VT323 Project Authors, SIL Open Font License 1.1.</div>
                        </div>
                        <div class="tv-frame-footer text-caption">BACK guide</div>
                    </div>
                `;

                this.modalScreenEl.querySelectorAll('.tv-btn').forEach(el => {
                    el.onclick = () => {
                        const r = Number(el.dataset.r);
                        const c = Number(el.dataset.c);
                        focusRow = r;
                        focusCol = c;
                        grid[r][c].action();
                    };
                });
            };

            draw();

            this._keyHandler = (k) => {
                const grid = buildGrid();
                if (k === 'UP') {
                    focusRow = Math.max(0, focusRow - 1);
                    focusCol = Math.min(focusCol, grid[focusRow].length - 1);
                    draw();
                } else if (k === 'DOWN') {
                    focusRow = Math.min(grid.length - 1, focusRow + 1);
                    focusCol = Math.min(focusCol, grid[focusRow].length - 1);
                    draw();
                } else if (k === 'LEFT') {
                    focusCol = Math.max(0, focusCol - 1);
                    draw();
                } else if (k === 'RIGHT') {
                    focusCol = Math.min(grid[focusRow].length - 1, focusCol + 1);
                    draw();
                } else if (k === 'OK') {
                    grid[focusRow][focusCol].action();
                } else if (k === 'BACK') {
                    this.go('GUIDE');
                }
            };
        }
    }

    window.addEventListener('DOMContentLoaded', () => {
        const app = new App();
        window.RerunApp = app;
        app.init();
    });
})(window);
