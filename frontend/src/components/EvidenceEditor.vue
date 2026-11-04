<template>
  <el-dialog :model-value="open" :title="titles[type] || '编辑'" width="720px" class="evidence-editor" :close-on-click-modal="false" @update:model-value="$emit('close')">
    <form v-if="draft" id="evidence-edit-form" @submit.prevent="submit">
      <template v-if="type === 'topic'">
        <label class="field-label" for="edit-topic-name">主题名称</label><el-input id="edit-topic-name" v-model="draft.name" maxlength="160" />
        <label class="field-label" for="edit-topic-description">方向范围与边界</label><el-input id="edit-topic-description" type="textarea" :rows="3" v-model="draft.description" maxlength="1500" />
        <div class="field-row"><div><label class="field-label" for="edit-maturity">成熟度判断（人工）</label><el-select id="edit-maturity" v-model="draft.maturity"><el-option v-for="(label,key) in maturityLabels" :key="key" :value="key" :label="label" /></el-select></div><div><label class="field-label" for="edit-topic-state">编辑状态</label><el-select id="edit-topic-state" v-model="draft.editorStatus"><el-option label="待核实" value="DRAFT" /><el-option label="编辑已确认" value="CONFIRMED" /></el-select></div></div>
        <label class="field-label" for="edit-editor-note">判断依据 / 编辑备注</label><el-input id="edit-editor-note" type="textarea" :rows="3" v-model="draft.editorNote" maxlength="4000" />
      </template>
      <template v-if="type === 'note'">
        <div class="field-row"><div><label class="field-label" for="edit-note-kind">笔记类型</label><el-select id="edit-note-kind" v-model="draft.kind"><el-option v-for="(label,key) in noteLabels" :key="key" :value="key" :label="label" /></el-select></div><div><label class="field-label" for="edit-note-state">编辑状态</label><el-select id="edit-note-state" v-model="draft.editorStatus"><el-option label="待核实草稿" value="DRAFT" /><el-option label="编辑已确认" value="CONFIRMED" /></el-select></div></div>
        <label class="field-label" for="edit-note-text">梳理内容</label><el-input id="edit-note-text" type="textarea" :rows="5" v-model="draft.text" maxlength="4000" placeholder="写明具体问题、适用条件，以及现有材料能够支持到什么程度…" />
      </template>
      <template v-if="type === 'coverage'">
        <div class="editor-source-title">{{ sources.find(s => s.pmid === draft.reviewPmid)?.title }}</div>
        <label class="field-label" for="edit-coverage-status">覆盖状态</label><el-select id="edit-coverage-status" v-model="draft.status"><el-option v-for="(label,key) in coverageLabels" :key="key" :value="key" :label="label" /></el-select>
        <p class="field-help">“摘要涉及”仅说明摘要提到该问题。深入、部分或未覆盖，需要编辑核对全文。</p>
        <label class="field-label" for="edit-coverage-rationale">覆盖内容与判断理由</label><el-input id="edit-coverage-rationale" type="textarea" :rows="3" v-model="draft.rationale" maxlength="4000" />
        <template v-if="fulltext"><label class="field-label" for="edit-fulltext-note">全文核对记录</label><el-input id="edit-fulltext-note" type="textarea" :rows="4" v-model="draft.fullTextNote" maxlength="8000" placeholder="记录章节、页码、原文片段及覆盖边界。由编辑提供，系统未自动核对全文。" /><el-checkbox v-model="draft.verifiedFullText">我已核对全文，并记录判断依据</el-checkbox></template>
      </template>
      <template v-if="type === 'review'">
        <div class="editor-source-title">{{ sources.find(s => s.pmid === draft.pmid)?.title }}</div>
        <p class="field-help">由编辑核对后记录；发表日期与综述检索截止日期分别填写。未报告时可明确填写“未报告”。</p>
        <label class="field-label" for="edit-review-cutoff">综述检索截止日期 / 未报告</label><el-input id="edit-review-cutoff" v-model="draft.searchCutoff" maxlength="100" placeholder="例如 2025-12-31；未知时留空" />
        <label class="field-label" for="edit-review-scope">已有综述覆盖了哪些内容</label><el-input id="edit-review-scope" type="textarea" :rows="5" v-model="draft.scopeNote" maxlength="6000" placeholder="研究目的、人群、研究类型、主要问题和讨论范围；附核对章节或原文依据。" />
        <label class="field-label" for="edit-review-limitations">综述边界、局限与尚未涉及的问题</label><el-input id="edit-review-limitations" type="textarea" :rows="4" v-model="draft.limitations" maxlength="6000" />
        <label class="field-label" for="edit-review-status">编辑核对状态</label><el-select id="edit-review-status" v-model="draft.editorStatus"><el-option label="待核实草稿" value="DRAFT" /><el-option label="编辑已确认" value="CONFIRMED" /></el-select>
      </template>
      <template v-if="type === 'candidate'">
        <label class="field-label" for="edit-card-title">拟邀稿标题</label><el-input id="edit-card-title" v-model="draft.title" maxlength="300" />
        <label class="field-label" for="edit-card-question">核心问题</label><el-input id="edit-card-question" type="textarea" :rows="2" v-model="draft.question" maxlength="3000" />
        <label class="field-label" for="edit-card-why">为什么现在值得写</label><el-input id="edit-card-why" type="textarea" :rows="2" v-model="draft.whyNow" maxlength="3000" />
        <label class="field-label" for="edit-card-difference">与既有综述的差异化假设</label><el-input id="edit-card-difference" type="textarea" :rows="3" v-model="draft.difference" maxlength="3000" placeholder="列出最接近的综述和预期增加的内容；未完成查重请写明待核实。" />
        <label class="field-label" for="edit-card-boundary">文章边界与分工</label><el-input id="edit-card-boundary" type="textarea" :rows="2" v-model="draft.boundary" maxlength="3000" />
        <label class="field-label" for="edit-card-outline">建议提纲（每行一项，最多 10 项）</label><el-input id="edit-card-outline" type="textarea" :rows="4" v-model="outline" />
        <div class="field-row"><div><label class="field-label" for="edit-card-status">编辑决策</label><el-select id="edit-card-status" v-model="draft.status"><el-option v-for="(label,key) in decisionLabels" :key="key" :value="key" :label="label" /></el-select></div><div><label class="field-label" for="edit-card-note">决策备注 / 合并去向</label><el-input id="edit-card-note" v-model="draft.editorNote" maxlength="4000" /></div></div>
      </template>
      <section v-if="needsCitations" class="editor-citations">
        <div class="editor-citation-heading"><strong>支撑文献与原文片段</strong><el-button size="small" type="primary" plain @click="addCitation" :disabled="draft.citations.length >= 10">添加引文</el-button></div>
        <p class="field-help">查看原文后填写支持判断的片段。保存时会校验 PMID 和原文；引文存在不等于判断已获验证。</p>
        <div v-for="(citation,i) in draft.citations" :key="i" class="editor-citation-row">
          <label class="field-label" :for="`edit-citation-${i}`">来源文献 {{ i+1 }}</label><el-select :id="`edit-citation-${i}`" v-model="citation.pmid" filterable placeholder="按标题或 PMID 查找文献" @change="citation.quote = ''"><el-option v-for="s in citationSources" :key="s.pmid" :value="s.pmid" :label="`${s.pmid} · ${s.title}`" /></el-select>
          <div class="editor-citation-actions"><el-select v-model="citation.field" :aria-label="`引文 ${i+1} 的原文字段`" @change="citation.quote = ''"><el-option label="摘要" value="abstract" /><el-option label="标题" value="title" /></el-select><button type="button" class="text-button" :disabled="!citation.pmid" @click="$emit('inspect',citation.pmid)">查看原文</button><button type="button" class="text-button" @click="draft.citations.splice(i,1)">移除</button></div>
          <label class="field-label" :for="`edit-quote-${i}`">逐字原文片段 {{ i+1 }}</label><el-input :id="`edit-quote-${i}`" type="textarea" :rows="3" v-model="citation.quote" maxlength="2500" placeholder="粘贴原始标题或摘要中的片段，保留原文语言…" />
        </div>
      </section>
      <div v-if="error || localError" class="inline-error" role="alert">{{ error || localError }}</div>
    </form>
    <template #footer><el-button @click="$emit('close')" :disabled="saving">取消</el-button><el-button type="primary" native-type="submit" form="evidence-edit-form" :loading="saving">保存修改</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { noteLabels, maturityLabels, coverageLabels, decisionLabels } from '../lib/evidence'
