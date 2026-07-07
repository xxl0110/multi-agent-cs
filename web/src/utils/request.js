import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '',
  timeout: 60000
})

// 响应拦截器
request.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    const msg = error.response?.data?.message || error.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
