---
paths:
  - "docs/**"
  - "tooling/docs/**"
---

# Docs site (sdmse.darken.eu)

- English pages are `docs/<page>.md`; translations are `docs/<path>/<page>.md` for each language in
  `docs/_data/languages.json`, with site labels in `docs/_data/ui/<code>.json`.
- Every heading needs a `{: #id }` line directly below it. The app links these anchors
  (e.g. `/setup#porter`), and translations keep the same IDs, so never change an existing ID.
- Editing an English page makes its translations stale (their `source_hash` no longer matches). The
  site then shows an "out of date" notice on them; the build does not fail. A new English page shows
  English in every language until translated.
- To resync a language, retranslate only the changed sections, then set `source_hash` to
  `python3 tooling/docs/check_translations.py docs --hash <page>.md`. A current translation must
  match its English page's rendered headings exactly, or the Pages build fails.
- Check locally: `python3 tooling/docs/check_translations.py docs` before a Jekyll build, then
  `check_links.py _site .` and `check_translations.py docs --site _site` on the built site.
