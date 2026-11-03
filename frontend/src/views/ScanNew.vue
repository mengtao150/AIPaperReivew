<template>
  <div class="page-heading"><div><div class="eyebrow">LITERATURE SEARCH</div><h1>文献检索</h1><p>直接构建 PubMed 检索式，或让 AI 帮你把研究问题变成清晰的检索方案。</p></div><span class="source-tag"><span class="live-dot"></span>PubMed 官方数据源</span></div>
  <div class="search-steps"><span :class="{ current: !run }"><b>01</b>准备检索条件</span><i></i><span :class="{ current: !!run && !fetchDone }"><b>02</b>检查并获取文献</span><i></i><span :class="{ current: fetchDone }"><b>03</b>阅读研究证据</span></div>
  <div class="search-layout">
    <section class="surface search-editor">
      <div class="entry-tabs" role="tablist" aria-label="检索方式"><button id="direct-tab" :class="{ active: mode === 'direct' }" role="tab" :aria-selected="mode === 'direct'" aria-controls="search-inputs" @click="mode = 'direct'"><Icon name="search" :size="17" />直接检索</button><button id="assistant-tab" :class="{ active: mode === 'ai' }" role="tab" :aria-selected="mode === 'ai'" aria-controls="assistant-inputs" @click="mode = 'ai'"><Icon name="spark" :size="17" />AI 辅助构建<span>辅助</span></button></div>
      <div class="surface-body">
        <section v-if="mode === 'ai'" id="assistant-inputs" role="tabpanel" aria-labelledby="assistant-tab" class="assistant-section">
          <div v-if="!activeModel()" class="model-required"><span class="assistant-symbol"><Icon name="spark" :size="20" /></span><div><strong>先连接你的 AI 模型</strong><p>使用自己的密钥，辅助梳理检索范围和检索式。</p><router-link to="/settings/models">前往模型设置 <span>→</span></router-link></div></div>
          <template v-else>
            <div class="assistant-heading"><span><span class="live-dot"></span>{{ activeModel()?.name }}</span><button class="text-button" :disabled="aiBusy" @click="resetConversation">新对话</button></div>
            <div v-if="!messages.length" class="assistant-intro"><span class="assistant-symbol"><Icon name="spark" :size="22" /></span><h3>先说说，你想了解什么？</h3><p>描述研究对象、关注方向或待核实的问题。<br>我会帮助你补齐影响检索范围的条件。</p><button class="example-prompt" @click="aiInput = '我想了解近半年成人2型糖尿病药物治疗与心肾结局相关的原始临床研究，暂不限具体药物。'">例如：成人 2 型糖尿病药物治疗与心肾结局 <Icon name="arrow" :size="13" /></button></div>
            <div v-else class="conversation" aria-live="polite"><div v-for="(message, index) in messages" :key="index" :class="['conversation-message', message.role]"><span>{{ message.role === 'user' ? '你' : '检索助手' }}</span><p>{{ message.content }}</p></div><div v-if="aiBusy" class="assistant-working">正在梳理检索条件…</div></div>
            <div class="assistant-compose"><el-input v-model="aiInput" type="textarea" :rows="3" maxlength="6000" placeholder="描述需求，或回答助手的问题…" :disabled="aiBusy" @keydown.ctrl.enter.prevent="sendMessage" /><div><small>Ctrl + Enter 发送</small><el-button type="primary" size="small" :disabled="!aiInput.trim() || aiBusy" :loading="aiBusy" @click="sendMessage">{{ aiBusy ? '整理中' : '发送' }}<Icon name="arrow" :size="14" /></el-button></div></div>
            <div v-if="aiError" class="inline-error" role="alert">{{ aiError }} <button class="text-button" @click="retryAssistant">重试</button></div>
            <div v-if="aiDraft" class="ai-draft"><span class="pill"><Icon name="spark" :size="12" />方案草稿</span><h3>{{ aiDraft.topic }}</h3><p>{{ aiDraft.summary }}</p><el-button type="primary" plain :disabled="aiBusy || fetching" @click="applyAiDraft">应用到检索条件<Icon name="arrow" :size="14" /></el-button><small>应用后可继续手动修改，系统不会自动开始检索。</small></div>
          </template>
          <div class="editor-divider"><span>检索条件</span></div>
        </section>
        <section id="search-inputs" :role="mode === 'direct' ? 'tabpanel' : undefined" :aria-labelledby="mode === 'direct' ? 'direct-tab' : undefined">
          <div class="form-field"><label class="field-label" for="search-topic">任务名称 <span>便于回看与识别</span></label><el-input id="search-topic" v-model="form.topic" maxlength="200" :disabled="fetching" placeholder="例如：糖尿病药物治疗与心肾结局" /></div>
          <div class="form-field"><div class="field-label"><label for="search-query">PubMed 检索式</label><span class="query-syntax">支持 PubMed 原生语法</span></div><el-input id="search-query" v-model="form.query" :disabled="fetching" type="textarea" :rows="5" maxlength="20000" placeholder='输入关键词或粘贴完整检索式，如：diabetes[tiab] AND cardiovascular[tiab]' /><p class="field-help">可使用 MeSH、字段标签及 AND / OR / NOT。完整检索式会保留原有逻辑。</p></div>
          <div class="filter-heading"><h3>筛选条件</h3><button class="text-button" :disabled="fetching" @click="clearFilters">清空筛选</button></div>
          <div class="form-field"><label class="field-label">时间范围 <span>默认不限定</span></label><div class="date-controls"><el-select v-model="form.dateField" :disabled="fetching" aria-label="日期含义"><el-option label="发表日期" value="dp" /><el-option label="入库日期" value="edat" /><el-option label="记录创建日期" value="crdt" /><el-option label="电子发表日期" value="epdat" /><el-option label="纸质发表日期" value="ppdat" /></el-select><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至" :disabled="fetching" :clearable="true" /></div><div class="date-shortcuts"><button v-for="months in [6, 12, 24]" :key="months" :disabled="fetching" @click="setMonths(months)">近 {{ months === 12 ? '1 年' : months === 24 ? '2 年' : '6 个月' }}</button></div></div>
          <div class="field-row"><div class="form-field"><label class="field-label" for="search-types">文章类型</label><el-select id="search-types" v-model="form.types" multiple collapse-tags collapse-tags-tooltip :disabled="fetching" placeholder="不限文章类型" style="width:100%" clearable><el-option v-for="type in articleTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select></div><div class="form-field"><label class="field-label" for="search-language">文献语言</label><el-select id="search-language" placeholder="不限语言" v-model="form.language" :disabled="fetching" style="width:100%"><el-option label="不限语言" value="" /><el-option label="英语" value="english" /><el-option label="中文" value="chinese" /></el-select></div></div>
          <div class="field-row"><div class="form-field"><label class="field-label" for="search-text">文本可用性</label><el-select id="search-text" v-model="form.textAvailability" :disabled="fetching" style="width:100%"><el-option label="不限" value="any" /><el-option label="有摘要" value="abstract" /><el-option label="有全文链接" value="fulltext" /><el-option label="免费全文" value="freefulltext" /></el-select></div><div class="form-field"><label class="field-label" for="search-sort">检索排序</label><el-select id="search-sort" v-model="form.sortOrder" :disabled="fetching" style="width:100%"><el-option label="最佳匹配 · Best Match" value="relevance" /><el-option label="发表日期 · 最新优先" value="pub_date" /></el-select></div></div>
          <div class="soft-note"><Icon name="info" :size="15" /><span>筛选条件会与上方检索式组合。部分类型与人群筛选依赖标引，可能遗漏尚未完成标引的新记录。</span></div>
        </section>
      </div>
      <div class="search-editor-footer"><span>筛选项均会显示在最终方案中</span><el-button type="primary" :loading="counting" :disabled="!form.query.trim() || !form.topic.trim() || fetching || previewLoading || !!previewError" @click="startSearch"><Icon name="search" :size="16" />试检索并检查结果</el-button></div>
    </section>
    <aside class="search-summary">
      <section class="surface"><div class="surface-header"><h2><Icon name="document" :size="17" />检索方案</h2><span class="pill">{{ run ? '已试检索' : '草稿' }}</span></div><div class="surface-body"><div class="summary-source"><span class="pubmed-mark">P</span><div><strong>PubMed</strong><small>美国国家医学图书馆</small></div></div><dl class="summary-list"><div><dt>时间</dt><dd>{{ form.dateFrom ? `${form.dateFrom} — ${form.dateTo}` : '不限时间' }}</dd></div><div><dt>类型</dt><dd>{{ form.types.length ? form.types.map(t => articleTypes.find(a => a.value === t)?.label || t).join('、') : '不限类型' }}</dd></div><div><dt>语言</dt><dd>{{ { english: '英语', chinese: '中文' }[form.language] || '不限语言' }}</dd></div><div><dt>文本</dt><dd>{{ { any: '不限', abstract: '有摘要', fulltext: '有全文链接', freefulltext: '免费全文' }[form.textAvailability] }}</dd></div></dl><div class="preview-heading"><span>最终提交的检索式</span><button class="text-button" :disabled="!compiledQuery" aria-label="复制最终检索式" @click="copyQuery"><Icon name="copy" :size="13" /></button></div><div class="code-block query-preview">{{ previewLoading ? '正在更新方案…' : compiledQuery || '填写检索式后，这里将展示完整方案。' }}</div><p v-if="previewError" class="inline-error">{{ previewError }}</p><p class="field-help">所有筛选均已计入，试检索将实际访问 PubMed。</p>
        <div v-if="countResult && run" class="count-result" aria-live="polite"><div class="count-label">PubMed 命中</div><div class="count-number">{{ countResult.total.toLocaleString() }}<span>篇文献</span></div><p v-if="countResult.total === 0">暂未找到记录。可减少限制或检查术语。</p><p v-else-if="countResult.total > 10000">本轮最多获取前 10,000 篇。建议缩小范围，或调整排序。</p><p v-else>请检查 PubMed 实际解释，再获取文献。</p><details><summary>查看 PubMed 实际查询解释</summary><div class="code-block">{{ countResult.translation }}</div></details><div v-for="warning in countResult.warnings" :key="warning" class="query-warning">{{ warning }}</div><el-button type="primary" style="width:100%;margin-top:15px" :disabled="countResult.total === 0 || fetching || !snapshotValid" :loading="fetching" @click="fetchArticles">{{ fetching ? '正在获取文献' : `获取${countResult.total > 10000 ? '前 10,000 篇' : '文献与摘要'}` }}<Icon name="arrow" :size="14" /></el-button></div>
        <div v-if="fetching || fetchDone || fetchError" class="fetch-progress" aria-live="polite"><el-progress :percentage="progress" :stroke-width="5" :show-text="false" /><p>{{ progressMessage }}</p><div v-if="fetchError" class="inline-error">{{ fetchError }}<el-button size="small" @click="fetchArticles">重试</el-button></div><el-button v-if="fetchDone" type="primary" style="width:100%;margin-top:10px" @click="$router.push(`/scan/${run.id}/articles`)">阅读文献<Icon name="arrow" :size="14" /></el-button></div>
        <div v-if="searchError" class="inline-error" role="alert">{{ searchError }}</div>
      </div></section>
      <div class="reading-tip"><span class="tip-icon"><Icon name="book" :size="18" /></span><div><strong>为深入阅读做好准备</strong><p>原文标题与摘要完整保留。悬浮查看中文翻译，随时回到原始证据。</p></div></div>
    </aside>
  </div>
