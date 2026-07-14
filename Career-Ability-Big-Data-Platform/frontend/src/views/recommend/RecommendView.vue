<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, StarFilled, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { getMyProfile, saveMyProfile } from '../../api/profile'
import { getRecommendations, getGapAnalysis } from '../../api/recommend'
import PageContainer from '../../components/common/PageContainer.vue'
import PageHeader from '../../components/common/PageHeader.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StatTile from '../../components/common/StatTile.vue'
import { init as initEcharts } from '../../utils/echarts'

const profile = reactive({
  major: '',
  skills: [],
  education: '',
  preferredCity: '',
  salaryMin: null,
  salaryMax: null,
})

const profileLoading = ref(false)
const recommendLoading = ref(false)
const recommendations = ref([])
const pagination = reactive({ page: 1, size: 20, total: 0, totalPages: 0 })
const hasProfile = ref(false)

const gapDialog = ref(false)
const gapLoading = ref(false)
const gapData = ref(null)
let radarChart = null

const educationOptions = ['大专', '本科', '硕士', '博士']

async function loadProfile() {
  profileLoading.value = true
  try {
    const res = await getMyProfile()
    if (res.data) {
      Object.assign(profile, {
        major: res.data.major || '',
        skills: res.data.skills || [],
        education: res.data.education || '',
        preferredCity: res.data.preferredCity || '',
        salaryMin: res.data.salaryMin,
        salaryMax: res.data.salaryMax,
      })
      hasProfile.value = true
    }
  } catch {
    // Profile not created yet — stay in empty state
  } finally {
    profileLoading.value = false
  }
}

async function handleSaveProfile() {
  profileLoading.value = true
  try {
    await saveMyProfile({
      major: profile.major || null,
      skills: profile.skills.length ? profile.skills : null,
      education: profile.education || null,
      preferredCity: profile.preferredCity || null,
      salaryMin: profile.salaryMin,
      salaryMax: profile.salaryMax,
    })
    hasProfile.value = true
    ElMessage.success('画像保存成功')
  } catch {
    ElMessage.error('画像保存失败')
  } finally {
    profileLoading.value = false
  }
}

async function loadRecommendations() {
  if (!hasProfile.value) {
    ElMessage.warning('请先创建就业画像')
    return
  }
  recommendLoading.value = true
  try {
    const res = await getRecommendations({ page: pagination.page, size: pagination.size })
    recommendations.value = res.data?.content || []
    pagination.total = res.data?.totalElements || 0
    pagination.totalPages = res.data?.totalPages || 0
  } catch {
    ElMessage.error('加载推荐失败')
  } finally {
    recommendLoading.value = false
  }
}

async function openGapAnalysis(positionId) {
  gapDialog.value = true
  gapLoading.value = true
  gapData.value = null
  try {
    const res = await getGapAnalysis(positionId)
    gapData.value = res.data
    await nextTick()
    renderRadar()
  } catch {
    ElMessage.error('加载差距分析失败')
  } finally {
    gapLoading.value = false
  }
}

function renderRadar() {
  const container = document.getElementById('gap-radar')
  if (!container || !gapData.value) return
  if (radarChart) radarChart.dispose()
  radarChart = initEcharts(container)
  const breakdown = gapData.value.scoreBreakdown || {}
  const labels = { skill: '技能', city: '城市', education: '学历', salary: '薪资', major: '专业' }
  radarChart.setOption({
    tooltip: { formatter: (p) => `${p.name}: ${(p.value * 100).toFixed(0)}%` },
    radar: {
      indicator: Object.entries(labels).map(([, v]) => ({ name: v, max: 1 })),
      center: ['50%', '55%'],
      radius: '70%',
    },
    series: [{
      type: 'radar',
      data: [{
        value: Object.keys(labels).map((k) => breakdown[k] || 0),
        name: '匹配度',
        areaStyle: { color: 'rgba(64,158,255,0.2)' },
        lineStyle: { color: '#409EFF' },
        itemStyle: { color: '#409EFF' },
      }],
    }],
  })
}

