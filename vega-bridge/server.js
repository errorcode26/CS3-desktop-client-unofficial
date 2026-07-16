const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const fs = require('fs');
const path = require('path');
const nodeCrypto = require('crypto');

const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

const offlineMapping = {
  "Moviesmod": { "name": "Moviesmod", "url": "https://moviesmod.farm" },
  "Animeflix": { "name": "Animeflix", "url": "https://ww3.animeflix.ltd" },
  "Topmovies": { "name": "Topmovies", "url": "https://moviesleech.asia" },
  "UhdMovies": { "name": "UhdMovies", "url": "https://uhdmovies.casa" },
  "filepress": { "name": "filepress", "url": "https://new14.filepress.store" },
  "Vega": { "name": "vegamovies", "url": "https://vegamovies.navy" },
  "lux": { "name": "luxmovies", "url": "https://rogmovies.rest" },
  "drive": { "name": "moviesDrive", "url": "https://new5.moviesdrives.my/" },
  "multi": { "name": "multimovies", "url": "https://multimovies.fyi" },
  "w4u": { "name": "world4ufree", "url": "https://world4ufree.at" },
  "extra": { "name": "extraMovies", "url": "https://extramovies.ist" },
  "hdhub": { "name": "hdhub4u", "url": "https://new3.hdhub4u.cl" },
  "kat": { "name": "katmovieshd", "url": "https://new.katmoviehd.top" },
  "dc": { "name": "dramacool", "url": "https://dramacool.org.ro" },
  "dooflix": { "name": "dooflix", "url": "https://dooflixpanel.com" },
  "autoEmbed": { "name": "autoEmbed", "url": "https://autoembed.cc" },
  "aed": { "name": "autoEmbedDrama", "url": "https://watch-drama.autoembed.cc" },
  "aea": { "name": "autoEmbedAnime", "url": "https://watch-anime.autoembed.cc" },
  "tokyoinsider": { "name": "tokyoinsider", "url": "https://www.tokyoinsider.com" },
  "consumet": { "name": "consumet", "url": "https://consumet.zendax.tech" },
  "nfMirror": { "name": "nfMirror", "url": "https://net22.cc" },
  "primewire": { "name": "primewire", "url": "https://primewire.pw" },
  "rive": { "name": "rive", "url": "https://www.rivestream.app" },
  "kissKh": { "name": "kissKh", "url": "https://kisskh.do" },
  "vadapav": { "name": "vadapav", "url": "https://vadapav.mov" },
  "cinemaLuxe": { "name": "cinemaLuxe", "url": "https://cinemalux.cyou" },
  "showbox": { "name": "showbox", "url": "https://www.showbox.media" },
  "animerulz": { "name": "animerulz", "url": "https://animerulz.co" },
  "moviesapi": { "name": "moviesapi", "url": "https://vidspark.to" },
  "ridomovies": { "name": "ridomovies", "url": "https://ridomovies.tv" },
  "protonMovies": { "name": "protonMovies", "url": "https://m.protonmovies.space" },
  "dramafull": { "name": "dramafull", "url": "https://dramafull.cc" },
  "nfCookie": { "name": "nf cookie verify", "url": "https://userverify.netmirror.app" },
  "embedsu": { "name": "embedsu", "url": "https://moviemaze.cc" },
  "filmyfly": { "name": "flimyfly", "url": "https://new2.filmyfiy.org" },
  "4khdhub": { "name": "4khdhub", "url": "https://4khdhub.one" },
  "moviezwap": { "name": "moviezwap", "url": "https://www.moviezwap.land/" },
  "9xflix": { "name": "9xflix", "url": "https://soft-water-2a42.flixoflixx.workers.dev" },
  "movieBox": { "name": "MovieBox", "url": "https://api6.aoneroom.com" },
  "cinevood": { "name": "Cinevood", "url": "https://kmmovies.space" },
  "kmmovies": { "name": "kmmovies", "url": "https://kmmovies.lol" },
  "zeefliz": { "name": "zeefliz", "url": "https://zeefliz.beer" },
  "katmoviefix": { "name": "katmoviefix", "url": "https://katmoviefix.study" },
  "movies4u": { "name": "movies4u", "url": "https://movies4u.ss" },
  "joya9tv": { "name": "joya9tv1", "url": "https://joya9tv1.com" },
  "skymovieshd": { "name": "skyMovesHd", "url": "https://skymovieshd.ceo" },
  "1cinevood": { "name": "cinewood", "url": "https://two.1cinevood.live" },
  "uniquestream": { "name": "uniquestream", "url": "https://anime.uniquestream.net" }
};

