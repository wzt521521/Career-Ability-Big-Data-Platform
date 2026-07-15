<script setup>
import { computed, ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Delete, RefreshRight, View, Search } from '@element-plus/icons-vue'
import {
  getReportTemplates,
  generateReport,
  getReports,
  getReportStatus,
  downloadReportFile,
  previewReportFile,
  readReportBinaryError,
  deleteReport,
} from '../../api/report'
import PageContainer from '../../components/common/PageContainer.vue'
import PageHeader from '../../components/common/PageHeader.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import { useUserStore } from '../../stores/user'

const POLL_INTERVAL_MS = 2000
const MAX_POLL_ATTEMPTS = 150
const userStore = useUserStore()

const loading = ref(false)
const reports = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0, totalPages: 0 })
const activeTab = ref('')
const searchKeyword = ref('')

const templates = ref([])
const generateDialog = ref(false)
const generating = ref(false)
const pollingTimers = new Map()
let unmounted = false
const canGenerate = computed(() => userStore.hasPermission('report:generate'))
const canDelete = computed(() => userStore.hasPermission('report:delete'))

const generateForm = reactive({
  templateId: null,
  title: '',
  timeRangeStart: '',
  timeRangeEnd: '',
  city: '',
  position: '',
  industry: '',
})

const statusLabels = {
  PENDING: { text: '等待中', type: 'info' },
  GENERATING: { text: '生成中', type: 'warning' },
  COMPLETED: { text: '已完成', type: 'success' },
  FAILED: { text: '失败', type: 'danger' },
}

function formatFileSize(bytes) {
  if (!bytes) return '--'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function loadReports() {
  loading.value = true
  try {
    const params = { page: pagination.page, size: pagination.size }
    if (activeTab.value) params.status = activeTab.value
    if (searchKeyword.value) params.keyword = searchKeyword.value
    const res = await getReports(params)
    reports.value = res.data?.content || []
    pagination.total = res.data?.totalElements || 0
    pagination.totalPages = res.data?.totalPages || 0
  } catch {
    ElMessage.error('加载报告列表失败')
  } finally {
    loading.value = false
  }
}

async function loadTemplates() {
  try {
    const res = await getReportTemplates()
    templates.value = res.data || []
    if (templates.value.length && !generateForm.templateId) {
      generateForm.templateId = templates.value[0].id
    }
  } catch {
    // Templates unavailable
  }
}

function openGenerateDialog() {
  const now = new Date()
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)
  generateForm.timeRangeStart = monthStart.toISOString().slice(0, 10)
  generateForm.timeRangeEnd = now.toISOString().slice(0, 10)
  const selectedTemplate = templates.value.find((t) => t.id === generateForm.templateId)
  const tplName = selectedTemplate?.templateName || '报告'
  generateForm.title = tplName + '-' + now.toISOString().slice(0, 10)
  generateDialog.value = true
}

async function handleGenerate() {
  if (!generateForm.templateId || !generateForm.title) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (generateForm.timeRangeStart && generateForm.timeRangeEnd
      && generateForm.timeRangeStart > generateForm.timeRangeEnd) {
    ElMessage.warning('开始日期不能晚于结束日期')
    return
  }
  generating.value = true
  try {
    const res = await generateReport({
      templateId: generateForm.templateId,
      title: generateForm.title,
      timeRangeStart: generateForm.timeRangeStart || null,
      timeRangeEnd: generateForm.timeRangeEnd || null,
      city: generateForm.city || null,
      position: generateForm.position || null,
      industry: generateForm.industry || null,
    })
    generateDialog.value = false
    ElMessage.success('报告生成任务已提交')
    await loadReports()
    startPolling(res.data.id)
  } catch {
    ElMessage.error('提交生成任务失败')
  } finally {
    generating.value = false
  }
}

function startPolling(recordId) {
  if (pollingTimers.has(recordId) || unmounted) return
  const state = { attempts: 0, inFlight: false, timer: null }
  state.timer = setInterval(async () => {
    if (state.inFlight || unmounted) return
    state.attempts += 1
    if (state.attempts > MAX_POLL_ATTEMPTS) {
      stopPolling(recordId)
      ElMessage.error('报告生成超时，请稍后刷新状态')
      return
    }
    state.inFlight = true
    try {
      const res = await getReportStatus(recordId)
      const status = res.data?.status
      if (status === 'COMPLETED' || status === 'FAILED') {
        stopPolling(recordId)
        if (status === 'COMPLETED') {
          ElMessage.success('报告生成完成')
        } else {
          ElMessage.error('报告生成失败：' + (res.data?.errorMsg || '未知错误'))
        }
        await loadReports()
      }
    } catch {
      stopPolling(recordId)
      if (!unmounted) ElMessage.error('报告状态查询失败')
    } finally {
      state.inFlight = false
    }
  }, POLL_INTERVAL_MS)
  pollingTimers.set(recordId, state)
}

