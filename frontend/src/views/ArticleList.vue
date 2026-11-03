<template>
  <div class="page-heading"><div><div class="eyebrow">RESEARCH LIBRARY</div><h1>{{ scan?.topic || '检索文献' }}</h1><p>完整保留原始标题与摘要，悬浮或聚焦内容即可查看中文翻译。</p></div><el-button @click="$router.push(`/scan/new?run=${id}`)"><Icon name="edit" :size="15" />编辑检索方案</el-button></div>
  <div class="library-summary"><span class="summary-item"><Icon name="document" :size="17" /><strong>{{ scan?.fetchedCount ?? total }}</strong>篇已获取文献</span><span class="summary-item"><span class="live-dot"></span>PubMed</span><span class="summary-item">{{ scan?.dateFrom ? `${scan.dateFrom} — ${scan.dateTo}` : '不限时间' }}</span><button class="text-button" @click="showQuery = !showQuery"><Icon name="search" :size="13" />{{ showQuery ? '收起' : '查看' }}检索式</button><span class="translation-hint"><Icon name="translate" :size="15" />悬浮查看中文</span></div>
  <div v-if="showQuery" class="code-block saved-query">{{ scan?.query }}</div>
  <div v-if="taskStatus" class="task-banner" aria-live="polite"><span><Icon name="info" :size="16" />{{ taskStatus.message }}</span><div v-if="['FETCHING','PAUSED','CLASSIFYING'].includes(taskStatus.phase)"><el-button v-if="taskStatus.phase === 'PAUSED'" size="small" @click="controlTask('resume')">恢复获取</el-button><el-button v-else-if="taskStatus.phase === 'FETCHING'" size="small" @click="controlTask('pause')">暂停</el-button><el-button size="small" @click="controlTask('cancel')">取消任务</el-button></div></div>
  <div v-if="!activeModel()" class="library-model-note"><Icon name="spark" :size="16" /><span>连接模型，即可在阅读时查看标题和摘要的中文译文。</span><router-link to="/settings/models">配置模型 →</router-link></div>
  <section class="surface library-surface">
    <div class="surface-header"><h2>文献列表 <span class="result-count">{{ total.toLocaleString() }}</span></h2><div class="library-filters"><el-button :loading="refreshingEmails" :disabled="loading || refreshingEmails || !articles.length" @click="refreshEmails"><Icon v-if="!refreshingEmails" name="connection" :size="14" />补查本页邮箱</el-button><el-select v-if="categories.length" v-model="selectedCat" :disabled="refreshingEmails" placeholder="全部分类" clearable aria-label="文献分类" style="width:165px" @change="resetAndLoad"><el-option v-for="c in categories" :key="c.id" :label="c.nameZh" :value="c.id" /></el-select><el-select v-model="sort" :disabled="refreshingEmails" style="width:155px" aria-label="文献排序" @change="resetAndLoad"><el-option label="检索顺序" value="relevance" /><el-option label="发表日期 · 最新优先" value="pub_date" /></el-select></div></div>
    <div class="email-source-note"><Icon name="info" :size="13" /><span>{{ emailRefreshSummary || '邮箱取自文献公开信息，保留来源与作者关联；未发现时显示“未找到”。旧文献可补查本页。' }}</span></div>
    <div v-if="emailRefreshError" class="library-error inline-error" role="alert">{{ emailRefreshError }}<button class="text-button" :disabled="refreshingEmails" @click="refreshEmails">重试补查</button></div>
    <div v-if="error" class="library-error inline-error" role="alert">{{ error }}<button class="text-button" @click="load">重新加载</button></div>
    <el-table v-loading="loading" :data="articles" row-key="id" :empty-text="loading ? '正在加载文献…' : '当前筛选下没有文献'" table-layout="fixed" style="width:100%">
      <el-table-column label="标题 / TITLE" min-width="290"><template #default="{ row }"><span v-if="row.isRetracted" class="pill retracted">已撤稿</span><TranslatedText :article-id="row.id" field="title" :text="row.title" /><div class="article-authors" :title="row.authors">{{ row.authors || '作者信息未提供' }}</div><div class="article-identifiers"><a :href="`https://pubmed.ncbi.nlm.nih.gov/${row.pmid}/`" target="_blank" rel="noopener noreferrer">PMID: {{ row.pmid }}<Icon name="external" :size="10" /></a><button class="text-button detail-link" @click="openArticle(row)">展开阅读</button></div></template></el-table-column>
      <el-table-column label="摘要 / ABSTRACT" min-width="330"><template #default="{ row }"><TranslatedText :article-id="row.id" field="abstract" :text="row.abstractText" /></template></el-table-column>
      <el-table-column label="作者 / 专家邮箱" min-width="230"><template #default="{ row }"><ArticleEmails :contacts="row.emailContacts" /></template></el-table-column>
      <el-table-column label="发表信息" width="195"><template #default="{ row }"><div class="article-journal">{{ row.journalAbbrev || row.journal || '期刊未提供' }}</div><div class="article-date">{{ row.pubdate || '日期未提供' }}</div><div class="article-types">{{ row.pubtypes || '类型未提供' }}</div><a v-if="row.doi" :href="`https://doi.org/${encodeURI(row.doi)}`" target="_blank" rel="noopener noreferrer" class="article-doi">DOI<Icon name="external" :size="10" /></a></template></el-table-column>
    </el-table>
    <div class="library-pagination"><span>每页 <el-select v-model="pageSize" :disabled="refreshingEmails" aria-label="每页条数" style="width:74px" @change="resetAndLoad"><el-option v-for="n in [10, 20, 50]" :key="n" :label="`${n} 篇`" :value="n" /></el-select><span class="pagination-total">共 {{ total.toLocaleString() }} 篇</span></span><el-pagination layout="prev, pager, next" :disabled="refreshingEmails" :total="total" :page-size="pageSize" v-model:current-page="currentPage" :pager-count="5" @current-change="load" /></div>
  </section>
  <el-drawer v-model="drawer" title="文献阅读" size="min(1000px, 96vw)" destroy-on-close>
    <template v-if="selectedArticle"><div class="reading-top"><span class="pill">{{ selectedArticle.journalAbbrev || 'PubMed' }}</span><span>{{ selectedArticle.pubdate }}</span><a :href="`https://pubmed.ncbi.nlm.nih.gov/${selectedArticle.pmid}/`" target="_blank" rel="noopener noreferrer">查看 PubMed 原文<Icon name="external" :size="12" /></a></div><h2 class="reading-title">{{ selectedArticle.title }}</h2><p class="reading-authors">{{ selectedArticle.authors }}</p><p v-if="selectedArticle.doi" class="reading-doi">DOI: {{ selectedArticle.doi }}</p><section class="reading-contacts"><div class="reading-label">作者 / 专家邮箱 <span>公开来源</span></div><ArticleEmails :contacts="selectedArticle.emailContacts" expanded /></section><div class="reading-columns"><section><div class="reading-label">原文摘要 <span>ORIGINAL</span></div><div class="reading-abstract">{{ selectedArticle.abstractText || 'PubMed 未提供摘要。请通过原始链接查看可获取的全文。' }}</div></section><section><div class="reading-label">中文阅读 <span>机器翻译 · 请对照原文</span></div><h4 class="translated-section-label">标题</h4><TranslatedText :article-id="selectedArticle.id" field="title" :text="selectedArticle.title" block /><h4 class="translated-section-label">摘要</h4><TranslatedText :article-id="selectedArticle.id" field="abstract" :text="selectedArticle.abstractText" block /></section></div></template>
  </el-drawer>
