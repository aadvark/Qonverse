# Qonverse backup format (v1)

Settings › Backup & restore saves one JSON file. The Android app and the web app use the same format, so a backup made on one restores on the other. Restoring **merges** into what's already there: packs are added or updated, progress is combined (the higher review level wins), and settings are applied.

```json
{
  "qonverse_backup_version": 1,
  "created": "2026-11-17T18:30:00+08:00",
  "app": "android",
  "packs": [ { "qonverse_pack_version": "1.2", "pack": { … }, "scenarios": [ … ] } ],
  "hidden": ["restaurant-server-th"],
  "familiar": { "hotel-front-office-ja": ["ja-check-in-01"] },
  "starred": ["hotel-front-office-ja|ja-check-in-01"],
  "srs": { "hotel-front-office-ja": { "ja-check-in-01": [2, 20410] } },
  "settings": { "theme": "dark", "normal_rate": 1.0, "slow_rate": 0.6, "voices": { "ja-JP": "ja-jp-x-htm-local" } }
}
```

| Field | Meaning |
|---|---|
| `packs` | Every pack on the device, in [pack format](pack-format.md). |
| `hidden` | Built-in sample packs the user deleted. |
| `familiar` | Phrase ids marked familiar, by pack id. |
| `starred` | Starred phrases as `packId|phraseId`. |
| `srs` | Spaced repetition: `[box, due day]` per phrase. Box 0–7; due day is days since 1970-01-01 (local date). Intervals: 0, 1, 2, 4, 7, 15, 30, 60 days. |
| `settings` | Theme (`light`, `dark`, `amoled`, `system`), speeds, and chosen voice per language. Voice names differ by device; unknown ones are ignored. |
