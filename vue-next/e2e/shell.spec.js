import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { resolve } from 'node:path'
import { emptyLive } from './fixtures/live.js'
test.beforeEach(async ({ page }) => emptyLive(page))

test('root redirect, local draft and old application links work without business requests', async ({ page }) => {
  const businessRequests = []
  page.on('request', (request) => {
    if (/^\/(?:api|manage|admin\/auth)(?:\/|$)/.test(new URL(request.url()).pathname)) businessRequests.push(request.url())
  })
  await page.goto('/')
  await expect(page).toHaveURL(/\/chat$/)
  await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧' })).toBeVisible()
  await expect(page.getByRole('link', { name: '原版对比' })).toHaveAttribute('href', 'http://localhost:5173/chat')
  await expect(page.getByRole('link', { name: '管理后台' })).toHaveAttribute('href', 'http://localhost:5173/admin/login?redirect=/admin/dashboard')
  const input = page.getByRole('textbox', { name: '输入问题' })
  await input.fill('这是一条本地草稿')
  await expect(input).toHaveValue('这是一条本地草稿')
  await expect(page.getByRole('button', { name: '发送消息' })).toBeEnabled()
  await page.getByRole('button', { name: '新建对话', exact: true }).click()
  await expect(input).toHaveValue('')
  expect(businessRequests.every((url) => /\/(session\/list|document\/options|knowledge-base\/options)$/.test(new URL(url).pathname))).toBe(true)
})

for (const viewport of [{ width: 390, height: 844 }, { width: 768, height: 1024 }, { width: 1024, height: 768 }, { width: 1440, height: 1000 }]) {
  test(`shell, keyboard and accessibility at ${viewport.width}`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport)
    await page.goto('/chat')
    await expect(page.getByRole('textbox', { name: '输入问题' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBe(0)
    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', { name: '跳到主要内容' })).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('main')).toBeFocused()
    if (viewport.width < 1024) {
      const trigger = page.getByRole('button', { name: '打开会话导航' })
      await trigger.click()
      await expect(page.getByRole('dialog', { name: '会话导航' })).toBeVisible()
      const close = page.getByRole('button', { name: '关闭会话导航' })
      await expect(close).toBeFocused()
      await page.keyboard.press('Shift+Tab')
      await expect(page.getByRole('dialog').getByRole('link', { name: '打开原版聊天' })).toBeFocused()
      await page.keyboard.press('Tab')
      await expect(close).toBeFocused()
      expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
      await page.keyboard.press('Escape')
      await expect(page.getByRole('dialog')).toHaveCount(0)
      await expect(trigger).toBeFocused()
    }
    const accessibility = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
    expect(accessibility.violations).toEqual([])
    await page.mouse.click(viewport.width - 5, 100)
    await page.screenshot({ path: resolve('../doc/重构前端/evidence/welcome-focus', `${testInfo.project.name}-welcome-${viewport.width}.png`), fullPage: true, animations: 'disabled' })
  })
}



