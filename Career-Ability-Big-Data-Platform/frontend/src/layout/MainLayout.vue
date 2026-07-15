<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight,
  Connection,
  DataAnalysis,
  Document,
  Fold,
  Histogram,
  HomeFilled,
  Key,
  List,
  Menu as MenuIcon,
  Monitor,
  Operation,
  Setting,
  Star,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import { useAppStore } from '../stores/app'
import { useUserStore } from '../stores/user'
import { hasAnyPermission } from '../utils/permission'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const roleLabels = {
  ROLE_ADMIN: '系统管理员',
  ROLE_STUDENT: '学生',
  ROLE_TEACHER: '教师',
  ROLE_ANALYST: '数据分析员',
  ROLE_COLLEGE_ADMIN: '学院管理员',
}

const menuGroups = computed(() => [
  {
    label: '数据分析',
    items: [
      { path: '/dashboard', label: '就业数据大屏', icon: DataAnalysis, requiresAuth: true, permissions: ['dashboard:view'] },
      { path: '/positions', label: '岗位分析', icon: Histogram, requiresAuth: true, permissions: ['position:view'] },
      { path: '/recommend', label: '岗位推荐', icon: Star, requiresAuth: true, permissions: ['recommend:view'] },
      { path: '/report', label: '报告中心', icon: Document, requiresAuth: true, permissions: ['report:view'] },
    ],
  },
  {
    label: '采集管理',
    items: [
      { path: '/collect/sources', label: '数据源管理', icon: Document, requiresAuth: true, permissions: ['collect:view'] },
      { path: '/collect/tasks', label: '采集任务管理', icon: Monitor, requiresAuth: true, permissions: ['collect:view'] },
      { path: '/collect/logs', label: '采集执行日志', icon: List, requiresAuth: true, permissions: ['collect:view'] },
    ],
  },
  {
    label: '账户',
    items: [
      { path: '/home', label: '工作台', icon: HomeFilled, requiresAuth: true },
      { path: '/profile', label: '个人资料', icon: User, requiresAuth: true },
    ],
  },
  {
    label: '系统管理',
    items: [
      { path: '/system/users', label: '用户管理', icon: UserFilled, requiresAuth: true, permissions: ['user:read'] },
      { path: '/system/roles', label: '角色权限', icon: Setting, requiresAuth: true, permissions: ['role:read'] },
      { path: '/system/logs', label: '操作日志', icon: List, requiresAuth: true, permissions: ['log:read'] },
    ],
  },
  {
    label: '开发者服务',
    items: [
      { path: '/open-api/keys', label: 'API Key', icon: Key, requiresAuth: true, permissions: ['api:key:manage'] },
      { path: '/open-api/calls', label: '调用统计', icon: DataAnalysis, requiresAuth: true, permissions: ['api:view'] },
      { path: '/api-docs', label: 'API 文档', icon: Document, requiresAuth: true, permissions: ['api:docs', 'api:view', 'api:key:manage'] },
    ],
  },
].map((group) => ({
  ...group,
  items: group.items.filter((item) => (
    (!item.requiresAuth || userStore.isAuthenticated)
    && hasAnyPermission(userStore.userInfo, item.permissions || [])
  )),
})).filter((group) => group.items.length))

const currentTitle = computed(() => route.meta.title || '就业数据大屏')
const currentSection = computed(() => route.meta.section || '数据分析')
const currentRole = computed(() => {
  const role = userStore.userInfo?.roles?.[0]
  return roleLabels[role] || role || '平台用户'
})
const userInitial = computed(() => userStore.displayName.trim().charAt(0).toUpperCase() || 'U')

async function handleCommand(command) {
  if (command === 'profile') {
    await router.push('/profile')
    return
  }
  if (command === 'logout') {
    await userStore.logout()
    await router.replace('/login')
  }
}
</script>

