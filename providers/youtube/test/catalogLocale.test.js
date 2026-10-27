import test from 'node:test';
import assert from 'node:assert/strict';
import { createBrowseRequest } from '../src/runtime/catalog.js';

for (const [language, expected] of [['es', 'es'], ['en', 'en'], [undefined, 'en']]) {
  test(`catalog locale ${language ?? 'default'}`, async () => {
    let requestBody;
    const browse = createBrowseRequest({ interfaceLanguage: language }, { guest: true }, async (_, options) => {
      requestBody = JSON.parse(options.body);
      return { ok: true, text: async () => '{}' };
    });
    await browse({ browseId: 'FEmusic_home' });
    assert.equal(requestBody.context.client.hl, expected);
  });
}
