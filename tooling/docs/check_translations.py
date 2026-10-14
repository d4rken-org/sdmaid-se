#!/usr/bin/env python3
"""Checks the translated docs pages against their English sources.

A translation lives at docs/<path>/<page>.md for each language in docs/_data/languages.json and
records the hash of the English file it was translated from (source_hash). While that hash
matches, its rendered headings (level, ID, order) must equal the English page's; otherwise it is
listed as stale and only checked for well-formedness.

Usage:
  check_translations.py <docs dir> [--write-status FILE]  before Jekyll: front matter, heading IDs, UI
                                                          strings; FILE lists stale pages for Jekyll
  check_translations.py <docs dir> --site <built site>    after Jekyll: rendered headings and links
  check_translations.py <docs dir> --hash <page>.md       source_hash for an English page
"""
import hashlib
import json
import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin, urlsplit

# Pages without translations
ENGLISH_ONLY = set(json.loads((Path(__file__).parent / "english_only.json").read_text(encoding="utf-8")))
HEADING = re.compile(r"^(#{1,6})\s+\S")
# kramdown ignores `#id` unless it matches ID (e.g. `#2-or-se`), so those use the `id="..."` form
ATTR = re.compile(r'^\{:\s*(?:#([^\s}]+)|id="([^"]+)")\s*\}\s*$')
FENCE = re.compile(r"^\s*(```|~~~)")
HASH = re.compile(r"^[0-9a-f]{16}$")
ID = re.compile(r"[A-Za-z][A-Za-z0-9_:-]*")
QUOTED = re.compile(r"""("(?:[^"\\]|\\.)*"|'(?:[^']|'')*')\s*(?:#.*)?""")


def scalar(value):
    """`key: value` scalars, plain or quoted, each optionally followed by a ` # comment`."""
    value = value.strip()
    if value[:1] in ("'", '"'):
        match = QUOTED.fullmatch(value)
        if not match:
            raise ValueError(f"unsupported front matter value {value}")
        quoted = match.group(1)[1:-1]
        return quoted.replace("''", "'") if value[0] == "'" else quoted
    return "" if value.startswith("#") else re.split(r"\s+#", value, maxsplit=1)[0]


def front_matter(text):
    match = re.match(r"---\n(.*?)\n---\n", text, re.S)
    if not match:
        return None, text
    fields = {}
    for line in match.group(1).splitlines():
        key, _, value = line.partition(":")
        fields[key.strip()] = scalar(value)
    return fields, text[match.end():]


def read_page(md, name, errors):
    """Front matter and body; reports a missing or unreadable front matter block and returns None."""
    try:
        fields, body = front_matter(md.read_text(encoding="utf-8"))
    except ValueError as error:
        errors.append(f"{name}: {error}")
        return None, ""
    if fields is None:
        errors.append(f"{name}: no front matter")
    return fields, body


def source_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()[:16]


def lint_ids(body, name, errors):
    """Every heading needs a `{: #id }` line directly below it, and no such line stands elsewhere."""
    lines, fenced, seen = body.split("\n"), False, set()
    for i, line in enumerate(lines):
        if FENCE.match(line):
            fenced = not fenced
        elif fenced:
            continue
        elif HEADING.match(line):
            attr = ATTR.match(lines[i + 1]) if i + 1 < len(lines) else None
            if not attr:
                errors.append(f"{name}: heading without a `{{: #id }}` line directly below: {line.strip()}")
                continue
            anchor = attr.group(1) or attr.group(2)
            if attr.group(1) and not ID.fullmatch(anchor):
                errors.append(f'{name}: kramdown does not read `{{: #{anchor} }}` as that ID, write `{{: id="{anchor}" }}`')
            if anchor in seen:
                errors.append(f"{name}: duplicate heading ID {anchor}")
            seen.add(anchor)
        elif ATTR.match(line) and not (i > 0 and HEADING.match(lines[i - 1])):
            errors.append(f"{name}: `{line.strip()}` is not directly below a heading")


def ui_strings(data, prefix=""):
    leaves = {}
    for key, value in data.items():
        if isinstance(value, dict):
            leaves.update(ui_strings(value, f"{prefix}{key}."))
        else:
            leaves[prefix + key] = value
    return leaves


class Content(HTMLParser):
    """Headings, IDs and links inside <main>."""

    def __init__(self):
        super().__init__()
        self.depth = 0
        self.headings, self.ids, self.links = [], [], []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "main":
            self.depth += 1
        if not self.depth:
            return
        if attrs.get("id"):
            self.ids.append(attrs["id"])
        if re.fullmatch(r"h[1-6]", tag):
            self.headings.append((int(tag[1]), attrs.get("id")))
        if tag == "a" and attrs.get("href") and not attrs.get("hreflang"):
            self.links.append(attrs["href"])

    def handle_endtag(self, tag):
        if tag == "main":
            self.depth -= 1


def pages(docs):
    """English pages by translation key, the configured languages, and English pages that can't be read."""
    english, problems = {}, []
    for md in sorted(docs.glob("*.md")):
        fields, _ = read_page(md, md.name, problems)
        if fields is None:
            continue
        if fields.get("lang") != "en" or not fields.get("translation_key") or not fields.get("permalink"):
            problems.append(f"{md.name}: needs front matter with lang: en, translation_key and permalink")
        elif fields["translation_key"] not in ENGLISH_ONLY:
            english[fields["translation_key"]] = (md, fields)
    languages = json.loads((docs / "_data/languages.json").read_text(encoding="utf-8"))
    return english, languages, problems


