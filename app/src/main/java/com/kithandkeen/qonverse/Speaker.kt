package com.kithandkeen.qonverse

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Speaks phrases with the phone's own text-to-speech engine (usually Speech Services by Google).
 * Nothing is bundled or recorded: voices stay inside the phone's speech app.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    var ready by mutableStateOf(false)
        private set

    private val waiting = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Unit>>()

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { id?.let { waiting.remove(it)?.complete(Unit) } }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { id?.let { waiting.remove(it)?.complete(Unit) } }
            override fun onStop(id: String?, interrupted: Boolean) { id?.let { waiting.remove(it)?.complete(Unit) } }
        })
    }

    /** Speaks and suspends until the phrase has finished (used by hands-free shadow mode). */
    suspend fun speakAndWait(text: String, lang: String, rate: Float, voiceName: String?) {
        if (!ready || text.isBlank()) return
        val id = "w" + System.nanoTime()
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        waiting[id] = done
        val voice = voiceName?.let { n -> runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == n } }
        if (voice != null) tts.voice = voice else tts.language = Locale.forLanguageTag(lang)
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        kotlinx.coroutines.withTimeoutOrNull(20_000) { done.await() }
    }

    /** True if this phone can speak the language now (voice data installed). */
    fun canSpeak(lang: String): Boolean {
        if (!ready) return true
        val r = tts.isLanguageAvailable(Locale.forLanguageTag(lang))
        return r >= TextToSpeech.LANG_AVAILABLE
    }

    /** Up to four voices for a language, offline voices first. */
    fun voicesFor(lang: String): List<Voice> {
        if (!ready) return emptyList()
        val want = Locale.forLanguageTag(lang)
        val all = runCatching { tts.voices }.getOrNull() ?: return emptyList()
        return all.filter { v ->
            v.locale.language == want.language &&
                (want.country.isBlank() || v.locale.country.isBlank() || v.locale.country == want.country)
        }.sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { -it.quality }, { it.name })).take(4)
    }

    fun speak(text: String, lang: String, rate: Float = 1f, times: Int = 1, voiceName: String? = null) {
        if (!ready || text.isBlank()) return
        val locale = Locale.forLanguageTag(lang)
        val voice = voiceName?.let { n -> runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == n } }
        if (voice != null) tts.voice = voice else tts.language = locale
        tts.setSpeechRate(rate)
        repeat(times) { i ->
            tts.speak(text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "q$i")
        }
    }

    fun speakAll(lines: List<String>, lang: String, rate: Float, voiceName: String?) {
        if (lines.isEmpty()) return
        speak(lines.first(), lang, rate, 1, voiceName)
        lines.drop(1).forEachIndexed { i, l -> tts.speak(l, TextToSpeech.QUEUE_ADD, null, "a$i") }
    }

    fun stop() = tts.stop()

    fun shutdown() = tts.shutdown()

    /** Opens the phone's screen for downloading voice data. */
    fun installVoicesIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
