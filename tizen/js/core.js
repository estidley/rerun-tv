/**
 * Rerun Core — Tizen / Web port of :core
 * Catalog, Curated, Router, Rules, Resolver, Facets, Scheduler, Tune, GuideBlocks, PlexParse, Playback
 */
(function (global) {
    'use strict';

    const Curated = {
        boomerangShows: [
            "Scooby-Doo, Where Are You!", "The Scooby-Doo Show", "The New Scooby-Doo Movies",
            "Scooby-Doo and Scrappy-Doo", "The 13 Ghosts of Scooby-Doo", "A Pup Named Scooby-Doo",
            "The Flintstones", "The Jetsons", "Looney Tunes", "The Looney Tunes Show",
            "Merrie Melodies", "The Bugs Bunny Show", "Tom and Jerry", "The Tom and Jerry Show",
            "The Yogi Bear Show", "Huckleberry Hound", "The Huckleberry Hound Show", "Top Cat",
            "Wacky Races", "Jonny Quest", "Space Ghost", "Space Ghost Coast to Coast",
            "The Pink Panther Show", "Pink Panther", "Popeye the Sailor", "Popeye",
            "Dexter's Laboratory", "Johnny Bravo", "The Smurfs", "Captain Caveman",
            "Josie and the Pussycats", "The Magilla Gorilla Show", "Quick Draw McGraw",
            "The Atom Ant Show", "Dastardly and Muttley in Their Flying Machines", "The Banana Splits",
            "Hong Kong Phooey", "Jabberjaw", "Speed Buggy", "The Herculoids", "Birdman and the Galaxy Trio",
            "Laff-A-Lympics", "Snagglepuss", "Wally Gator", "The Funky Phantom", "Inch High, Private Eye",
        ],
        toonamiShows: [
            "Dragon Ball", "Dragon Ball Z", "Dragon Ball GT", "Dragon Ball Super", "Dragon Ball Z Kai",
            "Sailor Moon", "Mobile Suit Gundam Wing", "Mobile Suit Gundam 0080: War in the Pocket",
            "Mobile Suit Gundam", "Mobile Fighter G Gundam", "G Gundam",
            "Tenchi Muyo!", "Tenchi Universe", "Outlaw Star", "Yu Yu Hakusho", "Rurouni Kenshin",
            "Cowboy Bebop", "The Big O", "Zoids", "Zoids: Chaotic Century", "Blue Submarine No. 6",
            "Ronin Warriors", "ThunderCats", "Voltron", "Voltron: Defender of the Universe",
            "ReBoot", "Beast Wars", "Beast Wars: Transformers", "Robotech", "Naruto", "One Piece",
            "Bleach", "Hikaru no Go", "IGPX", "Samurai Jack", "Teen Titans", "Megas XLR",
            "Duck Dodgers", "Inuyasha", "Fullmetal Alchemist", "Fullmetal Alchemist: Brotherhood",
            "Gurren Lagann", "Space Dandy", "Attack on Titan", "Sword Art Online", "Black Clover",
            "My Hero Academia",
        ],
    };

    function norm(s) {
        return (s || '').trim().toLowerCase();
    }

    function titleKey(s) {
        return norm(s).replace(/^the\s+/, '').replace(/[^a-z0-9]/g, '');
    }

    function createRules(partial = {}) {
        return {
            networks: Array.from(partial.networks || []),
            titles: Array.from(partial.titles || []),
            tmdbIds: Array.from(partial.tmdbIds || []),
            labels: Array.from(partial.labels || []),
            collections: Array.from(partial.collections || []),
            genres: Array.from(partial.genres || []),
            excludeGenres: Array.from(partial.excludeGenres || []),
            decades: Array.from(partial.decades || []),
            ratings: Array.from(partial.ratings || []),
            kinds: Array.from(partial.kinds || ['MOVIE', 'EPISODE']),
        };
    }

    function rulesMatches(rules, item, networkId) {
        const net = networkId !== undefined ? networkId : item.network;
        const kinds = rules.kinds || ['MOVIE', 'EPISODE'];
        if (!kinds.includes(item.kind)) return false;

        const itemGenres = (item.genres || []).map(norm);
        const nExclude = (rules.excludeGenres || []).map(norm);
        if (nExclude.length > 0 && itemGenres.some(g => nExclude.includes(g))) return false;

        const decades = rules.decades || [];
        if (decades.length > 0) {
            if (!item.year) return false;
            const dec = Math.floor(item.year / 10) * 10;
            if (!decades.includes(dec)) return false;
        }

        const nRatings = (rules.ratings || []).map(norm);
        if (nRatings.length > 0) {
            if (!item.contentRating) return false;
            if (!nRatings.includes(norm(item.contentRating))) return false;
        }

        const hasInclude = (rules.networks && rules.networks.length > 0) ||
            (rules.titles && rules.titles.length > 0) ||
            (rules.tmdbIds && rules.tmdbIds.length > 0) ||
            (rules.labels && rules.labels.length > 0) ||
            (rules.collections && rules.collections.length > 0) ||
            (rules.genres && rules.genres.length > 0);
        if (!hasInclude) return true;

        const nNetworks = (rules.networks || []).map(norm);
        if (net && nNetworks.includes(norm(net))) return true;

        const nTitles = (rules.titles || []).map(titleKey);
        const title = item.kind === 'EPISODE' ? item.showTitle : item.title;
        if (title && nTitles.includes(titleKey(title))) return true;

        const nTmdb = (rules.tmdbIds || []).map(norm);
        if (item.tmdbId && nTmdb.includes(norm(item.tmdbId))) return true;

        const nLabels = (rules.labels || []).map(norm);
        if ((item.labels || []).some(l => nLabels.includes(norm(l)))) return true;

        const nCollections = (rules.collections || []).map(norm);
        if ((item.collections || []).some(c => nCollections.includes(norm(c)))) return true;

        const nGenres = (rules.genres || []).map(norm);
        if (itemGenres.some(g => nGenres.includes(g))) return true;

        return false;
    }

    const DAY_NAMES = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

    function parseTimeMinutes(hhmm) {
        const parts = (hhmm || '00:00').split(':').map(Number);
        return (parts[0] || 0) * 60 + (parts[1] || 0);
    }

    function blockActiveAt(block, epochMs) {
        const d = new Date(epochMs);
        const dayIdx = d.getDay();
        const dayName = DAY_NAMES[dayIdx];
        const prevDayName = DAY_NAMES[(dayIdx + 6) % 7];
        const mins = d.getHours() * 60 + d.getMinutes();
        const startMins = parseTimeMinutes(block.start);
        const endMins = parseTimeMinutes(block.end);
        const days = block.days || [];
        if (startMins <= endMins) {
            return days.includes(dayName) && mins >= startMins && mins < endMins;
        } else {
            return (days.includes(dayName) && mins >= startMins) ||
                (days.includes(prevDayName) && mins < endMins);
        }
    }

    const Catalog = {
        networks: [
            { id: "disney", name: "Disney Channel", number: 101, aliases: ["Disney Channel", "Toon Disney"], kidsSafe: true },
            { id: "nick", name: "Nickelodeon", number: 102, aliases: ["Nickelodeon", "Nicktoons", "Nick", "TeenNick"], kidsSafe: true },
            { id: "cn", name: "Cartoon Network", number: 103, aliases: ["Cartoon Network", "CN"], kidsSafe: true },
            { id: "boomerang", name: "Boomerang", number: 104, aliases: ["Boomerang"], kidsSafe: true },
            { id: "toonami", name: "Toonami", number: 105, aliases: ["Toonami"], kidsSafe: true },
            { id: "pbs", name: "PBS", number: 106, aliases: ["PBS"], kidsSafe: false },
            { id: "pbskids", name: "PBS Kids", number: 107, aliases: ["PBS Kids"], kidsSafe: true },
            { id: "disneyxd", name: "Disney XD", number: 108, aliases: ["Disney XD", "Jetix"], kidsSafe: true },
            { id: "touchstone", name: "Touchstone", number: 109, aliases: [], kidsSafe: false },
            { id: "syfy", name: "SYFY", number: 110, aliases: ["Syfy", "Sci Fi Channel", "SciFi", "Sci-Fi Channel"], kidsSafe: false },
            { id: "usa", name: "USA", number: 111, aliases: ["USA Network", "USA"], kidsSafe: false },
            { id: "tnt", name: "TNT", number: 112, aliases: ["TNT"], kidsSafe: false },
            { id: "tbs", name: "TBS", number: 113, aliases: ["TBS"], kidsSafe: false },
            { id: "fx", name: "FX", number: 114, aliases: ["FX", "FXX", "FX Networks"], kidsSafe: false },
            { id: "amc", name: "AMC", number: 115, aliases: ["AMC"], kidsSafe: false },
            { id: "mtv", name: "MTV", number: 116, aliases: ["MTV"], kidsSafe: false },
            { id: "comedycentral", name: "Comedy Central", number: 117, aliases: ["Comedy Central"], kidsSafe: false },
            { id: "freeform", name: "ABC Family", number: 118, aliases: ["ABC Family", "Freeform", "Fox Family"], kidsSafe: false },
            { id: "paramountnet", name: "Paramount Network", number: 119, aliases: ["Paramount Network", "Spike", "Spike TV"], kidsSafe: false },
            { id: "discovery", name: "Discovery", number: 120, aliases: ["Discovery", "Discovery Channel"], kidsSafe: false },
            { id: "history", name: "History", number: 121, aliases: ["History", "History Channel", "The History Channel"], kidsSafe: false },
            { id: "food", name: "Food Network", number: 122, aliases: ["Food Network"], kidsSafe: false },
            { id: "hgtv", name: "HGTV", number: 123, aliases: ["HGTV"], kidsSafe: false },
            { id: "adultswim", name: "Adult Swim", number: 124, aliases: ["Adult Swim", "[adult swim]"], kidsSafe: false },
            { id: "nickjr", name: "Nick Jr.", number: 125, aliases: ["Nick Jr.", "Nick Jr"], kidsSafe: true },
            { id: "disneyjr", name: "Disney Junior", number: 126, aliases: ["Disney Junior", "Disney Jr.", "Playhouse Disney"], kidsSafe: true },
            { id: "hbo", name: "HBO", number: 130, aliases: ["HBO", "Cinemax"], kidsSafe: false },
        ],
        _aliasIndex: null,
        canonicalNetwork(raw) {
            if (!raw) return null;
            if (!this._aliasIndex) {
                this._aliasIndex = {};
                for (const n of this.networks) {
                    for (const a of n.aliases) this._aliasIndex[norm(a)] = n.id;
                }
            }
            return this._aliasIndex[norm(raw)] || null;
        },
        builtInChannels() {
            const weekdays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
            const kidRatings = ["G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG"];
            const mk = (id, number, name, kind, rules, opts = {}) => ({
                id,
                number,
                name,
                kind,
                rules: createRules(rules),
                order: opts.order || 'SHUFFLE',
                blocks: opts.blocks || [],
                kidsSafe: !!opts.kidsSafe,
                hidden: !!opts.hidden,
                movieEveryMs: opts.movieEveryMs || (6 * 3600000),
            });

            const net = this.networks.map(n => {
                if (n.id === 'boomerang') {
                    return mk(n.id, n.number, n.name, 'CURATED', { networks: [n.id], titles: Curated.boomerangShows }, { kidsSafe: true });
                }
                if (n.id === 'toonami') {
                    return mk(n.id, n.number, n.name, 'CURATED', { networks: [n.id], titles: Curated.toonamiShows }, { kidsSafe: true });
                }
                if (n.id === 'cn') {
                    return mk(n.id, n.number, n.name, 'NETWORK', { networks: [n.id] }, {
                        kidsSafe: true,
                        blocks: [{ days: weekdays, start: '16:00', end: '19:00', rules: createRules({ titles: Curated.toonamiShows }) }],
                    });
                }
                return mk(n.id, n.number, n.name, 'NETWORK', { networks: [n.id] }, { kidsSafe: n.kidsSafe });
            });

            const themes = [
                mk("horror", 301, "Horror", "THEME", { genres: ["Horror", "Thriller"] }),
                mk("scifi", 302, "Sci-Fi", "THEME", { genres: ["Science Fiction", "Sci-Fi & Fantasy"] }),
                mk("comedy", 303, "Comedy", "THEME", { genres: ["Comedy"] }),
                mk("action", 304, "Action", "THEME", { genres: ["Action", "Adventure"] }),
                mk("family", 305, "Family", "THEME", { genres: ["Family", "Animation"], ratings: kidRatings }, { kidsSafe: true }),
                mk("80s", 306, "80s Movies", "THEME", { decades: [1980], kinds: ["MOVIE"] }),
                mk("90s", 307, "90s Movies", "THEME", { decades: [1990], kinds: ["MOVIE"] }),
                mk("saturday", 308, "Saturday Morning", "THEME", {
                    genres: ["Animation"],
                    ratings: ["TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G"],
                    kinds: ["EPISODE"],
                }, { kidsSafe: true }),
            ];
            return net.concat(themes);
        },
        validateNumbers(defs) {
            const byNum = new Map();
            for (const d of defs) {
                if (!byNum.has(d.number)) byNum.set(d.number, []);
                byNum.get(d.number).push(d);
            }
            const nums = Array.from(byNum.keys()).sort((a, b) => a - b);
            const errs = [];
            for (const num of nums) {
                const clash = byNum.get(num);
                if (clash.length > 1) {
                    errs.push(`Channel ${num} is used by ${clash.map(c => c.name).join(' and ')}`);
                }
            }
            return errs;
        }
    };

    const Router = {
        _families: null,
        _init() {
            if (this._families) return;
            this._families = {};
            const add = (list, fam) => list.forEach(s => { this._families[norm(s)] = fam; });
            add([
                "Disney+", "Walt Disney Pictures", "Walt Disney Animation Studios", "Walt Disney Television",
                "Disney Television Animation", "Pixar", "Marvel Studios", "Marvel Animation", "Marvel Television",
                "Lucasfilm", "Lucasfilm Animation"
            ], 'DISNEY');
            add(["Paramount+", "Nickelodeon Animation Studio", "Nickelodeon Productions", "Nickelodeon Movies"], 'PARAMOUNT');
            add(["Max", "HBO Max", "Warner Bros. Animation", "Cartoon Network Studios", "Williams Street"], 'WARNER');
            add(["Peacock"], 'NBCU');
            add(["Hulu"], 'HULU');
            add(["Netflix", "Prime Video", "Amazon", "Amazon Studios", "Amazon MGM Studios", "Apple TV+", "Apple TV"], 'STREAMER');
        },
        route(item) {
            this._init();
            const fam = (item.network && this._families[norm(item.network)]) ||
                (item.studio && this._families[norm(item.studio)]) || null;
            if (!fam) return null;

            const rating = item.contentRating ? norm(item.contentRating) : null;
            const genres = new Set((item.genres || []).map(norm));
            const isPreschool = rating === 'tv-y';
            const isFamily = ['tv-y', 'tv-y7', 'tv-y7-fv', 'tv-g', 'g', 'tv-pg', 'pg'].includes(rating);
            const animated = genres.has('animation');
            const action = ['action', 'adventure', 'science fiction', 'sci-fi & fantasy', 'action & adventure'].some(g => genres.has(g));
            const scifi = ['science fiction', 'sci-fi & fantasy', 'fantasy', 'horror'].some(g => genres.has(g));
            const comedy = genres.has('comedy');
            const marvelOrLucas = [item.studio, item.network].filter(Boolean).some(s => {
                const k = norm(s);
                return k.startsWith('marvel') || k.startsWith('lucasfilm');
            });

            switch (fam) {
                case 'DISNEY':
                    if (isPreschool) return 'disneyjr';
                    if (isFamily && animated && (action || marvelOrLucas)) return 'disneyxd';
                    if (isFamily) return 'disney';
                    return 'touchstone';
                case 'PARAMOUNT':
                    if (isPreschool) return 'nickjr';
                    if (isFamily) return 'nick';
                    if (comedy) return 'comedycentral';
                    return 'paramountnet';
                case 'WARNER':
                    if (isFamily) return 'cn';
                    if (animated) return 'adultswim';
                    return 'hbo';
                case 'NBCU':
                    return scifi ? 'syfy' : 'usa';
                case 'HULU':
                    return 'fx';
                case 'STREAMER':
                    if (isPreschool) return 'pbskids';
                    if (isFamily && animated) return 'cn';
                    if (scifi) return 'syfy';
                    if (comedy) return 'comedycentral';
                    if (genres.has('documentary')) return genres.has('history') ? 'history' : 'discovery';
                    return 'usa';
            }
            return null;
        }
    };

    const Resolver = {
        DORMANT_MS: 4 * 3600000,
        MIN_ITEM_MS: 60000,
        networkOf(item) {
            return Catalog.canonicalNetwork(item.network) ||
                Catalog.canonicalNetwork(item.studio) ||
                Router.route(item);
        },
        inBase(def, item) {
            return rulesMatches(def.rules, item, this.networkOf(item));
        },
        pool(def, library) {
            return library.filter(item => {
                if (item.durationMs < this.MIN_ITEM_MS) return false;
                const nid = this.networkOf(item);
                if (rulesMatches(def.rules, item, nid)) return true;
                return (def.blocks || []).some(b => rulesMatches(b.rules, item, nid));
            });
        },
        isDormant(pool) {
            let sum = 0;
            for (const i of pool) sum += i.durationMs;
            return sum < this.DORMANT_MS;
        }
    };

    const Facets = {
        from(items) {
            const ci = (a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' });
            const uniq = (arr) => Array.from(new Set(arr));
            return {
                genres: uniq(items.flatMap(i => i.genres || [])).sort(ci),
                shows: uniq(items.filter(i => i.kind === 'EPISODE' && i.showTitle).map(i => i.showTitle)).sort(ci),
                labels: uniq(items.flatMap(i => i.labels || [])).sort(ci),
                collections: uniq(items.flatMap(i => i.collections || [])).sort(ci),
                decades: uniq(items.filter(i => i.year).map(i => Math.floor(i.year / 10) * 10)).sort((a, b) => a - b),
                ratings: uniq(items.map(i => i.contentRating).filter(Boolean)).sort(ci),
                networks: uniq(items.map(i => Resolver.networkOf(i)).filter(Boolean)).sort((a, b) => {
                    const na = (Catalog.networks.find(n => n.id === a) || {}).number || 999;
                    const nb = (Catalog.networks.find(n => n.id === b) || {}).number || 999;
                    return na - nb;
                }),
            };
        }
    };

    function validateDraft(draft, others) {
        const errors = [];
        if (!draft.name || !draft.name.trim()) errors.push("Give the channel a name");
        if (draft.number < 1 || draft.number > 999) errors.push("Channel number must be 1–999");
        const all = others.filter(o => o.id !== draft.id).concat([draft]);
        return errors.concat(Catalog.validateNumbers(all));
    }

    const GRID_MS = 30 * 60000;
    const TAIL_SKIP_MS = 5000;
    const TOP_UP_BELOW_MS = 24 * 3600000;

    function gridFloor(ms) {
        return Math.floor(ms / GRID_MS) * GRID_MS;
    }

    function gridCeil(ms) {
        return Math.ceil(ms / GRID_MS) * GRID_MS;
    }

    // Deterministic seeded PRNG matching Scheduler.kt behavior
    function makeRng(seed) {
        let s = (Number(seed) | 0) ^ 0x5DEECE66D;
        return {
            nextInt(bound) {
                s = (Math.imul(s, 1664525) + 1013904223) | 0;
                return Math.abs(s) % bound;
            }
        };
    }

    function shuffleWithRng(arr, rng) {
        const out = arr.slice();
        for (let i = out.length - 1; i > 0; i--) {
            const j = rng.nextInt(i + 1);
            const tmp = out[i];
            out[i] = out[j];
            out[j] = tmp;
        }
        return out;
    }

    function strHashCode(str) {
        let h = 0;
        for (let i = 0; i < str.length; i++) {
            h = (Math.imul(31, h) + str.charCodeAt(i)) | 0;
        }
        return h;
    }

    const Scheduler = {
        extend(def, pool, state, lastEndMs, nowMs, horizonMs = 48 * 3600000) {
            if (!pool || pool.length === 0) return { entries: [], state };
            const items = pool.slice().sort((a, b) => a.key.localeCompare(b.key));
            const baseFiltered = items.filter(i => Resolver.inBase(def, i));
            const base = baseFiltered.length > 0 ? baseFiltered : items;
            const blockSets = (def.blocks || []).map(b => items.filter(i => rulesMatches(b.rules, i, Resolver.networkOf(i))));

            const floor = gridFloor(nowMs);
            let cursor = (lastEndMs == null || lastEndMs < floor) ? floor : lastEndMs;
            const end = nowMs + horizonMs;

            const run = {
                seed: state.seed || strHashCode(def.id),
                deck: (state.deck || []).slice(),
                recent: (state.recent || []).slice(),
                pointers: Object.assign({}, state.episodePointers || {}),
                msSinceMovie: state.msSinceMovie || 0,
                rngCalls: state.rngCalls || 0,
                rng() {
                    return makeRng(this.seed + (this.rngCalls++));
                }
            };

            const out = [];
            while (cursor < end) {
                const bi = (def.blocks || []).findIndex(b => blockActiveAt(b, cursor));
                const candidates = (bi >= 0 && blockSets[bi].length > 0) ? blockSets[bi] : base;
                const item = this._pick(run, def, candidates);

                const progEnd = cursor + item.durationMs;
                const slotEnd = gridCeil(progEnd);
                out.push({ channelId: def.id, startMs: cursor, endMs: progEnd, kind: 'PROGRAM', itemKey: item.key });
                if (slotEnd > progEnd) {
                    out.push({ channelId: def.id, startMs: progEnd, endMs: slotEnd, kind: 'FILLER', itemKey: null });
                }

                run.recent.push(item.key);
                if (run.recent.length > 200) run.recent = run.recent.slice(run.recent.length - 200);
                run.msSinceMovie = item.kind === 'MOVIE' ? (slotEnd - cursor) : (run.msSinceMovie + (slotEnd - cursor));
                cursor = slotEnd;
            }

            return {
                entries: out,
                state: {
                    seed: run.seed,
                    deck: run.deck,
                    recent: run.recent,
                    episodePointers: run.pointers,
                    msSinceMovie: run.msSinceMovie,
                    rngCalls: run.rngCalls,
                }
            };
        },
        _pick(run, def, candidates) {
            const movies = candidates.filter(i => i.kind === 'MOVIE');
            const episodes = candidates.filter(i => i.kind === 'EPISODE');
            let wantMovie = false;
            if (movies.length === 0) wantMovie = false;
            else if (episodes.length === 0) wantMovie = true;
            else wantMovie = run.msSinceMovie >= (def.movieEveryMs || (6 * 3600000));
            return wantMovie ? this._pickMovie(run, def, movies) : this._pickEpisode(run, def, episodes);
        },
        _pickMovie(run, def, movies) {
            const byKey = new Map(movies.map(m => [m.key, m]));
            let idx = run.deck.findIndex(k => byKey.has(k));
            if (idx < 0) {
                run.deck.push(...this._freshDeck(run, def, movies));
                idx = run.deck.findIndex(k => byKey.has(k));
            }
            const key = run.deck.splice(idx, 1)[0];
            return byKey.get(key);
        },
        _freshDeck(run, def, movies) {
            if (def.order === 'IN_ORDER') {
                return movies.slice().sort((a, b) => (a.year || 0) - (b.year || 0) || a.title.localeCompare(b.title) || a.key.localeCompare(b.key)).map(m => m.key);
            }
            const keys = movies.map(m => m.key);
            const keySet = new Set(keys);
            const holdBackCount = Math.max(1, Math.floor(keys.length / 5));
            const holdBack = new Set();
            for (let i = run.recent.length - 1; i >= 0 && holdBack.size < holdBackCount; i--) {
                const k = run.recent[i];
                if (keySet.has(k)) holdBack.add(k);
            }
            const rest = keys.filter(k => !holdBack.has(k));
            if (rest.length === 0) return shuffleWithRng(keys, run.rng());
            return shuffleWithRng(rest, run.rng()).concat(shuffleWithRng(Array.from(holdBack).sort(), run.rng()));
        },
        _pickEpisode(run, def, episodes) {
            const showMap = new Map();
            for (const ep of episodes) {
                const sk = ep.showKey || ep.key;
                if (!showMap.has(sk)) showMap.set(sk, []);
                showMap.get(sk).push(ep);
            }
            const sortedKeys = Array.from(showMap.keys()).sort();
            let showKey;
            if (def.order === 'IN_ORDER') {
                const ordered = sortedKeys.slice().sort((a, b) => {
                    const ta = showMap.get(a)[0].showTitle || a;
                    const tb = showMap.get(b)[0].showTitle || b;
                    return ta.localeCompare(tb);
                });
                const rr = run.pointers.__round_robin || 0;
                run.pointers.__round_robin = rr + 1;
                showKey = ordered[rr % ordered.length];
            } else {
                const total = episodes.length;
                let r = run.rng().nextInt(total);
                for (const k of sortedKeys) {
                    const eps = showMap.get(k);
                    if (r < eps.length) {
                        showKey = k;
                        break;
                    }
                    r -= eps.length;
                }
            }
            const eps = showMap.get(showKey).slice().sort((a, b) =>
                (a.season || 0) - (b.season || 0) || (a.episode || 0) - (b.episode || 0) || a.key.localeCompare(b.key)
            );
            const ptr = run.pointers[showKey] || 0;
            run.pointers[showKey] = ptr + 1;
            return eps[ptr % eps.length];
        }
    };

    function locate(entries, nowMs) {
        let lo = 0, hi = entries.length - 1, found = -1;
        while (lo <= hi) {
            const mid = (lo + hi) >>> 1;
            const e = entries[mid];
            if (nowMs < e.startMs) hi = mid - 1;
            else if (nowMs >= e.endMs) lo = mid + 1;
            else { found = mid; break; }
        }
        if (found < 0) return null;
        const e = entries[found];
        const next = entries[found + 1] || null;
        if (e.kind === 'PROGRAM' && (e.endMs - nowMs) < TAIL_SKIP_MS && next != null) {
            return { entry: next, offsetMs: 0, next: entries[found + 2] || null };
        }
        return { entry: e, offsetMs: nowMs - e.startMs, next };
    }

    function needsTopUp(lastEndMs, nowMs) {
        return lastEndMs == null || (lastEndMs - nowMs) < TOP_UP_BELOW_MS;
    }

    function guideBlocks(entries, fromMs, toMs) {
        const out = [];
        const sorted = entries.slice().sort((a, b) => a.startMs - b.startMs);
        for (const e of sorted) {
            if (e.endMs <= fromMs || e.startMs >= toMs) continue;
            const s = Math.max(e.startMs, fromMs);
            const end = Math.min(e.endMs, toMs);
            const last = out[out.length - 1];
            if (e.kind === 'FILLER' && last && last.endMs === s) {
                last.endMs = end;
            } else {
                out.push({ startMs: s, endMs: end, itemKey: e.kind === 'PROGRAM' ? e.itemKey : null });
            }
        }
        return out;
    }

    const PlexParse = {
        _tags(obj, key) {
            return Array.from(new Set(((obj && obj[key]) || []).map(t => t && t.tag).filter(Boolean)));
        },
        _tmdb(obj) {
            const guids = (obj && obj.Guid) || [];
            for (const g of guids) {
                if (g && typeof g.id === 'string' && g.id.startsWith('tmdb://')) {
                    return g.id.slice('tmdb://'.length);
                }
            }
            return null;
        },
        pin(data) {
            const o = typeof data === 'string' ? JSON.parse(data) : data;
            return { id: Number(o.id || 0), code: String(o.code || ''), authToken: o.authToken || null };
        },
        servers(data) {
            const arr = typeof data === 'string' ? JSON.parse(data) : data;
            if (!Array.isArray(arr)) return [];
            return arr
                .filter(o => String(o.provides || '').split(',').map(s => s.trim()).includes('server'))
                .map(o => ({
                    name: String(o.name || ''),
                    clientId: String(o.clientIdentifier || ''),
                    accessToken: o.accessToken || null,
                    connections: (o.connections || []).filter(c => c && c.uri).map(c => ({
                        uri: String(c.uri),
                        local: !!c.local,
                        relay: !!c.relay,
                    })),
                }));
        },
        sections(data) {
            const o = typeof data === 'string' ? JSON.parse(data) : data;
            const mc = (o && o.MediaContainer) || {};
            return (mc.Directory || []).map(d => ({
                key: String(d.key || ''),
                type: String(d.type || ''),
                title: String(d.title || ''),
            }));
        },
        totalSize(data) {
            const o = typeof data === 'string' ? JSON.parse(data) : data;
            const mc = (o && o.MediaContainer) || {};
            return Number(mc.totalSize ?? mc.size ?? 0);
        },
        movies(data) {
            const o = typeof data === 'string' ? JSON.parse(data) : data;
            const mc = (o && o.MediaContainer) || {};
            return (mc.Metadata || []).map(m => {
                const media = (m.Media || [])[0] || {};
                const part = (media.Part || [])[0] || {};
                return {
                    key: String(m.ratingKey || ''),
                    kind: 'MOVIE',
                    title: String(m.title || ''),
                    year: m.year != null ? Number(m.year) : null,
                    durationMs: Number(m.duration || media.duration || 0),
                    genres: this._tags(m, 'Genre'),
                    network: null,
                    studio: m.studio || null,
                    contentRating: m.contentRating || null,
                    showKey: null,
                    showTitle: null,
                    season: null,
                    episode: null,
                    tmdbId: this._tmdb(m),
                    labels: this._tags(m, 'Label'),
                    collections: this._tags(m, 'Collection'),
                    partKey: part.key || null,
                    container: media.container || null,
                    videoCodec: media.videoCodec || null,
                    audioCodec: media.audioCodec || null,
                    thumb: m.thumb || null,
                };
            });
        },
        shows(data) {
            const o = typeof data === 'string' ? JSON.parse(data) : data;
            const mc = (o && o.MediaContainer) || {};
            return (mc.Metadata || []).map(s => ({
                key: String(s.ratingKey || ''),
                title: String(s.title || ''),
                year: s.year != null ? Number(s.year) : null,
                studio: s.studio || null,
                genres: this._tags(s, 'Genre'),
                contentRating: s.contentRating || null,
                tmdbId: this._tmdb(s),
                labels: this._tags(s, 'Label'),
                collections: this._tags(s, 'Collection'),
            }));
        },
        episodes(data, showsById) {
            const o = typeof data === 'string' ? JSON.parse(data) : data;
            const mc = (o && o.MediaContainer) || {};
            return (mc.Metadata || []).map(e => {
                const showKey = e.grandparentRatingKey ? String(e.grandparentRatingKey) : null;
                const show = (showKey && showsById[showKey]) || null;
                const media = (e.Media || [])[0] || {};
                const part = (media.Part || [])[0] || {};
                return {
                    key: String(e.ratingKey || ''),
                    kind: 'EPISODE',
                    title: String(e.title || ''),
                    year: e.year != null ? Number(e.year) : (show ? show.year : null),
                    durationMs: Number(e.duration || media.duration || 0),
                    genres: show ? show.genres : [],
                    network: show ? show.studio : null,
                    studio: show ? show.studio : null,
                    contentRating: e.contentRating || (show ? show.contentRating : null),
                    showKey,
                    showTitle: e.grandparentTitle || (show ? show.title : null),
                    season: e.parentIndex != null ? Number(e.parentIndex) : null,
                    episode: e.index != null ? Number(e.index) : null,
                    tmdbId: show ? show.tmdbId : null,
                    labels: show ? show.labels : [],
                    collections: show ? show.collections : [],
                    partKey: part.key || null,
                    container: media.container || null,
                    videoCodec: media.videoCodec || null,
                    audioCodec: media.audioCodec || null,
                    thumb: e.thumb || e.grandparentThumb || null,
                };
            });
        }
    };

    const Playback = {
        containers: new Set(["mp4", "m4v", "mov", "mkv", "ts"]),
        video: new Set(["h264", "hevc", "mpeg2video", "vp9", "av1"]),
        audio: new Set(["aac", "ac3", "eac3", "mp3", "opus", "flac", "vorbis"]),
        canDirectPlay(item) {
            return !!(
                item &&
                item.partKey &&
                item.container && this.containers.has(item.container.toLowerCase()) &&
                item.videoCodec && this.video.has(item.videoCodec.toLowerCase()) &&
                item.audioCodec && this.audio.has(item.audioCodec.toLowerCase())
            );
        },
        directUrl(base, item, token) {
            return `${base}${item.partKey}?X-Plex-Token=${encodeURIComponent(token)}`;
        },
        transcodeUrl(base, item, token, clientId, session, offsetMs) {
            const offsetSec = Math.floor(offsetMs / 1000);
            return `${base}/video/:/transcode/universal/start.m3u8` +
                `?path=${encodeURIComponent('/library/metadata/' + item.key)}` +
                `&mediaIndex=0&partIndex=0&protocol=hls` +
                `&offset=${offsetSec}` +
                `&fastSeek=1&directPlay=0&directStream=1&directStreamAudio=0` +
                `&videoResolution=1920x1080&maxVideoBitrate=20000&subtitles=none` +
                `&session=${encodeURIComponent(session)}&X-Plex-Session-Identifier=${encodeURIComponent(session)}` +
                `&X-Plex-Client-Identifier=${encodeURIComponent(clientId)}&X-Plex-Product=Rerun&X-Plex-Platform=Samsung%20Tizen` +
                `&X-Plex-Token=${encodeURIComponent(token)}`;
        },
        connectionOrder(server) {
            return (server.connections || []).slice().sort((a, b) => {
                const rank = c => c.relay ? 2 : (c.local ? 0 : 1);
                return rank(a) - rank(b);
            });
        },
        planPlayback(item, base, token, clientId, session, offsetMs) {
            if (!item) return { kind: 'UP_NEXT', reason: 'missing' };
            if (this.canDirectPlay(item)) {
                return {
                    kind: 'DIRECT',
                    url: this.directUrl(base, item, token),
                    fallbackUrl: this.transcodeUrl(base, item, token, clientId, session, offsetMs),
                    startMs: offsetMs,
                };
            }
            return {
                kind: 'TRANSCODED',
                url: this.transcodeUrl(base, item, token, clientId, session, offsetMs),
            };
        }
    };

    global.RerunCore = {
        Curated,
        Catalog,
        Router,
        Resolver,
        Facets,
        validateDraft,
        createRules,
        rulesMatches,
        GRID_MS,
        TAIL_SKIP_MS,
        gridFloor,
        gridCeil,
        Scheduler,
        locate,
        needsTopUp,
        guideBlocks,
        PlexParse,
        Playback,
        strHashCode,
    };
})(window);
