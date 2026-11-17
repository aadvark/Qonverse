package com.kithandkeen.qonverse.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/** Saves packs as JSON files on the phone, and keeps progress and settings in SharedPreferences. */
private const val DAY = 24L * 60 * 60 * 1000
/** Bump when the bundled sample packs are improved, so installed apps refresh them. */
private const val SAMPLES_REV = 2
/** Days until the next review for each box. */
val INTERVALS = listOf(0, 1, 2, 4, 7, 15, 30, 60)

class Store(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true; explicitNulls = false }
    private val dir = File(context.filesDir, "packs").apply { mkdirs() }
    val prefs = context.getSharedPreferences("qonverse", Context.MODE_PRIVATE)

    /** Copies the bundled sample packs on first run, and refreshes them when the app ships improved versions. */
    fun seedSamples() {
        val rev = prefs.getInt("samples_rev", if (prefs.getBoolean("seeded_v1", false)) 1 else 0)
        if (rev >= SAMPLES_REV) return
        val hidden = hidden()
        context.assets.list("packs")?.forEach { name ->
            if (rev > 0 && name.removeSuffix(".json") in hidden) return@forEach
            context.assets.open("packs/$name").use { input ->
                File(dir, name).outputStream().use { input.copyTo(it) }
            }
        }
        prefs.edit().putBoolean("seeded_v1", true).putInt("samples_rev", SAMPLES_REV).apply()
    }

    fun loadAll(): List<PackFile> = dir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { f -> runCatching { json.decodeFromString(PackFile.serializer(), f.readText()) }.getOrNull() }
        ?.sortedBy { it.pack.title.lowercase() }
        ?: emptyList()

    /** Reads what the user pasted. Accepts a bare JSON object or one wrapped in a ```json code block. */
    fun parse(raw: String): Result<PackFile> = runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(!BackupCodec.looksLikeBackup(raw)) { "This is a Qonverse backup, not a pack. Restore it from Settings › Backup & restore." }
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
        prefs.edit().putStringSet("hidden", hidden() - merged.pack.id).apply()
        markChanged()
        return merged
    }

    fun exists(id: String) = File(dir, safeName(id) + ".json").exists()

    fun delete(id: String) {
        File(dir, safeName(id) + ".json").delete()
        val ed = prefs.edit().remove("fam:$id")
        if (id in sampleIds()) ed.putStringSet("hidden", hidden() + id)
        ed.apply()
        markChanged()
    }

    private fun sampleIds(): Set<String> =
        context.assets.list("packs")?.map { it.removeSuffix(".json") }?.toSet() ?: emptySet()

    private fun hidden(): Set<String> = prefs.getStringSet("hidden", emptySet()) ?: emptySet()

    private fun safeName(id: String) = id.lowercase().replace(Regex("[^a-z0-9-]"), "-").take(80).ifBlank { "pack" }

    // ---- progress ----
    fun familiar(packId: String): Set<String> = prefs.getStringSet("fam:$packId", emptySet()) ?: emptySet()

    fun setFamiliar(packId: String, phraseId: String, on: Boolean) {
        val s = familiar(packId).toMutableSet()
        if (on) s += phraseId else s -= phraseId
        prefs.edit().putStringSet("fam:$packId", s).apply()
        markChanged()
    }

    // ---- starred phrases ----
    fun starred(): Set<String> = prefs.getStringSet("star", emptySet()) ?: emptySet()
    fun isStarred(packId: String, phraseId: String) = "$packId|$phraseId" in starred()
    fun setStarred(packId: String, phraseId: String, on: Boolean) {
        val key = "$packId|$phraseId"
        prefs.edit().putStringSet("star", if (on) starred() + key else starred() - key).apply()
        markChanged()
    }

    // ---- spaced repetition (Leitner boxes) ----
    /** phraseId -> (box, due epoch day). Box 0 = needs practice today. */
    fun srs(packId: String): Map<String, Pair<Int, Long>> =
        (prefs.getString("srs:$packId", null) ?: "").split(";").mapNotNull { e ->
            val p = e.split(","); if (p.size == 3) p[0] to (p[1].toInt() to p[2].toLong()) else null
        }.toMap()

    private fun saveSrs(packId: String, m: Map<String, Pair<Int, Long>>) =
        prefs.edit().putString("srs:$packId", m.entries.joinToString(";") { "${it.key},${it.value.first},${it.value.second}" }).apply()

    /** Records an answer: right moves the phrase up a box (seen again later), wrong sends it back to box 0. */
    fun recordAnswer(packId: String, phraseId: String, correct: Boolean) {
        val m = srs(packId).toMutableMap()
        val box = m[phraseId]?.first ?: 0
        val nb = if (correct) (box + 1).coerceAtMost(INTERVALS.size - 1) else 0
        m[phraseId] = nb to (today() + INTERVALS[nb])
        saveSrs(packId, m)
        setFamiliar(packId, phraseId, correct)
    }

    /** "Familiar" on the phrase card: schedule it for tomorrow if it was new or due. */
    fun markSeen(packId: String, phraseId: String, familiar: Boolean) {
        val m = srs(packId).toMutableMap()
        val cur = m[phraseId]
        m[phraseId] = if (familiar) maxOf(cur?.first ?: 0, 1) to maxOf(cur?.second ?: 0, today() + 1) else 0 to today()
        saveSrs(packId, m)
        setFamiliar(packId, phraseId, familiar)
    }

    fun today(): Long = java.time.LocalDate.now().toEpochDay()

    // ---- backup & restore ----
    fun exportBackup(): String {
        val packs = loadAll()
        val fam = prefs.all.keys.filter { it.startsWith("fam:") }
            .associate { it.removePrefix("fam:") to familiar(it.removePrefix("fam:")).sorted() }
            .filterValues { it.isNotEmpty() }
        val voices = prefs.all.filterKeys { it.startsWith("voice:") }.map { (k, v) -> k.removePrefix("voice:") to v.toString() }.toMap()
        val b = BackupFile(
            created = java.time.OffsetDateTime.now().withNano(0).toString(), app = "android",
            packs = packs, hidden = hidden().sorted(), familiar = fam,
            starred = starred().sorted(),
            srs = packs.associate { p -> p.pack.id to srs(p.pack.id).mapValues { listOf(it.value.first.toLong(), it.value.second) } }.filterValues { it.isNotEmpty() },
            settings = BackupSettings(themeMode, normalRate, slowRate, voices),
        )
        return BackupCodec.encode(b)
    }

    /** Merges a backup into what's on this phone: packs are added or updated, progress is combined, settings are applied. */
    fun restoreBackup(raw: String): RestoreResult {
        val b = BackupCodec.decode(raw)
        b.packs.forEach { save(it) }
        b.hidden.filter { h -> b.packs.none { it.pack.id == h } }.forEach { if (exists(it)) delete(it) }
        var n = 0
        b.familiar.forEach { (packId, ids) ->
            val merged = familiar(packId) + ids
            n += ids.size
            prefs.edit().putStringSet("fam:$packId", merged).apply()
        }
        prefs.edit().putStringSet("star", starred() + b.starred).apply()
        b.srs.forEach { (packId, entries) ->
            val m = srs(packId).toMutableMap()
            entries.forEach { (id, v) -> if (v.size == 2 && v[0].toInt() >= (m[id]?.first ?: -1)) m[id] = v[0].toInt() to v[1] }
            saveSrs(packId, m)
        }
        b.settings.theme?.let { themeMode = it }
        b.settings.normalRate?.let { normalRate = it.coerceIn(0.5f, 1.5f) }
        b.settings.slowRate?.let { slowRate = it.coerceIn(0.3f, 1f) }
        b.settings.voices.forEach { (lang, name) -> setVoiceFor(lang, name) }
        prefs.edit().putBoolean("unsaved_changes", false).apply()
        return RestoreResult(b.packs.size, n)
    }

    private fun markChanged() {
        if (!prefs.getBoolean("unsaved_changes", false))
            prefs.edit().putBoolean("unsaved_changes", true).putLong("first_unsaved_at", System.currentTimeMillis()).apply()
    }

    fun markBackedUp() = prefs.edit().putBoolean("unsaved_changes", false).putLong("last_backup_at", System.currentTimeMillis()).apply()

    val lastBackupAt: Long get() = prefs.getLong("last_backup_at", 0L)

    fun snoozeBackupReminder() = prefs.edit().putLong("backup_snooze_until", System.currentTimeMillis() + 7 * DAY).apply()

    /** Remind after a week of changes that haven't been backed up. */
    fun shouldRemindBackup(): Boolean {
        val now = System.currentTimeMillis()
        return prefs.getBoolean("unsaved_changes", false) &&
            now - prefs.getLong("first_unsaved_at", now) > 7 * DAY &&
            now > prefs.getLong("backup_snooze_until", 0L)
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

    var reminderOn: Boolean
        get() = prefs.getBoolean("reminder_on", false)
        set(v) = prefs.edit().putBoolean("reminder_on", v).apply()

    var reminderHour: Int
        get() = prefs.getInt("reminder_hour", 9)
        set(v) = prefs.edit().putInt("reminder_hour", v).apply()

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
