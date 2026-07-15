import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import { useUserStore } from '../stores/user'
import { hasAnyPermission } from '../utils/permission'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/LoginView.vue'),
    meta: { title: '账号登录', guestOnly: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/auth/RegisterView.vue'),
    meta: { title: '创建账号', guestOnly: true },
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/DashboardView.vue'),
        meta: { title: '就业数据大屏', section: '数据分析', requiresAuth: true, permissions: ['dashboard:view'] },
      },
      {
        path: 'positions',
        name: 'Positions',
        component: () => import('../views/positions/PositionView.vue'),
        meta: { title: '岗位分析', section: '数据分析', requiresAuth: true, permissions: ['position:view'] },
      },
      {
        path: 'recommend',
        name: 'Recommend',
        component: () => import('../views/recommend/RecommendView.vue'),
        meta: { title: '岗位推荐', section: '数据分析', requiresAuth: true, permissions: ['recommend:view'] },
      },
      {
        path: 'report',
        name: 'Reports',
        component: () => import('../views/report/ReportCenterView.vue'),
        meta: { title: '报告中心', section: '数据分析', requiresAuth: true, permissions: ['report:view'] },
      },
      {
        path: 'collect/sources',
        name: 'CollectSources',
        component: () => import('../views/collect/CollectSourceView.vue'),
        meta: { title: '数据源管理', section: '采集管理', requiresAuth: true, permissions: ['collect:view'] },
      },
      {
        path: 'collect/tasks',
        name: 'CollectTasks',
        component: () => import('../views/collect/CollectTaskView.vue'),
        meta: { title: '采集任务管理', section: '采集管理', requiresAuth: true, permissions: ['collect:view'] },
      },
      {
        path: 'collect/logs',
        name: 'CollectLogs',
        component: () => import('../views/collect/CollectLogView.vue'),
        meta: { title: '采集执行日志', section: '采集管理', requiresAuth: true, permissions: ['collect:view'] },
      },
      {
        path: 'home',
        name: 'Home',
        component: () => import('../views/home/HomeView.vue'),
        meta: { title: '工作台', section: '账户', requiresAuth: true },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/auth/ProfileView.vue'),
        meta: { title: '个人资料', section: '个人中心', requiresAuth: true },
      },
      {
        path: 'system/users',
        name: 'SystemUsers',
        component: () => import('../views/system/UserListView.vue'),
        meta: { title: '用户管理', section: '系统管理', requiresAuth: true, permissions: ['user:read'] },
      },
      {
        path: 'system/roles',
        name: 'SystemRoles',
        component: () => import('../views/system/RoleListView.vue'),
        meta: { title: '角色权限', section: '系统管理', requiresAuth: true, permissions: ['role:read'] },
      },
      {
        path: 'system/logs',
        name: 'OperationLogs',
        component: () => import('../views/system/OperationLogView.vue'),
        meta: { title: '操作日志', section: '系统管理', requiresAuth: true, permissions: ['log:read'] },
      },
      {
        path: 'open-api/keys',
        name: 'ApiKeys',
        component: () => import('../views/open-api/ApiKeyView.vue'),
        meta: { title: 'API Key', section: '开发者服务', requiresAuth: true, permissions: ['api:key:manage'] },
      },
      {
        path: 'open-api/calls',
        name: 'ApiCalls',
        component: () => import('../views/open-api/ApiCallLogView.vue'),
        meta: { title: '调用统计', section: '开发者服务', requiresAuth: true, permissions: ['api:view'] },
      },
      {
        path: 'api-docs',
        name: 'ApiDocs',
        component: () => import('../views/open-api/ApiDocsView.vue'),
        meta: { title: 'API 文档', section: '开发者服务', requiresAuth: true, permissions: ['api:docs', 'api:view', 'api:key:manage'] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (userStore.isAuthenticated && !userStore.validated) {
    try {
      await userStore.validateSession()
    } catch {
      userStore.clearLocalSession()
    }
  }

  if (to.meta.guestOnly && userStore.isAuthenticated) {
    return '/dashboard'
  }

  if (to.matched.some((record) => record.meta.requiresAuth) && !userStore.isAuthenticated) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  const permissions = to.meta.permissions || []
  if (!hasAnyPermission(userStore.userInfo, permissions)) {
    return '/dashboard'
  }

  document.title = to.meta.title
    ? `${to.meta.title} - 职业能力大数据服务平台`
    : '职业能力大数据服务平台'
  return true
})

export default router
