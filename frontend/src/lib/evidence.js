export const noteLabels = { CONSENSUS: '共识线索', CONTROVERSY: '持续争议', GAP: '空白 / 薄弱环节', CONTEXT: '研究背景' }
export const coverageLabels = { UNVERIFIED: '尚未核实', ABSTRACT_MENTION: '摘要涉及', DEEP: '深入覆盖', PARTIAL: '部分覆盖', NONE: '未覆盖' }
export const maturityLabels = { UNASSESSED: '尚未评估', MATURE: '相对成熟', EMERGING: '新兴方向', FRAGILE: '证据薄弱' }
export const decisionLabels = { PENDING: '待讨论', KEEP: '保留', MERGE: '合并', NARROW: '收窄范围', EVIDENCE_NEEDED: '待补证据', HOLD: '暂缓', REJECT: '淘汰' }
export function elapsedTime(startedAt, now = Date.now()) {
  const start = Date.parse(startedAt)
  if (!Number.isFinite(start)) return ''
  const seconds = Math.max(0, Math.floor((now - start) / 1000))
  return seconds < 60 ? `${seconds} 秒` : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}
export function authorEvidence(topic, sources) {
  const ids = new Set(topic?.pmids || []), authors = new Map()
  for (const paper of sources || []) {
    if (!ids.has(paper.pmid)) continue
    for (const name of [...new Set((paper.authors || '').split(';').map(s => s.trim()).filter(Boolean))]) {
      if (!authors.has(name)) authors.set(name, { name, pmids: [], contacts: [] })
      const author = authors.get(name); author.pmids.push(paper.pmid)
      for (const c of paper.emailContacts || []) if (c.authorNames?.includes(name) && !author.contacts.some(x => x.email === c.email)) author.contacts.push(c)
    }
  }
  return [...authors.values()].sort((a, b) => b.pmids.length - a.pmids.length || a.name.localeCompare(b.name))
}
export function monthlySeries(timeline = []) {
  const counts = new Map(timeline.filter(x => /^\d{4}-(0[1-9]|1[0-2])$/.test(x.month)).map(x => [x.month, x.count]))
  const months = [...counts.keys()].sort(); if (!months.length) return []
  let [year, month] = months[0].split('-').map(Number); const result = []
  while (result.length < 2400) {
    const key = `${year}-${String(month).padStart(2, '0')}`; result.push({ month: key, count: counts.get(key) || 0 })
    if (key >= months.at(-1)) break
    if (++month === 13) { month = 1; year++ }
  }
  return result
}
export function exportNotes(state) {
  const report = state.report || {}, scope = state.scope || {}, source = new Map((report.sources || []).map(s => [s.pmid, s]))
  const refs = citations => (citations || []).map(c => `  - [PMID ${c.pmid} · ${source.get(c.pmid)?.title || '文献'}](https://pubmed.ncbi.nlm.nih.gov/${c.pmid}/)（${c.field === 'title' ? '标题' : '摘要'}）\n\n    > ${c.quote.replaceAll('\n', '\n    > ')}`).join('\n')
  let text = `# ${scope.topic || '领域梳理'}\n\n检索式：${scope.query || ''}\n\n已获取 ${scope.retrievedCount ?? 0} / 命中 ${scope.totalHits ?? 0} 篇。日期：${scope.dateFrom || '不限'} 至 ${scope.dateTo || '不限'}。\n\n版本：${state.revision || 0}；生成时间：${report.generatedAt || ''}。\n\n${report.mode === 'AI' ? `AI 摘要梳理 ${report.aiAnalyzedCount} 篇，模型：${report.model || ''}。${report.sampling || ''}` : '按元数据规则形成主题，尚未作出领域共识或空白判断。'}\n\n所有新颖性及综述全文覆盖判断均需另行核实。\n`
  if (report.aiAnalyzedPmids?.length) text += `\n本轮 AI 实际纳入 PMID：${report.aiAnalyzedPmids.join(', ')}。\n`
  text += '\n## 已有综述盘点\n'
  for (const pmid of report.reviews || []) {
    const s = source.get(pmid), entry = report.reviewInventory?.find(r => r.pmid === pmid)
    text += `\n### [${s?.title || pmid}](https://pubmed.ncbi.nlm.nih.gov/${pmid}/)\n\n发表日期：${s?.pubdate || '未提供'}；检索截止日期：${entry?.searchCutoff || '尚未核实'}。\n\n编辑状态：${entry?.editorStatus === 'CONFIRMED' ? '编辑已确认' : '待核实'}。\n\n覆盖内容：${entry?.scopeNote || '尚未核实'}\n\n局限与边界：${entry?.limitations || '尚未核实'}\n`
  }
  for (const topic of report.topics || []) {
    text += `\n## ${topic.name}\n\n${topic.description || ''}\n\n成熟度：${maturityLabels[topic.maturity] || '尚未评估'}；编辑状态：${topic.editorStatus === 'CONFIRMED' ? '已确认' : '待核实'}。\n${topic.editorNote || ''}\n`
    for (const note of topic.notes || []) text += `\n### ${noteLabels[note.kind] || note.kind} · ${note.editorStatus === 'CONFIRMED' ? '编辑已确认' : '待核实草稿'}\n\n${note.text}\n\n${refs(note.citations)}\n`
    text += '\n### 综述覆盖\n'
    for (const coverage of topic.coverage || []) text += `\n- [${source.get(coverage.reviewPmid)?.title || coverage.reviewPmid}](https://pubmed.ncbi.nlm.nih.gov/${coverage.reviewPmid}/)：${coverageLabels[coverage.status] || '尚未核实'}。${coverage.rationale || ''}${coverage.verifiedFullText ? `；编辑全文核对记录：${coverage.fullTextNote}` : '；全文覆盖待核实'}\n${refs(coverage.citations)}\n`
    text += '\n### 相关作者线索（同名与角色待核实）\n'
    for (const a of authorEvidence(topic, report.sources).slice(0, 12)) text += `\n- ${a.name}；PMID：${a.pmids.join(', ')}；邮箱：${a.contacts.length ? a.contacts.map(c => c.email).join(', ') : '未找到'}\n`
  }
  text += '\n## 候选子议题\n'
  for (const card of report.candidates || []) text += `\n### ${card.title}\n\n状态：${decisionLabels[card.status] || '待讨论'}；新颖性尚未核实。\n\n核心问题：${card.question}\n\n邀稿依据：${card.whyNow}\n\n差异化假设：${card.difference}\n\n文章边界：${card.boundary}\n\n提纲：\n${(card.outline || []).map(s => `- ${s}`).join('\n')}\n\n编辑备注：${card.editorNote || ''}\n\n${refs(card.citations)}\n`
  return text
}
