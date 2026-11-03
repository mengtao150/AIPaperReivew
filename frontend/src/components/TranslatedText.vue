<template>
  <span v-if="!text?.trim()" class="missing-abstract">{{ field === 'abstract' ? 'PubMed 未提供摘要' : '无标题' }}</span>
  <div v-else-if="block" class="translation-block">
    <div v-if="entry.status === 'ready'" class="translation-body">{{ entry.text }}</div>
    <div v-else-if="entry.status === 'loading'" class="translation-status"><span class="translation-pulse"></span>正在翻译完整{{ field === 'title' ? '标题' : '摘要' }}…</div>
    <div v-else-if="entry.status === 'error'" class="translation-error">{{ entry.error }}<button class="text-button" @click="load(true)">重试</button></div>
    <div v-else-if="!activeModel()" class="translation-status">连接模型后可查看中文译文。<router-link to="/settings/models">配置模型 →</router-link></div>
    <div v-else class="translation-status"><el-button size="small" plain @click="load()"><Icon name="translate" :size="14" />翻译为中文</el-button><span>使用你配置的模型</span></div>
  </div>
  <el-popover v-else :visible="visible" placement="bottom-start" :width="520" :popper-style="{ maxWidth: 'calc(100vw - 40px)', padding: '19px 21px', borderRadius: '10px' }" :teleported="true" @before-enter="load()">
    <template #reference><button :class="['translated-reference', field]" :aria-label="`查看${field === 'title' ? '标题' : '摘要'}中文翻译：${text.slice(0, 80)}`" :aria-expanded="visible" @mouseenter="scheduleShow" @mouseleave="scheduleHide" @focus="open" @blur="scheduleHide" @click="open" @keydown.esc="visible = false"><span>{{ text }}</span></button></template>
    <div @mouseenter="keepOpen" @mouseleave="scheduleHide" @focusin="keepOpen" @focusout="scheduleHide" @keydown.esc="visible = false">
      <div class="translation-header"><span><Icon name="translate" :size="15" />中文{{ field === 'title' ? '标题' : '摘要' }}</span><span class="machine-label">机器翻译 · 请对照原文</span></div>
      <div v-if="entry.status === 'ready'" class="translation-body hover-body">{{ entry.text }}</div>
      <div v-else-if="entry.status === 'loading'" class="translation-status"><span class="translation-pulse"></span>正在翻译，请稍候…</div>
      <div v-else-if="entry.status === 'error'" class="translation-error">{{ entry.error }}<button class="text-button" @click="load(true)">重试</button></div>
      <div v-else-if="!activeModel()" class="translation-status">连接模型后可查看中文译文。<router-link to="/settings/models">配置模型 →</router-link></div>
      <div v-else-if="entry.status === 'missing'" class="translation-status">原始记录未提供内容。</div>
      <div v-else class="translation-status"><button class="text-button" @click="load()">加载中文翻译</button></div>
      <div v-if="entry.model" class="translation-model">{{ entry.model }} · 译文已缓存</div>
    </div>
  </el-popover>
</template>
<script setup>
import { computed, ref, onBeforeUnmount } from 'vue'
import Icon from './WorkbenchIcon.vue'
import { activeModel } from '../lib/models'
import { translationEntry, loadTranslation } from '../lib/translations'
const props = defineProps({ articleId: Number, field: String, text: String, block: Boolean })
const visible = ref(false), entry = computed(() => translationEntry(props.articleId, props.field))
let showTimer, hideTimer
function open() { clearTimeout(showTimer); clearTimeout(hideTimer); visible.value = true; load() }
function scheduleShow() { clearTimeout(hideTimer); showTimer = setTimeout(open, 450) }
function scheduleHide() { clearTimeout(showTimer); clearTimeout(hideTimer); hideTimer = setTimeout(() => visible.value = false, 180) }
function keepOpen() { clearTimeout(hideTimer) }
function load(retry = false) { if (props.text?.trim()) loadTranslation(props.articleId, props.field, retry) }
onBeforeUnmount(() => { clearTimeout(showTimer); clearTimeout(hideTimer) })
</script>
<style scoped>
.translated-reference{display:block;text-align:left;width:100%;padding:0;background:none;border:0;color:inherit;line-height:inherit;border-radius:3px}.translated-reference>span{display:-webkit-box;-webkit-box-orient:vertical;overflow:hidden;white-space:pre-wrap}.translated-reference.title{font-size:12px;font-weight:600;line-height:1.9;color:#434b67}.translated-reference.title>span{-webkit-line-clamp:3}.translated-reference.abstract{font-size:11px;font-weight:400;line-height:1.9;color:#8890a5}.translated-reference.abstract>span{-webkit-line-clamp:4}.translated-reference:hover{color:var(--accent)}.translation-header{display:flex;justify-content:space-between;gap:12px;padding-bottom:13px;margin-bottom:12px;border-bottom:1px solid #efedf6;align-items:center}.translation-header>span:first-child{display:flex;align-items:center;gap:7px;font-size:12px;font-weight:600;color:#796599}.machine-label{font-size:9px;color:#aaa2b5}.translation-body{font-size:12px;line-height:2;white-space:pre-wrap;color:#6e7187;overflow-wrap:anywhere}.hover-body{max-height:360px;overflow-y:auto;padding-right:5px;scrollbar-width:thin}.translation-status{font-size:11px;line-height:1.9;color:#a2a0af;display:flex;gap:8px;flex-wrap:wrap;align-items:center;padding:8px 0}.translation-status>span:last-child{font-size:10px}.translation-error{font-size:11px;line-height:1.8;color:#b67b79}.translation-model{font-size:9px;color:#b2aabd;border-top:1px solid #f1eef7;margin-top:13px;padding-top:10px}.translation-pulse{width:7px;height:7px;background:#b6a3dd;border-radius:50%;animation:pulse 1.1s infinite alternate}.missing-abstract{font-size:11px;color:#b6bac7;font-style:italic}.translation-block{min-height:55px}@keyframes pulse{to{opacity:.3}}
</style>
<style scoped>
.translated-reference.title{font-size:14px;color:#3d4761;line-height:1.8}.translated-reference.abstract{font-size:12px;color:#657188;line-height:1.9}.translation-body{font-size:13px;color:#59657b}.machine-label{color:#82788f}.translation-status{color:#7c778a}.translation-model{color:#82788f}
</style>
