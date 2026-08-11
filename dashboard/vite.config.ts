import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dashboard is a self-contained SPA under dashboard/. The backend API is reached via a
// RELATIVE base (/api/v1) so the browser stays same-origin — the backend (fastify) sets no
// CORS headers, so cross-origin calls fail. In dev the Vite server proxies /api and /health
// to the backend (default http://localhost:3000, override with VITE_PROXY_TARGET); in
// production a reverse proxy must route /api and /health to the backend, or set
// VITE_API_BASE_URL to an absolute URL.
const proxyTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:3000';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
      },
      // Health lives at the server root (backend/src/server.ts), not under /api/v1.
      '/health': {
        target: proxyTarget,
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'es2022',
    sourcemap: true,
  },
});
