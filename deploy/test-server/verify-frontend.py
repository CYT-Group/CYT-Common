#!/usr/bin/env python3
"""Check the public HTML and its same-origin entry assets as a browser would."""
import argparse
from html.parser import HTMLParser
import json
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener


class FrontendFailure(Exception):
    pass


def origin_of(url):
    parsed = urlsplit(url)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.password:
        raise FrontendFailure('Use an HTTP(S) site URL without embedded credentials')
    if parsed.port is not None and not 0 < parsed.port < 65536:
        raise FrontendFailure('Invalid site port')
    host = parsed.hostname.lower()
    if ':' in host:
        host = '[' + host + ']'
    port = parsed.port
    if port is not None and port != (443 if parsed.scheme == 'https' else 80):
        host += ':' + str(port)
    return parsed.scheme + '://' + host


class EntryAssets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.assets = []

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == 'script' and values.get('src'):
            self.assets.append(values['src'])
        elif tag == 'link' and values.get('href'):
            relations = values.get('rel', '').lower().split()
            if 'stylesheet' in relations or 'modulepreload' in relations or (
                    'preload' in relations and values.get('as') in ('script', 'style')):
                self.assets.append(values['href'])


class SameOriginRedirects(HTTPRedirectHandler):
    def __init__(self, origin):
        self.origin = origin

    def redirect_request(self, request, response, code, message, headers, new_url):
        if origin_of(new_url) != self.origin:
            raise FrontendFailure('Public frontend redirected outside the configured origin')
        return super().redirect_request(request, response, code, message, headers, new_url)


def verify_frontend(base_url, timeout=10, opener=None):
    if timeout <= 0 or timeout > 60:
        raise FrontendFailure('Timeout must be between 0 and 60 seconds')
    parsed = urlsplit(base_url)
    if parsed.query or parsed.fragment:
        raise FrontendFailure('Site URL must not contain a query or fragment')
    origin = origin_of(base_url)
    opener = opener or build_opener(ProxyHandler({}), SameOriginRedirects(origin))

    def read(url, browser_origin=False, limit=1024):
        headers = {'User-Agent': 'CYT-Common-Release-Check', 'Cache-Control': 'no-cache'}
        if browser_origin:
            headers['Origin'] = origin
            headers['Sec-Fetch-Mode'] = 'cors'
            headers['Sec-Fetch-Site'] = 'same-origin'
        try:
            with opener.open(Request(url, headers=headers), timeout=timeout) as response:
                if response.getcode() != 200:
                    raise FrontendFailure('HTTP ' + str(response.getcode()) + ': ' + urlsplit(url).path)
                return response.headers.get('Content-Type', '').split(';', 1)[0].lower(), response.read(limit)
        except HTTPError as error:
            raise FrontendFailure('HTTP ' + str(error.code) + ': ' + urlsplit(url).path) from None
        except (URLError, OSError) as error:
            raise FrontendFailure('Request failed: ' + urlsplit(url).path + ' (' + type(error).__name__ + ')') from None

    content_type, html = read(base_url, limit=2 * 1024 * 1024)
    if content_type not in ('text/html', 'application/xhtml+xml'):
        raise FrontendFailure('Public index did not return HTML')
    entries = EntryAssets()
    entries.feed(html.decode('utf-8-sig'))
    urls = []
    for entry in entries.assets:
        url = urljoin(base_url, entry)
        if origin_of(url) == origin and url not in urls:
            urls.append(url)
    if not urls or len(urls) > 64:
        raise FrontendFailure('Expected 1-64 same-origin frontend entry assets')
    for url in urls:
        content_type, body = read(url, browser_origin=True)
        path = urlsplit(url).path.lower()
        if path.endswith('.css'):
            allowed = ('text/css',)
        else:
            allowed = ('text/javascript', 'application/javascript', 'application/ecmascript', 'text/ecmascript')
        if content_type not in allowed or body.lstrip().lower().startswith((b'<!doctype', b'<html')):
            raise FrontendFailure('Wrong asset response type: ' + urlsplit(url).path)
    return {'origin': origin, 'assets_checked': len(urls), 'html': 200, 'browser_origin_assets': 200}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--timeout', type=float, default=10)
    args = parser.parse_args(argv)
    print(json.dumps(verify_frontend(args.base_url, args.timeout), ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except (FrontendFailure, ValueError, UnicodeError) as error:
        raise SystemExit('Frontend verification failed: ' + str(error)) from None
