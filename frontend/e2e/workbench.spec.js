import { test, expect } from '@playwright/test'
const profile = { id: 'model-fixture', name: '测试模型', provider: 'deepseek', model: 'deepseek-flash', baseUrl: 'https://api.deepseek.com', maskedKey: '••••••', hasKey: true, testedAt: '2026-11-03T08:00:00Z' }
async function models(page, configured = true) { await page.route('**/api/models', route => route.request().method() === 'GET' ? route.fulfill({ json: { profiles: configured ? [profile] : [], activeId: configured ? profile.id : '' } }) : route.fallback()) }
test('direct search preserves short queries and invalidates stale results after edits', async ({ page }) => {
  await models(page, false)
  await page.route('**/api/scans/preview', async route => { const data = route.request().postDataJSON(); await route.fulfill({ json: { query: `(${data.query})` } }) })
  await page.route('**/api/scans', async route => { await route.fulfill({ json: { run: { id: 101 }, query: '(HIV)', count: { total: 42, translation: 'HIV[All Fields]', warnings: [] } } }) })
  await page.goto('/scan/new'); await page.getByLabel('任务名称').fill('HIV 研究'); await page.getByLabel('PubMed 检索式', { exact: true }).fill('HIV')
  const request = page.waitForRequest(req => req.url().endsWith('/api/scans') && req.method() === 'POST')
  await page.getByRole('button', { name: '试检索并检查结果' }).click()
  const payload = (await request).postDataJSON(); expect(payload.language).toBe(''); expect(payload.textAvailability).toBe('any'); expect(payload.dateFrom).toBeNull()
  await expect(page.getByRole('button', { name: '获取文献与摘要' })).toBeEnabled()
  await page.getByLabel('PubMed 检索式', { exact: true }).fill('HIV AND prevention')
  await expect(page.getByRole('button', { name: '获取文献与摘要' })).toHaveCount(0)
})
test('AI assistant creates an editable draft without starting a search', async ({ page }) => {
  await models(page)
  await page.route('**/api/ai/search-assistant', route => route.fulfill({ json: { ready: true, reply: '已按你的描述整理草稿。', topic: '糖尿病研究', query: 'diabetes[tiab]', summary: '近半年，不限语言和研究类型。', filters: { dateFrom: '2026-05-03', dateTo: '2026-11-03' } } }))
  await page.route('**/api/scans/preview', route => route.fulfill({ json: { query: '(diabetes[tiab])' } }))
  await page.goto('/scan/new'); await page.getByRole('tab', { name: /AI 辅助构建/ }).click(); await page.getByPlaceholder('描述需求，或回答助手的问题…').fill('了解糖尿病近期研究，其他不限'); await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.getByRole('button', { name: '应用到检索条件' }).click(); await expect(page.getByLabel('PubMed 检索式', { exact: true })).toHaveValue('diabetes[tiab]'); await expect(page.getByPlaceholder('开始日期')).toHaveValue('2026-05-03'); await expect(page.getByText('PubMed 命中', { exact: true })).toHaveCount(0)
})
test('title and abstract hover translations are cached while original text stays visible', async ({ page }) => {
  await models(page)
  const article = { id: 501, pmid: '12345', title: 'Trial of cardiovascular outcomes', abstractText: 'METHODS: Adults were enrolled.\nRESULTS: No difference was observed.', authors: 'A. Researcher; B. Scientist', journalAbbrev: 'Test Journal', pubdate: '2026-10', pubtypes: 'Clinical Trial' }
  await page.route('**/api/scans/101', route => route.fulfill({ json: { id: 101, topic: '阅读测试', fetchedCount: 1, query: 'trial' } }))
  await page.route('**/api/scans/101/categories', route => route.fulfill({ json: [] }))
  await page.route('**/api/scans/101/articles?**', route => route.fulfill({ json: { content: [article], totalElements: 1 } }))
  let titleCalls = 0, abstractCalls = 0
  await page.route('**/api/ai/articles/501/translation', route => { const { field } = route.request().postDataJSON(); if (field === 'title') titleCalls++; else abstractCalls++; return route.fulfill({ json: { text: field === 'title' ? '心血管结局试验' : '方法：纳入成年参与者。\n结果：未观察到差异。', model: profile.model } }) })
  await page.goto('/scan/101/articles'); await expect(page.getByText('METHODS: Adults were enrolled.', { exact: false }).first()).toBeVisible()
  const title = page.getByRole('button', { name: /查看标题中文翻译/ }); await title.hover(); await expect(page.getByText('心血管结局试验', { exact: true })).toBeVisible(); await page.getByRole('heading', { name: '阅读测试' }).hover(); await title.hover(); expect(titleCalls).toBe(1)
  await page.getByRole('button', { name: /查看摘要中文翻译/ }).hover(); await expect(page.getByText('方法：纳入成年参与者。', { exact: false })).toBeVisible(); expect(abstractCalls).toBe(1)
  await page.getByRole('button', { name: '展开阅读' }).click(); await expect(page.getByText('原文摘要', { exact: false })).toBeVisible()
})
test('settings report connection errors, save successful tests and retain masked keys on reload', async ({ page }) => {
  let saved = false, failing = true
  await page.route('**/api/models', route => route.request().method() === 'GET' ? route.fulfill({ json: { profiles: saved ? [profile] : [], activeId: saved ? profile.id : '' } }) : failing ? route.fulfill({ status: 502, json: { error: '密钥无效或没有模型访问权限' } }) : (saved = true, route.fulfill({ json: { ok: true, latencyMs: 100, profile } })))
  await page.goto('/settings/models'); await page.getByLabel('API 密钥', { exact: false }).fill('not-a-real-key'); await page.getByRole('button', { name: '测试并保存' }).click(); await expect(page.getByRole('alert')).toContainText('密钥无效'); failing = false; await page.getByRole('button', { name: '测试并保存' }).click(); await expect(page.getByText('连接测试成功', { exact: true })).toBeVisible(); await page.reload(); await expect(page.getByText('正在使用', { exact: true })).toBeVisible(); await page.getByRole('button', { name: '编辑', exact: true }).click(); await expect(page.getByLabel('API 密钥', { exact: false })).toHaveValue(''); await expect(page.getByPlaceholder('已保存密钥，输入新值以替换')).toBeVisible()
})
test('a completed connection check with ok false never appears as success', async ({ page }) => {
  await models(page, false)
  await page.route('**/api/models/test', route => route.fulfill({ json: { ok: false, code: 'authentication_failed', upstreamStatus: 401, error: 'DeepSeek 密钥认证失败，请检查密钥是否来自该服务商。' } }))
  await page.goto('/settings/models'); await page.getByLabel('API 密钥', { exact: false }).fill('not-a-real-key'); await page.getByRole('button', { name: '测试连接', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('密钥认证失败'); await expect(page.getByText('连接测试成功', { exact: true })).toHaveCount(0)
})
test('Coding Plan preset submits its own endpoint without a general API fallback', async ({ page }) => {
  await models(page, false); let submitted
  await page.route('**/api/models/test', route => { submitted = route.request().postDataJSON(); return route.fulfill({ json: { ok: true, latencyMs: 80 } }) })
  await page.goto('/settings/models'); await page.getByRole('combobox', { name: '模型服务商' }).press('ArrowDown'); await page.getByRole('option', { name: '智谱 GLM Coding Plan · 国内', exact: true }).click(); await expect(page.getByLabel('API 地址', { exact: false })).toHaveValue('https://open.bigmodel.cn/api/coding/paas/v4'); await page.getByLabel('API 密钥', { exact: false }).fill('not-a-real-key'); await page.getByRole('button', { name: '测试连接', exact: true }).click(); await expect(page.getByText('连接测试成功', { exact: true })).toBeVisible(); expect(submitted.provider).toBe('bigmodel-coding'); expect(submitted.baseUrl).toBe('https://open.bigmodel.cn/api/coding/paas/v4')
})
test('follow-up clarification invalidates the earlier AI draft', async ({ page }) => {
  await models(page); let calls = 0
  await page.route('**/api/ai/search-assistant', route => route.fulfill({ json: ++calls === 1 ? { ready: true, reply: '已有草稿', topic: 'HIV', query: 'HIV', summary: '初始范围' } : { ready: false, reply: '请明确改变后的人群范围。' } }))
  await page.goto('/scan/new'); await page.getByRole('tab', { name: /AI 辅助构建/ }).click(); const input = page.getByPlaceholder('描述需求，或回答助手的问题…'); await input.fill('HIV研究'); await page.getByRole('button', { name: '发送', exact: true }).click(); await expect(page.getByRole('button', { name: '应用到检索条件' })).toBeVisible(); await input.fill('改成其他特殊人群'); await page.getByRole('button', { name: '发送', exact: true }).click(); await expect(page.getByText('请明确改变后的人群范围。')).toBeVisible(); await expect(page.getByRole('button', { name: '应用到检索条件' })).toHaveCount(0)
})
test('opening a fresh search clears a restored counted task', async ({ page }) => {
  await models(page, false)
  await page.route('**/api/scans/123', route => route.fulfill({ json: { id: 123, topic: '旧任务', sourceQuery: 'HIV', query: '(HIV)', status: 'COUNTED', totalHits: 1, sortOrder: 'relevance' } }))
  await page.route('**/api/scans/preview', route => route.fulfill({ json: { query: '(HIV)' } }))
  await page.goto('/scan/new?run=123'); await expect(page.getByLabel('任务名称')).toHaveValue('旧任务'); await expect(page.getByRole('button', { name: '获取文献与摘要' })).toBeVisible()
  await page.getByRole('link', { name: '文献检索 PubMed' }).click(); await expect(page.getByLabel('任务名称')).toHaveValue(''); await expect(page.getByRole('button', { name: '获取文献与摘要' })).toHaveCount(0)
})
test('search and settings remain within a narrow viewport', async ({ page }) => {
  await models(page, false); await page.setViewportSize({ width: 390, height: 844 })
  for (const path of ['/scan/new', '/settings/models']) { await page.goto(path); await expect(page.getByRole('heading', { level: 1 })).toBeVisible(); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true) }
})
test('existing research charts still render after the dependency upgrade', async ({ page }) => {
  await models(page, false)
  const errors = []; page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/scans/101', route => route.fulfill({ json: { id: 101, topic: '图表回归测试' } }))
  await page.route('**/api/scans/101/categories', route => route.fulfill({ json: [{ id: 1, nameZh: '临床研究', articleCount: 5, rctShare: 0.4 }] }))
  await page.goto('/scan/101'); await expect(page.locator('canvas')).toHaveCount(2); expect(errors).toEqual([])
})
test('article emails have their own column, retain sources and show 未找到 when missing', async ({ page }) => {
  await models(page, false)
  const rows = [{ id: 601, pmid: '123', title: 'Paper with contacts', abstractText: 'Original abstract', emailContacts: [{ email: 'alice@example.edu', authorNames: ['Alice Chen'], sourceUrl: 'https://pubmed.ncbi.nlm.nih.gov/123/', sourceLabel: 'PubMed 作者单位', sourceSnippet: 'University A. alice@example.edu.' }, { email: 'team@example.edu', authorNames: [], sourceUrl: 'https://pubmed.ncbi.nlm.nih.gov/123/', sourceLabel: '文献摘要', sourceSnippet: 'Contact team@example.edu.' }] }, { id: 602, pmid: '456', title: 'Paper without contacts', abstractText: '', emailContacts: [] }]
  await page.route('**/api/scans/101', route => route.fulfill({ json: { id: 101, topic: '联系信息测试', fetchedCount: 2, query: 'trial' } }))
  await page.route('**/api/scans/101/categories', route => route.fulfill({ json: [] }))
  await page.route('**/api/scans/101/articles?**', route => route.fulfill({ json: { content: rows, totalElements: 2 } }))
  await page.goto('/scan/101/articles'); await expect(page.getByRole('columnheader', { name: '作者 / 专家邮箱' })).toBeVisible(); await expect(page.getByText('alice@example.edu', { exact: true })).toBeVisible(); await expect(page.getByText('team@example.edu', { exact: true })).toBeVisible(); await expect(page.getByText('未找到', { exact: true })).toBeVisible(); await expect(page.getByText('归属待核实', { exact: true })).toBeVisible(); await expect(page.getByRole('link', { name: '邮箱来源', exact: true }).first()).toHaveAttribute('href', 'https://pubmed.ncbi.nlm.nih.gov/123/')
})
test('refreshing the current page fills legacy email records without changing the paper', async ({ page }) => {
  await models(page, false); let refreshed = false, requested
  const article = { id: 701, pmid: '123', title: 'Existing original title', abstractText: 'Existing original abstract' }
  await page.route('**/api/scans/101', route => route.fulfill({ json: { id: 101, topic: '补查邮箱测试', fetchedCount: 1, query: 'trial' } }))
  await page.route('**/api/scans/101/categories', route => route.fulfill({ json: [] }))
  await page.route('**/api/scans/101/articles?**', route => route.fulfill({ json: { content: [{ ...article, emailContacts: refreshed ? [{ email: 'verified@example.edu', authorNames: ['Alice Chen'], sourceUrl: 'https://pubmed.ncbi.nlm.nih.gov/123/', sourceLabel: 'PubMed 作者单位' }] : [] }], totalElements: 1 } }))
  await page.route('**/api/scans/101/articles/contacts', route => { requested = route.request().postDataJSON(); refreshed = true; return route.fulfill({ json: { checkedCount: 1, foundCount: 1, missingCount: 0, unavailableCount: 0 } }) })
  await page.goto('/scan/101/articles'); await expect(page.getByText('未找到', { exact: true })).toBeVisible(); await page.getByRole('button', { name: '补查本页邮箱', exact: true }).click(); await expect(page.getByText('verified@example.edu', { exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: /查看标题中文翻译/ })).toContainText('Existing original title'); expect(requested).toEqual({ articleIds: [701] }); await expect(page.getByText('未找到', { exact: true })).toHaveCount(0)
})
