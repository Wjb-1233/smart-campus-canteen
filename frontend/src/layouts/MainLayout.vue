<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">智慧食堂</div>
      <div class="role-badge">{{ roleName }}</div>
      <el-menu :default-active="route.path" router background-color="#123f6b" text-color="#dcecff" active-text-color="#ffffff">
        <el-menu-item-group v-for="group in menus" :key="group.title">
          <template #title>
            <span class="group-title">{{ group.title }}</span>
          </template>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            {{ item.label }}
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div>
          <b>{{ user.realName }}</b>
          <span class="sub">{{ roleName }} · {{ user.studentNo }}</span>
        </div>
        <div class="header-right">
          <el-tag size="small" type="info">{{ roleName }}工作台</el-tag>
          <el-button type="primary" link @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ROLE_NAMES } from '../router'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

interface MenuItem { path: string; label: string }
interface MenuGroup { title: string; items: MenuItem[] }

const roleName = computed(() => ROLE_NAMES[user.role] || user.role)

/** 学生/老师端菜单：点餐、吃饭 */
const DINER_MENU: MenuGroup[] = [
  { title: '点餐', items: [
    { path: '/home', label: '今日推荐' },
    { path: '/dishes', label: '菜品搜索' },
    { path: '/cart', label: '购物车' },
    { path: '/orders', label: '我的订单' },
  ] },
  { title: '健康', items: [
    { path: '/nutrition', label: '我的营养' },
  ] },
  { title: '账户', items: [
    { path: '/profile', label: '个人中心' },
  ] },
]

/** 食堂端菜单：档口接单、出餐、核销、菜品维护 */
const STALL_MENU: MenuGroup[] = [
  { title: '档口出餐', items: [
    { path: '/stall', label: '档口接单' },
    { path: '/dish-manage', label: '菜品管理' },
  ] },
  { title: '账户', items: [
    { path: '/profile', label: '个人中心' },
  ] },
]

/** 管理员端菜单：运营监管、营养核验、操作审计 */
const ADMIN_MENU: MenuGroup[] = [
  { title: '运营监管', items: [
    { path: '/dashboard', label: '数据看板' },
    { path: '/stall', label: '档口接单' },
    { path: '/dish-manage', label: '菜品管理' },
  ] },
  { title: '营养与审计', items: [
    { path: '/nutrition-console', label: '营养工作台' },
    { path: '/audit', label: '操作审计' },
  ] },
  { title: '账户', items: [
    { path: '/profile', label: '个人中心' },
  ] },
]

const menus = computed<MenuGroup[]>(() => {
  switch (user.role) {
    case 'STALL':
      return STALL_MENU
    case 'ADMIN':
      return ADMIN_MENU
    default:
      return DINER_MENU
  }
})

function logout() {
  user.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { min-height: 100vh; }
.aside {
  background: linear-gradient(180deg, #123f6b, #1a5f9e);
  color: #fff;
}
.brand {
  font-size: 22px;
  font-weight: 700;
  padding: 24px 16px 4px;
  letter-spacing: 1px;
}
.role-badge {
  margin: 0 16px 12px;
  display: inline-block;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, .18);
  color: #dcecff;
}
.group-title {
  color: #8fb8dd;
  font-size: 12px;
  letter-spacing: 1px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255,255,255,0.85);
  border-bottom: 1px solid var(--campus-line);
}
.header .sub {
  margin-left: 10px;
  color: var(--campus-muted);
  font-size: 13px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
