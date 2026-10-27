import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    // Bind IPv4 loopback so Playwright (127.0.0.1) and Windows localhost both reach Vite.
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      // Only `/api/v1` — do not match SPA routes like `/api-keys`.
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Preserve Set-Cookie Path from Spring for browser-session refresh cookies.
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    // Playwright specs live under e2e/ — keep Vitest from picking them up.
    exclude: ['**/node_modules/**', '**/dist/**', '**/e2e/**'],
  },
});
