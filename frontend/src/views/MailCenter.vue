<template>
  <div style="padding:24px">
    <div class="grid-2">
      <div class="panel">
        <div class="p-title">✉ 邀稿模板</div>
        <el-form label-position="top">
          <el-form-item label="邮件主题"><el-input v-model="form.subject" /></el-form-item>
          <el-form-item label="邮件正文">
            <el-input v-model="form.body" type="textarea" :rows="16" />
          </el-form-item>
          <div class="note" v-pre>💡 可用变量：{{专家姓名}} {{单位}} {{期刊名}} {{稿件标题}} {{审稿时限}} {{编辑姓名}}</div>
          <div class="actions">
            <el-button type="primary" @click="save">💾 保存并发送</el-button>
            <el-button @click="polish">✨ AI 润色</el-button>
          </div>
        </el-form>
      </div>
      <div class="panel">
        <div class="p-title">📤 发送状态</div>
        <el-table :data="invitations" stripe>
          <el-table-column label="收件人" min-width="160">
            <template #default="{row}">{{ expertName(row.expertId) }}</template>
          </el-table-column>
          <el-table-column prop="subject" label="主题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{row}">
              <span :class="'el-tag ' + row.status==='SENT'?'el-tag--success':''">{{ row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="sentAt" label="时间" width="140" />
        </el-table>
        <div class="note">⏱ 限速 ≤6 封/时 · ≤20 封/天 · 群发单显 · 拒绝即入抑制名单</div>
      </div>
    </div>
  </div>
</template>

<script>
import api from '../api'
export default {
  props: ['id'],
  data() {
    return {
      form: {
        subject: '[示例期刊] 审稿邀请',
        body: '尊敬的 {{专家姓名}} 教授：\n\n诚挚邀请您审阅以下稿件。\n\n此致\n敬礼',
        expertId: this.$route.query.expertId || null,
        scanRunId: this.id
      },
      invitations: [], experts: []
    }
  },
  async mounted() {
    const [invRes, expRes] = await Promise.all([
      api.get(`/invitations/run/${this.id}`),
      api.get(`/scans/${this.id}/experts`)
    ])
    this.invitations = invRes.data
    this.experts = expRes.data.content || []
    if (this.$route.query.expertName) {
      this.form.subject = `[示例期刊] 审稿邀请 - ${this.$route.query.expertName}`
    }
  },
  methods: {
    expertName(id) { return this.experts.find(e => e.id === id)?.name || id },
    async save() {
      const res = await api.post('/invitations', this.form)
      await api.post(`/invitations/${res.data.id}/send`)
      this.$message.success('邀稿邮件已发送！')
      this.loadData()
    },
    polish() { this.$message.info('AI 润色功能需配置 LLM API Key') }
  }
}
</script>
