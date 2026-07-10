<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Grid, List, Search, RefreshLeft, Location, OfficeBuilding, Coin } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { registerMap } from '../../utils/echarts.js'
import chinaGeoJson from '@datapool/china.geojson'
import ChartPanel from '../../components/ChartPanel.vue'
import { getPositions, getPosition, getSuggestions } from '../../api/positions.js'
import { getSkillStats, getCityStats } from '../../api/analytics.js'

registerMap('china', chinaGeoJson)

const loading = ref(false)
const loadError = ref(false)
const detailLoading = ref(false)
const items = ref([])
const total = ref(0)
const detail = ref(null)
const drawerVisible = ref(false)
const compareVisible = ref(false)
const compareItems = ref([])
const viewMode = ref('card')
const skillStats = ref({ topSkills: [] })
const cityStats = ref({ ranking: [] })

const filter = reactive({
  keyword: '', city: '', salaryMin: undefined, salaryMax: undefined,
  education: '', experience: '', industry: '', sortBy: 'publishDate',
  sortDirection: 'desc', page: 1, size: 12
})

const cities = ['北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '南京', '西安', '重庆', '长沙', '苏州']
const educations = ['不限', '大专', '本科', '硕士', '博士']
const experiences = ['不限', '应届', '1-3年', '3-5年', '5-10年', '10年以上']
const industries = ['互联网', '软件和信息技术服务', '人工智能', '金融', '电子商务', '制造业', '教育']

const skillOption = computed(() => {
  const values = (skillStats.value.topSkills || []).slice(0, 12).reverse()
  return {
    tooltip: { trigger: 'axis', backgroundColor: '#17201f', borderWidth: 0, textStyle: { color: '#fff' } },
    grid: { left: 8, right: 20, top: 16, bottom: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf0ef' } }, axisLabel: { color: '#697774' } },
    yAxis: { type: 'category', data: values.map(item => item.name), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#45524f' } },
    series: [{ type: 'bar', data: values.map(item => item.value), barMaxWidth: 16, itemStyle: { color: '#258478', borderRadius: [0, 3, 3, 0] } }]
  }
})

const cityOption = computed(() => {
  const provinceCounts = new Map()
  for (const item of cityStats.value.ranking || []) {
    const province = provinceName(item.province || item.name)
    provinceCounts.set(province, (provinceCounts.get(province) || 0) + Number(item.value || 0))
  }
  const values = [...provinceCounts].map(([name, value]) => ({ name, value }))
  const max = Math.max(1, ...values.map(item => item.value))
  return {
    tooltip: { trigger: 'item', formatter: params => `${params.name}<br/>${params.value || 0} 个岗位` },
    visualMap: { min: 0, max, calculable: false, orient: 'horizontal', left: 'center', bottom: 0, inRange: { color: ['#eef5f3', '#79b6ad', '#258478'] }, textStyle: { color: '#697774' } },
    series: [{ name: '岗位数量', type: 'map', map: 'china', roam: false, top: 8, bottom: 34, data: values,
      itemStyle: { areaColor: '#eef2f1', borderColor: '#ffffff', borderWidth: 1 },
      emphasis: { label: { show: true }, itemStyle: { areaColor: '#e16f50' } },
      select: { disabled: true } }]
  }
})

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const data = await getPositions(cleanParams(filter))
    items.value = data.content
    total.value = data.totalElements
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function loadAnalysis() {
  try {
    const [skills, citiesData] = await Promise.all([getSkillStats(), getCityStats()])
    skillStats.value = skills
    cityStats.value = citiesData
  } catch {
    // The list remains usable when the secondary analysis panels are unavailable.
  }
}

function search() { filter.page = 1; load() }
function reset() {
  Object.assign(filter, { keyword: '', city: '', salaryMin: undefined, salaryMax: undefined, education: '', experience: '', industry: '', sortBy: 'publishDate', sortDirection: 'desc', page: 1, size: 12 })
  load()
}
function pageChanged(page) { filter.page = page; load() }

async function showDetail(id) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try { detail.value = await getPosition(id) } finally { detailLoading.value = false }
}

async function fetchSuggestions(query, callback) {
  if (!query.trim()) return callback([])
  try {
    const values = await getSuggestions(query)
    callback(values.map(value => ({ value })))
  } catch { callback([]) }
}

function cleanParams(values) {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== '' && value !== undefined && value !== null))
}