const props = defineProps({ open: Boolean, type: String, value: Object, sources: { type: Array, default: () => [] }, saving: Boolean, error: String })
const emit = defineEmits(['close', 'save', 'inspect'])
const draft = ref(null), outline = ref(''), localError = ref('')
const titles = { topic: '编辑方向与边界', note: '编辑梳理笔记', coverage: '核对综述覆盖', review: '盘点已有综述', candidate: '编辑子议题卡' }
watch(() => props.open, open => { if (open) { draft.value = JSON.parse(JSON.stringify(props.value || {})); draft.value.citations ||= []; outline.value = (draft.value.outline || []).join('\n'); localError.value = '' } })
const fulltext = computed(() => ['DEEP', 'PARTIAL', 'NONE'].includes(draft.value?.status))
const needsCitations = computed(() => ['note', 'candidate'].includes(props.type) || (props.type === 'coverage' && draft.value?.status === 'ABSTRACT_MENTION'))
const citationSources = computed(() => props.type === 'coverage' ? props.sources.filter(s => s.pmid === draft.value?.reviewPmid) : props.sources)
function addCitation() { draft.value.citations.push({ pmid: props.type === 'coverage' ? draft.value.reviewPmid : '', field: 'abstract', quote: '' }) }
function submit() {
  localError.value = ''
  if (needsCitations.value && (!draft.value.citations.length || draft.value.citations.some(c => !c.pmid || c.quote.trim().length < 8))) { localError.value = '请至少添加一条来源文献，并填写不少于 8 个字符的逐字原文片段。'; return }
  if (props.type === 'coverage' && fulltext.value && (!draft.value.verifiedFullText || !draft.value.fullTextNote?.trim())) { localError.value = '请先填写全文核对记录，并确认已核对全文。'; return }
  if (props.type === 'candidate') draft.value.outline = outline.value.split('\n').map(s => s.trim()).filter(Boolean)
  emit('save', JSON.parse(JSON.stringify(draft.value)))
}
</script>
<style scoped>
.field-label{margin-top:20px}.el-select{width:100%}.field-row{margin-bottom:4px}.editor-source-title{padding:12px 15px;background:#f7f6fc;line-height:1.8;border-radius:8px}.editor-citations{margin-top:25px;padding-top:20px;border-top:1px solid var(--border)}.editor-citation-heading{display:flex;justify-content:space-between;align-items:center;font-size:13px}.editor-citation-row{margin:15px 0;padding:0 16px 16px;background:#faf9fd;border:1px solid #ece9f4;border-radius:9px}.editor-citation-actions{display:flex;gap:12px;align-items:center;margin-top:10px}.editor-citation-actions .el-select{width:95px}.el-checkbox{margin-top:10px;max-width:100%;white-space:normal;height:auto;line-height:1.8}
</style>