<template>
  <div class="app-shell">
    <aside
      class="sidebar"
      :class="{ collapsed: appStore.sidebarCollapsed }"
    >
      <RouterLink
        to="/dashboard"
        class="brand-block"
      >
        <div class="brand-mark">
          CA
        </div>
        <div
          v-if="!appStore.sidebarCollapsed"
          class="brand-copy"
        >
          <strong>职业能力平台</strong>
          <span>管理控制台</span>
        </div>
      </RouterLink>

      <el-menu
        :default-active="route.path"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        router
        class="side-menu"
      >
        <template
          v-for="(group, groupIndex) in menuGroups"
          :key="group.label"
        >
          <div
            v-if="!appStore.sidebarCollapsed"
            class="menu-group-label"
          >
            {{ group.label }}
          </div>
          <div
            v-else-if="groupIndex > 0"
            class="menu-divider"
          />
          <el-menu-item
            v-for="item in group.items"
            :key="item.path"
            :index="item.path"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>
              {{ item.label }}
            </template>
          </el-menu-item>
        </template>
      </el-menu>
    </aside>

    <el-drawer
      v-model="appStore.mobileMenuOpen"
      direction="ltr"
      :with-header="false"
      size="260px"
      class="mobile-drawer"
    >
      <div class="mobile-brand">
        <div class="brand-mark">
          CA
        </div>
        <div class="brand-copy">
          <strong>职业能力平台</strong>
          <span>管理控制台</span>
        </div>
      </div>
      <el-menu
        :default-active="route.path"
        router
        @select="appStore.closeMobileMenu"
      >
        <template
          v-for="group in menuGroups"
          :key="group.label"
        >
          <div class="mobile-group-label">
            {{ group.label }}
          </div>
          <el-menu-item
            v-for="item in group.items"
            :key="item.path"
            :index="item.path"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-drawer>

    <div class="main-column">
      <header class="topbar">
        <div class="topbar-left">
          <el-button
            class="desktop-toggle"
            text
            :icon="appStore.sidebarCollapsed ? MenuIcon : Fold"
            title="切换侧边栏"
            @click="appStore.toggleSidebar"
          />
          <el-button
            class="mobile-toggle"
            text
            :icon="MenuIcon"
            title="打开菜单"
            @click="appStore.openMobileMenu"
          />
          <nav
            class="topbar-breadcrumb"
            aria-label="当前位置"
          >
            <span>{{ currentSection }}</span>
            <el-icon><ArrowRight /></el-icon>
            <strong>{{ currentTitle }}</strong>
          </nav>
        </div>

        <RouterLink
          v-if="!userStore.isAuthenticated"
          to="/login"
          class="login-trigger"
        >
          <el-icon><User /></el-icon>
          <span>登录</span>
        </RouterLink>
        <el-dropdown
          v-else
          @command="handleCommand"
        >
          <button
            type="button"
            class="user-trigger"
            aria-label="打开用户菜单"
          >
            <el-avatar :size="32">
              {{ userInitial }}
            </el-avatar>
            <span class="user-copy">
              <strong>{{ userStore.displayName }}</strong>
              <small>{{ currentRole }}</small>
            </span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                command="profile"
                :icon="Operation"
              >
                个人资料
              </el-dropdown-item>
              <el-dropdown-item
                command="logout"
                :icon="Connection"
                divided
              >
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="main-content">
        <RouterView v-slot="{ Component, route: viewRoute }">
          <Transition
            name="app-route"
            mode="out-in"
          >
            <component
              :is="Component"
              :key="viewRoute.path"
            />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  position: sticky;
  top: 0;
  width: var(--sidebar-width);
  height: 100vh;
  flex: 0 0 var(--sidebar-width);
  overflow: hidden;
  border-right: 1px solid var(--app-border);
  background: var(--app-surface-subtle);
  transition:
    width var(--motion-duration-base) var(--motion-ease-standard),
    flex-basis var(--motion-duration-base) var(--motion-ease-standard);
}

.sidebar.collapsed {
  width: var(--sidebar-width-collapsed);
  flex-basis: var(--sidebar-width-collapsed);
}

.brand-block {
  display: flex;
  height: var(--topbar-height);
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--app-border);
  color: inherit;
  transition: background-color var(--motion-duration-fast) var(--motion-ease-standard);
}

.brand-block:hover {
  background: var(--app-surface-muted);
}

.sidebar.collapsed .brand-block {
  justify-content: center;
  padding: 0;
}

