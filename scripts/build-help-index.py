#!/usr/bin/python3
#
# build-help-index.py — regenerate MiniSearch JSON indices for the Logisim help system.
#
# Usage:  python3 build-help-index.py lang [lang ...]
#         python3 build-help-index.py en
#         python3 build-help-index.py en de fr es pt ru el
#
# For each language, scans help/{lang}/ recursively, extracts the page title and
# body text from every .html file, and writes help/{lang}/contents.json.
#
# The sidebar HTML files (help/{lang}/sidebar.html) are hand-maintained and are NOT
# modified by this script.

import sys, os, os.path, json
from html.parser import HTMLParser

# Navigation link prefixes to strip from extracted text (they add noise to search).
IGNORE_PREFIXES = {
    "en": ["Back to ", "Up to ", "Next:"],
    "fr": ["Back to ", "Voltar à ", "Retornar à", "Next:", "Suivant:"],
    "es": ["Back to ", "Retornar à", "Next:"],
    "ru": ["Back to ", "Назад к ", "Next:", "Далее:"],
    "el": ["Back to ", "Next:"],
    "pt": ["Back to ", "Voltar à ", "Retornar à", "Next:", "Próximo:"],
    "de": ["Back to ", "Zurück zur ", "Next:", "Weiter:"],
}

# Tags whose content should be completely skipped
SKIP_TAGS = {"script", "style", "nav"}

class _Extractor(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.title = None
        self._in_title = False
        self._title_buf = []
        self._skip_depth = 0
        self._parts = []

    def handle_starttag(self, tag, attrs):
        if tag == "title":
            self._in_title = True
        if tag in SKIP_TAGS:
            self._skip_depth += 1

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False
            if self.title is None:
                self.title = "".join(self._title_buf).strip()
        if tag in SKIP_TAGS:
            self._skip_depth = max(0, self._skip_depth - 1)

    def handle_data(self, data):
        if self._in_title:
            self._title_buf.append(data)
        elif self._skip_depth == 0:
            self._parts.append(data)

    def get_text(self):
        return "".join(self._parts)


def extract(html_path, ignore_prefixes):
    """Return (title, text) extracted from an HTML file."""
    with open(html_path, encoding="utf-8", errors="replace") as f:
        raw = f.read()

    p = _Extractor()
    p.feed(raw)

    title = p.title or os.path.splitext(os.path.basename(html_path))[0]

    lines = (line.strip() for line in p.get_text().splitlines())
    chunks = (phrase.strip() for line in lines for phrase in line.split("  "))
    text = "\n".join(
        c for c in chunks
        if c and not any(c.startswith(pfx) for pfx in ignore_prefixes)
    )
    return title, text


def build(lang, base):
    html_root = os.path.join(base, "help", lang)
    if not os.path.isdir(html_root):
        print(f"  ERROR: {html_root} not found, skipping.")
        return

    ignore = IGNORE_PREFIXES.get(lang, [])
    entries = []

    for dirpath, dirnames, filenames in os.walk(html_root):
        dirnames.sort()  # deterministic order
        for fn in sorted(filenames):
            if not fn.endswith(".html"):
                continue
            path = os.path.join(dirpath, fn)
            rel = "/" + os.path.relpath(path, os.path.join(base, "help")).replace("\\", "/")
            title, text = extract(path, ignore)
            entries.append({
                "id": len(entries) + 1,
                "title": title,
                "url": rel,
                "text": text,
            })

    out = os.path.join(base, "help", lang, "contents.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=2)
    print(f"  Built search index for {lang}: wrote {len(entries)} entries to {out}")


if len(sys.argv) < 2:
    print(f"Usage: {sys.argv[0]} lang [lang ...]")
    print(f"  e.g. {sys.argv[0]} en")
    print(f"  e.g. {sys.argv[0]} en de fr es pt ru el")
    sys.exit(1)

scripts = os.path.dirname(os.path.realpath(sys.argv[0]))
base = os.path.dirname(scripts)

for lang in sys.argv[1:]:
    # print(f"==== building help index for lang={lang} ====")
    build(lang, base)
