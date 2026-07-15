import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PositionView from './PositionView.vue'

const positionApi = vi.hoisted(() => ({
  comparePositions: vi.fn(),
  getPosition: vi.fn(),
  getPositions: vi.fn(),
  getSuggestions: vi.fn(),
}))
const analyticsApi = vi.hoisted(() => ({ getCityStats: vi.fn(), getSkillStats: vi.fn() }))
const message = vi.hoisted(() => ({ error: vi.fn(), warning: vi.fn() }))

vi.mock('../../api/positions.js', () => positionApi)
vi.mock('../../api/analytics.js', () => analyticsApi)
vi.mock('../../utils/echarts.js', () => ({ registerMap: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: message }))

const stubs = {
  ChartPanel: true,
  ElAlert: { props: ['title'], template: '<div class="alert-stub">{{ title }}</div>' },
  ElAutocomplete: true,
  ElButton: { props: ['disabled', 'loading'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  ElCheckbox: true,
  ElDialog: { template: '<div><slot /></div>' },
  ElDrawer: { template: '<div><slot /></div>' },
  ElEmpty: { props: ['description'], template: '<div class="empty-stub">{{ description }}</div>' },
  ElIcon: { template: '<i><slot /></i>' },
  ElInputNumber: true,
  ElLink: true,
  ElOption: true,
  ElPagination: true,
  ElRadioButton: true,
  ElRadioGroup: true,
  ElSelect: { template: '<select><slot /></select>' },
  ElTable: { template: '<table><slot /></table>' },
  ElTableColumn: true,
  ElTag: { template: '<span><slot /></span>' },
}

function mountView() {
  return mount(PositionView, {
    global: { directives: { loading: {} }, stubs },
  })
}

describe('PositionView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    positionApi.getPositions.mockResolvedValue({ content: [], totalElements: 0 })
    analyticsApi.getSkillStats.mockResolvedValue(null)
    analyticsApi.getCityStats.mockResolvedValue({})
  })

  it('shows an empty result without crashing when optional analytics fields are missing', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('.empty-stub').text()).toBe('没有符合条件的岗位')
    expect(wrapper.vm.skillStats).toEqual({ topSkills: [] })
    expect(wrapper.vm.cityStats).toEqual({ ranking: [] })
  })

  it('reports a list request failure while retaining the empty fallback', async () => {
    positionApi.getPositions.mockRejectedValue(new Error('offline'))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('.alert-stub').text()).toContain('岗位数据暂时无法加载')
    expect(wrapper.get('.empty-stub').text()).toBe('没有符合条件的岗位')
  })

  it('uses the server comparison contract for two selected positions', async () => {
    positionApi.comparePositions.mockResolvedValue([{ id: 1, title: 'Java工程师' }, { id: 2, title: '数据工程师' }])
    const wrapper = mountView()
    await flushPromises()
    wrapper.vm.compareItems = [{ id: 1 }, { id: 2 }]

    await wrapper.vm.openComparison()

    expect(positionApi.comparePositions).toHaveBeenCalledWith([1, 2])
    expect(wrapper.vm.compareVisible).toBe(true)
  })

  it('keeps the list, filters, and empty fallback available on a narrow mobile viewport', async () => {
    const originalWidth = window.innerWidth
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 375 })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('.filter-band').exists()).toBe(true)
    expect(wrapper.find('.position-results').exists()).toBe(true)
    expect(wrapper.get('.empty-stub').text()).toBe('没有符合条件的岗位')
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalWidth })
  })
})
