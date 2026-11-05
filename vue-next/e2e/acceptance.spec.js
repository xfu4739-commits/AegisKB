import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { resolve } from 'node:path'
import { mockAcceptance } from './fixtures/acceptance.js'

const output = resolve(process.env.AEGIS_EVIDENCE_DIR || '../doc/重构前端/evidence/welcome-focus')
async function shot(page, name, info) {
  await page.screenshot({ path: resolve(output, `${info.project.name}-${name}.png`), animations: 'disabled', fullPage: true })
}
async function signature(page) {
  return page.locator('#chat-main-content, #chat-next-main').evaluate((el) => {
    const style = getComputedStyle(el)
    return { color: style.color, background: style.backgroundColor, font: style.fontFamily, width: el.clientWidth }
  })
}
for (const width of [390, 1440]) {
  for (const scene of ['welcome', 'history', 'error']) {
    test(`same snapshot comparison ${scene} at ${width}`, async ({ page, context }, info) => {
      const calls = await mockAcceptance(context, scene)
      await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 })
      for (const [version, url] of [['legacy', 'http://localhost:5173/chat'], ['next', 'http://localhost:5174/chat']]) {
        await page.goto(url)
        await page.waitForLoadState('networkidle')
        if (scene === 'welcome') await expect(page.getByRole('textbox').first()).toBeVisible()
        else {
          await expect(page.getByText('一个清晰的工作流', { exact: true })).toBeVisible()
          if (scene === 'error') await expect(page.getByText(/隔离验收：回答服务中断/)).toBeVisible()
          // Match the reading position, rather than screenshotting different answers.
          const readingPanel = page.locator(version === 'legacy' ? '[aria-label="对话消息"]' : '.welcome-scroll')
          await readingPanel.evaluate((el) => { el.style.scrollBehavior = 'auto'; el.scrollTo({ top: 0, behavior: 'instant' }) })
          await expect.poll(() => readingPanel.evaluate((el) => el.scrollTop)).toBe(0)
        }
        await shot(page, `${version}-${scene}-${width}`, info)
        if (version === 'next') {
          expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
        }
      }
      expect(calls.some((path) => /\/(stream|reset|stop)$/.test(path))).toBe(false)
    })
  }
}

test('answer failure state remains accessible', async ({ page, context }) => {
  await mockAcceptance(context, 'error')
  await page.goto('/chat')
  await expect(page.getByRole('button', { name: '重新发送此问题', exact: true })).toBeVisible()
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
})

for (const width of [390, 768, 1024, 1440]) {
  test(`source modal and scope settings are accessible at ${width}`, async ({ page, context }, info) => {
    await mockAcceptance(context)
    await page.setViewportSize({ width, height: width < 1024 ? 844 : 1000 })
    await page.goto('/chat')
    const citation = page.getByRole('button', { name: '查看来源 1', exact: true })
    await citation.click()
    const modal = page.getByRole('dialog', { name: '引用来源', exact: true })
    await expect(modal.getByText('来源标识', { exact: true })).toBeVisible()
    await expect(modal.getByText(/检索说明已加载/)).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
    await shot(page, `source-${width}`, info)
    await page.keyboard.press('Escape')
    await expect(modal).toBeHidden(); await expect(citation).toBeFocused()
    const scope = page.getByRole('button', { name: '设置回答范围', exact: true })
    await scope.click()
    await page.getByRole('checkbox', { name: '隔离资料库' }).check()
    await page.getByLabel('回答模式', { exact: true }).selectOption('DOCUMENT')
    await page.getByLabel('提问文档', { exact: true }).selectOption('90071992547409931234')
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
    await shot(page, `scope-${width}`, info)
    await page.keyboard.press('Escape'); await expect(scope).toBeFocused()
    await expect(page.getByText('当前文档 · 前端验收示例文档')).toBeVisible()
  })
}

test('old app remains unchanged after visiting new app', async ({ page, context }) => {
  await mockAcceptance(context, 'welcome')
  await page.goto('http://localhost:5173/chat')
  const oldStyle = await signature(page)
  await page.goto('/chat')
  const newStyle = await signature(page)
  const legacyTab = context.waitForEvent('page')
  await page.getByRole('link', { name: '原版对比', exact: true }).click()
  const old = await legacyTab
  await expect(old.getByRole('textbox', { name: '输入问题' })).toBeVisible()
  expect(await signature(old)).toEqual(oldStyle)
  await old.close()
  expect(await signature(page)).toEqual(newStyle)
  await page.reload(); await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧' })).toBeVisible()
  expect(await signature(page)).toEqual(newStyle)
})

test('admin link and auth guard remain usable', async ({ page, context }, info) => {
  await mockAcceptance(context, 'welcome')
  await page.goto('/chat')
  await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧' })).toBeVisible()
  const newStyle = await signature(page)
  const adminTab = context.waitForEvent('page')
  await page.getByRole('link', { name: '管理后台', exact: true }).click()
  const admin = await adminTab
  await expect(admin.getByRole('heading', { name: '登录工作台' })).toBeVisible()
  await admin.goto('http://localhost:5173/admin/dashboard')
  await expect(admin).toHaveURL(/\/admin\/login\?redirect=/)
  await shot(admin, 'legacy-admin-guard', info)
  await admin.close()
  expect(await signature(page)).toEqual(newStyle)
})



