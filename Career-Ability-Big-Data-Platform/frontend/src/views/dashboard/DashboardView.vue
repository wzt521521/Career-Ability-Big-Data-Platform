<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Refresh, Briefcase, Coin, OfficeBuilding, TrendCharts } from '@element-plus/icons-vue'
import ChartPanel from '../../components/ChartPanel.vue'
import {
  getOverview, getTrendStats, getCityStats, getSkillStats,
  getEducationStats, getCompanyStats
} from '../../api/analytics.js'

const loading = ref(false)
const loadError = ref(false)
const overview = ref({})
const trends = ref({ monthly: [] })
const cities = ref({ salaryComparison: [] })
const skills = ref({ topSkills: [] })
const education = ref({ distribution: [] })
const companies = ref({ industryDistribution: [] })
const refreshSeconds = ref(60)
const refreshedAt = ref(null)
let timer

const palette = ['#258478', '#e16f50', '#c89534', '#55748f', '#765f85', '#7f9b75']
const tooltip = { trigger: 'axis', backgroundColor: '#17201f', borderWidth: 0, textStyle: { color: '#fff' } }

const metrics = computed(() => [
  { label: '岗位总量', value: formatInteger(overview.value.totalPositions), unit: '个', icon: Briefcase, tone: 'teal' },
  { label: '本月新增', value: formatInteger(overview.value.newThisMonth), unit: '个', icon: TrendCharts, tone: 'coral' },
  { label: '平均月薪', value: formatNumber(overview.value.averageSalary), unit: 'K', icon: Coin, tone: 'gold' },
  { label: '活跃企业', value: formatInteger(overview.value.activeCompanies), unit: '家', icon: OfficeBuilding, tone: 'blue' }
])

