import test from 'node:test'
import assert from 'node:assert/strict'
import { translationIdentity } from './translationIdentity.js'
test('editing the service endpoint invalidates cached translations even when model ID is unchanged', () => {
  const profile = { id: 'saved-connection', baseUrl: 'https://old.example/v1', model: 'shared-model', provider: 'custom' }
  assert.notEqual(translationIdentity(profile, 1, 'title'), translationIdentity({ ...profile, baseUrl: 'https://new.example/v1' }, 1, 'title'))
})
