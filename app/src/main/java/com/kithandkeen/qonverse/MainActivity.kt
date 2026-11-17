package com.kithandkeen.qonverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kithandkeen.qonverse.data.PackFile
import com.kithandkeen.qonverse.data.Store
import com.kithandkeen.qonverse.ui.AddPackScreen
import com.kithandkeen.qonverse.ui.HomeScreen
import com.kithandkeen.qonverse.ui.PackScreen
import com.kithandkeen.qonverse.ui.PhraseScreen
import com.kithandkeen.qonverse.ui.PracticeScreen
import com.kithandkeen.qonverse.ui.Q
import com.kithandkeen.qonverse.ui.QonverseTheme
import com.kithandkeen.qonverse.ui.SettingsScreen
import com.kithandkeen.qonverse.ui.SearchScreen
import com.kithandkeen.qonverse.ui.ShadowScreen
import com.kithandkeen.qonverse.ui.StarredScreen

/** App-wide state: packs, progress version, theme, and the speech engine. */
class AppState(val store: Store, val speaker: Speaker) {
    var packs by mutableStateOf(store.loadAll())
        private set
    var theme by mutableStateOf(store.themeMode)
        private set
    /** Normal playback speed, changeable from the speed chip on learning and practice screens. */
    var speed by mutableFloatStateOf(store.normalRate)
        private set
    fun changeSpeed(v: Float) { store.normalRate = v; speed = v }

    /** A newer preview version found by the update check, if any. */
    var update by mutableStateOf<UpdateInfo?>(null)
    var updateDismissed by mutableStateOf(false)
    /** Bumped whenever progress changes, so screens re-read it. */
    var progressTick by mutableIntStateOf(0)

    fun reload() { packs = store.loadAll() }
    fun pack(id: String): PackFile? = packs.firstOrNull { it.pack.id == id }
    fun changeTheme(mode: String) { store.themeMode = mode; theme = mode }
    fun setFamiliar(packId: String, phraseId: String, on: Boolean) {
        store.setFamiliar(packId, phraseId, on); progressTick++
    }
    fun familiar(packId: String): Set<String> { progressTick; return store.familiar(packId) }
    fun voiceFor(lang: String) = store.voiceFor(lang)
    fun say(text: String, lang: String, slow: Boolean = false, times: Int = 1) =
        speaker.speak(text, lang, if (slow) store.slowRate else speed, times, store.voiceFor(lang))
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState not provided") }

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this).apply { seedSamples() }
        app = AppState(store, Speaker(this))
        // keep the daily reminder scheduled and the widget fresh
        runCatching { ReminderWorker.schedule(this, store.reminderHour); PhraseWidget.refresh(this) }
        setContent {
            CompositionLocalProvider(LocalApp provides app) {
                QonverseTheme(app.theme) { AppNav() }
            }
        }
    }

    override fun onDestroy() {
        app.speaker.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val app = LocalApp.current
    // Check for a newer preview version every time the app opens.
    LaunchedEffect(Unit) { if (app.store.autoUpdate) app.update = Updater.check() }
    app.update?.let { if (!app.updateDismissed) UpdateDialog(it) { app.updateDismissed = true } }
    Box(Modifier.fillMaxSize().background(Q.c.bg).systemBarsPadding()) {
        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    openStarred = { nav.navigate("starred") },
                    openSearch = { nav.navigate("search") },
                    openPack = { nav.navigate("pack/$it") },
                    openPhrase = { p, s, i -> nav.navigate("phrase/$p/$s/$i") },
                    addPack = { nav.navigate("add") },
                    practice = { nav.navigate("practice/all") },
                    settings = { nav.navigate("settings") },
                )
            }
            composable("add") {
                AddPackScreen(back = { nav.popBackStack() }, saved = { id ->
                    nav.popBackStack(); nav.navigate("pack/$id")
                })
            }
            composable("pack/{id}") { e ->
                val id = e.arguments?.getString("id") ?: ""
                PackScreen(
                    id, back = { nav.popBackStack() },
                    openScenario = { s -> nav.navigate("phrase/$id/$s/0") },
                    practice = { nav.navigate("practice/$id") },
                    shadow = { nav.navigate("shadow/$id") },
                    deleted = { nav.popBackStack() },
                )
            }
            composable("phrase/{p}/{s}/{i}") { e ->
                PhraseScreen(
                    e.arguments?.getString("p") ?: "", e.arguments?.getString("s") ?: "",
                    e.arguments?.getString("i")?.toIntOrNull() ?: 0, close = { nav.popBackStack() },
                )
            }
            composable("practice/{p}") { e ->
                PracticeScreen(e.arguments?.getString("p") ?: "all", close = { nav.popBackStack() })
            }
            composable("settings") { SettingsScreen(back = { nav.popBackStack() }) }
            composable("starred") { StarredScreen(back = { nav.popBackStack() }) { r -> nav.navigate("phrase/${r.pack.pack.id}/${r.scenarioId}/${r.index}") } }
            composable("search") { SearchScreen(back = { nav.popBackStack() }) { r -> nav.navigate("phrase/${r.pack.pack.id}/${r.scenarioId}/${r.index}") } }
            composable("shadow/{p}") { e -> ShadowScreen(e.arguments?.getString("p") ?: "", close = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun UpdateDialog(info: UpdateInfo, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = Q.c
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        containerColor = c.surface,
        title = { Text("Update available", color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Qonverse ${info.versionName} is ready (you have ${BuildConfig.VERSION_NAME}).", color = c.ink)
                if (info.notes.isNotBlank()) Text(info.notes, color = c.muted)
                if (busy) LinearProgressIndicator(progress = { progress }, color = c.box, trackColor = c.track)
                message?.let { Text(it, color = c.boxText) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (!Updater.canInstall(ctx)) {
                    message = "Allow Qonverse to install updates, then come back and tap Update again."
                    Updater.openInstallPermission(ctx)
                    return@TextButton
                }
                busy = true; message = null
                scope.launch {
                    runCatching { Updater.download(ctx, info) { progress = it } }
                        .onSuccess { busy = false; Updater.install(ctx, it) }
                        .onFailure { busy = false; message = "Couldn't download the update. Check your connection and try again." }
                }
            }) { Text(if (busy) "Downloading…" else "Update", color = c.boxText) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Later", color = c.muted) } },
    )
}
