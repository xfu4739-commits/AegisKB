<script setup>
import { computed, ref } from 'vue'
import { renderMarkdown } from './markdown.js'
const props = defineProps({ content: { type: String, default: '' }, references: { type: Array, default: () => [] } })
const emit = defineEmits(['citation'])
const rendered = computed(() => renderMarkdown(props.content, props.references))
const copyStatus = ref('')

async function copyCode(event) {
  const citation = event.target.closest('[data-citation]')
  if (citation) { emit('citation', Number(citation.dataset.citation)); return }
  const trigger = event.target.closest('[data-copy-code]')
  if (!trigger) return
  const index = Number(trigger.dataset.copyCode)
  if (!Number.isInteger(index) || index < 0 || index >= rendered.value.codes.length) return
  try {
    await navigator.clipboard.writeText(rendered.value.codes[index])
    copyStatus.value = `代码 ${index + 1} 已复制`
  } catch { copyStatus.value = '复制失败，请手动选择代码。' }
}
</script>

<template>
  <div class="chat-next-markdown" @click="copyCode" v-html="rendered.html"></div>
  <p v-if="copyStatus" class="copy-status" role="status">{{ copyStatus }}</p>
</template>

<style scoped>
.chat-next-markdown { min-width: 0; overflow-wrap: anywhere; font-size: var(--text-body); line-height: 1.8; }
.chat-next-markdown :deep(h1), .chat-next-markdown :deep(h2), .chat-next-markdown :deep(h3) { margin: 1.5em 0 0.65em; font-size: var(--text-message-heading); font-weight: 600; line-height: 1.5; }
.chat-next-markdown :deep(h3) { font-size: var(--text-lead); }
.chat-next-markdown :deep(> :first-child) { margin-top: 0; }
.chat-next-markdown :deep(p) { margin: 0.8em 0; }
.chat-next-markdown :deep(li) { margin-block: 0.35em; }
.chat-next-markdown :deep(ul), .chat-next-markdown :deep(ol) { padding-inline-start: 1.5rem; }
.chat-next-markdown :deep(blockquote) { margin: 1.3em 0; border-inline-start: 3px solid var(--border-strong); padding: 4px 16px; color: var(--muted); background: var(--canvas); }
.chat-next-markdown :deep(table) { display: block; max-width: 100%; width: max-content; overflow-x: auto; border-collapse: collapse; margin: 1.3em 0; font-size: var(--text-small); }
.chat-next-markdown :deep(th), .chat-next-markdown :deep(td) { min-width: 9rem; padding: 10px 14px; border: 1px solid var(--border); text-align: start; vertical-align: top; }
.chat-next-markdown :deep(th) { background: var(--sidebar); font-weight: 500; }
.chat-next-markdown :deep(a) { color: var(--primary); text-decoration: underline; text-underline-offset: 3px; }
.chat-next-markdown :deep(code) { font-family: var(--font-mono); }
.chat-next-markdown :deep(code:not(pre code)) { padding: 2px 5px; background: var(--accent); border-radius: var(--radius-small); }
.chat-next-markdown :deep(.code-block) { overflow: hidden; border-radius: var(--radius-control); margin-block: 1.3em; background: var(--code-bg); }
.chat-next-markdown :deep(.code-heading) { display: flex; justify-content: space-between; align-items: center; padding: 7px 14px; color: var(--muted); font-size: var(--text-caption); }
.chat-next-markdown :deep(.code-heading button) { min-height: 32px; border: 0; padding: 3px 7px; background: transparent; color: var(--muted); cursor: pointer; border-radius: var(--radius-small); }
.chat-next-markdown :deep(.code-heading button:hover) { color: var(--primary); background: var(--accent); }
.chat-next-markdown :deep(pre) { max-width: 100%; overflow-x: auto; padding: 16px; margin: 0; font-size: var(--text-small); line-height: 1.8; }
.chat-next-markdown :deep(.hljs-keyword), .chat-next-markdown :deep(.hljs-built_in), .chat-next-markdown :deep(.hljs-literal) { color: var(--primary); }
.chat-next-markdown :deep(.hljs-string) { color: var(--code-string); }
.chat-next-markdown :deep(.hljs-comment) { color: var(--muted); }
.copy-status { margin: 8px 0; color: var(--muted); font-size: var(--text-caption); }
.chat-next-markdown :deep(.citation-trigger) { display: inline; border: 0; padding: 2px 5px; background: var(--accent); color: var(--primary); border-radius: var(--radius-small); cursor: pointer; font: inherit; }
</style>
