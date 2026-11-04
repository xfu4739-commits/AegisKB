export async function emptyLive(page) {
  await page.route('**/api/chat/**', (route) => {
    const path = new URL(route.request().url()).pathname
    return route.fulfill({ json: { code: '0', data: path.endsWith('/session/list') ? { sessions: [], totalPages: '1' } : [] } })
  })
}
