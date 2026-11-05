import { test, expect } from '@playwright/test'
import { spawn } from 'node:child_process'
import { readdir, readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { mockAcceptance } from './fixtures/acceptance.js'

test('built app excludes development fixtures and keeps the default chat route', async ({ page, context, request }) => {
  const cwd = fileURLToPath(new URL('..', import.meta.url))
  const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--host', 'localhost', '--port', '4174', '--strictPort'], { cwd, windowsHide: true, stdio: 'pipe' })
  let log = ''
  server.stdout.on('data', (chunk) => { log += chunk })
  server.stderr.on('data', (chunk) => { log += chunk })
  const exited = new Promise((resolve) => server.once('exit', resolve))
  try {
    await expect.poll(async () => {
      if (server.exitCode !== null) throw new Error(`Production preview failed: ${log}`)
      return request.get('http://localhost:4174').then((result) => result.status()).catch(() => 0)
    }).toBe(200)
    const files = await readdir(new URL('../dist/assets/', import.meta.url))
    for (const name of files.filter((name) => name.endsWith('.js'))) {
      const source = await readFile(new URL(`../dist/assets/${name}`, import.meta.url), 'utf8')
      expect(source.includes('demo-local-'), `${name} must exclude demo session generation`).toBe(false)
      expect(source.includes('/__preview/chat'), `${name} must exclude the development data endpoint`).toBe(false)
      expect(source.includes('a8-session'), `${name} must exclude admin comparison data`).toBe(false)
      expect(source.includes('真实字段资料库'), `${name} must exclude contract fixtures`).toBe(false)
    }
    await mockAcceptance(context, 'welcome')
    const fixtureRequests = []
    page.on('request', (req) => { if (req.url().includes('/__preview/chat')) fixtureRequests.push(req.url()) })
    await page.goto('http://localhost:4174/preview/chat')
    await expect(page).toHaveURL('http://localhost:4174/chat')
    await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧' })).toBeVisible()
    await expect(page.getByRole('link', { name: '交互演示', exact: true })).toHaveCount(0)
    expect(fixtureRequests).toEqual([])
    await page.goto('http://localhost:4174/admin')
    await expect(page).toHaveURL(/\/admin\/login\?redirect=/)
    await expect(page.getByRole('heading', { name: '登录管理后台' })).toBeVisible()
  } finally {
    server.kill()
    await exited
  }
})

