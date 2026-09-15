<template>
  <div class="page">
    <div class="hero-band">
      <h2 style="margin:0">个人中心</h2>
    </div>
    <div class="card-soft" v-if="profile" style="margin-bottom:16px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="学号">{{ profile.studentNo }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ profile.realName }}</el-descriptions-item>
        <el-descriptions-item label="手机">{{ profile.phoneMasked }}</el-descriptions-item>
        <el-descriptions-item label="院系">{{ profile.department }}</el-descriptions-item>
        <el-descriptions-item label="余额">¥{{ profile.balance }}</el-descriptions-item>
        <el-descriptions-item label="近7日消费">¥{{ profile.weekSpend }}</el-descriptions-item>
        <el-descriptions-item label="营养达标率">{{ profile.nutritionRate }}%</el-descriptions-item>
        <el-descriptions-item label="过敏原">{{ (profile.allergies || []).join('、') || '无' }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:12px; display:flex; gap:8px; flex-wrap:wrap">
        <el-input-number v-model="rechargeAmt" :min="1" :max="5000" />
        <el-button type="primary" @click="recharge">充值</el-button>
      </div>
    </div>

    <div class="card-soft" style="margin-bottom:16px">
      <h3>健康档案</h3>
      <el-form label-width="100px" style="max-width:560px">
        <el-form-item label="过敏原">
          <el-select v-model="health.allergies" multiple filterable allow-create style="width:100%">
            <el-option v-for="a in allergyOptions" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="饮食禁忌">
          <el-select v-model="health.dietTaboo" multiple filterable allow-create style="width:100%">
            <el-option v-for="a in tabooOptions" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标热量">
          <el-input-number v-model="health.targetCalorie" :min="800" :max="4000" />
        </el-form-item>
        <el-form-item label="目标蛋白(g)">
          <el-input-number v-model="health.targetProtein" :min="20" :max="200" />
        </el-form-item>
        <el-form-item label="宗教标签">
          <el-input v-model="health.religionTag" placeholder="如 HALAL" />
        </el-form-item>
        <el-button type="primary" @click="saveHealth">保存健康档案</el-button>
      </el-form>
    </div>

    <div class="card-soft">
      <h3>账户流水</h3>
      <el-table :data="ledgers">
        <el-table-column prop="createdAt" label="时间" min-width="160" />
        <el-table-column prop="bizType" label="类型" width="110" />
        <el-table-column prop="changeAmt" label="变动" width="100" />
        <el-table-column prop="balanceAfter" label="余额" width="100" />
        <el-table-column prop="remark" label="备注" min-width="140" />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const profile = ref<any>(null)
const ledgers = ref<any[]>([])
const rechargeAmt = ref(100)
const allergyOptions = ['花生', '海鲜', '乳制品', '麸质']
const tabooOptions = ['辣', '生冷', '油炸']
const health = reactive({
  allergies: [] as string[],
  dietTaboo: [] as string[],
  targetCalorie: 2000,
  targetProtein: 70,
  religionTag: '',
})

async function load() {
  const res: any = await http.get('/auth/profile')
  profile.value = res.data
  health.allergies = [...(res.data?.allergies || [])]
  health.dietTaboo = [...(res.data?.health?.dietTaboo || [])]
  health.targetCalorie = res.data?.health?.targetCalorie || 2000
  health.targetProtein = res.data?.health?.targetProtein || 70
  health.religionTag = res.data?.health?.religionTag || ''
  const lg: any = await http.get('/auth/ledger')
  ledgers.value = lg.data || []
}

async function saveHealth() {
  await http.put('/auth/health', health)
  ElMessage.success('健康档案已保存')
  await load()
}

async function recharge() {
  await http.post('/auth/recharge', { amount: rechargeAmt.value, channel: 'CAMPUS_CARD' })
  ElMessage.success('充值成功')
  await load()
}

onMounted(load)
</script>