function handlePageChange(page) {
  pagination.page = page
  loadRecommendations()
}

function matchPercentColor(percent) {
  if (percent >= 80) return '#67C23A'
  if (percent >= 60) return '#409EFF'
  if (percent >= 40) return '#E6A23C'
  return '#F56C6C'
}

function matchStars(percent) {
  if (percent >= 90) return 5
  if (percent >= 70) return 4
  if (percent >= 50) return 3
  if (percent >= 30) return 2
  return 1
}

onMounted(async () => {
  await loadProfile()
  if (hasProfile.value) {
    await loadRecommendations()
  }
})
</script>

<template>
  <PageContainer>
    <PageHeader
      title="岗位推荐"
      description="基于你的就业画像，智能匹配最适合的岗位"
    >
      <template #actions>
        <el-button
          type="primary"
          :icon="Refresh"
          :loading="recommendLoading"
          :disabled="!hasProfile"
          @click="loadRecommendations"
        >
          刷新推荐
        </el-button>
      </template>
    </PageHeader>

    <div class="recommend-layout">
      <!-- 左侧：画像编辑 -->
      <aside class="profile-panel">
        <div class="panel-title">
          <el-icon><StarFilled /></el-icon>
          <span>就业画像</span>
        </div>
        <el-form
          label-position="top"
          size="default"
          :disabled="profileLoading"
        >
          <el-form-item label="专业名称">
            <el-input
              v-model="profile.major"
              placeholder="如：计算机科学与技术"
              maxlength="100"
            />
          </el-form-item>

          <el-form-item label="已掌握技能">
            <el-select
              v-model="profile.skills"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="输入技能后回车添加"
              class="skill-select"
            />
          </el-form-item>

          <el-form-item label="学历">
            <el-select
              v-model="profile.education"
              placeholder="请选择"
              clearable
            >
              <el-option
                v-for="item in educationOptions"
                :key="item"
                :label="item"
                :value="item"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="意向城市">
            <el-input
              v-model="profile.preferredCity"
              placeholder="多个城市用逗号分隔，如：北京,上海,杭州"
              maxlength="200"
            />
          </el-form-item>

          <el-form-item label="期望薪资（K/月）">
            <div class="salary-row">
              <el-input-number
                v-model="profile.salaryMin"
                :min="0"
                :max="200"
                placeholder="最低"
                controls-position="right"
              />
              <span class="salary-sep">—</span>
              <el-input-number
                v-model="profile.salaryMax"
                :min="0"
                :max="200"
                placeholder="最高"
                controls-position="right"
              />
            </div>
          </el-form-item>

          <el-button
            type="primary"
            :loading="profileLoading"
            style="width: 100%"
            @click="handleSaveProfile"
          >
            保存画像
          </el-button>
        </el-form>
      </aside>

      <!-- 右侧：推荐列表 -->
      <section class="recommend-main">
        <div
          v-if="!hasProfile"
          class="empty-wrap"
        >
          <EmptyState
            icon="UserFilled"
            title="尚未创建就业画像"
            description="请先在左侧填写你的专业、技能、意向城市等信息并保存，然后获取个性化岗位推荐。"
          />
        </div>

        <div
          v-else-if="recommendLoading"
          v-loading="recommendLoading"
          style="min-height: 300px"
        />

        <div
          v-else-if="!recommendations.length"
          class="empty-wrap"
        >
          <EmptyState
            icon="Search"
            title="暂无推荐结果"
            description="当前没有可匹配的岗位数据，请联系管理员导入岗位信息。"
          />
        </div>

        <template v-else>
          <div class="result-summary">
            共 <strong>{{ pagination.total }}</strong> 个岗位，按匹配度排序
          </div>
          <div class="card-list">
            <div
              v-for="item in recommendations"
              :key="item.positionId"
              class="recommend-card"
            >
              <div class="card-header">
                <h3 class="card-title">
                  {{ item.title }}
                </h3>
                <div class="match-area">
                  <div
                    class="match-badge"
                    :style="{ color: matchPercentColor(item.matchPercent) }"
                  >
                    <span class="match-score">{{ item.matchPercent }}%</span>
                    <span class="match-label">匹配度</span>
                  </div>
                  <el-rate
                    :model-value="matchStars(item.matchPercent)"
                    disabled
                    show-score
                    :score-template="matchStars(item.matchPercent) + '星'"
                    size="small"
                    style="margin-top: 4px"
                  />
                </div>
              </div>

              <div class="card-meta">
                <span>{{ item.companyName }}</span>
                <span v-if="item.industry">{{ item.industry }}</span>
                <span v-if="item.city">{{ item.city }}</span>
                <span v-if="item.education">{{ item.education }}</span>
                <span v-if="item.salaryMin != null || item.salaryMax != null">
                  {{ item.salaryMin != null ? item.salaryMin : '?' }}-{{ item.salaryMax != null ? item.salaryMax : '?' }}K
                </span>
              </div>

              <div class="skill-match">
                <div
                  v-if="item.matchedSkills.length"
                  class="skill-row"
                >
                  <span class="skill-tag-label"><el-icon><CircleCheckFilled /></el-icon> 已匹配</span>
                  <el-tag
                    v-for="sk in item.matchedSkills"
                    :key="sk"
                    size="small"
                    type="success"
                    effect="plain"
                    class="skill-tag"
                  >
                    {{ sk }}
                  </el-tag>
                </div>
                <div
                  v-if="item.unmatchedSkills.length"
                  class="skill-row"
                >
                  <span class="skill-tag-label"><el-icon><CircleCloseFilled /></el-icon> 待学习</span>
                  <el-tag
                    v-for="sk in item.unmatchedSkills.slice(0, 8)"
                    :key="sk"
                    size="small"
                    type="danger"
                    effect="plain"
                    class="skill-tag"
                  >
                    {{ sk }}
                  </el-tag>
                  <el-tag
                    v-if="item.unmatchedSkills.length > 8"
                    size="small"
                    type="info"
                    effect="plain"
                  >
                    +{{ item.unmatchedSkills.length - 8 }}
                  </el-tag>
                </div>
              </div>

              <div class="card-actions">
                <el-button
                  size="small"
                  type="primary"
                  text
                  @click="openGapAnalysis(item.positionId)"
                >
                  查看差距分析
                </el-button>
              </div>
            </div>
          </div>

          <div
            v-if="pagination.totalPages > 1"
            class="pagination-wrap"
          >
            <el-pagination
              :current-page="pagination.page"
              :page-size="pagination.size"
              :total="pagination.total"
              layout="prev, pager, next"
              @current-change="handlePageChange"
            />
          </div>
        </template>
      </section>
    </div>

    <!-- 差距分析弹窗 -->
    <el-dialog
      v-model="gapDialog"
      title="技能差距分析"
      width="640px"
      destroy-on-close
    >
      <div
        v-if="gapLoading"
        v-loading="gapLoading"
        style="min-height: 300px"
      />
      <template v-else-if="gapData">
        <div class="gap-summary">
          <StatTile
            :value="gapData.totalScore * 100"
            title="综合匹配度"
            unit="%"
            :precision="1"
          />
          <div class="gap-suggestion">
            <p>{{ gapData.suggestion }}</p>
          </div>
        </div>

        <div
          id="gap-radar"
          style="width: 100%; height: 320px; margin: 16px 0"
        />

        <div class="gap-skills">
          <div v-if="gapData.matchedSkills.length">
            <h4>✅ 已匹配技能（{{ gapData.matchedSkills.length }}）</h4>
            <el-tag
              v-for="sk in gapData.matchedSkills"
              :key="sk"
              type="success"
              effect="plain"
              class="skill-tag"
            >
              {{ sk }}
            </el-tag>
          </div>
          <div
            v-if="gapData.missingSkills.length"
            style="margin-top: 12px"
          >
            <h4>📚 建议学习（{{ gapData.missingSkills.length }}）</h4>
            <el-tag
              v-for="sk in gapData.missingSkills"
              :key="sk"
              type="danger"
              effect="plain"
              class="skill-tag"
            >
              {{ sk }}
            </el-tag>
          </div>
          <div
            v-if="gapData.extraSkills.length"
            style="margin-top: 12px"
          >
            <h4>💡 额外掌握（{{ gapData.extraSkills.length }}）</h4>
            <el-tag
              v-for="sk in gapData.extraSkills"
              :key="sk"
              type="info"
              effect="plain"
              class="skill-tag"
            >
              {{ sk }}
            </el-tag>
          </div>
        </div>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.recommend-layout {
  display: flex;
  gap: var(--space-6);
  align-items: flex-start;
}

