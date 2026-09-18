<template>
  <div class="page">
    <div class="hero-band" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
      <div>
        <h2 style="margin:0 0 8px">营养工作台（管理员端）</h2>
        <p style="margin:0;opacity:.92">
          院系达标率预警 · 班级营养排行 · 档口均衡评分{{ canSeeIntervention ? ' · 营养干预建议' : '' }}{{ isAdmin ? ' · 菜品营养数据核验' : '' }}
        </p>
      </div>
      <div>
        <el-tag type="info" style="margin-right:8px">数据日期 {{ generatedAt || '-' }}</el-tag>
        <el-button size="small" :loading="loading" @click="loadAll">刷新全部</el-button>
      </div>
    </div>

    <!-- 干预建议：仅管理员端可见（与后端权限一致） -->
    <div v-if="canSeeIntervention" class="card-soft" style="margin-bottom:16px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <h3 style="margin:0">营养干预建议</h3>
        <el-tag v-if="highCount" type="danger">高危 {{ highCount }} 条</el-tag>
      </div>
      <div v-if="suggestions.length" style="margin-top:12px">
        <el-alert v-for="(s, i) in suggestions" :key="i" :type="levelType(s.level)" show-icon :closable="false"
          style="margin-bottom:8px"
          :title="`【${s.scope}·${s.target}】${s.content}`" />
      </div>
      <el-empty v-else description="暂无干预建议" />
    </div>

    <!-- 菜品营养数据核验：管理员端职责，修正 -> 核验 -> 学生/老师端展示可信标识 -->
    <div v-if="isAdmin" class="card-soft" style="margin-bottom:16px">
      <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
        <h3 style="margin:0">菜品营养数据核验</h3>
        <div>
          <el-tag type="success" style="margin-right:6px">已核验 {{ auditVerified }}</el-tag>
          <el-tag type="warning" style="margin-right:6px">待核验 {{ auditPending }}</el-tag>
          <el-tag type="danger">数据异常 {{ auditAbnormal }}</el-tag>
        </div>
      </div>
      <p style="color:var(--campus-muted);font-size:13px;margin:8px 0 0">
        营养数据是学生/老师端热量统计、蛋白质达标率与营养周报的计算依据，核验通过后学生/老师端会展示「已核验」标识。
      </p>
      <el-table :data="auditDishes" empty-text="暂无菜品" max-height="400" style="margin-top:12px">
        <el-table-column prop="name" label="菜品" min-width="130" />
        <el-table-column label="热量(kcal)" width="110">
          <template #default="{ row }">{{ row.nutrition?.calorie ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="蛋白(g)" width="90">
          <template #default="{ row }">{{ row.nutrition?.protein ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="脂肪/碳水(g)" width="130">
          <template #default="{ row }">{{ row.nutrition?.fat ?? '-' }} / {{ row.nutrition?.carb ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="核验状态" width="110">
          <template #default="{ row }">
            <el-tag :type="auditTag(row.auditStatus)" size="small">{{ auditLabel(row.auditStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="异常说明" min-width="160">
          <template #default="{ row }">
            <span v-if="row.issues?.length" style="color:#e04b4b">{{ row.issues.join('；') }}</span>
            <span v-else style="color:var(--campus-muted)">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFix(row)">修正</el-button>
            <el-button v-if="!row.verified" link type="success" :disabled="row.issues?.length > 0"
              @click="verify(row, true)">核验</el-button>
            <el-button v-else link type="warning" @click="verify(row, false)">撤销核验</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-row :gutter="16">
      <el-col :md="14" :xs="24">
        <div class="card-soft">
          <h3 style="margin-top:0">院系蛋白质达标率</h3>
          <div ref="deptChartRef" style="height:300px"></div>
        </div>
      </el-col>
      <el-col :md="10" :xs="24">
        <div class="card-soft" style="min-height:300px">
          <h3 style="margin-top:0">红色预警（达标率 &lt; 80%）</h3>
          <el-alert v-for="a in alerts" :key="a.department" :title="a.alert" type="error" show-icon
            style="margin-top:10px" :closable="false" />
          <el-empty v-if="!alerts.length" description="今日无红色预警" />
        </div>
      </el-col>

      <el-col :md="12" :xs="24" style="margin-top:16px">
        <div class="card-soft">
          <h3 style="margin-top:0">班级营养 TOP</h3>
          <el-table :data="classTop" empty-text="暂无数据" max-height="320">
            <el-table-column prop="rankNo" label="名次" width="70" />
            <el-table-column prop="className" label="班级" width="110" />
            <el-table-column prop="realName" label="姓名" />
            <el-table-column prop="proteinRate" label="达标率%" width="100" sortable />
          </el-table>
        </div>
      </el-col>

      <el-col :md="12" :xs="24" style="margin-top:16px">
        <div class="card-soft">
          <h3 style="margin-top:0">档口营养均衡评分</h3>
          <el-table :data="stallScores" empty-text="暂无数据" max-height="320">
            <el-table-column prop="stallName" label="档口" />
            <el-table-column prop="avgProtein" label="均蛋白(g)" width="100" />
            <el-table-column prop="avgCalorie" label="均热量" width="100" />
            <el-table-column prop="balanceScore" label="均衡分" width="110" sortable>
              <template #default="{ row }">
                <el-tag :type="scoreTag(row.balanceScore)" size="small">{{ row.balanceScore }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="建议" width="90">
              <template #default="{ row }">
                <el-button link type="primary" :disabled="!canJumpDashboard" @click="jumpStall(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <!-- 营养数据修正弹窗 -->
    <el-dialog v-model="fixVisible" title="修正菜品营养数据" width="420px">
      <el-form label-width="90px">
        <el-form-item label="菜品"><b>{{ fixForm.name }}</b></el-form-item>
        <el-form-item label="热量(kcal)"><el-input-number v-model="fixForm.calorie" :min="0" :max="5000" /></el-form-item>
        <el-form-item label="蛋白质(g)"><el-input-number v-model="fixForm.protein" :min="0" :max="500" /></el-form-item>
        <el-form-item label="脂肪(g)"><el-input-number v-model="fixForm.fat" :min="0" :max="500" /></el-form-item>
        <el-form-item label="碳水(g)"><el-input-number v-model="fixForm.carb" :min="0" :max="1000" /></el-form-item>
        <el-form-item label="修正说明"><el-input v-model="fixForm.remark" placeholder="如：按最新食材营养成分表校正" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fixVisible = false">取消</el-button>
        <el-button type="primary" :loading="fixing" @click="submitFix">保存并转待核验</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import http from '../api/http'
import { useUserStore } from '../stores/user'

const router = useRouter()
const user = useUserStore()
const role = user.role

/** 与后端 @PreAuthorize 对齐的权限判断：营养核验与干预建议归管理员端 */
const isAdmin = role === 'ADMIN'
const canSeeIntervention = role === 'ADMIN'
const canJumpDashboard = role === 'ADMIN'

const loading = ref(false)
const generatedAt = ref('')
const alerts = ref<any[]>([])
const classTop = ref<any[]>([])
const stallScores = ref<any[]>([])
const suggestions = ref<any[]>([])
const highCount = ref(0)
const deptChartRef = ref<HTMLDivElement>()
let deptChart: echarts.ECharts | null = null

const auditDishes = ref<any[]>([])
const auditVerified = ref(0)
const auditPending = ref(0)
const auditAbnormal = ref(0)
const fixVisible = ref(false)
const fixing = ref(false)
const fixForm = reactive({ dishId: 0, name: '', calorie: 0, protein: 0, fat: 0, carb: 0, remark: '' })

function levelType(level: string): 'success' | 'warning' | 'error' | 'info' {
  if (level === 'HIGH') return 'error'
  if (level === 'MID') return 'warning'
  if (level === 'LOW') return 'success'
  return 'info'
}

function scoreTag(score: number) {
  const v = Number(score)
  if (v < 60) return 'danger'
  if (v < 75) return 'warning'
  return 'success'
}

function auditTag(status: string): 'success' | 'warning' | 'danger' {
  if (status === 'VERIFIED') return 'success'
  if (status === 'ABNORMAL') return 'danger'
  return 'warning'
}

function auditLabel(status: string) {
  if (status === 'VERIFIED') return '已核验'
  if (status === 'ABNORMAL') return '数据异常'
  return '待核验'
}

function jumpStall(row: any) {
  if (!canJumpDashboard) {
    ElMessage.info('经营数据看板仅对管理员端开放')
    return
  }
  router.push({ path: '/dashboard', query: { stallId: row.stallId } })
}

async function loadDept() {
  const res: any = await http.get('/nutrition/report/dept-daily')
  const depts = res.data?.departments || []
  alerts.value = res.data?.alerts || []
  generatedAt.value = res.data?.generatedAt || ''
  await nextTick()
  renderDept(depts)
}

function renderDept(depts: any[]) {
  if (!deptChartRef.value) return
  deptChart = deptChart || echarts.init(deptChartRef.value)
  const names = depts.map((d) => d.department)
  const rates = depts.map((d) => Number(d.avgProteinRate || 0))
  deptChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 90, right: 40, top: 20, bottom: 24 },
    xAxis: { type: 'value', max: 100, name: '%' },
    yAxis: { type: 'category', data: names },
    series: [
      {
        type: 'bar',
        data: rates.map((r) => ({
          value: r,
          itemStyle: { color: r < 80 ? '#e04b4b' : '#37b26c', borderRadius: [0, 6, 6, 0] },
        })),
        label: { show: true, position: 'right', formatter: '{c}%' },
      },
      {
        type: 'line',
        markLine: {
          symbol: 'none',
          data: [{ xAxis: 80, label: { formatter: '阈值 80%' }, lineStyle: { color: '#e6a23c' } }],
        },
        data: [],
      },
    ],
  })
}

async function loadClassTop() {
  const res: any = await http.get('/nutrition/report/class-top')
  classTop.value = res.data?.topList || []
}

async function loadStallScore() {
  const res: any = await http.get('/nutrition/report/stall-score')
  stallScores.value = res.data?.stalls || []
}

async function loadIntervention() {
  const res: any = await http.get('/nutrition/report/intervention')
  suggestions.value = res.data?.suggestions || []
  highCount.value = res.data?.highCount || 0
}

async function loadDishAudit() {
  const res: any = await http.get('/nutrition/dish-audit')
  auditDishes.value = res.data?.dishes || []
  auditVerified.value = res.data?.verifiedCount || 0
  auditPending.value = res.data?.pendingCount || 0
  auditAbnormal.value = res.data?.abnormalCount || 0
}

function openFix(row: any) {
  fixForm.dishId = row.dishId
  fixForm.name = row.name
  fixForm.calorie = Number(row.nutrition?.calorie ?? 0)
  fixForm.protein = Number(row.nutrition?.protein ?? 0)
  fixForm.fat = Number(row.nutrition?.fat ?? 0)
  fixForm.carb = Number(row.nutrition?.carb ?? 0)
  fixForm.remark = ''
  fixVisible.value = true
}

async function submitFix() {
  fixing.value = true
  try {
    await http.post(`/nutrition/dish-audit/${fixForm.dishId}/nutrition`, {
      calorie: fixForm.calorie,
      protein: fixForm.protein,
      fat: fixForm.fat,
      carb: fixForm.carb,
      remark: fixForm.remark,
    })
    ElMessage.success('营养数据已修正，状态已转为待核验')
    fixVisible.value = false
    await loadDishAudit()
  } finally {
    fixing.value = false
  }
}

async function verify(row: any, verified: boolean) {
  await http.post(`/nutrition/dish-audit/${row.dishId}/${verified ? 'verify' : 'unverify'}`)
  ElMessage.success(verified ? '核验通过' : '已撤销核验')
  await loadDishAudit()
}

async function loadAll() {
  loading.value = true
  try {
    const tasks: Promise<any>[] = [loadDept(), loadClassTop(), loadStallScore()]
    if (canSeeIntervention) tasks.push(loadIntervention())
    if (isAdmin) tasks.push(loadDishAudit())
    await Promise.all(tasks)
  } finally {
    loading.value = false
  }
}

function resize() {
  deptChart?.resize()
}

onMounted(async () => {
  await loadAll()
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  deptChart?.dispose()
})
</script>
