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
        { path: 'positions', component: () => import('../views/positions/PositionView.vue'), meta: { title: '岗位分析' } }
      ]
    }
  ]
})
