import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: process.env.SCOUT_BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
