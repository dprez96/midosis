import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// En desarrollo, /api se reenvía a core: el navegador ve un solo origen y core no
// necesita abrir CORS. MIDOSIS_CORE_URL permite apuntar a otra instancia, por ejemplo la de
// Cloud Run. changeOrigin envía el Host de core: sin él, Cloud Run responde 404, porque
// recibe «localhost:5173».
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: process.env.MIDOSIS_CORE_URL ?? 'http://localhost:8080', changeOrigin: true },
    },
    // Las pruebas leen los vectores del contrato, fuera de farmacia/. Solo esa carpeta,
    // no el resto del repositorio.
    fs: {
      allow: ['.', '../contrato/vectores'],
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/preparacion.ts'],
    restoreMocks: true,
  },
})
