import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// En desarrollo, /api se reenvía a core: el navegador ve un solo origen y core no
// necesita abrir CORS. MIDOSIS_CORE_URL permite apuntar a otra instancia.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': process.env.MIDOSIS_CORE_URL ?? 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/preparacion.ts'],
    restoreMocks: true,
  },
})
