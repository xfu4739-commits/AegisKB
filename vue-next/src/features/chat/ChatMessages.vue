<script setup>
import { h, ref, inject, onMounted, onUpdated, onBeforeUnmount } from 'vue'
import { Button, Alert, Modal } from 'ant-design-vue'
import { Bubble, Prompts } from 'ant-design-x-vue'
import { DocumentDuplicateIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import ChatMarkdown from './ChatMarkdown.vue'
import { sourceReferences } from './references.js'
import { chatApi } from './chatApi.js'
const props = defineProps({ messages: { type: Array, default: () => [] }, preview: Boolean, busy: Boolean, conversationId: { type: String, default: '' } })
const emit = defineEmits(['prompt', 'retry', 'layout'])
onMounted(() => emit('layout'))
onUpdated(() => emit('layout'))
onBeforeUnmount(() => { sourceVersion++ })
const followups = (texts) => texts.map((text, index) => ({ key: String(index), description: h(Button, { type: 'text', class: 'followup-button', onClick: (event) => { event.stopPropagation(); emit('prompt', text) } }, { default: () => text }) }))
const copyStatus = ref('')
const source = ref(null), sourceError = ref(''), sourceLoading = ref(false), retrievalCount = ref(null)
const popupContainer = inject('chat-next-container')
let sourceVersion = 0
async function openSource(message, index) {
  source.value = sourceReferences(message.references).find((item) => item.index === index) || null
  const token = ++sourceVersion
  sourceError.value = ''; sourceLoading.value = false; retrievalCount.value = null
  if (!source.value || !message.exchangeId || props.preview) return
  sourceLoading.value = true
  try { const data = await chatApi.retrieval(props.conversationId, message.exchangeId); if (token === sourceVersion && Array.isArray(data)) retrievalCount.value = data.length }
  catch { if (token === sourceVersion) sourceError.value = '检索说明暂时无法加载，已绑定的来源和回答仍可查看。' }
  finally { if (token === sourceVersion) sourceLoading.value = false }
}
function closeSource() { sourceVersion++; source.value = null }
async function copyMessage(message) {
  try { await navigator.clipboard.writeText(message.content); copyStatus.value = '消息已复制' }
  catch { copyStatus.value = '复制失败，请手动选择文本。' }
}
</script>

<template>
  <section class="chat-next-messages" :aria-label="preview ? '演示对话消息' : '对话消息'">
    <article v-for="message in messages" :key="message.id" :data-message-role="message.role" class="message-row">
      <Bubble
        :content="message.content"
        :placement="message.role === 'user' ? 'end' : 'start'"
        :variant="message.role === 'user' ? 'filled' : 'borderless'"
        :typing="false"
        :styles="{ content: { maxWidth: '100%', minWidth: '0', padding: message.role === 'user' ? '14px 18px' : '0', background: message.role === 'user' ? 'var(--user-bubble)' : 'transparent', borderRadius: 'var(--radius-control)' } }"
      >
        <template #avatar><span class="message-avatar" :class="{ 'user-avatar': message.role === 'user' }" aria-hidden="true"><template v-if="message.role === 'user'">你</template><img v-else src="/aegis-mark.svg" alt="" width="26" height="26" /></span></template>
        <template #header><span class="message-author">{{ message.role === 'user' ? '你' : 'AegisKB' }}<time>{{ message.time }}</time></span></template>
        <template #message>
          <p v-if="message.role === 'user'" class="user-text">{{ message.content }}</p>
          <template v-else>
            <ChatMarkdown :content="message.content" :references="message.references" @citation="(index) => openSource(message, index)" />
            <p v-if="message.statusText" class="message-status" role="status">{{ message.statusText }}</p>
            <Alert v-if="message.error" type="error" show-icon>
              <template #message>回答未完成</template><template #description>{{ message.error }}<Button :disabled="busy" type="link" @click="$emit('retry', message.id)"><ArrowPathIcon class="chat-next-icon" aria-hidden="true" />{{ preview ? '重试演示回答' : '重新发送此问题' }}</Button></template>
            </Alert>
          </template>
        </template>
        <template #footer>
          <div v-if="message.content" class="message-actions"><Button type="text" size="small" :aria-label="`复制${message.role === 'user' ? '问题' : '回答'}`" @click="copyMessage(message)"><DocumentDuplicateIcon class="chat-next-icon" aria-hidden="true" /><span>复制</span></Button></div>
          <div v-if="message.recommendations?.length" class="recommendations">
            <p>继续探索</p>
            <Prompts :items="followups(message.recommendations)" wrap :styles="{ item: { padding: '0', border: 'none' } }" @item-click="({ data }) => emit('prompt', message.recommendations[Number(data.key)])">
            </Prompts>
          </div>
        </template>
      </Bubble>
    </article>
    <p v-if="copyStatus" class="copy-status" role="status">{{ copyStatus }}</p>
    <Modal :open="Boolean(source)" title="引用来源" :footer="null" :get-container="popupContainer" centered destroy-on-close @cancel="closeSource">
      <div v-if="source" class="source-details">
        <h3>[{{ source.index }}] {{ source.title }}</h3>
        <dl><template v-for="(label, key) in { knowledgeBaseName: '知识库', sectionPath: '章节', page: '页码', documentId: '文档 ID', chunk: '定位', identity: '来源标识' }" :key="key"><template v-if="source[key]"><dt>{{ label }}</dt><dd>{{ source[key] }}</dd></template></template></dl>
        <blockquote>{{ source.snippet || '后端未返回引用片段。' }}</blockquote>
        <p v-if="sourceLoading" role="status">正在读取检索说明…</p>
        <p v-if="retrievalCount !== null">检索说明已加载，共 {{ retrievalCount }} 条记录；正文引用以本轮后端绑定为准。</p>
        <Alert v-if="sourceError" type="warning" :message="sourceError" />
      </div>
    </Modal>
  </section>
</template>

<style scoped>
.chat-next-messages { width: 100%; max-width: 760px; padding: 24px 0 40px; margin: 0 auto; }
.message-row { min-width: 0; margin-bottom: 30px; }
.message-avatar { display: grid; place-items: center; width: 28px; height: 28px; border-radius: var(--radius-round); color: var(--primary); background: transparent; font-size: var(--text-caption); font-weight: 500; }
.message-avatar svg { width: 20px; height: 20px; }
.user-avatar { color: var(--muted); background: var(--sidebar); font-size: var(--text-caption); }
.message-author { display: inline-flex; gap: 10px; font-size: var(--text-caption); color: var(--text); font-weight: 500; }
.message-author time { color: var(--muted); font-weight: 400; }
.user-text { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
.message-actions { margin-top: 6px; }
.message-actions :deep(button) { display: inline-flex; align-items: center; gap: 5px; color: var(--muted); font-size: var(--text-caption); }
.message-actions svg { width: 15px; height: 15px; }
.recommendations { padding-top: 10px; margin-top: 10px; border-top: 1px solid var(--border); }
.recommendations > p, .copy-status { color: var(--muted); font-size: var(--text-caption); }
.recommendations :deep(.followup-button) { height: auto; white-space: normal; text-align: start; }
.message-status { color: var(--muted); font-size: var(--text-caption); }
.source-details { overflow-wrap: anywhere; }
.source-details dl { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 8px 16px; }
.source-details dt { color: var(--muted); }
.source-details dd { margin: 0; }
.source-details blockquote { margin: 16px 0; padding: 14px; white-space: pre-wrap; background: var(--canvas); border-left: 3px solid var(--border-strong); }
@media (max-width: 600px) { .chat-next-messages { padding-top: 20px; } }
</style>
