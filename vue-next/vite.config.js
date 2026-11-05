import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

const root = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, root, '')
  const target = env.VITE_PROXY_TARGET || 'http://127.0.0.1:9083'
  return {
    plugins: [vue(), {
      name: 'isolated-chat-preview',
      apply: 'serve',
      async configureServer(server) {
        const { previewFixture } = await import('./preview/fixtures.js')
        server.middlewares.use('/__preview/chat', (request, response) => {
          const scenario = new URL(request.url, 'http://localhost').searchParams.get('scenario') || 'history'
          response.setHeader('Content-Type', 'application/json; charset=utf-8')
          response.setHeader('Cache-Control', 'no-store')
          if (scenario === 'error') {
            response.statusCode = 503
            response.end(JSON.stringify({ message: 'Intentional preview failure' }))
          } else {
            const finish = () => response.end(JSON.stringify(previewFixture(scenario)))
            if (scenario === 'loading') setTimeout(finish, 1200)
            else finish()
          }
        })
      }
    }],
    server: {
      host: 'localhost',
      port: 5174,
      strictPort: true,
      fs: { strict: true, allow: [root] },
      proxy: Object.fromEntries(['/api', '/manage', '/admin/auth'].map((path) => [path, { target, changeOrigin: true }]))
    },
    test: {
      environment: 'jsdom',
      include: ['src/**/*.spec.js'],
      restoreMocks: true
    }
  }
})
