<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, VideoPlay, VideoPause, Refresh } from '@element-plus/icons-vue'
import { listTasks, createTask, updateTask, deleteTask, listSources, pauseTask, resumeTask, runTask } from '../../api/collect.js'
import { init } from '../../utils/echarts.js'

const loading = ref(false)
const tasks = ref([])
const sources = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const statsRefreshedAt = ref(null)

const form = reactive({
  id: null,
  sourceId: null,
  taskName: '',
  cronExpression: '',
  status: 'IDLE',
  maxRetries: 3,
  retryCount: 0
})

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  sourceId: [{ required: true, message: '请选择关联数据源', trigger: 'change' }]
}

// ---- 统计卡片 ----
const stats = computed(() => {
  const total = tasks.value.length
  const running = tasks.value.filter(t => t.status === 'RUNNING').length
  const idle = tasks.value.filter(t => t.status === 'IDLE').length
  const failed = tasks.value.filter(t => t.status === 'FAILED').length
  return { total, running, idle, failed }
})

// ---- 任务状态分布饼图 ----
const taskChartEl = ref(null)
let taskChart

const chartOption = computed(() => ({
  tooltip: { trigger: 'item', backgroundColor: '#17201f', borderWidth: 0, textStyle: { color: '#fff' } },
  legend: { bottom: 0, textStyle: { color: '#697774' } },
  color: ['#2a9d78', '#258478', '#e16f50', '#91a39f'],
  series: [{
    name: '任务状态', type: 'pie', radius: ['44%', '70%'], center: ['50%', '44%'],
    label: { formatter: '{b}\n{d}%', color: '#45524f' },
    data: [
      { name: '运行中', value: stats.value.running },
      { name: '空闲', value: stats.value.idle },
      { name: '失败', value: stats.value.failed }
    ].filter(d => d.value > 0)
  }]
}))

function renderChart() {
  if (!taskChartEl.value) return
  if (!taskChart) taskChart = init(taskChartEl.value)
  taskChart.setOption(chartOption.value, true)
}

// ---- 数据加载 ----
async function load() {
  loading.value = true
  try {
    const [taskList, sourceList] = await Promise.all([listTasks(), listSources()])
    tasks.value = taskList
    sources.value = sourceList
    statsRefreshedAt.value = new Date()
    setTimeout(renderChart, 80)
  } finally {
    loading.value = false
  }
}

function sourceName(sourceId) {
  return sources.value.find(s => s.id === sourceId)?.sourceName || `ID:${sourceId}`
}

// ---- 任务开关 ----
async function toggleStatus(row) {
  const action = row.status === 'RUNNING'
    ? pauseTask
    : row.status === 'PAUSED'
      ? resumeTask
      : runTask
  const actionLabel = row.status === 'RUNNING' ? '已暂停' : row.status === 'PAUSED' ? '已恢复' : '已启动'
  try {
    const updated = await action(row.id)
    Object.assign(row, updated)
    ElMessage.success(actionLabel)
    setTimeout(renderChart, 100)
  } catch {
    ElMessage.error('任务状态切换失败')
  }
}

