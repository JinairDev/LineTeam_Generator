import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // backend application.properties 의 server.port 와 같아야 함. ECONNREFUSED → 백엔드 미실행 또는 포트 불일치
        target: 'http://localhost:8765',
        changeOrigin: true,
      },
      // 개발 시 Node가 Google CSV를 받아 전달(SASE 환경에서 Java PKIX와 별도로 시도)
      '/google-sheets-csv': {
        target: 'https://docs.google.com',
        changeOrigin: true,
        secure: false,
      },
    },
  },
})
