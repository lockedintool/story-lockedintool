// The owner's rules, checked automatically before ANY screens update reaches a phone (owner, 4 Nov 2026: 'everything that
// I told you, all the problems and fixes I ever told you about - the app is supposed to keep on checking that these
// mistakes don't ever happen again'). screens.yml runs this on every push; one failed rule = the update is stopped.
// Every owner-reported problem that a browser can see gets a check here, written in the owner's words.
// Run: node owner-checks.mjs [url]   (default http://localhost:8800/index.html)
import { chromium } from 'playwright';
const URL = process.argv[2] || 'http://localhost:8800/index.html';
const SIZES = [[360, 780], [393, 852], [412, 892]];
const SCREENS = ['dashboard', 'money', 'wallet', 'trading', 'notes', 'apps', 'community', 'layouts', 'subscription', 'settings', 'information', 'legal-privacy', 'legal-terms', 'legal-safety'];
const MAY_SCROLL = new Set(['settings', 'information', 'apps', 'legal-privacy', 'legal-terms', 'legal-safety']);   // owner: 'only settings and information' (+ the Apps list)
const UA = 'Mozilla/5.0 (Linux; Android 16; SM-A155F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36';
const fails = [];
// Never hang: if anything below stops answering, the check ends with a clear message instead of blocking every update.
const WATCHDOG = setTimeout(() => { console.log('OWNER RULES CHECK TIMED OUT (something stopped answering) - failing safe'); process.exit(1); }, 20 * 60 * 1000);
const within = (p, ms) => Promise.race([p, new Promise(r => setTimeout(() => r('timeout'), ms))]);
const fail = (rule, where, what) => { fails.push(`${rule} | ${where} | ${what}`); };
const b = await chromium.launch();
for (const [w, h] of SIZES) {
  const ctx = await b.newContext({ viewport: { width: w, height: h }, isMobile: true, hasTouch: true, userAgent: UA });
  await ctx.addInitScript(() => { try { localStorage.setItem('story-msg-account', JSON.stringify({ id: 'check', token: 't', pw: true, username: 'check' })); } catch (e) {} });
  const pg = await ctx.newPage();
  const errs = [];
  pg.on('pageerror', e => errs.push(String(e && e.message || e).slice(0, 200)));
  await pg.goto(URL); await pg.waitForTimeout(2500);
  for (const s of SCREENS) {
    errs.length = 0;
    await pg.evaluate(s => window.storyShow && window.storyShow(s, false), s);
    await pg.waitForTimeout(900);
    const r = await pg.evaluate(() => {
      const a = document.querySelector('#device-wrap #content-area') || document.getElementById('content-area'); if (!a) return null;
      const ab = a.getBoundingClientRect(), cs = getComputedStyle(a);
      let cut = [];
      a.querySelectorAll('*').forEach(e => { const er = e.getBoundingClientRect(); const st = getComputedStyle(e);
        if (er.height > 0 && er.width > 0 && st.visibility !== 'hidden' && st.position !== 'fixed' && er.bottom > ab.bottom + 3 && !e.closest('[class*="scroll"], .pay-list, .notes-ghosts')) cut.push(e.tagName + '.' + String(e.className || '').slice(0, 30)); });
      const shell = a.firstElementChild, sr = shell ? shell.getBoundingClientRect() : null;
      const text = a.innerText || '';
      return { cur: (window.state || {}).current, canScroll: cs.overflowY !== 'hidden' && a.scrollHeight > a.clientHeight + 1,
        cut: cut.slice(0, 3), fill: sr ? (sr.bottom - ab.top) / ab.height : 0, text };
    });
    const where = `${s} @ ${w}x${h}`;
    if (!r) { fail('screen opens', where, 'no content area'); continue; }
    // 'I told you only settings and information must have scrolling ... not even a tiny bit' (4 Nov 2026)
    if (!MAY_SCROLL.has(s) && r.canScroll) fail('no scrolling except Settings / Information', where, 'the screen scrolls');
    // 'make sure nothing is cutting'
    if (!MAY_SCROLL.has(s) && r.cut.length) fail('nothing cut', where, 'runs off the bottom: ' + r.cut.join(', '));
    // 'pages are always have to be full screen full' (3 Nov 2026)
    if (!MAY_SCROLL.has(s) && r.fill < 0.9) fail('every page fills the whole screen', where, 'fills only ' + Math.round(r.fill * 100) + '%');
    // 'Story is never shown anywhere in the app' (STORY is a separate company)
    if (/\bstor(y|ies)\b/i.test(r.text)) fail("no 'Story' anywhere", where, (r.text.match(/.{0,30}\bstor(y|ies)\b.{0,30}/i) || [''])[0]);
    // 'Focus should never be alone - should be called focus page' (4 Nov 2026)
    const lone = r.text.match(/.{0,20}\b(Focus|FOCUS)\b(?!\s*(page|PAGE)).{0,20}/);   // the page's name (the plain word 'focus' in a sentence is fine)
    if (lone) fail("'Focus' is always 'Focus page'", where, lone[0]);
    // 'everything must be professional' (4 Nov 2026): no casual spelling anywhere a person can read
    const casual = r.text.match(/.{0,20}\b(thes|dont|cant|wont|doesnt|isnt|im|ur|pls|plz|gonna|wanna|alot|Publics)\b.{0,20}/i);
    if (casual) fail('professional wording', where, casual[0]);
    // the app must never break: no errors while opening a screen
    if (errs.length) fail('no errors', where, errs[0]);
  }
  // 'in the videos I was able to scroll on the pages, on the other video I couldn't ... it changes multiple times' (5 Nov 2026):
  // a page you SWIPE to must be the same as one opened directly - no scrolling, nothing cut.
  await pg.evaluate(() => window.storyShow && window.storyShow('money', false));
  await pg.waitForTimeout(2500);
  for (let i = 0; i < 2; i++) {
    const moved = await pg.evaluate(() => { try { const n = PAGES.indexOf(state.current) + 1; if (n >= PAGES.length) return null; goToPageIndex(n); return PAGES[n]; } catch (e) { return 'err ' + e.message; } });
    if (!moved) break;
    await pg.waitForTimeout(1200);
    const r = await pg.evaluate(() => { const a = document.querySelector('#device-wrap #content-area'); if (!a) return null; const ab = a.getBoundingClientRect(), cs = getComputedStyle(a);
      let cut = 0; a.querySelectorAll('*').forEach(e => { const er = e.getBoundingClientRect(), st = getComputedStyle(e); if (er.height > 0 && st.visibility !== 'hidden' && st.position !== 'fixed' && er.bottom > ab.bottom + 3 && !e.closest('[class*="scroll"], .pay-list, .notes-ghosts')) cut++; });
      return { cur: state.current, canScroll: cs.overflowY !== 'hidden' && a.scrollHeight > a.clientHeight + 1, cut }; });
    const where = `swiped to ${moved} @ ${w}x${h}`;
    if (!r) { fail('screen opens', where, 'no content area'); continue; }
    if (!MAY_SCROLL.has(r.cur) && r.canScroll) fail('no scrolling except Settings / Information', where, 'the swiped-to page scrolls');
    if (!MAY_SCROLL.has(r.cur) && r.cut) fail('nothing cut', where, r.cut + ' parts run off the bottom');
  }
  await ctx.close();
}
// 'I just went inside the dashboard, the colors disappeared and they appeared again - you know this is wrong' (5 Nov 2026):
// with a colour system on, a screen must have your colours from its very first frame - checked straight after it opens.
{
  const ctx = await b.newContext({ viewport: { width: 393, height: 852 }, isMobile: true, hasTouch: true, userAgent: UA });
  await ctx.addInitScript(() => { try { localStorage.setItem('story-msg-account', JSON.stringify({ id: 'check', token: 't', pw: true, username: 'check' }));
    localStorage.setItem('story-color-systems', JSON.stringify({ all: { bg: '#1ab8d6', text: '#f5d90a' }, dash: { bg: '#1ab8d6', text: '#f5d90a' } })); } catch (e) {} });
  // Kept offline: on GitHub's machine the test account is refused by the real server and the log-in screen comes up,
  // under which Lockedintool deliberately shows no colours - so this check would test the log-in screen, not yours.
  await ctx.route('**/*', r => r.request().url().startsWith('http://localhost') ? r.continue() : r.abort());
  const pg = await ctx.newPage();
  const cerr = []; pg.on('pageerror', e => cerr.push(String(e && e.message || e).slice(0, 160)));
  await pg.goto(URL); await pg.waitForTimeout(2500);
  for (const s of ['settings', 'dashboard', 'money', 'dashboard', 'wallet', 'layouts']) {
    const got = await pg.evaluate(s => { window.storyShow && window.storyShow(s, false); const a = document.querySelector('#device-wrap #content-area');
      return a ? getComputedStyle(a).backgroundColor + (document.querySelector('.lit-auth') ? ' (log-in screen up)' : '') + ' sys=' + JSON.stringify((window.litColorSystemFor && litColorSystemFor(s)) || null) : 'none'; }, s);
    if (!got.startsWith('rgb(26, 184, 214)')) fail('your colours from the first frame', s + ' @ 393x852', 'opened with ' + got + (cerr.length ? ' | error: ' + cerr[0] : ''));
  }
  await ctx.close();
}
// 'Please now check that everything works fine - the page styles are smooth, no glitches' (5 Nov 2026): pulling the
// Vertical / Horizontal stack and turning the 3D Circle must never make the phone restyle the pages inside them (that was
// ~6000 elements per pull, seconds of work on a phone) and the 3D Circle must have no freeze on its first turn.
{
  const ctx = await b.newContext({ viewport: { width: 393, height: 852 }, isMobile: true, hasTouch: true, userAgent: UA });
  await ctx.addInitScript(() => { try { localStorage.setItem('story-msg-account', JSON.stringify({ id: 'check', token: 't', pw: true, username: 'check' })); } catch (e) {} });
  await ctx.route('**/*', r => r.request().url().startsWith('http://localhost') ? r.continue() : r.abort());
  const pg = await ctx.newPage();
  const perr = []; pg.on('pageerror', e => perr.push(String(e && e.message || e).slice(0, 160)));
  const cdp = await ctx.newCDPSession(pg);
  await pg.goto(URL); await pg.waitForTimeout(2500);
  await pg.evaluate(() => { window.storyShow && window.storyShow('money', false); });
  await pg.waitForTimeout(600);
  for (const style of ['vertical', 'horizontal', 'wheel']) {
    const one = async () => {
    await pg.evaluate(a => { state.pageArrangement = a; window.storyPagesAction('page-quick-style'); }, style);
    await pg.waitForTimeout(900);
    const box = await pg.evaluate(() => { const r = document.querySelector('#device-wrap #content-area').getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; });
    const H = style !== 'vertical';
    const sx = box.x - (H ? 110 : 0), sy = box.y - (H ? 0 : 140), ex = box.x + (H ? 110 : 0), ey = box.y + (H ? 0 : 140);
    const events = [];
    cdp.on('Tracing.dataCollected', d => events.push(...d.value));
    const traced = await within(cdp.send('Tracing.start', { categories: 'devtools.timeline' }).then(() => true).catch(() => false), 10000) === true;
    await pg.evaluate(() => { window.__fmax = 0; window.__frun = true; let last = performance.now(); (function t() { if (!window.__frun) return; const n = performance.now(); window.__fmax = Math.max(window.__fmax, n - last); last = n; requestAnimationFrame(t); })(); });
    await pg.mouse.move(sx, sy); await pg.mouse.down();
    for (let i = 1; i <= 24; i++) await pg.mouse.move(sx + (ex - sx) * i / 24, sy + (ey - sy) * i / 24);
    await pg.mouse.up(); await pg.waitForTimeout(250);
    const fmax = await pg.evaluate(() => { window.__frun = false; return window.__fmax; });
    if (traced) { const done = new Promise(r => cdp.once('Tracing.tracingComplete', r)); await within(cdp.send('Tracing.end').catch(() => {}), 10000); await within(done, 15000); }
    // Leave out the single biggest restyle: the finger touching down restyles the page once (pressed / hover state) -
    // normal. The bug restyled EVERY page preview on EVERY move (thousands each time).
    const counts = events.filter(e => e.name === 'UpdateLayoutTree').map(e => (e.args && e.args.elementCount) || 0).sort((x, y) => y - x);
    const restyled = counts.slice(1).reduce((n, c) => n + c, 0);
    if (traced && style !== 'wheel' && restyled > 2000) fail('page styles are smooth', style + ' @ 393x852', 'pulling the stack restyled ' + restyled + ' elements (should be a few hundred)');
    if (fmax > 250) fail('page styles are smooth', style + ' @ 393x852', 'a ' + Math.round(fmax) + ' ms freeze while it moved');
    if (perr.length) fail('no errors', style + ' @ 393x852', perr[0]);
    await pg.evaluate(() => { state.pageStyleViewOpen = false; state.pageStyleOn = false; window.storyShow && window.storyShow('money', false); });
    await pg.waitForTimeout(300);
      };
    if (await within(one().then(() => 'ok'), 90000) !== 'ok') fail('page styles are smooth', style + ' @ 393x852', 'the check did not finish within 90 s');
  }
  await ctx.close();
}
// EVERY WORD STAYS READABLE, WHATEVER COLOURS YOU PICK (#242 - owner, 11 Nov 2026: 'See thes some not visible words and
// buttons. So this needs to be a check for the whole entire app. So that no matter what type of background wallpaper or
// background colors or text colors. Nothing affects visibility'). With a colour system (teal + gold), every word that is
// in your text colour (labels, times, sub-lines - not the real green / red amounts) must stand out from what is behind it.
{
  const ctx = await b.newContext({ viewport: { width: 393, height: 852 }, isMobile: true, hasTouch: true, userAgent: UA });
  await ctx.addInitScript(() => { try { localStorage.setItem('story-msg-account', JSON.stringify({ id: 'check', token: 't', pw: true, username: 'check' }));
    localStorage.setItem('story-color-systems', JSON.stringify({ all: { bg: '#2b5f66', text: '#f2c200' }, dash: { bg: '#2b5f66', text: '#f2c200' } })); } catch (e) {} });
  const pg = await ctx.newPage();
  await pg.goto(URL); await pg.waitForTimeout(2500);
  await pg.evaluate(() => document.documentElement.classList.add('focus'));   // the app's look
  for (const s of ['dashboard', 'money', 'wallet', 'trading', 'settings', 'apps', 'information', 'layouts', 'notes', 'community']) {
    await pg.evaluate(s => { state.current = s; renderDevice(); }, s); await pg.waitForTimeout(500);
    const low = await pg.evaluate(() => {
      const P = v => { const m = v.match(/[\d.]+/g); return m ? m.map(Number) : [0, 0, 0]; };
      const L = (r, g, b) => { const f = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
      const out = [];
      document.querySelectorAll('#device-wrap #content-area *').forEach(el => {
        if (![...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())) return;
        const r = el.getBoundingClientRect(); if (r.width < 2 || r.height < 2) return;
        const cs = getComputedStyle(el); if (cs.visibility === 'hidden' || +cs.opacity < 0.3) return;
        const c = P(cs.color), ca = c[3] == null ? 1 : c[3];
        if (Math.abs(c[0] - 242) + Math.abs(c[1] - 194) + Math.abs(c[2] - 0) > 60) return;   // only words in your text colour
        let n = el, bg = null; while (n) { const x = P(getComputedStyle(n).backgroundColor); if (x.length >= 3 && (x[3] == null || x[3] > 0.5)) { bg = x; break; } n = n.parentElement; }
        if (!bg) return;
        const t = [0, 1, 2].map(i => c[i] * ca + bg[i] * (1 - ca));
        const l1 = L(...t), l2 = L(bg[0], bg[1], bg[2]); const k = (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
        if (k < 2.6) out.push(el.textContent.trim().slice(0, 30) + ' (' + k.toFixed(2) + ')');
      });
      return out;
    });
    if (low.length) fail('every word stays readable', s + ' with your colours', low.slice(0, 4).join(', '));
  }
  // On a background picture every word carries a halo (#243 - the owner's Trading screenshot: 'Search broker' unreadable on
  // the picture, because the app look's 'text-shadow:none !important' had silently removed the halo on phones).
  const tiny = 'data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==';
  for (const s of ['dashboard', 'money', 'wallet', 'trading', 'settings']) {
    const bare = await pg.evaluate(([s, img]) => { LIT_BG.all = img; LIT_BG.dashboard = img; state.current = s; renderDevice();
      const a = document.querySelector('#device-wrap #content-area'); if (!a || !a.classList.contains('lit-has-bg')) return ['no picture shown'];
      const out = []; a.querySelectorAll('*').forEach(el => { if (out.length > 3) return;
        if (![...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())) return;
        const r = el.getBoundingClientRect(); if (r.width < 2 || r.height < 2) return;
        if (getComputedStyle(el).textShadow === 'none') out.push(el.textContent.trim().slice(0, 30)); });
      return out; }, [s, tiny]);
    if (bare.length) fail('every word stays readable', s + ' on a background picture (no halo)', bare.join(', '));
  }
  await ctx.close();
}
await within(b.close(), 15000);
clearTimeout(WATCHDOG);
if (fails.length) { console.log('OWNER RULES BROKEN (' + fails.length + '):\n' + fails.join('\n')); process.exit(1); }
console.log('All owner rules pass');