function salary(item) {
  if (!item.salaryMin && !item.salaryMax) return '薪资面议'
  if (!item.salaryMin) return `${item.salaryMax}K/月`
  if (!item.salaryMax) return `${item.salaryMin}K/月`
  return `${item.salaryMin}-${item.salaryMax}K/月`
}

function toggleCompare(item, checked) {
  if (!checked) {
    compareItems.value = compareItems.value.filter(value => value.id !== item.id)
    return
  }
  if (compareItems.value.length >= 3) {
    ElMessage.warning('最多同时对比 3 个岗位')
    return
  }
  compareItems.value.push(item)
}

function provinceName(value) {
  const name = String(value || '')
  if (['北京', '天津', '上海', '重庆'].includes(name)) return `${name}市`
  if (['内蒙古', '西藏'].includes(name)) return `${name}自治区`
  if (name === '广西') return '广西壮族自治区'
  if (name === '宁夏') return '宁夏回族自治区'
  if (name === '新疆') return '新疆维吾尔自治区'
  if (name === '香港') return '香港特别行政区'
  if (name === '澳门') return '澳门特别行政区'
  return /省$|市$|自治区$|特别行政区$/.test(name) ? name : `${name}省`
}

onMounted(() => { load(); loadAnalysis() })
</script>

<template>
  <div class="page position-page">
    <el-alert v-if="loadError" class="load-alert" title="岗位数据暂时无法加载，请检查后端服务后重试" type="error" show-icon :closable="false" />
    <section class="filter-band" aria-label="岗位筛选">
      <el-autocomplete v-model="filter.keyword" :fetch-suggestions="fetchSuggestions" placeholder="搜索岗位或企业" clearable :prefix-icon="Search" @keyup.enter="search" @select="search" />
      <el-select v-model="filter.city" placeholder="城市" clearable><el-option v-for="value in cities" :key="value" :label="value" :value="value" /></el-select>
      <div class="salary-range"><el-input-number v-model="filter.salaryMin" :min="0" :max="200" controls-position="right" placeholder="最低 K" /><span>至</span><el-input-number v-model="filter.salaryMax" :min="0" :max="200" controls-position="right" placeholder="最高 K" /></div>
      <el-select v-model="filter.education" placeholder="学历" clearable><el-option v-for="value in educations" :key="value" :label="value" :value="value" /></el-select>
      <el-select v-model="filter.experience" placeholder="经验" clearable><el-option v-for="value in experiences" :key="value" :label="value" :value="value" /></el-select>
      <el-select v-model="filter.industry" placeholder="行业" clearable><el-option v-for="value in industries" :key="value" :label="value" :value="value" /></el-select>
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
    </section>

    <div class="result-toolbar">
      <span>共 <strong>{{ total }}</strong> 个岗位</span>
      <div>
        <el-select v-model="filter.sortBy" aria-label="排序方式" style="width: 126px" @change="search">
          <el-option label="最新发布" value="publishDate" /><el-option label="薪资从高到低" value="salaryMax" /><el-option label="岗位名称" value="title" />
        </el-select>
        <el-radio-group v-model="viewMode" aria-label="展示模式">
          <el-radio-button value="card" title="卡片视图"><el-icon><Grid /></el-icon></el-radio-button>
          <el-radio-button value="list" title="列表视图"><el-icon><List /></el-icon></el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <section v-loading="loading" class="position-results" :class="viewMode">
      <el-empty v-if="!loading && !items.length" description="没有符合条件的岗位" />
      <article v-for="item in items" :key="item.id" class="position-item" tabindex="0" @click="showDetail(item.id)" @keyup.enter="showDetail(item.id)">
        <el-checkbox class="compare-check" :model-value="compareItems.some(value => value.id === item.id)" @click.stop @change="checked => toggleCompare(item, checked)">加入对比</el-checkbox>
        <div class="position-main">
          <div class="title-line"><h2>{{ item.title }}</h2><strong>{{ salary(item) }}</strong></div>
          <p class="company"><el-icon><OfficeBuilding /></el-icon>{{ item.companyName || '企业信息待完善' }}<span v-if="item.companySize">· {{ item.companySize }}</span></p>
          <div class="meta"><span><el-icon><Location /></el-icon>{{ item.city || '地点不限' }}</span><span>{{ item.education || '学历不限' }}</span><span>{{ item.experience || '经验不限' }}</span><span v-if="item.industry">{{ item.industry }}</span></div>
        </div>
        <div class="skill-tags"><el-tag v-for="skill in item.skills?.slice(0, 5)" :key="skill" effect="plain">{{ skill }}</el-tag></div>
        <time>{{ item.publishDate || '发布时间未知' }}</time>
      </article>
    </section>

    <el-pagination v-if="total" class="pagination" background layout="prev, pager, next, sizes, total" :current-page="filter.page" :page-size="filter.size" :page-sizes="[12, 24, 48]" :total="total" @current-change="pageChanged" @size-change="value => { filter.size = value; search() }" />

    <section class="analysis-grid">
      <ChartPanel title="技能需求排行" subtitle="全量岗位技能词频 TOP 12" :option="skillOption" :empty="!skillStats.topSkills?.length" />
      <ChartPanel title="岗位地域热力图" subtitle="各省份招聘岗位分布" :option="cityOption" :empty="!cityStats.ranking?.length" />
    </section>

    <div v-if="compareItems.length" class="compare-bar">
      <span>已选 {{ compareItems.length }}/3</span>
      <div class="compare-names"><el-tag v-for="item in compareItems" :key="item.id" closable @close="toggleCompare(item, false)">{{ item.title }}</el-tag></div>
      <el-button text @click="compareItems = []">清空</el-button>
      <el-button type="primary" :disabled="compareItems.length < 2" @click="compareVisible = true">开始对比</el-button>
    </div>

    <el-dialog v-model="compareVisible" title="同类岗位对比" width="min(900px, 94vw)">
      <el-table :data="compareItems" stripe>
        <el-table-column prop="title" label="岗位" min-width="150" />
        <el-table-column prop="companyName" label="企业" min-width="140" />
        <el-table-column label="薪资" width="110"><template #default="scope">{{ salary(scope.row) }}</template></el-table-column>
        <el-table-column prop="city" label="城市" width="80" />
        <el-table-column prop="education" label="学历" width="80" />
        <el-table-column prop="experience" label="经验" width="90" />
        <el-table-column label="技能" min-width="210"><template #default="scope">{{ scope.row.skills?.join('、') || '不限' }}</template></el-table-column>
      </el-table>
    </el-dialog>

    <el-drawer v-model="drawerVisible" title="岗位详情" size="min(560px, 94vw)" destroy-on-close>
      <div v-loading="detailLoading" class="detail-content">
        <template v-if="detail">
          <span class="detail-company">{{ detail.companyName }}</span>
          <h2>{{ detail.title }}</h2>
          <strong class="detail-salary"><el-icon><Coin /></el-icon>{{ salary(detail) }}</strong>
          <dl>
            <div><dt>工作地点</dt><dd>{{ detail.city || '不限' }} · {{ detail.cityTier || '其他' }}</dd></div>
            <div><dt>学历经验</dt><dd>{{ detail.education || '不限' }} · {{ detail.experience || '不限' }}</dd></div>
            <div><dt>企业信息</dt><dd>{{ detail.companyType || '类型未知' }} · {{ detail.companySize || '规模未知' }}</dd></div>
            <div><dt>所属行业</dt><dd>{{ detail.industry || '行业未知' }}</dd></div>
          </dl>
          <section><h3>技能要求</h3><div class="detail-tags"><el-tag v-for="skill in detail.skills" :key="skill">{{ skill }}</el-tag></div></section>
          <section v-if="detail.welfare?.length"><h3>福利待遇</h3><div class="detail-tags"><el-tag v-for="value in detail.welfare" :key="value" type="success" effect="plain">{{ value }}</el-tag></div></section>
          <section v-if="detail.description"><h3>岗位描述</h3><p class="description">{{ detail.description }}</p></section>
          <el-link v-if="detail.sourceUrl" :href="detail.sourceUrl" target="_blank" type="primary">查看原始岗位</el-link>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.filter-band { display: grid; grid-template-columns: minmax(210px, 1.5fr) 120px minmax(250px, 1.4fr) 110px 120px 150px auto auto; gap: 9px; align-items: center; padding: 16px; border: 1px solid var(--line); border-radius: 6px; background: #fff; }
.load-alert { margin-bottom: 16px; }
.salary-range { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 6px; color: var(--muted); font-size: 12px; }
.salary-range :deep(.el-input-number) { width: 100%; }
.result-toolbar { display: flex; align-items: center; justify-content: space-between; margin: 18px 0 11px; color: var(--muted); font-size: 13px; }
.result-toolbar strong { color: var(--ink); }.result-toolbar > div { display: flex; gap: 8px; }
.position-results { min-height: 180px; }
.position-results.card { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.position-item { position: relative; min-width: 0; padding: 18px; border: 1px solid var(--line); border-radius: 6px; background: #fff; cursor: pointer; transition: border-color .16s, box-shadow .16s, transform .16s; }
.compare-check { display: flex; justify-content: flex-end; height: 20px; margin: -5px 0 6px; }
.position-item:hover, .position-item:focus-visible { border-color: #9ebdb8; outline: none; box-shadow: 0 7px 18px rgba(23,32,31,.07); transform: translateY(-1px); }
.title-line { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.title-line h2 { min-width: 0; margin: 0; overflow: hidden; color: var(--ink); font-size: 16px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.title-line strong { flex: 0 0 auto; color: var(--coral); font-size: 14px; }
.company { display: flex; align-items: center; gap: 5px; margin: 11px 0; overflow: hidden; color: #45524f; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.meta { display: flex; flex-wrap: wrap; gap: 5px 12px; color: var(--muted); font-size: 12px; }.meta span { display: inline-flex; align-items: center; gap: 3px; }
.skill-tags { display: flex; min-height: 26px; flex-wrap: wrap; gap: 5px; margin-top: 15px; }.skill-tags .el-tag { max-width: 110px; border-radius: 3px; overflow: hidden; text-overflow: ellipsis; }
.position-item time { display: block; margin-top: 14px; color: #8c9895; font-size: 11px; text-align: right; }
.position-results.list { display: flex; flex-direction: column; gap: 8px; }
.position-results.list .position-item { display: grid; grid-template-columns: minmax(280px, 1.4fr) minmax(220px, 1fr) 90px; align-items: center; gap: 18px; padding: 14px 18px; }
.position-results.list .skill-tags { margin: 0; }.position-results.list time { margin: 0; }
.pagination { justify-content: flex-end; margin: 20px 0 30px; }
.analysis-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; padding-top: 24px; border-top: 1px solid var(--line); }
.compare-bar { position: fixed; right: 24px; bottom: 24px; z-index: 30; display: flex; max-width: calc(100vw - 272px); align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #b9c8c5; border-radius: 6px; background: #fff; box-shadow: 0 10px 30px rgba(23,32,31,.18); color: var(--muted); font-size: 12px; }
.compare-names { display: flex; min-width: 0; gap: 5px; }.compare-names .el-tag { max-width: 140px; overflow: hidden; text-overflow: ellipsis; }
.detail-content { min-height: 260px; padding: 0 5px 24px; }.detail-company { color: var(--muted); font-size: 13px; }.detail-content > h2 { margin: 5px 0 8px; color: var(--ink); font-size: 23px; }.detail-salary { display: inline-flex; align-items: center; gap: 5px; color: var(--coral); font-size: 18px; }
dl { margin: 24px 0; border-top: 1px solid var(--line); }dl div { display: grid; grid-template-columns: 82px 1fr; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--line); font-size: 13px; }dt { color: var(--muted); }dd { margin: 0; color: var(--ink); }
.detail-content section { margin: 22px 0; }.detail-content h3 { margin: 0 0 11px; font-size: 14px; }.detail-tags { display: flex; flex-wrap: wrap; gap: 7px; }.description { color: #45524f; font-size: 14px; line-height: 1.8; white-space: pre-wrap; }
@media (max-width: 1280px) { .filter-band { grid-template-columns: 1.5fr 120px 1.4fr 110px; }.position-results.card { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 760px) {
  .filter-band { grid-template-columns: 1fr 1fr; }.filter-band > :first-child, .salary-range { grid-column: span 2; }
  .position-results.card { grid-template-columns: 1fr; }.position-results.list .position-item { display: block; }.position-results.list .skill-tags { margin-top: 15px; }
  .analysis-grid { grid-template-columns: 1fr; }.pagination { overflow-x: auto; justify-content: flex-start; }.result-toolbar { align-items: flex-start; gap: 8px; }.result-toolbar > div { flex-wrap: wrap; justify-content: flex-end; }
  .compare-bar { right: 8px; bottom: 70px; left: 8px; max-width: none; flex-wrap: wrap; }.compare-names { flex: 1 1 100%; order: -1; overflow-x: auto; }
}
</style>
