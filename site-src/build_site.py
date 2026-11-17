"""Builds the website in docs/: the landing page, version.json (for in-app updates), the pack list and the prompt copy.

Run after building the APK:  python3 site-src/build_site.py
"""
import hashlib, html, json, math, os, re, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
E = html.escape
gradle = open(os.path.join(ROOT, "app/build.gradle.kts")).read()
version = re.search(r'versionName = "([^"]+)"', gradle).group(1)
code = int(re.search(r'versionCode = (\d+)', gradle).group(1))

# ---------------------------------------------------------------- APK, version.json, packs, prompt
apk = os.path.join(DOCS, "download", f"Qonverse-{version}.apk")
for old in os.listdir(os.path.join(DOCS, "download")):
    if old.endswith(".apk") and old != os.path.basename(apk):
        os.remove(os.path.join(DOCS, "download", old))
sha = hashlib.sha256(open(apk, "rb").read()).hexdigest()
size = f"{os.path.getsize(apk) / 1_000_000:.1f} MB"
notes = open(os.path.join(ROOT, "site-src", "release-notes.txt"), encoding="utf-8").read().strip()
json.dump({"versionCode": code, "versionName": version,
           "apkUrl": f"https://raw.githubusercontent.com/aadvark/Qonverse/main/docs/download/Qonverse-{version}.apk",
           "notes": notes}, open(os.path.join(DOCS, "version.json"), "w"), indent=2)

os.makedirs(os.path.join(DOCS, "packs"), exist_ok=True)
pack_files = sorted(f for f in os.listdir(os.path.join(ROOT, "packs")) if f.endswith(".json"))
for f in pack_files:
    shutil.copy(os.path.join(ROOT, "packs", f), os.path.join(DOCS, "packs", f))
json.dump(pack_files, open(os.path.join(DOCS, "packs", "index.json"), "w"))
shutil.copy(os.path.join(ROOT, "prompts", "pack-generator-prompt.md"), os.path.join(DOCS, "prompt.md"))
open(os.path.join(DOCS, ".nojekyll"), "w").close()

# ---------------------------------------------------------------- isometric cube wallpaper
S = 17
C = math.cos(math.pi / 6)
W = 2 * S * C
parts = []
for j in range(-1, 4):
    for i in range(-1, 3):
        x = i * W + (W / 2 if j % 2 else 0)
        y = j * 1.5 * S
        P = lambda dx, dy: f"{x + dx:.2f} {y + dy:.2f}"
        parts.append(f"M{P(-S*C, -S/2)}L{P(0, -S)}L{P(S*C, -S/2)}L{P(S*C, S/2)}L{P(0, S)}L{P(-S*C, S/2)}Z"
                     f"M{P(-S*C, -S/2)}L{P(0, 0)}L{P(S*C, -S/2)}M{P(0, 0)}L{P(0, S)}")
CUBE_D = "".join(parts)
_count = [0]


def cubes():
    _count[0] += 1
    pid = f"cube{_count[0]}"
    return (f'<svg class="cubes" aria-hidden="true"><defs><pattern id="{pid}" width="{W:.3f}" height="{3 * S}" patternUnits="userSpaceOnUse">'
            f'<path d="{CUBE_D}" fill="none" stroke="currentColor" stroke-width="1" stroke-linejoin="round"/></pattern></defs>'
            f'<rect width="100%" height="100%" fill="url(#{pid})"/></svg>')


# ---------------------------------------------------------------- language marquee
GREET = [("いらっしゃいませ", "Japanese"), ("欢迎光临", "Mandarin"), ("어서 오세요", "Korean"), ("สวัสดีค่ะ", "Thai"),
         ("Xin chào quý khách", "Vietnamese"), ("Selamat datang", "Malay"), ("歡迎光臨", "Cantonese"),
         ("Maligayang pagdating", "Tagalog"), ("स्वागत है", "Hindi"), ("வரவேற்கிறோம்", "Tamil"), ("Bienvenido", "Spanish"),
         ("Bienvenue", "French"), ("Willkommen", "German"), ("أهلاً وسهلاً", "Arabic")]
one = "".join(f'<span class="item"><span>{E(g)}</span><small>{l}</small></span><span class="dot" aria-hidden="true">●</span>' for g, l in GREET)
marquee = one + one.replace('class="item"', 'class="item" aria-hidden="true"')