.brand-mark {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: var(--radius-6);
  background: var(--app-accent);
  color: white;
  font-size: 13px;
  font-weight: 700;
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.brand-copy strong {
  overflow: hidden;
  color: var(--app-text);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-copy span {
  margin-top: 3px;
  color: var(--app-muted);
  font-size: 11px;
}

.side-menu {
  height: calc(100vh - var(--topbar-height));
  overflow-x: hidden;
  overflow-y: auto;
  padding: var(--space-2) 0 var(--space-5);
  border-right: 0;
  background: transparent;
}

.menu-group-label,
.mobile-group-label {
  color: var(--app-muted);
  font-size: 11px;
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0;
  line-height: 1;
}

.menu-group-label {
  padding: var(--space-4) var(--space-4) var(--space-2);
}

.menu-group-label:first-child {
  padding-top: var(--space-2);
}

.menu-divider {
  height: 1px;
  margin: var(--space-3) var(--space-4);
  background: var(--app-border);
}

.main-column {
  min-width: 0;
  flex: 1;
  background: var(--app-bg);
}

.topbar {
  position: sticky;
  z-index: 10;
  top: 0;
  display: flex;
  height: var(--topbar-height);
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-6);
  border-bottom: 1px solid var(--app-border);
  background: rgb(255 255 255 / 96%);
  backdrop-filter: blur(8px);
}

.topbar-left {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.topbar-breadcrumb {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  color: var(--app-muted);
  font-size: var(--font-size-13);
}

.topbar-breadcrumb .el-icon {
  flex: 0 0 auto;
  font-size: 12px;
}

.topbar-breadcrumb strong {
  overflow: hidden;
  color: var(--app-text);
  font-size: var(--font-size-14);
  font-weight: var(--font-weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-toggle {
  display: none;
}

.user-trigger {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-2);
  border: 0;
  border-radius: var(--radius-6);
  background: transparent;
  color: var(--app-text-regular);
  cursor: pointer;
  transition:
    color var(--motion-duration-fast) var(--motion-ease-standard),
    background-color var(--motion-duration-fast) var(--motion-ease-standard);
}

.user-trigger:hover {
  background: var(--app-surface-muted);
  color: var(--app-text);
}

.login-trigger {
  display: inline-flex;
  height: var(--control-height-lg);
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  border-radius: var(--radius-6);
  color: var(--app-text-regular);
  font-size: var(--font-size-13);
  font-weight: var(--font-weight-semibold);
  transition:
    color var(--motion-duration-fast) var(--motion-ease-standard),
    background-color var(--motion-duration-fast) var(--motion-ease-standard);
}

.login-trigger:hover {
  background: var(--app-surface-muted);
  color: var(--app-text);
}

.user-trigger :deep(.el-avatar) {
  flex: 0 0 auto;
  background: var(--color-brand-100);
  color: var(--color-brand-700);
  font-weight: var(--font-weight-semibold);
}

.user-copy {
  display: flex;
  max-width: 140px;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.25;
}

.user-copy strong,
.user-copy small {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-copy strong {
  color: var(--app-text);
  font-size: var(--font-size-13);
  font-weight: var(--font-weight-semibold);
}

.user-copy small {
  margin-top: 2px;
  color: var(--app-muted);
  font-size: 11px;
}

.main-content {
  width: 100%;
  max-width: 1680px;
  min-width: 0;
  margin: 0 auto;
  padding: var(--space-6);
}

.mobile-brand {
  display: flex;
  height: var(--topbar-height);
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--app-border);
  color: var(--app-text);
}

.mobile-group-label {
  padding: var(--space-5) var(--space-4) var(--space-2);
}

.mobile-group-label:first-child {
  padding-top: var(--space-4);
}

:global(.mobile-drawer .el-drawer__body) {
  padding: 0;
}

:global(.mobile-drawer .el-menu) {
  border-right: 0;
}

@media (max-width: 900px) {
  .sidebar {
    display: none;
  }

  .desktop-toggle {
    display: none;
  }

  .mobile-toggle {
    display: inline-flex;
  }

  .main-content {
    padding: var(--space-4);
  }
}

@media (max-width: 520px) {
  .topbar {
    padding: 0 var(--space-3);
  }

  .topbar-breadcrumb > span,
  .topbar-breadcrumb > .el-icon {
    display: none;
  }

  .user-copy {
    display: none;
  }

  .main-content {
    padding: var(--space-3);
  }
}
</style>
