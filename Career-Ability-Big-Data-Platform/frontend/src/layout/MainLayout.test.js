import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it } from 'vitest'
import MainLayout from './MainLayout.vue'
import { useUserStore } from '../stores/user'

const stubs = {
  ElAvatar: { template: '<span><slot /></span>' },
  ElButton: { template: '<button><slot /></button>' },
  ElDrawer: { template: '<aside><slot /></aside>' },
  ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
  ElDropdownItem: { template: '<div><slot /></div>' },
  ElDropdownMenu: { template: '<div><slot /></div>' },
  ElIcon: { template: '<i><slot /></i>' },
  ElMenu: { template: '<nav><slot /></nav>' },
  ElMenuItem: {
    props: ['index'],
    template: '<div class="menu-item-stub" :data-menu-index="index"><slot /><slot name="title" /></div>',
  },
  RouterLink: { template: '<a><slot /></a>' },
  RouterView: { template: '<div />' },
}

async function mountLayout(permissions) {
  const pinia = createPinia()
  setActivePinia(pinia)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/dashboard', component: { template: '<div />' } }],
  })
  await router.push('/dashboard')

  const userStore = useUserStore(pinia)
  userStore.accessToken = 'access-token'
  userStore.refreshToken = 'refresh-token'
  userStore.userInfo = {
    permissions,
    roles: ['ROLE_STUDENT'],
    username: 'student',
  }
  userStore.validated = true

  return mount(MainLayout, {
    global: {
      plugins: [pinia, router],
      stubs,
    },
  })
}

afterEach(() => {
  localStorage.clear()
})

describe('MainLayout analysis navigation permissions', () => {
  it('hides recommendation and report navigation entries unless the current user has their permission', async () => {
    const noPermissions = await mountLayout([])

    expect(noPermissions.find('[data-menu-index="/recommend"]').exists()).toBe(false)
    expect(noPermissions.find('[data-menu-index="/report"]').exists()).toBe(false)
    noPermissions.unmount()

    const recommendOnly = await mountLayout(['recommend:view'])

    expect(recommendOnly.find('[data-menu-index="/recommend"]').exists()).toBe(true)
    expect(recommendOnly.find('[data-menu-index="/report"]').exists()).toBe(false)
  })

  it('separates API key management from API call audit navigation', async () => {
    const keyManager = await mountLayout(['api:key:manage'])

    expect(keyManager.find('[data-menu-index="/open-api/keys"]').exists()).toBe(true)
    expect(keyManager.find('[data-menu-index="/open-api/calls"]').exists()).toBe(false)
    expect(keyManager.find('[data-menu-index="/api-docs"]').exists()).toBe(true)
    keyManager.unmount()

    const auditor = await mountLayout(['api:view'])

    expect(auditor.find('[data-menu-index="/open-api/keys"]').exists()).toBe(false)
    expect(auditor.find('[data-menu-index="/open-api/calls"]').exists()).toBe(true)
  })
})
