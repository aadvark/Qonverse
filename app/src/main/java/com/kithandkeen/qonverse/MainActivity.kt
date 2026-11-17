package com.kithandkeen.qonverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
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

/** App-wide state: packs, progress version, theme, and the speech engine. */
class AppState(val store: Store, val speaker: Speaker) {
    var packs by mutableStateOf(store.loadAll())
        private set
    var theme by mutableStateOf(store.themeMode)
        private set
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
        speaker.speak(text, lang, if (slow) store.slowRate else store.normalRate, times, store.voiceFor(lang))
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState not provided") }

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this).apply { seedSamples() }
        app = AppState(store, Speaker(this))
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
    Box(Modifier.fillMaxSize().background(Q.c.bg).systemBarsPadding()) {
        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
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
        }
    }
}
