package com.kithandkeen.qonverse.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kithandkeen.qonverse.LocalApp
import com.kithandkeen.qonverse.data.PackFile
import com.kithandkeen.qonverse.data.Phrase
import kotlinx.coroutines.launch

// ================================================================ PHRASE (listen & imitate)

@Composable
fun PhraseScreen(packId: String, scenarioId: String, start: Int, close: () -> Unit) {
    val app = LocalApp.current
    val c = Q.c
    val pack = app.pack(packId) ?: run { TopBar("Not found", close); return }
    val scen = pack.scenarios.firstOrNull { it.id == scenarioId } ?: pack.scenarios.first()
    val lang = pack.pack.targetLanguage
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (scen.phrases.size - 1).coerceAtLeast(0))) { scen.phrases.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { app.store.saveLast(packId, scen.id, pager.currentPage) }
    val fam = app.familiar(packId)

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(scen.title, close) {
            SpeedChip()
            Text("${pager.currentPage + 1}/${scen.phrases.size}", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            scen.phrases.forEachIndexed { i, ph ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(
                        when { i == pager.currentPage -> c.audio; ph.id in fam -> c.box; else -> c.line },
                    ),
                )
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            PhraseCard(pack, scen.phrases[page], lang)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ph = scen.phrases[pager.currentPage]
            SecondaryButton("Again later", Modifier.weight(1f), color = c.ink) {
                app.store.markSeen(packId, ph.id, false); app.progressTick++
                scope.launch { if (pager.currentPage < scen.phrases.size - 1) pager.animateScrollToPage(pager.currentPage + 1) }
            }
            PrimaryButton(if (ph.id in fam) "✓ Familiar" else "Familiar", Modifier.weight(1f)) {
                app.store.markSeen(packId, ph.id, true); app.progressTick++
                scope.launch { if (pager.currentPage < scen.phrases.size - 1) pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
    }
}

@Composable
private fun PhraseCard(pack: PackFile, ph: Phrase, lang: String) {
    val app = LocalApp.current
    val c = Q.c
    Box(Modifier.fillMaxSize()) {
        CubeWallpaper()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // the phrase
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(22.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                var guest by remember(ph.id) { mutableStateOf(false) }
                val starred = remember(ph.id, app.progressTick) { app.store.isStarred(pack.pack.id, ph.id) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { if (ph.politeness.isNotBlank()) Chip(ph.politeness.replaceFirstChar { it.uppercase() } + " · Phrase", c.boxSoft, c.boxText) }
                    Text(
                        "Show guest", color = c.boxText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { guest = true }.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Switch) { app.store.setStarred(pack.pack.id, ph.id, !starred); app.progressTick++ }
                            .semantics { contentDescription = if (starred) "Remove star" else "Star this phrase" },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (starred) "★" else "☆", color = if (starred) c.orange else c.muted, fontSize = 24.sp) }
                }
                if (guest) ShowGuestDialog(ph, lang) { guest = false }
                Text(ph.text, color = c.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
                if (ph.romanization.isNotBlank()) Text(ph.romanization, color = c.muted, fontSize = 17.sp)
                Text(ph.meaning, color = c.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                ph.inASentence?.let { s ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    SectionLabel("In a sentence")
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(highlight(s.text, ph.text, c.boxText, c.audio), color = c.ink, fontSize = 16.sp, lineHeight = 24.sp)
                            if (s.romanization.isNotBlank()) Text(s.romanization, color = c.sub, fontSize = 14.sp)
                            Text(s.meaning, color = c.muted, fontSize = 14.sp)
                        }
                        HSpacer(8.dp)
                        SpeakerButton({ app.say(s.text, lang) }, 40.dp, "Play the sentence")
                    }
                }
            }
            // controls
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                ControlButton("0.6×", "Slow") { app.say(ph.text, lang, slow = true) }
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(c.audio)
                        .clickable(role = Role.Button) { app.say(ph.text, lang) }.semantics { contentDescription = "Play phrase" },
                    contentAlignment = Alignment.Center,
                ) { PlayIcon(34.dp, c.onAudio) }
                ControlButton("×3", "Loop") { app.say(ph.text, lang, times = 3) }
            }
            PronunciationCheck(ph, lang)
            // when to use
            if (ph.whenToUse.isNotBlank() || ph.notes.isNotBlank()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.boxSoft).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("WHEN TO USE IT", color = c.boxDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (ph.whenToUse.isNotBlank()) Text(ph.whenToUse, color = c.ink, fontSize = 15.sp, lineHeight = 21.sp)
                    if (ph.notes.isNotBlank()) Text(ph.notes, color = c.sub, fontSize = 14.sp, lineHeight = 20.sp)
                    if (ph.pattern.isNotBlank()) Text("Pattern: ${ph.pattern}", color = c.sub, fontSize = 14.sp)
                }
            }
            // conversation
            if (ph.example.dialogue.isNotEmpty()) {
                SectionLabel("In a conversation")
                if (ph.example.setting.isNotBlank()) {
                    Text(ph.example.setting, color = c.muted, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.surface).padding(horizontal = 12.dp, vertical = 6.dp))
                }
                ph.example.dialogue.forEach { line ->
                    val mine = line.isYou
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Text(if (mine) "You" else line.speaker.replaceFirstChar { it.uppercase() }, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Bubble(mine, Modifier.widthIn(max = 320.dp), fill = if (mine) c.boxSoft else c.surface, border = c.box) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(if (line.usesPhrase) highlight(line.text, ph.text, c.boxText, c.audio) else buildAnnotatedString { append(line.text) }, color = c.ink, fontSize = 16.sp, lineHeight = 23.sp)
                                    if (line.romanization.isNotBlank()) Text(line.romanization, color = c.sub, fontSize = 13.sp)
                                    if (line.meaning.isNotBlank()) Text(line.meaning, color = c.muted, fontSize = 13.sp)
                                }
                                HSpacer(8.dp)
                                SpeakerButton({ app.say(line.text, lang) }, 40.dp, "Play this line")
                            }
                        }
                    }
                }
                SecondaryButton("▶  Play whole conversation", Modifier.fillMaxWidth(), color = c.audioText) {
                    app.speaker.speakAll(ph.example.dialogue.map { it.text }, lang, app.speed, app.voiceFor(lang))
                }
            }
            if (ph.likelyResponses.isNotEmpty()) {
                SectionLabel("They might also say")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    ph.likelyResponses.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Tri(r.text, r.romanization, r.meaning, 15) }
                            SpeakerButton({ app.say(r.text, lang) }, 40.dp, "Play this reply")
                        }
                    }
                }
            }
            Spacer(12.dp)
        }
    }
}

