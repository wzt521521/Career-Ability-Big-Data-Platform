import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: () => import('../views/dashboard/DashboardView.vue'), meta: { title: '就业数据大屏' } },
        { path: 'positions', component: () => import('../views/positions/PositionView.vue'), meta: { title: '岗位分析' } },
        { path: 'collect/sources', component: () => import('../views/collect/CollectSourceView.vue'), meta: { title: '数据源管理' } },
        { path: 'collect/tasks', component: () => import('../views/collect/CollectTaskView.vue'), meta: { title: '采集任务管理' } },
        { path: 'collect/logs', component: () => import('../views/collect/CollectLogView.vue'), meta: { title: '采集执行日志' } }
      ]
    }
  ]
})
