import { test, expect } from '@playwright/test'

test('real protocol hydrates history and scope; deleting only happens after confirmation', async ({ page }) => {
  const writes = []
  await page.route('**/api/chat/**', (route) => {
    const path = new URL(route.request().url()).pathname
    const body = route.request().postDataJSON()
    let data
    if (path.endsWith('/session/list')) { expect(body.pageNo).toBe('1'); expect(body.pageSize).toBe('200'); data = { sessions: [{ conversationId: '90071992547409931234', latestUserMessage: '真实协议会话' }], totalPages: '1' } }
    else if (path.endsWith('/knowledge-base/options')) data = [{ id: 'kb', baseName: '测试资料库' }]
    else if (path.endsWith('/document/options')) data = [{ documentId: 'doc', knowledgeBaseId: 'kb', documentName: '测试文档' }]
    else if (path.endsWith('/session/detail')) { expect(body.conversationId).toBe('90071992547409931234'); data = { conversationId: body.conversationId, latestUserMessage: '真实协议会话', chatMode: 'DOCUMENT', knowledgeBaseSelectionMode: 'SELECTED', selectedKnowledgeBaseIds: ['kb'], selectedDocumentId: 'doc', exchanges: [{ exchangeId: '90071992547409931235', question: '历史问题', answer: '历史回答' }] } }
    else if (path.endsWith('/session/reset')) { writes.push(body); data = null }
    else throw new Error(`Unexpected endpoint ${path}`)
    return route.fulfill({ json: { code: '0', data } })
  })
  await page.goto('/chat')
  await expect(page.getByText('历史回答', { exact: true })).toBeVisible()
  await expect(page.getByText('当前文档 · 测试文档')).toBeVisible()
  await expect(page.getByRole('button', { name: '发送消息' })).toBeDisabled()
  expect(writes).toEqual([])
  await page.getByRole('button', { name: /^会话操作：/ }).click()
  await page.getByRole('menuitem', { name: '删除会话', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '删除会话', exact: true })
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
  expect(writes).toEqual([])
  await page.getByRole('button', { name: /^会话操作：/ }).click()
  await page.getByRole('menuitem', { name: '删除会话', exact: true }).click()
  await dialog.getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来，随时开始吧' })).toBeVisible()
  expect(writes).toEqual([{ conversationId: '90071992547409931234' }])
})

