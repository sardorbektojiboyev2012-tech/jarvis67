// JARVIS media host: Instagram uchun ochiq HTTPS URL beradi. Bog'liqliksiz (faqat Node 18+).
// Ishga tushirish: API_KEY=maxfiy BASE_URL=https://sizning-domen.uz node server.js
const http = require('http'), fs = require('fs'), path = require('path'), crypto = require('crypto');
const PORT = process.env.PORT || 8080, API_KEY = process.env.API_KEY, BASE_URL = (process.env.BASE_URL || '').replace(/\/$/, '');
const DIR = process.env.FILES_DIR || path.join(__dirname, 'files'), TTL_MS = 3 * 24 * 3600 * 1000;
if (!API_KEY || API_KEY.length < 16) { console.error('API_KEY (kamida 16 belgi) kerak'); process.exit(1); }
fs.mkdirSync(DIR, { recursive: true });
const safeEq = (a, b) => { const x = Buffer.from(a || ''), y = Buffer.from(b); return x.length === y.length && crypto.timingSafeEqual(x, y); };
const MIME = { '.mp4': 'video/mp4', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png' };

http.createServer((req, res) => {
  const send = (c, o) => { res.writeHead(c, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(o)); };
  if (req.method === 'GET' && req.url === '/health') return send(200, { ok: true });
  if (req.method === 'PUT' && req.url.startsWith('/upload/')) {
    if (!safeEq(req.headers['x-api-key'], API_KEY)) return send(401, { error: 'unauthorized' });
    const ext = path.extname(decodeURIComponent(req.url.slice(8))).toLowerCase();
    if (!MIME[ext]) return send(400, { error: 'faqat mp4/jpg/png' });
    const name = crypto.randomUUID() + ext, tmp = path.join(DIR, name + '.part');
    const out = fs.createWriteStream(tmp); let size = 0, aborted = false;
    req.on('data', d => { size += d.length; if (size > 300 * 1024 * 1024 && !aborted) { aborted = true; out.destroy(); req.destroy(); fs.unlink(tmp, () => {}); } });
    req.pipe(out);
    out.on('finish', () => { if (aborted) return; fs.renameSync(tmp, path.join(DIR, name)); send(200, { url: `${BASE_URL}/files/${name}` }); });
    out.on('error', () => send(500, { error: 'write failed' }));
    return;
  }
  if (req.method === 'GET' && req.url.startsWith('/files/')) {
    const name = path.basename(req.url.slice(7)), f = path.join(DIR, name);
    if (!MIME[path.extname(name)] || !fs.existsSync(f)) return send(404, { error: 'not found' });
    const st = fs.statSync(f), range = req.headers.range;
    if (range) { const m = /bytes=(\d*)-(\d*)/.exec(range); const s = parseInt(m[1] || '0'), e = m[2] ? parseInt(m[2]) : st.size - 1;
      res.writeHead(206, { 'Content-Type': MIME[path.extname(name)], 'Content-Range': `bytes ${s}-${e}/${st.size}`, 'Accept-Ranges': 'bytes', 'Content-Length': e - s + 1 });
      return fs.createReadStream(f, { start: s, end: e }).pipe(res); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(name)], 'Content-Length': st.size, 'Accept-Ranges': 'bytes' });
    return fs.createReadStream(f).pipe(res);
  }
  send(404, { error: 'not found' });
}).listen(PORT, () => console.log('JARVIS backend :' + PORT));

setInterval(() => fs.readdirSync(DIR).forEach(n => { const f = path.join(DIR, n); try { if (Date.now() - fs.statSync(f).mtimeMs > TTL_MS) fs.unlinkSync(f); } catch (e) {} }), 3600 * 1000);
