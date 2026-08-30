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
      localStorage.removeItem('satoken')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default request