/** 全局字典：餐段、订单状态、标签颜色 */

export const MEAL_PERIODS = ['BREAKFAST', 'LUNCH', 'DINNER', 'NIGHT'] as const

export const MEAL_PERIOD_NAMES: Record<string, string> = {
  BREAKFAST: '早餐',
  LUNCH: '午餐',
  DINNER: '晚餐',
  NIGHT: '夜宵',
}

export const ORDER_STATUS_NAMES: Record<string, string> = {
  CREATED: '待支付',
  PAID: '待接单',
  PREPARING: '备餐中',
  READY: '待取餐',
  PICKED: '已取餐',
  ABNORMAL: '异常处理',
  CANCELLED: '已取消',
}

export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export function orderStatusTag(status: string): TagType {
  switch (status) {
    case 'CREATED':
      return 'info'
    case 'PAID':
      return 'warning'
    case 'PREPARING':
      return 'primary'
    case 'READY':
      return 'success'
    case 'PICKED':
      return 'info'
    case 'ABNORMAL':
      return 'danger'
    case 'CANCELLED':
      return 'info'
    default:
      return 'info'
  }
}

/** 订单进度（用于学生/老师端步骤条） */
export const ORDER_STEPS = ['PAID', 'PREPARING', 'READY', 'PICKED'] as const

export function statusStep(status: string): number {
  const idx = ORDER_STEPS.indexOf(status as any)
  return idx < 0 ? 0 : idx
}
