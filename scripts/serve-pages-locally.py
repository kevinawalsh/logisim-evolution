#!/usr/bin/env python3
import http.server, sys

PREFIX = "/logisim-evolution"

class Handler(http.server.SimpleHTTPRequestHandler):
    def translate_path(self, path):
        if path.startswith(PREFIX):
            path = path[len(PREFIX):] or "/"
        return super().translate_path(path)

http.server.test(HandlerClass=Handler, port=8000)
