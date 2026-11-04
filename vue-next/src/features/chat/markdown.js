import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import python from 'highlight.js/lib/languages/python'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import { sourceReferences } from './references.js'

for (const [name, language] of Object.entries({ javascript, json, python, bash, sql })) hljs.registerLanguage(name, language)
const escape = (value) => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;')

export function renderMarkdown(source, references = []) {
  const codes = []
  const eligible = new Set(sourceReferences(references).map((item) => item.index))
  const parser = new Marked({ gfm: true, breaks: true })
  parser.use({ extensions: [{ name: 'citation', level: 'inline', start: (src) => src.indexOf('['),
    tokenizer(src) {
      const match = /^\[([1-9][0-9]*)\](?!\()/.exec(src)
      if (match) return { type: 'citation', raw: match[0], index: Number(match[1]) }
    },
    renderer(token) { return eligible.has(token.index) ? `<button type="button" class="citation-trigger" data-citation="${token.index}" aria-label="查看来源 ${token.index}">[${token.index}]</button>` : token.raw }
  }] })
  parser.use({ renderer: {
    html: (token) => escape(token.text),
    code: ({ text, lang }) => {
      const index = codes.push(text) - 1
      const requested = (lang || '').split(/\s/)[0]
      const language = requested === 'js' ? 'javascript' : requested
      const supported = hljs.getLanguage(language)
      const content = supported ? hljs.highlight(text, { language }).value : escape(text)
      return `<div class="code-block"><div class="code-heading"><span>${escape(requested || 'text')}</span><button type="button" data-copy-code="${index}" aria-label="复制代码 ${index + 1}">复制代码</button></div><pre><code>${content}</code></pre></div>`
    }
  } })
  const html = DOMPurify.sanitize(parser.parse(source || ''), {
    FORBID_TAGS: ['img', 'svg', 'style', 'iframe', 'form', 'input', 'video', 'audio'],
    FORBID_ATTR: ['style'],
    ADD_ATTR: ['data-copy-code', 'data-citation', 'aria-label', 'type']
  })
  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll('table, pre').forEach((element) => element.setAttribute('tabindex', '0'))
  template.content.querySelectorAll('a').forEach((link) => {
    // A citation-looking label is still part of the Markdown link. Keep a
    // single interactive target rather than nesting a source button inside it.
    link.querySelectorAll('[data-citation]').forEach((button) => button.replaceWith(document.createTextNode(button.textContent)))
    try {
      const url = new URL(link.getAttribute('href'))
      if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Unsupported URL')
      link.setAttribute('target', '_blank')
      link.setAttribute('rel', 'noopener noreferrer')
    } catch { link.replaceWith(document.createTextNode(link.textContent)) }
  })
  return { html: template.innerHTML, codes }
}
