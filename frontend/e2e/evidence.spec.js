import { test, expect } from '@playwright/test'
const profile = { id: 'fixture', name: '受控测试模型', provider: 'deepseek', model: 'test', baseUrl: 'https://api.deepseek.com' }
function fixture() {
  const citation = { pmid: '101', field: 'abstract', quote: 'Allocation was concealed.' }
  return { revision: 1, persisted: true, stale: false, job: { status: 'IDLE' }, scope: { topic: '方法学专题策划', query: 'methods[tiab]', retrievedCount: 3, totalHits: 10, dateFrom: '2025-01-01', dateTo: '2026-11-03' }, report: {
    mode: 'AI', generatedAt: '2026-11-03T10:00:00', analyzedCount: 3, aiAnalyzedCount: 3, excludedCount: 0, missingAbstractCount: 0, undatedCount: 1, unassignedCount: 0,
    timeline: [{ month: '2025-01', count: 1 }, { month: '2026-02', count: 1 }], reviews: ['102'],
    sources: [{ id: 1, pmid: '101', title: 'Allocation methods study', authors: 'Alice Chen; Bob Li', pubdate: '2025-01', pubtypes: 'Journal Article', isReview: false, emailContacts: [{ email: 'alice@example.edu', authorNames: ['Alice Chen'], sourceUrl: 'https://pubmed.ncbi.nlm.nih.gov/101/' }] }, { id: 2, pmid: '102', title: 'Methods evidence review', authors: 'Alice Chen', pubdate: '2026-02', pubtypes: 'Systematic Review', isReview: true, emailContacts: [] }, { id: 3, pmid: '103', title: 'An undated paper', authors: 'Bob Li', pubdate: '2025', isReview: false, emailContacts: [] }],
    topics: [{ id: 't1', name: '终点选择', description: '终点选择的讨论范围', maturity: 'UNASSESSED', editorStatus: 'DRAFT', pmids: ['101', '102'], assignments: [], notes: [{ id: 'n1', kind: 'CONTEXT', text: '本研究报告了分配隐藏。', editorStatus: 'DRAFT', citations: [citation] }], coverage: [{ reviewPmid: '102', status: 'UNVERIFIED', rationale: '', citations: [] }] }, { id: 't2', name: '样本量设计', description: '样本量设计的讨论范围', maturity: 'UNASSESSED', editorStatus: 'DRAFT', pmids: ['101', '103'], assignments: [], notes: [], coverage: [] }],
    candidates: [{ id: 'c1', title: '终点与设计假设的衔接', question: '哪些设计假设影响结果解释？', whyNow: '需要梳理所纳入的方法学问题。', difference: '与既有综述的范围差异待核实。', boundary: '聚焦设计假设。', outline: ['问题界定', '现有证据'], status: 'PENDING', noveltyStatus: 'UNVERIFIED', editorNote: '', pmids: ['101'], citations: [citation] }]
  } }
}
async function setup(page, configured = false) {
  let state = fixture(); const writes = []
  await page.route('**/api/models', route => route.fulfill({ json: { profiles: configured ? [profile] : [], activeId: configured ? profile.id : '' } }))
  await page.route('**/api/scans/77/evidence', async route => {
    if (route.request().method() === 'PATCH') {
      const payload = route.request().postDataJSON(); writes.push(payload)
      if (payload.operation === 'topic') Object.assign(state.report.topics.find(t => t.id === payload.topicId), payload)
      if (payload.operation === 'note') Object.assign(state.report.topics[0].notes[0], payload)
      if (payload.operation === 'candidate') Object.assign(state.report.candidates[0], payload)
      if (payload.operation === 'review') { state.report.reviewInventory ||= []; state.report.reviewInventory.push(payload) }
      state.revision++
    }
    await route.fulfill({ json: state })
  })
  await page.route('**/api/scans/77/evidence/articles/*', route => { const pmid = route.request().url().split('/').at(-1); return route.fulfill({ json: { ...state.report.sources.find(s => s.pmid === pmid), abstractText: 'Allocation was concealed. The endpoint was assessed at week 24.' } }) })
  return { writes, state }
}
test('map, review matrix and notes distinguish metadata, coverage and source-backed claims', async ({ page }) => {
  await setup(page)
  await page.goto('/scan/77/topics'); await expect(page.getByRole('heading', { name: '领域梳理与专题策划' })).toBeVisible()
  await expect(page.locator('canvas')).toHaveCount(2)
  await expect(page.getByRole('button', { name: 'AI 梳理与提炼' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '重新 AI 梳理' })).toBeDisabled()
  await page.getByRole('tab', { name: '综述覆盖' }).click()
  await expect(page.getByRole('button', { name: /终点选择 · PMID 102：尚未核实/ })).toBeVisible()
  await expect(page.getByText('检索截止日期：尚未核实', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '梳理笔记' }).click(); await expect(page.getByText('本研究报告了分配隐藏。', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: /PMID 101.*查看引文/ }).click()
  await expect(page.getByRole('blockquote')).toHaveText('Allocation was concealed.')
  await expect(page.getByText('Allocation was concealed. The endpoint was assessed at week 24.', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: /在 PubMed 查看原始记录/ })).toHaveAttribute('href', 'https://pubmed.ncbi.nlm.nih.gov/101/')
})
test('edited direction and candidate decisions persist after reload', async ({ page }) => {
  const { writes } = await setup(page); await page.goto('/scan/77/topics')
  await page.getByRole('button', { name: '编辑方向', exact: true }).click(); await page.getByLabel('主题名称', { exact: true }).fill('终点选择与解释边界'); await page.getByRole('button', { name: '保存修改', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeHidden(); await page.reload(); await expect(page.getByRole('heading', { name: '终点选择与解释边界', exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '候选子议题' }).click(); await page.getByRole('button', { name: '编辑与决策' }).click(); await page.getByLabel('决策备注 / 合并去向', { exact: true }).fill('需补充既有综述盘点'); await page.getByRole('button', { name: '保存修改', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeHidden(); await expect(page.getByText('编辑备注：需补充既有综述盘点', { exact: true })).toBeVisible()
  expect(writes[0].revision).toBe(1); expect(writes[1].revision).toBe(2)
})
test('manual full-text coverage requires verification and a record', async ({ page }) => {
  const { writes } = await setup(page); await page.goto('/scan/77/topics'); await page.getByRole('tab', { name: '综述覆盖' }).click()
  await page.getByRole('button', { name: /终点选择 · PMID 102：尚未核实/ }).click()
  await page.getByRole('combobox', { name: '覆盖状态', exact: true }).press('ArrowDown'); await page.getByRole('option', { name: '深入覆盖', exact: true }).click()
  await page.getByLabel('覆盖内容与判断理由').fill('已讨论终点选择。'); await page.getByRole('button', { name: '保存修改', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('请先填写全文核对记录'); expect(writes.length).toBe(0)
})
test('analysis failure retains source-backed results and discloses the sampling limit', async ({ page }) => {
  await setup(page, true)
  await page.route('**/api/scans/77/evidence/analyze', route => route.fulfill({ status: 422, json: { error: '密钥权限不足，原有结果未被覆盖' } }))
  await page.goto('/scan/77/topics'); await page.getByRole('button', { name: '重新 AI 梳理' }).click()
  await expect(page.getByText(/每篇摘要|摘要可能截断/)).toBeVisible(); await page.getByRole('button', { name: '开始生成草稿' }).click()
  await expect(page.getByRole('alert')).toContainText('原有结果未被覆盖'); await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('heading', { name: '终点选择', exact: true })).toBeVisible()
})
test('a running analysis discloses elapsed time and its dedicated model deadline', async ({ page }) => {
  const { state } = await setup(page, true)
  state.job = { status: 'RUNNING', startedAt: new Date(Date.now() - 95_000).toISOString(), timeoutSeconds: 300, sourceCount: 20, message: '正在等待模型梳理结果，原有结果仍可阅读。' }
  await page.route('**/api/scans/77/evidence/job', route => route.fulfill({ json: state.job }))
  await page.goto('/scan/77/topics')
  await expect(page.getByRole('status').filter({ hasText: /已等待 1 分/ })).toBeVisible()
  await expect(page.getByText('模型请求最长等待 5 分钟', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '重新 AI 梳理' })).toBeDisabled()
})
test('four workbench views fit a narrow viewport with internal matrix scrolling', async ({ page }) => {
  await setup(page); await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/scan/77/topics')
  for (const name of ['领域图谱', '综述覆盖', '梳理笔记', '候选子议题']) { await page.getByRole('tab', { name, exact: true }).click(); await expect(page.getByRole('heading', { level: 1 })).toBeVisible(); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true) }
})
test('review inventory persists a search cutoff independently of publication date', async ({ page }) => {
  const { writes } = await setup(page); await page.goto('/scan/77/topics'); await page.getByRole('tab', { name: '综述覆盖' }).click()
  await page.getByRole('button', { name: '盘点综述信息', exact: true }).click()
  await page.getByLabel('综述检索截止日期 / 未报告', { exact: true }).fill('未报告')
  await page.getByLabel('已有综述覆盖了哪些内容', { exact: true }).fill('正文第3节讨论了设计假设，尚需核对终点相关内容。')
  await page.getByRole('button', { name: '保存修改', exact: true }).click(); await expect(page.getByRole('dialog')).toBeHidden()
  await expect(page.getByText('检索截止日期：未报告', { exact: true })).toBeVisible(); expect(writes[0].operation).toBe('review')
  await expect(page.getByText('2026-02 · PMID 102', { exact: true })).toBeVisible()
})
test('absence of reviews in the retrieved corpus is not presented as a synthesis gap', async ({ page }) => {
  const { state } = await setup(page); state.report.reviews = []; state.report.sources.forEach(s => { s.isReview = false })
  await page.goto('/scan/77/topics'); await page.getByRole('tab', { name: '综述覆盖' }).click()
  await expect(page.getByRole('heading', { name: '当前资料中尚未识别到综述' })).toBeVisible()
  await expect(page.getByText(/这不能说明该领域没有综述/)).toBeVisible()
})
test('live retrieved corpus renders without using a model and supplies desktop/mobile previews', async ({ page }) => {
  test.skip(process.env.SCOUT_LIVE_WORKBENCH !== 'true', 'Optional local integration check against an existing saved search')
  const errors = []; page.on('pageerror', e => errors.push(e.message))
  await page.goto('/scan/16/topics'); await expect(page.getByRole('heading', { name: '领域梳理与专题策划' })).toBeVisible()
  await expect(page.locator('canvas')).toHaveCount(2); await page.screenshot({ path: '../.local/evidence-desktop.png', fullPage: true })
  await page.getByRole('tab', { name: '综述覆盖' }).click(); await expect(page.getByRole('table')).toBeVisible(); await page.screenshot({ path: '../.local/evidence-reviews.png', fullPage: false })
  await page.setViewportSize({ width: 390, height: 844 }); await page.getByRole('tab', { name: '梳理笔记' }).click(); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: '../.local/evidence-mobile.png', fullPage: false }); expect(errors).toEqual([])
})
