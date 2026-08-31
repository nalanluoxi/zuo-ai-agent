import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [
    vue()
  ],
  server: {
    port: 5173,
    proxy: {
      // 所有 /api 请求代理到 Gateway (9000)
      '/api': { target: 'http://localhost:9000', changeOrigin: true }
    }
  }
})
