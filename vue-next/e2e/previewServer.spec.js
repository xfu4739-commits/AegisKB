import { expect, test } from '@playwright/test'

// APIRequestContext is not intercepted by page.route: this covers the actual
// development middleware as well as the page tests' independent fixture mocks.
test('development server serves isolated fixtures and explicit errors', async ({ request }) => {
  const history = await request.get('/__preview/chat?scenario=history')
  expect(history.headers()['content-type']).toContain('application/json')
  expect((await history.json()).sessions).toHaveLength(2)
  expect((await (await request.get('/__preview/chat?scenario=welcome')).json()).sessions).toHaveLength(0)
  expect((await request.get('/__preview/chat?scenario=error')).status()).toBe(503)
})
