<template>
  <div class="workbench-shell">
    <aside class="app-sidebar">
      <router-link to="/scan/new" class="brand"><span class="brand-mark"><Icon name="book" :size="23" /></span><span><strong>审稿人工作台</strong><small>REVIEWER SCOUT</small></span></router-link>
      <div class="nav-caption">研究工作空间</div>
      <nav aria-label="主导航">
        <router-link to="/scan/new" class="nav-link"><Icon name="search" />文献检索<span class="nav-pill">PubMed</span></router-link>
        <router-link to="/tasks" class="nav-link"><Icon name="folder" />检索记录</router-link>
        <template v-if="scanId"><div class="nav-caption current-caption">当前检索</div><router-link :to="`/scan/${scanId}/articles`" class="nav-link"><Icon name="document" />文献列表</router-link><router-link :to="`/scan/${scanId}/topics`" class="nav-link"><Icon name="connection" />领域梳理</router-link><router-link :to="`/scan/${scanId}`" class="nav-link"><Icon name="chart" />研究分布</router-link><details class="other-tools"><summary>其他研究工具</summary><router-link :to="`/scan/${scanId}/experts`" class="nav-link"><Icon name="connection" />专家筛选</router-link><router-link :to="`/scan/${scanId}/mail`" class="nav-link"><Icon name="document" />邀稿邮件</router-link></details></template>
      </nav>
      <div class="sidebar-bottom"><div class="workspace-note"><span class="live-dot"></span>独立研究空间<small>让每一次判断，都有证据可循。</small></div><router-link to="/settings/models" class="nav-link"><Icon name="settings" />模型设置<span v-if="!active" class="status-dot"></span></router-link><div class="sidebar-profile"><span class="avatar">R</span><div><strong>研究者工作空间</strong><small>本机工作区</small></div></div></div>
    </aside>
    <div class="app-body">
      <header class="app-topbar"><div class="breadcrumb">工作空间 <span>/</span> <strong>{{ pageName }}</strong></div><router-link to="/settings/models" class="model-indicator"><span :class="['live-dot', { muted: !active }]"></span>{{ active?.name || '配置 AI 模型' }}<Icon name="settings" :size="15" /></router-link></header>
      <main class="workspace-main"><router-view /></main>
      <footer class="app-footer"><span>REVIEWER SCOUT</span><span>严谨检索 · 清晰阅读 · 独立判断</span></footer>
    </div>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Icon from './components/WorkbenchIcon.vue'
import { modelState, activeModel } from './lib/models'
const route = useRoute()
const active = computed(() => { modelState.activeId; return activeModel() })
const scanId = computed(() => /^\d+$/.test(route.params.id || '') ? route.params.id : null)
const pageName = computed(() => route.path.includes('/settings') ? '模型设置' : route.path === '/tasks' ? '检索记录' : route.path.endsWith('/articles') ? '文献列表' : route.path.endsWith('/topics') ? '领域梳理' : route.path === '/scan/new' ? '文献检索' : '研究详情')
</script>