let cachedMapping = null;
let lastFetchTime = 0;
const CACHE_DURATION = 60 * 60 * 1000; // 1 hour

async function getBaseUrl(provider) {
    if (!provider) return '';
    
    let mapping = offlineMapping;
    if (!cachedMapping || (Date.now() - lastFetchTime > CACHE_DURATION)) {
        try {
            const response = await axios.get('https://himanshu8443.github.io/providers/modflix.json', { timeout: 3000 });
            if (response.data && typeof response.data === 'object') {
                cachedMapping = response.data;
                lastFetchTime = Date.now();
            }
        } catch (err) {
            console.error("Failed to fetch online base URLs, using offline cache:", err.message);
        }
    }
    
    if (cachedMapping) {
        mapping = cachedMapping;
    }

    const pk = provider.toLowerCase();
    
    // 1. Direct match
    if (mapping[provider]) return mapping[provider].url;
    
    // 2. Case-insensitive exact match
    let matchedKey = Object.keys(mapping).find(k => k.toLowerCase() === pk);
    if (matchedKey) return mapping[matchedKey].url;

    // 3. Substring / Property Name matching
    matchedKey = Object.keys(mapping).find(k => {
        const mk = k.toLowerCase();
        const name = (mapping[k].name || "").toLowerCase();
        return pk.includes(mk) || mk.includes(pk) || pk.includes(name) || name.includes(pk);
    });

    if (matchedKey) return mapping[matchedKey].url;
    return '';
}

// Crypto wrapper — mirrors what the original Vega desktop app exposes as providerContext.Crypto
// Providers use this for AES decryption of obfuscated stream URLs.
const Crypto = {
    AES: {
        decrypt: (ciphertext, key, options = {}) => {
            try {
                // CryptoJS-compatible AES CBC decrypt
                const keyStr = typeof key === 'string' ? key : key.toString();
                const ivStr = options.iv ? options.iv.toString() : keyStr.slice(0, 16);
                const keyBuf = Buffer.from(keyStr.length === 32 ? keyStr : keyStr.padEnd(32, '\0').slice(0, 32));
                const ivBuf = Buffer.from(ivStr.padEnd(16, '\0').slice(0, 16));
                // Handle both base64 string and CryptoJS ciphertext object
                const cipherStr = typeof ciphertext === 'string' ? ciphertext
                    : ciphertext.toString ? ciphertext.toString() : String(ciphertext);
                const decipher = nodeCrypto.createDecipheriv('aes-256-cbc', keyBuf, ivBuf);
                decipher.setAutoPadding(true);
                const decrypted = Buffer.concat([decipher.update(Buffer.from(cipherStr, 'base64')), decipher.final()]);
                return { toString: (enc) => decrypted.toString(enc === 'utf8' || !enc ? 'utf8' : enc) };
            } catch (e) {
                return { toString: () => '' };
            }
        },
        encrypt: (plaintext, key, options = {}) => {
            try {
                const keyStr = typeof key === 'string' ? key : key.toString();
                const ivStr = options.iv ? options.iv.toString() : keyStr.slice(0, 16);
                const keyBuf = Buffer.from(keyStr.length === 32 ? keyStr : keyStr.padEnd(32, '\0').slice(0, 32));
                const ivBuf = Buffer.from(ivStr.padEnd(16, '\0').slice(0, 16));
                const cipher = nodeCrypto.createCipheriv('aes-256-cbc', keyBuf, ivBuf);
                const encrypted = Buffer.concat([cipher.update(Buffer.from(plaintext)), cipher.final()]);
                const b64 = encrypted.toString('base64');
                return { toString: () => b64, ciphertext: { toString: () => b64 } };
            } catch (e) {
                return { toString: () => '' };
            }
        },
    },
    enc: {
        Utf8: 'utf8',
        Base64: 'base64',
        Hex: 'hex',
    },
    MD5: (str) => {
        return { toString: () => nodeCrypto.createHash('md5').update(str).digest('hex') };
    },
    SHA256: (str) => {
        return { toString: () => nodeCrypto.createHash('sha256').update(str).digest('hex') };
    },
    lib: { WordArray: { random: (bytes) => ({ toString: () => nodeCrypto.randomBytes(bytes).toString('hex') }) } },
};

