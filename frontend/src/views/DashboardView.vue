<template>
  <div class="page">
    <div class="hero-band">
      <h2 style="margin:0 0 8px">运营数据大屏</h2>
      <p style="margin:0;opacity:.92">在线 {{ overview?.onlineUsers ?? 0 }} · 待取 {{ overview?.pendingOrders ?? 0 }} ·
        剩饭率 {{ overview?.wasteRate ?? 0 }}%（同比 {{ overview?.wasteYoYPercent ?? 0 }}%）</p>
    </div>

    <el-row :gutter="12" style="margin-bottom:12px">
      <el-col :md="6" :xs="12">
        <div class="card-soft stat"><div class="stat-label">在线用户</div><div class="stat-value">{{ overview?.onlineUsers ?? 0 }}</div></div>
      </el-col>
      <el-col :md="6" :xs="12">
        <div class="card-soft stat"><div class="stat-label">待取餐订单</div><div class="stat-value">{{ overview?.pendingOrders ?? 0 }}</div></div>
      </el-col>
      <el-col :md="6" :xs="12">
        <div class="card-soft stat"><div class="stat-label">剩饭率</div><div class="stat-value">{{ overview?.wasteRate ?? 0 }}%</div></div>
      </el-col>
      <el-col :md="6" :xs="12">
        <div class="card-soft stat"><div class="stat-label">剩饭率同比</div><div class="stat-value">{{ overview?.wasteYoYPercent ?? 0 }}%</div></div>
      </el-col>
    </el-row>

    <div class="card-soft" style="margin-bottom:12px; display:flex; gap:8px; flex-wrap:wrap; align-items:center">
      <el-date-picker v-model="filters.date" type="date" value-format="YYYY-MM-DD" placeholder="统计日期" />
      <el-select v-model="filters.canteenId" placeholder="全部食堂" clearable style="width:170px">
        <el-option v-for="c in canteens" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="filters.stallId" placeholder="全部档口" clearable style="width:200px">
        <el-option v-for="s in stallOptions" :key="s.id" :label="`${s.name}（待处理 ${s.pendingCount}）`" :value="s.id" />
      </el-select>
      <el-button type="primary" @click="load">筛选刷新</el-button>
      <el-button @click="clearFilter">清空</el-button>
      <el-button @click="$router.push('/stall')">去档口接单</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :md="14" :xs="24"><div class="card-soft"><div ref="lineRef" style="height:320px"></div></div></el-col>
      <el-col :md="10" :xs="24"><div class="card-soft"><div ref="pieRef" style="height:320px"></div></div></el-col>
      <el-col :span="24" style="margin-top:16px">
        <div class="card-soft"><div ref="wasteRef" style="height:280px"></div></div>
      </el-col>
      <el-col :span="24" style="margin-top:16px">
        <div class="card-soft">
          <h3>TOP 10 热销菜品</h3>
          <el-table :data="overview?.topDishes10 || []" empty-text="暂无销量数据">
            <el-table-column prop="name" label="菜品" />
            <el-table-column prop="qty" label="销量" width="120" sortable />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import http from '../api/http'

const route = useRoute()
const overview = ref<any>(null)
const lineRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()
const wasteRef = ref<HTMLDivElement>()
const stallOptions = ref<any[]>([])
const filters = reactive<{ date: string | null; canteenId: number | undefined; stallId: number | undefined }>({
  date: null,
  canteenId: undefined,
  stallId: undefined,
})

const canteens = computed(() => {
  const map = new Map<number, string>()
  for (const s of stallOptions.value) {
    if (s.canteenId != null) map.set(s.canteenId, `第 ${s.canteenId} 食堂`)
  }
  return Array.from(map.entries()).map(([id, name]) => ({ id, name }))
})

let lineChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null
let wasteChart: echarts.ECharts | null = null

async function loadOptions() {
  try {
    const res: any = await http.get('/order/stall/options')
    stallOptions.value = res.data || []
  } catch {
    stallOptions.value = []
  }
}

async function load() {
  const res: any = await http.get('/dashboard/overview', {
    params: {
      date: filters.date || undefined,
      canteenId: filters.canteenId || undefined,
      stallId: filters.stallId || undefined,
    },
  })
  overview.value = res.data
  await nextTick()
  renderCharts()
}

function clearFilter() {
  filters.date = null
  filters.canteenId = undefined
  filters.stallId = undefined
  load()
}

function renderCharts() {
  if (lineRef.value) {
    lineChart = lineChart || echarts.init(lineRef.value)
    const trend = overview.value?.orderTrend24h || []
    lineChart.setOption({
      title: { text: '近24小时订单量' },
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 24, top: 50, bottom: 30 },
      xAxis: { type: 'category', data: trend.map((i: any) => i.hourLabel) },
      yAxis: { type: 'value' },
      series: [{
        type: 'line', smooth: true, data: trend.map((i: any) => i.cnt),
        areaStyle: { color: 'rgba(26,95,158,.15)' }, lineStyle: { color: '#1a5f9e' },
      }],
    }, true)
  }
  if (pieRef.value) {
    pieChart = pieChart || echarts.init(pieRef.value)
    pieChart.setOption({
      title: { text: '档口排队人数' },
      tooltip: { trigger: 'item' },
      series: [{ type: 'pie', radius: ['40%', '70%'], data: overview.value?.stallQueue || [] }],
    }, true)
  }
  if (wasteRef.value) {
    wasteChart = wasteChart || echarts.init(wasteRef.value)
    const curve = overview.value?.wasteCurve || []
    const days = curve.map((_: any, i: number) => {
      const d = new Date()
      d.setDate(d.getDate() - (curve.length - 1 - i))
      return `${d.getMonth() + 1}/${d.getDate()}`
    })
    wasteChart.setOption({
      title: { text: '近7日剩饭率趋势' },
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 24, top: 50, bottom: 30 },
      xAxis: { type: 'category', data: days },
      yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
      series: [{
        type: 'line', smooth: true, data: curve,
        markLine: {
          data: [{ yAxis: 20, name: '预警线' }],
          lineStyle: { color: '#f56c6c', type: 'dashed' },
          label: { formatter: '预警线 20%' },
        },
        areaStyle: { color: 'rgba(245,108,108,.12)' },
        lineStyle: { color: '#f56c6c' },
      }],
    }, true)
  }
}

function resize() {
  lineChart?.resize()
  pieChart?.resize()
  wasteChart?.resize()
}

onMounted(async () => {
  await loadOptions()
  if (route.query.stallId) filters.stallId = Number(route.query.stallId)
  await load()
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  lineChart?.dispose()
  pieChart?.dispose()
  wasteChart?.dispose()
})
</script>

<style scoped>
.stat { text-align: center; }
.stat-label { color: var(--campus-muted); font-size: 13px; }
.stat-value { font-size: 26px; font-weight: 700; color: #123f6b; margin-top: 6px; }
</style>
