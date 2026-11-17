"""Builds docs/index.html (the GitHub Pages landing page) from the template, the packs and the APK."""
import hashlib, html, json, math, os, re, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
version = re.search(r'versionName = "([^"]+)"', open(os.path.join(ROOT, "app/build.gradle.kts")).read()).group(1)
apk = os.path.join(DOCS, "download", f"Qonverse-{version}.apk")
sha = hashlib.sha256(open(apk, "rb").read()).hexdigest()
size = f"{os.path.getsize(apk) / 1_000_000:.1f} MB"

# seamless isometric cube tile (s = 17px)
s = 17; c = math.cos(math.pi / 6); w = 2 * s * c
d = []
for j in range(-1, 4):
    for i in range(-1, 3):
        x = i * w + (w / 2 if j % 2 else 0); y = j * 1.5 * s
        P = lambda dx, dy: f"{x+dx:.2f} {y+dy:.2f}"
        d.append(f"M{P(-s*c,-s/2)}L{P(0,-s)}L{P(s*c,-s/2)}L{P(s*c,s/2)}L{P(0,s)}L{P(-s*c,s/2)}Z"
                 f"M{P(-s*c,-s/2)}L{P(0,0)}L{P(s*c,-s/2)}M{P(0,0)}L{P(0,s)}")
tile = (f'<pattern id="cube" width="{w:.3f}" height="{3*s}" patternUnits="userSpaceOnUse">'
        f'<path d="{"".join(d)}" fill="none" stroke="currentColor" stroke-width="1" stroke-linejoin="round"/></pattern>')

BADGE = {"ja": "日本語", "zh": "中文", "ko": "한국어", "th": "ไทย"}
os.makedirs(os.path.join(DOCS, "packs"), exist_ok=True)
cards = []
for f in sorted(os.listdir(os.path.join(ROOT, "packs"))):
    if not f.endswith(".json"): continue
    shutil.copy(os.path.join(ROOT, "packs", f), os.path.join(DOCS, "packs", f))
    p = json.load(open(os.path.join(ROOT, "packs", f), encoding="utf-8"))
    n = sum(len(sc["phrases"]) for sc in p["scenarios"])
    ph = p["scenarios"][0]["phrases"][0]
    lang = p["pack"]["target_language"].split("-")[0]
    e = html.escape
    cards.append(f'''      <div class="card pack">
        <div class="badge" lang="{e(lang)}">{BADGE.get(lang, lang.upper())}</div>
        <h3>{e(p["pack"]["title"])}</h3>
        <p style="margin:0;color:var(--muted);font-size:14px">{len(p["scenarios"])} scenarios · {n} phrases · {e(p["pack"].get("politeness",""))}</p>
        <div class="sample"><b lang="{e(lang)}">{e(ph["text"])}</b><span>{e(ph["romanization"])}</span><span>{e(ph["meaning"])}</span></div>
        <a href="packs/{e(f)}" download>Download pack (.json)</a>
      </div>''')

t = open(os.path.join(ROOT, "site-src", "index.template.html"), encoding="utf-8").read()
t = re.sub(r'<pattern id="cube".*?</pattern>', tile, t, flags=re.S)
t = (t.replace("__VERSION__", version).replace("__SIZE__", size).replace("__SHA__", sha)
      .replace("__PACKS__", "\n".join(cards))
      .replace("__PROMPT__", html.escape(open(os.path.join(ROOT, "prompts", "pack-generator-prompt.md"), encoding="utf-8").read())))
open(os.path.join(DOCS, "index.html"), "w", encoding="utf-8").write(t)
open(os.path.join(DOCS, ".nojekyll"), "w").close()
print("docs/index.html built:", version, size, sha[:12])