// openWebView stub — the original Vega app opens a real browser window for WAF challenges.
// We can't do that in a headless Node bridge, so we return an empty result.
// Providers that REQUIRE openWebView to solve Cloudflare will fail gracefully rather than crash.
const openWebView = async (url, options = {}) => {
    console.warn(`[Vega Bridge] openWebView called for: ${url} — WAF solving not supported in bridge mode.`);
    return { data: '', cookies: '', cookieMap: {}, userAgent: providerContext.commonHeaders['User-Agent'], expires: 0, url };
};

// Setup provider context — matches the original Vega desktop app's providerContext shape
const providerContext = {
    axios,
    cheerio,
    getBaseUrl,
    Crypto,
    // Keep Aes as alias so old provider code that uses providerContext.Aes also works
    Aes: Crypto,
    openWebView,
    commonHeaders: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0',
        'sec-ch-ua': '"Not_A Brand";v="8", "Chromium";v="120", "Microsoft Edge";v="120"',
        'sec-ch-ua-mobile': '?0',
        'sec-ch-ua-platform': '"Windows"',
        'Accept-Language': 'en-US,en;q=0.9',
    },
};

// Map of loaded providers
const providers = {};

// Helper to load provider module dynamically
function loadProviderModule(providerName, moduleName) {
    const distPath = path.join(__dirname, 'providers', providerName);
    const modulePath = path.join(distPath, `${moduleName}.js`);
    
    if (!fs.existsSync(modulePath)) {
        throw new Error(`Module ${moduleName} not found for provider ${providerName} at ${modulePath}`);
    }
    
    // Clear cache to allow hot-reloading if needed
    delete require.cache[require.resolve(modulePath)];
    return require(modulePath);
}

// Endpoint: /getProviders
app.get('/getProviders', (req, res) => {
    try {
        const providersDir = path.join(__dirname, 'providers');
        if (!fs.existsSync(providersDir)) fs.mkdirSync(providersDir, { recursive: true });
        
        const dirs = fs.readdirSync(providersDir, { withFileTypes: true })
            .filter(d => d.isDirectory())
            .map(d => d.name);
        res.json(dirs);
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: e.message });
    }
});

// Endpoint: /getAvailableProviders
// Lists all providers defined in any stored manifests
app.get('/getAvailableProviders', async (req, res) => {
    try {
        const manifestsDir = path.join(__dirname, 'manifests');
        if (!fs.existsSync(manifestsDir)) return res.json([]);

        const files = fs.readdirSync(manifestsDir);
        const available = [];

        for (const file of files) {
            if (file.endsWith('.json')) {
                try {
                    const content = JSON.parse(fs.readFileSync(path.join(manifestsDir, file), 'utf8'));
                    let repoUrl = content.url;
                    if (content.url.endsWith('.json')) {
                        repoUrl = content.url.substring(0, content.url.lastIndexOf('/'));
                    }
                    
                    for (const p of content.providers) {
                        available.push({
                            ...p,
                            baseUrl: repoUrl
                        });
                    }
                } catch (err) {
                    console.error('Error reading manifest file:', file, err);
                }
            }
        }
        res.json(available);
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: e.message });
    }
});

