import { defineStore } from 'pinia'
import http from '../api/http'
import { MEAL_PERIOD_NAMES } from '../utils/dict'

export { MEAL_PERIOD_NAMES }

export interface CartLine {
  id?: number
  dishId: number
  name: string
  price: number
  quantity: number
  mealPeriod: string
  calorie?: number
  protein?: number
  fat?: number
  carb?: number
  nutritionVerified?: boolean
  stallId?: number
  stallName?: string
}

export interface NutritionTotal {
  calorie: number
  protein: number
  fat: number
  carb: number
}

/** 解析后端 nutrition_json，供热量汇总与「本单营养成分图谱」使用 */
export function parseNutrition(nutritionJson?: string): NutritionTotal {
  const empty: NutritionTotal = { calorie: 0, protein: 0, fat: 0, carb: 0 }
  if (!nutritionJson) return empty
  try {
    const n = JSON.parse(nutritionJson)
    return {
      calorie: Number(n?.calorie || 0),
      protein: Number(n?.protein || 0),
      fat: Number(n?.fat || 0),
      carb: Number(n?.carb || 0),
    }
  } catch {
    return empty
  }
}

/**
 * 购物车以服务端 t_cart_item 为准（多端/换设备一致），本地仅做视图缓存。
 */
export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [] as CartLine[],
    stallNames: {} as Record<number, string>,
    loading: false,
  }),
  getters: {
    totalAmount: (s) => s.items.reduce((sum, i) => sum + i.price * i.quantity, 0),
    totalCalorie: (s) => s.items.reduce((sum, i) => sum + (i.calorie || 0) * i.quantity, 0),
    totalQuantity: (s) => s.items.reduce((sum, i) => sum + i.quantity, 0),
    /** 本单营养合计，用于订单确认页成分图谱 */
    nutritionTotal: (s): NutritionTotal => ({
      calorie: s.items.reduce((sum, i) => sum + (i.calorie || 0) * i.quantity, 0),
      protein: s.items.reduce((sum, i) => sum + (i.protein || 0) * i.quantity, 0),
      fat: s.items.reduce((sum, i) => sum + (i.fat || 0) * i.quantity, 0),
      carb: s.items.reduce((sum, i) => sum + (i.carb || 0) * i.quantity, 0),
    }),
  },
  actions: {
    async loadStalls() {
      if (Object.keys(this.stallNames).length) return
      try {
        const res: any = await http.get('/dish/stalls')
        const map: Record<number, string> = {}
        for (const s of res.data || []) map[s.id] = s.name
        this.stallNames = map
      } catch {
        this.stallNames = {}
      }
    },
    async fetch() {
      this.loading = true
      try {
        await this.loadStalls()
        const res: any = await http.get('/cart')
        this.items = (res.data || []).map((row: any) => {
          const nut = parseNutrition(row.nutritionJson)
          return {
            id: row.id,
            dishId: row.dishId,
            name: row.name || `菜品#${row.dishId}`,
            price: Number(row.price || 0),
            quantity: row.quantity,
            mealPeriod: row.mealPeriod,
            calorie: nut.calorie,
            protein: nut.protein,
            fat: nut.fat,
            carb: nut.carb,
            stallId: row.stallId,
            stallName: this.stallNames[row.stallId] || `档口#${row.stallId}`,
          }
        })
      } finally {
        this.loading = false
      }
    },
    async add(dishId: number, quantity: number, mealPeriod?: string) {
      await http.post('/cart', { dishId, quantity, mealPeriod })
      await this.fetch()
    },
    async remove(id?: number) {
      if (id == null) return
      await http.delete(`/cart/${id}`)
      await this.fetch()
    },
    async clear() {
      await http.delete('/cart')
      this.items = []
    },
    /** 退出登录时清空本地视图缓存（服务端购物车保留） */
    reset() {
      this.items = []
    },
  },
})