@Composable
private fun ControlButton(big: String, small: String, onClick: () -> Unit) {
    val c = Q.c
    Column(
        Modifier.size(width = 76.dp, height = 64.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "$small playback" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(big, color = c.audioText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(small, color = c.muted, fontSize = 12.sp)
    }
}

private fun highlight(text: String, key: String, color: Color, underline: Color) = buildAnnotatedString {
    val i = if (key.isBlank()) -1 else text.indexOf(key)
    if (i < 0) { append(text); return@buildAnnotatedString }
    append(text.substring(0, i))
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(key) }
    append(text.substring(i + key.length))
}

// ================================================================ PRACTICE (chat style)

private data class Question(val pack: PackFile, val phrase: Phrase, val options: List<Phrase>)

/** Today's review: phrases that are due first, then up to 5 new ones; a random mix if nothing is due. */
private fun buildQuestions(packs: List<PackFile>, store: com.kithandkeen.qonverse.data.Store, count: Int = 10): List<Question> {
    val today = store.today()
    val all = packs.flatMap { p -> val s = store.srs(p.pack.id); p.scenarios.flatMap { sc -> sc.phrases.map { Triple(p, it, s[it.id]) } } }
    if (all.isEmpty()) return emptyList()
    val due = all.filter { it.third != null && it.third!!.second <= today }.sortedBy { it.third!!.second }
    val fresh = all.filter { it.third == null }.shuffled().take(5)
    var pool = (due + fresh).take(count)
    if (pool.isEmpty()) pool = all.shuffled().take(8)
    return pool.map { (p, ph, _) ->
        val others = p.scenarios.flatMap { it.phrases }.filter { it.id != ph.id }.shuffled().take(2)
        Question(p, ph, (others + ph).shuffled())
    }
}

fun reviewCounts(packs: List<PackFile>, store: com.kithandkeen.qonverse.data.Store): Pair<Int, Int> {
    val today = store.today()
    var due = 0; var new = 0
    packs.forEach { p -> val s = store.srs(p.pack.id); p.scenarios.forEach { sc -> sc.phrases.forEach { ph ->
        val e = s[ph.id]; if (e == null) new++ else if (e.second <= today) due++ } } }
    return due to new
}

@Composable
fun PracticeScreen(packId: String, close: () -> Unit) {
    val app = LocalApp.current
    val c = Q.c
    val packs = if (packId == "all") app.packs else listOfNotNull(app.pack(packId))
    val questions = remember(packId) { buildQuestions(packs, app.store) }
    var index by remember { mutableIntStateOf(0) }
    var picked by remember(index) { mutableStateOf<Phrase?>(null) }
    if (questions.isEmpty()) { Column { TopBar("Review", close); Text("Add a pack first.", color = c.muted, modifier = Modifier.padding(20.dp)) }; return }
    if (index >= questions.size) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            BubbleQ(72.dp, c.box)
            Text("Review done!", color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Say each phrase out loud a few more times today.", color = c.muted, fontSize = 15.sp)
            PrimaryButton("Back to home", Modifier.fillMaxWidth(), onClick = close)
        }
        return
    }
    val q = questions[index]
    val lang = q.pack.pack.targetLanguage
    val cue = q.phrase.example.dialogue.let { d ->
        val u = d.indexOfFirst { it.usesPhrase }
        if (u > 0 && !d[u - 1].isYou) d[u - 1] else d.firstOrNull { !it.isYou }
    }
    val correct = picked?.id == q.phrase.id

    Column(Modifier.fillMaxSize().background(c.chatBg)) {
        Column(Modifier.fillMaxWidth().background(c.surface).padding(bottom = 10.dp)) {
            TopBar(cue?.speaker?.replaceFirstChar { it.uppercase() }?.ifBlank { "Guest" } ?: "Guest", close) {
                SpeedChip()
                Text("${index + 1}/${questions.size}", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, end = 10.dp))
            }
            ProgressBar((index + 1) / questions.size.toFloat(), Modifier.padding(horizontal = 16.dp), 4.dp)
        }
        Box(Modifier.weight(1f)) {
            CubeWallpaper()
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (q.phrase.example.setting.isNotBlank()) {
                    Text(q.phrase.example.setting, color = c.muted, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(999.dp)).background(c.surface).padding(horizontal = 12.dp, vertical = 5.dp))
                }
                // what the guest says (or the situation)
                Bubble(false, Modifier.widthIn(max = 320.dp), fill = c.surface, border = c.box) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f, fill = false)) {
                            if (cue != null) Tri(cue.text, cue.romanization, cue.meaning, 17)
                            else Text(q.phrase.whenToUse, color = c.ink, fontSize = 15.sp)
                        }
                        if (cue != null) { HSpacer(8.dp); SpeakerButton({ app.say(cue.text, lang) }, 44.dp, "Hear them") }
                    }
                }
                if (picked == null) Text("Tap the phrase you need", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End))
                q.options.forEach { o ->
                    val isRight = picked != null && o.id == q.phrase.id
                    val isWrong = picked != null && o.id == picked?.id && !isRight
                    if (picked == null || isRight || isWrong) {
                        Box(Modifier.align(Alignment.End).clickable(enabled = picked == null) {
                            picked = o
                            val right = o.id == q.phrase.id
                            if (right) app.say(o.text, lang)
                            app.store.recordAnswer(q.pack.pack.id, q.phrase.id, right); app.progressTick++
                        }) {
                            Bubble(
                                true, Modifier.widthIn(max = 320.dp),
                                fill = when { isRight -> c.box; isWrong -> c.dangerSoft; else -> c.boxFaint },
                                border = when { isRight -> c.box; isWrong -> c.danger; else -> c.box.copy(alpha = 0.5f) },
                            ) {
                                Tri(
                                    o.text, o.romanization, o.meaning, 16,
                                    color = if (isRight) c.onBox else c.ink,
                                    subColor = if (isRight) c.onBox.copy(alpha = 0.85f) else c.sub,
                                    mutedColor = if (isRight) c.onBox.copy(alpha = 0.85f) else c.muted,
                                )
                            }
                        }
                    }
                }
                if (picked != null) {
                    if (correct) {
                        Row(
                            Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(999.dp)).background(c.boxSoft).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Spot on — now say it aloud", color = c.boxDeep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            HSpacer(8.dp)
                            SpeakerButton({ app.say(q.phrase.text, lang) }, 36.dp, "Hear it again")
                        }
                    } else {
                        Text("Not quite — the right phrase is filled in. It'll come back later today.", color = c.dangerText, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.dangerSoft).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(c.surface).padding(16.dp)) {
            PrimaryButton(if (picked == null) "Pick a phrase" else "Next", Modifier.weight(1f), enabled = picked != null) { index++ }
        }
    }
}

