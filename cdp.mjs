// Phone test helper: runs JavaScript inside Lockedintool's own screens (WebViews) on the test phone, through the
// WebView's DevTools port (only the test-only build turns it on). Usage: node cdp.mjs <filter> "<js>"
//   filter = 'pages' | 's3' | 'profile' | 'dial' | 'all'   -> prints each window's result as JSON
const [, , filter = 'all', js = '1'] = process.argv;
const kind = u => /[?&]dial=1/.test(u) ? 'dial' : /[?&]profile=1/.test(u) ? (/gap=1/.test(u) ? 'gap' : 'profile') : /[?&]s3=1/.test(u) ? 's3' : /[?&]widget=/.test(u) ? 'widget' : 'pages';
const list = await (await fetch('http://127.0.0.1:9222/json')).json();
const out = [];
for (const t of list.filter(t => t.type === 'page' && /index\.html/.test(t.url) && (filter === 'all' || kind(t.url) === filter))) {
  const r = await new Promise(res => {
    const ws = new WebSocket(t.webSocketDebuggerUrl);
    const to = setTimeout(() => { try { ws.close(); } catch (e) {} res({ error: 'timeout' }); }, 8000);
    ws.onopen = () => ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression: js, returnByValue: true, awaitPromise: true } }));
    ws.onmessage = m => { const d = JSON.parse(m.data); if (d.id === 1) { clearTimeout(to); ws.close(); res(d.result && d.result.result ? d.result.result.value : d); } };
    ws.onerror = () => { clearTimeout(to); res({ error: 'ws' }); };
  });
  out.push({ win: kind(t.url), r });
}
console.log(JSON.stringify(out));
