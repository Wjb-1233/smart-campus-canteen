<template>
  <div class="page">
    <div class="hero-band">
      <h2 style="margin:0 0 8px">购物车结账</h2>
      <p style="margin:0;opacity:.92">
        合计 ¥{{ cart.totalAmount.toFixed(2) }} · 预计热量 {{ cart.totalCalorie }} kcal ·
        {{ stallCount }} 个档口，提交后将按档口自动拆单
      </p>
    </div>

    <div class="card-soft">
      <el-table :data="cart.items" v-loading="cart.loading" empty-text="购物车为空，去菜品搜索挑一份吧">
        <el-table-column prop="name" label="菜品" min-width="140" />
        <el-table-column prop="stallName" label="档口" width="130" />
        <el-table-column label="时段" width="90">
          <template #default="{ row }">{{ periodName(row.mealPeriod) }}</template>
        </el-table-column>
        <el-table-column label="单价" width="90">
          <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column label="小计" width="100">
          <template #default="{ row }">¥{{ (row.price * row.quantity).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="热量" width="90">
          <template #default="{ row }">{{ (row.calorie || 0) * row.quantity }} kcal</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="cart.remove(row.id)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top:16px">
        <el-alert v-if="cart.totalCalorie > 900" type="warning" show-icon :closable="false"
          title="营养提醒：本单热量偏高，建议搭配低脂轻食或减少主食份量" />
        <el-alert v-else-if="cart.items.length && cart.totalCalorie < 400" type="info" show-icon :closable="false"
          title="营养提醒：本单热量偏低，建议补充主食或蛋白质来源" />
      </div>

      <!-- 订单确认页菜品成分图谱（需求：订单确认页菜品成分图谱展示） -->
      <div v-if="cart.items.length" class="nutrition-panel">
        <div class="panel-head">
          <h3 style="margin:0">本单营养成分图谱</h3>
          <span class="panel-tip">按单餐推荐摄入量折算（{{ recommend.calorie }} kcal / 蛋白 {{ recommend.protein }} g 为 100%）</span>
        </div>
        <el-row :gutter="16" style="margin-top:10px">
          <el-col :md="12" :xs="24">
            <div ref="radarRef" style="height:280px"></div>
          </el-col>
          <el-col :md="12" :xs="24">
            <div class="nut-grid">
              <div v-for="n in nutritionCards" :key="n.label" class="nut-cell">
                <div class="nut-label">{{ n.label }}</div>
                <div class="nut-value">{{ n.value }}<span class="nut-unit">{{ n.unit }}</span></div>
                <div class="nut-sub">单餐建议 {{ n.recommend }}{{ n.unit }} ·
                  <span :class="n.rate > 120 ? 'over' : n.rate < 60 ? 'under' : 'ok'">{{ n.rate }}%</span>
                </div>
              </div>
            </div>
            <div class="tag-cloud">
              <el-tag v-for="(item, idx) in cart.items" :key="idx" size="small" effect="plain" style="margin:4px 6px 0 0">
                {{ item.name }} ×{{ item.quantity }}
                <span v-if="item.protein"> · 蛋白 {{ (item.protein * item.quantity).toFixed(0) }}g</span>
              </el-tag>
            </div>
          </el-col>
        </el-row>
      </div>

      <div style="margin-top:16px; display:flex; align-items:center; gap:16px; flex-wrap:wrap">
        <div>
          <span style="margin-right:8px;color:var(--campus-muted)">取餐时段：</span>
          <el-select v-model="mealPeriod" style="width:130px">
            <el-option v-for="(label, key) in MEAL_PERIOD_NAMES" :key="key" :label="label" :value="key" />
          </el-select>
        </div>
        <div>
          <span style="margin-right:8px;color:var(--campus-muted)">支付方式：</span>
          <el-radio-group v-model="payChannel">
            <el-radio-button value="BALANCE">校园卡余额</el-radio-button>
            <el-radio-button value="WECHAT">微信支付</el-radio-button>
            <el-radio-button value="ALIPAY">支付宝</el-radio-button>
          </el-radio-group>
        </div>
        <span v-if="payChannel === 'BALANCE'" style="color:var(--campus-muted);font-size:13px">
          余额 ¥{{ (balance ?? 0).toFixed(2) }}
        </span>
        <el-button type="primary" :disabled="!cart.items.length || submitting" :loading="loading" @click="openConfirm">
          确认下单
        </el-button>
        <el-button :disabled="!cart.items.length" @click="clearAll">清空</el-button>
      </div>
    </div>

    <!-- 营养搭配建议弹窗：需用户确认后再进入支付流程 -->
    <el-dialog v-model="adviceVisible" title="营养搭配建议" width="460px">
      <div v-if="advice.length">
        <el-alert v-for="a in advice" :key="a.title" :title="a.title" :type="a.type" show-icon :closable="false"
          style="margin-bottom:10px" />
      </div>
      <el-empty v-else description="当前搭配营养均衡，无需调整" />
      <template #footer>
        <div style="display:flex;justify-content:space-between">
          <el-button @click="adviceVisible = false">返回调整</el-button>
          <el-button type="primary" :loading="loading" @click="proceedFromAdvice">我已了解，继续下单</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 模拟支付弹窗 -->
    <el-dialog v-model="payVisible" :title="payTitle" width="420px" :close-on-click-modal="false" :show-close="false">
      <div style="text-align:center;padding:8px 0 4px">
        <div style="font-size:34px;margin-bottom:10px">{{ payIcon }}</div>
        <div style="font-size:18px;font-weight:600;margin-bottom:4px">应付 ¥{{ cart.totalAmount.toFixed(2) }}</div>
        <div style="color:var(--campus-muted);font-size:13px">{{ paySub }}</div>
      </div>
      <template #footer>
        <div style="display:flex;justify-content:space-between">
          <el-button @click="payVisible = false">取消支付</el-button>
          <el-button type="success" :loading="loading" @click="place">模拟支付成功</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import http from '../api/http'