// ================================================================ SETTINGS

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(back: () -> Unit) {
    val app = LocalApp.current
    val c = Q.c
    val ctx = LocalContext.current
    val langs = app.packs.map { it.pack.targetLanguage }.distinct()
    var lang by remember { mutableStateOf(langs.firstOrNull() ?: "ja-JP") }
    var voiceTick by remember { mutableIntStateOf(0) }
    var normal by remember { mutableFloatStateOf(app.store.normalRate) }
    var slow by remember { mutableFloatStateOf(app.store.slowRate) }
    val sample = mapOf("ja" to "いらっしゃいませ", "zh" to "欢迎光临", "ko" to "어서 오세요", "th" to "สวัสดีค่ะ", "vi" to "Xin chào quý khách")

    Box(Modifier.fillMaxSize()) {
        CubeWallpaper()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TopBar("Settings", back)
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Appearance", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.track).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("light" to "Light", "dark" to "Dark", "amoled" to "AMOLED", "system" to "Auto").forEach { (k, label) ->
                        val on = app.theme == k
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp)).background(if (on) c.surface else Color.Transparent)
                                .clickable(role = Role.RadioButton) { app.changeTheme(k) },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = if (on) c.ink else c.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                    }
                }

                Spacer(6.dp)
                Text("Voices", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Each language has its own voice. Voices come free with your phone's speech engine.", color = c.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    langs.forEach { l ->
                        val on = l == lang
                        Text(
                            "${langBadge(l)} · ${langEnglish(l)}", color = if (on) c.onBox else c.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) c.box else c.surface)
                                .border(1.dp, if (on) c.box else c.line, RoundedCornerShape(999.dp)).clickable { lang = l }.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                val voices = remember(lang, app.speaker.ready, voiceTick) { app.speaker.voicesFor(lang) }
                val chosen = remember(lang, voiceTick) { app.store.voiceFor(lang) }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    if (voices.isEmpty()) {
                        Text(
                            if (app.speaker.ready) "No ${langEnglish(lang)} voice on this phone yet." else "Loading voices…",
                            color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                    voices.forEachIndexed { i, v ->
                        val on = (chosen ?: voices.first().name) == v.name
                        val needsDownload = v.features?.contains(android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                        Row(
                            Modifier.fillMaxWidth().height(60.dp).clickable(role = Role.RadioButton) { app.store.setVoiceFor(lang, v.name); voiceTick++ },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) c.box else c.muted, CircleShape), contentAlignment = Alignment.Center) {
                                if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(c.box))
                            }
                            HSpacer(12.dp)
                            Column(Modifier.weight(1f)) {
                                Text("Voice ${i + 1}", color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    listOfNotNull(
                                        if (v.isNetworkConnectionRequired) "online" else "offline",
                                        if (needsDownload) "needs download" else null,
                                    ).joinToString(" · "), color = c.muted, fontSize = 13.sp,
                                )
                            }
                            SpeakerButton({
                                app.speaker.speak(sample[lang.substringBefore('-')] ?: "Hello", lang, normal, 1, v.name)
                            }, 40.dp, "Hear voice ${i + 1}")
                        }
                    }
                }
                SmallButton("Get more voices") { runCatching { ctx.startActivity(app.speaker.installVoicesIntent()) } }

                Spacer(6.dp)
                Text("Speed", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val sliderColors = SliderDefaults.colors(thumbColor = c.box, activeTrackColor = c.box, inactiveTrackColor = c.track)
                Text("Normal speed  ${"%.1f".format(normal)}×", color = c.ink, fontSize = 15.sp)
                Slider(normal, { normal = it }, valueRange = 0.6f..1.4f, onValueChangeFinished = { app.changeSpeed(normal) }, colors = sliderColors)
                Text("Slow speed  ${"%.1f".format(slow)}×", color = c.ink, fontSize = 15.sp)
                Slider(slow, { slow = it }, valueRange = 0.3f..0.9f, onValueChangeFinished = { app.store.slowRate = slow }, colors = sliderColors)

                Spacer(6.dp)
                Text("Daily reminder", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                ReminderSection()

                Spacer(6.dp)
                Text("Backup & restore", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                BackupSection()

                Spacer(6.dp)
                Text("Updates", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                var auto by remember { mutableStateOf(app.store.autoUpdate) }
                var checkMsg by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp))
                        .clickable(role = Role.Switch) { auto = !auto; app.store.autoUpdate = auto }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Check for updates when the app opens", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Preview builds update from GitHub. Android asks you to confirm each install.", color = c.muted, fontSize = 13.sp)
                    }
                    HSpacer(12.dp)
                    androidx.compose.material3.Switch(checked = auto, onCheckedChange = { auto = it; app.store.autoUpdate = it },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.box, checkedThumbColor = c.onBox))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallButton("Check now") {
                        checkMsg = "Checking…"
                        scope.launch {
                            val u = com.kithandkeen.qonverse.Updater.check()
                            if (u != null) { app.update = u; app.updateDismissed = false; checkMsg = null }
                            else checkMsg = "You have the latest version."
                        }
                    }
                    HSpacer(12.dp)
                    checkMsg?.let { Text(it, color = c.muted, fontSize = 13.sp) }
                }

                Spacer(6.dp)
                Text("About", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Logo(20)
                    Text("Version ${com.kithandkeen.qonverse.BuildConfig.VERSION_NAME} (preview) · Open source under the Apache License 2.0", color = c.muted, fontSize = 13.sp)
                    Text("Made by Lee Wen Yang (Mike) · Kith + Keen", color = c.muted, fontSize = 13.sp)
                    Text(
                        "github.com/aadvark/Qonverse", color = c.boxText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aadvark/Qonverse"))) } }.padding(vertical = 6.dp),
                    )
                }
                Spacer(24.dp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderSection() {
    val app = LocalApp.current
    val c = Q.c
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(app.store.reminderOn) }
    var hour by remember { mutableIntStateOf(app.store.reminderHour) }
    fun apply(enable: Boolean) {
        on = enable; app.store.reminderOn = enable
        com.kithandkeen.qonverse.ReminderWorker.schedule(ctx, hour)
    }
    val askPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> apply(granted) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Remind me to practise", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("A daily notification with your phrase of the day.", color = c.muted, fontSize = 13.sp)
            }
            androidx.compose.material3.Switch(
                checked = on,
                onCheckedChange = { want ->
                    if (want && android.os.Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else apply(want)
                },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.box, checkedThumbColor = c.onBox),
            )
        }
        if (on) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 9, 12, 18, 21).forEach { h ->
                    val sel = h == hour
                    Text(
                        if (h < 12) "$h am" else if (h == 12) "12 pm" else "${h - 12} pm",
                        color = if (sel) c.onBox else c.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (sel) c.box else c.bg)
                            .border(1.dp, if (sel) c.box else c.line, RoundedCornerShape(999.dp))
                            .clickable { hour = h; app.store.reminderHour = h; com.kithandkeen.qonverse.ReminderWorker.schedule(ctx, h) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }
        Text("Tip: add the “Phrase of the day” widget from your home screen's widget list.", color = c.muted, fontSize = 13.sp)
    }
}
