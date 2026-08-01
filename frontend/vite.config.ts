import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 백엔드를 프록시로 붙여 브라우저에서는 같은 출처로 보이게 한다.
// CORS 설정이 필요 없고, 아래 격리 헤더를 켜도 API 호출이 막히지 않는다.
const proxy = {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
}

// 탭 메모리를 코드로 재려면 performance.measureUserAgentSpecificMemory() 가 필요하고
// 그 API 는 cross-origin isolation 을 요구한다.
const headers = {
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Cross-Origin-Embedder-Policy': 'require-corp',
}

export default defineConfig({
  plugins: [react()],
  server: { proxy, headers },
  preview: { proxy, headers },
})
