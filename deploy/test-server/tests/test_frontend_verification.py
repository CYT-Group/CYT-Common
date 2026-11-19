import io
from pathlib import Path
import runpy
from types import SimpleNamespace
import unittest
from urllib.error import HTTPError


MODULE = runpy.run_path(str(Path(__file__).parents[1] / 'verify-frontend.py'))
ORIGIN = 'https://common.example.invalid'
HTML = b'<script type="module" crossorigin src="/assets/app.js"></script><link rel="modulepreload" href="/assets/runtime.js"><link rel="stylesheet" crossorigin href="/assets/app.css">'


class Response(io.BytesIO):
    def __init__(self, content_type, body):
        super().__init__(body)
        self.headers = {'Content-Type': content_type}

    def getcode(self):
        return 200


class FrontendVerificationTests(unittest.TestCase):
    def opener(self, *, html=HTML, failure=None, asset_type=None):
        self.requests = []

        def open(request, timeout):
            self.requests.append(request)
            if request.full_url == ORIGIN + '/':
                return Response('text/html;charset=UTF-8', html)
            if failure:
                raise HTTPError(request.full_url, failure, 'fixture', {}, None)
            if request.full_url.endswith('.css'):
                return Response(asset_type or 'text/css', b'.app { color: black; }')
            return Response(asset_type or 'text/javascript', b'export const app = true;')

        return SimpleNamespace(open=open)

    def test_module_preload_script_and_css_use_the_browser_origin(self):
        result = MODULE['verify_frontend'](ORIGIN + '/', opener=self.opener())
        self.assertEqual(3, result['assets_checked'])
        self.assertIsNone(self.requests[0].get_header('Origin'))
        self.assertTrue(all(request.get_header('Origin') == ORIGIN for request in self.requests[1:]))
        self.assertTrue(all(request.get_header('Sec-fetch-mode') == 'cors' for request in self.requests[1:]))

    def test_cors_403_cannot_pass_a_successful_index(self):
        with self.assertRaisesRegex(MODULE['FrontendFailure'], 'HTTP 403'):
            MODULE['verify_frontend'](ORIGIN + '/', opener=self.opener(failure=403))

    def test_missing_build_asset_is_reported(self):
        with self.assertRaisesRegex(MODULE['FrontendFailure'], 'HTTP 404'):
            MODULE['verify_frontend'](ORIGIN + '/', opener=self.opener(failure=404))

    def test_html_fallback_for_javascript_is_not_a_success(self):
        with self.assertRaisesRegex(MODULE['FrontendFailure'], 'response type'):
            MODULE['verify_frontend'](ORIGIN + '/', opener=self.opener(asset_type='text/html'))

    def test_javascript_mime_does_not_hide_html_fallback(self):
        opener = SimpleNamespace(open=lambda request, timeout: Response('text/html', HTML) if request.full_url == ORIGIN + '/' else Response('text/javascript', b'<!doctype html><html>fallback</html>'))
        with self.assertRaisesRegex(MODULE['FrontendFailure'], 'response type'):
            MODULE['verify_frontend'](ORIGIN + '/', opener=opener)

    def test_external_assets_are_not_fetched_and_duplicates_are_checked_once(self):
        html = HTML + b'<script src="https://external.invalid/tracker.js"></script><script src="/assets/app.js"></script>'
        result = MODULE['verify_frontend'](ORIGIN + '/', opener=self.opener(html=html))
        self.assertEqual(3, result['assets_checked'])
        self.assertFalse(any('external.invalid' in request.full_url for request in self.requests))

    def test_an_error_page_with_no_frontend_cannot_pass(self):
        with self.assertRaisesRegex(MODULE['FrontendFailure'], 'entry assets'):
            MODULE['verify_frontend'](ORIGIN + '/', opener=self.opener(html=b'<h1>Error</h1>'))

    def test_base_url_rejects_credentials_queries_fragments_and_other_schemes(self):
        for base in ['https://user:password@common.invalid/', 'https://common.invalid/?password=fixture', 'https://common.invalid/#login', 'file:///local/index.html']:
            with self.subTest(base=base), self.assertRaises(MODULE['FrontendFailure']):
                MODULE['verify_frontend'](base, opener=self.opener())

    def test_origin_normalizes_default_ports_and_supports_ipv6(self):
        self.assertEqual('https://common.invalid', MODULE['origin_of']('https://common.invalid:443/'))
        self.assertEqual('http://[::1]:8088', MODULE['origin_of']('http://[::1]:8088/'))

    def test_redirects_outside_the_configured_site_are_rejected(self):
        redirects = MODULE['SameOriginRedirects'](ORIGIN)
        with self.assertRaisesRegex(MODULE['FrontendFailure'], 'outside'):
            redirects.redirect_request(None, None, 302, 'fixture', {}, 'https://other.invalid/')