// ---- 表单操作 ----
function openCreate() {
  isEdit.value = false
  Object.assign(form, { id: null, sourceId: sources.value[0]?.id || null, taskName: '', cronExpression: '', status: 'IDLE', maxRetries: 3, retryCount: 0 })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除任务「${row.taskName}」吗？`, '删除确认', { type: 'warning' })
    await deleteTask(row.id)
    ElMessage.success('已删除')
    await load()
  } catch { /* cancelled */ }
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload = { ...form }
  delete payload.id
  delete payload.createTime
  delete payload.updateTime
  if (isEdit.value) {
    await updateTask(form.id, payload)
    ElMessage.success('任务已更新')
  } else {
    payload.status = payload.cronExpression?.trim() ? 'SCHEDULED' : 'IDLE'
    await createTask(payload)
    ElMessage.success('任务已创建')
  }
  dialogVisible.value = false
  await load()
}

function statusTag(status) {
  const map = {
    RUNNING: { type: 'success', text: '运行中' },
    SCHEDULED: { type: 'primary', text: '已调度' },
    IDLE: { type: 'info', text: '空闲' },
    FAILED: { type: 'danger', text: '失败' },
    ERROR: { type: 'danger', text: '异常' },
    PAUSED: { type: 'warning', text: '已暂停' }
  }
  return map[status] || { type: 'info', text: status }
}

onMounted(load)
onBeforeUnmount(() => taskChart?.dispose())
</script>

<template>
  <div class="page collect-task-page">
    <div class="page-toolbar">
      <h2>采集任务管理</h2>
      <div class="toolbar-right">
        <span class="refresh-note" v-if="statsRefreshedAt">更新于 {{ statsRefreshedAt.toLocaleTimeString('zh-CN', { hour12: false }) }}</span>
        <el-button :icon="Refresh" :loading="loading" title="刷新" circle @click="load" />
        <el-button v-permission="'collect:toggle'" type="primary" :icon="Plus" @click="openCreate">新增任务</el-button>
      </div>
    </div>

    <!-- 任务状态概览卡片 -->
    <section class="stat-cards">
      <div class="stat-card"><strong>{{ stats.total }}</strong><span>任务总数</span></div>
      <div class="stat-card running"><strong>{{ stats.running }}</strong><span>运行中</span></div>
      <div class="stat-card idle"><strong>{{ stats.idle }}</strong><span>空闲</span></div>
      <div class="stat-card failed"><strong>{{ stats.failed }}</strong><span>失败</span></div>
    </section>

    <!-- 状态分布图 + 任务列表 -->
    <section class="task-body">
      <div class="task-chart-panel">
        <h3>任务状态分布</h3>
        <div ref="taskChartEl" class="chart-box" />
      </div>
      <div class="task-table-panel">
        <el-table :data="tasks" v-loading="loading" stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="taskName" label="任务名称" minWidth="140" />
          <el-table-column label="关联数据源" minWidth="140">
            <template #default="{ row }">{{ sourceName(row.sourceId) }}</template>
          </el-table-column>
          <el-table-column prop="cronExpression" label="Cron表达式" width="140" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).text }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastRunTime" label="上次执行" width="170" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'collect:toggle'" type="success" link size="small" :icon="row.status === 'RUNNING' ? VideoPause : VideoPlay" @click="toggleStatus(row)">
                {{ row.status === 'RUNNING' ? '暂停' : row.status === 'PAUSED' ? '恢复' : '启动' }}
              </el-button>
              <el-button v-permission="'collect:toggle'" type="primary" link :icon="Edit" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button v-permission="'collect:toggle'" type="danger" link :icon="Delete" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑任务' : '新增任务'" width="540px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="如：每日Kaggle数据导入" />
        </el-form-item>
        <el-form-item label="关联数据源" prop="sourceId">
          <el-select v-model="form.sourceId" style="width:100%" placeholder="选择数据源">
            <el-option v-for="s in sources" :key="s.id" :label="s.sourceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron表达式" prop="cronExpression">
          <el-input v-model="form.cronExpression" placeholder="如：0 0 6 * * ?（每天6点）" />
        </el-form-item>
        <el-form-item label="最大重试次数">
          <el-input-number v-model="form.maxRetries" :min="0" :max="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-toolbar h2 { margin: 0; font-size: 18px; font-weight: 650; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.refresh-note { color: var(--muted); font-size: 12px; }
.stat-cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 16px; }
.stat-card { display: flex; flex-direction: column; gap: 6px; padding: 16px 18px; border: 1px solid var(--line); border-radius: 6px; background: #fff; }
.stat-card strong { font-size: 28px; font-weight: 700; color: var(--ink); }
.stat-card span { font-size: 12px; color: var(--muted); }
.stat-card.running { border-left: 3px solid #2a9d78; }
.stat-card.idle { border-left: 3px solid #258478; }
.stat-card.failed { border-left: 3px solid #e16f50; }
.task-body { display: grid; grid-template-columns: 280px 1fr; gap: 16px; }
.task-chart-panel { min-width: 0; padding: 16px 18px; border: 1px solid var(--line); border-radius: 6px; background: #fff; }
.task-chart-panel h3 { margin: 0 0 4px; font-size: 15px; font-weight: 650; }
.chart-box { width: 100%; height: 240px; }
.task-table-panel { min-width: 0; }
@media (max-width: 960px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .task-body { grid-template-columns: 1fr; }
}
@media (max-width: 430px) {
  .stat-cards { grid-template-columns: 1fr 1fr; }
}
</style>
