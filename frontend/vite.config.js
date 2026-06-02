import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [VantResolver()],
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/recipe': {
        target: 'http://localhost:9001',
        changeOrigin: true,
      },
    },
  },
})
