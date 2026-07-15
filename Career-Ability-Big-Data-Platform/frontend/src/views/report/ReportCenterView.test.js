import { flushPromises, mount } from '@vue/test-utils'
import { computed, h, inject, provide } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ReportCenterView from './ReportCenterView.vue'

const reportApi = vi.hoisted(() => ({
  deleteReport: vi.fn(),
  downloadReportFile: vi.fn(),
  generateReport: vi.fn(),
  getReportStatus: vi.fn(),
  getReportTemplates: vi.fn(),
  getReports: vi.fn(),
  previewReportFile: vi.fn(),
  readReportBinaryError: vi.fn(),
}))
const message = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}))
const userStore = vi.hoisted(() => ({
  hasPermission: vi.fn(() => true),
}))

vi.mock('element-plus', () => ({
  ElMessage: message,
  ElMessageBox: { confirm: vi.fn() },
}))
vi.mock('../../api/report', () => reportApi)
vi.mock('../../stores/user', () => ({ useUserStore: () => userStore }))

const tableRowsKey = Symbol('tableRows')

const ElTable = {
  props: ['data'],
  setup(props, { slots }) {
    provide(tableRowsKey, computed(() => props.data || []))
    return () => h('div', { class: 'table-stub' }, slots.default?.())
  },
}

const ElTableColumn = {
  setup(_, { slots }) {
    const rows = inject(tableRowsKey, computed(() => []))
    return () => h(
      'div',
      { class: 'table-column-stub' },
      rows.value.flatMap((row) => slots.default?.({ row }) || []),
    )
  },
}

const stubs = {
  ElButton: {
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: `
      <button
        :data-loading="String(Boolean(loading))"
        :disabled="disabled"
        @click="$emit('click', $event)"
      ><slot /></button>
    `,
  },
  ElDatePicker: true,
  ElDialog: { template: '<div><slot /><slot name="footer" /></div>' },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { template: '<div><slot /></div>' },
  ElIcon: { template: '<i><slot /></i>' },
  ElInput: { template: '<input />' },
  ElOption: true,
  ElPagination: true,
  ElSelect: { template: '<select><slot /></select>' },
  ElTabPane: true,
  ElTable,
  ElTableColumn,
  ElTabs: { template: '<div><slot /></div>' },
  ElTag: { template: '<span><slot /></span>' },
  EmptyState: {
    props: ['title'],
    template: '<div class="empty-state-stub">{{ title }}</div>',
  },
  PageContainer: { template: '<section><slot /></section>' },
  PageHeader: { template: '<header><slot name="actions" /></header>' },
}

function page(content = []) {
  return {
    data: {
      content,
      totalElements: content.length,
      totalPages: content.length ? 1 : 0,
    },
  }
}

function generatingReport() {
  return {
    id: 7,
    reportTitle: 'Monthly report',
    status: 'GENERATING',
  }
}

function mountView() {
  return mount(ReportCenterView, {
    global: {
      directives: { loading: {} },
      stubs,
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  reportApi.getReportTemplates.mockResolvedValue({ data: [] })
  reportApi.getReports.mockResolvedValue(page())
  reportApi.readReportBinaryError.mockResolvedValue('报告文件请求失败')
  userStore.hasPermission.mockReturnValue(true)
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('ReportCenterView', () => {
  it('renders a loading state until the initial report list request resolves', async () => {
    let resolveReports
    reportApi.getReports.mockReturnValue(new Promise((resolve) => {
      resolveReports = resolve
    }))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('.report-list-loading').exists()).toBe(true)

    resolveReports(page())
    await flushPromises()

    expect(wrapper.find('.report-list-loading').exists()).toBe(false)
  })

  it('shows the empty state after a successful report request with no records', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('.empty-state-stub').text()).toBe('暂无报告')
  })

  it('reports list failures and restores the empty fallback', async () => {
    reportApi.getReports.mockRejectedValue(new Error('service unavailable'))

    const wrapper = mountView()
    await flushPromises()

    expect(message.error).toHaveBeenCalledWith('加载报告列表失败')
    expect(wrapper.find('.report-list-loading').exists()).toBe(false)
    expect(wrapper.get('.empty-state-stub').text()).toBe('暂无报告')
  })

  it('stops polling and refreshes the list after a report completes', async () => {
    reportApi.getReports
      .mockResolvedValueOnce(page([generatingReport()]))
      .mockResolvedValueOnce(page([{ ...generatingReport(), status: 'COMPLETED' }]))
    reportApi.getReportStatus.mockResolvedValue({ data: { status: 'COMPLETED' } })

    const wrapper = mountView()
    await flushPromises()
    vi.useFakeTimers()

    const refreshButton = wrapper.findAll('button').find((button) => button.text() === '刷新')
    expect(refreshButton).toBeDefined()
    await refreshButton.trigger('click')
    await vi.advanceTimersByTimeAsync(2000)

    expect(message.success).toHaveBeenCalledWith('报告生成完成')
    expect(reportApi.getReports).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(4000)
    expect(reportApi.getReportStatus).toHaveBeenCalledTimes(1)
  })

  it('stops polling and surfaces the backend error when a report fails', async () => {
    reportApi.getReports
      .mockResolvedValueOnce(page([generatingReport()]))
      .mockResolvedValueOnce(page([{ ...generatingReport(), status: 'FAILED' }]))
    reportApi.getReportStatus.mockResolvedValue({
      data: { errorMsg: 'renderer unavailable', status: 'FAILED' },
    })

    const wrapper = mountView()
    await flushPromises()
    vi.useFakeTimers()

    const refreshButton = wrapper.findAll('button').find((button) => button.text() === '刷新')
    expect(refreshButton).toBeDefined()
    await refreshButton.trigger('click')
    await vi.advanceTimersByTimeAsync(2000)

    expect(message.error).toHaveBeenCalledWith('报告生成失败：renderer unavailable')
    expect(reportApi.getReports).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(4000)
    expect(reportApi.getReportStatus).toHaveBeenCalledTimes(1)
  })

  it('cleans polling timers when the component unmounts', async () => {
    reportApi.getReports.mockResolvedValueOnce(page([generatingReport()]))
    const wrapper = mountView()
    await flushPromises()
    vi.useFakeTimers()

    const refreshButton = wrapper.findAll('button').find((button) => button.text() === '刷新')
    await refreshButton.trigger('click')
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(4000)

    expect(reportApi.getReportStatus).not.toHaveBeenCalled()
  })

  it('shows a parsed binary download error instead of navigating to an unauthenticated URL', async () => {
    reportApi.getReports.mockResolvedValueOnce(page([{ ...generatingReport(), status: 'COMPLETED' }]))
    reportApi.downloadReportFile.mockRejectedValue(new Error('binary failure'))
    reportApi.readReportBinaryError.mockResolvedValue('没有下载权限')
    const wrapper = mountView()
    await flushPromises()

    const downloadButton = wrapper.findAll('button').find((button) => button.text() === '下载')
    await downloadButton.trigger('click')
    await flushPromises()

    expect(reportApi.downloadReportFile).toHaveBeenCalledWith(7)
    expect(message.error).toHaveBeenCalledWith('没有下载权限')
  })

  it('hides mutating controls when the current user lacks report permissions', async () => {
    userStore.hasPermission.mockReturnValue(false)
    reportApi.getReports.mockResolvedValueOnce(page([{ ...generatingReport(), status: 'COMPLETED' }]))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('button').map((button) => button.text())).not.toContain('生成新报告')
    expect(wrapper.findAll('button').map((button) => button.text())).not.toContain('删除')
  })
})
