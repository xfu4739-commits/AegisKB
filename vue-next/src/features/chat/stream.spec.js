import { describe, it, expect } from 'vitest'
import { consumeStream } from './stream.js'

describe('backend event stream', () => {
  it('decodes split UTF-8, CRLF, comments and multiline data without losing the tail', async () => {
    const bytes = new TextEncoder().encode(': ping\r\nevent: message\r\ndata: {"type":"text",\r\ndata: "content":"中文"}\r\n\r\ndata: {"type":"status","content":"完成"}\n\ndata: [DONE]\n\n')
    let offset = 0
    const events = []
    await consumeStream(new ReadableStream({ pull(c) { if (offset === bytes.length) c.close(); else c.enqueue(bytes.slice(offset, ++offset)) } }), (event) => events.push(event))
    expect(events).toEqual([{ type: 'text', content: '中文' }, { type: 'status', content: '完成' }])
  })
  it('fails closed for malformed events', async () => {
    const stream = new ReadableStream({ start(c) { c.enqueue(new TextEncoder().encode('data: invalid\n\n')); c.close() } })
    await expect(consumeStream(stream, () => {})).rejects.toThrow('流式事件')
  })
})
