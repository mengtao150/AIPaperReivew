<template>
  <div class="page-heading"><div><div class="eyebrow">SEARCH HISTORY</div><h1>检索记录</h1><p>回到研究问题，继续上一次探索。</p></div><el-button type="primary" @click="$router.push('/scan/new')"><Icon name="plus" :size="15" />新建检索</el-button></div>
  <div class="surface"><div class="surface-header"><h2>全部检索 <span class="pill">{{ scans.length }}</span></h2><button class="text-button" :disabled="loading" @click="load"><Icon name="search" :size="14" />刷新</button></div><div v-if="error" class="inline-error history-error">{{ error }}<button class="text-button" @click="load">重试</button></div><div v-if="!loading && !error && !scans.length" class="empty-state"><div class="empty-icon"><Icon name="folder" :size="26" /></div><h3>开始你的第一次检索</h3><p>构建检索条件，汇集与你的问题相关的研究证据。</p><el-button type="primary" @click="$router.push('/scan/new')">新建文献检索<Icon name="arrow" :size="15" /></el-button></div>
  <el-table v-else v-loading="loading" :data="scans" row-key="id"><el-table-column label="检索任务" min-width="260"><template #default="{row}"><button class="task-title" @click="open(row)">{{ row.topic }}</button><div class="task-description">{{ row.dateFrom ? `${row.dateFrom} — ${row.dateTo}` : '不限时间' }} · {{ row.language === 'english' ? '英语' : row.language === 'chinese' ? '中文' : '不限语言' }}</div></template></el-table-column><el-table-column label="状态" width="125"><template #default="{row}"><span :class="['pill', status(row).color]">{{ status(row).label }}</span></template></el-table-column><el-table-column label="检索 / 已获取" width="145"><template #default="{row}"><span class="task-count">{{ row.totalHits?.toLocaleString() ?? '—' }}<span> / {{ row.fetchedCount?.toLocaleString() ?? '—' }}</span></span></template></el-table-column><el-table-column label="创建时间" width="155"><template #default="{row}"><span class="task-date">{{ formatDate(row.createdAt) }}</span></template></el-table-column><el-table-column label="操作" width="140"><template #default="{row}"><button class="text-button" @click="open(row)">{{ ['FETCHED','CLASSIFIED'].includes(row.status) ? '阅读文献' : ['FETCHING','CLASSIFYING','PAUSED'].includes(row.status) ? '查看任务' : '继续检索' }}<Icon name="arrow" :size="13" /></button></template></el-table-column></el-table>
  </div>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '../components/WorkbenchIcon.vue'
import api from '../api'
import { errorMessage } from '../lib/search'
const router = useRouter(), scans = ref([]), loading = ref(false), error = ref('')
let timer, disposed = false
async function load() { if (loading.value) return; loading.value = true; error.value = ''; try { const { data } = await api.get('/scans'); scans.value = data.sort((a,b) => b.id - a.id) } catch (e) { error.value = errorMessage(e, '暂时无法读取检索记录，请重试') } finally { loading.value = false } }
async function poll() { if (disposed) return; await load(); if (!disposed) timer = setTimeout(poll, 8000) }
function open(row) { router.push(['FETCHED','CLASSIFIED','FETCHING','CLASSIFYING','PAUSED'].includes(row.status) ? `/scan/${row.id}/articles` : `/scan/new?run=${row.id}`) }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—' }
function status(row) { return ({ CLASSIFIED: { label: '可阅读', color: 'green' }, FETCHED: { label: '已获取', color: 'green' }, FETCHING: { label: '正在获取', color: 'amber' }, CLASSIFYING: { label: '正在整理', color: 'amber' }, PAUSED: { label: '已暂停', color: 'amber' }, INTERRUPTED: { label: '已中断，可重试', color: 'amber' }, FAILED: { label: '未完成', color: 'amber' }, CANCELLED: { label: '已取消', color: '' }, COUNTED: { label: '待获取文献', color: '' }, CREATED: { label: '待检索', color: '' } })[row.status] || { label: '处理中', color: '' } }
onMounted(poll); onBeforeUnmount(() => { disposed = true; clearTimeout(timer) })
</script>
<style scoped>
.task-title{padding:0;background:none;border:0;color:#5a5b78;font-size:12px;text-align:left;line-height:1.8;font-weight:500}.task-title:hover{color:var(--accent)}.task-description{font-size:10px;color:#a6adbd;margin-top:7px}.task-count{font-size:12px;color:#777790}.task-count>span{color:#b5b8c7;font-size:11px}.task-date{font-size:10px;color:#a2a9bb}.history-error{margin:20px}
</style>