const trendOption = computed(() => {
  const values = trends.value.monthly || []
  return {
    tooltip,
    grid: { left: 12, right: 18, top: 30, bottom: 10, containLabel: true },
    xAxis: { type: 'category', data: values.map(item => item.name), boundaryGap: false, axisLine: { lineStyle: { color: '#ccd5d2' } }, axisLabel: { color: '#697774' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf0ef' } }, axisLabel: { color: '#697774' } },
    series: [{ name: '新增岗位', type: 'line', smooth: true, symbolSize: 7, data: values.map(item => item.value), lineStyle: { width: 3, color: '#258478' }, itemStyle: { color: '#258478' }, areaStyle: { color: 'rgba(37,132,120,.10)' } }]
  }
})

const cityOption = computed(() => {
  const values = (cities.value.salaryComparison || []).slice(0, 10).reverse()
  return {
    tooltip,
    grid: { left: 10, right: 22, top: 22, bottom: 10, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf0ef' } }, axisLabel: { formatter: '{value}K', color: '#697774' } },
    yAxis: { type: 'category', data: values.map(item => item.name), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#45524f' } },
    series: [{ name: '平均月薪', type: 'bar', data: values.map(item => item.averageSalary), barMaxWidth: 18, itemStyle: { color: '#e16f50', borderRadius: [0, 3, 3, 0] } }]
  }
})

const skillOption = computed(() => ({
  tooltip: { ...tooltip, trigger: 'item' },
  color: palette,
  series: [{ type: 'treemap', roam: false, nodeClick: false, breadcrumb: { show: false }, top: 10, bottom: 8, left: 4, right: 4,
    label: { show: true, formatter: '{b}', color: '#fff', fontWeight: 600, overflow: 'truncate' },
    upperLabel: { show: false }, itemStyle: { borderColor: '#fff', borderWidth: 3, gapWidth: 2 },
    levels: [{ color: palette, colorMappingBy: 'index', itemStyle: { borderRadius: 3 } }],
    data: (skills.value.topSkills || []).map(item => ({ name: item.name, value: item.value })) }]
}))

const educationOption = computed(() => ({
  tooltip: { ...tooltip, trigger: 'item' },
  legend: { bottom: 0, textStyle: { color: '#697774' } },
  color: palette,
  series: [{ name: '学历要求', type: 'pie', radius: ['44%', '70%'], center: ['50%', '44%'], avoidLabelOverlap: true,
    label: { formatter: '{b}\n{d}%', color: '#45524f' },
    data: (education.value.distribution || []).map(item => ({ name: item.name, value: item.value })) }]
}))

const industryOption = computed(() => {
  const values = (companies.value.industryDistribution || []).slice(0, 6)
  const max = Math.max(1, ...values.map(item => Number(item.value)))
  return {
    tooltip: { ...tooltip, trigger: 'item' },
    color: ['#55748f'],
    radar: { radius: '66%', center: ['50%', '52%'], indicator: values.map(item => ({ name: item.name, max })), splitArea: { areaStyle: { color: ['#fafbfb', '#f3f6f5'] } }, axisName: { color: '#45524f' }, splitLine: { lineStyle: { color: '#dfe5e3' } } },
    series: [{ type: 'radar', data: [{ name: '岗位数量', value: values.map(item => item.value), areaStyle: { color: 'rgba(85,116,143,.22)' }, lineStyle: { width: 2 } }] }]
  }
})

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const [overviewData, trendData, cityData, skillData, educationData, companyData] = await Promise.all([
      getOverview(), getTrendStats(), getCityStats(), getSkillStats(), getEducationStats(), getCompanyStats()
    ])
    overview.value = overviewData
    trends.value = trendData
    cities.value = cityData
    skills.value = skillData
    education.value = educationData
    companies.value = companyData
    refreshedAt.value = new Date()
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function configureTimer() {
  clearInterval(timer)
  timer = setInterval(load, refreshSeconds.value * 1000)
}

function formatInteger(value) { return new Intl.NumberFormat('zh-CN').format(Number(value || 0)) }
function formatNumber(value) { return Number(value || 0).toFixed(1) }

onMounted(() => { load(); configureTimer() })
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="page dashboard-page">
    <el-alert v-if="loadError" class="load-alert" title="统计数据暂时无法加载，请检查后端服务后重试" type="error" show-icon :closable="false" />
    <div class="page-toolbar">
      <p>数据更新时间：{{ refreshedAt ? refreshedAt.toLocaleTimeString('zh-CN', { hour12: false }) : '--:--:--' }}</p>
      <div class="refresh-tools">
        <el-select v-model="refreshSeconds" aria-label="自动刷新间隔" style="width: 112px" @change="configureTimer">
          <el-option label="30 秒刷新" :value="30" />
          <el-option label="60 秒刷新" :value="60" />
          <el-option label="120 秒刷新" :value="120" />
        </el-select>
        <el-button :icon="Refresh" :loading="loading" title="立即刷新" circle @click="load" />
      </div>
    </div>

    <section class="metric-grid" aria-label="核心就业指标">
      <article v-for="metric in metrics" :key="metric.label" class="metric" :class="metric.tone">
        <div class="metric-icon"><el-icon><component :is="metric.icon" /></el-icon></div>
        <div>
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}<small>{{ metric.unit }}</small></strong>
        </div>
      </article>
    </section>

    <section class="chart-grid">
      <ChartPanel class="wide" title="岗位增长趋势" subtitle="近 12 个月新增岗位变化" :option="trendOption" :loading="loading" :empty="!trends.monthly?.length" />
      <ChartPanel title="城市薪资排行" subtitle="平均月薪 TOP 10，单位 K" :option="cityOption" :loading="loading" :empty="!cities.salaryComparison?.length" />
      <ChartPanel title="热门技能图谱" subtitle="岗位技能标签出现频次" :option="skillOption" :loading="loading" :empty="!skills.topSkills?.length" />
      <ChartPanel title="学历要求分布" subtitle="不同学历要求的岗位占比" :option="educationOption" :loading="loading" :empty="!education.distribution?.length" />
      <ChartPanel class="wide" title="行业招聘活跃度" subtitle="岗位数量最多的六个行业" :option="industryOption" :loading="loading" :empty="!companies.industryDistribution?.length" />
    </section>
  </div>
</template>

<style scoped>
.refresh-tools { display: flex; align-items: center; gap: 8px; }
.load-alert { margin-bottom: 16px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 16px; }
.metric { display: flex; min-width: 0; min-height: 112px; align-items: center; gap: 16px; padding: 18px 20px; border: 1px solid var(--line); border-top: 3px solid var(--teal); border-radius: 6px; background: #fff; }
.metric.coral { border-top-color: var(--coral); }.metric.gold { border-top-color: var(--gold); }.metric.blue { border-top-color: #55748f; }
.metric-icon { display: grid; width: 42px; height: 42px; flex: 0 0 42px; place-items: center; border-radius: 5px; background: rgba(37,132,120,.1); color: var(--teal); font-size: 21px; }
.coral .metric-icon { background: rgba(225,111,80,.1); color: var(--coral); }.gold .metric-icon { background: rgba(200,149,52,.12); color: var(--gold); }.blue .metric-icon { background: rgba(85,116,143,.1); color: #55748f; }
.metric span { display: block; margin-bottom: 7px; color: var(--muted); font-size: 12px; }
.metric strong { color: var(--ink); font-size: 26px; line-height: 1; font-weight: 700; }
.metric small { margin-left: 5px; color: var(--muted); font-size: 12px; font-weight: 500; }
.chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.chart-grid .wide { grid-column: span 2; }
@media (max-width: 1100px) { .metric-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 760px) {
  .page-toolbar { align-items: flex-start; }
  .metric-grid { grid-template-columns: 1fr 1fr; gap: 10px; }
  .metric { min-height: 94px; gap: 10px; padding: 13px; }
  .metric-icon { width: 34px; height: 34px; flex-basis: 34px; }
  .metric strong { font-size: 20px; }
  .chart-grid { grid-template-columns: 1fr; }
  .chart-grid .wide { grid-column: span 1; }
}
@media (max-width: 430px) { .metric-grid { grid-template-columns: 1fr; } }
</style>