// Endpoint: /getSearchPosts
app.get('/getSearchPosts', async (req, res) => {
    try {
        const { provider, query, page = 1 } = req.query;
        if (!provider || !query) return res.status(400).json({ error: 'Missing provider or query' });
        
        const module = loadProviderModule(provider, 'posts');
        const results = await module.getSearchPosts({
            searchQuery: query,
            page: parseInt(page),
            providerValue: provider,
            signal: new AbortController().signal,
            providerContext
        });
        
        res.json(results);
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: e.message });
    }
});

// Endpoint: /getPosts (for home page / catalog)
app.get('/getPosts', async (req, res) => {
    try {
        const { provider, filter = '', page = 1 } = req.query;
        if (!provider) return res.status(400).json({ error: 'Missing provider' });
        
        const module = loadProviderModule(provider, 'posts');
        const results = await module.getPosts({
            filter: filter,
            page: parseInt(page),
            providerValue: provider,
            signal: new AbortController().signal,
            providerContext
        });
        
        res.json(results);
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: e.message });
    }
});

// Helper to undo the Kotlin app's automatic relative link resolution against 127.0.0.1
function restoreUrl(url) {
    if (!url) return url;
    return url.replace(/^http:\/\/127\.0\.0\.1/, '');
}

// Endpoint: /getMeta
app.get('/getMeta', async (req, res) => {
    try {
        const { provider, link } = req.query;
        if (!provider || !link) return res.status(400).json({ error: 'Missing provider or link' });
        
        const module = loadProviderModule(provider, 'meta');
        const results = await module.getMeta({
            link: restoreUrl(link),
            provider: provider,
            providerContext
        });
        
        res.json(results);
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: e.message });
    }
});

// Endpoint: /getEpisodes
app.get('/getEpisodes', async (req, res) => {
    try {
        const { provider, url } = req.query;
        if (!provider || !url) return res.status(400).json({ error: 'Missing provider or url' });
        
        const module = loadProviderModule(provider, 'episodes');
        const results = await module.getEpisodes({
            url: restoreUrl(url),
            providerContext
        });
        
        res.json(results);
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: e.message });
    }
});

// Endpoint: /getStream
app.get('/getStream', async (req, res) => {
    try {
        const { provider, link, type = 'movie' } = req.query;
        if (!provider || !link) return res.status(400).json({ error: 'Missing provider or link' });
        
        const module = loadProviderModule(provider, 'stream');
        const results = await module.getStream({
            link: restoreUrl(link),
            type: type,
            signal: new AbortController().signal,
            providerContext
        });
        
        res.json(results);
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: e.message });
    }
});

// Endpoint: /getCatalog
app.get('/getCatalog', (req, res) => {
    try {
        const { provider } = req.query;
        if (!provider) return res.status(400).json({ error: 'Missing provider' });
        
        const module = loadProviderModule(provider, 'catalog');
        res.json(module.catalog || []);
    } catch (e) {
        res.json([]);
    }
});

// Endpoint: /uninstallProvider
app.post('/uninstallProvider', (req, res) => {
    try {
        const { provider } = req.body;
        if (!provider) return res.status(400).json({ error: 'Missing provider name' });

        const providerDir = path.join(__dirname, 'providers', provider);
        if (fs.existsSync(providerDir)) {
            fs.rmSync(providerDir, { recursive: true, force: true });
            console.log(`[Vega Bridge] Uninstalled provider: ${provider}`);
            res.json({ success: true, message: `Successfully uninstalled ${provider}` });
        } else {
            res.status(404).json({ error: `Provider ${provider} not found` });
        }
    } catch (e) {
        console.error(e);
        res.status(500).json({ error: e.message });
    }
});

// Endpoint: /ping
app.get('/ping', (req, res) => {
    res.json({ status: 'ok' });
});

const PORT = process.argv[2] || process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Vega JS Bridge listening on port ${PORT}`);
});
