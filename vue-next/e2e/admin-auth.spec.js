import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { resolve } from 'node:path'
import { mockAdminAuth, loginAsFixture, seedAdmin, fixtureToken } from './fixtures/admin-auth.js'

const output = resolve(process.env.AEGIS_ADMIN_EVIDENCE_DIR || '../doc/重构前端/evidence/admin/A1')
const assertAccount = (page) => expect(page.getByRole('heading', { name: '账户', exact: true })).toBeVisible()
const storageToken = (page) => page.evaluate(() => localStorage.getItem('aegis-kb-admin-token'))
const errors = new WeakMap()
test.beforeEach(({ page }) => { errors.set(page, []); page.on('pageerror', (error) => errors.get(page).push(error.message)) })
test.afterEach(({ page }) => { expect(errors.get(page)).toEqual([]) })

test('missing auth, form validation, login failure/retry and password visibility', async ({ page, context }, info) => {
  const state = await mockAdminAuth(context)
  await page.goto('/admin/account')
  await expect(page).toHaveURL(/\/admin\/login\?redirect=/)
  await expect(page.getByRole('textbox', { name: '用户名' })).toHaveValue('')
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('')
  await page.screenshot({ path: resolve(output, `${info.project.name}-login.png`), animations: 'disabled' })
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('请输入用户名', { exact: true })).toBeVisible()
  await expect(page.getByText('请输入用户名', { exact: true })).toHaveCSS('opacity', '1')
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
  expect(state.loginCalls).toBe(0)
  state.loginFailures = 1
  await loginAsFixture(page)
  await expect(page.getByText('账号或密码不正确', { exact: true })).toBeVisible()
  await expect(page.getByText('请输入用户名', { exact: true })).toHaveCount(0)
  await expect(page.getByText('请输入密码', { exact: true })).toHaveCount(0)
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
  expect(await storageToken(page)).toBeNull()
  await page.getByRole('button', { name: '显示密码' }).click()
  await expect(page.getByLabel('密码', { exact: true })).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: '隐藏密码' }).click()
  state.loginDelay = 200
  await page.getByRole('button', { name: '登录', exact: true }).dblclick()
  await assertAccount(page)
  expect(state.loginCalls).toBe(2)
  expect(await storageToken(page)).toBe(fixtureToken)
  expect(await page.evaluate(() => localStorage.getItem('aegis-kb-admin-user'))).toBe('fixture-admin')
  expect(state.blocked).toEqual([])
})

test('external return path is refused and unknown admin routes stay in the admin shell', async ({ page, context }) => {
  await mockAdminAuth(context)
  await page.goto('/admin/login?redirect=//external.invalid')
  await loginAsFixture(page); await expect(page.getByRole('heading', { name: '运营总览', exact: true })).toBeVisible()
  await expect(page).toHaveURL('http://localhost:5174/admin/dashboard')
  await page.goto('/admin/not-implemented')
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
  await page.getByRole('link', { name: '返回后台', exact: true }).click()
  await expect(page.getByRole('heading', { name: '运营总览', exact: true })).toBeVisible()
})

test('known expired token and server 401 both return to login', async ({ page, context }) => {
  const state = await mockAdminAuth(context)
  await seedAdmin(page, `header.${Buffer.from(JSON.stringify({ exp: 1 })).toString('base64url')}.signature`)
  await expect(page.getByRole('heading', { name: '登录管理后台' })).toBeVisible()
  expect(state.meCalls).toBe(0)
  await seedAdmin(page, 'server-rejected-token')
  await expect(page.getByRole('heading', { name: '登录管理后台' })).toBeVisible()
  expect(await storageToken(page)).toBeNull()
})

test('connection failures preserve token and can retry; 403 never masquerades as logged out', async ({ page, context }, info) => {
  const state = await mockAdminAuth(context); state.meStatus = 503
  await seedAdmin(page)
  await expect(page.getByRole('heading', { name: '无法确认登录状态' })).toBeVisible()
  expect(await storageToken(page)).toBe(fixtureToken)
  await page.screenshot({ path: resolve(output, `${info.project.name}-access-error.png`), animations: 'disabled' })
  state.meStatus = 200
  await page.getByRole('button', { name: '重试', exact: true }).click(); await assertAccount(page)
  state.meStatus = 403
  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: '无权访问', exact: true })).toBeVisible()
  expect(await storageToken(page)).toBe(fixtureToken)
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
  await page.getByRole('link', { name: '返回对话', exact: true }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧' })).toBeVisible()
})

