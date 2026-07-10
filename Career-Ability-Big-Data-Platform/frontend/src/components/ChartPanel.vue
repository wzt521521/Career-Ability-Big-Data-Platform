<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init } from '../utils/echarts.js'

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  option: { type: Object, required: true },
  loading: { type: Boolean, default: false },
  empty: { type: Boolean, default: false }
})

const element = ref(null)
let chart
let observer

const render = async () => {
  await nextTick()
  if (!element.value || props.empty) return
  if (!chart) chart = init(element.value)
  chart.setOption(props.option, true)
}

watch(() => props.option, render, { deep: true })
watch(() => props.empty, render)

onMounted(() => {
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(element.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <section class="chart-panel" v-loading="loading">
    <header class="chart-heading">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <slot name="actions" />
    </header>
    <el-empty v-if="empty && !loading" description="暂无统计数据" :image-size="72" />
    <div v-else ref="element" class="chart-canvas" />
  </section>
</template>

<style scoped>
.chart-panel {
  min-width: 0;
  min-height: 340px;
  padding: 20px 22px 16px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
}
.chart-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.chart-heading h2 { margin: 0; color: var(--ink); font-size: 16px; font-weight: 650; }
.chart-heading p { margin: 5px 0 0; color: var(--muted); font-size: 12px; }
.chart-canvas { width: 100%; height: 276px; margin-top: 4px; }
</style>