</template>
<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import Icon from '../components/WorkbenchIcon.vue'
import TranslatedText from '../components/TranslatedText.vue'
import ArticleEmails from '../components/ArticleEmails.vue'
import { activeModel } from '../lib/models'
import { errorMessage } from '../lib/search'
const props = defineProps({ id: String })
const articles = ref([]), categories = ref([]), scan = ref(null), total = ref(0), currentPage = ref(1), pageSize = ref(20), selectedCat = ref(null), sort = ref('relevance'), loading = ref(false), error = ref(''), showQuery = ref(false), drawer = ref(false), selectedArticle = ref(null)
const taskStatus = ref(null)
const refreshingEmails = ref(false), emailRefreshError = ref(''), emailRefreshSummary = ref('')
let controller, sequence = 0, taskTimer, disposed = false
async function load() {
  controller?.abort(); controller = new AbortController(); const request = ++sequence; loading.value = true; error.value = ''
  try { const params = { page: currentPage.value - 1, size: pageSize.value, sort: sort.value }; if (selectedCat.value) params.categoryId = selectedCat.value; const { data } = await api.get(`/scans/${props.id}/articles`, { params, signal: controller.signal }); if (request !== sequence) return; articles.value = data.content; total.value = data.totalElements }
  catch (e) { if (e.code !== 'ERR_CANCELED' && request === sequence) { articles.value = []; error.value = errorMessage(e, '文献加载失败，请重试') } }
  finally { if (request === sequence) loading.value = false }
}
function resetAndLoad() { currentPage.value = 1; load() }
async function initialize() {
  clearTimeout(taskTimer); taskStatus.value = null
  emailRefreshError.value = ''; emailRefreshSummary.value = ''
  selectedCat.value = null; currentPage.value = 1; articles.value = []; scan.value = null; total.value = 0; categories.value = []
  try { const { data } = await api.get(`/scans/${props.id}`); scan.value = data; sort.value = data.sortOrder ? 'relevance' : 'pub_date'; if (['FETCHING','CLASSIFYING','PAUSED'].includes(data.status)) pollTask() } catch (e) { error.value = errorMessage(e, '检索记录不存在或暂时不可用') }
  await load()
  try { const { data } = await api.get(`/scans/${props.id}/categories`); categories.value = data } catch { categories.value = [] }
}
function openArticle(article) { selectedArticle.value = article; drawer.value = true }
async function refreshEmails() {
  if (refreshingEmails.value || !articles.value.length) return
  const scanId = props.id, articleIds = articles.value.map(article => article.id)
  refreshingEmails.value = true; emailRefreshError.value = ''; emailRefreshSummary.value = ''
  try {
    const { data } = await api.post(`/scans/${scanId}/articles/contacts`, { articleIds })
    if (props.id !== scanId) return
    await load()
    if (selectedArticle.value) { const updated = articles.value.find(article => article.id === selectedArticle.value.id); if (updated) selectedArticle.value = updated }
    emailRefreshSummary.value = `已补查 ${data.checkedCount} 篇文献，${data.foundCount} 篇找到邮箱，${data.missingCount} 篇未找到${data.unavailableCount ? `；另有 ${data.unavailableCount} 篇未返回元数据，可重试` : ''}。来源：PubMed 公开作者信息与摘要。`
    ElMessage.success('本页邮箱信息已更新')
  } catch (e) { emailRefreshError.value = errorMessage(e, '邮箱补查失败，请稍后重试') }
  finally { refreshingEmails.value = false }
}
async function pollTask() {
  if (disposed) return
  try {
    const { data } = await api.get(`/scans/${props.id}/status`); taskStatus.value = data
    if (['DONE','CLASSIFIED','FAILED','CANCELLED','INTERRUPTED'].includes(data.phase)) { await load(); try { const result = await api.get(`/scans/${props.id}`); scan.value = result.data } catch { } if (['DONE','CLASSIFIED'].includes(data.phase)) taskStatus.value = null; return }
    taskTimer = setTimeout(pollTask, 2000)
  } catch { taskStatus.value = { phase: 'ERROR', message: '进度暂时不可用，请刷新页面查看任务状态。' } }
}
async function controlTask(action) { try { await api.post(`/scans/${props.id}/${action}`); clearTimeout(taskTimer); pollTask() } catch (e) { ElMessage.error(errorMessage(e)) } }
onMounted(initialize); watch(() => props.id, initialize); onBeforeUnmount(() => { disposed = true; controller?.abort(); clearTimeout(taskTimer) })
</script>
<style scoped>
.library-summary{display:flex;align-items:center;gap:22px;font-size:10px;color:#9ca3b4;padding:0 0 21px;flex-wrap:wrap}.summary-item{display:flex;align-items:center;gap:7px}.summary-item strong{color:#6e6d8a;font-weight:600;font-size:14px}.summary-item>svg{color:#a79acb}.translation-hint{display:flex;gap:6px;align-items:center;margin-left:auto;color:#a59aba;font-size:10px}.saved-query{margin-bottom:20px}.library-model-note{display:flex;align-items:center;gap:8px;font-size:11px;background:#f0ecfc;color:#9485b1;border:1px solid #e9e2f8;border-radius:8px;padding:12px 15px;margin:0 0 20px}.library-model-note>a{margin-left:auto;white-space:nowrap;font-size:10px}.result-count{font-size:10px;font-weight:400;color:#a69abb;background:#f4f1fb;padding:3px 7px;border-radius:4px}.library-filters{display:flex;gap:10px}.article-authors{color:#a8aebd;font-size:9px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:9px}.article-identifiers{display:flex;align-items:center;gap:15px;margin-top:8px}.article-identifiers>a{display:inline-flex;align-items:center;gap:4px;font-size:9px;color:#a09aba}.detail-link{font-size:9px;padding:0;color:#9384b4}.article-journal{font-size:11px;font-weight:500;color:#7c8399;line-height:1.8}.article-date{font-size:10px;color:#a1a8b9;margin-top:5px}.article-types{font-size:9px;color:#b1b7c5;line-height:1.8;margin-top:7px}.article-doi{display:inline-flex;align-items:center;gap:4px;font-size:9px;color:#a69bbb;margin-top:8px}.library-pagination{display:flex;justify-content:space-between;align-items:center;padding:18px 22px;gap:14px}.library-pagination>span{display:flex;align-items:center;gap:7px;font-size:10px;color:#a1a7b7}.pagination-total{margin-left:7px;color:#b2b6c3}.library-pagination :deep(.el-select__wrapper){min-height:29px!important;font-size:10px}.library-pagination :deep(.el-pager li){font-size:11px;min-width:28px}.library-error{margin:15px 22px}.retracted{background:#fff1ed;color:#bd7467;margin-bottom:7px}.reading-top{display:flex;align-items:center;gap:12px;color:#a2a8b8;font-size:11px}.reading-top>a{margin-left:auto;display:flex;align-items:center;gap:5px}.reading-title{font-size:21px;line-height:1.6;color:#414760;font-weight:600;margin:23px 0 13px}.reading-authors,.reading-doi{font-size:11px;line-height:1.9;color:#a0a6b7}.reading-columns{display:grid;grid-template-columns:1fr 1fr;gap:30px;margin-top:30px}.reading-columns>section+section{border-left:1px solid var(--border);padding-left:25px}.reading-label{font-size:12px;font-weight:600;color:#6e708c;padding-bottom:15px;margin-bottom:18px;border-bottom:1px solid var(--border);display:flex;justify-content:space-between;align-items:center}.reading-label>span{font-size:8px;font-weight:400;color:#b0a4c1}.reading-abstract{font-size:12px;line-height:2;color:#8a91a5;white-space:pre-wrap;overflow-wrap:anywhere}.translated-section-label{font-size:10px;font-weight:500;color:#aaa0bb;margin:24px 0 10px}.translated-section-label:first-of-type{margin-top:0}@media(max-width:1000px){.translation-hint{display:none}.library-filters{gap:7px}.library-surface>.surface-header{flex-wrap:wrap}}@media(max-width:760px){.library-summary{gap:12px}.library-model-note{flex-wrap:wrap}.library-model-note>a{margin-left:24px}.page-heading{flex-wrap:wrap}.reading-columns{grid-template-columns:1fr}.reading-columns>section+section{border:0;padding-left:0}.library-pagination{flex-wrap:wrap}.library-filters{width:100%}.library-filters>.el-select{flex:1}.pagination-total{display:none}}
</style>
<style scoped>
.article-authors{color:#7d879b;font-size:11px}.article-identifiers>a,.detail-link{font-size:10px;color:#8a7aab}.article-journal{font-size:12px;color:#5f6d85}.article-date{font-size:11px;color:#7c879d}.article-types{font-size:10px;color:#8a94a7}.reading-abstract{font-size:13px;color:#626f87}.reading-authors,.reading-doi{color:#77839a}
.task-banner{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:13px 16px;background:#fff9ee;border:1px solid #f2e8d5;border-radius:8px;margin-bottom:18px;font-size:12px;color:#9b8259}.task-banner>span{display:flex;gap:7px;align-items:center}.task-banner .el-button{height:29px;font-size:10px}
.library-filters{flex-wrap:wrap;justify-content:flex-end}.email-source-note{display:flex;align-items:flex-start;gap:6px;padding:10px 22px;background:#fcfbff;color:#9586ad;font-size:10px;line-height:1.8;border-bottom:1px solid #efedf6}.email-source-note svg{margin-top:3px}.reading-contacts{margin:26px 0}.reading-contacts .reading-label{margin-bottom:13px}
</style>
