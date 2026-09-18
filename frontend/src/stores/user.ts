import { defineStore } from 'pinia'
import http from '../api/http'
import { useCartStore } from './cart'

interface LoginResult {
  token: string
  role: string
  realName: string
  studentNo: string
  userId: number
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: '' as string,
    role: '' as string,
    realName: '' as string,
    studentNo: '' as string,
    userId: 0 as number,
  }),
  getters: {
    isLogin: (s) => !!s.token,
    /** 学生/老师端：可点餐、下单、查看个人营养 */
    isDiner: (s) => s.role === 'STUDENT',
    /** 食堂端：可接单、出餐、核销、维护菜品 */
    isStallStaff: (s) => s.role === 'STALL',
    /** 管理员端：运营看板、营养核验、操作审计 */
    isAdmin: (s) => s.role === 'ADMIN',
  },
  actions: {
    async login(studentNo: string, password: string) {
      const res: any = await http.post('/auth/login', { studentNo, password })
      const data = res.data as LoginResult
      this.applyLogin(data)
    },
    /** OAuth 等外部登录入口复用 */
    applyLogin(data: LoginResult) {
      this.token = data.token
      this.role = data.role
      this.realName = data.realName
      this.studentNo = data.studentNo
      this.userId = data.userId
      // 切换账号时清空上一个账号的本地购物车视图
      useCartStore().reset()
    },
    logout() {
      this.token = ''
      this.role = ''
      this.realName = ''
      this.studentNo = ''
      this.userId = 0
      useCartStore().reset()
    },
  },
  persist: true,
})
