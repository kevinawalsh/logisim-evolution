#!/usr/bin/python3
#
# publish-docs.py — copy help/ to docs/, copy icons, and substitute placeholders in sidebar.js.
#
# Usage: python3 scripts/publish-docs.py
#
# Copies all files from help/ into docs/ (preserving docs/index.html and
# docs/.nojekyll which are not in help/), then replaces three placeholders
# in the copied docs/sidebar.js:
#   __VERSION__       — from VERSION file
#   __SOURCE_LINK__   — from "source:" line in contact.txt
#   __RELEASE_LINK__  — from "releases:" line in contact.txt

import os, shutil

scripts = os.path.dirname(os.path.realpath(__file__))
base    = os.path.dirname(scripts)

# Read substitution values
version = open(os.path.join(base, "VERSION")).read().strip()

contact = {}
for line in open(os.path.join(base, "contact.txt")):
    if ":" in line:
        key, _, val = line.partition(":")
        contact[key.strip()] = val.strip()

source_link  = contact["source"]
release_link = contact["releases"]

# Copy help/ -> docs/ (dirs_exist_ok preserves docs/index.html, docs/.nojekyll)
help_dir = os.path.join(base, "help")
docs_dir = os.path.join(base, "docs")
shutil.copytree(help_dir, docs_dir, dirs_exist_ok=True)

# Substitute placeholders in docs/sidebar.js
sidebar = os.path.join(docs_dir, "sidebar.js")
js = open(sidebar, encoding="utf-8").read()
js = js.replace("__VERSION__",      version)
js = js.replace("__SOURCE_LINK__",  source_link)
js = js.replace("__RELEASE_LINK__", release_link)
with open(sidebar, "w", encoding="utf-8") as f:
    f.write(js)

print(f"Published help/ -> docs/  (version {version})")
