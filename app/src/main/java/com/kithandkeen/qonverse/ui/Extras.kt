package com.kithandkeen.qonverse.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kithandkeen.qonverse.AppState
import com.kithandkeen.qonverse.LocalApp
import com.kithandkeen.qonverse.data.PackFile
import com.kithandkeen.qonverse.data.Phrase
import kotlinx.coroutines.delay

/** A phrase with where it lives, for search, starred and the widget. */
data class PhraseRef(val pack: PackFile, val scenarioId: String, val index: Int, val phrase: Phrase)

fun allPhrases(packs: List<PackFile>): List<PhraseRef> =
    packs.flatMap { p -> p.scenarios.flatMap { s -> s.phrases.mapIndexed { i, ph -> PhraseRef(p, s.id, i, ph) } } }

// ================================================================ 1. show to guest

/** Full-screen, large-print card to turn towards the guest. */
@Composable
fun ShowGuestDialog(ph: Phrase, lang: String, close: () -> Unit) {
    val app = LocalApp.current
    val c = Q.c
    val line = ph.inASentence ?: com.kithandkeen.qonverse.data.Line(ph.text, ph.romanization, ph.meaning)
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(c.surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Show this to your guest", color = c.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("Close", color = c.boxText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = close).padding(12.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(line.text, color = c.ink, fontSize = 40.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Box(Modifier.size(width = 60.dp, height = 3.dp).background(c.box))
                if (line.romanization.isNotBlank()) Text(line.romanization, color = c.muted, fontSize = 16.sp, textAlign = TextAlign.Center)
                Text(line.meaning, color = c.muted, fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            SpeakerButton({ app.say(line.text, lang) }, 84.dp, "Play it for the guest", big = true)
            Spacer(12.dp)
        }
    }
}

// ================================================================ 2. starred + search

@Composable
fun PhraseList(items: List<PhraseRef>, open: (PhraseRef) -> Unit, empty: String) {
    val app = LocalApp.current
    val c = Q.c
    if (items.isEmpty()) { Text(empty, color = c.muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp)); return }
    items.forEach { r ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp))
                .clickable { open(r) }.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Tri(r.phrase.text, r.phrase.romanization, r.phrase.meaning, 17)
                Text(r.pack.pack.title, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            HSpacer(8.dp)
            SpeakerButton({ app.say(r.phrase.text, r.pack.pack.targetLanguage) }, 44.dp, "Play ${r.phrase.meaning}")
        }
        Spacer(10.dp)
    }
}

@Composable
fun StarredScreen(back: () -> Unit, open: (PhraseRef) -> Unit) {
    val app = LocalApp.current
    app.progressTick
    val stars = app.store.starred()
    val items = allPhrases(app.packs).filter { "${it.pack.pack.id}|${it.phrase.id}" in stars }
    Box(Modifier.fillMaxSize()) {
        CubeWallpaper()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TopBar("Starred phrases", back) { SpeedChip() }
            Column(Modifier.padding(horizontal = 16.dp)) {
                PhraseList(items, open, "No starred phrases yet. Tap the ☆ on any phrase to keep it here for quick access during your shift.")
            }
        }
    }
}

@Composable
fun SearchScreen(back: () -> Unit, open: (PhraseRef) -> Unit) {
    val app = LocalApp.current
    val c = Q.c
    var q by remember { mutableStateOf("") }
    val all = remember(app.packs) { allPhrases(app.packs) }
    val needle = q.trim().lowercase()
    val results = if (needle.length < 1) emptyList() else all.filter { r ->
        val p = r.phrase
        listOf(p.text, p.romanization, p.meaning, p.inASentence?.text, p.inASentence?.meaning, p.whenToUse)
            .any { it?.lowercase()?.contains(needle) == true }
    }.take(60)
    Box(Modifier.fillMaxSize()) {
        CubeWallpaper()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TopBar("Search phrases", back)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Try “bill”, “passport” or お会計", color = c.muted) },
                    label = { Text("Search in English or the other language") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.box, unfocusedBorderColor = c.line,
                        focusedContainerColor = c.surface, unfocusedContainerColor = c.surface),
                )
                if (needle.isNotEmpty()) PhraseList(results, open, "No phrases match “$q”.")
            }
        }
    }
}

// ================================================================ 3. pronunciation check

private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

private fun similarity(a: String, b: String): Float {
    if (a.isEmpty() || b.isEmpty()) return 0f
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
    }
    return 1f - d[a.length][b.length].toFloat() / maxOf(a.length, b.length)
}

