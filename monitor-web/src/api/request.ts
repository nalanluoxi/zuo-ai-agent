import axios from 'axios'

const request = axios.create({ baseURL: '/api/log' })

request.interceptors.response.use(
  res => res.data,
  err => {
    return Promise.reject(err)
  }
)

export default request
