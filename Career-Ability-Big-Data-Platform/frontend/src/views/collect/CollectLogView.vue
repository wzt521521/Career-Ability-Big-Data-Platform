<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { listLogs, listTaskLogs, listTasks } from '../../api/collect.js'

const loading = ref(false)
const logs = ref([])
const tasks = ref([])
const selectedTaskId = ref(null)
const detailVisible = ref(false)
const detailLog = ref(null)
const refreshedAt = ref(null)

const stats = computed(() => {
  const total = logs.value.length
  const running = logs.value.filter(l => logStatus(l).code === 'RUNNING').length
  const success = logs.value.filter(l => logStatus(l).code === 'SUCCESS').length
  const partial = logs.value.filter(l => logStatus(l).code === 'PARTIAL').length
  const failure = logs.value.filter(l => logStatus(l).code === 'FAILED').length
  const totalRecords = logs.value.reduce((sum, l) => sum + (l.totalCount || 0), 0)
  const totalSuccess = logs.value.reduce((sum, l) => sum + (l.successCount || 0), 0)
  const totalFail = logs.value.reduce((sum, l) => sum + (l.failCount || 0), 0)
  return { total, running, success, partial, failure, totalRecords, totalSuccess, totalFail }
})

async function load() {
  loading.value = true
  try {
    const [logList, taskList] = await Promise.all([listLogs(), listTasks()])
    logs.value = logList
    tasks.value = taskList
    refreshedAt.value = new Date()
  } finally {
    loading.value = false
  }
}

async function filterByTask(taskId) {
  loading.value = true
  selectedTaskId.value = taskId
  try {
    logs.value = taskId ? await listTaskLogs(taskId, { page: 1, size: 100 }) : await listLogs()
  } finally {
    loading.value = false
  }
}

function showDetail(log) {
  detailLog.value = log
  detailVisible.value = true
}

function taskName(taskId) {
  return tasks.value.find(t => t.id === taskId)?.taskName || `ID:${taskId}`
}

function successRate(log) {
  if (!log.totalCount) return '—'
  return Math.round((log.successCount / log.totalCount) * 100) + '%'
}

