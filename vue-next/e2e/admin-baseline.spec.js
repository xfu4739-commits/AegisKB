import { test, expect } from '@playwright/test'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { mockLegacyAdmin, documentId } from './fixtures/admin-baseline.js'

const output = resolve(process.env.AEGIS_BASELINE_EVIDENCE_DIR || '../doc/重构前端/evidence/admin/A0')
const directionOutput = resolve(output, 'revision-2')
const directionSource = resolve('../doc/重构前端/evidence/admin/A0/revision-2')
const pages = [
  ['dashboard', '/admin/dashboard', '运营总览'],
  ['knowledge-bases', '/admin/knowledge-bases', '知识库管理'],
  ['documents', '/admin/documents', '文档接入'],
  ['document-detail', `/admin/documents/${documentId}`, 'A0 隔离示例 · 团队知识使用指南'],
  ['observability', '/admin/observability', '会话总数'],
  ['configuration', '/admin/settings/configuration', '当前配置']
]
for (const width of [390, 1440]) {
  test(`A0 legacy admin read-only baseline at ${width}`, async ({ context, page }, info) => {
    test.setTimeout(90000)
    const state = await mockLegacyAdmin(context)
    const pageErrors = []
    page.on('pageerror', (error) => pageErrors.push(error.message))
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 })
    for (const [name, path, text] of pages) {
      await page.goto(`http://localhost:5173${path}`)
      await expect(page.locator('#admin-main-content').getByText(text, { exact: true }).first()).toBeVisible()
      await page.waitForLoadState('networkidle')
      await page.screenshot({ path: resolve(output, `${info.project.name}-legacy-${name}-${width}.png`), fullPage: true, animations: 'disabled' })
      expect(state.blocked).toEqual([])
      expect(pageErrors).toEqual([])
    }
    expect(state.calls.some((c) => c.path === '/manage/document/detail/query' && c.body.documentId === documentId)).toBe(true)
    await test.info().attach('isolated-read-requests', { body: JSON.stringify(state.calls, null, 2), contentType: 'application/json' })
  })
  test(`A0 static design direction at ${width}`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 })
    const html = readFileSync(resolve(directionSource, '后台布局草案.html'), 'utf8')
    const logo = readFileSync(resolve('public/aegis-mark.svg')).toString('base64')
    await page.setContent(html.replace('../../../../../../vue-next/public/aegis-mark.svg', `data:image/svg+xml;base64,${logo}`))
    await expect.poll(() => page.locator('.brand img').evaluate((img) => img.complete && img.naturalWidth > 0)).toBe(true)
    for (const view of ['overview', 'list', 'detail']) {
      await page.evaluate((view) => { location.hash = view }, view)
      await expect(page.locator(`[data-page="${view}"] h1`)).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      await page.screenshot({ path: resolve(directionOutput, `${info.project.name}-direction-${view}-${width}.png`), fullPage: true, animations: 'disabled' })
    }
    const cdp = await page.context().newCDPSession(page)
    await cdp.send('DOM.enable'); await cdp.send('CSS.enable')
    const { root } = await cdp.send('DOM.getDocument')
    const renderedFonts = {}
    for (const selector of ['[data-page="detail"] h1', '[data-page="detail"] dd']) {
      const { nodeId } = await cdp.send('DOM.querySelector', { nodeId: root.nodeId, selector })
      renderedFonts[selector] = (await cdp.send('CSS.getPlatformFontsForNode', { nodeId })).fonts
    }
    writeFileSync(resolve(directionOutput, `${info.project.name}-fonts-${width}.json`), JSON.stringify(renderedFonts, null, 2))
    await cdp.detach()
    if (width < 1024) {
      await page.locator('.mobile-navigation > summary').click()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      await page.screenshot({ path: resolve(directionOutput, `${info.project.name}-direction-navigation-${width}.png`), fullPage: true, animations: 'disabled' })
    }
  })
}
