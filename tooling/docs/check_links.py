#!/usr/bin/env python3
"""Fails if a link into the docs site points at a page or heading that the built site lacks.

Checks every https://sdmse.darken.eu/... URL in the repository's sources (the app opens these) and
every internal link inside the built site.

Usage: check_links.py <built site dir> <repo root>
"""
import re
import subprocess
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urljoin, urlsplit

SITE_HOST = "sdmse.darken.eu"


class PageParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = set()
        self.hrefs = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get("id"):
            self.ids.add(attrs["id"])
        if tag == "a" and attrs.get("name"):
            self.ids.add(attrs["name"])
        if tag == "a" and attrs.get("href"):
            self.hrefs.append(attrs["href"])


def page_file(site, path):
    if path in ("", "/"):
        candidates = [site / "index.html"]
    elif path.endswith("/"):
        # GitHub Pages serves /foo/ only from foo/index.html
        candidates = [site / path.strip("/") / "index.html"]
    else:
        path = path.strip("/")
        candidates = [site / f"{path}.html", site / path / "index.html", site / path]
    return next((c for c in candidates if c.is_file()), None)


def main():
    site, repo = Path(sys.argv[1]), Path(sys.argv[2])
    pages = {}
    for html in site.rglob("*.html"):
        parser = PageParser()
        parser.feed(html.read_text(encoding="utf-8"))
        pages[html] = parser

    def check(path, fragment):
        target = page_file(site, path)
        if target is None:
            return f"no page for {path or '/'}"
        if fragment and target in pages and fragment not in pages[target].ids:
            return f"{path or '/'} has no #{fragment}"
        return None

    errors = []

    tracked = subprocess.run(["git", "-C", str(repo), "ls-files"], capture_output=True, text=True, check=True).stdout.split()
    url_pattern = re.compile(r"https://" + re.escape(SITE_HOST) + r"(/[^\s\"'<>)\]`]*)?")
    for name in tracked:
        if "/values-" in name or name.startswith("fastlane/metadata/") or name.startswith("docs/"):
            continue
        file = repo / name
        if file.suffix not in {".kt", ".md", ".yml", ".yaml", ".xml"}:
            continue
        for lineno, line in enumerate(file.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            for match in url_pattern.finditer(line):
                parts = urlsplit(match.group(0))
                error = check(parts.path, unquote(parts.fragment))
                if error:
                    errors.append(f"{name}:{lineno}: {match.group(0)}: {error}")

    for html, parser in pages.items():
        page_url = "/" + html.relative_to(site).as_posix()
        for href in parser.hrefs:
            parts = urlsplit(href)
            if parts.scheme or parts.netloc:
                if parts.netloc != SITE_HOST:
                    continue
            elif not parts.path:
                if parts.fragment and unquote(parts.fragment) not in parser.ids:
                    errors.append(f"{html.relative_to(site)}: {href}: no such heading")
                continue
            else:
                parts = urlsplit(urljoin(page_url, href))
            error = check(parts.path, unquote(parts.fragment))
            if error:
                errors.append(f"{html.relative_to(site)}: {href}: {error}")

    for error in errors:
        print(error)
    print(f"{len(errors)} broken link(s)" if errors else "All docs links resolve")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