# ---------------------------------------------------------------- feature carousel
IC = {
    "play": '<svg width="30" height="30" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>',
    "chat": '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/><path d="M8.5 11h.01M12 11h.01M15.5 11h.01"/></svg>',
    "spark": '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3l1.9 4.6L18.5 9l-4.6 1.9L12 15.5l-1.9-4.6L5.5 9l4.6-1.4z"/><path d="M19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/></svg>',
    "abc": '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h10M9 4v3M6 7c1 4 4 7 8 8M13 7c-1 4-4 7-8 8M14 20l3.5-8 3.5 8M15 17.5h5"/></svg>',
    "voice": '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M3 18v-6a9 9 0 0 1 18 0v6"/><path d="M21 19a2 2 0 0 1-2 2h-1v-6h3zM3 19a2 2 0 0 0 2 2h1v-6H3z"/></svg>',
    "eye": '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>',
    "star": '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z"/></svg>',
    "cloud": '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 18a5 5 0 1 1 .9-9.9A6 6 0 0 1 19 10a4 4 0 0 1-1 8z"/><path d="M12 12v6M9.5 14.5 12 12l2.5 2.5"/></svg>',
    "moon": '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>',
}
SLIDES = [
    ("listen", "Listen", "audio", "play", "Listen, imitate, check",
     "Hear every phrase, sentence and reply in a natural voice, say it back, then let your phone check your pronunciation.",
     ["Speed button right on the screen: 0.6× to 1.2×", "Slow and loop ×3 playback", "Pronunciation check with your phone's speech recognition"],
     "03-phrase.webp", "Phrase screen with speed, play and pronunciation controls"),
    ("guest", "Show guest", "box", "eye", "Show it to your guest",
     "Busy lobby or unsure of your accent? Flip the phrase into big, clear text for the guest to read, with a play button.",
     ["Large print, readable across the counter", "Full sentence with meaning underneath", "Plays aloud for the guest too"],
     "08-show-guest.webp", "Large show-to-guest card in Japanese"),
    ("chat", "Smart review", "box", "chat", "Smarter review, as a chat",
     "The guest speaks; you tap the phrase you need. Spaced repetition brings back the ones you miss and spaces out the ones you know.",
     ["Today's review: what's due plus a few new phrases", "Speech-bubble chat, just like a messaging app", "Daily reminder and phrase-of-the-day widget on Android"],
     "04-practice.webp", "Chat-style review screen"),
    ("shadow", "Shadow mode", "audio", "voice", "Hands-free shadow mode",
     "Put your earphones in: each phrase plays, pauses for you to repeat, then moves on. Perfect for the commute.",
     ["Each phrase twice, with time to repeat", "Pause, back and next at any time", "Keeps the screen on while you practise"],
     "09-shadow.webp", "Shadow mode screen"),
    ("star", "Starred & search", "box", "star", "Starred phrases and search",
     "Star the phrases you use every shift for one-tap access, and search every pack in English or the other language.",
     ["Your shift's key phrases in one list", "Search “bill” and find お会計, 계산서, 刷卡…", "Tap any result to practise it"],
     "10-search.webp", "Search results for bill across packs"),
    ("ai", "Your AI", "box", "spark", "Made by your AI, for your job",
     "Copy one prompt into any AI chat. It interviews you and writes a pack for your exact role and guests.",
     ["Works with Claude, ChatGPT, Gemini and more", "Paste it in, or import a file from a colleague", "Every line with transliteration and meaning"],
     "05-add.webp", "Add a pack screen"),
    ("backup", "Backup", "box", "cloud", "Back up and switch phones",
     "Save your packs, progress and settings in one small file, then restore it on a new phone — Android or iPhone.",
     ["Save to Google Drive, iCloud or email", "Android backups restore in the iPhone web app", "Gentle reminder if you haven't backed up"],
     "06-settings.webp", "Settings with backup, reminder and voices"),
    ("themes", "Themes", "box", "moon", "Day, dark and AMOLED",
     "Calm violet by day, soft indigo at night, and true black to save battery on OLED screens.",
     ["Gentle colours that are easy on the eyes", "Follows your phone's setting on Auto", "Isometric cube wallpaper throughout"],
     "07-home-dark.webp", "Home screen in dark mode"),
]
slides_html, tabs_html = [], []
for k, (sid, tab, kind, ic, title, text, bullets, img, alt) in enumerate(SLIDES):
    lis = "".join(f"<li>{E(b)}</li>" for b in bullets)
    slides_html.append(
        f'<div class="slide{" on" if k == 0 else ""}" id="slide-{sid}" role="tabpanel" aria-roledescription="slide" aria-label="{k + 1} of {len(SLIDES)}: {E(title)}">'
        f'<div><div class="ic {kind}">{IC[ic]}</div><h3>{E(title)}</h3><p>{E(text)}</p><ul>{lis}</ul></div>'
        f'<div class="shot"><img src="img/{img}" width="540" height="1169" alt="{E(alt)}" loading="lazy"></div></div>')
    tabs_html.append(f'<button class="tab" role="tab" aria-controls="slide-{sid}" aria-selected="{"true" if k == 0 else "false"}">{E(tab)}<span class="fill"></span></button>')

# ---------------------------------------------------------------- sample pack cards
BADGE = {"ja": "日本語", "zh": "中文", "ko": "한국어", "th": "ไทย"}
cards = []
for f in pack_files:
    p = json.load(open(os.path.join(ROOT, "packs", f), encoding="utf-8"))
    n = sum(len(sc["phrases"]) for sc in p["scenarios"])
    ph = p["scenarios"][0]["phrases"][0]
    lang = p["pack"]["target_language"].split("-")[0]
    cards.append(
        f'      <div class="pack reveal"><div class="badge" lang="{E(lang)}">{BADGE.get(lang, lang.upper())}</div>'
        f'<h3>{E(p["pack"]["title"])}</h3><p style="margin:0;color:var(--muted);font-size:14px">{len(p["scenarios"])} scenarios · {n} phrases · {E(p["pack"].get("politeness", ""))}</p>'
        f'<div class="sample"><b lang="{E(lang)}">{E(ph["text"])}</b><span>{E(ph["romanization"])}</span><span>{E(ph["meaning"])}</span></div>'
        f'<a href="packs/{E(f)}" download>Download pack (.json)</a></div>')

# ---------------------------------------------------------------- page
t = open(os.path.join(ROOT, "site-src", "index.template.html"), encoding="utf-8").read()
while "__CUBES__" in t:
    t = t.replace("__CUBES__", cubes(), 1)
t = (t.replace("__MARQUEE__", marquee)
      .replace("__SLIDES__", "\n        ".join(slides_html))
      .replace("__TABS__", "".join(tabs_html))
      .replace("__PACKS__", "\n".join(cards))
      .replace("__VERSION__", version).replace("__SIZE__", size).replace("__SHA__", sha))
left = re.findall(r"__[A-Z]+__", t)
assert not left, left
open(os.path.join(DOCS, "index.html"), "w", encoding="utf-8").write(t)
print("docs/ built:", version, f"(code {code})", size, sha[:12])
