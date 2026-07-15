import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          element: ['element-plus', '@element-plus/icons-vue'],
          charts: ['echarts']
        }
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/v3/api-docs': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/swagger-ui': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/webjars': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  test: {
    environment: 'jsdom',
    outputFile: {
      junit: 'test-results/vitest-junit.xml',
    },
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{js,vue}'],
      exclude: ['src/**/*.test.js'],
      reporter: ['text', 'json-summary', 'lcov'],
      reportsDirectory: 'coverage',
      thresholds: {
        statements: 30,
        branches: 65,
        functions: 35,
        lines: 30,
      },
    },
  }
})