function stopPolling(recordId) {
  const state = pollingTimers.get(recordId)
  if (state) {
    clearInterval(state.timer)
    pollingTimers.delete(recordId)
  }
}

async function saveBinary(blob, id, preview) {
  const url = URL.createObjectURL(blob)
  if (preview) {
    window.open(url, '_blank', 'noopener')
  } else {
    const link = document.createElement('a')
    link.href = url
    link.download = `report-${id}.pdf`
    document.body.appendChild(link)
    link.click()
    link.remove()
  }
  window.setTimeout(() => URL.revokeObjectURL(url), 60000)
}

async function downloadReport(id) {
  try {
    await saveBinary(await downloadReportFile(id), id, false)
  } catch (error) {
    ElMessage.error(await readReportBinaryError(error))
  }
}

async function previewReport(id) {
  try {
    await saveBinary(await previewReportFile(id), id, true)
  } catch (error) {
    ElMessage.error(await readReportBinaryError(error))
  }
}

async function handleDelete(id) {
  // 清理该报告的轮询定时器
  stopPolling(id)
  try {
    await ElMessageBox.confirm('确定要删除这份报告吗？删除后不可恢复。', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteReport(id)
    ElMessage.success('报告已删除')
    await loadReports()
  } catch {
    // Cancelled
  }
}

function handleSearch() {
  pagination.page = 1
  loadReports()
}

function handleTabChange(tab) {
  activeTab.value = tab
  pagination.page = 1
  loadReports()
}

function handlePageChange(page) {
  pagination.page = page
  loadReports()
}

onMounted(async () => {
  await loadTemplates()
  await loadReports()
})

onUnmounted(() => {
  unmounted = true
  pollingTimers.forEach((_, id) => stopPolling(id))
})
</script>

<template>
  <PageContainer>
    <PageHeader
      title="报告中心"
      description="生成、预览和下载就业市场分析报告"
    >
      <template #actions>
        <el-button
          v-if="canGenerate"
          type="primary"
          :icon="Plus"
          @click="openGenerateDialog"
        >
          生成新报告
        </el-button>
      </template>
    </PageHeader>

    <el-tabs
      v-model="activeTab"
      @tab-change="handleTabChange"
    >
      <el-tab-pane
        label="全部"
        name=""
      />
      <el-tab-pane
        label="生成中"
        name="GENERATING"
      />
      <el-tab-pane
        label="已完成"
        name="COMPLETED"
      />
      <el-tab-pane
        label="失败"
        name="FAILED"
      />
    </el-tabs>

    <div class="search-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索报告标题..."
        :prefix-icon="Search"
        clearable
        style="width: 320px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #append>
          <el-button
            :icon="Search"
            @click="handleSearch"
          >
            搜索
          </el-button>
        </template>
      </el-input>
    </div>

    <div
      v-if="loading"
      v-loading="loading"
      class="report-list-loading"
      style="min-height: 300px"
    />

    <div
      v-else-if="!reports.length"
      style="padding: 60px 0"
    >
      <EmptyState
        icon="Document"
        title="暂无报告"
        description="点击上方「生成新报告」按钮，创建你的第一份就业分析报告"
      />
    </div>

    <template v-else>
      <div class="report-table-wrap">
        <el-table
          :data="reports"
          stripe
          style="width: 100%"
        >
          <el-table-column
            prop="reportTitle"
            label="报告标题"
            min-width="200"
          />
          <el-table-column
            label="模板"
            width="130"
          >
            <template #default="{ row }">
              <span class="template-name">{{ row.templateName || '--' }}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="状态"
            width="110"
          >
            <template #default="{ row }">
              <el-tag
                :type="statusLabels[row.status]?.type || 'info'"
                size="small"
                :class="{ 'is-loading-tag': row.status === 'GENERATING' }"
              >
                {{ statusLabels[row.status]?.text || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="时间范围"
            width="200"
          >
            <template #default="{ row }">
              <span v-if="row.timeRangeStart || row.timeRangeEnd">
                {{ row.timeRangeStart || '--' }} ~ {{ row.timeRangeEnd || '--' }}
              </span>
              <span
                v-else
                style="color: #aaa"
              >--</span>
            </template>
          </el-table-column>
          <el-table-column
            label="文件大小"
            width="100"
          >
            <template #default="{ row }">
              {{ formatFileSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column
            label="创建时间"
            width="170"
          >
            <template #default="{ row }">
              {{ row.createTime ? row.createTime.slice(0, 16).replace('T', ' ') : '--' }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="180"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'COMPLETED'"
                size="small"
                text
                type="primary"
                :icon="View"
                @click="previewReport(row.id)"
              >
                预览
              </el-button>
              <el-button
                v-if="row.status === 'COMPLETED'"
                size="small"
                text
                type="primary"
                :icon="Download"
                @click="downloadReport(row.id)"
              >
                下载
              </el-button>
              <el-button
                v-if="row.status === 'PENDING' || row.status === 'GENERATING'"
                size="small"
                text
                :icon="RefreshRight"
                @click="startPolling(row.id)"
              >
                刷新
              </el-button>
              <el-button
                v-if="canDelete"
                size="small"
                text
                type="danger"
                :icon="Delete"
                @click="handleDelete(row.id)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div
        v-if="pagination.totalPages > 1"
        style="display: flex; justify-content: center; margin-top: 24px"
      >
        <el-pagination
          :current-page="pagination.page"
          :page-size="pagination.size"
          :total="pagination.total"
          layout="prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </template>

    <!-- 生成报告弹窗 -->
    <el-dialog
      v-model="generateDialog"
      title="生成新报告"
      width="500px"
      class="report-generate-dialog"
      destroy-on-close
    >
      <el-form
        label-position="top"
        size="default"
      >
        <el-form-item label="报告模板">
          <el-select
            v-model="generateForm.templateId"
            style="width: 100%"
          >
            <el-option
              v-for="tpl in templates"
              :key="tpl.id"
              :label="tpl.templateName"
              :value="tpl.id"
            >
              <span>{{ tpl.templateName }}</span>
              <span style="float:right;color:#aaa;font-size:12px">{{ tpl.templateType }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="报告标题">
          <el-input
            v-model="generateForm.title"
            placeholder="输入报告标题"
            maxlength="120"
          />
        </el-form-item>
        <el-form-item label="城市筛选（可选）">
          <el-input v-model.trim="generateForm.city" maxlength="100" />
        </el-form-item>
        <el-form-item label="岗位筛选（可选）">
          <el-input v-model.trim="generateForm.position" maxlength="100" />
        </el-form-item>
        <el-form-item label="行业筛选（可选）">
          <el-input v-model.trim="generateForm.industry" maxlength="100" />
        </el-form-item>
        <el-form-item label="数据时间范围（可选）">
          <div class="date-range">
            <el-date-picker
              v-model="generateForm.timeRangeStart"
              type="date"
              placeholder="起始日期"
              value-format="YYYY-MM-DD"
              style="flex:1"
            />
            <span class="date-range-separator">至</span>
            <el-date-picker
              v-model="generateForm.timeRangeEnd"
              type="date"
              placeholder="截止日期"
              value-format="YYYY-MM-DD"
              style="flex:1"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialog = false">
          取消
        </el-button>
        <el-button
          v-if="canGenerate"
          type="primary"
          :loading="generating"
          @click="handleGenerate"
        >
          确认生成
        </el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.search-bar {
  margin-bottom: var(--space-4);
}

.report-table-wrap {
  min-width: 0;
  overflow-x: auto;
}

.date-range {
  display: flex;
  gap: var(--space-3);
  width: 100%;
}

.date-range :deep(.el-date-editor) {
  flex: 1;
  min-width: 0;
}

.date-range-separator {
  flex: 0 0 auto;
  align-self: center;
  color: var(--app-muted);
}

@media (max-width: 768px) {
  .search-bar :deep(.el-input) {
    width: 100% !important;
  }
}

@media (max-width: 540px) {
  .date-range {
    flex-direction: column;
    gap: var(--space-2);
  }

  .date-range-separator {
    display: none;
  }

  :deep(.report-generate-dialog) {
    width: calc(100vw - 32px) !important;
    margin: var(--space-4) auto;
  }
}

.template-name {
  color: var(--app-muted);
  font-size: var(--font-size-12);
}

.is-loading-tag {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}
</style>
