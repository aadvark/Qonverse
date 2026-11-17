package com.kithandkeen.qonverse.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One-file backup shared by the Android app and the web app (docs/backup-format.md).
 * A backup made on one restores on the other.
 */
@Serializable
data class BackupFile(
    @SerialName("qonverse_backup_version") val version: Int = 1,
    val created: String = "",
    val app: String = "android",
    val packs: List<PackFile> = emptyList(),
    /** Ids of built-in sample packs the user deleted. */
    val hidden: List<String> = emptyList(),
    /** Familiar phrase ids, by pack id. */
    val familiar: Map<String, List<String>> = emptyMap(),
    /** Starred phrases as "packId|phraseId". */
    val starred: List<String> = emptyList(),
    /** Spaced-repetition state: pack id -> phrase id -> [box, due epoch day]. */
    val srs: Map<String, Map<String, List<Long>>> = emptyMap(),
    val settings: BackupSettings = BackupSettings(),
)

@Serializable
data class BackupSettings(
    val theme: String? = null,
    @SerialName("normal_rate") val normalRate: Float? = null,
    @SerialName("slow_rate") val slowRate: Float? = null,
    /** Chosen voice name, by language tag. Voice names differ between devices; unknown ones are ignored. */
    val voices: Map<String, String> = emptyMap(),
)

data class RestoreResult(val packs: Int, val phrasesFamiliar: Int)

object BackupCodec {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true; explicitNulls = false; encodeDefaults = true }

    fun looksLikeBackup(raw: String) = raw.contains("\"qonverse_backup_version\"")

    fun decode(raw: String): BackupFile {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "This isn't a Qonverse backup file." }
        val text = raw.substring(start, end + 1)
        require(looksLikeBackup(text)) { "This isn't a Qonverse backup file." }
        return json.decodeFromString(BackupFile.serializer(), text)
    }

    fun encode(b: BackupFile): String = json.encodeToString(BackupFile.serializer(), b)
}
