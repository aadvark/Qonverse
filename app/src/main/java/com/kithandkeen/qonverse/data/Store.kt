package com.kithandkeen.qonverse.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/** Saves packs as JSON files on the phone, and keeps progress and settings in SharedPreferences. */
class Store(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true; explicitNulls = false }
    private val dir = File(context.filesDir, "packs").apply { mkdirs() }
    val prefs = context.getSharedPreferences("qonverse", Context.MODE_PRIVATE)

    /** Copies the bundled sample packs the first time the app runs. */
    fun seedSamples() {
        if (prefs.getBoolean("seeded_v1", false)) return
        context.assets.list("packs")?.forEach { name ->
            context.assets.open("packs/$name").use { input ->
                File(dir, name).outputStream().use { input.copyTo(it) }
            }
        }
        prefs.edit().putBoolean("seeded_v1", true).apply()
    }

    fun loadAll(): List<PackFile> = dir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { f -> runCatching { json.decodeFromString(PackFile.serializer(), f.readText()) }.getOrNull() }
        ?.sortedBy { it.pack.title.lowercase() }
        ?: emptyList()

    /** Reads what the user pasted. Accepts a bare JSON object or one wrapped in a ```json code block. */
    fun parse(raw: String): Result<PackFile> = runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "No pack found. Paste the whole code block your AI gave you." }
        val pack = json.decodeFromString(PackFile.serializer(), raw.substring(start, end + 1))
        val problems = check(pack)
        require(problems.isEmpty()) { problems.joinToString("\n") }
        pack
    }

    private fun check(p: PackFile): List<String> {
        val out = mutableListOf<String>()
        if (p.pack.id.isBlank()) out += "The pack has no id."
        if (p.pack.targetLanguage.isBlank()) out += "The pack doesn't say which language it teaches."
        if (p.scenarios.isEmpty()) out += "The pack has no scenarios."
        p.scenarios.forEach { s ->
            if (s.phrases.isEmpty()) out += "Scenario \"${s.title}\" has no phrases."
            s.phrases.forEach { ph -> if (ph.text.isBlank()) out += "A phrase in \"${s.title}\" is empty." }
        }
        return out
    }

    /** Saves a pack. If a pack with the same id exists (a pack pasted in parts), its scenarios are merged. */
    fun save(incoming: PackFile): PackFile {
        val file = File(dir, safeName(incoming.pack.id) + ".json")
        val merged = if (file.exists()) {
            val old = runCatching { json.decodeFromString(PackFile.serializer(), file.readText()) }.getOrNull()
            if (old == null) incoming else {
                val byId = LinkedHashMap<String, Scenario>()
                old.scenarios.forEach { byId[it.id] = it }
                incoming.scenarios.forEach { byId[it.id] = it }
                incoming.copy(scenarios = byId.values.toList())
            }
        } else incoming
        file.writeText(json.encodeToString(PackFile.serializer(), merged))
        return merged
    }

    fun exists(id: String) = File(dir, safeName(id) + ".json").exists()

    fun delete(id: String) {
        File(dir, safeName(id) + ".json").delete()
        prefs.edit().remove("fam:$id").apply()
    }

    private fun safeName(id: String) = id.lowercase().replace(Regex("[^a-z0-9-]"), "-").take(80).ifBlank { "pack" }

    // ---- progress ----
    fun familiar(packId: String): Set<String> = prefs.getStringSet("fam:$packId", emptySet()) ?: emptySet()

    fun setFamiliar(packId: String, phraseId: String, on: Boolean) {
        val s = familiar(packId).toMutableSet()
        if (on) s += phraseId else s -= phraseId
        prefs.edit().putStringSet("fam:$packId", s).apply()
    }

    // ---- settings ----
    var themeMode: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(v) = prefs.edit().putString("theme", v).apply()

    var normalRate: Float
        get() = prefs.getFloat("rate_normal", 1.0f)
        set(v) = prefs.edit().putFloat("rate_normal", v).apply()

    var slowRate: Float
        get() = prefs.getFloat("rate_slow", 0.6f)
        set(v) = prefs.edit().putFloat("rate_slow", v).apply()

    var autoUpdate: Boolean
        get() = prefs.getBoolean("auto_update", true)
        set(v) = prefs.edit().putBoolean("auto_update", v).apply()

    fun voiceFor(lang: String): String? = prefs.getString("voice:$lang", null)
    fun setVoiceFor(lang: String, name: String) = prefs.edit().putString("voice:$lang", name).apply()

    fun saveLast(packId: String, scenarioId: String, index: Int) =
        prefs.edit().putString("last", "$packId|$scenarioId|$index").apply()

    fun last(): Triple<String, String, Int>? = prefs.getString("last", null)?.split("|")?.let {
        if (it.size == 3) Triple(it[0], it[1], it[2].toIntOrNull() ?: 0) else null
    }
}
