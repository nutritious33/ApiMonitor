import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    // Output directly into Spring Boot's static resource folder
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    // During `npm run dev`, proxy API calls to the running Spring Boot instance
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
