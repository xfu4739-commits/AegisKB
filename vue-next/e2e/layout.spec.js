import { test, expect } from '@playwright/test'
import { resolve } from 'node:path'
import { mockAcceptance } from './fixtures/acceptance.js'

test('AegisKB keeps navigation quiet and the scope inside the composer', async ({ page, context }, info) => {
  await mockAcceptance(context)
  await page.goto('/chat')
  await expect(page).toHaveTitle(/AegisKB/)
  await expect(page.locator('.brand')).toHaveText('AegisKB')
  await expect(page.locator('.brand img')).toBeVisible()
  await expect.poll(() => page.locator('.brand img').evaluate((img) => img.naturalWidth)).toBeGreaterThan(0)
  await page.locator('.brand-identity').screenshot({ path: resolve('../doc/重构前端/evidence/welcome-focus', `${info.project.name}-brand.png`) })
  await expect(page.locator('.workspace-header')).not.toContainText('如何把一个想法')
  await expect(page.locator('.composer-caption, .preview-toolbar')).toHaveCount(0)
  await expect(page.locator('.composer-sender').getByRole('button', { name: '设置回答范围' })).toBeVisible()
  await page.getByRole('button', { name: '收起侧栏', exact: true }).click()
  await expect(page.getByRole('button', { name: '新建对话', exact: true })).toBeHidden()
  await page.getByRole('button', { name: '展开侧栏', exact: true }).click()
  await expect(page.getByRole('button', { name: '新建对话', exact: true })).toBeVisible()
})

test('preview scenarios live in a popover and stay isolated', async ({ page, context }, info) => {
  await mockAcceptance(context, 'welcome')
  await page.goto('/preview/chat?scenario=welcome')
  await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧' })).toBeVisible()
  await expect(page.locator('.preview-toolbar')).toHaveCount(0)
  await page.getByRole('button', { name: '预览设置', exact: true }).click()
  await page.getByLabel('选择演示场景', { exact: true }).selectOption('history')
  await expect(page.getByRole('table')).toHaveCount(1)
  await page.getByRole('button', { name: '预览设置', exact: true }).click()
  await expect(page.getByLabel('选择演示场景', { exact: true })).toBeHidden()
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.screenshot({ path: resolve('../doc/重构前端/evidence/welcome-focus', `${info.project.name}-preview-1440.png`), animations: 'disabled' })
})



