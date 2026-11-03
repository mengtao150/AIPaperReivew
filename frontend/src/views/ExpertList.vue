<template>
  <div style="padding:24px">
    <div style="margin-bottom:16px;display:flex;justify-content:space-between;align-items:center">
      <div class="p-title" style="margin:0">👥 专家筛选</div>
      <el-button type="primary" @click="extract">🧲 从语料中提取专家</el-button>
    </div>
    <el-table :data="experts" stripe v-loading="loading">
      <el-table-column prop="name" label="姓名" min-width="160" />
      <el-table-column prop="institution" label="单位" min-width="200" show-overflow-tooltip />
      <el-table-column prop="countryRegion" label="地域" width="80" />
      <el-table-column label="适配分" width="90" align="right">
        <template #default="{row}"><b>{{ row.totalScore?.toFixed(1) }}</b></template>
      </el-table-column>
      <el-table-column prop="topicOutputs36m" label="36月产出" width="90" align="right" />
      <el-table-column label邮箱 class="mono" width="200">
        <template #default="{row}">
          <span v-if="row.email" class="mono">{{ row.email }}</span>
          <span v-else class="dim">未找到</span>
        </template>
      </el-table-column>
      <el-table-column label="邮箱级别" width="80">
        <template #default="{row}"><span :class="'el-tag ' + (row.emailType==='A'?'el-tag--success':'')">{{ row.emailType || '—' }}</span></template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="120">
        <template #default="{row}">
          <el-button size="small" type="primary" plain @click="invite(row)">邀稿</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top:16px;display:flex;justify-content:center">
      <el-pagination background layout="prev, pager, next, total" :total="total" :page-size="pageSize" v-model:current-page="currentPage" @current-change="load" />
    </div>
  </div>
</template>

<script>
import api from '../api'
export default {
  props: ['id'],
  data() { return { experts: [], total: 0, currentPage: 1, pageSize: 20, loading: false } },
  async mounted() { this.load() },
  methods: {
    async load() {
      this.loading = true
      const res = await api.get(`/scans/${this.id}/experts`, { params: { page: this.currentPage - 1, size: this.pageSize } })
      this.experts = res.data.content
      this.total = res.data.totalElements
      this.loading = false
    },
    async extract() {
      this.$message.info('正在从语料中提取专家…')
      await api.post(`/scans/${this.id}/experts`)
      this.$message.success('专家提取完成')
      this.load()
    },
    invite(row) {
      this.$router.push({ path: `/scan/${this.id}/mail`, query: { expertId: row.id, expertName: row.name, expertEmail: row.email } })
    }
  }
}
</script>
