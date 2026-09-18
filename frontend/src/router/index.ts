import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '../stores/user'

/** 角色 → 登录后默认首页 */
export const ROLE_HOME: Record<string, string> = {
  STUDENT: '/home',
  STALL: '/stall',
  ADMIN: '/dashboard',
}

/** 角色中文名（对外展示的端名） */
export const ROLE_NAMES: Record<string, string> = {
  STUDENT: '学生/老师',
  STALL: '食堂',
  ADMIN: '管理员',
}

/** 系统支持的角色集合，用于兜底识别脏数据/历史缓存 */
export const KNOWN_ROLES = Object.keys(ROLE_HOME)

export function homeForRole(role: string) {
  return ROLE_HOME[role] || '/home'
}

/** 学生/老师端：订餐、吃饭 */
const DINER_ROLES = ['STUDENT']
/** 食堂端：档口接单、出餐、核销、菜品维护（管理员端可代管） */
const STALL_ROLES = ['STALL', 'ADMIN']
/** 管理员端：运营看板、营养核验、操作审计 */
const ADMIN_ROLES = ['ADMIN']

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'home', component: () => import('../views/HomeView.vue'),
        meta: { roles: DINER_ROLES, title: '首页推荐' } },
      { path: 'dishes', name: 'dishes', component: () => import('../views/DishSearchView.vue'),
        meta: { roles: DINER_ROLES, title: '菜品搜索' } },
      { path: 'dish/:id', name: 'dish-detail', component: () => import('../views/DishDetailView.vue'),
        meta: { roles: DINER_ROLES, title: '菜品详情' } },
      { path: 'cart', name: 'cart', component: () => import('../views/CartView.vue'),
        meta: { roles: DINER_ROLES, title: '购物车' } },
      { path: 'orders', name: 'orders', component: () => import('../views/OrdersView.vue'),
        meta: { roles: DINER_ROLES, title: '我的订单' } },
      { path: 'nutrition', name: 'nutrition', component: () => import('../views/NutritionView.vue'),
        meta: { roles: DINER_ROLES, title: '我的营养' } },
      { path: 'nutrition-console', name: 'nutrition-console', component: () => import('../views/NutritionConsoleView.vue'),
        meta: { roles: ADMIN_ROLES, title: '营养工作台' } },
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue'),
        meta: { roles: ADMIN_ROLES, title: '数据看板' } },
      { path: 'stall', name: 'stall', component: () => import('../views/StallBoardView.vue'),
        meta: { roles: STALL_ROLES, title: '档口接单' } },
      { path: 'dish-manage', name: 'dish-manage', component: () => import('../views/DishManageView.vue'),
        meta: { roles: STALL_ROLES, title: '菜品管理' } },
      { path: 'audit', name: 'audit', component: () => import('../views/AuditLogView.vue'),
        meta: { roles: ADMIN_ROLES, title: '操作审计' } },
      { path: 'profile', name: 'profile', component: () => import('../views/ProfileView.vue'),
        meta: { title: '个人中心' } },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const user = useUserStore()
  document.title = to.meta.title ? `${to.meta.title} · 智慧校园食堂` : '智慧校园食堂'

  // 角色已失效（历史 5 角色数据 / 本地脏缓存）：清理登录态回登录页，避免重定向死循环
  if (user.isLogin && !KNOWN_ROLES.includes(user.role)) {
    user.logout()
    return { path: '/login' }
  }

  if (to.meta.public) {
    // 已登录用户访问登录页 → 回到自己的角色首页
    if (to.path === '/login' && user.isLogin) return homeForRole(user.role)
    return true
  }
  if (!user.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const roles = to.meta.roles as string[] | undefined
  if (roles && roles.length && !roles.includes(user.role)) {
    const home = homeForRole(user.role)
    // 兜底：目标就是该角色首页却仍不匹配时直接放行，杜绝无限重定向
    if (to.path === home) return true
    // 越权访问：静默回到该角色的默认首页，避免学生/老师端误入运营大屏
    return home
  }
  return true
})

export default router
