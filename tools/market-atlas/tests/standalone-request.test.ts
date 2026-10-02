import test from 'node:test';
import assert from 'node:assert/strict';
import { standaloneRequest } from '../lib/standalone-request.ts';

test('HTTPS reverse proxy retains the original body and public origin without trusting client headers', async () => {
  const input = new Request('http://127.0.0.1:5174/tools/market-atlas/api/action?test=1', {
    method: 'POST',
    headers: {
      Origin: 'https://example.test:8443',
      Cookie: 'market_atlas=test',
      'Content-Type': 'application/json',
      'X-Forwarded-Host': 'attacker.test',
      'oai-authenticated-user-id': 'another-person',
      'oai-authenticated-user-email': 'other@example.test',
    },
    body: '{"action":"lesson-open"}',
  });
  const output = standaloneRequest(input, 'https://example.test:8443');
  assert.equal(output.url, 'https://example.test:8443/tools/market-atlas/api/action?test=1');
  assert.equal(output.headers.get('Origin'), new URL(output.url).origin);
  assert.equal(output.headers.get('Cookie'), 'market_atlas=test');
  assert.equal(output.headers.get('oai-authenticated-user-id'), null);
  assert.equal(output.headers.get('oai-authenticated-user-email'), null);
  assert.equal(await output.text(), '{"action":"lesson-open"}');
});

test('foreign Origin remains foreign so application origin checks reject it', () => {
  const output = standaloneRequest(new Request('http://localhost/api/action', { headers: { Origin: 'https://attacker.test' } }), 'https://example.test');
  assert.notEqual(output.headers.get('Origin'), new URL(output.url).origin);
});

test('local previews are unchanged and invalid operator configuration is rejected', () => {
  const input = new Request('http://localhost/api/state');
  assert.equal(standaloneRequest(input), input);
  for (const invalid of ['https://example.test/path', 'https://example.test/', 'ftp://example.test']) {
    assert.throws(() => standaloneRequest(input, invalid));
  }
});

const canonical = 'https://example.test:80';
const publicOrigins = `${canonical},http://example.test,https://example.test`;
for (const [label, protocol, host, expected] of [
  ['HTTPS on port 80', 'https', 'example.test:80', canonical],
  ['HTTP on port 80', 'http', 'example.test:80', 'http://example.test'],
  ['HTTPS on port 443', 'https', 'example.test:443', 'https://example.test'],
]) {
  test(`allowed ${label} retains same-origin POST, cookies and body`, async () => {
    const input = new Request('http://127.0.0.1:5174/tools/market-atlas/api/action?test=1', {
      method: 'POST',
      headers: {
        Origin: expected,
        Cookie: 'market_atlas=original-cookie',
        'Content-Type': 'application/json',
        'X-Forwarded-Proto': protocol,
        'X-Forwarded-Host': host,
        'oai-authenticated-user-id': 'forged-user',
        'oai-authenticated-user-full-name': 'forged-name',
      },
      body: '{"version":1,"action":"lesson-open","payload":{"id":"equity"}}',
    });
    const output = standaloneRequest(input, canonical, publicOrigins);
    assert.equal(output.url, `${expected}/tools/market-atlas/api/action?test=1`);
    assert.equal(output.headers.get('Origin'), new URL(output.url).origin);
    assert.equal(output.headers.get('Cookie'), 'market_atlas=original-cookie');
    assert.equal(output.headers.get('oai-authenticated-user-id'), null);
    assert.equal(output.headers.get('oai-authenticated-user-full-name'), null);
    assert.equal(await output.text(), '{"version":1,"action":"lesson-open","payload":{"id":"equity"}}');
  });
}

test('unlisted and malformed forwarded hosts cannot change the public origin or make attacker Origin same-origin', () => {
  for (const host of ['attacker.test', 'example.test.attacker.test', 'example.test:8443', 'attacker.test, example.test', 'attacker@example.test', 'example.test/path', 'example.test\\attacker', 'example.test:invalid']) {
    const output = standaloneRequest(new Request('http://localhost/tools/market-atlas/api/action', {
      headers: { Origin: 'https://attacker.test', 'X-Forwarded-Proto': 'https', 'X-Forwarded-Host': host },
    }), canonical, publicOrigins);
    assert.equal(new URL(output.url).origin, canonical, host);
    assert.equal(output.headers.get('Origin'), 'https://attacker.test', host);
    assert.notEqual(output.headers.get('Origin'), new URL(output.url).origin, host);
  }
});

test('forwarded origin selection requires both trusted headers and defaults to the single canonical origin', () => {
  const invalidHeaders: Record<string, string>[] = [
    { 'X-Forwarded-Host': 'example.test' },
    { 'X-Forwarded-Proto': 'http' },
    { 'X-Forwarded-Proto': 'https,http', 'X-Forwarded-Host': 'example.test' },
    { 'X-Forwarded-Proto': 'javascript', 'X-Forwarded-Host': 'example.test' },
  ];
  for (const headers of invalidHeaders) {
    const output = standaloneRequest(new Request('http://localhost/api/state', { headers }), canonical, publicOrigins);
    assert.equal(new URL(output.url).origin, canonical);
  }
  const output = standaloneRequest(new Request('http://localhost/api/action', {
    headers: { Origin: 'http://example.test', 'X-Forwarded-Proto': 'http', 'X-Forwarded-Host': 'example.test' },
  }), canonical);
  assert.equal(new URL(output.url).origin, canonical);
  assert.notEqual(output.headers.get('Origin'), new URL(output.url).origin);
});

test('foreign Origin is unchanged even when forwarding selects an allowed secondary origin', () => {
  const output = standaloneRequest(new Request('http://localhost/api/action', {
    headers: { Origin: 'https://attacker.test', 'X-Forwarded-Proto': 'https', 'X-Forwarded-Host': 'example.test:443' },
  }), canonical, publicOrigins);
  assert.equal(new URL(output.url).origin, 'https://example.test');
  assert.equal(output.headers.get('Origin'), 'https://attacker.test');
  assert.notEqual(output.headers.get('Origin'), new URL(output.url).origin);
});

test('operator origin lists accept whitespace and reject invalid entries or a missing canonical origin', () => {
  const input = new Request('http://localhost/api/state', {
    headers: { 'X-Forwarded-Proto': 'http', 'X-Forwarded-Host': 'example.test' },
  });
  assert.equal(new URL(standaloneRequest(input, canonical, ' http://example.test , https://example.test ').url).origin, 'http://example.test');
  for (const invalid of ['https://example.test/path', 'https://example.test/', 'ftp://example.test', 'https://example.test,', 'https://example.test:443']) {
    assert.throws(() => standaloneRequest(input, canonical, invalid), Error, invalid);
  }
  assert.throws(() => standaloneRequest(input, undefined, publicOrigins), /requires MARKET_ATLAS_PUBLIC_ORIGIN/);
});
