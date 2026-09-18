<template>
  <div class="page" v-if="dish">
    <div class="hero-band">
      <h2 style="margin:0 0 8px">{{ dish.name }}</h2>
      <p style="margin:0">{{ dish.stallName }} · {{ dish.mealPeriod }} · {{ dish.stockHint }}</p>
    </div>
    <el-row :gutter="16">
      <el-col :md="14" :xs="24">
        <div class="card-soft">
          <p>单价：<b>¥{{ dish.price }}</b></p>
          <p>库存：{{ dish.stock }}</p>
          <div>
            <el-tag v-for="t in dish.tags" :key="t" class="tag-chip">{{ t }}</el-tag>
            <el-tag v-if="dish.nutritionVerified" type="success" effect="dark" class="tag-chip">
              营养数据已核验
            </el-tag>
            <el-tag v-else type="info" class="tag-chip">营养数据待核验</el-tag>
          </div>
          <el-divider />
          <h3>营养成分图谱</h3>
          <div ref="chartRef" style="height:280px"></div>
        </div>
      </el-col>
      <el-col :md="10" :xs="24">
        <div class="card-soft">
          <el-input-number v-model="qty" :min="1" :max="Math.max(1, dish.stock || 1)" />
          <el-button type="primary" style="margin-left:12px" :loading="adding" :disabled="dish.stock <= 0" @click="addCart">
            {{ dish.stock <= 0 ? '已售罄' : '加入购物车' }}
          </el-button>
          <el-button style="margin-left:8px" @click="$router.push('/cart')">去购物车</el-button>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { useCartStore } from '../stores/cart'

const route = useRoute()
const dish = ref<any>(null)
const qty = ref(1)
const adding = ref(false)
const chartRef = ref<HTMLDivElement>()
const cart = useCartStore()

onMounted(async () => {
  const res: any = await http.get(`/dish/${route.params.id}`)
  dish.value = res.data
  await nextTick()
  if (chartRef.value && dish.value?.nutrition) {
    const chart = echarts.init(chartRef.value)
    const n = dish.value.nutrition
    chart.setOption({
      color: ['#1a5f9e', '#3a8fd0', '#7eb6e8', '#a9cdea'],
      tooltip: {},
      radar: {
        indicator: [
          { name: '热量', max: 800 },
          { name: '蛋白质', max: 50 },
          { name: '脂肪', max: 40 },
          { name: '碳水', max: 100 },
        ],
      },
      series: [{
        type: 'radar',
        data: [{ value: [n.calorie || 0, n.protein || 0, n.fat || 0, n.carb || 0], name: dish.value.name }],
      }],
    })
  }
})

async function addCart() {
  adding.value = true
  try {
    await cart.add(dish.value.id, qty.value, dish.value.mealPeriod)
    ElMessage.success('已加入购物车')
  } finally {
    adding.value = false
  }
}
</script>
