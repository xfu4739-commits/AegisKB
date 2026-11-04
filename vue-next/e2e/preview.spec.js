import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { resolve } from 'node:path'
import { previewFixture } from '../preview/fixtures.js'
import { emptyLive } from './fixtures/live.js'

test.beforeEach(async ({ page }) => {
  await emptyLive(page)
  await page.route('**/__preview/chat?*', (route) => {
    const scene = new URL(route.request().url()).searchParams.get('scenario')
    return route.fulfill({ status: scene === 'error' ? 503 : 200, json: previewFixture(scene) })
  })
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: async (text) => { window.__copiedText = text } } })
  })
})

test('new conversation has one focused greeting and an empty composer', async ({ page }) => {
  await page.goto('/chat')
  const input = page.getByRole('textbox', { name: '输入问题' })
  await input.fill('尚未发送的草稿')
  await page.getByRole('button', { name: '新建对话', exact: true }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
  await expect(page.getByRole('button', { name: '探索一个想法', exact: true })).toHaveCount(0)
  await expect(page.getByText('从你的问题开始，连接已有知识。', { exact: true })).toHaveCount(0)
  await expect(input).toHaveValue('')
  await expect(input).toBeFocused()
  await expect(page.locator('[data-message-role]')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '发送消息' })).toBeDisabled()
})

test('demo messages, copy, recommendations, local send and session switching stay isolated', async ({ page }) => {
  const businessRequests = []
  page.on('request', (request) => { if (/^\/(api|manage|admin\/auth)\//.test(new URL(request.url()).pathname)) businessRequests.push(request.url()) })
  await page.goto('/preview/chat')
  await expect(page.getByText('今天', { exact: true })).toBeVisible()
  await expect(page.getByText('昨天', { exact: true })).toBeVisible()
  await expect(page.getByRole('table')).toHaveCount(1)
  await page.getByRole('button', { name: '复制代码 1', exact: true }).click()
  await expect.poll(() => page.evaluate(() => window.__copiedText)).toContain('const nextStep = {')
  await expect(page.getByRole('status')).toContainText('代码 1 已复制')
  await page.getByRole('button', { name: '帮我整理一个行动清单', exact: true }).click()
  const input = page.getByRole('textbox', { name: '输入问题' })
  await expect(input).toHaveValue('帮我整理一个行动清单')
  await expect(page.locator('[data-message-role]')).toHaveCount(2)
  await input.press('Enter')
  await expect(page.locator('[data-message-role]')).toHaveCount(4)
  await expect(input).toHaveValue('')
  await page.getByRole('button', { name: /^打开会话：这是一段用于检查长标题/ }).click()
  await expect(page.getByText('演示回答暂时未完成')).toBeVisible()
  await page.getByRole('button', { name: '重试演示回答' }).click()
  await expect(page.getByText('演示回答已重新展示。', { exact: false })).toBeVisible()
  expect(businessRequests).toEqual([])
})

test('scope controls require a selected document and clear stale scope', async ({ page }) => {
  await page.goto('/preview/chat?scenario=welcome')
  const input = page.getByRole('textbox', { name: '输入问题' })
  await input.fill('演示范围检查')
  await page.getByRole('button', { name: '设置回答范围' }).click()
  await page.getByRole('checkbox', { name: '产品资料（演示）' }).check()
  await page.getByLabel('回答模式', { exact: true }).selectOption('DOCUMENT')
  await expect(page.getByRole('button', { name: '发送消息' })).toBeDisabled()
  await page.getByLabel('提问文档', { exact: true }).selectOption('demo-doc-a')
  await page.getByRole('button', { name: '关闭回答范围设置' }).click()
  await expect(page.getByRole('button', { name: '发送消息' })).toBeEnabled()
  await expect(page.getByText('当前文档 · 产品协作指南（演示）')).toBeVisible()
  await page.getByRole('button', { name: '设置回答范围' }).click()
  await page.getByRole('button', { name: '清空知识库', exact: true }).click()
  await expect(page.getByText('开放提问 · 未限定知识库')).toBeVisible()
  await page.getByRole('button', { name: '关闭回答范围设置' }).click()
})

test('deleting demo sessions requires confirmation', async ({ page }) => {
  await page.goto('/preview/chat')
  await page.getByRole('button', { name: /^会话操作：如何把零散的信息/ }).click()
  await page.getByRole('menuitem', { name: '删除演示会话', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '删除演示会话' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByRole('button', { name: /^打开会话：/ })).toHaveCount(2)
  await page.getByRole('button', { name: /^会话操作：如何把零散的信息/ }).click()
  await page.getByRole('menuitem', { name: '删除演示会话', exact: true }).click()
  await dialog.getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('button', { name: /^打开会话：/ })).toHaveCount(1)
  await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧' })).toBeVisible()
})

test('loading, failure and retry are explicit', async ({ page }) => {
  await page.goto('/preview/chat?scenario=error')
  await expect(page.getByRole('alert')).toContainText('演示资料加载失败')
  await page.getByRole('button', { name: '重试加载' }).click()
  await expect(page.getByRole('table')).toHaveCount(1)
  let release
  await page.route('**/__preview/chat?*', async (route) => {
    await new Promise((resolve) => { release = resolve })
    await route.fulfill({ json: previewFixture() })
  })
  await page.reload()
  await expect(page.getByRole('status', { name: '正在加载对话' })).toBeVisible()
  release()
  await expect(page.getByRole('table')).toHaveCount(1)
})

for (const viewport of [{ width: 390, height: 844 }, { width: 768, height: 1024 }, { width: 1024, height: 768 }, { width: 1440, height: 1000 }]) {
  test(`long demo messages and accessibility at ${viewport.width}`, async ({ page }, info) => {
    await page.setViewportSize(viewport)
    await page.goto('/preview/chat')
    await expect(page.getByRole('table')).toHaveCount(1)
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBe(0)
    await page.getByRole('button', { name: '复制代码 1' }).scrollIntoViewIfNeeded()
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
    expect(results.violations).toEqual([])
    await page.screenshot({ path: resolve('../doc/重构前端/evidence/welcome-focus', `${info.project.name}-messages-${viewport.width}.png`), fullPage: true, animations: 'disabled' })
  })
}



