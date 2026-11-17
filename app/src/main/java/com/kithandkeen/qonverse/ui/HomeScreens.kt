package com.kithandkeen.qonverse.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kithandkeen.qonverse.LocalApp
import com.kithandkeen.qonverse.data.PackFile

private val LANG_NAMES = mapOf(
    "ja" to "日本語", "zh" to "中文", "ko" to "한국어", "th" to "ไทย", "vi" to "Tiếng Việt", "yue" to "粵語",
    "ms" to "Melayu", "id" to "Indonesia", "tl" to "Tagalog", "hi" to "हिन्दी", "ta" to "தமிழ்", "km" to "ខ្មែរ",
    "es" to "Español", "fr" to "Français", "de" to "Deutsch", "en" to "English",
)

fun langBadge(tag: String): String = LANG_NAMES[tag.substringBefore('-').lowercase()] ?: tag.substringBefore('-').uppercase()

fun langEnglish(tag: String): String =
    java.util.Locale.forLanguageTag(tag).getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { tag }

// ================================================================ HOME

@Composable
fun HomeScreen(
    openPack: (String) -> Unit,
    openPhrase: (String, String, Int) -> Unit,
    addPack: () -> Unit,
    practice: () -> Unit,
    settings: () -> Unit,
) {
    val app = LocalApp.current
    val c = Q.c
    val packs = app.packs
    val due = packs.sumOf { p -> p.scenarios.sumOf { it.phrases.size } - app.familiar(p.pack.id).size }.coerceAtLeast(0)
    val last = app.store.last()

    Box(Modifier.fillMaxSize()) {
        CubeWallpaper()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Logo(24) }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape)
                        .clickable(role = Role.Button, onClick = settings).semantics { contentDescription = "Settings" },
                    contentAlignment = Alignment.Center,
                ) { Text("⚙", color = c.ink, fontSize = 18.sp) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ready for today's phrases?", color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Short phrases for your job, with sound.", color = c.muted, fontSize = 15.sp)
            }
            // review card
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.box).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("TODAY'S REVIEW", color = c.onBox.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (due == 0) "All familiar" else "$due phrases", color = c.onBox, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Practise as a chat with a guest", color = c.onBox.copy(alpha = 0.9f), fontSize = 14.sp)
                    }
                    Text("話", color = c.orange, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                        .clickable(enabled = packs.isNotEmpty(), role = Role.Button, onClick = practice),
                    contentAlignment = Alignment.Center,
                ) { Text("Start review", color = c.boxText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            }
            if (last != null) {
                val lp = app.pack(last.first)
                val ls = lp?.scenarios?.firstOrNull { it.id == last.second }
                if (lp != null && ls != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
                            .border(1.dp, c.line, RoundedCornerShape(16.dp))
                            .clickable { openPhrase(lp.pack.id, ls.id, last.third) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.audio), contentAlignment = Alignment.Center) {
                            PlayIcon(18.dp, c.onAudio)
                        }
                        HSpacer(14.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Continue learning", color = c.muted, fontSize = 13.sp)
                            Text("${ls.title} · Phrase ${last.third + 1} of ${ls.phrases.size}", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("›", color = c.muted, fontSize = 22.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("My packs", color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "+ Add pack", color = c.boxText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = addPack).padding(10.dp),
                )
            }
            if (packs.isEmpty()) {
                Text("No packs yet. Tap “Add pack” to make one with your AI.", color = c.muted, fontSize = 15.sp)
            }
            packs.forEach { p -> PackRow(p) { openPack(p.pack.id) } }
            Spacer(24.dp)
        }
    }
}

@Composable
private fun PackRow(p: PackFile, onClick: () -> Unit) {
    val app = LocalApp.current
    val c = Q.c
    val total = p.scenarios.sumOf { it.phrases.size }
    val fam = app.familiar(p.pack.id).size
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(c.boxSoft), contentAlignment = Alignment.Center) {
            Text(langBadge(p.pack.targetLanguage), color = c.boxText, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        }
        HSpacer(12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.pack.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            ProgressBar(if (total == 0) 0f else fam / total.toFloat())
            Text("$fam of $total familiar · ${p.scenarios.size} scenarios", color = c.muted, fontSize = 12.sp)
        }
    }
}

// ================================================================ ADD PACK

