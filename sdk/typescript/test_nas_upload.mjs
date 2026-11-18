import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { stripTypeScriptTypes } from 'node:module';

const source = await readFile(new URL('./index.ts', import.meta.url), 'utf8');
const { Gateway } = await import('data:text/javascript;base64,' + Buffer.from(stripTypeScriptTypes(source, {mode: 'transform'})).toString('base64'));

test('Node backend streams exact binary PUT, handles empty files, and never follows redirects', async () => {
  const calls = [];
  const server = createServer(async (request, response) => {
    let size = 0; for await (const block of request) size += block.length;
    calls.push({size, method: request.method, url: request.url, length: request.headers['content-length']});
    const redirect = request.url.includes('redirect');
    response.writeHead(redirect ? 302 : 200, {Location: '/must-not-follow', 'Content-Type': 'application/json'});
    response.end(JSON.stringify({ok: !redirect, data: {size_bytes: size}, error: {message: 'redirect'}}));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const gateway = new Gateway(`http://127.0.0.1:${server.address().port}/prefix`, 'fixture');
    const stream = length => new ReadableStream({start(controller) { for (let i = 0; i < length; i += 65536) controller.enqueue(new Uint8Array(Math.min(65536, length - i))); controller.close(); }});
    assert.equal((await gateway.uploadFile(stream(140000), {filename: 'file.bin', contentLength: 140000})).size_bytes, 140000);
    assert.equal(calls[0].method, 'PUT'); assert.equal(calls[0].length, '140000');
    assert.match(calls[0].url, /^\/prefix\/integrations\/nas\/objects\/upload\?/);
    assert.equal((await gateway.uploadFile(stream(0), {filename: 'empty.bin', contentLength: 0})).size_bytes, 0);
    await assert.rejects(gateway.uploadFile(stream(1), {filename: 'file.bin', key: 'redirect', contentLength: 1}));
    assert.equal(calls.length, 3);
    await assert.rejects(gateway.call('nas.objects.upload'), /uploadFile/);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
