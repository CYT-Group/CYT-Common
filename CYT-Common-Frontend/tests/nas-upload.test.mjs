import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { callExample } from '../src/utils/documentation.ts';

const upload = {
  key: 'nas.objects.upload',
  path: '/integrations/nas/objects/upload',
  transport: 'raw_binary',
  http_method: 'PUT',
  tags: ['write', 'danger', 'raw_binary'],
};
test('upload examples use binary PUT and dedicated backend SDK methods', () => {
  const http = callExample(upload, {}, 'http', 'https://gateway.example/api');
  assert.match(http, /--request PUT/);
  assert.match(http, /filename=file.bin&folder=uploads/);
  assert.match(http, /--data-binary '@file.bin'/);
  assert.doesNotMatch(http, /application\/json|POST|Idempotency/);
  assert.match(callExample(upload, {}, 'python', 'https://gateway.example'), /upload_file\(/);
  assert.match(
    callExample(upload, {}, 'typescript', 'https://gateway.example'),
    /uploadFile\(stream/,
  );
});

test('offline guide executes PUT binary examples and query parameter rendering', () => {
  const html = readFileSync(
    new URL(
      '../../CYT-Common-API/src/main/resources/templates/integration-guide.html',
      import.meta.url,
    ),
    'utf8',
  );
  const script = html.match(/<script>\s*([\s\S]*?)<\/script>/)[1];
  const elements = new Map();
  const element = () => ({
    value: '',
    textContent: '',
    disabled: false,
    dataset: {},
    classList: { toggle() {} },
    append() {},
    replaceChildren() {},
    addEventListener() {},
    setAttribute() {},
  });
  const spec = {
    info: { title: 'Upload' },
    paths: {
      [upload.path]: {
        put: {
          'x-cyt-operation': upload.key,
          'x-cyt-transport': 'raw_binary',
          parameters: [
            { name: 'filename', in: 'query', required: true, schema: { type: 'string' } },
          ],
          requestBody: {
            content: {
              'application/octet-stream': { schema: { type: 'string', format: 'binary' } },
            },
          },
          responses: {},
        },
      },
    },
  };
  const byId = (id) => {
    if (!elements.has(id)) elements.set(id, element());
    return elements.get(id);
  };
  byId('openapi-data').textContent = JSON.stringify(spec);
  byId('address').value = 'https://gateway.example';
  const context = vm.createContext({
    URL,
    structuredClone,
    document: { getElementById: byId, createElement: element },
    navigator: { clipboard: { writeText: async () => {} } },
    setTimeout,
  });
  vm.runInContext(script, context);
  assert.equal(byId('endpoint').textContent, 'PUT ' + upload.path);
  assert.match(byId('schema').textContent, /filename/);
  assert.match(byId('sample').textContent, /--request PUT/);
  byId('language').value = 'python';
  vm.runInContext('renderSample()', context);
  assert.match(byId('sample').textContent, /upload_file\(/);
  byId('language').value = 'typescript';
  vm.runInContext('renderSample()', context);
  assert.match(byId('sample').textContent, /uploadFile\(stream/);
});
