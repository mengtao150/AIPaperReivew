<template>
  <div style="padding:24px">
    <div class="grid-2" style="margin-bottom:18px">
      <div class="panel">
        <div class="p-title">📊 主题分布（一级分类）</div>
        <div ref="pieChart" style="height:360px"></div>
      </div>
      <div class="panel">
        <div class="p-title">方向文献数量</div>
        <div ref="trendChart" style="height:360px"></div>
      </div>
    </div>
    <div class="panel">
      <div class="p-title">📋 分类概览</div>
      <el-table :data="categories" stripe>
        <el-table-column prop="nameZh" label="分类名称" min-width="200" />
        <el-table-column prop="articleCount" label="文章数" width="100" align="right" />
        <el-table-column label="RCT 占比" width="120" align="right">
          <template #default="{row}">{{ (row.rctShare * 100).toFixed(0) }}%</template>
        </el-table-column>
        <el-table-column prop="themeZh" label="主题句" min-width="250" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script>
import api from '../api'
import * as echarts from 'echarts/core'
import { PieChart, BarChart } from 'echarts/charts'
import { TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([PieChart, BarChart, TooltipComponent, GridComponent, CanvasRenderer])
export default {
  props: ['id'],
  data() { return { categories: [], scan: null } },
  async mounted() {
    if (!this.id || this.id === 'undefined' || isNaN(Number(this.id))) {
      this.$message.warning('扫描 ID 无效，请从新建扫描页开始')
      this.$router.push('/scan/new')
      return
    }
    const [catRes, scanRes] = await Promise.all([
      api.get(`/scans/${this.id}/categories`),
      api.get(`/scans/${this.id}`)
    ])
    this.categories = catRes.data
    this.scan = scanRes.data
    this.$nextTick(() => this.renderCharts())
  },
  methods: {
    renderCharts() {
      const pie = echarts.init(this.$refs.pieChart)
      pie.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} 篇 ({d}%)' },
        series: [{
          type: 'pie', radius: ['38%', '68%'], center: ['50%', '50%'],
          label: { show: true, formatter: '{b}\n{d}%' },
          data: this.categories.filter(c => c.articleCount > 0).map(c => ({
            name: c.nameZh, value: c.articleCount
          }))
        }]
      })
      const trend = echarts.init(this.$refs.trendChart)
      trend.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: this.categories.map(c => c.nameZh.substring(0, 6)), axisLabel: { rotate: 45, fontSize: 10 } },
        yAxis: { type: 'value', name: '文章数' },
        series: [{ type: 'bar', data: this.categories.map(c => c.articleCount), itemStyle: { color: '#3b6ff0', borderRadius: [4,4,0,0] } }]
      })
      window.addEventListener('resize', () => { pie.resize(); trend.resize() })
    }
  }
}
</script>
