import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RecommendView from './RecommendView.vue'

const profileApi = vi.hoisted(() => ({
  getMyProfile: vi.fn(),
  saveMyProfile: vi.fn(),
}))
const recommendApi = vi.hoisted(() => ({
  getGapAnalysis: vi.fn(),
  getRecommendations: vi.fn(),
}))
const message = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('element-plus', () => ({ ElMessage: message }))
vi.mock('../../api/profile', () => profileApi)
vi.mock('../../api/recommend', () => recommendApi)
vi.mock('../../utils/echarts', () => ({ init: vi.fn() }))

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
  ElDialog: { template: '<div><slot /></div>' },
  ElForm: {
    props: ['disabled'],
    template: '<form :data-disabled="String(Boolean(disabled))"><slot /></form>',
  },
  ElFormItem: { template: '<div><slot /></div>' },
  ElIcon: { template: '<i><slot /></i>' },
  ElInput: { template: '<input />' },
  ElInputNumber: { template: '<input type="number" />' },
  ElOption: true,
  ElPagination: true,
  ElRate: true,
  ElSelect: { template: '<select><slot /></select>' },
  ElTag: { template: '<span><slot /></span>' },
  EmptyState: {
    props: ['title'],
    template: '<div class="empty-state-stub">{{ title }}</div>',
  },
  PageContainer: { template: '<section><slot /></section>' },
  PageHeader: { template: '<header><slot name="actions" /></header>' },
  StatTile: true,
}

const profile = {
  education: '本科',
  major: '计算机科学与技术',
  preferredCity: '北京',
  salaryMax: 25,
  salaryMin: 15,
  skills: ['Java'],
}

function mountView() {
  return mount(RecommendView, {
    global: {
      directives: { loading: {} },
      stubs,
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('RecommendView', () => {
  it('keeps the profile editor disabled while the initial profile request is loading', async () => {
    let resolveProfile
    profileApi.getMyProfile.mockReturnValue(new Promise((resolve) => {
      resolveProfile = resolve
    }))

    const wrapper = mountView()
    await wrapper.vm.$nextTick()

    expect(wrapper.get('form').attributes('data-disabled')).toBe('true')

    resolveProfile({ data: null })
    await flushPromises()

    expect(wrapper.get('form').attributes('data-disabled')).toBe('false')
  })

  it('shows the recommendation empty state after an existing profile returns no matches', async () => {
    profileApi.getMyProfile.mockResolvedValue({ data: profile })
    recommendApi.getRecommendations.mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0 },
    })

    const wrapper = mountView()
    await flushPromises()

    expect(recommendApi.getRecommendations).toHaveBeenCalledWith({ page: 1, size: 20 })
    expect(wrapper.get('.empty-state-stub').text()).toBe('暂无推荐结果')
  })

  it('clears the loading state and reports an API failure without hiding the empty fallback', async () => {
    profileApi.getMyProfile.mockResolvedValue({ data: profile })
    recommendApi.getRecommendations.mockRejectedValue(new Error('service unavailable'))

    const wrapper = mountView()
    await flushPromises()

    expect(message.error).toHaveBeenCalledWith('加载推荐失败')
    expect(wrapper.find('.recommend-list-loading').exists()).toBe(false)
    expect(wrapper.get('.empty-state-stub').text()).toBe('暂无推荐结果')
  })
})
