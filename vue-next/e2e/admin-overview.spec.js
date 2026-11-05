import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { resolve } from 'node:path'
import { mockOverview } from './fixtures/admin-overview.js'
import { seedAdmin, fixtureToken } from './fixtures/admin-auth.js'

const output = resolve(process.env.AEGIS_OVERVIEW_EVIDENCE_DIR || '../doc/重构前端/evidence/admin/A2')
const errors = new WeakMap(), states = new WeakMap()
test.beforeEach(async ({ page }) => {
  errors.set(page, []); page.on('pageerror', (error) => errors.get(page).push(error.message))
  await page.clock.setFixedTime(new Date('2026-11-04T09:30:00+08:00'))
})
test.afterEach(({ page }) => { expect(errors.get(page)).toEqual([]); expect(states.get(page)?.auth.blocked || []).toEqual([]) })
async function setup(page, context, scene) {
  const state = await mockOverview(context, scene); states.set(page, state)
  await seedAdmin(page)
  return state
}
async function chooseWindow(page, label) {
  await page.getByRole('combobox', { name: '统计时间窗' }).selectOption({ label })
}
const metric = (page, key) => page.locator(`[data-metric="${key}"] strong`)

test('default admin entry shows real sample denominators and only existing read requests', async ({ page, context }) => {
  const state = await setup(page, context)
  await page.goto('/admin')
  await expect(page).toHaveURL(/\/admin\/dashboard$/)
  await expect(metric(page, 'total')).toHaveText('12')
  await expect(metric(page, 'parse')).toHaveText('2')
  await expect(page.getByRole('region', { name: '文档处理', exact: true })).toContainText('返回样本 3 条 / 总量 12')
  await expect(page.getByRole('region', { name: '路由样本', exact: true })).toContainText('33.3%')
  await expect(page.getByRole('link', { name: '旧版文档管理' })).toHaveAttribute('href', /redirect=%2Fadmin%2Fdocuments/)
  expect(state.calls[0].body).toEqual({ pageNo: '1', pageSize: '200', keyword: '' })
  expect(state.calls[1].body).toEqual({ pageNo: '1', pageSize: '200' })
})

test('partial failures retry independently and failed refresh keeps clearly marked last results', async ({ page, context }) => {
  const state = await setup(page, context); state.documentStatus = 503
  await page.goto('/admin/dashboard')
  await expect(page.getByText('文档统计读取失败', { exact: true })).toBeVisible()
  await expect(page.getByRole('region', { name: '路由样本', exact: true })).toContainText('33.3%')
  expect(await page.locator('[data-metric="total"]').count()).toBe(0)
  const routeReads = state.calls.filter((c) => c.path.includes('/route/')).length
  state.documentStatus = 200
  await page.getByRole('button', { name: '重试文档统计' }).click()
  await expect(metric(page, 'total')).toHaveText('12')
  expect(state.calls.filter((c) => c.path.includes('/route/')).length).toBe(routeReads)
  state.documentStatus = 503
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.getByText('文档统计刷新失败，仍显示上次结果', { exact: true })).toBeVisible()
  await expect(metric(page, 'total')).toHaveText('12')
})

test('quality charts keep units, deltas, gaps and accessible data tables', async ({ page, context }) => {
  await setup(page, context)
  await page.goto('/admin/quality-overview?window=7')
  await expect(metric(page, 'success-rate')).toHaveText('59.1%')
  await expect(page.getByText('-4.3 个百分点', { exact: true })).toBeVisible()
  await expect(metric(page, 'failed')).toHaveText('0')
  const inspect = page.getByRole('button', { name: '查看路由总量逐日数据' })
  await inspect.click()
  const modal = page.getByRole('dialog', { name: '路由总量 · 逐日数据' })
  await expect(modal.getByRole('cell', { name: '100', exact: true })).toBeVisible()
  await expect(modal).toHaveCSS('opacity', '1')
  await expect(modal).toHaveCSS('transform', 'none')
  await expect.poll(() => page.locator('.admin-next-overlay-host').evaluate((host) => host.getAnimations({ subtree: true }).every((animation) => animation.playState !== 'running' && !animation.pending))).toBe(true)
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
  await page.keyboard.press('Escape'); await expect(modal).toBeHidden(); await expect(inspect).toBeFocused()
  await page.locator('[aria-label="趋势指标"]').getByText('置信度', { exact: true }).click()
  await expect(page.getByRole('radio', { name: '置信度', exact: true })).toBeChecked()
  await expect(page.getByRole('img', { name: '路由置信度图，详细数值见下方数据表' })).toContainText('1.00')
  await page.getByText('查看路由数据表', { exact: true }).click()
  await expect(page.getByRole('region', { name: '路由逐日数据' }).getByRole('row', { name: /11-03/ })).toContainText('—')
})

