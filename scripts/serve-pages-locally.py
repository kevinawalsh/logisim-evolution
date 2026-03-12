#!/usr/bin/env python3
#
# serve-pages-locally.py — serve help or docs directory under /logisim-evolution/
#
# Usage: python3 scripts/serve-pages-locally.py [directory]
#   directory  path to serve (default: ./help)
#
# Serves http://localhost:8000/logisim-evolution/...
# Visit e.g. http://localhost:8000/logisim-evolution/en/guide/index.html

import http.server, os, sys

PORT = 8000
PREFIX = "/logisim-evolution"

scripts = os.path.dirname(os.path.realpath(__file__))
base = os.path.dirname(scripts)
directory = sys.argv[1] if len(sys.argv) > 1 else os.path.join(base, "help")
directory = os.path.realpath(directory)

class Handler(http.server.SimpleHTTPRequestHandler):
    def translate_path(self, path):
        if path.startswith(PREFIX):
            path = path[len(PREFIX):] or "/"
        return super().translate_path(path)

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=directory, **kwargs)

print(f"Serving {directory}")
print(f"Visit http://localhost:{PORT}/{PREFIX[1:]}/en/guide/index.html")
with http.server.ThreadingHTTPServer(("", PORT), Handler) as httpd:
    httpd.serve_forever()
