<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { listSources, createSource, updateSource, deleteSource } from '../../api/collect.js'

const loading = ref(false)
const sources = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  sourceName: '',
  sourceType: 'FILE',
  filePath: '',
  importFrequency: 'ONCE',
  description: '',
  status: 1
})

const rules = {
  sourceName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  filePath: [{ required: true, message: '请输入文件路径', trigger: 'blur' }]
}

const columns = [
  { prop: 'id', label: 'ID', width: 70 },
  { prop: 'sourceName', label: '数据源名称', minWidth: 140 },
  { prop: 'sourceType', label: '来源类型', width: 100 },
  { prop: 'filePath', label: '文件路径', minWidth: 180 },
  { prop: 'importFrequency', label: '导入频率', width: 100 },
  { prop: 'description', label: '说明', minWidth: 120 },
  { prop: 'status', label: '状态', width: 80 }
]

async function load() {
  loading.value = true
  try {
    sources.value = await listSources()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { id: null, sourceName: '', sourceType: 'FILE', filePath: '', importFrequency: 'ONCE', description: '', status: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除数据源「${row.sourceName}」吗？`, '删除确认', { type: 'warning' })
    await deleteSource(row.id)
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
    await updateSource(form.id, payload)
    ElMessage.success('数据源已更新')
  } else {
    await createSource(payload)
    ElMessage.success('数据源已创建')
  }
  dialogVisible.value = false
  await load()
}

function statusTag(status) {
  return status === 1 ? { type: 'success', text: '启用' } : { type: 'info', text: '禁用' }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-toolbar">
      <h2>数据源管理</h2>
      <el-button v-permission="'collect:toggle'" type="primary" :icon="Plus" @click="openCreate">新增数据源</el-button>
    </div>

    <el-table :data="sources" v-loading="loading" stripe>
      <el-table-column v-for="col in columns" :key="col.prop" v-bind="col" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'collect:toggle'" type="primary" link :icon="Edit" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'collect:toggle'" type="danger" link :icon="Delete" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑数据源' : '新增数据源'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="sourceName">
          <el-input v-model="form.sourceName" placeholder="如：Kaggle-2024招聘数据" />
        </el-form-item>
        <el-form-item label="来源类型" prop="sourceType">
          <el-select v-model="form.sourceType" style="width:100%">
            <el-option label="文件 (FILE)" value="FILE" />
            <el-option label="URL" value="URL" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件路径" prop="filePath">
          <el-input v-model="form.filePath" placeholder="如：/data/kaggle_jobs.csv" />
        </el-form-item>
        <el-form-item label="导入频率" prop="importFrequency">
          <el-select v-model="form.importFrequency" style="width:100%">
            <el-option label="仅一次 (ONCE)" value="ONCE" />
            <el-option label="每日 (DAILY)" value="DAILY" />
            <el-option label="每周 (WEEKLY)" value="WEEKLY" />
            <el-option label="每月 (MONTHLY)" value="MONTHLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="数据来源、字段说明等" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
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
</style>