/** Say the phrase; the phone's speech recognition checks whether it understood you. */
@Composable
fun PronunciationCheck(ph: Phrase, lang: String) {
    val c = Q.c
    val ctx = LocalContext.current
    val available = remember { SpeechRecognizer.isRecognitionAvailable(ctx) }
    var result by remember(ph.id) { mutableStateOf<Pair<Boolean, String>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        val target = normalize(ph.text)
        val best = heard.maxByOrNull { h -> val n = normalize(h); if (n.contains(target)) 1f else similarity(n, target) }
        val n = normalize(best ?: "")
        val ok = n.contains(target) || similarity(n, target) >= 0.75f
        result = ok to (best ?: "")
    }
    if (!available) return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) {
                runCatching {
                    launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: ${ph.text}"))
                }.onFailure { result = false to "" }
            }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.box), contentAlignment = Alignment.Center) { MicIcon(c.onBox) }
        HSpacer(12.dp)
        Column(Modifier.weight(1f)) {
            when {
                result == null -> {
                    Text("Check my pronunciation", color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Say the phrase — your phone tells you if it understood.", color = c.muted, fontSize = 13.sp)
                }
                result!!.first -> {
                    Text("✓ Understood!", color = c.boxText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("It heard: ${result!!.second}", color = c.muted, fontSize = 13.sp)
                }
                else -> {
                    Text("Not quite — try again", color = c.danger, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(if (result!!.second.isBlank()) "It didn't catch that. Listen once more, then try." else "It heard: ${result!!.second}", color = c.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun MicIcon(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val st = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(w * 0.36f, w * 0.1f), size = androidx.compose.ui.geometry.Size(w * 0.28f, w * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f), style = st)
        drawArc(color, 0f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(w * 0.22f, w * 0.28f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, w * 0.5f), style = st)
        drawLine(color, androidx.compose.ui.geometry.Offset(w / 2, w * 0.78f), androidx.compose.ui.geometry.Offset(w / 2, w * 0.92f), st.width)
    }
}

// ================================================================ 5. hands-free shadow mode

/** Plays each phrase, leaves a gap for you to repeat it, then moves on. Keeps the screen on. */
@Composable
fun ShadowScreen(packId: String, close: () -> Unit) {
    val app = LocalApp.current
    val c = Q.c
    val pack = app.pack(packId) ?: run { TopBar("Not found", close); return }
    val lang = pack.pack.targetLanguage
    val items = remember(packId) { allPhrases(listOf(pack)) }
    var index by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    var stage by remember { mutableStateOf("Listen") }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false; app.speaker.stop() } }
    LaunchedEffect(running, index) {
        if (!running) { app.speaker.stop(); return@LaunchedEffect }
        val ph = items[index].phrase
        repeat(2) {
            stage = "Listen"
            app.speaker.speakAndWait(ph.text, lang, app.speed, app.voiceFor(lang))
            stage = "Your turn — say it"
            delay((1600L + ph.text.length * 260L / app.speed).toLong().coerceAtMost(7000))
        }
        if (index < items.size - 1) index++ else { running = false; stage = "Finished — great work!" }
    }
    val ph = items[index].phrase
    Box(Modifier.fillMaxSize()) {
        CubeWallpaper()
        Column(Modifier.fillMaxSize()) {
            TopBar("Shadow mode", close) {
                SpeedChip()
                Text("${index + 1}/${items.size}", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            }
            ProgressBar((index + 1) / items.size.toFloat(), Modifier.padding(horizontal = 16.dp), 4.dp)
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Chip(stage, if (stage.startsWith("Your")) c.audio else c.boxSoft, if (stage.startsWith("Your")) c.onAudio else c.boxText)
                Text(ph.text, color = c.ink, fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (ph.romanization.isNotBlank()) Text(ph.romanization, color = c.muted, fontSize = 17.sp, textAlign = TextAlign.Center)
                Text(ph.meaning, color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("Each phrase plays twice, with a pause for you to repeat it. Works well with earphones.", color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("‹ Back", Modifier.weight(1f), color = c.ink) { if (index > 0) index-- }
                PrimaryButton(if (running) "Pause" else "Play", Modifier.weight(1.3f)) {
                    if (!running && index == items.size - 1 && stage.startsWith("Finished")) index = 0
                    running = !running
                }
                SecondaryButton("Next ›", Modifier.weight(1f), color = c.ink) { if (index < items.size - 1) index++ }
            }
        }
    }
}

@Suppress("unused")
private fun AppState.touch() = progressTick
