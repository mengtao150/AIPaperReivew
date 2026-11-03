import test from 'node:test'
import assert from 'node:assert/strict'
import { newSearchForm, searchPayload, canFetchSnapshot } from './search.js'

test('a pasted PubMed query receives no implicit filters', () => {
  const form = newSearchForm(); form.query = 'HIV'; form.topic = 'HIV 研究'
  assert.deepEqual(searchPayload(form), { topic: 'HIV 研究', query: 'HIV', dateFrom: null, dateTo: null, dateField: 'dp', articleTypes: '', language: '', textAvailability: 'any', sortOrder: 'relevance' })
})
test('editing any search condition invalidates an earlier count', () => {
  const form = newSearchForm(); form.query = 'diabetes'; const snapshot = JSON.stringify(searchPayload(form))
  assert.equal(canFetchSnapshot(form, snapshot), true)
  form.language = 'english'
  assert.equal(canFetchSnapshot(form, snapshot), false)
})
