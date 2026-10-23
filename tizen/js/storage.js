/**
 * Rerun Storage & Plex Sync — IndexedDB + localStorage + PlexApi + LibrarySync + ScheduleRepo
 */
(function (global) {
    'use strict';

    const { Catalog, Resolver, Scheduler, needsTopUp, PlexParse, Playback, strHashCode } = global.RerunCore;

    function uuid() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
            const r = (Math.random() * 16) | 0;
            return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
        });
    }

    const Settings = {
        _get(k, def = null) {
            const v = localStorage.getItem('rerun_' + k);
            return v !== null ? v : def;
        },
        _set(k, v) {
            if (v === null || v === undefined) localStorage.removeItem('rerun_' + k);
            else localStorage.setItem('rerun_' + k, String(v));
        },
        get clientId() {
            let id = this._get('client_id');
            if (!id) {
                id = uuid();
                this._set('client_id', id);
            }
            return id;
        },
        get accountToken() { return this._get('account_token'); },
        set accountToken(v) { this._set('account_token', v); },
        get serverToken() { return this._get('server_token'); },
        set serverToken(v) { this._set('server_token', v); },
        get serverBase() { return this._get('server_base'); },
        set serverBase(v) { this._set('server_base', v); },
        get serverName() { return this._get('server_name'); },
        set serverName(v) { this._set('server_name', v); },
        get serverId() { return this._get('server_id'); },
        set serverId(v) { this._set('server_id', v); },
        get lastSyncMs() { return Number(this._get('last_sync', '0')); },
        set lastSyncMs(v) { this._set('last_sync', String(v)); },
        get lastChannel() { return this._get('last_channel'); },
        set lastChannel(v) { this._set('last_channel', v); },
        get crtLevel() { return this._get('crt_level', 'SUBTLE'); },
        set crtLevel(v) { this._set('crt_level', v); },
        flag(key, def = true) {
            const v = this._get(key);
            return v === null ? def : v === 'true';
        },
        setFlag(key, on) { this._set(key, on ? 'true' : 'false'); },
        get isSignedIn() { return !!this.accountToken; },
        signOut() {
            ['account_token', 'server_token', 'server_base', 'server_name', 'server_id', 'last_sync'].forEach(k => this._set(k, null));
        }
    };

    class RerunDb {
        constructor() {
            this._dbPromise = null;
        }

        open() {
            if (this._dbPromise) return this._dbPromise;
            this._dbPromise = new Promise((resolve, reject) => {
                const req = indexedDB.open('rerun_tizen_db', 1);
                req.onupgradeneeded = e => {
                    const db = e.target.result;
                    if (!db.objectStoreNames.contains('items')) db.createObjectStore('items', { keyPath: 'key' });
                    if (!db.objectStoreNames.contains('channels')) db.createObjectStore('channels', { keyPath: 'id' });
                    if (!db.objectStoreNames.contains('entries')) {
                        const es = db.createObjectStore('entries', { keyPath: ['channelId', 'startMs'] });
                        es.createIndex('byChannel', 'channelId', { unique: false });
                    }
                    if (!db.objectStoreNames.contains('states')) db.createObjectStore('states', { keyPath: 'channelId' });
                };
                req.onsuccess = () => resolve(req.result);
                req.onerror = () => reject(req.error);
            });
            return this._dbPromise;
        }

        async getAll(storeName) {
            const db = await this.open();
            return new Promise((resolve, reject) => {
                const tx = db.transaction(storeName, 'readonly');
                const req = tx.objectStore(storeName).getAll();
                req.onsuccess = () => resolve(req.result || []);
                req.onerror = () => reject(req.error);
            });
        }

        async get(storeName, key) {
            const db = await this.open();
            return new Promise((resolve, reject) => {
                const tx = db.transaction(storeName, 'readonly');
                const req = tx.objectStore(storeName).get(key);
                req.onsuccess = () => resolve(req.result || null);
                req.onerror = () => reject(req.error);
            });
        }

        async put(storeName, value) {
            const db = await this.open();
            return new Promise((resolve, reject) => {
                const tx = db.transaction(storeName, 'readwrite');
                tx.objectStore(storeName).put(value);
                tx.oncomplete = () => resolve();
                tx.onerror = () => reject(tx.error);
            });
        }

        async putAll(storeName, values, clearFirst = false) {
            const db = await this.open();
            return new Promise((resolve, reject) => {
                const tx = db.transaction(storeName, 'readwrite');
                const st = tx.objectStore(storeName);
                if (clearFirst) st.clear();
                for (const v of values) st.put(v);
                tx.oncomplete = () => resolve();
                tx.onerror = () => reject(tx.error);
            });
        }

        async delete(storeName, key) {
            const db = await this.open();
            return new Promise((resolve, reject) => {
                const tx = db.transaction(storeName, 'readwrite');
                tx.objectStore(storeName).delete(key);
                tx.oncomplete = () => resolve();
                tx.onerror = () => reject(tx.error);
            });
        }

        async getChannelEntries(channelId) {
            const db = await this.open();
            return new Promise((resolve, reject) => {
                const tx = db.transaction('entries', 'readonly');
                const idx = tx.objectStore('entries').index('byChannel');
                const req = idx.getAll(IDBKeyRange.only(channelId));
                req.onsuccess = () => {
                    const list = (req.result || []).sort((a, b) => a.startMs - b.startMs);
                    resolve(list);
                };
                req.onerror = () => reject(req.error);
            });
        }

        async pruneEntriesBefore(cutoffMs) {
            const all = await this.getAll('entries');
            const toDelete = all.filter(e => e.endMs <= cutoffMs);
            if (toDelete.length === 0) return;
            const db = await this.open();
            return new Promise((resolve, reject) => {
                const tx = db.transaction('entries', 'readwrite');
                const st = tx.objectStore('entries');
                for (const e of toDelete) st.delete([e.channelId, e.startMs]);
                tx.oncomplete = () => resolve();
                tx.onerror = () => reject(tx.error);
            });
        }

        async deleteEntriesFrom(channelId, fromMs) {
            const list = await this.getChannelEntries(channelId);
            const toDelete = list.filter(e => e.startMs >= fromMs);
            if (toDelete.length === 0) return;
            const db = await this.open();
            return new Promise((resolve, reject) => {
                const tx = db.transaction('entries', 'readwrite');
                const st = tx.objectStore('entries');
                for (const e of toDelete) st.delete([e.channelId, e.startMs]);
                tx.oncomplete = () => resolve();
                tx.onerror = () => reject(tx.error);
            });
        }
    }

    const PlexApi = {
        _headers(token = null, extra = {}) {
            const h = Object.assign({
                'Accept': 'application/json',
                'X-Plex-Client-Identifier': Settings.clientId,
                'X-Plex-Product': 'Rerun',
                'X-Plex-Version': '0.1.0',
                'X-Plex-Platform': 'Samsung Tizen',
                'X-Plex-Device': 'Samsung Smart TV',
                'X-Plex-Device-Name': 'Rerun TV (Samsung)',
            }, extra);
            if (token) h['X-Plex-Token'] = token;
            return h;
        },
        async _fetch(url, opts = {}, timeoutMs = 15000) {
            const ctrl = new AbortController();
            const timer = setTimeout(() => ctrl.abort(), timeoutMs);
            try {
                const resp = await fetch(url, Object.assign({}, opts, { signal: ctrl.signal }));
                if (!resp.ok) throw new Error(`HTTP ${resp.status} for ${url}`);
                return await resp.text();
            } finally {
                clearTimeout(timer);
            }
        },
        async createPin() {
            const text = await this._fetch('https://plex.tv/api/v2/pins?strong=false', {
                method: 'POST',
                headers: this._headers(),
            });
            return PlexParse.pin(text);
        },
        async checkPin(id) {
            const text = await this._fetch(`https://plex.tv/api/v2/pins/${id}`, {
                method: 'GET',
                headers: this._headers(),
            });
            return PlexParse.pin(text);
        },
        async servers(token) {
            const text = await this._fetch('https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1', {
                method: 'GET',
                headers: this._headers(token),
            });
            return PlexParse.servers(text);
        },
        async pickConnection(server) {
            const token = server.accessToken || Settings.accountToken;
            for (const c of Playback.connectionOrder(server)) {
                try {
                    await this._fetch(`${c.uri}/identity`, {
                        method: 'GET',
                        headers: this._headers(token),
                    }, 3500);
                    return c.uri;
                } catch (_) {
                    // try next connection
                }
            }
            return null;
        },
        async get(base, path, token, start = null, size = null) {
            const sep = path.includes('?') ? '&' : '?';
            let url = `${base}${path}${sep}X-Plex-Token=${encodeURIComponent(token)}`;
            if (start !== null) url += `&X-Plex-Container-Start=${start}`;
            if (size !== null) url += `&X-Plex-Container-Size=${size}`;
            return await this._fetch(url, {
                method: 'GET',
                headers: { 'Accept': 'application/json' },
            });
        }
    };

    class ScheduleRepo {
        constructor(db) {
            this.db = db;
            this._libraryCache = null;
        }

        invalidateLibrary() {
            this._libraryCache = null;
        }

        async _lib() {
            if (this._libraryCache) return this._libraryCache;
            const list = await this.db.getAll('items');
            this._libraryCache = new Map(list.map(i => [i.key, i]));
            return this._libraryCache;
        }

        async item(key) {
            if (!key) return null;
            const m = await this._lib();
            return m.get(key) || null;
        }

        async libraryItems() {
            const m = await this._lib();
            return Array.from(m.values());
        }

        async allChannels() {
            const rows = await this.db.getAll('channels');
            return rows.sort((a, b) => a.number - b.number);
        }

        async activeChannels() {
            const all = await this.allChannels();
            return all.filter(c => !c.dormant && !c.hidden);
        }

        async channel(id) {
            return await this.db.get('channels', id);
        }

        async ensure(nowMs) {
            await this.db.pruneEntriesBefore(nowMs - 6 * 3600000);
            const active = await this.activeChannels();
            for (const row of active) {
                await this._topUp(row, nowMs);
            }
        }

        async ensureChannel(channelId, nowMs) {
            const row = await this.channel(channelId);
            if (row) await this._topUp(row, nowMs);
        }

        async _topUp(row, nowMs) {
            const existing = await this.db.getChannelEntries(row.id);
            const last = existing.length > 0 ? existing[existing.length - 1].endMs : null;
            if (!needsTopUp(last, nowMs)) return;

            const lib = await this.libraryItems();
            const pool = Resolver.pool(row.def, lib);
            const stateRow = await this.db.get('states', row.id);
            const state = stateRow ? stateRow.state : { seed: strHashCode(row.id) };
            const ext = Scheduler.extend(row.def, pool, state, last, nowMs);
            if (ext.entries.length > 0) {
                await this.db.putAll('entries', ext.entries, false);
            }
            await this.db.put('states', { channelId: row.id, state: ext.state });
        }

        async entries(channelId, fromMs, toMs) {
            const list = await this.db.getChannelEntries(channelId);
            return list.filter(e => e.endMs > fromMs && e.startMs < toMs);
        }

        async allEntries(fromMs, toMs) {
            const all = await this.db.getAll('entries');
            const out = {};
            for (const e of all) {
                if (e.endMs <= fromMs || e.startMs >= toMs) continue;
                if (!out[e.channelId]) out[e.channelId] = [];
                out[e.channelId].push(e);
            }
            return out;
        }

        async resetFuture(channelId, nowMs) {
            const current = (await this.entries(channelId, nowMs, nowMs + 1))[0] || null;
            await this.db.deleteEntriesFrom(channelId, current ? current.endMs : nowMs);
            const row = await this.channel(channelId);
            if (row) await this._topUp(row, nowMs);
        }

        async saveChannel(row) {
            await this.db.put('channels', row);
        }

        async deleteChannel(id) {
            await this.db.delete('channels', id);
            await this.db.deleteEntriesFrom(id, 0);
            await this.db.delete('states', id);
        }

        async refreshChannels(items = null) {
            const library = items || await this.libraryItems();
            const existingList = await this.db.getAll('channels');
            const existing = new Map(existingList.map(r => [r.id, r]));
            const rows = [];

            for (const def of Catalog.builtInChannels()) {
                const row = existing.get(def.id);
                const current = row ? row.def : def;
                const pool = Resolver.pool(current, library);
                const poolMs = pool.reduce((acc, i) => acc + i.durationMs, 0);
                rows.push({
                    id: current.id,
                    number: current.number,
                    name: current.name,
                    def: current,
                    builtIn: true,
                    dormant: Resolver.isDormant(pool),
                    poolMs,
                    hidden: !!current.hidden,
                });
            }
            for (const row of existingList.filter(r => !r.builtIn)) {
                const pool = Resolver.pool(row.def, library);
                const poolMs = pool.reduce((acc, i) => acc + i.durationMs, 0);
                rows.push(Object.assign({}, row, {
                    number: row.def.number,
                    name: row.def.name,
                    dormant: Resolver.isDormant(pool),
                    poolMs,
                    hidden: !!row.def.hidden,
                }));
            }
            await this.db.putAll('channels', rows, true);
        }
    }

    const LibrarySync = {
        PAGE: 200,
        async sync(repo, onProgress) {
            const base = Settings.serverBase;
            const token = Settings.serverToken || Settings.accountToken;
            if (!base || !token) throw new Error("No Plex server configured");

            onProgress({ stage: "Reading libraries", done: 0, total: 0, finished: false });
            const sections = PlexParse.sections(await PlexApi.get(base, "/library/sections", token));
            const items = [];

            for (const s of sections.filter(sec => sec.type === 'movie')) {
                await this._pages(base, `/library/sections/${s.key}/all?type=1&includeGuids=1`, token, (json, done, total) => {
                    items.push(...PlexParse.movies(json));
                    onProgress({ stage: `Movies: ${s.title}`, done, total, finished: false });
                });
            }

            for (const s of sections.filter(sec => sec.type === 'show')) {
                const shows = {};
                await this._pages(base, `/library/sections/${s.key}/all?type=2&includeGuids=1`, token, (json, done, total) => {
                    for (const sh of PlexParse.shows(json)) shows[sh.key] = sh;
                    onProgress({ stage: `Shows: ${s.title}`, done, total, finished: false });
                });
                await this._pages(base, `/library/sections/${s.key}/all?type=4`, token, (json, done, total) => {
                    items.push(...PlexParse.episodes(json, shows));
                    onProgress({ stage: `Episodes: ${s.title}`, done, total, finished: false });
                });
            }

            onProgress({ stage: "Saving", done: items.length, total: items.length, finished: false });
            await repo.db.putAll('items', items, true);
            repo.invalidateLibrary();
            await repo.refreshChannels(items);
            Settings.lastSyncMs = Date.now();
            onProgress({ stage: "Done", done: items.length, total: items.length, finished: true });
        },
        async _pages(base, path, token, onPage) {
            let start = 0;
            while (true) {
                const json = await PlexApi.get(base, path, token, start, this.PAGE);
                const total = PlexParse.totalSize(json);
                start += this.PAGE;
                onPage(json, Math.min(start, total), total);
                if (start >= total) break;
            }
        }
    };

    global.RerunStorage = {
        Settings,
        RerunDb,
        PlexApi,
        ScheduleRepo,
        LibrarySync,
        uuid,
    };
})(window);
