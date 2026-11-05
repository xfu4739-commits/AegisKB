import { describe, expect, it } from 'vitest'
import { renderMarkdown } from './markdown.js'

describe('safe answer rendering', () => {
  it('does not put citation buttons inside link labels', () => {
    const host = document.createElement('div')
    host.innerHTML = renderMarkdown('[[1]](https://example.org)', [{ citationIdentity: 'bound-source' }]).html
    expect(host.querySelector('a')).not.toBeNull()
    expect(host.querySelector('a button')).toBeNull()
    expect(host.querySelector('a').textContent).toBe('[1]')
  })
  it('only binds stable backend source identities and preserves original numbering outside code and links', () => {
    const references = [{ contextOnly: true, referenceId: 'ctx' }, { citationIdentity: 'source', documentName: '<来源>' }, { title: '无 identity' }]
    const host = document.createElement('div')
    host.innerHTML = renderMarkdown('[1] [2] [3] [99] [0] ［2］ `[2]` [2](https://example.org)\n\n```text\n[2]\n```', references).html
    expect([...host.querySelectorAll('[data-citation]')].map((item) => item.dataset.citation)).toEqual(['2'])
    expect(host.querySelector('[data-citation]').textContent).toBe('[2]')
    expect(host.textContent).toContain('[99]')
  })
  it('renders tables and code while keeping exact copyable code', () => {
    const rendered = renderMarkdown('| 项目 | 结果 |\n| --- | --- |\n| 范围 | 明确 |\n\n```js\nconst value = "<test>"\n```')
    const host = document.createElement('div')
    host.innerHTML = rendered.html
    expect(host.querySelector('table')?.getAttribute('tabindex')).toBe('0')
    expect(host.querySelector('pre')?.getAttribute('tabindex')).toBe('0')
    expect(rendered.html).toContain('data-copy-code="0"')
    expect(rendered.codes).toEqual(['const value = "<test>"'])
  })

  it('does not execute raw HTML, images, scripts or unsafe links', () => {
    const rendered = renderMarkdown('<img src=x onerror=alert(1)>\n<script>alert(1)</script>\n\n[bad](javascript:alert%281%29)\n\n[good](https://example.org)')
    const host = document.createElement('div')
    host.innerHTML = rendered.html
    expect(host.querySelector('img,script,iframe,svg')).toBeNull()
    expect(host.querySelector('[onerror]')).toBeNull()
    expect(host.querySelectorAll('a')).toHaveLength(1)
    expect(host.querySelector('a').rel).toBe('noopener noreferrer')
  })

  it('keeps unknown citations as plain text, without inventing source binding', () => {
    const rendered = renderMarkdown('待核对 [1]、[99]，以及全角［2］。')
    expect(rendered.html).toContain('[99]')
    expect(rendered.html).not.toContain('data-citation')
  })
})