import { MEAL_PERIOD_NAMES, useCartStore } from '../stores/cart'

const cart = useCartStore()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const payChannel = ref('BALANCE')
const payVisible = ref(false)
const adviceVisible = ref(false)
const advice = ref<any[]>([])
const mealPeriod = ref('LUNCH')
const balance = ref<number | null>(null)
const radarRef = ref<HTMLDivElement>()
let radar: echarts.ECharts | null = null

/** 单餐推荐摄入量：默认按成人一餐估算，绑定健康档案后使用个人目标值 */
const profileTarget = ref<{ calorie?: number; protein?: number }>({})

const stallCount = computed(() => new Set(cart.items.map((i) => i.stallId)).size)
const nut = computed(() => cart.nutritionTotal)

const recommend = computed(() => ({
  calorie: Math.round((profileTarget.value.calorie || 2100) / 3),
  protein: Math.round((profileTarget.value.protein || 85) / 3),
}))

const nutritionCards = computed(() => {
  const n = nut.value
  const r = recommend.value
  return [
    { label: '热量', value: Math.round(n.calorie), unit: 'kcal', recommend: r.calorie, rate: pct(n.calorie, r.calorie) },
    { label: '蛋白质', value: Math.round(n.protein), unit: 'g', recommend: r.protein, rate: pct(n.protein, r.protein) },
    { label: '脂肪', value: Math.round(n.fat), unit: 'g', recommend: 25, rate: pct(n.fat, 25) },
    { label: '碳水', value: Math.round(n.carb), unit: 'g', recommend: 110, rate: pct(n.carb, 110) },
  ]
})

const payTitle = computed(() =>
  payChannel.value === 'WECHAT' ? '微信支付（模拟）' : payChannel.value === 'ALIPAY' ? '支付宝（模拟）' : '校园卡余额支付')
const payIcon = computed(() =>
  payChannel.value === 'WECHAT' ? 'W' : payChannel.value === 'ALIPAY' ? 'A' : '卡')
const paySub = computed(() =>
  payChannel.value === 'BALANCE'
    ? '将扣除账户余额，余额不足将自动取消订单'
    : '模拟第三方回调，点击后自动回调确认支付')

function pct(actual: number, base: number) {
  if (!base) return 0
  return Math.round((actual / base) * 100)
}

function periodName(p: string) {
  return MEAL_PERIOD_NAMES[p] || p
}

async function loadBalance() {
  try {
    const res: any = await http.get('/auth/profile')
    balance.value = Number(res.data?.balance ?? 0)
    const health: any = res.data?.health || {}
    profileTarget.value = {
      calorie: Number(health.targetCalorie) || undefined,
      protein: Number(health.targetProtein) || undefined,
    }
  } catch {
    balance.value = null
  }
}

/** 点击「确认下单」：有营养建议则先弹窗让用户确认，避免支付与提示同时发生 */
async function openConfirm() {
  if (!cart.items.length) return
  advice.value = buildAdvice()
  if (advice.value.length) {
    adviceVisible.value = true
    return
  }
  await proceed()
}

async function proceedFromAdvice() {
  adviceVisible.value = false
  await proceed()
}

/** 营养建议确认后的支付分支 */
async function proceed() {
  if (payChannel.value === 'BALANCE') {
    await loadBalance()
    if (balance.value != null && balance.value < cart.totalAmount) {
      try {
        await ElMessageBox.confirm(
          `校园卡余额 ¥${balance.value.toFixed(2)} 不足以支付 ¥${cart.totalAmount.toFixed(2)}，是否前往个人中心充值？`,
          '余额不足', { confirmButtonText: '去充值', cancelButtonText: '返回', type: 'warning' })
        router.push('/profile')
        return
      } catch {
        return
      }
    }
    await place()
  } else {
    payVisible.value = true
  }
}