.profile-panel {
  position: sticky;
  top: calc(var(--topbar-height) + var(--space-6));
  width: 320px;
  flex-shrink: 0;
  padding: var(--space-5);
  border-radius: var(--radius-8);
  background: var(--app-surface);
  border: 1px solid var(--app-border);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  color: var(--app-text);
  font-size: var(--font-size-16);
  font-weight: var(--font-weight-semibold);
}

.salary-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
}

.salary-row .el-input-number {
  flex: 1;
}

.salary-sep {
  color: var(--app-muted);
  flex-shrink: 0;
}

.recommend-main {
  flex: 1;
  min-width: 0;
}

.empty-wrap {
  padding: var(--space-10) 0;
}

.result-summary {
  margin-bottom: var(--space-4);
  color: var(--app-muted);
  font-size: var(--font-size-13);
}

.result-summary strong {
  color: var(--app-accent);
  font-weight: var(--font-weight-semibold);
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.recommend-card {
  padding: var(--space-5);
  border-radius: var(--radius-8);
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  transition: box-shadow var(--motion-duration-fast) var(--motion-ease-standard);
}

.recommend-card:hover {
  box-shadow: var(--shadow-md);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-4);
  margin-bottom: var(--space-3);
}

.card-title {
  margin: 0;
  font-size: var(--font-size-16);
  font-weight: var(--font-weight-semibold);
  color: var(--app-text);
}

