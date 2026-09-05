import axios from 'axios'

const request = axios.create({ baseURL: '/api' })

request.interceptors.request.use(config => {
  const token = localStorage.getItem('satoken')
  if (token) config.headers['satoken'] = token
  return config
})

request.interceptors.response.use(
  res => res.data,
  err => {
    if (err.response?.status === 401) {
      // 同时清理 userInfo，避免下次登录前残留旧用户信息
      localStorage.removeItem('satoken')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default request