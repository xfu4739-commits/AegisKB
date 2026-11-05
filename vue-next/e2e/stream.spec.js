import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { resolve } from 'node:path'

async function mockApi(page, stream) {
  const requests = []
  await page.route('**/api/chat/**', async (route) => {
    const path = new URL(route.request().url()).pathname, body = route.request().postDataJSON()
    requests.push({ path, body })
    if (path.endsWith('/stream')) return stream(route, body)
    if (path.endsWith('/retrieval/results')) return route.fulfill({ status: 503, json: { code: '503', message: '隔离检索说明失败' } })
    const data = path.endsWith('/session/list') ? { sessions: [], totalPages: '1' } : path.endsWith('/session/detail') ? { conversationId: body.conversationId, latestUserMessage: '隔离测试问题', exchanges: [{ exchangeId: '90071992547409931235', question: '隔离测试问题', answer: '后端归档回答 [1] [2] [99]', references: [{ contextOnly: true, referenceId: 'context' }, { citationIdentity: 'source-2', documentName: '隔离测试文档', quoteText: '仅来自后端的片段', pageNo: '12' }] }] } : []
    return route.fulfill({ json: { code: '0', data } })
  })
  return requests
}
test('IME and Shift+Enter do not submit; Enter streams, hydrates final sources and preserves modal focus', async ({ page }, info) => {
  const requests = await mockApi(page, (route) => route.fulfill({ contentType: 'text/event-stream', body: 'data: {"type":"text","content":"流式初稿"}\n\ndata: [DONE]\n\n' }))
  await page.goto('/chat')
  const input = page.getByRole('textbox', { name: '输入问题' })
  await input.fill('隔离测试问题')
  await input.dispatchEvent('compositionstart')
  await input.dispatchEvent('keydown', { key: 'Enter', keyCode: 229, isComposing: true })
  await input.dispatchEvent('compositionend')
  expect(requests.filter((item) => item.path.endsWith('/stream'))).toHaveLength(0)
  await input.press('Shift+Enter')
  expect(requests.filter((item) => item.path.endsWith('/stream'))).toHaveLength(0)
  await input.press('Enter')
  const citation = page.getByRole('button', { name: '查看来源 2', exact: true })
  await expect(citation).toBeVisible()
  await expect(input).toHaveValue('')
  expect(requests.filter((item) => item.path.endsWith('/stream'))).toHaveLength(1)
  expect(requests.find((item) => item.path.endsWith('/stream')).body).toMatchObject({ question: '隔离测试问题', chatMode: 'OPEN_CHAT', knowledgeBaseSelectionMode: 'NONE', selectedDocumentId: null })
  await expect(page.getByRole('button', { name: '查看来源 1', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '查看来源 99', exact: true })).toHaveCount(0)
  await citation.click()
  const modal = page.getByRole('dialog', { name: '引用来源', exact: true })
  await expect(modal.getByText('仅来自后端的片段')).toBeVisible()
  await expect(modal.getByText(/检索说明暂时无法加载/)).toBeVisible()
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
  await page.screenshot({ path: resolve(process.env.AEGIS_EVIDENCE_DIR || '../doc/重构前端/evidence/welcome-focus', `${info.project.name}-citation.png`), animations: 'disabled' })
  await page.keyboard.press('Escape'); await expect(modal).toBeHidden(); await expect(citation).toBeFocused()
  await expect(page.getByText('后端归档回答', { exact: false })).toBeVisible()
})
test('pending generation locks conflicts and stop calls the server; late reply cannot replace stopped content', async ({ page }) => {
  let release
  const requests = await mockApi(page, async (route) => { await new Promise((resolve) => { release = resolve }); await route.fulfill({ contentType: 'text/event-stream', body: 'data: {"type":"text","content":"迟到回答"}\n\n' }).catch(() => {}) })
  await page.goto('/chat')
  await page.getByRole('textbox', { name: '输入问题' }).fill('停止测试')
  await page.getByRole('button', { name: '发送消息' }).click()
  const stop = page.getByRole('button', { name: '停止生成', exact: true })
  await expect(stop).toBeVisible()
  await expect(page.getByRole('button', { name: '新建对话', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '设置回答范围', exact: true })).toBeDisabled()
  await stop.click(); release()
  await expect(page.getByText('已停止生成', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '新建对话', exact: true })).toBeEnabled()
  await expect(page.getByText('迟到回答', { exact: true })).toHaveCount(0)
  expect(requests.filter((item) => item.path.endsWith('/session/stop'))).toHaveLength(1)
})
test('partial answers remain visible on errors and retry requires a deliberate click', async ({ page }) => {
  const requests = await mockApi(page, (route) => route.fulfill({ contentType: 'text/event-stream', body: 'data: {"type":"text","content":"部分回答"}\n\ndata: {"type":"error","content":"隔离服务失败"}\n\n' }))
  await page.goto('/chat')
  await page.getByRole('textbox', { name: '输入问题' }).fill('失败测试')
  await page.getByRole('button', { name: '发送消息' }).click()
  await expect(page.getByText('部分回答', { exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('隔离服务失败')
  expect(requests.filter((item) => item.path.endsWith('/stream'))).toHaveLength(1)
  await page.getByRole('button', { name: '重新发送此问题', exact: true }).click()
  await expect(page.getByText('部分回答', { exact: true })).toHaveCount(2)
  expect(requests.filter((item) => item.path.endsWith('/stream'))).toHaveLength(2)
})

test('scrolling up pauses output following and the latest button resumes it', async ({ page }) => {
  await mockApi(page, () => { throw new Error('Stream must stay inside the isolated browser fixture') })
  await page.addInitScript(() => {
    const original = window.fetch.bind(window)
    window.fetch = (url, options) => {
      if (url === '/api/chat/stream') return Promise.resolve(new Response(new ReadableStream({ start(controller) {
        window.__pushChat = (content) => controller.enqueue(new TextEncoder().encode(`data: ${JSON.stringify({ type: 'text', content })}\n\n`))
      } }), { headers: { 'Content-Type': 'text/event-stream' } }))
      return original(url, options)
    }
  })
  await page.goto('/chat')
  await page.getByRole('textbox', { name: '输入问题' }).fill('滚动测试')
  await page.getByRole('button', { name: '发送消息' }).click()
  await page.waitForFunction(() => typeof window.__pushChat === 'function')
  await page.evaluate(() => window.__pushChat('长段落内容。\n\n'.repeat(90)))
  const panel = page.locator('.welcome-scroll')
  await expect.poll(() => panel.evaluate((el) => el.scrollHeight - el.scrollTop - el.clientHeight)).toBeLessThan(100)
  await panel.hover(); await page.mouse.wheel(0, -1100)
  const latest = page.getByRole('button', { name: '回到最新消息', exact: true })
  await expect(latest).toBeVisible()
  await page.evaluate(() => window.__pushChat('新片段。\n\n'.repeat(20)))
  await expect(latest).toBeVisible()
  expect(await panel.evaluate((el) => el.scrollHeight - el.scrollTop - el.clientHeight)).toBeGreaterThan(200)
  await latest.click()
  await expect.poll(() => panel.evaluate((el) => el.scrollHeight - el.scrollTop - el.clientHeight)).toBeLessThan(100)
  await page.getByRole('button', { name: '停止生成', exact: true }).click()
})



