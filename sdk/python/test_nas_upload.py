import json
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from cyt_gateway import Gateway, GatewayError


class UploadSDKTest(unittest.TestCase):
    def test_real_file_stream_put_and_no_redirect_or_retry(self):
        calls = []
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass
            def do_PUT(self):
                length = int(self.headers['Content-Length'])
                size = 0
                while size < length:
                    size += len(self.rfile.read(min(65536, length - size)))
                calls.append((self.path, size, self.headers['Content-Type']))
                status = 302 if 'redirect' in self.path else 200
                body = json.dumps({'ok': status == 200, 'data': {'size_bytes': size}, 'error': {'message': 'redirect'}}).encode()
                self.send_response(status)
                self.send_header('Location', '/must-not-follow')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            gateway = Gateway(f'http://127.0.0.1:{server.server_port}/prefix', 'fixture-key')
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'file.bin'
                path.write_bytes(b'fixture' * 20000)
                result = gateway.upload_file(path, folder='文档')
                self.assertEqual(140000, result['size_bytes'])
                self.assertEqual(140000, calls[0][1])
                self.assertIn('/prefix/integrations/nas/objects/upload?', calls[0][0])
                self.assertIn('filename=file.bin', calls[0][0])
                self.assertEqual('application/octet-stream', calls[0][2])
                with self.assertRaises(GatewayError):
                    gateway.upload_file(path, key='redirect')
                self.assertEqual(2, len(calls))
                path.write_bytes(b'')
                self.assertEqual(0, gateway.upload_file(path)['size_bytes'])
        finally:
            server.shutdown()
            server.server_close()

    def test_json_upload_is_rejected_locally(self):
        with self.assertRaises(ValueError):
            Gateway('http://unused.invalid', 'fixture').call('nas.objects.upload')


if __name__ == '__main__':
    unittest.main()
