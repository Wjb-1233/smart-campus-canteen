<template>
  <div class="page">
    <div class="hero-band">
      <h2 style="margin:0 0 8px">{{ greeting }}，{{ user.realName }}</h2>
      <p style="margin:0;opacity:.9">当前推荐时段：{{ MEAL_PERIOD_NAMES[mealPeriod] }} · 基于历史偏好 + 健康档案 + 热度的混合推荐</p>
    </div>

    <div class="card-soft" style="margin-bottom:16px; display:flex; gap:8px; align-items:center; flex-wrap:wrap">
      <el-radio-group v-model="mealPeriod" @change="load">
        <el-radio-button v-for="(label, key) in MEAL_PERIOD_NAMES" :key="key" :value="key">{{ label }}</el-radio-button>
      </el-radio-group>
      <el-button link type="primary" @click="load">换一批</el-button>
      <el-button link type="primary" @click="$router.push('/dishes')">全部菜品</el-button>
      <el-button link type="primary" @click="$router.push('/cart')">购物车</el-button>
      <el-button link type="primary" @click="$router.push('/orders')">我的订单</el-button>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="d in list" :key="d.id" :xs="24" :sm="12" :md="8">
        <div class="card-soft dish" @click="$router.push(`/dish/${d.id}`)">
          <div class="title">{{ d.name }}</div>
          <div class="meta">
            ¥{{ Number(d.price).toFixed(2) }} · 热量 {{ d.nutrition?.calorie ?? 0 }} kcal ·
            蛋白 {{ d.nutrition?.protein ?? 0 }} g · 热度 {{ d.heatScore }}
          </div>
          <div class="meta">推荐理由：{{ d.reason || '综合历史偏好与热度' }}</div>
          <div class="meta">{{ d.stockHint }}</div>
          <div>
            <el-tag v-for="t in d.tags" :key="t" size="small" class="tag-chip" type="info">{{ t }}</el-tag>
          </div>
        </div>
      </el-col>
      <el-col v-if="!loading && !list.length" :span="24">
        <div class="card-soft"><el-empty description="该时段暂无推荐菜品，换个时段看看" /></div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import http from '../api/http'
import { MEAL_PERIOD_NAMES } from '../utils/dict'
import { useUserStore } from '../stores/user'

const user = useUserStore()
const list = ref<any[]>([])
const loading = ref(false)
const mealPeriod = ref(currentPeriod())

function currentPeriod() {
  const h = new Date().getHours()
  if (h < 10) return 'BREAKFAST'
  if (h < 15) return 'LUNCH'
  if (h < 21) return 'DINNER'
  return 'NIGHT'
}

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/dish/recommend', { params: { mealPeriod: mealPeriod.value, limit: 6 } })
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.dish { margin-bottom: 16px; cursor: pointer; transition: transform .2s ease; }
.dish:hover { transform: translateY(-3px); }
.title { font-size: 18px; font-weight: 600; margin-bottom: 6px; }
.meta { color: var(--campus-muted); margin-bottom: 6px; font-size: 13px; }
</style>
