<script setup>
import { computed } from 'vue'
import {
  ArrowRight,
  CircleCheck,
  DataAnalysis,
  Document,
  Key,
  List,
  Lock,
  Setting,
  User,
  UserFilled,
  View,
} from '@element-plus/icons-vue'
import PageContainer from '../../components/common/PageContainer.vue'
import StatTile from '../../components/common/StatTile.vue'
import { useUserStore } from '../../stores/user'
import { hasAnyPermission } from '../../utils/permission'

const userStore = useUserStore()

const roleLabels = {
  ROLE_ADMIN: '系统管理员',
  ROLE_STUDENT: '学生',
  ROLE_TEACHER: '教师',
  ROLE_ANALYST: '数据分析员',
  ROLE_COLLEGE_ADMIN: '学院管理员',
}

const actionDefinitions = [
  { label: '个人资料', path: '/profile', icon: User },
  { label: '用户管理', path: '/system/users', icon: UserFilled, permissions: ['user:read'] },
  { label: '角色权限', path: '/system/roles', icon: Setting, permissions: ['role:read'] },
  { label: '操作日志', path: '/system/logs', icon: List, permissions: ['log:read'] },
  { label: 'API Key', path: '/open-api/keys', icon: Key, permissions: ['api:key:manage'] },
  { label: '调用统计', path: '/open-api/calls', icon: DataAnalysis, permissions: ['api:view'] },
  { label: 'API 文档', path: '/api-docs', icon: Document, permissions: ['api:docs', 'api:view', 'api:key:manage'] },
]

const availableActions = computed(() => actionDefinitions.filter((action) => (
  hasAnyPermission(userStore.userInfo, action.permissions || [])
)))

const roleSummary = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.map((role) => roleLabels[role] || role).join(' / ') || '未分配角色'
})

const profileCompleteness = computed(() => {
  const fields = ['realName', 'email', 'phone', 'college']
  const completed = fields.filter((field) => Boolean(userStore.userInfo?.[field]?.trim())).length
  return Math.round((completed / fields.length) * 100)
})

const summaries = computed(() => [
  {
    label: '当前角色',
    value: userStore.userInfo?.roles?.length || 0,
    unit: '个',
    icon: User,
    tone: 'brand',
  },
  {
    label: '有效权限',
    value: userStore.userInfo?.permissions?.length || 0,
    unit: '项',
    icon: Lock,
    tone: 'info',
  },
  {
    label: '可用模块',
    value: availableActions.value.length,
    unit: '个',
    icon: View,
    tone: 'neutral',
  },
  {
    label: '资料完整度',
    value: profileCompleteness.value,
    unit: '%',
    icon: CircleCheck,
    tone: profileCompleteness.value === 100 ? 'success' : 'warning',
  },
])
</script>

<template>
  <PageContainer
    title="工作台"
    :description="`欢迎回来，${userStore.displayName}。当前以 ${roleSummary} 身份访问平台。`"
    :framed="false"
  >
    <div class="summary-grid">
      <StatTile
        v-for="summary in summaries"
        :key="summary.label"
        :label="summary.label"
        :value="summary.value"
        :unit="summary.unit"
        :icon="summary.icon"
        :tone="summary.tone"
      />
    </div>

    <div class="workspace-grid">
      <section class="workspace-panel quick-panel">
        <header class="section-heading">
          <div>
            <span>常用功能</span>
            <h2>快捷入口</h2>
          </div>
          <small>{{ availableActions.length }} 个可用模块</small>
        </header>

        <nav class="quick-actions" aria-label="快捷入口">
          <RouterLink
            v-for="action in availableActions"
            :key="action.path"
            :to="action.path"
          >
            <el-icon><component :is="action.icon" /></el-icon>
            <span>{{ action.label }}</span>
            <el-icon class="action-arrow">
              <ArrowRight />
            </el-icon>
          </RouterLink>
        </nav>
      </section>

      <section class="workspace-panel account-panel">
        <header class="section-heading">
          <div>
            <span>当前账号</span>
            <h2>账号摘要</h2>
          </div>
          <el-tag
            :type="userStore.userInfo?.status === 1 ? 'success' : 'danger'"
            effect="plain"
          >
            {{ userStore.userInfo?.status === 1 ? '正常' : '停用' }}
          </el-tag>
        </header>

        <dl class="account-facts">
          <div>
            <dt>登录账号</dt>
            <dd>{{ userStore.userInfo?.username || '-' }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd>{{ roleSummary }}</dd>
          </div>
          <div>
            <dt>所属学院</dt>
            <dd>{{ userStore.userInfo?.college || '未设置' }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ userStore.userInfo?.createTime || '暂无记录' }}</dd>
          </div>
        </dl>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 0.8fr);
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.workspace-panel {
  min-width: 0;
  padding: var(--space-5);
  border: 1px solid var(--app-border);
  border-radius: var(--radius-6);
  background: var(--app-surface);
  box-shadow: var(--shadow-xs);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.section-heading span,
.section-heading small,
.account-facts dt {
  color: var(--app-muted);
  font-size: var(--font-size-12);
}

.section-heading h2 {
  margin: var(--space-1) 0 0;
  color: var(--app-text);
  font-size: var(--font-size-16);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}

.quick-actions a {
  display: grid;
  min-height: 48px;
  grid-template-columns: 24px minmax(0, 1fr) 18px;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--app-border);
  border-radius: var(--radius-6);
  color: var(--app-text-regular);
  transition:
    color var(--motion-duration-fast) var(--motion-ease-standard),
    border-color var(--motion-duration-fast) var(--motion-ease-standard),
    background-color var(--motion-duration-fast) var(--motion-ease-standard),
    transform var(--motion-duration-fast) var(--motion-ease-standard);
}

.quick-actions a:hover {
  border-color: var(--color-brand-300);
  background: var(--color-brand-50);
  color: var(--app-accent-strong);
  transform: translateY(-1px);
}

.quick-actions a > .el-icon:first-child {
  color: var(--app-accent);
  font-size: var(--font-size-18);
}

.quick-actions span {
  overflow: hidden;
  font-weight: var(--font-weight-medium);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-arrow {
  justify-self: end;
  color: var(--app-muted);
}

.account-facts {
  display: grid;
  gap: 0;
  margin: 0;
}

.account-facts > div {
  display: grid;
  min-width: 0;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-top: 1px solid var(--app-border);
}

.account-facts dt,
.account-facts dd {
  margin: 0;
}

.account-facts dd {
  overflow-wrap: anywhere;
  color: var(--app-text-regular);
  font-size: var(--font-size-13);
}

@media (max-width: 1120px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .summary-grid,
  .quick-actions {
    grid-template-columns: 1fr;
  }

  .workspace-panel {
    padding: var(--space-4);
  }
}
</style>