test('window changes reject late responses and keep stale data only for the same window', async ({ page, context }) => {
  const state = await setup(page, context)
  await page.goto('/admin/quality-overview')
  await expect(page.getByText('无可比上一期', { exact: true })).toBeVisible()
  expect(await page.locator('.admin-next-overview-delta').count()).toBe(0)
  state.qualityStatus = 503
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await expect(page.getByText('刷新失败，仍显示上次结果', { exact: true })).toBeVisible()
  await expect(metric(page, 'route-total')).toHaveText('372')
  state.qualityStatus = 200; state.delays['7'] = 250
  await chooseWindow(page, '近 7 天')
  await expect.poll(() => state.calls.some((c) => c.body.windowDays === '7')).toBe(true)
  await chooseWindow(page, '近 30 天')
  await expect.poll(() => state.completed.some((c) => c.window === '7') && state.completed.some((c) => c.window === '30')).toBe(true)
  await expect(page.locator('.admin-next-overview-meta')).toContainText('近 30 天')
  await expect(page).toHaveURL(/window=30/)
  state.qualityStatus = 503
  await chooseWindow(page, '近 90 天')
  await expect(page.getByText('运行统计读取失败', { exact: true })).toBeVisible()
  expect(await page.locator('[data-metric]').count()).toBe(0)
  state.qualityStatus = 200
  await page.getByRole('button', { name: '重试运行统计' }).click()
  await expect(page.locator('.admin-next-overview-meta')).toContainText('近 90 天')
})

test('missing fields stay unrecorded and real zeros remain zero', async ({ page, context }) => {
  await setup(page, context, 'missing')
  await page.goto('/admin/quality-overview')
  await expect(metric(page, 'success-rate')).toHaveText('—')
  await expect(metric(page, 'failed')).toHaveText('0')
  const row = page.getByRole('region', { name: '检索通道统计' }).getByRole('row', { name: /向量检索/ })
  await expect(row.getByRole('cell').nth(1)).toHaveText('—')
  await expect(page.getByText('日序列已截断，仅显示接口返回区间', { exact: true })).toBeVisible()
})

test('empty data has a truthful empty state', async ({ page, context }) => {
  await setup(page, context, 'empty')
  await page.goto('/admin/dashboard')
  await expect(metric(page, 'total')).toHaveText('0')
  await expect(page.getByText('暂无文档样本', { exact: true })).toBeVisible()
  await page.goto('/admin/quality-overview')
  await expect(page.getByText('当前时间窗暂无运行记录', { exact: true })).toBeVisible()
  expect(await page.locator('[data-metric]').count()).toBe(0)
})

test('management 403 preserves the session, while 401 returns to login', async ({ page, context }) => {
  const state = await setup(page, context); state.documentStatus = 403
  await page.goto('/admin/dashboard')
  await expect(page.getByText('无权读取统计', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('aegis-kb-admin-token'))).toBe(fixtureToken)
  state.documentStatus = 401
  await page.getByRole('button', { name: '重试文档统计' }).click()
  await expect(page.getByRole('heading', { name: '登录管理后台' })).toBeVisible()
})

test('changing identity hides previous statistics while server confirmation is pending', async ({ page, context }) => {
  await setup(page, context)
  await page.goto('/admin/dashboard')
  await expect(metric(page, 'total')).toHaveText('12')
  let release, requested = false
  const confirmation = new Promise((resolve) => { release = resolve })
  await context.route('**/admin/auth/me', async (route) => {
    requested = true
    await confirmation
    await route.fulfill({ json: { code: '0', data: { username: 'second-fixture-admin' } } })
  })
  const other = await context.newPage()
  try {
    await other.goto('/chat')
    await other.evaluate(() => localStorage.setItem('aegis-kb-admin-token', 'second-fixture-token'))
    await expect.poll(() => requested).toBe(true)
    await expect(page.locator('[data-metric]')).toHaveCount(0)
    release()
    await expect(page.getByText('second-fixture-admin', { exact: true })).toBeVisible()
    await expect(metric(page, 'total')).toHaveText('12')
  } finally { release(); await other.close() }
})

for (const width of [390, 768, 1024, 1440]) {
  test(`overview layouts and accessibility at ${width}`, async ({ page, context }, info) => {
    await setup(page, context)
    await page.setViewportSize({ width, height: width < 1024 ? 844 : 1000 })
    for (const [path, title, name] of [['dashboard', '运营总览', 'dashboard'], ['quality-overview?window=7', '知识运行全景', 'quality']]) {
      await page.goto(`/admin/${path}`)
      await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
      await expect(page.locator('[data-metric] strong').first()).not.toHaveText('—')
      await page.waitForLoadState('networkidle')
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
      expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
      await page.screenshot({ path: resolve(output, `${info.project.name}-${name}-${width}.png`), fullPage: true, animations: 'disabled' })
    }
  })
}
