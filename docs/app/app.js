/* Qonverse web app — same packs and lessons as the Android app, in any modern browser (including iPhone Safari).
   Data stays in this browser (localStorage). Speech uses the browser's built-in voices (speechSynthesis). */
(function () {
  'use strict';
  var VERSION = '0.3.0';
  var app = document.getElementById('app');
  var LS = {
    get: function (k, d) { try { var v = localStorage.getItem('qv.' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set: function (k, v) { try { localStorage.setItem('qv.' + k, JSON.stringify(v)); } catch (e) {} }
  };
  var state = { builtIn: [], packs: [], voices: [], picked: null, practice: null };

  // ---------------------------------------------------------------- helpers
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function cap(s) { s = s || ''; return s.charAt(0).toUpperCase() + s.slice(1); }
  var BADGE = { ja: '日本語', zh: '中文', ko: '한국어', th: 'ไทย', vi: 'Tiếng Việt', yue: '粵語', ms: 'Melayu', id: 'Indonesia', tl: 'Tagalog', hi: 'हिन्दी', ta: 'தமிழ்', km: 'ខ្មែរ', es: 'Español', fr: 'Français', de: 'Deutsch', en: 'English' };
  function base(tag) { return String(tag || '').split('-')[0].toLowerCase(); }
  function badge(tag) { return BADGE[base(tag)] || base(tag).toUpperCase(); }
  function langName(tag) { try { return new Intl.DisplayNames(['en'], { type: 'language' }).of(base(tag)) || tag; } catch (e) { return tag; } }
  function settings() { return Object.assign({ theme: 'system', normal: 1, slow: 0.6, voices: {}, last: null }, LS.get('settings', {})); }
  function saveSettings(s) { LS.set('settings', s); }
  function fam(id) { return LS.get('fam', {})[id] || []; }
  function setFam(id, pid, on) {
    var all = LS.get('fam', {}); var s = all[id] || [];
    s = s.filter(function (x) { return x !== pid; }); if (on) s.push(pid);
    all[id] = s; LS.set('fam', all); markChanged();
  }
  function markChanged() { var b = LS.get('bk', {}); if (!b.unsaved) { b.unsaved = true; b.first = Date.now(); LS.set('bk', b); } }
  function today() { var d = new Date(); return Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 864e5); }
  var INTERVALS = [0, 1, 2, 4, 7, 15, 30, 60];
  function srs(id) { return LS.get('srs', {})[id] || {}; }
  function saveSrs(id, m) { var all = LS.get('srs', {}); all[id] = m; LS.set('srs', all); }
  function recordAnswer(id, pid, ok) {
    var m = srs(id), box = (m[pid] || [0])[0], nb = ok ? Math.min(box + 1, INTERVALS.length - 1) : 0;
    m[pid] = [nb, today() + INTERVALS[nb]]; saveSrs(id, m); setFam(id, pid, ok);
  }
  function markSeen(id, pid, ok) {
    var m = srs(id), cur = m[pid];
    m[pid] = ok ? [Math.max(cur ? cur[0] : 0, 1), Math.max(cur ? cur[1] : 0, today() + 1)] : [0, today()];
    saveSrs(id, m); setFam(id, pid, ok);
  }
  function stars() { return LS.get('star', []); }
  function isStar(id, pid) { return stars().indexOf(id + '|' + pid) >= 0; }
  function setStar(id, pid, on) { var s = stars().filter(function (x) { return x !== id + '|' + pid; }); if (on) s.push(id + '|' + pid); LS.set('star', s); markChanged(); }
  function counts() {
    var t0 = today(), due = 0, fresh = 0;
    state.packs.forEach(function (p) { var m = srs(p.pack.id); phrasesOf(p).forEach(function (ph) { var e = m[ph.id]; if (!e) fresh++; else if (e[1] <= t0) due++; }); });
    return { due: due, fresh: fresh, today: due + Math.min(fresh, 5) };
  }
  function allRefs() {
    var out = [];
    state.packs.forEach(function (p) { p.scenarios.forEach(function (s) { s.phrases.forEach(function (ph, i) { out.push({ p: p, sid: s.id, i: i, ph: ph }); }); }); });
    return out;
  }
  function phrasesOf(p) { return p.scenarios.reduce(function (a, s) { return a.concat(s.phrases); }, []); }
  function findPack(id) { return state.packs.filter(function (p) { return p.pack.id === id; })[0]; }
  function toast(msg) {
    var t = document.createElement('div'); t.className = 'toast'; t.textContent = msg; document.body.appendChild(t);
    setTimeout(function () { t.remove(); }, 3200);
  }
  window.QV = { toast: toast };

  var ICON = {
    play: '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>',
    playBig: '<svg width="34" height="34" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>',
    back: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 12H5M11 6l-6 6 6 6"/></svg>',
    close: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>',
    gear: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/></svg>',
    share: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7M16 6l-4-4-4 4M12 2v13"/></svg>'
  };
  function logo(size) {
    size = size || 24; var q = Math.round(size * 1.08);
    return '<div class="logo" style="font-size:' + size + 'px" role="img" aria-label="Qonverse"><svg width="' + q + '" height="' + q + '" viewBox="0 0 40 40" fill="none" aria-hidden="true"><circle cx="19" cy="19" r="13.5" stroke="var(--box-text)" stroke-width="5.5"/><path d="M24 31.2 Q28.6 37.2 38 37.6 Q32.6 33.2 31.4 24" fill="var(--box-text)" stroke="var(--box-text)" stroke-width="2" stroke-linejoin="round"/></svg><span aria-hidden="true">onverse</span></div>';
  }
  var TAIL = '<svg class="tail" viewBox="0 0 22 14" aria-hidden="true"><path class="f" d="M7.6 0 L20.4 0 L20 .75 L2 12.5 L8 .75 Z"/><path class="s" d="M8 .75 L2 12.5 L20 .75"/></svg>';
  function tri(t, r, m) { return '<div class="tri"><div class="t">' + t + '</div>' + (r ? '<div class="r">' + esc(r) + '</div>' : '') + (m ? '<div class="m">' + esc(m) + '</div>' : '') + '</div>'; }
  function highlight(text, key) {
    var i = key ? text.indexOf(key) : -1;
    if (i < 0) return esc(text);
    return esc(text.slice(0, i)) + '<mark class="key">' + esc(key) + '</mark>' + esc(text.slice(i + key.length));
  }
  function speakBtn(text, lang, label) { return '<button class="speak" data-say="' + esc(text) + '" data-lang="' + esc(lang) + '" aria-label="' + esc(label || 'Play') + '">' + ICON.play + '</button>'; }
  var SPEEDS = [0.6, 0.8, 1.0, 1.2];
  function speedChip() {
    var v = settings().normal;
    return '<button class="speed" data-speed aria-label="Playback speed ' + v.toFixed(1) + '×. Tap to change."><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M4.5 17a9 9 0 1 1 15 0"/><path d="M12 13l4-4"/></svg>' + v.toFixed(1) + '×</button>';
  }
  function topbar(title, backTo, right) {
    return '<div class="topbar">' + (backTo != null ? '<button class="icon-btn" data-go="' + esc(backTo) + '" aria-label="Back">' + ICON.back + '</button>' : '') +
      '<div class="title">' + esc(title) + '</div>' + (right || '') + '</div>';
  }

  // ---------------------------------------------------------------- theme & wallpaper
  function applyTheme() {
    var t = settings().theme;
    document.documentElement.setAttribute('data-theme', t);
    var m = document.querySelector('meta[name="theme-color"]');
    var dark = t === 'dark' || t === 'amoled' || (t === 'system' && window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches);
    if (m) m.setAttribute('content', t === 'amoled' ? '#000000' : dark ? '#101315' : '#FBFBFD');
  }
  (function cubes() {
    var s = 17, c = Math.cos(Math.PI / 6), w = 2 * s * c, d = '';
    function P(x, y) { return x.toFixed(2) + ' ' + y.toFixed(2); }
    for (var j = -1; j < 4; j++) for (var i = -1; i < 3; i++) {
      var x = i * w + (j % 2 ? w / 2 : 0), y = j * 1.5 * s;
      d += 'M' + P(x - s * c, y - s / 2) + 'L' + P(x, y - s) + 'L' + P(x + s * c, y - s / 2) + 'L' + P(x + s * c, y + s / 2) + 'L' + P(x, y + s) + 'L' + P(x - s * c, y + s / 2) + 'Z' +
        'M' + P(x - s * c, y - s / 2) + 'L' + P(x, y) + 'L' + P(x + s * c, y - s / 2) + 'M' + P(x, y) + 'L' + P(x, y + s);
    }
    var pat = document.getElementById('cube'); if (pat) { pat.setAttribute('width', w.toFixed(3)); pat.setAttribute('height', String(3 * s)); }
    var path = document.getElementById('cubepath'); if (path) path.setAttribute('d', d);
  })();

  // ---------------------------------------------------------------- speech
  var synth = window.speechSynthesis;
  function loadVoices() { if (synth) state.voices = synth.getVoices() || []; }
  if (synth) { loadVoices(); if ('onvoiceschanged' in synth) synth.onvoiceschanged = function () { loadVoices(); if (location.hash.indexOf('settings') >= 0) render(); }; }
  function voicesFor(lang) {
    var b = base(lang), region = (lang.split('-')[1] || '').toUpperCase();
    var v = state.voices.filter(function (x) { return base(x.lang.replace('_', '-')) === b; });
    v.sort(function (a, z) {
      var ar = (a.lang.replace('_', '-').split('-')[1] || '').toUpperCase() === region ? 0 : 1;
      var zr = (z.lang.replace('_', '-').split('-')[1] || '').toUpperCase() === region ? 0 : 1;
      return ar - zr || (a.localService === z.localService ? 0 : a.localService ? -1 : 1);
    });
    return v.slice(0, 4);
  }
  function say(text, lang, opts) {
    opts = opts || {};
    if (!synth) { toast('This browser can’t speak. Try Safari or Chrome.'); return; }
    var st = settings(); synth.cancel();
    var list = Array.isArray(text) ? text : [];
    if (!list.length) for (var i = 0; i < (opts.times || 1); i++) list.push(text);
    var chosen = st.voices[lang], vs = voicesFor(lang);
    var voice = vs.filter(function (v) { return v.name === chosen; })[0] || vs[0];
    if (!voice && state.voices.length) toast('No ' + langName(lang) + ' voice in this browser yet. See Settings for how to add one.');
    list.forEach(function (t) {
      var u = new SpeechSynthesisUtterance(t); u.lang = lang;
      if (voice) u.voice = voice;
      u.rate = opts.rate || (opts.slow ? st.slow : st.normal);
      synth.speak(u);
    });
  }

  // ---------------------------------------------------------------- packs
  function userPacks() { return LS.get('packs', {}); }
  function rebuild() {
    var mine = userPacks(), hidden = LS.get('hidden', []), byId = {};
    state.builtIn.forEach(function (p) { if (hidden.indexOf(p.pack.id) < 0) byId[p.pack.id] = p; });
    Object.keys(mine).forEach(function (k) { byId[k] = mine[k]; });
    state.packs = Object.keys(byId).map(function (k) { return byId[k]; }).sort(function (a, b) { return a.pack.title.localeCompare(b.pack.title); });
  }
  function parsePack(raw) {
    if (raw.indexOf('qonverse_backup_version') >= 0) throw new Error('This is a Qonverse backup, not a pack. Restore it from Settings › Backup & restore.');
    var a = raw.indexOf('{'), b = raw.lastIndexOf('}');
    if (a < 0 || b <= a) throw new Error('No pack found. Paste the whole code block your AI gave you.');
    var p = JSON.parse(raw.slice(a, b + 1)), errs = [];
    if (!p.pack || !p.pack.id) errs.push('The pack has no id.');
    if (!p.pack || !p.pack.target_language) errs.push('The pack doesn’t say which language it teaches.');
    if (!Array.isArray(p.scenarios) || !p.scenarios.length) errs.push('The pack has no scenarios.');
    (p.scenarios || []).forEach(function (s) {
      if (!s.phrases || !s.phrases.length) errs.push('Scenario “' + s.title + '” has no phrases.');
      (s.phrases || []).forEach(function (ph) { if (!ph.text) errs.push('A phrase in “' + s.title + '” is empty.'); });
    });
    if (errs.length) throw new Error(errs.join('\n'));
    return p;
  }
  function savePack(p) {
    var mine = userPacks(), old = mine[p.pack.id] || state.builtIn.filter(function (x) { return x.pack.id === p.pack.id; })[0];
    if (old) {
      var map = {}, order = [];
      old.scenarios.concat(p.scenarios).forEach(function (s) { if (!map[s.id]) order.push(s.id); map[s.id] = s; });
      p = Object.assign({}, p, { scenarios: order.map(function (k) { return map[k]; }) });
    }
    mine[p.pack.id] = p; LS.set('packs', mine);
    var hidden = LS.get('hidden', []).filter(function (x) { return x !== p.pack.id; }); LS.set('hidden', hidden);
    rebuild(); markChanged(); return p;
  }
  function deletePack(id) {
    var mine = userPacks(); delete mine[id]; LS.set('packs', mine);
    var hidden = LS.get('hidden', []); if (hidden.indexOf(id) < 0) hidden.push(id); LS.set('hidden', hidden);
    var all = LS.get('fam', {}); delete all[id]; LS.set('fam', all); rebuild(); markChanged();
  }

  // ---------------------------------------------------------------- backup & restore (same file format as the Android app)
  function exportBackup() {
    var st = settings();
    return JSON.stringify({ qonverse_backup_version: 1, created: new Date().toISOString(), app: 'web',
      packs: Object.keys(userPacks()).map(function (k) { return userPacks()[k]; }).concat(state.builtIn.filter(function (b) { return !userPacks()[b.pack.id] && LS.get('hidden', []).indexOf(b.pack.id) < 0; })),
      hidden: LS.get('hidden', []), familiar: LS.get('fam', {}), starred: stars(), srs: LS.get('srs', {}),
      settings: { theme: st.theme, normal_rate: st.normal, slow_rate: st.slow, voices: st.voices } }, null, 2);
  }
  function restoreBackup(raw) {
    var a = raw.indexOf('{'), z = raw.lastIndexOf('}');
    if (a < 0 || raw.indexOf('qonverse_backup_version') < 0) throw new Error('This isn’t a Qonverse backup file.');
    var b = JSON.parse(raw.slice(a, z + 1)), n = 0;
    (b.packs || []).forEach(function (p) { savePack(p); });
    (b.hidden || []).forEach(function (h) { if (!(b.packs || []).some(function (p) { return p.pack.id === h; }) && findPack(h)) deletePack(h); });
    var fam = LS.get('fam', {});
    Object.keys(b.familiar || {}).forEach(function (k) { var s = fam[k] || []; b.familiar[k].forEach(function (x) { if (s.indexOf(x) < 0) s.push(x); n++; }); fam[k] = s; });
    LS.set('fam', fam);
    var sr = LS.get('srs', {});
    Object.keys(b.srs || {}).forEach(function (k) { var m = sr[k] || {}; Object.keys(b.srs[k]).forEach(function (pid) { var v = b.srs[k][pid]; if (!m[pid] || v[0] >= m[pid][0]) m[pid] = [Number(v[0]), Number(v[1])]; }); sr[k] = m; });
    LS.set('srs', sr);
    var s = stars(); (b.starred || []).forEach(function (x) { if (s.indexOf(x) < 0) s.push(x); }); LS.set('star', s);
    var st = settings(), bs = b.settings || {};
    if (bs.theme) st.theme = bs.theme; if (bs.normal_rate) st.normal = bs.normal_rate; if (bs.slow_rate) st.slow = bs.slow_rate;
    Object.keys(bs.voices || {}).forEach(function (k) { st.voices[k] = bs.voices[k]; });
    saveSettings(st); applyTheme();
    var bk = LS.get('bk', {}); bk.unsaved = false; LS.set('bk', bk);
    return { packs: (b.packs || []).length, phrases: n };
  }
  function doBackup(done) {
    var text = exportBackup(), name = 'qonverse-backup-' + new Date().toISOString().slice(0, 10) + '.json';
    var finish = function () { var bk = LS.get('bk', {}); bk.unsaved = false; bk.last = Date.now(); LS.set('bk', bk); done && done('Backup saved. Keep it somewhere safe, like iCloud Drive or Google Drive.'); };
    try {
      var file = new File([text], name, { type: 'application/json' });
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        navigator.share({ files: [file], title: 'Qonverse backup' }).then(finish, function (e) { if (e && e.name !== 'AbortError') download(); });
        return;
      }
    } catch (e) {}
    download();
    function download() {
      var a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([text], { type: 'application/json' })); a.download = name;
      document.body.appendChild(a); a.click(); setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 1000); finish();
    }
  }
  function shouldRemind() { var b = LS.get('bk', {}); return b.unsaved && Date.now() - (b.first || Date.now()) > 7 * 864e5 && Date.now() > (b.snooze || 0); }

  // ---------------------------------------------------------------- screens
  function home() {
    var cn = counts(), due = cn.today;
    var st = settings(), last = st.last, cont = '';
    if (last) {
      var lp = findPack(last[0]), ls = lp && lp.scenarios.filter(function (s) { return s.id === last[1]; })[0];
      if (ls) cont = '<button class="card pack-row" data-go="phrase/' + esc(lp.pack.id) + '/' + esc(ls.id) + '/' + last[2] + '"><span class="badge" style="background:var(--audio);color:var(--on-audio)">' + ICON.play + '</span><span class="grow"><span class="muted small" style="display:block">Continue learning</span><b>' + esc(ls.title) + ' · Phrase ' + (last[2] + 1) + ' of ' + ls.phrases.length + '</b></span><span class="muted">›</span></button>';
    }
    var packs = state.packs.map(function (p) {
      var total = phrasesOf(p).length, f = fam(p.pack.id).length;
      return '<button class="card pack-row" data-go="pack/' + esc(p.pack.id) + '"><span class="badge">' + esc(badge(p.pack.target_language)) + '</span><span class="grow stack" style="gap:6px"><b style="font-size:15px">' + esc(p.pack.title) + '</b><span class="bar"><i style="width:' + (total ? Math.round(100 * f / total) : 0) + '%"></i></span><span class="muted tiny">' + f + ' of ' + total + ' familiar · ' + p.scenarios.length + ' scenarios</span></span></button>';
    }).join('');
    return '<div class="screen"><div class="pad stack" style="padding-top:16px;padding-bottom:120px">' +
      '<div class="row"><div class="grow">' + logo(24) + '</div><button class="icon-btn ring" data-go="settings" aria-label="Settings">' + ICON.gear + '</button></div>' +
      '<div><h1>Ready for today’s phrases?</h1><div class="muted">Short phrases for your job, with sound.</div></div>' +
      '<div class="review"><div class="row" style="align-items:flex-start"><div class="grow"><div class="kicker">Today’s review</div><div class="big">' + (due > 0 ? due + ' phrases' : 'All done today') + '</div><div style="opacity:.9;font-size:14px">' + (due > 0 ? cn.due + ' due · ' + Math.min(cn.fresh, 5) + ' new · as a chat with a guest' : 'Practise anyway, or come back tomorrow') + '</div></div><div class="hanzi" lang="ja">話</div></div>' +
      '<button class="btn block" data-go="practice/all"' + (state.packs.length ? '' : ' disabled') + '>Start review</button></div>' + cont +
      (shouldRemind() ? '<div class="notice stack" style="gap:8px"><b style="color:var(--box-deep)">Back up your packs and progress</b><span>Save one small file so you can restore everything on a new phone.</span><div class="row"><button class="btn ghost small" id="bkNow">Back up now</button><button class="btn ghost small" id="bkLater">Not now</button></div></div>' : '') +
      '<div class="row" style="gap:10px"><button class="card pack-row" data-go="starred" style="flex:1;flex-direction:column;align-items:flex-start;gap:0"><b>★ Starred</b><span class="muted tiny">' + (stars().length ? stars().length + ' phrases' : 'Keep key phrases here') + '</span></button><button class="card pack-row" data-go="search" style="flex:1;flex-direction:column;align-items:flex-start;gap:0"><b>⌕ Search</b><span class="muted tiny">All packs</span></button></div>' +
      '<div class="row"><h2 class="grow">My packs</h2><button class="link" data-go="add">+ Add pack</button></div>' +
      (packs || '<p class="muted">No packs yet. Tap “Add pack” to make one with your AI.</p>') +
      '<p class="muted tiny" style="text-align:center">Qonverse web app ' + VERSION + ' (preview) · <a href="../" style="color:inherit">About Qonverse</a></p>' +
      '</div></div>';
  }

  function addScreen() {
    return '<div class="screen">' + topbar('Add a pack', '') + '<div class="pad stack" style="padding-bottom:110px">' +
      '<h1>Make a pack with your AI</h1><p class="muted" style="margin:0">Works with Claude, ChatGPT, Gemini or any AI chat. No account or AI key needed in Qonverse.</p>' +
      '<div class="step"><div class="n">1</div><div class="grow stack" style="gap:8px;padding-top:5px"><b>Copy the Qonverse prompt</b><div><button class="btn ghost small" id="copyPrompt" style="border-color:var(--box);color:var(--box-text)">Copy prompt</button></div></div></div>' +
      '<div class="step"><div class="n">2</div><div class="grow stack" style="gap:6px;padding-top:5px"><b>Paste it into your AI chat</b><span class="muted small">It asks about your job, language and situations, then gives you a block of code.</span></div></div>' +
      '<div class="step"><div class="n">3</div><div class="grow stack" style="gap:10px;padding-top:5px"><b>Paste the pack here</b><label class="sr" for="raw">Pack from your AI</label><textarea id="raw" placeholder="{ &quot;qonverse_pack_version&quot;: … }"></textarea>' +
      '<div class="row" style="flex-wrap:wrap;gap:10px"><button class="btn ghost small" id="pasteBtn">Paste from clipboard</button><label class="btn ghost small" for="file">Import file</label><input id="file" type="file" accept=".json,application/json,text/plain" class="sr"></div></div></div>' +
      '<div id="result"></div></div>' +
      '<div class="footerbar"><button class="btn primary block" id="saveBtn" disabled>Save pack</button></div></div>';
  }
  function bindAdd() {
    var raw = document.getElementById('raw'), res = document.getElementById('result'), save = document.getElementById('saveBtn'), parsed = null;
    function check() {
      parsed = null; save.disabled = true;
      if (!raw.value.trim()) { res.innerHTML = ''; return; }
      try {
        parsed = parsePack(raw.value);
        var n = phrasesOf(parsed).length, exists = !!findPack(parsed.pack.id);
        res.innerHTML = '<div class="ok"><b>✓ Pack looks good</b><div style="margin-top:4px">' + esc(parsed.pack.title) + '<br>' + parsed.scenarios.length + ' scenarios · ' + n + ' phrases · ' + esc(langName(parsed.pack.target_language)) + '</div><div class="muted small" style="margin-top:6px">' + (exists ? 'You already have this pack — these scenarios will be added to it.' : 'Got it in parts? Save this, then paste the next part and we’ll join them.') + '</div></div>';
        save.disabled = false;
      } catch (e) { res.innerHTML = '<div class="err"><b>This doesn’t look like a complete pack</b><br>' + esc(e.message) + '</div>'; }
    }
    raw.addEventListener('input', check);
    document.getElementById('pasteBtn').onclick = function () {
      if (navigator.clipboard && navigator.clipboard.readText) navigator.clipboard.readText().then(function (t) { raw.value = t; check(); }, function () { raw.focus(); toast('Long-press in the box and choose Paste.'); });
      else { raw.focus(); toast('Long-press in the box and choose Paste.'); }
    };
    document.getElementById('file').onchange = function (e) {
      var f = e.target.files[0]; if (!f) return;
      var r = new FileReader(); r.onload = function () { raw.value = r.result; check(); }; r.readAsText(f);
    };
    document.getElementById('copyPrompt').onclick = function () {
      var b = this;
      fetch('../prompt.md').then(function (r) { return r.text(); }).then(function (t) {
        var body = t.split('\n---\n').slice(1).join('\n---\n') || t;
        return navigator.clipboard.writeText(body);
      }).then(function () { b.textContent = 'Copied — now open your AI'; b.style.background = 'var(--box)'; b.style.color = 'var(--on-box)'; },
        function () { toast('Couldn’t copy. Open the prompt on the Qonverse website instead.'); });
    };
    save.onclick = function () { if (parsed) { var p = savePack(parsed); go('pack/' + p.pack.id); } };
  }

  function packScreen(id) {
    var p = findPack(id); if (!p) return '<div class="screen">' + topbar('Pack not found', '') + '</div>';
    var f = fam(id), total = phrasesOf(p).length, lang = p.pack.target_language;
    var noVoice = state.voices.length && !voicesFor(lang).length;
    var scen = p.scenarios.map(function (s, i) {
      var done = s.phrases.filter(function (x) { return f.indexOf(x.id) >= 0; }).length, complete = done && done === s.phrases.length;
      var sub = [s.title_target, s.title_romanization].filter(Boolean).join(' · ');
      return '<button class="scen" data-go="phrase/' + esc(id) + '/' + esc(s.id) + '/0"><span class="num' + (complete ? ' done' : '') + '">' + (complete ? '✓' : i + 1) + '</span><span class="grow"><b style="display:block;font-size:15px">' + esc(s.title) + '</b>' + (sub ? '<span class="muted small">' + esc(sub) + '</span>' : '') + '</span><span class="muted small">' + done + '/' + s.phrases.length + ' ›</span></button>';
    }).join('');
    return '<div class="screen">' + topbar('', '', '<button class="link danger" id="del">Delete</button>') +
      '<div class="pad stack" style="gap:12px"><div class="row"><span class="badge" style="width:60px;height:60px;border-radius:16px;background:var(--box);color:var(--on-box);font-size:15px">' + esc(badge(lang)) + '</span><div class="grow"><h1 style="font-size:22px">' + esc(p.pack.title) + '</h1><div class="muted small">' + [langName(lang), cap(p.pack.politeness), total + ' phrases'].filter(Boolean).map(esc).join(' · ') + '</div></div></div>' +
      '<div class="bar" style="height:8px"><i style="width:' + (total ? Math.round(100 * f.length / total) : 0) + '%"></i></div><div class="muted small">' + f.length + ' of ' + total + ' familiar</div>' +
      (noVoice ? '<div class="notice"><b>No ' + esc(langName(lang)) + ' voice in this browser.</b><br>On iPhone: Settings › Accessibility › Spoken Content › Voices › ' + esc(langName(lang)) + '.</div>' : '') +
      '<div class="row" style="gap:10px"><button class="btn primary" style="flex:1" data-go="practice/' + esc(id) + '">Practise</button><button class="btn ghost" style="flex:1" data-go="shadow/' + esc(id) + '">🎧 Shadow mode</button></div></div>' +
      '<div class="sheet"><h2 style="padding:4px 4px 8px">Scenarios</h2>' + scen + '</div></div>';
  }

  function phraseScreen(id, sid, idx) {
    var p = findPack(id); if (!p) return packScreen(id);
    var s = p.scenarios.filter(function (x) { return x.id === sid; })[0] || p.scenarios[0];
    idx = Math.max(0, Math.min(idx, s.phrases.length - 1));
    var st = settings(); st.last = [id, s.id, idx]; saveSettings(st);
    var ph = s.phrases[idx], lang = p.pack.target_language, f = fam(id);
    var dots = s.phrases.map(function (x, i) { return '<i class="' + (i === idx ? 'now' : f.indexOf(x.id) >= 0 ? 'on' : '') + '"></i>'; }).join('');
    var sent = ph.in_a_sentence ? '<div class="hr"></div><div class="label">In a sentence</div><div class="row" style="align-items:flex-start"><div class="grow">' + tri(highlight(ph.in_a_sentence.text, ph.text), ph.in_a_sentence.romanization, ph.in_a_sentence.meaning) + '</div>' + speakBtn(ph.in_a_sentence.text, lang, 'Play the sentence') + '</div>' : '';
    var when = (ph.when_to_use || ph.notes) ? '<div class="when"><div class="label">When to use it</div>' + (ph.when_to_use ? '<div>' + esc(ph.when_to_use) + '</div>' : '') + (ph.notes ? '<div class="sub small">' + esc(ph.notes) + '</div>' : '') + (ph.pattern ? '<div class="sub small">Pattern: ' + esc(ph.pattern) + '</div>' : '') + '</div>' : '';
    var dlg = (ph.example && ph.example.dialogue || []);
    var conv = dlg.length ? '<div class="label">In a conversation</div>' + (ph.example.setting ? '<div class="pill">' + esc(ph.example.setting) + '</div>' : '') + dlg.map(function (l) {
      var mine = String(l.speaker).toLowerCase() === 'you';
      return '<div class="bubble-wrap ' + (mine ? 'mine' : 'theirs') + '"><span class="muted tiny" style="font-weight:600">' + (mine ? 'You' : esc(cap(l.speaker))) + '</span><div class="bubble ' + (mine ? 'mine' : 'theirs') + '"><div class="grow">' + tri(l.uses_phrase ? highlight(l.text, ph.text) : esc(l.text), l.romanization, l.meaning) + '</div>' + speakBtn(l.text, lang, 'Play this line') + TAIL + '</div></div>';
    }).join('') + '<button class="btn ghost block" id="playAll" style="color:var(--audio-text)">▶ Play whole conversation</button>' : '';
    var replies = (ph.likely_responses || []).length ? '<div class="label">They might also say</div><div class="card" style="padding:4px 10px 4px 14px">' + ph.likely_responses.map(function (r) { return '<div class="row" style="padding:8px 0"><div class="grow">' + tri(esc(r.text), r.romanization, r.meaning) + '</div>' + speakBtn(r.text, lang, 'Play this reply') + '</div>'; }).join('') + '</div>' : '';
    return '<div class="screen">' + topbar(s.title, 'pack/' + id, speedChip() + '<span class="muted small" style="padding:0 10px 0 6px">' + (idx + 1) + '/' + s.phrases.length + '</span>') +
      '<div class="dots">' + dots + '</div><div class="pad stack" id="swipe" style="padding-top:14px;padding-bottom:110px;gap:14px">' +
      '<div class="card phrase-card"><div class="row" style="gap:4px"><div class="grow">' + (ph.politeness ? '<span class="chip">' + esc(cap(ph.politeness)) + ' · Phrase</span>' : '') + '</div><button class="link" id="showGuest">Show guest</button><button class="icon-btn" id="star" aria-label="' + (isStar(id, ph.id) ? 'Remove star' : 'Star this phrase') + '" style="font-size:24px;color:' + (isStar(id, ph.id) ? 'var(--orange)' : 'var(--muted)') + '">' + (isStar(id, ph.id) ? '★' : '☆') + '</button></div>' +
      '<div class="phrase-text" lang="' + esc(base(lang)) + '">' + esc(ph.text) + '</div>' + (ph.romanization ? '<div class="muted" style="font-size:17px">' + esc(ph.romanization) + '</div>' : '') +
      '<div style="font-size:19px;font-weight:600">' + esc(ph.meaning) + '</div>' + sent + '</div>' +
      '<div class="row" style="justify-content:space-between;padding:0 8px"><button class="ctrl" data-say="' + esc(ph.text) + '" data-lang="' + esc(lang) + '" data-slow="1" aria-label="Play slowly"><b>0.6×</b><span>Slow</span></button>' +
      '<button class="speak big" data-say="' + esc(ph.text) + '" data-lang="' + esc(lang) + '" aria-label="Play phrase">' + ICON.playBig + '</button>' +
      '<button class="ctrl" data-say="' + esc(ph.text) + '" data-lang="' + esc(lang) + '" data-times="3" aria-label="Play three times"><b>×3</b><span>Loop</span></button></div>' +
      (SR ? '<button class="card row" id="pron" style="padding:12px;text-align:left;width:100%"><span class="speak" style="background:var(--box);color:var(--on-box)"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/></svg></span><span class="grow" id="pronText"><b style="display:block">Check my pronunciation</b><span class="muted small">Say the phrase — your device tells you if it understood.</span></span></button>' : '') +
      when + conv + replies + '</div>' +
      '<div class="footerbar"><button class="btn ghost" style="flex:1" id="again">Again later</button><button class="btn primary" style="flex:1" id="familiar">' + (f.indexOf(ph.id) >= 0 ? '✓ Familiar' : 'Familiar') + '</button></div></div>';
  }
  function bindPhrase(id, sid, idx) {
    var p = findPack(id); if (!p) return;
    var s = p.scenarios.filter(function (x) { return x.id === sid; })[0] || p.scenarios[0], ph = s.phrases[Math.min(idx, s.phrases.length - 1)];
    function next(d) { var n = idx + d; if (n >= 0 && n < s.phrases.length) go('phrase/' + id + '/' + s.id + '/' + n, true); else if (d > 0) go('pack/' + id); }
    document.getElementById('again').onclick = function () { markSeen(id, ph.id, false); next(1); };
    document.getElementById('familiar').onclick = function () { markSeen(id, ph.id, true); next(1); };
    document.getElementById('star').onclick = function () { setStar(id, ph.id, !isStar(id, ph.id)); render(); };
    document.getElementById('showGuest').onclick = function () { showGuest(ph, p.pack.target_language); };
    var pr = document.getElementById('pron'); if (pr) pr.onclick = function () { checkPron(ph, p.pack.target_language); };
    var pa = document.getElementById('playAll');
    if (pa) pa.onclick = function () { say(ph.example.dialogue.map(function (l) { return l.text; }), p.pack.target_language); };
    var el = document.getElementById('swipe'), x0 = null, y0 = 0;
    el.addEventListener('touchstart', function (e) { x0 = e.touches[0].clientX; y0 = e.touches[0].clientY; }, { passive: true });
    el.addEventListener('touchend', function (e) {
      if (x0 == null) return; var dx = e.changedTouches[0].clientX - x0, dy = e.changedTouches[0].clientY - y0; x0 = null;
      if (Math.abs(dx) > 70 && Math.abs(dx) > 2 * Math.abs(dy)) next(dx < 0 ? 1 : -1);
    });
  }

  function shuffle(a) { a = a.slice(); for (var i = a.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)); var t = a[i]; a[i] = a[j]; a[j] = t; } return a; }
  function buildPractice(id) {
    var packs = id === 'all' ? state.packs : [findPack(id)].filter(Boolean), all = [], t0 = today();
    packs.forEach(function (p) { var m = srs(p.pack.id); phrasesOf(p).forEach(function (ph) { all.push({ p: p, ph: ph, e: m[ph.id] }); }); });
    var due = all.filter(function (x) { return x.e && x.e[1] <= t0; }).sort(function (a, b) { return a.e[1] - b.e[1]; });
    var fresh = shuffle(all.filter(function (x) { return !x.e; })).slice(0, 5);
    var pool = due.concat(fresh).slice(0, 10);
    if (!pool.length) pool = shuffle(all).slice(0, 8);
    return { id: id, i: 0, picked: null, qs: pool.map(function (x) {
      var others = shuffle(phrasesOf(x.p).filter(function (o) { return o.id !== x.ph.id; })).slice(0, 2);
      return { p: x.p, ph: x.ph, opts: shuffle(others.concat([x.ph])) };
    }) };
  }
  function practiceScreen(id) {
    if (!state.practice || state.practice.id !== id) state.practice = buildPractice(id);
    var P = state.practice;
    if (!P.qs.length) return '<div class="screen">' + topbar('Review', '') + '<p class="pad muted">Add a pack first.</p></div>';
    if (P.i >= P.qs.length) {
      return '<div class="screen" style="justify-content:center;align-items:center;text-align:center;padding:24px;gap:16px">' + logo(32) + '<h1>Review done!</h1><p class="muted" style="margin:0">Say each phrase out loud a few more times today.</p><button class="btn primary" id="restart">Practise again</button><button class="link" data-go="">Back to home</button></div>';
    }
    var q = P.qs[P.i], lang = q.p.pack.target_language, d = (q.ph.example && q.ph.example.dialogue) || [], u = -1;
    d.forEach(function (l, i) { if (u < 0 && l.uses_phrase) u = i; });
    var cue = u > 0 && String(d[u - 1].speaker).toLowerCase() !== 'you' ? d[u - 1] : d.filter(function (l) { return String(l.speaker).toLowerCase() !== 'you'; })[0];
    var who = cue ? cap(cue.speaker) : 'Guest', picked = P.picked, right = picked && picked === q.ph.id;
    var opts = q.opts.map(function (o) {
      var isR = picked && o.id === q.ph.id, isW = picked && o.id === picked && !isR;
      if (picked && !isR && !isW) return '';
      return '<button class="bubble ' + (isR ? 'right' : isW ? 'wrong' : 'choice') + '" data-pick="' + esc(o.id) + '"' + (picked ? ' disabled' : '') + ' style="max-width:88%;align-self:flex-end;font:inherit;color:inherit">' + tri(esc(o.text), o.romanization, o.meaning) + TAIL + '</button>';
    }).join('');
    return '<div class="screen chat"><div style="background:var(--surface);padding-bottom:10px">' + topbar(who, '', speedChip() + '<span class="muted small" style="padding:0 10px 0 6px">' + (P.i + 1) + '/' + P.qs.length + '</span>') +
      '<div class="pad"><div class="bar" style="height:4px"><i style="width:' + Math.round(100 * (P.i + 1) / P.qs.length) + '%"></i></div></div></div>' +
      '<div class="pad stack" style="padding-top:16px;padding-bottom:110px;gap:12px">' + (q.ph.example && q.ph.example.setting ? '<div class="pill">' + esc(q.ph.example.setting) + '</div>' : '') +
      '<div class="bubble-wrap theirs"><div class="bubble theirs"><div class="grow">' + (cue ? tri(esc(cue.text), cue.romanization, cue.meaning) : esc(q.ph.when_to_use)) + '</div>' + (cue ? speakBtn(cue.text, lang, 'Hear them') : '') + TAIL + '</div></div>' +
      (picked ? '' : '<div class="muted tiny" style="align-self:flex-end;font-weight:600">Tap the phrase you need</div>') + opts +
      (picked ? (right ? '<div class="pill" style="background:var(--box-soft);color:var(--box-deep);font-weight:600;display:flex;align-items:center;gap:8px;padding:4px 4px 4px 14px">Spot on — now say it aloud ' + speakBtn(q.ph.text, lang, 'Hear it again') + '</div>'
        : '<div class="err small" style="text-align:center">Not quite — the right phrase is filled in. It’ll come back later today.</div>') : '') +
      '</div><div class="footerbar"><button class="btn primary block" id="nextQ"' + (picked ? '' : ' disabled') + '>' + (picked ? 'Next' : 'Pick a phrase') + '</button></div></div>';
  }
  function bindPractice(id) {
    var P = state.practice; if (!P) return;
    app.querySelectorAll('[data-pick]').forEach(function (b) {
      b.onclick = function () {
        var q = P.qs[P.i]; P.picked = b.getAttribute('data-pick');
        var ok = P.picked === q.ph.id; if (ok) say(q.ph.text, q.p.pack.target_language);
        recordAnswer(q.p.pack.id, q.ph.id, ok);
        render();
      };
    });
    var n = document.getElementById('nextQ'); if (n) n.onclick = function () { P.i++; P.picked = null; render(); window.scrollTo(0, 0); };
    var r = document.getElementById('restart'); if (r) r.onclick = function () { state.practice = buildPractice(id); render(); };
  }

  function settingsScreen() {
    var st = settings(), langs = [];
    state.packs.forEach(function (p) { if (langs.indexOf(p.pack.target_language) < 0) langs.push(p.pack.target_language); });
    var cur = state.settingsLang && langs.indexOf(state.settingsLang) >= 0 ? state.settingsLang : langs[0] || 'ja-JP';
    state.settingsLang = cur;
    var vs = voicesFor(cur), chosen = st.voices[cur] || (vs[0] && vs[0].name);
    var themes = [['light', 'Light'], ['dark', 'Dark'], ['amoled', 'AMOLED'], ['system', 'Auto']].map(function (t) { return '<button data-theme-set="' + t[0] + '" aria-pressed="' + (st.theme === t[0]) + '">' + t[1] + '</button>'; }).join('');
    var chips = langs.map(function (l) { return '<button class="lang-chip" data-lang-set="' + esc(l) + '" aria-pressed="' + (l === cur) + '">' + esc(badge(l)) + ' · ' + esc(langName(l)) + '</button>'; }).join('');
    var voices = vs.length ? vs.map(function (v, i) {
      return '<div class="voice"><button class="radio' + (v.name === chosen ? ' on' : '') + '" data-voice="' + esc(v.name) + '" aria-label="Use voice ' + (i + 1) + '" role="radio" aria-checked="' + (v.name === chosen) + '"></button><div class="grow" data-voice="' + esc(v.name) + '" style="cursor:pointer"><b style="font-size:15px">Voice ' + (i + 1) + '</b><div class="muted small">' + esc(v.name) + (v.localService ? '' : ' · online') + '</div></div><button class="speak" data-preview="' + esc(v.name) + '" aria-label="Hear voice ' + (i + 1) + '">' + ICON.play + '</button></div>';
    }).join('') : '<div class="muted small" style="padding:14px">' + (synth ? (state.voices.length ? 'No ' + esc(langName(cur)) + ' voice in this browser yet.' : 'Loading voices…') : 'This browser can’t speak.') + '</div>';
    return '<div class="screen">' + topbar('Settings', '') + '<div class="pad stack" style="padding-bottom:40px">' +
      '<h2>Appearance</h2><div class="seg" role="group" aria-label="Theme">' + themes + '</div>' +
      '<h2 style="margin-top:8px">Voices</h2><p class="muted small" style="margin:-8px 0 0">Each language has its own voice. Voices come free with your phone or computer.</p>' +
      '<div class="chips">' + chips + '</div><div class="card">' + voices + '</div>' +
      '<p class="muted small" style="margin:0">Need more voices? On iPhone: Settings › Accessibility › Spoken Content › Voices. On Android: Settings › Text-to-speech.</p>' +
      '<h2 style="margin-top:8px">Speed</h2>' +
      '<label class="small">Normal speed <b id="nv">' + st.normal.toFixed(1) + '×</b><input type="range" min="0.6" max="1.4" step="0.1" value="' + st.normal + '" id="normal"></label>' +
      '<label class="small">Slow speed <b id="sv">' + st.slow.toFixed(1) + '×</b><input type="range" min="0.3" max="0.9" step="0.1" value="' + st.slow + '" id="slow"></label>' +
      '<h2 style="margin-top:8px">Backup &amp; restore</h2><div class="card stack" style="padding:16px;gap:10px"><span class="muted small">Saves your packs, progress and settings in one file. Restore it on a new phone — iPhone web app or the Android app.</span><b class="small">' + (LS.get('bk', {}).last ? 'Last backup: ' + new Date(LS.get('bk', {}).last).toLocaleDateString() : 'Not backed up yet') + '</b><div class="row" style="gap:10px;flex-wrap:wrap"><button class="btn ghost small" id="bkNow">Back up now</button><label class="btn ghost small" for="restoreFile">Restore</label><input id="restoreFile" type="file" accept=".json,application/json,text/plain" class="sr"></div><span class="small" id="bkMsg" style="color:var(--box-text)"></span></div>' +
      '<h2 style="margin-top:8px">Daily reminder</h2><p class="muted small" style="margin:-8px 0 0">Daily reminders and the phrase-of-the-day widget are in the Android app.</p>' +
      '<h2 style="margin-top:8px">About</h2><div class="card" style="padding:16px">' + logo(20) +
      '<p class="muted small">Web app ' + VERSION + ' (preview) · updates itself each time you open it while online.<br>Your packs and progress stay in this browser only.<br>Open source under the Apache License 2.0 · Made by Lee Wen Yang (Mike) · Kith + Keen</p>' +
      '<a class="link" style="padding:0" href="https://github.com/aadvark/Qonverse">github.com/aadvark/Qonverse</a></div></div></div>';
  }
  function bindSettings() {
    app.querySelectorAll('[data-theme-set]').forEach(function (b) { b.onclick = function () { var s = settings(); s.theme = b.getAttribute('data-theme-set'); saveSettings(s); applyTheme(); render(); }; });
    app.querySelectorAll('[data-lang-set]').forEach(function (b) { b.onclick = function () { state.settingsLang = b.getAttribute('data-lang-set'); render(); }; });
    app.querySelectorAll('[data-voice]').forEach(function (b) { b.onclick = function () { var s = settings(); s.voices[state.settingsLang] = b.getAttribute('data-voice'); saveSettings(s); render(); }; });
    var samples = { ja: 'いらっしゃいませ', zh: '欢迎光临', ko: '어서 오세요', th: 'สวัสดีค่ะ', vi: 'Xin chào quý khách' };
    app.querySelectorAll('[data-preview]').forEach(function (b) {
      b.onclick = function () {
        var name = b.getAttribute('data-preview'), v = state.voices.filter(function (x) { return x.name === name; })[0];
        if (!synth) return; synth.cancel(); var u = new SpeechSynthesisUtterance(samples[base(state.settingsLang)] || 'Hello'); u.lang = state.settingsLang; if (v) u.voice = v; u.rate = settings().normal; synth.speak(u);
      };
    });
    var rf = document.getElementById('restoreFile');
    rf.onchange = function (e) {
      var f = e.target.files[0]; if (!f) return; var r = new FileReader();
      r.onload = function () { try { var res = restoreBackup(r.result); render(); toast('Restored ' + res.packs + ' packs and progress for ' + res.phrases + ' phrases.'); } catch (err) { document.getElementById('bkMsg').textContent = err.message; } };
      r.readAsText(f);
    };
    ['normal', 'slow'].forEach(function (k) {
      var el = document.getElementById(k); el.oninput = function () { var s = settings(); s[k] = parseFloat(el.value); saveSettings(s); document.getElementById(k === 'normal' ? 'nv' : 'sv').textContent = s[k].toFixed(1) + '×'; };
    });
  }

  // ---------------------------------------------------------------- show to guest
  function showGuest(ph, lang) {
    var line = ph.in_a_sentence || { text: ph.text, romanization: ph.romanization, meaning: ph.meaning };
    var el = document.createElement('div'); el.className = 'guest'; el.setAttribute('role', 'dialog'); el.setAttribute('aria-label', 'Show this to your guest');
    el.innerHTML = '<div class="row"><span class="grow muted small">Show this to your guest</span><button class="link" data-close>Close</button></div>' +
      '<div class="guest-body"><div class="guest-text" lang="' + esc(base(lang)) + '">' + esc(line.text) + '</div><i class="guest-rule"></i>' +
      (line.romanization ? '<div class="muted">' + esc(line.romanization) + '</div>' : '') + '<div class="muted">' + esc(line.meaning) + '</div></div>' +
      '<button class="speak big" style="align-self:center" aria-label="Play it for the guest">' + ICON.playBig + '</button>';
    document.body.appendChild(el);
    el.querySelector('[data-close]').onclick = function () { el.remove(); };
    el.querySelector('.speak').onclick = function () { say(line.text, lang); };
  }

  // ---------------------------------------------------------------- pronunciation check
  var SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  function norm(s) { return String(s).toLowerCase().replace(/[\s\p{P}\p{S}]/gu, ''); }
  function sim(a, b) {
    if (!a || !b) return 0; var d = [], i, j;
    for (i = 0; i <= a.length; i++) { d[i] = [i]; } for (j = 0; j <= b.length; j++) d[0][j] = j;
    for (i = 1; i <= a.length; i++) for (j = 1; j <= b.length; j++) d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    return 1 - d[a.length][b.length] / Math.max(a.length, b.length);
  }
  function checkPron(ph, lang) {
    var out = document.getElementById('pronText'); if (!SR || !out) return;
    var rec = new SR(); rec.lang = lang; rec.maxAlternatives = 5; rec.interimResults = false;
    out.innerHTML = '<b style="display:block">Listening…</b><span class="muted small">Say: ' + esc(ph.text) + '</span>';
    rec.onresult = function (e) {
      var target = norm(ph.text), best = '', score = 0;
      for (var k = 0; k < e.results[0].length; k++) { var h = e.results[0][k].transcript, n = norm(h), sc = n.indexOf(target) >= 0 ? 1 : sim(n, target); if (sc > score) { score = sc; best = h; } }
      out.innerHTML = score >= 0.75 ? '<b style="display:block;color:var(--box-text)">✓ Understood!</b><span class="muted small">It heard: ' + esc(best) + '</span>'
        : '<b style="display:block;color:var(--danger)">Not quite — try again</b><span class="muted small">It heard: ' + esc(best || '…') + '</span>';
    };
    rec.onerror = function () { out.innerHTML = '<b style="display:block">Couldn’t listen</b><span class="muted small">Allow the microphone, or try Chrome or Safari.</span>'; };
    try { rec.start(); } catch (e) {}
  }

  // ---------------------------------------------------------------- starred & search
  function listItems(items, empty) {
    if (!items.length) return '<p class="muted">' + esc(empty) + '</p>';
    return items.map(function (x) {
      return '<div class="card row" style="padding:10px 8px 10px 14px"><button class="grow" style="background:none;border:none;text-align:left;padding:0" data-go="phrase/' + esc(x.p.pack.id) + '/' + esc(x.sid) + '/' + x.i + '">' + tri(esc(x.ph.text), x.ph.romanization, x.ph.meaning) + '<span class="muted tiny">' + esc(x.p.pack.title) + '</span></button>' + speakBtn(x.ph.text, x.p.pack.target_language, 'Play') + '</div>';
    }).join('');
  }
  function listScreen(title, items, empty) { return '<div class="screen">' + topbar(title, '', speedChip()) + '<div class="pad stack" style="gap:10px;padding-bottom:40px">' + listItems(items, empty) + '</div></div>'; }
  function searchScreen() {
    return '<div class="screen">' + topbar('Search phrases', '') + '<div class="pad stack" style="gap:10px;padding-bottom:40px"><label class="sr" for="q">Search</label><input id="q" type="search" placeholder="Try “bill”, “passport” or お会計" style="min-height:48px;border-radius:14px;border:1px solid var(--line);background:var(--surface);padding:0 14px;font-size:16px" value="' + esc(state.q || '') + '"><div id="results"></div></div></div>';
  }
  function bindSearch() {
    var q = document.getElementById('q'), out = document.getElementById('results');
    function run() {
      state.q = q.value; var n = q.value.trim().toLowerCase();
      if (!n) { out.innerHTML = ''; return; }
      var hits = allRefs().filter(function (x) { var ph = x.ph, s = ph.in_a_sentence || {}; return [ph.text, ph.romanization, ph.meaning, s.text, s.meaning, ph.when_to_use].some(function (v) { return v && String(v).toLowerCase().indexOf(n) >= 0; }); }).slice(0, 60);
      out.innerHTML = '<div class="stack" style="gap:10px">' + listItems(hits, 'No phrases match “' + q.value + '”.') + '</div>';
    }
    q.addEventListener('input', run); run(); q.focus();
  }

  // ---------------------------------------------------------------- hands-free shadow mode
  var shadow = { on: false, run: 0, lock: null };
  function stopShadow() { shadow.on = false; shadow.run++; if (synth) synth.cancel(); if (shadow.lock) { try { shadow.lock.release(); } catch (e) {} shadow.lock = null; } }
  function speakWait(text, lang) {
    return new Promise(function (res) {
      if (!synth) return res(); var st = settings(), vs = voicesFor(lang), v = vs.filter(function (x) { return x.name === st.voices[lang]; })[0] || vs[0];
      var u = new SpeechSynthesisUtterance(text); u.lang = lang; if (v) u.voice = v; u.rate = st.normal;
      var done = false, fin = function () { if (!done) { done = true; res(); } }; u.onend = fin; u.onerror = fin; setTimeout(fin, 15000);
      synth.cancel(); synth.speak(u);
    });
  }
  function wait(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function shadowScreen(id) {
    var p = findPack(id); if (!p) return packScreen(id);
    var items = phrasesOf(p); state.shadowI = Math.min(state.shadowI || 0, items.length - 1);
    var ph = items[state.shadowI];
    return '<div class="screen">' + topbar('Shadow mode', 'pack/' + id, speedChip() + '<span class="muted small" style="padding:0 10px 0 6px">' + (state.shadowI + 1) + '/' + items.length + '</span>') +
      '<div class="pad"><div class="bar" style="height:4px"><i style="width:' + Math.round(100 * (state.shadowI + 1) / items.length) + '%"></i></div></div>' +
      '<div class="pad stack" style="flex:1;justify-content:center;align-items:center;text-align:center;gap:16px;padding-top:24px;padding-bottom:120px"><span class="chip" id="stage">' + (shadow.on ? 'Listen' : 'Ready') + '</span>' +
      '<div class="phrase-text" lang="' + esc(base(p.pack.target_language)) + '">' + esc(ph.text) + '</div>' + (ph.romanization ? '<div class="muted" style="font-size:17px">' + esc(ph.romanization) + '</div>' : '') +
      '<div style="font-size:18px;font-weight:600">' + esc(ph.meaning) + '</div><p class="muted small">Each phrase plays twice, with a pause for you to repeat it. Works well with earphones.</p></div>' +
      '<div class="footerbar"><button class="btn ghost" style="flex:1" id="shPrev">‹ Back</button><button class="btn primary" style="flex:1.3" id="shPlay">' + (shadow.on ? 'Pause' : 'Play') + '</button><button class="btn ghost" style="flex:1" id="shNext">Next ›</button></div></div>';
  }
  function bindShadow(id) {
    var p = findPack(id); if (!p) return; var items = phrasesOf(p), lang = p.pack.target_language;
    function go2(i) { state.shadowI = Math.max(0, Math.min(items.length - 1, i)); var was = shadow.on; stopShadow(); shadow.on = was; render(); if (was) loop(); }
    document.getElementById('shPrev').onclick = function () { go2(state.shadowI - 1); };
    document.getElementById('shNext').onclick = function () { go2(state.shadowI + 1); };
    document.getElementById('shPlay').onclick = function () { if (shadow.on) { stopShadow(); render(); } else { shadow.on = true; render(); loop(); } };
    async function loop() {
      var my = ++shadow.run;
      try { if (navigator.wakeLock) shadow.lock = await navigator.wakeLock.request('screen'); } catch (e) {}
      while (shadow.on && my === shadow.run) {
        var ph = items[state.shadowI];
        for (var k = 0; k < 2 && my === shadow.run; k++) {
          var sEl = document.getElementById('stage'); if (sEl) { sEl.textContent = 'Listen'; sEl.style.background = ''; sEl.style.color = ''; }
          await speakWait(ph.text, lang); if (my !== shadow.run) return;
          sEl = document.getElementById('stage'); if (sEl) { sEl.textContent = 'Your turn — say it'; sEl.style.background = 'var(--audio)'; sEl.style.color = 'var(--on-audio)'; }
          await wait(Math.min(7000, 1600 + ph.text.length * 260 / settings().normal));
        }
        if (my !== shadow.run) return;
        if (state.shadowI < items.length - 1) { state.shadowI++; render(); shadow.run = my; } else { stopShadow(); render(); toast('Finished — great work!'); return; }
      }
    }
  }

  // ---------------------------------------------------------------- router
  function go(route, replace) { if (replace) history.replaceState(null, '', '#' + route); else location.hash = route; if (replace) render(); }
  function render() {
    var parts = location.hash.replace(/^#\/?/, '').split('/').map(decodeURIComponent), r = parts[0] || '';
    var html, bind = function () {};
    if (r === 'add') { html = addScreen(); bind = bindAdd; }
    else if (r === 'pack') { html = packScreen(parts[1]); bind = function () { var d = document.getElementById('del'); if (d) d.onclick = function () { if (d.dataset.armed) { deletePack(parts[1]); go(''); } else { d.dataset.armed = 1; d.textContent = 'Tap again to delete'; } }; }; }
    else if (r === 'phrase') { html = phraseScreen(parts[1], parts[2], parseInt(parts[3] || '0', 10)); bind = function () { bindPhrase(parts[1], parts[2], parseInt(parts[3] || '0', 10)); }; }
    else if (r === 'practice') { html = practiceScreen(parts[1] || 'all'); bind = function () { bindPractice(parts[1] || 'all'); }; }
    else if (r === 'settings') { html = settingsScreen(); bind = bindSettings; }
    else if (r === 'starred') { html = listScreen('Starred phrases', allRefs().filter(function (x) { return isStar(x.p.pack.id, x.ph.id); }), 'No starred phrases yet. Tap the ☆ on any phrase to keep it here for quick access during your shift.'); }
    else if (r === 'search') { html = searchScreen(); bind = bindSearch; }
    else if (r === 'shadow') { html = shadowScreen(parts[1]); bind = function () { bindShadow(parts[1]); }; }
    else { state.practice = null; html = home(); }
    if (r !== 'shadow') stopShadow();
    app.innerHTML = html; bind();
    var ih = document.getElementById('install'); if (ih && state.installReady) ih.hidden = r !== '';
  }
  app.addEventListener('click', function (e) {
    if (e.target.closest('[data-speed]')) {
      var st = settings(), cur = st.normal, nx = SPEEDS.filter(function (v) { return v > cur + 0.01; })[0] || SPEEDS[0];
      st.normal = nx; saveSettings(st); app.querySelectorAll('[data-speed]').forEach(function (b) { b.outerHTML = speedChip(); }); return;
    }
    if (e.target.closest('#bkNow')) { doBackup(function (m) { var el = document.getElementById('bkMsg'); if (el) el.textContent = m; else { toast(m); render(); } }); return; }
    if (e.target.closest('#bkLater')) { var bk = LS.get('bk', {}); bk.snooze = Date.now() + 7 * 864e5; LS.set('bk', bk); render(); return; }
    var t = e.target.closest('[data-go],[data-say]'); if (!t || t.disabled) return;
    if (t.hasAttribute('data-say')) { say(t.getAttribute('data-say'), t.getAttribute('data-lang'), { slow: !!t.getAttribute('data-slow'), times: parseInt(t.getAttribute('data-times') || '1', 10) }); return; }
    var to = t.getAttribute('data-go'); if (to.indexOf('practice') === 0) state.practice = null;
    go(to); window.scrollTo(0, 0);
  });
  window.addEventListener('hashchange', render);

  // ---------------------------------------------------------------- install hint (iPhone / iPad)
  function installHint() {
    var ios = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    var standalone = navigator.standalone || (window.matchMedia && matchMedia('(display-mode: standalone)').matches);
    if (!ios || standalone || LS.get('installHintDone', false)) return;
    var el = document.getElementById('install');
    el.innerHTML = '<span class="badge" style="width:44px;height:44px;background:var(--box);color:#fff">' + ICON.share + '</span><span class="grow small"><b>Install Qonverse</b><br>Tap Share, then “Add to Home Screen”.</span><button class="icon-btn" aria-label="Dismiss">' + ICON.close + '</button>';
    state.installReady = true; el.hidden = (location.hash.replace(/^#\/?/, '') !== '');
    el.querySelector('button').onclick = function () { el.hidden = true; state.installReady = false; LS.set('installHintDone', true); };
  }

  // ---------------------------------------------------------------- start
  applyTheme();
  if (window.matchMedia) matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);
  app.innerHTML = '<div class="screen" style="justify-content:center;align-items:center">' + logo(28) + '</div>';
  fetch('../packs/index.json').then(function (r) { return r.json(); }).then(function (list) {
    return Promise.all(list.map(function (f) { return fetch('../packs/' + f).then(function (r) { return r.json(); }); }));
  }).catch(function () { return []; }).then(function (packs) {
    state.builtIn = packs; rebuild(); render(); installHint();
  });
})();
