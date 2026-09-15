<template>
  <div class="page">
    <div class="hero-band" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
      <div>
        <h2 style="margin:0 0 8px">档口接单看板</h2>
        <p style="margin:0;opacity:.92">学生/老师端下单实时推送到本看板 · 接单/出餐/核销状态机校验 · 缺货预警</p>
      </div>
      <el-tag :type="wsOk ? 'success' : 'info'">{{ wsOk ? 'WS 实时已连接' : 'WS 未连接（轮询中）' }}</el-tag>
    </div>

    <div class="card-soft" style="margin-bottom:12px; display:flex; flex-wrap:wrap; gap:8px; align-items:center">
      <span style="color:var(--campus-muted)">当前档口：</span>
      <el-select v-model="stallId" style="width:230px" @change="onStallChange">
        <el-option v-for="s in stallOptions" :key="s.id" :value="s.id"
          :label="`${s.name}（待处理 ${s.pendingCount}）`" />
      </el-select>
      <el-button type="primary" @click="load">刷新</el-button>
      <el-button @click="exportExcel">导出Excel</el-button>
      <el-button @click="exportPdf">导出PDF</el-button>
      <el-input v-model="scanCode" placeholder="输入取餐码核销" style="width:170px" @keyup.enter="scan" />
      <el-button type="success" @click="scan">扫码核销</el-button>
      <el-tag v-if="pendingCount" type="warning">待处理 {{ pendingCount }}</el-tag>
    </div>

    <div class="card-soft" style="margin-bottom:12px" v-if="stockAlerts.length">
      <h4 style="margin:0 0 8px">实时缺货预警</h4>
      <el-tag v-for="a in stockAlerts" :key="a.dishId + '-' + a.stock" :type="a.level === 'CRITICAL' ? 'danger' : 'warning'"
        class="tag-chip" style="margin-right:8px">
        {{ a.message }}
      </el-tag>
      <el-button link type="primary" @click="goDishManage">去补货</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" empty-text="当前档口暂无待处理订单">
      <el-table-column prop="orderNo" label="订单号" min-width="150" />
      <el-table-column prop="pickupCode" label="取餐码" width="90" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="orderStatusTag(row.status)" size="small">{{ statusName(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="mealPeriod" label="时段" width="80" />
      <el-table-column label="金额" width="90">
        <template #default="{ row }">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="催单" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.urgeCount" type="danger" size="small">{{ row.urgeCount }} 次</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="取餐倒计时" width="120">
        <template #default="{ row }">
          <el-tag :type="remainOf(row) < 300 ? 'danger' : 'warning'">{{ formatRemain(remainOf(row)) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="expectPickupAt" label="预计取餐" min-width="160" />
      <el-table-column label="操作" width="400" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="showDetail(row)">明细</el-button>
          <el-button size="small" :disabled="!can(row, 'PREPARING')" @click="setStatus(row, 'PREPARING')">接单/备餐</el-button>
          <el-button size="small" type="success" :disabled="!can(row, 'READY')" @click="setStatus(row, 'READY')">出餐</el-button>
          <el-button size="small" type="primary" :disabled="!can(row, 'PICKED')" @click="setStatus(row, 'PICKED')">核销</el-button>
          <el-button size="small" type="danger" :disabled="!can(row, 'ABNORMAL')" @click="markAbnormal(row)">异常</el-button>
          <el-button size="small" type="warning" @click="cancelOrder(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" :title="`订单明细 · ${detail?.orderNo || ''}`" width="560px">
      <el-descriptions :column="2" border size="small" v-if="detail">
        <el-descriptions-item label="取餐码">{{ detail.pickupCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusName(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="餐段">{{ detail.mealPeriod }}</el-descriptions-item>
        <el-descriptions-item label="支付渠道">{{ detail.payChannel }}</el-descriptions-item>
        <el-descriptions-item label="预计取餐">{{ detail.expectPickupAt }}</el-descriptions-item>
        <el-descriptions-item label="催单次数">{{ detail.urgeCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '无' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail?.items || []" size="small" style="margin-top:12px">
        <el-table-column prop="dishName" label="菜品" min-width="140" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column label="单价" width="90">
          <template #default="{ row }">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="替换" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.replacedFrom" type="warning" size="small">替换自 #{{ row.replacedFrom }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Client } from '@stomp/stompjs'
import http from '../api/http'
import { ORDER_STATUS_NAMES, orderStatusTag } from '../utils/dict'
import { openStomp } from '../utils/ws'

const router = useRouter()
const stallId = ref<number>(1)
const stallOptions = ref<any[]>([])
const rows = ref<any[]>([])
const scanCode = ref('')
const wsOk = ref(false)
const loading = ref(false)
const nowTick = ref(Date.now())
const stockAlerts = ref<any[]>([])
const detailVisible = ref(false)
const detail = ref<any>(null)

let timer: number | undefined
let pollTimer: number | undefined
let client: Client | null = null

const pendingCount = computed(() => rows.value.length)

function statusName(s: string) {
  return ORDER_STATUS_NAMES[s] || s
}

function remainOf(row: any) {
  if (row.expectPickupAt) {
    return Math.max(0, Math.floor((new Date(row.expectPickupAt).getTime() - nowTick.value) / 1000))
  }
  return row.remainSeconds ?? 0
}

function formatRemain(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

/** 与后端状态机保持一致的按钮可用性判断 */
function can(row: any, target: string) {
  const s = row.status
  if (target === 'PREPARING') return ['PAID', 'ABNORMAL'].includes(s)
  if (target === 'READY') return ['PAID', 'PREPARING', 'ABNORMAL'].includes(s)
  if (target === 'PICKED') return ['READY', 'ABNORMAL'].includes(s)
  if (target === 'ABNORMAL') return ['PAID', 'PREPARING', 'READY'].includes(s)
  return false
}

async function loadOptions() {
  try {
    const res: any = await http.get('/order/stall/options')
    stallOptions.value = res.data || []
    if (stallOptions.value.length && !stallOptions.value.some((s: any) => s.id === stallId.value)) {
      stallId.value = stallOptions.value[0].id
    }
  } catch {
    stallOptions.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const res: any = await http.get(`/order/stall/${stallId.value}/pending`)
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function setStatus(row: any, status: string, abnormalReason?: string) {
  try {
    await http.post(`/order/${row.id}/status`, null, { params: { status, abnormalReason } })
    ElMessage.success(`订单 ${row.orderNo} 已更新为「${statusName(status)}」`)
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '状态更新失败')
  }
}

async function markAbnormal(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入异常原因（如：漏装小菜 / 食材售罄）', '标记异常', {
      confirmButtonText: '提交', cancelButtonText: '取消', inputValue: '食材售罄，无法出餐',
    })
    await setStatus(row, 'ABNORMAL', value || '档口标记异常')
  } catch {
    /* 用户取消 */
  }
}

async function cancelOrder(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('取消订单将回滚库存，已支付订单将自动退款给用户', '取消订单', {
      confirmButtonText: '确认取消', cancelButtonText: '返回', inputValue: '档口无法出餐',
    })
    const res: any = await http.post(`/order/${row.id}/cancel`, null, { params: { reason: value } })
    ElMessage.success(`已取消，退款 ¥${res.data?.refund ?? 0}`)
    await load()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function scan() {
  if (!scanCode.value) return
  try {
    const res: any = await http.post('/order/scan', null, { params: { pickupCode: scanCode.value } })
    ElMessage.success(`核销成功：订单 ${res.data?.orderNo || scanCode.value}`)
    scanCode.value = ''
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '核销失败')
  }
}

async function showDetail(row: any) {
  const res: any = await http.get(`/order/${row.id}/detail`)
  detail.value = res.data
  detailVisible.value = true
}

function goDishManage() {
  router.push('/dish-manage')
}

async function exportExcel() {
  const res: any = await http.get('/order/statistics/export', { params: { stallId: stallId.value }, responseType: 'blob' })
  downloadBlob(res.data, 'daily-report.xlsx')
}

async function exportPdf() {
  const res: any = await http.get('/order/statistics/export-pdf', { params: { stallId: stallId.value }, responseType: 'blob' })
  downloadBlob(res.data, 'daily-report.pdf')
}

function downloadBlob(data: BlobPart, name: string) {
  const blob = new Blob([data])
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

function connectWs() {
  client?.deactivate()
  const currentStall = stallId.value
  client = openStomp({
    onConnect: (c) => {
      wsOk.value = true
      c.subscribe(`/topic/stall/${currentStall}`, (msg) => {
        try {
          const body = JSON.parse(msg.body)
          if (body.type === 'NEW') ElMessage.info(`新订单 ${body.orderNo} · 取餐码 ${body.pickupCode}`)
          if (body.type === 'URGE') ElMessage.warning(`催单：${body.orderNo}`)
          load()
        } catch { /* ignore */ }
      })
      c.subscribe('/topic/stock-alert', (msg) => {
        try {
          const body = JSON.parse(msg.body)
          if (!stockAlerts.value.some((a) => a.dishId === body.dishId && a.stock === body.stock)) {
            stockAlerts.value = [body, ...stockAlerts.value].slice(0, 8)
          }
          ElMessage.warning(body.message)
        } catch { /* ignore */ }
      })
    },
    onDisconnect: () => { wsOk.value = false },
  })
}

function onStallChange() {
  load()
  connectWs()
}

onMounted(async () => {
  await loadOptions()
  await load()
  connectWs()
  timer = window.setInterval(() => { nowTick.value = Date.now() }, 1000)
  pollTimer = window.setInterval(load, 15000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (pollTimer) clearInterval(pollTimer)
  client?.deactivate()
})
</script>
