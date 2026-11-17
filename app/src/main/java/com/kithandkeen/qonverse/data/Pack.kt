package com.kithandkeen.qonverse.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Qonverse scenario pack, format v1.x. See docs/pack-format.md in the repository. */
@Serializable
data class PackFile(
    @SerialName("qonverse_pack_version") val version: String = "1.2",
    val pack: PackInfo,
    val scenarios: List<Scenario> = emptyList(),
)

@Serializable
data class PackInfo(
    val id: String,
    val title: String,
    val description: String = "",
    val industry: String = "",
    val role: String = "",
    val audience: String = "",
    @SerialName("learner_language") val learnerLanguage: String = "en",
    @SerialName("target_language") val targetLanguage: String,
    @SerialName("romanization_system") val romanizationSystem: String = "",
    val politeness: String = "",
    val level: String = "",
    @SerialName("generated_by") val generatedBy: String = "",
    val created: String = "",
)

@Serializable
data class Scenario(
    val id: String,
    val title: String,
    @SerialName("title_target") val titleTarget: String = "",
    @SerialName("title_romanization") val titleRomanization: String = "",
    val context: String = "",
    val phrases: List<Phrase> = emptyList(),
)

@Serializable
data class Line(
    val text: String,
    val romanization: String = "",
    val meaning: String = "",
)

@Serializable
data class DialogueLine(
    val speaker: String = "",
    val text: String,
    val romanization: String = "",
    val meaning: String = "",
    @SerialName("uses_phrase") val usesPhrase: Boolean = false,
) {
    val isYou: Boolean get() = speaker.equals("you", ignoreCase = true)
}

@Serializable
data class Example(
    val setting: String = "",
    val dialogue: List<DialogueLine> = emptyList(),
)

@Serializable
data class Phrase(
    val id: String,
    val text: String,
    val romanization: String = "",
    val meaning: String = "",
    val literal: String = "",
    val pattern: String = "",
    val variants: List<Line> = emptyList(),
    val politeness: String = "",
    @SerialName("in_a_sentence") val inASentence: Line? = null,
    @SerialName("when_to_use") val whenToUse: String = "",
    val notes: String = "",
    val example: Example = Example(),
    @SerialName("likely_responses") val likelyResponses: List<Line> = emptyList(),
)
