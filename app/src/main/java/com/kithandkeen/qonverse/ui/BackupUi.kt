package com.kithandkeen.qonverse.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kithandkeen.qonverse.LocalApp
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/** Opens the system "Save" screen (Drive, Downloads, etc.) and writes a backup there. */
@Composable
fun rememberBackupAction(onResult: (String) -> Unit): () -> Unit {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(app.store.exportBackup().toByteArray()) }
            app.store.markBackedUp(); app.progressTick++
        }.onSuccess { onResult("Backup saved. Keep it somewhere safe, like Google Drive.") }
            .onFailure { onResult("Couldn't save the backup. Try another location.") }
    }
    return { launcher.launch("qonverse-backup-${LocalDate.now()}.json") }
}

/** Opens the file picker and merges the chosen backup into this phone. */
@Composable
fun rememberRestoreAction(onResult: (String) -> Unit): () -> Unit {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val raw = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            app.store.restoreBackup(raw)
        }.onSuccess { r ->
            app.reload(); app.changeTheme(app.store.themeMode); app.progressTick++
            onResult("Restored ${r.packs} packs and progress for ${r.phrasesFamiliar} phrases.")
        }.onFailure { e -> onResult(e.message ?: "That file couldn't be restored.") }
    }
    return { launcher.launch(arrayOf("application/json", "text/*", "*/*")) }
}

fun lastBackupText(at: Long): String =
    if (at == 0L) "Not backed up yet" else "Last backup: " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))

/** Gentle reminder on Home after a week of changes that haven't been backed up. */
@Composable
fun BackupReminder(onDone: () -> Unit) {
    val app = LocalApp.current
    val c = Q.c
    val backup = rememberBackupAction { onDone() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.boxSoft).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Back up your packs and progress", color = c.boxDeep, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("Save one small file so you can restore everything if you change phones.", color = c.ink, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("Back up now", backup)
            SmallButton("Not now") { app.store.snoozeBackupReminder(); onDone() }
        }
    }
}

@Composable
fun BackupSection() {
    val app = LocalApp.current
    val c = Q.c
    var msg by remember { mutableStateOf<String?>(null) }
    val backup = rememberBackupAction { msg = it }
    val restore = rememberRestoreAction { msg = it }
    app.progressTick
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Saves your packs, progress and settings in one file. Restore it on a new phone — Android or the web app on iPhone.", color = c.muted, fontSize = 13.sp)
        Text(lastBackupText(app.store.lastBackupAt), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("Back up now", backup)
            SmallButton("Restore", restore)
        }
        msg?.let { Text(it, color = c.boxText, fontSize = 13.sp) }
    }
}
