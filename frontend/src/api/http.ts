import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import router from '../router'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const user = useUserStore()
  if (user.token) {
    config.headers.Authorization = `Bearer ${user.token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    if (res.config.responseType === 'blob') {
      return res
    }
    const body = res.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(body)
    }
    return body
  },
  (err) => {
    if (err.response?.status === 401) {
      const user = useUserStore()
      user.logout()
      router.push('/login')
    }
    ElMessage.error(err.response?.data?.message || err.message || '网络错误')
    return Promise.reject(err)
  },
)

export default http
