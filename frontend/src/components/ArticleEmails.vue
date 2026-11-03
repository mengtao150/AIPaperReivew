<template>
  <div v-if="!items.length" class="email-not-found" title="当前文献记录中未找到公开邮箱，可补查本页作者信息。">未找到</div>
  <div v-else :class="['publication-emails', { expanded }]">
    <div v-for="contact in visibleItems" :key="contact.email.toLowerCase()" class="publication-email">
      <div class="email-address-row"><span class="contact-address">{{ contact.email }}</span><button class="copy-email" :aria-label="`复制邮箱 ${contact.email}`" title="复制邮箱" @click="copy(contact.email)"><Icon name="copy" :size="12" /></button></div>
      <div class="contact-author" :title="contact.authorNames?.join('；') || '原始信息未明确邮箱归属'">{{ contact.authorNames?.length ? `作者关联：${contact.authorNames.join('；')}` : '归属待核实' }}</div>
      <a v-if="sourceLink(contact)" :href="sourceLink(contact)" target="_blank" rel="noopener noreferrer" class="contact-source" aria-label="邮箱来源" :title="contact.sourceSnippet || contact.sourceLabel || '公开来源'">{{ contact.sourceLabel || '查看来源' }}<Icon name="external" :size="10" /></a>
      <span v-else class="contact-source unavailable-source">来源待核实</span>
      <p v-if="expanded && contact.sourceSnippet" class="contact-snippet">{{ contact.sourceSnippet }}</p>
    </div>
    <button v-if="!expanded && items.length > 2" class="text-button more-emails" @click="showAll = !showAll">{{ showAll ? '收起' : `另有 ${items.length - 2} 个邮箱` }}</button>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import Icon from './WorkbenchIcon.vue'
const props = defineProps({ contacts: Array, expanded: Boolean })
const showAll = ref(false)
const items = computed(() => {
  const seen = new Set()
  return (props.contacts || []).filter(contact => {
    if (!contact?.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contact.email)) return false
    const key = contact.email.toLowerCase(); if (seen.has(key)) return false; seen.add(key); return true
  })
})
const visibleItems = computed(() => props.expanded || showAll.value ? items.value : items.value.slice(0, 2))
function sourceLink(contact) { try { const url = new URL(contact.sourceUrl); return ['http:', 'https:'].includes(url.protocol) ? url.href : null } catch { return null } }
async function copy(email) { try { await navigator.clipboard.writeText(email); ElMessage.success('邮箱已复制') } catch { ElMessage.info('可选中邮箱文字后手动复制') } }
</script>
<style scoped>
.email-not-found{font-size:12px;color:#98a0b1;padding-top:2px}.publication-email+.publication-email{margin-top:13px;padding-top:11px;border-top:1px solid #f0edf6}.email-address-row{display:flex;align-items:flex-start;gap:6px}.contact-address{font-size:11px;color:#676083;line-height:1.8;overflow-wrap:anywhere;user-select:text}.copy-email{flex-shrink:0;border:0;background:none;padding:3px;color:#a599bf;display:grid;place-items:center;opacity:.65;border-radius:3px}.copy-email:hover,.copy-email:focus-visible{color:var(--accent);opacity:1}.contact-author{font-size:10px;color:#9295a8;line-height:1.8;margin-top:3px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.contact-source{font-size:9px;color:#9682b5;display:inline-flex;align-items:center;gap:4px;margin-top:4px}.contact-source:hover{color:var(--accent)}.unavailable-source{color:#a9a2b6}.more-emails{font-size:10px;margin-top:9px;padding:0}.contact-snippet{font-size:11px;color:#8c8b9e;line-height:1.9;margin:8px 0 0;white-space:pre-wrap;overflow-wrap:anywhere}.expanded .publication-email{padding:15px 17px;border:1px solid #ece8f3;background:#fcfbff;border-radius:8px;margin:0}.expanded{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:12px}.expanded .contact-address{font-size:12px}.expanded .contact-author{-webkit-line-clamp:unset}
</style>
