<template>
  <div class="evidence-chart" ref="canvas" role="img" :aria-label="kind === 'timeline' ? '按真实发表月份统计的文献数量，缺少月份的文献不计入' : '主题关联图，连线表示共同文献；可通过左侧主题列表选择方向'"></div>
</template>
<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { GraphChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { monthlySeries } from '../lib/evidence'
echarts.use([GraphChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer])
const props = defineProps({ topics: { type: Array, default: () => [] }, timeline: { type: Array, default: () => [] }, selected: String, kind: { type: String, default: 'graph' } })
const emit = defineEmits(['select']), canvas = ref(null)
let chart, observer
function draw() {
  if (!chart) return
  if (props.kind === 'timeline') {
    const data = monthlySeries(props.timeline)
    chart.setOption({ animation: false, tooltip: { trigger: 'axis', renderMode: 'richText' }, grid: { left: 40, right: 22, top: 22, bottom: 35 }, xAxis: { type: 'category', data: data.map(d => d.month), axisLine: { lineStyle: { color: '#e4e6ef' } }, axisLabel: { color: '#8c94a5', fontSize: 10 } }, yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#f0f1f7' } }, axisLabel: { color: '#8c94a5', fontSize: 10 } }, series: [{ type: 'line', data: data.map(d => d.count), showSymbol: data.length < 30, smooth: false, lineStyle: { color: '#8576db', width: 2 }, itemStyle: { color: '#8576db' }, areaStyle: { color: '#f0edfc' } }] }, true)
    return
  }
  const nodes = props.topics.map((t, i) => ({ id: t.id, name: t.name, value: t.pmids?.length || 0, symbolSize: Math.min(78, 38 + Math.sqrt(t.pmids?.length || 0) * 4), itemStyle: { color: t.id === props.selected ? '#6254df' : ['#a79ad9', '#8bb9ba', '#b4a5d8', '#b4c4df'][i % 4], borderColor: '#fff', borderWidth: 4 }, label: { show: true, color: '#fff', position: 'inside', fontSize: 12, fontWeight: 600, formatter: String(i + 1).padStart(2, '0') } }))
  const links = []
  for (let i = 0; i < props.topics.length; i++) for (let j = i + 1; j < props.topics.length; j++) {
    const set = new Set(props.topics[i].pmids || []), shared = (props.topics[j].pmids || []).filter(p => set.has(p)).length
    if (shared) links.push({ source: props.topics[i].id, target: props.topics[j].id, value: shared, lineStyle: { width: Math.min(3, 1 + Math.log1p(shared)), opacity: [props.topics[i].id, props.topics[j].id].includes(props.selected) ? .38 : .09, color: '#a49bbf' } })
  }
  chart.setOption({ animation: false, tooltip: { renderMode: 'richText', formatter: p => p.dataType === 'edge' ? `共同文献：${p.value} 篇` : `${p.name}\n关联文献：${p.value} 篇` }, series: [{ type: 'graph', layout: 'circular', roam: true, zoom: 1.05, data: nodes, links, emphasis: { focus: 'adjacency' }, circular: { rotateLabel: false }, left: '12%', right: '12%', top: '15%', bottom: '15%' }] }, true)
}
onMounted(() => { chart = echarts.init(canvas.value); chart.on('click', p => { if (p.dataType === 'node') emit('select', p.data.id) }); observer = new ResizeObserver(() => chart?.resize()); observer.observe(canvas.value); draw() })
watch(() => [props.topics, props.timeline, props.selected, props.kind], () => nextTick(draw), { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose(); chart = null })
</script>
<style scoped>.evidence-chart{height:360px;width:100%;min-width:0}@media(max-width:760px){.evidence-chart{height:280px}}</style>
