<template>
  <div class="page">
    <div class="hero-band" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
      <div>
        <h2 style="margin:0 0 8px">操作审计</h2>
        <p style="margin:0;opacity:.92">关键写操作留痕：下单 / 支付 / 出餐 / 核销 / 菜品变更</p>
      </div>
      <div>
        <el-input v-model="keyword" placeholder="按操作人/动作过滤" style="width:200px;margin-right:8px" clearable />
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-table :data="filtered" v-loading="loading" class="card-soft" empty-text="暂无审计记录">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="createdAt" label="时间" min-width="170" />
      <el-table-column label="操作人" width="150">
        <template #default="{ row }">
          {{ row.realName || '未知' }}<span style="color:var(--campus-muted)"> #{{ row.userId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ roleName(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="action" label="动作" width="170" />
      <el-table-column prop="detail" label="详情" min-width="240" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="140" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import http from '../api/http'
import { ROLE_NAMES } from '../router'

const rows = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')

const filtered = computed(() => {
  if (!keyword.value) return rows.value
  const k = keyword.value.toLowerCase()
  return rows.value.filter((r) =>
    String(r.realName || '').toLowerCase().includes(k) || String(r.action || '').toLowerCase().includes(k))
})

function roleName(role: string) {
  return ROLE_NAMES[role] || role || '-'
}

async function load() {
  loading.value = true
  try {
    const res: any = await http.get('/admin/audit/logs', { params: { limit: 200 } })
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
