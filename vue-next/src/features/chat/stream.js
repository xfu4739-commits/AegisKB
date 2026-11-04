// Transport only: business event types remain owned by the existing backend.
export async function consumeStream(stream, onEvent) {
  const reader = stream.getReader(), decoder = new TextDecoder()
  let buffer = ''
  const dispatch = (block) => {
    const lines = block.split(/\r?\n/)
    const data = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).replace(/^ /, '')).join('\n')
    if (!data.trim() || data.trim() === '[DONE]') return
    let event
    try { event = JSON.parse(data) } catch { throw new Error('无法解析后端流式事件。') }
    onEvent(event)
  }
  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
      let match
      while ((match = /\r?\n\r?\n/.exec(buffer))) {
        dispatch(buffer.slice(0, match.index))
        buffer = buffer.slice(match.index + match[0].length)
      }
      if (done) { if (buffer.trim()) dispatch(buffer); return }
    }
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock() }
}
