<template>
  <div class="page">
    <div class="hero-band">
      <h2 style="margin:0 0 8px">我的营养周报</h2>
      <p style="margin:0;opacity:.92">{{ data?.proteinWarning || '正在统计近 7 日营养摄入…' }}</p>
    </div>

    <el-alert v-if="lowProtein" type="warning" show-icon :closable="false" style="margin-bottom:12px"
      title="蛋白质摄入不足，建议在推荐菜品中选择高蛋白套餐（鸡胸 / 豆制品 / 鸡蛋）" />

    <el-row :gutter="16">
      <el-col :md="16" :xs="24">
        <div class="card-soft">
          <div ref="chartRef" style="height:340px"></div>
        </div>
      </el-col>
      <el-col :md="8" :xs="24">
        <div class="card-soft" style="height:100%">
          <h3 style="margin-top:0">近 7 日概览</h3>
          <el-statistic title="日均热量 (kcal)" :value="avgCalorie" />
          <el-statistic title="日均蛋白质 (g)" :value="avgProtein" style="margin-top:14px" />
          <el-statistic title="达标天数 (蛋白质≥56g)" :value="passDays" style="margin-top:14px" />
          <el-divider />
          <div class="tip">{{ adviceText }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="card-soft" style="margin-top:16px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <h3 style="margin:0">为你的营养目标推荐</h3>
        <el-button link type="primary" @click="$router.push('/dishes')">去点餐</el-button>
      </div>
      <el-row :gutter="12" style="margin-top:12px">
        <el-col v-for="d in recommends" :key="d.id" :md="6" :sm="12" :xs="24" style="margin-bottom:12px">
          <el-card shadow="hover" @click="$router.push(`/dish/${d.id}`)" style="cursor:pointer">
            <div class="dish-name">{{ d.name }}</div>
            <div class="dish-meta">
              ¥{{ Number(d.price).toFixed(2) }} · {{ d.nutrition?.calorie ?? 0 }} kcal · 蛋白 {{ d.nutrition?.protein ?? 0 }} g
            </div>
            <div class="dish-meta">{{ (d.tags || []).slice(0, 2).join(' / ') || '营养均衡' }}</div>
          </el-card>
        </el-col>
        <el-col v-if="!recommends.length" :span="24">
          <el-empty description="暂无推荐，稍后再试" />
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import http from '../api/http'

const data = ref<any>(null)
const recommends = ref<any[]>([])
const chartRef = ref<HTMLDivElement>()
const mealPeriod = ref(currentMealPeriod())

function currentMealPeriod() {
  const h = new Date().getHours()
  if (h < 10) return 'BREAKFAST'
  if (h < 15) return 'LUNCH'
  if (h < 21) return 'DINNER'
  return 'NIGHT'
}

const calories = computed<number[]>(() => data.value?.calories || [])
const proteins = computed<number[]>(() => data.value?.proteins?.map((p: any) => Number(p)) || [])
const avgCalorie = computed(() => (calories.value.length ? Math.round(calories.value.reduce((a, b) => a + b, 0) / calories.value.length) : 0))
const avgProtein = computed(() => (proteins.value.length ? Number((proteins.value.reduce((a, b) => a + b, 0) / proteins.value.length).toFixed(1)) : 0))
const passDays = computed(() => proteins.value.filter((p) => p >= 56).length)
const lowProtein = computed(() => proteins.value.length > 0 && proteins.value[proteins.value.length - 1] < 56)

const adviceText = computed(() => {
  if (!calories.value.length) return '暂无摄入数据，先点一份餐吧。'
  if (avgProtein.value < 40) return '蛋白质明显偏低：建议每餐补充 1 份优质蛋白（鸡蛋/鸡胸/豆制品），并可选择「高蛋白」标签菜品。'
  if (avgCalorie.value > 2200) return '热量偏高：建议减少油炸类与含糖饮料，增加蔬菜与粗粮比例。'
  if (avgCalorie.value < 1400) return '热量偏低：建议保证三餐规律并适当增加主食与坚果摄入。'
  return '整体营养结构较为均衡，继续保持规律三餐即可。'
})

async function loadWeekly() {
  const res: any = await http.get('/nutrition/weekly')
  data.value = res.data
  await nextTick()
  renderChart()
}

function renderChart() {
  if (!chartRef.value) return
  const chart = echarts.init(chartRef.value)
  const days = (data.value?.days || []).map((d: string) => d.slice(5))
  chart.setOption({
    color: ['#1a5f9e', '#37b26c'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['热量(kcal)', '蛋白质(g)'], top: 0 },
    grid: { left: 50, right: 50, top: 46, bottom: 30 },
    xAxis: { type: 'category', data: days },
    yAxis: [
      { type: 'value', name: 'kcal' },
      { type: 'value', name: 'g' },
    ],
    series: [
      { name: '热量(kcal)', type: 'bar', barWidth: 18, itemStyle: { borderRadius: [6, 6, 0, 0] }, data: calories.value },
      { name: '蛋白质(g)', type: 'line', smooth: true, yAxisIndex: 1, symbolSize: 8, data: proteins.value },
    ],
  })
  window.addEventListener('resize', () => chart.resize())
}

async function loadRecommend() {
  try {
    const res: any = await http.get('/dish/recommend', { params: { mealPeriod: mealPeriod.value, limit: 4 } })
    recommends.value = res.data || []
  } catch {
    recommends.value = []
  }
}

onMounted(async () => {
  await loadWeekly()
  await loadRecommend()
})
</script>

<style scoped>
.dish-name { font-weight: 600; color: #123f6b; }
.dish-meta { font-size: 12px; color: var(--campus-muted); margin-top: 6px; }
.tip { font-size: 13px; color: #41556b; line-height: 1.7; }
</style>