function buildAdvice() {
  const total = cart.totalCalorie
  const list: any[] = []
  if (total > 900) {
    list.push({ title: '本单热量偏高（' + total + ' kcal），建议替换为低卡轻食或减少主食份量', type: 'warning' })
  }
  if (total > 0 && total < 400) {
    list.push({ title: '热量偏低，建议搭配杂粮粥或鸡胸沙拉补充能量', type: 'info' })
  }
  if (nut.value.protein > 0 && nut.value.protein < recommend.value.protein * 0.5) {
    list.push({ title: '蛋白质偏少（' + Math.round(nut.value.protein) + 'g），建议补充鸡蛋 / 鸡胸 / 豆制品', type: 'info' })
  }
  const hasVegetable = cart.items.some((i) => /菜|蔬|沙拉|西兰花|青菜|番茄/.test(i.name))
  const hasProtein = cart.items.some((i) => (i.protein || 0) > 8 || /鸡|牛|鱼|虾|蛋|豆|肉/.test(i.name))
  const hasStaple = cart.items.some((i) => /饭|面|粥|馒头|包子|粉/.test(i.name))
  if (!hasVegetable) list.push({ title: '建议搭配一份蔬菜类菜品，补充膳食纤维', type: 'info' })
  if (!hasProtein) list.push({ title: '建议补充优质蛋白（鸡蛋 / 鸡胸 / 豆制品）', type: 'info' })
  if (!hasStaple) list.push({ title: '建议搭配主食，保证碳水摄入', type: 'info' })
  return list.slice(0, 3)
}

function renderRadar() {
  if (!radarRef.value) return
  radar = radar || echarts.init(radarRef.value)
  const cards = nutritionCards.value
  radar.setOption({
    tooltip: {},
    legend: { bottom: 0, data: ['本单占比', '单餐推荐线'] },
    radar: {
      indicator: cards.map((c) => ({ name: c.label, max: 150 })),
      radius: '62%',
      splitLine: { lineStyle: { color: '#dfe8f3' } },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: cards.map((c) => c.rate),
            name: '本单占比',
            areaStyle: { color: 'rgba(26,95,158,.25)' },
            lineStyle: { color: '#1a5f9e' },
            itemStyle: { color: '#1a5f9e' },
          },
          {
            value: cards.map(() => 100),
            name: '单餐推荐线',
            lineStyle: { type: 'dashed', color: '#e6a23c' },
            itemStyle: { color: '#e6a23c' },
          },
        ],
      },
    ],
  })
}

watch(() => [cart.items.length, nut.value.calorie, nut.value.protein], async () => {
  await nextTick()
  if (cart.items.length) renderRadar()
})

async function place() {
  if (submitting.value) return
  submitting.value = true
  loading.value = true
  try {
    const res: any = await http.post('/order/place', {
      mealPeriod: mealPeriod.value,
      payChannel: payChannel.value,
      items: cart.items.map((i) => ({ dishId: i.dishId, quantity: i.quantity })),
    })
    const data = res.data || {}
    const count = data.orderCount || 1
    const channelName = payChannel.value === 'WECHAT' ? '微信' : payChannel.value === 'ALIPAY' ? '支付宝' : '余额'
    ElMessage.success(`下单成功：${channelName}支付 · 拆成 ${count} 笔档口订单，取餐码 ${data.pickupCode || '-'}`)
    if (data.replaceLogs?.length) {
      ElMessage.info(`库存不足，已自动替换 ${data.replaceLogs.length} 项同类菜品`)
    }
    payVisible.value = false
    cart.reset()
    router.push('/orders')
  } finally {
    loading.value = false
    submitting.value = false
    await cart.fetch().catch(() => undefined)
  }
}

async function clearAll() {
  await cart.clear()
  ElMessage.success('购物车已清空')
}

function resize() {
  radar?.resize()
}

onMounted(async () => {
  await cart.fetch()
  if (cart.items.length) mealPeriod.value = cart.items[0].mealPeriod
  await loadBalance()
  await nextTick()
  renderRadar()
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  radar?.dispose()
})
</script>

<style scoped>
.nutrition-panel {
  margin-top: 18px;
  border: 1px dashed var(--campus-line);
  border-radius: 12px;
  padding: 14px 16px 6px;
  background: rgba(26, 95, 158, .03);
}
.panel-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}
.panel-tip {
  color: var(--campus-muted);
  font-size: 12px;
}
.nut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.nut-cell {
  background: #fff;
  border: 1px solid var(--campus-line);
  border-radius: 10px;
  padding: 10px 12px;
}
.nut-label {
  color: var(--campus-muted);
  font-size: 12px;
}
.nut-value {
  font-size: 22px;
  font-weight: 700;
  color: #123f6b;
  line-height: 1.3;
}
.nut-unit {
  font-size: 12px;
  margin-left: 4px;
  color: var(--campus-muted);
  font-weight: 400;
}
.nut-sub {
  font-size: 12px;
  color: var(--campus-muted);
}
.nut-sub .ok { color: #37b26c; }
.nut-sub .over { color: #e04b4b; }
.nut-sub .under { color: #e6a23c; }
.tag-cloud {
  margin-top: 10px;
}
</style>
