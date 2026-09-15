<template>
  <div class="page">
    <div class="hero-band" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
      <div>
        <h2 style="margin:0 0 8px">我的订单</h2>
        <p style="margin:0;opacity:.92">档口接单 / 出餐进度实时推送 · 支持取消与催单 · 剩饭浪费登记</p>
      </div>
      <el-tag :type="wsOk ? 'success' : 'info'">{{ wsOk ? '实时同步中' : '手动刷新' }}</el-tag>
    </div>

    <div class="card-soft" style="margin-bottom:12px; display:flex; gap:8px; align-items:center; flex-wrap:wrap">
      <el-radio-group v-model="filter" size="small">
        <el-radio-button value="">全部({{ rows.length }})</el-radio-button>
        <el-radio-button value="ACTIVE">进行中({{ activeCount }})</el-radio-button>
        <el-radio-button value="PICKED">已完成</el-radio-button>
        <el-radio-button value="CANCELLED">已取消</el-radio-button>
      </el-radio-group>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <el-table :data="filteredRows" v-loading="loading" class="card-soft" row-key="id"
      empty-text="暂无订单，去点一份吧">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div style="padding:8px 16px">
            <el-steps :active="statusStep(row.status)" align-center finish-status="success" style="margin-bottom:12px"
              v-if="row.status !== 'CANCELLED' && row.status !== 'CREATED'">
              <el-step title="已支付" description="等待档口接单" />
              <el-step title="备餐中" description="档口正在制作" />
              <el-step title="待取餐" description="凭取餐码取餐" />
              <el-step title="已取餐" description="订单完成" />
            </el-steps>
            <el-alert v-if="row.status === 'ABNORMAL'" type="error" show-icon :closable="false"
              :title="`异常处理中：${row.abnormalReason || '档口正在处理'}`" style="margin-bottom:12px" />
            <el-table :data="row.items || []" size="small">
              <el-table-column prop="dishName" label="菜品" min-width="140" />
              <el-table-column prop="quantity" label="数量" width="80" />
              <el-table-column label="单价" width="90">
                <template #default="s">¥{{ Number(s.row.unitPrice || 0).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="热量" width="100">
                <template #default="s">{{ (s.row.calorie || 0) * s.row.quantity }} kcal</template>
              </el-table-column>
              <el-table-column label="备注" width="120">
                <template #default="s">
                  <el-tag v-if="s.row.replacedFrom" type="warning" size="small">库存替换</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
            <div style="margin-top:12px; display:flex; gap:8px; flex-wrap:wrap">
              <el-button v-if="canWaste(row)" link type="warning" @click="openWaste(row)">登记剩饭浪费</el-button>
              <el-button v-if="canCancel(row)" link type="danger" @click="cancel(row)">取消订单</el-button>
              <el-button v-if="canUrge(row)" link type="primary" @click="urge(row)">催单</el-button>
              <el-tag v-if="row.status === 'READY'" type="success" size="small" effect="plain">
                已出餐，如需处理请联系档口
              </el-tag>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="orderNo" label="订单号" min-width="160" />
      <el-table-column label="档口" width="80">
        <template #default="{ row }">#{{ row.stallId }}</template>
      </el-table-column>
      <el-table-column label="时段" width="80">
        <template #default="{ row }">{{ mealName(row.mealPeriod) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="90">
        <template #default="{ row }">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="totalCalorie" label="热量" width="90" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="orderStatusTag(row.status)" size="small">{{ row.statusName || statusName(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="取餐码" width="110">
        <template #default="{ row }">
          <b v-if="['PAID','PREPARING','READY'].includes(row.status)" style="color:#1a5f9e">{{ row.pickupCode }}</b>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="expectPickupAt" label="预计取餐" min-width="160" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canUrge(row)" link type="primary" @click="urge(row)">催单</el-button>
          <el-button v-if="canCancel(row)" link type="danger" @click="cancel(row)">取消</el-button>
          <el-button v-if="canWaste(row)" link type="warning" @click="openWaste(row)">浪费登记</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="wasteVisible" title="剩饭浪费登记" width="420px">
      <el-form label-width="90px">
        <el-form-item label="菜品">
          <el-select v-model="wasteForm.dishId" style="width:100%">
            <el-option v-for="it in wasteItems" :key="it.dishId" :label="it.dishName" :value="it.dishId" />
          </el-select>
        </el-form-item>
        <el-form-item label="浪费比例">
          <el-slider v-model="wasteRatioPct" :min="0" :max="100" />
          <span>{{ wasteRatioPct }}%</span>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="wasteForm.reason" placeholder="如：份量过大/口味不合适" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wasteVisible = false">取消</el-button>
        <el-button type="primary" @click="submitWaste">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Client } from '@stomp/stompjs'
