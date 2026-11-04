import { describe, expect, it } from 'vitest'
import { legacyLinks } from './environment.js'

describe('legacy application links', () => {
  it('keeps legacy routes on the configured server and preserves a base path', () => {
    expect(legacyLinks('https://example.org/legacy')).toEqual({
      chat: 'https://example.org/legacy/chat',
      admin: 'https://example.org/legacy/admin/login?redirect=/admin/dashboard'
    })
  })

  it.each(['javascript:alert(1)', 'not-a-url', '', 'https://user:secret@example.org'])('uses a safe local fallback for %s', (value) => {
    expect(legacyLinks(value).chat).toBe('http://localhost:5173/chat')
  })
})
