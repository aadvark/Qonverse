# Qonverse pack format (v1.2)

A pack is one JSON file. The full machine-readable definition is [`schema/pack.schema.json`](../schema/pack.schema.json), and a complete example is [`packs/hotel-front-office-ja.json`](../packs/hotel-front-office-ja.json).

```
pack                       about the pack
  id, title, description
  target_language          BCP-47 tag the phone's speech engine understands, e.g. ja-JP, zh-CN, th-TH
  learner_language         language of meanings and notes, e.g. en
  romanization_system      e.g. Hepburn, Hanyu Pinyin, Revised Romanization
  politeness, level, industry, role, audience
scenarios[]                situations at work, e.g. check-in
  id, title
  title_target, title_romanization
  context
  phrases[]
    id
    text                   the short phrase only — what text-to-speech reads aloud
    romanization           English-letter transliteration (required for non-Latin scripts)
    meaning                meaning in the learner's language
    literal, pattern, variants[]
    politeness             very formal | polite | casual
    in_a_sentence          { text, romanization, meaning } — a full sentence using the phrase
    when_to_use            the real-work moment
    notes                  tips, tones, gestures
    example
      setting
      dialogue[]           { speaker, text, romanization, meaning, uses_phrase? }
    likely_responses[]     { text, romanization, meaning }
```

## Rules

- **Phrases, not sentences.** `text` is a short reusable chunk (usually 1–6 words). The full sentence goes in `in_a_sentence`.
- **Transliteration and meaning always together.** For languages not written in Latin letters, every piece of target-language text has both `romanization` and `meaning`.
- **Speakable text.** No brackets, slashes or notes inside `text` or sentence fields.
- **Packs in parts.** If an AI sends a pack in several parts with the same `pack.id`, Qonverse merges the scenarios when each part is pasted.

## Validating a pack

```bash
pip install jsonschema
python3 -c "import json,jsonschema; jsonschema.validate(json.load(open('my-pack.json')), json.load(open('schema/pack.schema.json'))); print('ok')"
```
