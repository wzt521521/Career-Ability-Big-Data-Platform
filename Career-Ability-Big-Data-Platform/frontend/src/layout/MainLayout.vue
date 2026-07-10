<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, Fold, Histogram, Menu as MenuIcon } from '@element-plus/icons-vue'

const collapsed = ref(false)
const route = useRoute()
const title = computed(() => route.meta.title || '职业能力大数据服务平台')
</script>

<template>
  <div class="app-shell" :class="{ collapsed }">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">职</span>
        <div v-show="!collapsed" class="brand-copy">
          <strong>职业能力平台</strong>
          <small>CAREER INTELLIGENCE</small>
        </div>
      </div>
      <nav aria-label="主导航">
        <router-link to="/dashboard" title="就业数据大屏">
          <el-icon><DataAnalysis /></el-icon><span v-show="!collapsed">就业数据大屏</span>
        </router-link>
        <router-link to="/positions" title="岗位分析">
          <el-icon><Histogram /></el-icon><span v-show="!collapsed">岗位分析</span>
        </router-link>
      </nav>
      <button class="collapse-button" type="button" :title="collapsed ? '展开导航' : '收起导航'" @click="collapsed = !collapsed">
        <el-icon><MenuIcon v-if="collapsed" /><Fold v-else /></el-icon>
      </button>
    </aside>
    <main>
      <header class="topbar">
        <div>
          <span class="section-label">就业市场观测台</span>
          <h1>{{ title }}</h1>
        </div>
        <div class="data-status"><span /> 数据服务</div>
      </header>
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell { min-height: 100vh; padding-left: 224px; transition: padding-left .2s ease; }
.app-shell.collapsed { padding-left: 72px; }
.sidebar { position: fixed; inset: 0 auto 0 0; z-index: 20; display: flex; width: 224px; flex-direction: column; overflow: hidden; background: #17201f; color: #fff; transition: width .2s ease; }
.collapsed .sidebar { width: 72px; }
.brand { display: flex; height: 76px; align-items: center; gap: 11px; padding: 0 18px; border-bottom: 1px solid rgba(255,255,255,.1); white-space: nowrap; }
.brand-mark { display: grid; width: 36px; height: 36px; flex: 0 0 36px; place-items: center; border-radius: 5px; background: #e16f50; font-size: 18px; font-weight: 700; }
.brand-copy { display: flex; flex-direction: column; gap: 2px; }
.brand-copy strong { font-size: 15px; letter-spacing: 0; }
.brand-copy small { color: #91a39f; font-size: 9px; letter-spacing: 0; }
nav { display: flex; flex: 1; flex-direction: column; gap: 4px; padding: 18px 10px; }
nav a { display: flex; height: 44px; align-items: center; gap: 12px; padding: 0 14px; border-left: 3px solid transparent; border-radius: 4px; color: #aebbb8; font-size: 14px; text-decoration: none; white-space: nowrap; }
nav a:hover { background: rgba(255,255,255,.06); color: #fff; }
nav a.router-link-active { border-left-color: #e16f50; background: rgba(255,255,255,.09); color: #fff; }
nav .el-icon { flex: 0 0 18px; font-size: 18px; }
.collapse-button { display: grid; width: 44px; height: 44px; margin: 0 14px 16px; place-items: center; border: 0; border-radius: 4px; background: transparent; color: #91a39f; cursor: pointer; }
.collapse-button:hover { background: rgba(255,255,255,.08); color: #fff; }
main { min-width: 0; }
.topbar { display: flex; min-height: 76px; align-items: center; justify-content: space-between; padding: 12px 28px; border-bottom: 1px solid var(--line); background: rgba(255,255,255,.94); }
.section-label { color: var(--muted); font-size: 11px; }
.topbar h1 { margin: 3px 0 0; color: var(--ink); font-size: 20px; font-weight: 680; }
.data-status { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: 12px; }
.data-status span { width: 7px; height: 7px; border-radius: 50%; background: #2a9d78; box-shadow: 0 0 0 3px rgba(42,157,120,.12); }
@media (max-width: 760px) {
  .app-shell, .app-shell.collapsed { padding-left: 0; padding-bottom: 62px; }
  .sidebar, .collapsed .sidebar { inset: auto 0 0 0; width: 100%; height: 62px; flex-direction: row; }
  .brand, .collapse-button { display: none; }
  nav { flex-direction: row; justify-content: space-around; padding: 7px; }
  nav a { height: 48px; border-left: 0; border-bottom: 3px solid transparent; padding: 0 16px; }
  nav a.router-link-active { border-left-color: transparent; border-bottom-color: #e16f50; }
  .topbar { min-height: 68px; padding: 10px 16px; }
  .data-status { display: none; }
}
</style>