def expected_permalink(language, en_fields):
    # e.g. /setup -> /de/setup, / -> /de/
    return f"/{language['path']}{en_fields['permalink']}" if en_fields["permalink"] != "/" else f"/{language['path']}/"


def built(site, permalink):
    path = permalink.strip("/")
    if not path:
        return site / "index.html"
    return site / (f"{path}/index.html" if permalink.endswith("/") else f"{path}.html")


def check_sources(docs, status_file):
    english, languages, errors = pages(docs)
    stale, missing = [], []
    for md, _ in english.values():
        lint_ids(md.read_text(encoding="utf-8"), md.name, errors)

    en_ui = ui_strings(json.loads((docs / "_data/ui/en.json").read_text(encoding="utf-8")))
    for language in languages:
        code, folder = language["code"], docs / language["path"]
        ui_file = docs / "_data/ui" / f"{code}.json"
        if ui_file.is_file():
            strings = ui_strings(json.loads(ui_file.read_text(encoding="utf-8")))
            for key in en_ui:
                if not isinstance(strings.get(key), str) or not strings[key].strip():
                    errors.append(f"_data/ui/{code}.json: {key} must be a non-empty string")
        else:
            errors.append(f"_data/ui/{code}.json: missing")

        for md in sorted(folder.glob("*.md")):
            if md.stem not in {en_md.stem for en_md, _ in english.values()}:
                errors.append(f"{md.relative_to(docs)}: no English page {md.name}")
        for key, (en_md, en_fields) in english.items():
            md = folder / en_md.name
            name = str(md.relative_to(docs))
            if not md.is_file():
                missing.append(name)
                continue
            fields, body = read_page(md, name, errors)
            if fields is None:
                continue
            expected = {
                "lang": code,
                "translation_key": key,
                "permalink": expected_permalink(language, en_fields),
            }
            for field, value in expected.items():
                if fields.get(field) != value:
                    errors.append(f"{name}: {field} should be {value}, is {fields.get(field)}")
            if not fields.get("title"):
                errors.append(f"{name}: title missing")
            if not HASH.match(fields.get("source_hash", "")):
                errors.append(f"{name}: source_hash missing or malformed, see --hash")
            elif fields["source_hash"] != source_hash(en_md):
                stale.append(name)
            lint_ids(body, name, errors)

    for name in missing:
        print(f"untranslated (shows English): {name}")
    for name in stale:
        print(f"stale: {name}")
    if status_file:
        status_file.write_text(json.dumps({name: {"stale": True} for name in stale}, indent=2) + "\n", encoding="utf-8")
    return errors, f"{len(stale)} stale, {len(missing)} untranslated page(s)"


def check_site(docs, site):
    english, languages, _ = pages(docs)
    errors, warnings, current = [], [], 0
    parsed = {}

    def content(permalink):
        if permalink not in parsed:
            parser = Content()
            parser.feed(built(site, permalink).read_text(encoding="utf-8"))
            parsed[permalink] = parser
        return parsed[permalink]

    for language in languages:
        prefix = f"/{language['path']}"
        translated = {}
        for key, (en_md, en_fields) in english.items():
            md = docs / language["path"] / en_md.name
            fields = read_page(md, f"{language['path']}/{md.name}", errors)[0] if md.is_file() else None
            if fields and fields.get("permalink") == expected_permalink(language, en_fields):
                translated[en_fields["permalink"]] = fields
        for key, (en_md, en_fields) in english.items():
            fields = translated.get(en_fields["permalink"])
            if fields is None:
                continue
            name = f"{language['path']}/{en_md.name}"
            unbuilt = [p for p in (fields["permalink"], en_fields["permalink"]) if not built(site, p).is_file()]
            if unbuilt:
                errors.append(f"{name}: {', '.join(unbuilt)} not in the built site")
                continue
            page = content(fields["permalink"])
            duplicates = {i for i in page.ids if page.ids.count(i) > 1}
            if duplicates:
                errors.append(f"{name}: duplicate IDs {', '.join(sorted(duplicates))}")
            is_current = fields.get("source_hash") == source_hash(en_md)
            if is_current:
                current += 1
                if page.headings != content(en_fields["permalink"]).headings:
                    errors.append(f"{name}: rendered headings differ from {en_md.name} (level, ID and order must match)")
            for href in page.links:
                parts = urlsplit(urljoin(fields["permalink"], href))
                if parts.netloc not in ("", "sdmse.darken.eu"):
                    continue
                path = parts.path or "/"
                if path.endswith("/index.html"):
                    path = path[: -len("index.html")]
                elif path.endswith(".html"):
                    path = path[: -len(".html")]
                if path in translated:
                    (errors if is_current else warnings).append(f"{name}: links English {href}, use {prefix}{path}")
    for warning in warnings:
        print(f"warning: {warning}")
    return errors, f"{current} current translation(s) match their English headings"


def main():
    docs = Path(sys.argv[1])
    if "--hash" in sys.argv:
        print(source_hash(docs / sys.argv[sys.argv.index("--hash") + 1]))
        return 0
    if "--site" in sys.argv:
        errors, summary = check_site(docs, Path(sys.argv[sys.argv.index("--site") + 1]))
    else:
        status_file = Path(sys.argv[sys.argv.index("--write-status") + 1]) if "--write-status" in sys.argv else None
        errors, summary = check_sources(docs, status_file)
    for error in errors:
        print(error)
    print(f"{len(errors)} translation error(s)" if errors else f"Translations OK: {summary}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
