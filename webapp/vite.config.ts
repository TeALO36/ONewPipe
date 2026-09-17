import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In development the UI runs on Vite and talks to a local ONewPipe server
// (ONEWPIPE_SERVER, default http://127.0.0.1:18080). The production build is
// copied into the server's web resources.
const server = process.env.ONEWPIPE_SERVER ?? 'http://127.0.0.1:18080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: server, changeOrigin: false },
      '/health': { target: server, changeOrigin: false }
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: false
  }
})
