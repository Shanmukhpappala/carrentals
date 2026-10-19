import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const apiTarget = env.VITE_API_BASE_URL?.replace(/\/$/, '')

  return {
    plugins: [react()],
    server: {
      host: '0.0.0.0',
      ...(apiTarget ? {
        proxy: {
          '/api': {
            target: apiTarget,
            changeOrigin: true,
            configure(proxy) {
              proxy.on('proxyReq', (proxyRequest) => proxyRequest.removeHeader('origin'))
            },
          },
        },
      } : {}),
    },
  }
})