@Composable
fun AddPackScreen(back: () -> Unit, saved: (String) -> Unit) {
    val app = LocalApp.current
    val c = Q.c
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var raw by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val result = remember(raw) { if (raw.isBlank()) null else app.store.parse(raw) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) raw = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: "" }.getOrDefault("")
    }

    Box(Modifier.fillMaxSize()) {
        CubeWallpaper()
        Column(Modifier.fillMaxSize()) {
            TopBar("Add a pack", back)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Make a pack with your AI", color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Works with Claude, ChatGPT, Gemini or any AI chat app. No account or AI key needed in Qonverse.", color = c.muted, fontSize = 15.sp)
                Step(1, "Copy the Qonverse prompt") {
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (copied) c.box else c.surface)
                            .border(1.dp, c.box, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) {
                                val prompt = ctx.assets.open("pack-generator-prompt.md").bufferedReader().readText()
                                clipboard.setText(AnnotatedString(prompt.substringAfter("---\n", prompt)))
                                copied = true
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) { Text(if (copied) "Copied — now open your AI" else "Copy prompt", color = if (copied) c.onBox else c.boxText, fontWeight = FontWeight.SemiBold) }
                }
                Step(2, "Paste it into your AI chat") {
                    Text("It asks about your job, language and situations, then gives you a block of code.", color = c.muted, fontSize = 14.sp)
                }
                Step(3, "Paste the pack here") {
                    OutlinedTextField(
                        value = raw, onValueChange = { raw = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = c.ink),
                        placeholder = { Text("{ \"qonverse_pack_version\": … }", color = c.muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.box, unfocusedBorderColor = c.line,
                            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface),
                        label = { Text("Pack from your AI") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallButton("Paste from clipboard") { raw = clipboard.getText()?.text ?: raw }
                        SmallButton("Import file") { picker.launch(arrayOf("application/json", "text/*", "*/*")) }
                    }
                }
                result?.let { r ->
                    val box = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    r.onSuccess { p ->
                        val n = p.scenarios.sumOf { it.phrases.size }
                        val merging = app.store.exists(p.pack.id)
                        Column(box.background(c.boxSoft).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("✓ Pack looks good", color = c.boxDeep, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${p.pack.title}\n${p.scenarios.size} scenarios · $n phrases · ${langEnglish(p.pack.targetLanguage)}", color = c.ink, fontSize = 14.sp)
                            Text(
                                if (merging) "You already have this pack — these scenarios will be added to it." else "Got it in parts? Save this, then paste the next part and we'll join them.",
                                color = c.muted, fontSize = 13.sp,
                            )
                        }
                    }
                    r.onFailure { e ->
                        Column(box.background(c.dangerSoft).padding(14.dp)) {
                            Text("This doesn't look like a complete pack", color = c.dangerText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(e.message ?: "Check that you copied the whole code block.", color = c.dangerText, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(8.dp)
            }
            PrimaryButton("Save pack", Modifier.fillMaxWidth().padding(16.dp), enabled = result?.isSuccess == true) {
                result?.getOrNull()?.let { val s = app.store.save(it); app.reload(); saved(s.pack.id) }
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, body: @Composable () -> Unit) {
    val c = Q.c
    Row {
        Box(Modifier.size(32.dp).clip(CircleShape).background(c.box), contentAlignment = Alignment.Center) {
            Text("$n", color = c.onBox, fontWeight = FontWeight.Bold)
        }
        HSpacer(14.dp)
        Column(Modifier.weight(1f).padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            body()
        }
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit) {
    val c = Q.c
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
    ) { Text(text, color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

// ================================================================ PACK

@Composable
fun PackScreen(id: String, back: () -> Unit, openScenario: (String) -> Unit, practice: () -> Unit, deleted: () -> Unit) {
    val app = LocalApp.current
    val c = Q.c
    val p = app.pack(id) ?: run { TopBar("Pack not found", back); return }
    val fam = app.familiar(id)
    val total = p.scenarios.sumOf { it.phrases.size }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        CubeWallpaper()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TopBar("", back) {
                Text(
                    if (confirmDelete) "Tap again to delete" else "Delete", color = c.danger, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                        if (confirmDelete) { app.store.delete(id); app.reload(); deleted() } else confirmDelete = true
                    }.padding(10.dp),
                )
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(c.box), contentAlignment = Alignment.Center) {
                        Text(langBadge(p.pack.targetLanguage), color = c.onBox, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    }
                    HSpacer(14.dp)
                    Column {
                        Text(p.pack.title, color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(listOf(langEnglish(p.pack.targetLanguage), p.pack.politeness.replaceFirstChar { it.uppercase() }, "$total phrases").filter { it.isNotBlank() }.joinToString(" · "), color = c.muted, fontSize = 14.sp)
                    }
                }
                ProgressBar(if (total == 0) 0f else fam.size / total.toFloat(), height = 8.dp)
                Text("${fam.size} of $total familiar", color = c.muted, fontSize = 13.sp)
                if (!app.speaker.canSpeak(p.pack.targetLanguage)) {
                    val ctx = LocalContext.current
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.boxSoft)
                            .clickable { runCatching { ctx.startActivity(app.speaker.installVoicesIntent()) } }.padding(14.dp),
                    ) {
                        Text("No ${langEnglish(p.pack.targetLanguage)} voice on this phone yet", color = c.boxDeep, fontWeight = FontWeight.Bold)
                        Text("Tap to download it free from your phone's speech settings.", color = c.ink, fontSize = 13.sp)
                    }
                }
                PrimaryButton("Practise this pack", Modifier.fillMaxWidth(), onClick = practice)
            }
            Spacer(16.dp)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(c.surface).padding(16.dp),
            ) {
                Text("Scenarios", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(4.dp, 4.dp, 4.dp, 8.dp))
                p.scenarios.forEachIndexed { i, s ->
                    val done = s.phrases.count { it.id in fam }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { openScenario(s.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val complete = done == s.phrases.size && done > 0
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (complete) c.box else c.boxSoft),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (complete) "✓" else "${i + 1}", color = if (complete) c.onBox else c.boxText, fontWeight = FontWeight.Bold) }
                        HSpacer(12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            val sub = listOf(s.titleTarget, s.titleRomanization).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 13.sp)
                        }
                        Text("$done/${s.phrases.size}", color = c.muted, fontSize = 13.sp)
                        HSpacer(6.dp)
                        Text("›", color = c.muted, fontSize = 20.sp)
                    }
                }
                Spacer(24.dp)
            }
        }
    }
}

@Suppress("unused")
private fun openUrl(ctx: android.content.Context, url: String) =
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
