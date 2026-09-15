<template>
  <div class="page">
    <div class="hero-band" style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px">
      <div>
        <h2 style="margin:0 0 8px">菜品管理</h2>
        <p style="margin:0;opacity:.9">菜品CRUD · 多维标签 · 图片上传 · 实时库存</p>
      </div>
      <el-button type="primary" @click="openEdit()">+ 新增菜品</el-button>
    </div>

    <div class="card-soft" style="margin-bottom:12px; display:flex; gap:8px; align-items:center; flex-wrap:wrap">
      <el-input v-model="keyword" placeholder="搜索菜品" clearable style="width:220px" @keyup.enter="load" />
      <el-select v-model="filterStatus" placeholder="状态" clearable style="width:120px">
        <el-option label="上架" value="1" />
        <el-option label="下架" value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="exportExcel">导出Excel</el-button>
      <el-button @click="exportPdf">导出PDF</el-button>
      <el-tag type="danger" v-if="alerts.length">缺货预警 {{ alerts.length }} 项</el-tag>
    </div>

    <el-table :data="filteredRows" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="菜品" min-width="140" />
      <el-table-column prop="stallName" label="档口" width="110" />
      <el-table-column prop="price" label="价格" width="80" />
      <el-table-column prop="stock" label="库存" width="80">
        <template #default="{ row }">
          <el-tag :type="row.stock <= 0 ? 'danger' : row.stock <= 10 ? 'warning' : 'success'" size="small">
            {{ row.stock }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="mealPeriod" label="时段" width="90" />
      <el-table-column prop="heatScore" label="热度" width="80" sortable />
      <el-table-column label="标签" min-width="180">
        <template #default="{ row }">
          <el-tag v-for="t in row.tags" :key="t" size="small" class="tag-chip">{{ t }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑菜品' : '新增菜品'" width="620px" destroy-on-close>
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="菜品名称" required>
              <el-input v-model="form.name" placeholder="如：红烧牛肉面" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="档口" required>
              <el-select v-model="form.stallId" style="width:100%" placeholder="选择档口">
                <el-option v-for="s in stalls" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格" required>
              <el-input-number v-model="form.price" :min="0" :max="9999" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存" required>
              <el-input-number v-model="form.stock" :min="0" :max="99999" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用餐时段">
              <el-select v-model="form.mealPeriod" style="width:100%">
                <el-option label="早餐" value="BREAKFAST" />
                <el-option label="午餐" value="LUNCH" />
                <el-option label="晚餐" value="DINNER" />
                <el-option label="夜宵" value="NIGHT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="热度">
              <el-input-number v-model="form.heatScore" :min="0" :max="999" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="标签">
          <el-checkbox-group v-model="form.tagCodes">
            <el-checkbox v-for="t in tags" :key="t.code" :value="t.code" border class="tag-chip">{{ t.name }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="营养成分">
          <el-row :gutter="12">
            <el-col :span="6">
              <el-input-number v-model="form.nutrition.calorie" :min="0" placeholder="热量" style="width:100%" />
            </el-col>
            <el-col :span="6">
              <el-input-number v-model="form.nutrition.protein" :min="0" :precision="1" placeholder="蛋白g" style="width:100%" />
            </el-col>
            <el-col :span="6">
              <el-input-number v-model="form.nutrition.fat" :min="0" :precision="1" placeholder="脂肪g" style="width:100%" />
            </el-col>
            <el-col :span="6">
              <el-input-number v-model="form.nutrition.carb" :min="0" :precision="1" placeholder="碳水g" style="width:100%" />
            </el-col>
          </el-row>
        </el-form-item>

        <el-form-item label="菜品图片">
          <el-upload :show-file-list="false" accept="image/*" :before-upload="uploadImage">
            <el-button size="small">上传图片(Base64)</el-button>
          </el-upload>
          <span v-if="form.imageUrl" style="margin-left:12px;color:#1a5f9e">{{ form.imageUrl }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Client } from '@stomp/stompjs'
import http from '../api/http'
import { openStomp } from '../utils/ws'

const rows = ref<any[]>([])
const tags = ref<any[]>([])
const stalls = ref<any[]>([])
const alerts = ref<any[]>([])
const keyword = ref('')
const filterStatus = ref<string>('')
const dialogVisible = ref(false)
const saving = ref(false)
let client: Client | null = null

const form = reactive<any>({
  id: null,
  name: '',
  stallId: undefined,
  price: 0,
  stock: 0,
  mealPeriod: 'LUNCH',
  heatScore: 50,
  status: 1,
  tagCodes: [] as string[],
  nutrition: { calorie: 0, protein: 0, fat: 0, carb: 0 },
  imageUrl: '',
})

const filteredRows = computed(() => {
  let list = rows.value
  if (keyword.value) {
    const k = keyword.value.toLowerCase()
    list = list.filter((r) => (r.name || '').toLowerCase().includes(k))
  }
  if (filterStatus.value !== '') {
    list = list.filter((r) => r.status === Number(filterStatus.value))
  }
  return list
})

async function load() {
  const res: any = await http.get('/dish/admin/list')
  rows.value = res.data || []
  try {
    const alertRes: any = await http.get('/dish/stock/alerts', { params: { threshold: 10 } })
    alerts.value = alertRes.data || []
  } catch { alerts.value = [] }
}

async function loadMeta() {
  const [tagRes, stallRes]: any[] = await Promise.all([
    http.get('/dish/tags'),
    http.get('/dish/stalls'),
  ])
  tags.value = tagRes.data || []
  stalls.value = stallRes.data || []
}

function openEdit(row?: any) {
  form.id = row?.id ?? null
  form.name = row?.name ?? ''
  form.stallId = row?.stallId ?? stalls.value[0]?.id
  form.price = row?.price ?? 0
  form.stock = row?.stock ?? 0
  form.mealPeriod = row?.mealPeriod ?? 'LUNCH'
  form.heatScore = row?.heatScore ?? 50
  form.status = row?.status ?? 1
  form.tagCodes = row ? [...(row.tagCodes || [])] : []
  const n = row?.nutrition || {}
  form.nutrition = {
    calorie: n.calorie || 0,
    protein: n.protein || 0,
    fat: n.fat || 0,
    carb: n.carb || 0,
  }
  form.imageUrl = row?.imageUrl ?? ''
  dialogVisible.value = true
}

async function uploadImage(file: File) {
  const reader = new FileReader()
  reader.onload = async () => {
    try {
      const res: any = await http.post('/files/upload-base64', {
        base64: String(reader.result),
        name: `dish_${Date.now()}`,
      })
      form.imageUrl = res.data?.url || ''
      ElMessage.success('图片上传成功')
    } catch {
      ElMessage.error('图片上传失败')
    }
  }
  reader.readAsDataURL(file)
  return false
}

async function save() {
  if (!form.name || !form.stallId) {
    ElMessage.warning('请填写名称并选择档口')
    return
  }
  saving.value = true
  try {
    const payload = {
      id: form.id || undefined,
      name: form.name,
      stallId: form.stallId,
      price: form.price,
      stock: form.stock,
      mealPeriod: form.mealPeriod,
      heatScore: form.heatScore,
      status: form.status,
      imageUrl: form.imageUrl || undefined,
      nutritionJson: JSON.stringify(form.nutrition),
      tagCodes: form.tagCodes,
    }
    await http.post('/dish', payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除「${row.name}」？将下架该菜品。`, '删除确认', { type: 'warning' })
  await http.delete(`/dish/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

async function exportExcel() {
  const res: any = await http.get('/order/statistics/export', { responseType: 'blob' })
  const blob = new Blob([res.data])
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'daily-report.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}

async function exportPdf() {
  const res: any = await http.get('/order/statistics/export-pdf', { responseType: 'blob' })
  const blob = new Blob([res.data])
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'daily-report.pdf'
  a.click()
  URL.revokeObjectURL(url)
}

/** 订阅后端缺货预警广播，库存低于阈值时实时提醒并高亮 */
function connectWs() {
  client = openStomp({
    onConnect: (c) => {
      c.subscribe('/topic/stock-alert', (msg) => {
        try {
          const body = JSON.parse(msg.body)
          if (!alerts.value.some((a) => a.dishId === body.dishId && a.stock === body.stock)) {
            alerts.value = [body, ...alerts.value]
          }
          ElMessage.warning(body.message)
        } catch { /* ignore */ }
      })
    },
  })
}

onMounted(async () => {
  await loadMeta()
  await load()
  connectWs()
})

onUnmounted(() => {
  client?.deactivate()
})
</script>