test('logout cancel is read-only; confirmed logout clears the session and redirects', async ({ page, context }) => {
  const state = await mockAdminAuth(context)
  await seedAdmin(page); await assertAccount(page)
  const trigger = page.getByRole('button', { name: '退出登录', exact: true })
  await trigger.click()
  const modal = page.getByRole('dialog', { name: '退出登录', exact: true })
  await modal.getByRole('button', { name: '取消', exact: true }).click()
  await expect(modal).toBeHidden(); await expect(trigger).toBeFocused()
  expect(state.logoutCalls).toBe(0)
  await trigger.click(); await modal.getByRole('button', { name: '退出', exact: true }).click()
  await expect(page.getByRole('heading', { name: '登录管理后台' })).toBeVisible()
  expect(await storageToken(page)).toBeNull()
  await expect.poll(() => state.logoutCalls).toBe(1)
})

test('logout in another tab removes a protected profile without redirecting public chat', async ({ page, context }) => {
  await mockAdminAuth(context); await seedAdmin(page); await assertAccount(page)
  const other = await context.newPage(); await other.goto('/chat')
  await other.evaluate(() => localStorage.removeItem('aegis-kb-admin-token'))
  await expect(page.getByRole('heading', { name: '登录管理后台' })).toBeVisible()
  await expect(other).toHaveURL(/\/chat$/)
  await other.close()
})

for (const width of [390, 768, 1024, 1440]) {
  test(`admin shell navigation, keyboard and styles at ${width}`, async ({ page, context }, info) => {
    await mockAdminAuth(context)
    await page.setViewportSize({ width, height: width < 1024 ? 844 : 1000 })
    const adminModules = []
    page.on('request', (request) => { if (new URL(request.url()).pathname.startsWith('/src/features/admin/')) adminModules.push(request.url()) })
    await page.goto('/chat')
    await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧' })).toBeVisible()
    const signature = () => page.locator('#chat-next-main').evaluate((el) => ({ color: getComputedStyle(el).color, font: getComputedStyle(el).fontFamily, width: el.clientWidth }))
    const original = await signature()
    expect(adminModules).toEqual([])
    await page.goto('/admin/login')
    await expect(page.getByRole('heading', { name: '登录管理后台' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
    await page.screenshot({ path: resolve(output, `${info.project.name}-login-${width}.png`), fullPage: true, animations: 'disabled' })
    await seedAdmin(page); await assertAccount(page)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.keyboard.press('Tab'); await expect(page.getByRole('link', { name: '跳到主要内容' })).toBeFocused()
    await page.keyboard.press('Enter'); await expect(page.getByRole('main')).toBeFocused()
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
    await page.screenshot({ path: resolve(output, `${info.project.name}-shell-${width}.png`), fullPage: true, animations: 'disabled' })
    if (width < 1024) {
      const trigger = page.getByRole('button', { name: '打开后台导航' }); await trigger.click()
      const dialog = page.getByRole('dialog', { name: '后台导航', exact: true })
      await expect(dialog.getByRole('button', { name: '关闭后台导航' })).toBeFocused()
      await page.keyboard.press('Shift+Tab'); await expect(dialog.getByRole('link', { name: '旧版后台' })).toBeFocused()
      await page.keyboard.press('Tab'); await expect(dialog.getByRole('button', { name: '关闭后台导航' })).toBeFocused()
      expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
      await page.screenshot({ path: resolve(output, `${info.project.name}-navigation-${width}.png`), fullPage: true, animations: 'disabled' })
      await page.keyboard.press('Escape'); await expect(dialog).toHaveCount(0); await expect(trigger).toBeFocused()
      await trigger.click(); await dialog.getByRole('link', { name: '返回对话', exact: true }).click()
    } else {
      await page.getByRole('button', { name: '收起后台导航' }).click()
      await expect(page.locator('.admin-next-sidebar')).toHaveCSS('width', '64px')
      await page.getByRole('link', { name: '返回对话', exact: true }).click()
    }
    await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧' })).toBeVisible()
    expect(await signature()).toEqual(original)
    await expect(page.getByRole('link', { name: '管理后台', exact: true })).toHaveAttribute('href', 'http://localhost:5173/admin/login?redirect=/admin/dashboard')
  })
}
