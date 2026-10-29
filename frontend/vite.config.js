import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 개발 서버가 /api 요청을 로컬 백엔드로 넘겨, 브라우저에는 같은 출처로 보이게 한다.
// 같은 출처여야 SameSite=Strict 리프레시 쿠키가 따라가고 CORS 설정이 필요 없다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
});