import http from '../api/http'
import { MEAL_PERIOD_NAMES, ORDER_STATUS_NAMES, orderStatusTag, statusStep } from '../utils/dict'
import { openStomp } from '../utils/ws'
import { useUserStore } from '../stores/user'

const user = useUserStore()
const rows = ref<any[]>([])
const loading = ref(false)
const wsOk = ref(false)
const filter = ref('')
const wasteVisible = ref(false)
const wasteItems = ref<any[]>([])
const wasteRatioPct = ref(30)
const wasteForm = reactive({ orderId: 0, dishId: undefined as number | undefined, reason: '' })
let client: Client | null = null

const ACTIVE_STATUS = ['CREATED', 'PAID', 'PREPARING', 'READY', 'ABNORMAL']

const activeCount = computed(() => rows.value.filter((r) => ACTIVE_STATUS.includes(r.status)).length)

const filteredRows = computed(() => {
  if (!filter.value) return rows.value
  if (filter.value === 'ACTIVE') return rows.value.filter((r) => ACTIVE_STATUS.includes(r.status))
  return rows.value.filter((r) => r.status === filter.value)
})

function statusName(s: string) {
  return ORDER_STATUS_NAMES[s] || s
}
function mealName(p: string) {
  return MEAL_PERIOD_NAMES[p] || p
}
function canCancel(row: any) {
  // 已出餐（READY）后档口已投入备餐，学生侧不可自助取消，避免食物浪费
  return row.cancellable ?? ['CREATED', 'PAID', 'PREPARING'].includes(row.status)
}
function canUrge(row: any) {
  return ['PAID', 'PREPARING', 'READY'].includes(row.status) && (row.urgeCount ?? 0) < 5
}
function canWaste(row: any) {
  return ['PICKED', 'READY', 'ABNORMAL'].includes(row.status)
}

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/order/mine')
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function cancel(row: any) {
  try {
    await ElMessageBox.confirm(
      row.status === 'CREATED'
        ? '确认取消该订单？库存将立即释放。'
        : '确认取消该订单？已支付金额将退款至校园卡余额。',
      '取消订单', { confirmButtonText: '确认取消', cancelButtonText: '再想想', type: 'warning' })
    const res: any = await http.post(`/order/${row.id}/cancel`, null, { params: { reason: '学生主动取消' } })
    ElMessage.success(`已取消订单${res.data?.refund ? `，退款 ¥${res.data.refund}` : ''}`)
    await load()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function urge(row: any) {
  try {
    await http.post(`/order/${row.id}/urge`)
    ElMessage.warning(`已向档口催单 ${(row.urgeCount || 0) + 1} 次`)
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '催单失败')
  }
}

async function openWaste(row: any) {
  wasteForm.orderId = row.id
  wasteForm.reason = ''
  wasteRatioPct.value = 30
  const res: any = await http.get(`/order/${row.id}/items`)
  wasteItems.value = res.data || []
  wasteForm.dishId = wasteItems.value[0]?.dishId
  wasteVisible.value = true
}

async function submitWaste() {
  if (!wasteForm.dishId) {
    ElMessage.warning('请选择菜品')
    return
  }
  await http.post('/waste/report', {
    orderId: wasteForm.orderId,
    dishId: wasteForm.dishId,
    wasteRatio: wasteRatioPct.value / 100,
    reason: wasteForm.reason || '用户反馈',
  })
  ElMessage.success('浪费记录已提交')
  wasteVisible.value = false
}

function connectWs() {
  if (!user.userId) return
  client = openStomp({
    onConnect: (c) => {
      wsOk.value = true
      // 档口接单 / 出餐后，学生端「我的订单」实时刷新
      c.subscribe(`/topic/user/${user.userId}`, (msg) => {
        try {
          const body = JSON.parse(msg.body)
          if (body.type === 'STATUS' && body.orderNo) {
            ElMessage.info(`订单 ${body.orderNo} 状态更新：${statusName(body.status)}`)
          }
          load()
        } catch { /* ignore */ }
      })
    },
    onDisconnect: () => { wsOk.value = false },
  })
}

onMounted(async () => {
  await load()
  connectWs()
})

onUnmounted(() => {
  client?.deactivate()
})
</script>
