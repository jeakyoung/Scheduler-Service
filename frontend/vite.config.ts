import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    // @testing-library/jest-dom's auto-extend (imported in setupFiles) reaches for the
    // global `expect` at import time, so this must be on for that side-effect import to work.
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
})
