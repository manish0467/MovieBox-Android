#!/usr/bin/env python3
"""Serve local player fixtures and print the headers from every request."""

import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class HeaderLoggingHandler(SimpleHTTPRequestHandler):
    def _print_request_headers(self) -> None:
        print(f"\n{self.command} {self.path}", flush=True)
        for name, value in self.headers.items():
            print(f"{name}: {value}", flush=True)

    def do_GET(self) -> None:
        self._print_request_headers()
        super().do_GET()

    def do_HEAD(self) -> None:
        self._print_request_headers()
        super().do_HEAD()


def main() -> None:
    default_directory = Path(__file__).resolve().parent / "out"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--directory", type=Path, default=default_directory)
    args = parser.parse_args()

    handler = partial(HeaderLoggingHandler, directory=str(args.directory))
    server = ThreadingHTTPServer((args.bind, args.port), handler)
    print(f"Serving {args.directory} on {args.bind}:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
