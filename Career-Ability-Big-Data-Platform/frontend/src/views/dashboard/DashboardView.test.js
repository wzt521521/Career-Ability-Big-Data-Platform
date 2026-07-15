import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardView from './DashboardView.vue'

const analyticsApi = vi.hoisted(() => ({ getDashboard: vi.fn() }))

vi.mock('../../api/analytics.js', () => analyticsApi)

const stubs = {
  ChartPanel: {
    props: ['empty', 'loading', 'title'],
    template: '<section class="chart-stub" :data-empty="String(Boolean(empty))">{{ title }}</section>',
  },
  ElAlert: { props: ['title'], template: '<div class="alert-stub">{{ title }}</div>' },
  ElButton: { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  ElIcon: { template: '<i><slot /></i>' },
  ElOption: true,
  ElSelect: { template: '<select><slot /></select>' },
}

function mountView() {
  return mount(DashboardView, {
    global: { directives: { loading: {} }, stubs },
  })
}

describe('DashboardView', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(() => vi.useRealTimers())

  it('renders empty chart states when a successful response omits optional dimensions', async () => {
    analyticsApi.getDashboard.mockResolvedValue({ overview: { totalPositions: 0 }, trends: {} })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('岗位总量')
    expect(wrapper.findAll('.chart-stub').filter(chart => chart.attributes('data-empty') === 'true')).toHaveLength(5)
  })

  it('keeps the dashboard visible and reports a backend failure', async () => {
    analyticsApi.getDashboard.mockRejectedValue(new Error('unavailable'))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('.alert-stub').text()).toContain('统计数据暂时无法加载')
    expect(wrapper.findAll('.chart-stub')).toHaveLength(5)
  })

  it('refreshes with the configured interval', async () => {
    vi.useFakeTimers()
    analyticsApi.getDashboard.mockResolvedValue({})
    mountView()
    await flushPromises()

    await vi.advanceTimersByTimeAsync(60_000)

    expect(analyticsApi.getDashboard).toHaveBeenCalledTimes(2)
  })

  it('keeps every metric and chart reachable on a narrow mobile viewport', async () => {
    const originalWidth = window.innerWidth
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 375 })
    analyticsApi.getDashboard.mockResolvedValue({})

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('.metric')).toHaveLength(4)
    expect(wrapper.findAll('.chart-stub')).toHaveLength(5)
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalWidth })
  })
})