function duration(log) {
  if (!log.startTime || !log.endTime) return '—'
  const ms = new Date(log.endTime) - new Date(log.startTime)
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

function logStatus(log) {
  if (!log.endTime) return { code: 'RUNNING', type: 'warning', text: '运行中' }
  if (log.errorMsg || (log.failCount > 0 && !log.successCount)) {
    return { code: 'FAILED', type: 'danger', text: '失败' }
  }
  if (log.failCount > 0) return { code: 'PARTIAL', type: 'warning', text: '部分完成' }
  return { code: 'SUCCESS', type: 'success', text: '成功' }
}

function retryInfo(log) {
  const recordedAttempt = log.errorMsg?.match(/(?:第 |已完成 )(\d+)\/(\d+) 次重试/)
  if (recordedAttempt) return `${recordedAttempt[1]}/${recordedAttempt[2]}`
  const task = tasks.value.find(t => t.id === log.taskId)
  if (!task) return '—'
  return `${task.retryCount ?? 0}/${task.maxRetries ?? 3}`
}

onMounted(load)
</script>

<template>
  <div class="page collect-log-page">
    <div class="page-toolbar">
      <h2>采集执行日志</h2>
      <div class="toolbar-right">
        <span class="refresh-note" v-if="refreshedAt">更新于 {{ refreshedAt.toLocaleTimeString('zh-CN', { hour12: false }) }}</span>
        <el-button :icon="Refresh" :loading="loading" title="刷新" circle @click="load" />
      </div>
    </div>

    <!-- 日志汇总统计 -->
    <section class="log-stats">
      <div class="log-stat-card"><strong>{{ stats.total }}</strong><span>日志总数</span></div>
      <div class="log-stat-card running"><strong>{{ stats.running }}</strong><span>运行中</span></div>
      <div class="log-stat-card success"><strong>{{ stats.success }}</strong><span>全部成功</span></div>
      <div class="log-stat-card partial"><strong>{{ stats.partial }}</strong><span>部分成功</span></div>
      <div class="log-stat-card failure"><strong>{{ stats.failure }}</strong><span>失败</span></div>
      <div class="log-stat-card"><strong>{{ stats.totalRecords }}</strong><span>处理记录</span></div>
      <div class="log-stat-card"><strong>{{ stats.totalSuccess }}/{{ stats.totalFail }}</strong><span>成功/失败</span></div>
    </section>

    <!-- 筛选 + 表格 -->
    <el-card shadow="never" style="border-radius:6px">
      <template #header>
        <div class="card-header">
          <span>日志列表</span>
          <el-select v-model="selectedTaskId" placeholder="按任务筛选" clearable style="width:220px" @change="filterByTask">
            <el-option v-for="t in tasks" :key="t.id" :label="t.taskName" :value="t.id" />
          </el-select>
        </div>
      </template>
      <el-table :data="logs" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="关联任务" minWidth="140">
          <template #default="{ row }">{{ taskName(row.taskId) }}</template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" minWidth="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="logStatus(row).type" size="small">{{ logStatus(row).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总数" width="70">
          <template #default="{ row }">{{ row.totalCount ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="成功" width="70">
          <template #default="{ row }">
            <span style="color:#2a9d78;font-weight:600">{{ row.successCount ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="失败" width="70">
          <template #default="{ row }">
            <span :style="{ color: row.failCount ? '#e16f50' : 'inherit', fontWeight: row.failCount ? 600 : 400 }">{{ row.failCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成功率" width="85">
          <template #default="{ row }">{{ successRate(row) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="80">
          <template #default="{ row }">{{ duration(row) }}</template>
        </el-table-column>
        <el-table-column label="重试" width="70">
          <template #default="{ row }">{{ retryInfo(row) }}</template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 日志详情弹窗 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="560px">
      <template v-if="detailLog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务">{{ taskName(detailLog.taskId) }}</el-descriptions-item>
          <el-descriptions-item label="文件">{{ detailLog.fileName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="logStatus(detailLog).type" size="small">{{ logStatus(detailLog).text }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="重试">{{ retryInfo(detailLog) }}</el-descriptions-item>
          <el-descriptions-item label="总数">{{ detailLog.totalCount ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="成功">{{ detailLog.successCount ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ detailLog.failCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="成功率">{{ successRate(detailLog) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ detailLog.startTime }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ detailLog.endTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ duration(detailLog) }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detailLog.errorMsg" class="error-block">
          <h4>异常信息</h4>
          <pre>{{ detailLog.errorMsg }}</pre>
        </div>
        <el-empty v-if="!detailLog.errorMsg" description="无异常信息" :image-size="48" style="margin-top:16px" />
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-toolbar h2 { margin: 0; font-size: 18px; font-weight: 650; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.refresh-note { color: var(--muted); font-size: 12px; }
.log-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(132px, 1fr)); gap: 12px; margin-bottom: 16px; }
.log-stat-card { display: flex; flex-direction: column; gap: 4px; padding: 14px 16px; border: 1px solid var(--line); border-radius: 6px; background: #fff; }
.log-stat-card strong { font-size: 22px; font-weight: 700; color: var(--ink); }
.log-stat-card span { font-size: 11px; color: var(--muted); white-space: nowrap; }
.log-stat-card.success { border-left: 3px solid #2a9d78; }
.log-stat-card.running { border-left: 3px solid #258478; }
.log-stat-card.partial { border-left: 3px solid #c89534; }
.log-stat-card.failure { border-left: 3px solid #e16f50; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.error-block { margin-top: 16px; }
.error-block h4 { margin: 0 0 6px; font-size: 14px; color: #e16f50; }
.error-block pre { margin: 0; padding: 12px; max-height: 200px; overflow-y: auto; border-radius: 4px; background: #fef5f3; color: #b03a2e; font-size: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-all; }
</style>
