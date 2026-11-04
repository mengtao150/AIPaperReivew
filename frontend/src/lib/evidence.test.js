import test from 'node:test'
import assert from 'node:assert/strict'
import { authorEvidence, monthlySeries, exportNotes, elapsedTime } from './evidence.js'

test('analysis waiting time reflects its actual start rather than a simulated percentage', () => {
  assert.equal(elapsedTime('2026-11-03T13:00:00Z', Date.parse('2026-11-03T13:01:35Z')), '1 分 35 秒')
  assert.equal(elapsedTime('not a date', Date.now()), '')
  assert.equal(elapsedTime('2026-11-03T13:00:05Z', Date.parse('2026-11-03T13:00:00Z')), '0 秒')
})

test('author suggestions use actual topic papers and only explicitly associated emails', () => {
  const sources = [{ pmid: '1', authors: 'Alice Chen; Bob Li', emailContacts: [{ email: 'alice@example.edu', authorNames: ['Alice Chen'] }, { email: 'team@example.edu', authorNames: [] }] }, { pmid: '2', authors: 'Alice Chen', emailContacts: [] }]
  const authors = authorEvidence({ pmids: ['1', '2'] }, sources)
  assert.deepEqual(authors[0].pmids, ['1', '2']); assert.equal(authors[0].name, 'Alice Chen')
  assert.deepEqual(authors[0].contacts.map(c => c.email), ['alice@example.edu']); assert.equal(authors[1].contacts.length, 0)
})
test('timeline fills only interior missing months and never assigns year-only papers to January', () => {
  assert.deepEqual(monthlySeries([{ month: '2025-12', count: 2 }, { month: '2026-02', count: 3 }]), [{ month: '2025-12', count: 2 }, { month: '2026-01', count: 0 }, { month: '2026-02', count: 3 }])
  assert.deepEqual(monthlySeries([]), [])
})
test('export includes search scope, draft states and traceable quote sources', () => {
  const text = exportNotes({ scope: { topic: '主题', query: 'HIV', retrievedCount: 1, totalHits: 5 }, revision: 2, report: { mode: 'AI', aiAnalyzedCount: 1, generatedAt: '2026-11-03', sources: [{ pmid: '1', title: 'Source title' }], topics: [{ name: '细分方向', notes: [{ kind: 'GAP', text: '待核实的缺口', editorStatus: 'DRAFT', citations: [{ pmid: '1', field: 'abstract', quote: 'Original quoted evidence.' }] }] }], candidates: [] } })
  assert.match(text, /HIV/); assert.match(text, /1.*5/); assert.match(text, /待核实/); assert.match(text, /pubmed.ncbi.nlm.nih.gov\/1\//); assert.match(text, /Original quoted evidence/)
})
