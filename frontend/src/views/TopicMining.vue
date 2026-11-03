<template>
  <div style="padding:24px">
    <div style="margin-bottom:16px;display:flex;justify-content:space-between;align-items:center">
      <div class="p-title" style="margin:0">💡 主题挖掘与新颖性查重</div>
      <el-button type="primary" @click="mine">🧭 执行主题挖掘</el-button>
    </div>
    <el-empty v-if="!leads.length" description="点击上方按钮开始挖掘" />
    <div v-for="lead in leads" :key="lead.id" class="panel" style="margin-bottom:14px">
      <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:8px">
        <div>
          <span class="el-tag" style="margin-right:8px">{{ lead.dimension }}</span>
          <strong style="font-size:14px">{{ lead.titleZh }}</strong>
        </div>
        <div>
          <span v-if="lead.noveltyStatus==='CHECKED'" class="el-tag el-tag--success">{{ lead.confidence }} 置信</span>
          <span v-else-if="lead.noveltyStatus==='PENDING'" class="el-tag el-tag--warning">待查重</span>
        </div>
      </div>
      <p class="dim" style="margin-bottom:10px">{{ lead.description }}</p>
      <div style="display:flex;gap:8px;align-items:center">
        <span class="badge">支撑文献 {{ lead.articleCount }} 篇</span>
        <el-button size="small" :disabled="lead.noveltyStatus==='CHECKED'" @click="checkNovelty(lead)">🔬 五层查重</el-button>
        <el-button size="small" type="primary" plain @click="$router.push(`/scan/${id}/experts`)">转专家筛选 →</el-button>
      </div>
      <div v-if="checks[lead.id]" style="margin-top:12px">
        <el-steps :active="checks[lead.id].length" simple style="margin-bottom:8px">
          <el-step v-for="c in checks[lead.id]" :key="c.layerNo" :title="'层'+c.layerNo" :description="c.layerName.substring(0,12)" :status="stepStatus(c.status)" />
        </el-steps>
        <div v-for="c in checks[lead.id]" :key="c.layerNo" style="padding:6px 0;border-bottom:1px dashed #e2e7f0;font-size:12.5px">
          <b>层{{ c.layerNo }} {{ c.layerName }}</b>
          <span :class="['el-tag', c.status==='空窗'?'el-tag--success':c.status==='部分重叠'?'el-tag--warning':'el-tag--danger']" style="margin:0 8px">{{ c.status }}</span>
          <span class="dim">{{ c.detail }}</span>
        </div>
        <div v-if="lead.verdict" style="margin-top:10px;padding:10px 14px;background:#f5f7fb;border-radius:8px;font-size:12.5px">
          <b>结论：</b>{{ lead.verdict }}
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api from '../api'
export default {
  props: ['id'],
  data() { return { leads: [], checks: {} } },
  async mounted() { await this.load() },
  methods: {
    async load() {
      const res = await api.get(`/scans/${this.id}/leads`)
      this.leads = res.data
    },
    async mine() {
      await api.post(`/scans/${this.id}/mine`)
      await this.load()
      this.$message.success('主题挖掘完成')
    },
    async checkNovelty(lead) {
      const res = await api.post(`/scans/leads/${lead.id}/check`)
      this.checks[lead.id] = res.data
      lead.noveltyStatus = 'CHECKED'
      lead.confidence = '中'
      lead.verdict = '部分重叠 → 需差异化改写角度'
    },
    stepStatus(status) {
      if (status === '空窗') return 'success'
      if (status === '部分重叠') return 'warning'
      if (status === '未完成') return 'error'
      return 'wait'
    }
  }
}
</script>
