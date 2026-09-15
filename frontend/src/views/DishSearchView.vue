<template>
  <div class="page">
    <div class="hero-band">
      <h2 style="margin:0 0 8px">菜品搜索</h2>
      <p style="margin:0;opacity:.9">支持标签组合筛选、关键词模糊匹配、热度排序</p>
    </div>

    <div class="card-soft" style="margin-bottom:16px">
      <el-input v-model="keyword" placeholder="搜索菜品名称" clearable style="max-width:280px;margin-right:12px" @keyup.enter="search" />
      <el-select v-model="mealPeriod" clearable placeholder="用餐时段" style="width:140px;margin-right:12px">
        <el-option label="早餐" value="BREAKFAST" />
        <el-option label="午餐" value="LUNCH" />
        <el-option label="晚餐" value="DINNER" />
        <el-option label="夜宵" value="NIGHT" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
      <div style="margin-top:12px">
        <el-checkbox-group v-model="selectedTags">
          <el-checkbox v-for="t in tags" :key="t.code" :label="t.code" border class="tag-chip">{{ t.name }}</el-checkbox>
        </el-checkbox-group>
      </div>
    </div>

    <el-table :data="rows" stripe @row-click="(row:any) => $router.push(`/dish/${row.id}`)">
      <el-table-column label="菜品" min-width="150">
        <template #default="{ row }">
          {{ row.name }}
          <el-tag v-if="row.nutritionVerified" size="small" type="success" effect="plain" style="margin-left:6px">营养已核验</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="stallName" label="档口" width="120" />
      <el-table-column prop="price" label="价格" width="90" />
      <el-table-column label="热量/蛋白" width="130">
        <template #default="{ row }">
          <span v-if="row.nutrition?.calorie != null">
            {{ row.nutrition.calorie }} kcal · {{ row.nutrition.protein ?? 0 }} g
          </span>
          <span v-else style="color:var(--campus-muted)">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="heatScore" label="热度" width="90" sortable />
      <el-table-column prop="stockHint" label="余量" width="110" />
      <el-table-column label="标签" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="t in row.tags" :key="t" size="small" class="tag-chip">{{ t }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click.stop="quickAdd(row)">加入购物车</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { useCartStore } from '../stores/cart'

const cart = useCartStore()
const keyword = ref('')
const mealPeriod = ref('LUNCH')
const tags = ref<any[]>([])
const selectedTags = ref<string[]>([])
const rows = ref<any[]>([])

async function loadTags() {
  const res: any = await http.get('/dish/tags')
  tags.value = res.data || []
}

async function search() {
  const res: any = await http.get('/dish/search', {
    params: {
      keyword: keyword.value || undefined,
      mealPeriod: mealPeriod.value || undefined,
      tags: selectedTags.value.join(',') || undefined,
    },
  })
  rows.value = res.data || []
}

/** 搜索结果直接加购，减少「详情页->加购」的跳转层级 */
async function quickAdd(row: any) {
  try {
    await cart.add(row.id, 1, mealPeriod.value || row.mealPeriod || 'LUNCH')
    ElMessage.success(`已加入购物车：${row.name}`)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加入购物车失败')
  }
}

watch(selectedTags, () => search())
onMounted(async () => {
  await loadTags()
  await search()
})
</script>