.match-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.match-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-6);
  background: var(--app-surface-subtle);
  min-width: 72px;
}

.match-score {
  font-size: var(--font-size-20);
  font-weight: var(--font-weight-bold);
  line-height: 1.2;
}

.match-label {
  font-size: 11px;
  color: var(--app-muted);
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-4);
  margin-bottom: var(--space-3);
  color: var(--app-muted);
  font-size: var(--font-size-13);
}

.card-meta span:not(:last-child)::after {
  content: '|';
  margin-left: var(--space-3);
  color: var(--app-border);
}

.skill-match {
  margin-bottom: var(--space-2);
}

.skill-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1);
  margin-bottom: var(--space-2);
}

.skill-tag-label {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-right: var(--space-1);
  color: var(--app-muted);
  font-size: var(--font-size-12);
  white-space: nowrap;
}

.skill-tag {
  margin: 2px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--space-2);
  border-top: 1px solid var(--app-border-subtle);
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
}

.gap-summary {
  display: flex;
  align-items: flex-start;
  gap: var(--space-6);
  margin-bottom: var(--space-4);
}

.gap-suggestion {
  flex: 1;
  padding: var(--space-4);
  border-radius: var(--radius-6);
  background: var(--app-surface-subtle);
  font-size: var(--font-size-14);
  color: var(--app-text-regular);
  line-height: 1.6;
}

.gap-skills h4 {
  margin: 0 0 var(--space-2);
  font-size: var(--font-size-14);
  color: var(--app-text);
}

@media (max-width: 900px) {
  .recommend-layout {
    flex-direction: column;
  }

  .profile-panel {
    position: static;
    width: 100%;
  }

  .gap-summary {
    flex-direction: column;
  }
}
</style>