</template>
<script setup>
import { reactive, ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import Icon from '../components/WorkbenchIcon.vue'
import api from '../api'
import { activeModel } from '../lib/models'
import { newSearchForm, searchPayload, canFetchSnapshot, errorMessage } from '../lib/search'
const route = useRoute(), form = reactive(newSearchForm()), mode = ref('direct')
const articleTypes = [{ label: '随机对照试验', value: 'randomized controlled trial' }, { label: '临床试验', value: 'clinical trial' }, { label: '观察性研究', value: 'observational study' }, { label: '比较研究', value: 'comparative study' }, { label: '系统综述', value: 'systematic review' }, { label: 'Meta 分析', value: 'meta-analysis' }, { label: '综述', value: 'review' }, { label: '病例报告', value: 'case reports' }]
const dateRange = computed({ get: () => form.dateFrom ? [form.dateFrom, form.dateTo] : null, set: value => { form.dateFrom = value?.[0] || null; form.dateTo = value?.[1] || null } })
const compiledQuery = ref(''), previewLoading = ref(false), previewError = ref(''), counting = ref(false), searchError = ref(''), countResult = ref(null), run = ref(null), snapshot = ref('')
const fetching = ref(false), fetchDone = ref(false), fetchError = ref(''), progress = ref(0), progressMessage = ref('')
const messages = ref([]), aiInput = ref(''), aiBusy = ref(false), aiError = ref(''), aiDraft = ref(null), history = ref([])
const snapshotValid = computed(() => !!snapshot.value && canFetchSnapshot(form, snapshot.value))
let previewTimer, pollTimer, previewController, previewSequence = 0, routeSequence = 0, assistantSequence = 0, countSequence = 0, disposed = false
watch(form, () => {
  countResult.value = null; run.value = null; snapshot.value = ''; searchError.value = ''; fetchDone.value = false; fetchError.value = ''
  compiledQuery.value = ''; previewError.value = ''; clearTimeout(previewTimer); previewController?.abort(); previewSequence++
  if (!form.query.trim()) { previewLoading.value = false; return }
  previewLoading.value = true; const sequence = previewSequence
  previewTimer = setTimeout(async () => {
    previewController = new AbortController()
    try { const { data } = await api.post('/scans/preview', searchPayload(form), { signal: previewController.signal }); if (sequence === previewSequence) compiledQuery.value = data.query }
    catch (error) { if (error.code !== 'ERR_CANCELED' && sequence === previewSequence) previewError.value = errorMessage(error, '暂时无法生成预览，请检查后端服务') }
    finally { if (sequence === previewSequence) previewLoading.value = false }
  }, 350)
}, { deep: true })
function clearFilters() { Object.assign(form, { dateFrom: null, dateTo: null, dateField: 'dp', types: [], language: '', textAvailability: 'any', sortOrder: 'relevance' }) }
function localDate(d) { return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` }
function setMonths(months) { const end = new Date(), start = new Date(end); start.setMonth(start.getMonth() - months); dateRange.value = [localDate(start), localDate(end)] }
async function startSearch() {
  if (counting.value || fetching.value) return
  const payload = searchPayload(form), current = JSON.stringify(payload)
  const request = ++countSequence
  counting.value = true; searchError.value = ''; countResult.value = null; run.value = null
  try { const { data } = await api.post('/scans', payload); if (request !== countSequence || JSON.stringify(searchPayload(form)) !== current) return; run.value = data.run; countResult.value = data.count; compiledQuery.value = data.query; snapshot.value = current }
  catch (error) { if (request === countSequence) searchError.value = errorMessage(error, 'PubMed 试检索失败，请重试') }
  finally { if (request === countSequence) counting.value = false }
}
async function fetchArticles() {
  if (!run.value || !snapshotValid.value || fetching.value) return
  fetching.value = true; fetchDone.value = false; fetchError.value = ''; progress.value = 0; progressMessage.value = '任务已提交，正在准备文献…'; clearTimeout(pollTimer)
  try { await api.post(`/scans/${run.value.id}/fetch`); pollStatus() } catch (error) { fetching.value = false; fetchError.value = errorMessage(error) }
}
async function pollStatus() {
  if (disposed) return
  try {
    const { data } = await api.get(`/scans/${run.value.id}/status`); progress.value = Math.max(0, Math.min(100, Math.round(data.progressPercent || 0))); progressMessage.value = data.message || '正在获取文献…'
    if (['DONE', 'CLASSIFIED'].includes(data.phase)) { fetching.value = false; fetchDone.value = true; progress.value = 100; return }
    if (['FAILED', 'CANCELLED'].includes(data.phase)) { fetching.value = false; fetchError.value = data.error || data.message || '任务未完成，可重试'; return }
  } catch (error) { fetching.value = false; fetchError.value = errorMessage(error, '无法读取进度，可在检索记录中查看任务'); return }
  pollTimer = setTimeout(pollStatus, 1800)
}
async function sendMessage() {
  if (!aiInput.value.trim() || aiBusy.value) return
  const content = aiInput.value.trim(); aiInput.value = ''; messages.value.push({ role: 'user', content }); history.value.push({ role: 'user', content }); await askAssistant()
}
async function askAssistant() {
  const request = ++assistantSequence
  aiBusy.value = true; aiError.value = ''; aiDraft.value = null
  try { const { data } = await api.post('/ai/search-assistant', { messages: history.value, criteria: searchPayload(form) }); if (request !== assistantSequence) return; messages.value.push({ role: 'assistant', content: data.reply }); history.value.push({ role: 'assistant', content: JSON.stringify(data) }); if (data.ready) aiDraft.value = data }
  catch (error) { if (request === assistantSequence) aiError.value = errorMessage(error, '助手暂时无法回答，请重试') }
  finally { if (request === assistantSequence) aiBusy.value = false }
}
function retryAssistant() { if (!aiBusy.value) askAssistant() }
function resetConversation() { assistantSequence++; messages.value = []; history.value = []; aiInput.value = ''; aiDraft.value = null; aiError.value = ''; aiBusy.value = false }
function applyAiDraft() {
  if (fetching.value) return
  form.topic = aiDraft.value.topic || form.topic; form.query = aiDraft.value.query
  const filters = aiDraft.value.filters || {}
  for (const key of ['dateFrom', 'dateTo', 'dateField', 'language', 'textAvailability', 'sortOrder']) if (Object.hasOwn(filters, key)) form[key] = filters[key]
  if (Array.isArray(filters.articleTypes)) form.types = filters.articleTypes
  ElMessage.success('已应用草稿，请检查检索式和全部筛选条件')
}
async function copyQuery() { try { await navigator.clipboard.writeText(compiledQuery.value); ElMessage.success('检索式已复制') } catch { ElMessage.info('可选中检索式后手动复制') } }
async function initializeSearch() {
  const request = ++routeSequence
  countSequence++; previewSequence++; clearTimeout(previewTimer); clearTimeout(pollTimer); previewController?.abort()
  resetConversation(); Object.assign(form, newSearchForm()); mode.value = 'direct'; counting.value = false; fetching.value = false; fetchDone.value = false; fetchError.value = ''; progressMessage.value = ''; progress.value = 0; run.value = null; countResult.value = null; snapshot.value = ''; compiledQuery.value = ''; previewError.value = ''; searchError.value = ''
  if (!/^\d+$/.test(route.query.run || '')) return
  try {
    const { data } = await api.get(`/scans/${route.query.run}`)
    if (request !== routeSequence) return
    Object.assign(form, { topic: data.topic, query: data.sourceQuery || data.query, dateFrom: data.sourceQuery ? data.dateFrom || null : null, dateTo: data.sourceQuery ? data.dateTo || null : null, dateField: data.dateField || 'dp', types: data.sourceQuery ? data.articleTypes?.split('|').filter(Boolean) || [] : [], language: data.sourceQuery ? data.language || '' : '', textAvailability: data.textAvailability || 'any', sortOrder: data.sortOrder || 'relevance' })
    await nextTick()
    if (data.status === 'COUNTED') { run.value = data; snapshot.value = JSON.stringify(searchPayload(form)); countResult.value = { total: data.totalHits || 0, translation: data.queryTranslation || data.query, warnings: JSON.parse(data.searchWarnings || '[]') } }
  }
  catch (error) { if (request === routeSequence) searchError.value = errorMessage(error, '无法读取历史方案') }
}
onMounted(initializeSearch)
watch(() => route.query.run, initializeSearch)
onBeforeUnmount(() => { disposed = true; clearTimeout(previewTimer); clearTimeout(pollTimer); previewController?.abort() })
</script>
<style scoped>
.search-steps{display:flex;align-items:center;gap:18px;margin:7px 0 26px;font-size:10px;color:#a2a8b9}.search-steps span{display:flex;align-items:center;gap:8px;white-space:nowrap}.search-steps b{display:grid;place-items:center;width:21px;height:21px;border-radius:50%;background:#edeff5;color:#a3a9b9;font-size:9px;font-weight:500}.search-steps i{height:1px;background:#e6e8f1;width:48px}.search-steps .current{color:#7f6fbe}.search-steps .current b{background:#eae4fd;color:#7b66bf}.search-layout{display:grid;grid-template-columns:minmax(0,1fr) 322px;gap:24px;align-items:start}.entry-tabs{display:flex;border-bottom:1px solid var(--border);padding:0 24px;gap:30px}.entry-tabs button{display:flex;align-items:center;gap:8px;background:none;border:0;border-bottom:2px solid transparent;padding:20px 2px 18px;color:#9297aa;font-size:12px}.entry-tabs button.active{border-color:var(--accent);color:var(--accent);font-weight:600}.entry-tabs button>span{font-size:8px;padding:2px 4px;background:#f2edff;color:#9c87c3;border-radius:3px}.search-editor>.surface-body{padding:27px}.query-syntax{margin-left:auto}.filter-heading{display:flex;justify-content:space-between;align-items:center;margin:29px 0 20px;border-top:1px solid #eff0f5;padding-top:21px}.filter-heading h3{font-size:12px;font-weight:600;margin:0}.date-controls{display:flex;gap:10px}.date-controls>.el-select{width:124px;flex:0 0 124px}.date-controls :deep(.el-date-editor){width:100%;min-width:0;height:39px}.date-shortcuts{display:flex;gap:8px;margin-top:9px}.date-shortcuts button{border:1px solid #e9eaf1;border-radius:4px;background:#fff;color:#969bb0;font-size:9px;padding:4px 8px}.date-shortcuts button:hover{border-color:#bfb4ee;color:var(--accent)}.search-editor-footer{padding:20px 26px;display:flex;justify-content:space-between;align-items:center;gap:12px;border-top:1px solid var(--border);background:#fdfdff;border-radius:0 0 12px 12px}.search-editor-footer>span{font-size:9px;color:#a0a6b6}.search-summary{position:sticky;top:24px}.search-summary .surface-body{padding:23px 20px}.summary-source{display:flex;align-items:center;gap:10px;margin-bottom:22px}.pubmed-mark{font-family:Georgia,serif;font-size:25px;font-weight:bold;color:#7489b0;background:#edf2fa;display:grid;place-items:center;width:36px;height:38px;border-radius:8px}.summary-source strong{font-size:12px;font-weight:600}.summary-source small{display:block;font-size:9px;color:#9ea5b5;margin-top:4px}.summary-list{margin:0 0 22px}.summary-list>div{display:flex;gap:12px;margin:13px 0;font-size:11px}.summary-list dt{color:#a1a7b6;flex:0 0 30px}.summary-list dd{color:#747e94;margin:0;text-align:right;flex:1;font-size:10px;line-height:1.7}.preview-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;font-size:10px;color:#929bad}.query-preview{min-height:125px;font-size:10px;max-height:300px;overflow:auto}.reading-tip{display:flex;gap:11px;padding:23px 5px;font-size:11px}.tip-icon{color:#b4a8d5;margin-top:2px}.reading-tip strong{font-weight:500;color:#89849e}.reading-tip p{line-height:1.9;color:#a2a6b5;font-size:10px;margin:7px 0 0}.count-result{margin-top:23px;padding-top:20px;border-top:1px solid var(--border)}.count-label{font-size:10px;color:#929bad}.count-number{font-size:33px;letter-spacing:-1px;color:#4e477a;margin:8px 0}.count-number span{font-size:10px;color:#a0a4b5;margin-left:9px;letter-spacing:0}.count-result>p{font-size:10px;line-height:1.8;color:#9298aa}.count-result details{font-size:10px;color:#8b80ad;margin-top:12px}.count-result summary{cursor:pointer}.count-result details .code-block{margin-top:10px;font-size:9px;max-height:220px;overflow:auto}.query-warning{font-size:10px;line-height:1.7;background:#fff8ec;padding:9px;margin-top:8px;border-radius:5px;color:#ab8955}.fetch-progress{margin-top:18px}.fetch-progress>p{font-size:10px;line-height:1.8;color:#939bae}.assistant-section{margin-bottom:25px}.assistant-symbol{display:grid;place-items:center;background:#f1edff;color:#9a81c6;width:43px;height:43px;border-radius:13px}.assistant-intro{text-align:center;padding:12px 0 20px}.assistant-intro .assistant-symbol{margin:0 auto 13px}.assistant-intro h3{font-size:15px;font-weight:600;margin:0 0 9px}.assistant-intro p{font-size:11px;color:#939aad;line-height:1.9;margin:0 0 18px}.example-prompt{display:inline-flex;gap:14px;align-items:center;font-size:10px;background:#faf8ff;border:1px solid #ede7f9;color:#9283b0;padding:10px 12px;border-radius:7px}.assistant-heading{display:flex;justify-content:space-between;font-size:10px;color:#9590a7;margin-bottom:12px}.assistant-heading>span{display:flex;align-items:center;gap:6px}.assistant-compose{border:1px solid #e4dfef;border-radius:9px;padding:4px;background:#fff}.assistant-compose :deep(.el-textarea__inner){box-shadow:none!important;resize:none}.assistant-compose>div:last-child{display:flex;justify-content:space-between;padding:5px 8px;align-items:center}.assistant-compose small{font-size:9px;color:#b0aabd}.assistant-compose .el-button{height:30px}.conversation{max-height:350px;overflow:auto;scrollbar-width:thin;margin-bottom:15px;padding-right:5px}.conversation-message{margin-bottom:16px}.conversation-message>span{font-size:9px;color:#a9a0ba}.conversation-message>p{white-space:pre-wrap;font-size:12px;line-height:1.9;color:#7b748b;margin:6px 0 0;padding:12px 14px;background:#f6f3fc;border-radius:2px 10px 10px}.conversation-message.user{margin-left:30px}.conversation-message.user>span{display:block;text-align:right}.conversation-message.user>p{background:#f5f7fb;color:#7d879d;border-radius:10px 2px 10px 10px}.assistant-working{color:#9b8fba;font-size:11px;padding:5px}.ai-draft{margin-top:18px;background:#fbfaff;border:1px solid #e7e0f6;border-radius:9px;padding:17px}.ai-draft h3{font-size:13px;line-height:1.8}.ai-draft p{font-size:11px;color:#9290a5;line-height:1.9}.ai-draft small{display:block;font-size:9px;color:#aba5b8;margin-top:10px}.editor-divider{position:relative;border-top:1px solid var(--border);margin:30px 0}.editor-divider span{position:absolute;top:-8px;background:white;padding:0 10px 0 0;font-size:10px;color:#aaa4b7}.model-required{display:flex;gap:12px;background:#fbfaff;padding:20px;border-radius:9px}.model-required strong{font-size:12px;color:#7e709b}.model-required p{font-size:10px;color:#a19ab0;line-height:1.8}.model-required a{font-size:11px}@media(max-width:1180px){.search-layout{grid-template-columns:minmax(0,1fr)}.search-summary{position:static;display:grid;grid-template-columns:1fr}.query-preview{min-height:70px}.reading-tip{display:none}}@media(max-width:760px){.search-editor>.surface-body{padding:20px}.search-steps{gap:8px;font-size:8px}.search-steps i{width:13px}.search-editor-footer{padding:18px;flex-wrap:wrap}.search-editor-footer>span{display:none}.search-editor-footer .el-button{width:100%}.date-controls{flex-direction:column}.date-controls>.el-select{width:100%;flex:auto}.entry-tabs{gap:24px;padding:0 20px}.example-prompt{font-size:9px;text-align:left}}
</style>
<style scoped>
.search-editor-footer{position:sticky;bottom:0;z-index:3;box-shadow:0 -4px 12px #33325804}.assistant-compose small,.summary-list dt,.field-help{color:#828599}.summary-list dd{color:#647089}.query-preview{color:#68728a}
</style>
