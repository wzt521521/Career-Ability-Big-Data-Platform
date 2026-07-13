<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Delete, RefreshRight, View, Search } from '@element-plus/icons-vue'
import {
  getReportTemplates,
  generateReport,
  getReports,
  getReportStatus,
  getReportDownloadUrl,
  getReportPreviewUrl,
  deleteReport,
} from '../../api/report'
import PageContainer from '../../components/common/PageContainer.vue'
import PageHeader from '../../components/common/PageHeader.vue'
import EmptyState from '../../components/common/EmptyState.vue'

const loading = ref(false)
const reports = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0, totalPages: 0 })
const activeTab = ref('')
const searchKeyword = ref('')

const templates = ref([])
const generateDialog = ref(false)
const generating = ref(false)
const pollingTimers = ref({})

const generateForm = reactive({
  templateId: null,
  title: '',
  timeRangeStart: '',
  timeRangeEnd: '',
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
  generating.value = true
  try {
    const res = await generateReport({
      templateId: generateForm.templateId,
      title: generateForm.title,
      timeRangeStart: generateForm.timeRangeStart || null,
      timeRangeEnd: generateForm.timeRangeEnd || null,
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
  if (pollingTimers.value[recordId]) return
  pollingTimers.value[recordId] = setInterval(async () => {
    try {
      const res = await getReportStatus(recordId)
      const status = res.data?.status
      if (status === 'COMPLETED' || status === 'FAILED') {
        clearInterval(pollingTimers.value[recordId])
        delete pollingTimers.value[recordId]
        if (status === 'COMPLETED') {
          ElMessage.success('报告生成完成')
        } else {
          ElMessage.error('报告生成失败：' + (res.data?.errorMsg || '未知错误'))
        }
        await loadReports()
      }
    } catch {
      clearInterval(pollingTimers.value[recordId])
      delete pollingTimers.value[recordId]
    }
  }, 2000)
}

function downloadReport(id) {
  window.open(getReportDownloadUrl(id), '_blank')
}

function previewReport(id) {
  window.open(getReportPreviewUrl(id), '_blank')
}

async function handleDelete(id) {
  // 清理该报告的轮询定时器
  if (pollingTimers.value[id]) {
    clearInterval(pollingTimers.value[id])
    delete pollingTimers.value[id]
  }
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
  Object.values(pollingTimers.value).forEach(clearInterval)
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
              v-if="row.status === 'GENERATING'"
              size="small"
              text
              :icon="RefreshRight"
              @click="startPolling(row.id)"
            >
              刷新
            </el-button>
            <el-button
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
            maxlength="200"
          />
        </el-form-item>
        <el-form-item label="数据时间范围（可选）">
          <div style="display:flex;gap:12px;width:100%">
            <el-date-picker
              v-model="generateForm.timeRangeStart"
              type="date"
              placeholder="起始日期"
              value-format="YYYY-MM-DD"
              style="flex:1"
            />
            <span style="line-height:32px;color:#aaa">至</span>
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
