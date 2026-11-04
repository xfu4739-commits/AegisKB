<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { Button, Checkbox, Popover } from 'ant-design-vue'
import { Sender } from 'ant-design-x-vue'
import { ArrowUpIcon, StopIcon, BookOpenIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import ChatInput from './ChatInput.vue'
import ChatSelect from './ChatSelect.vue'
const inputComponents = { input: ChatInput }
const props = defineProps({ draft: { type: String, default: '' }, enabled: Boolean, preview: Boolean, streaming: Boolean, stopping: Boolean, stopRequest: Boolean, scopeEnabled: Boolean, busy: Boolean, scope: { type: Object, default: () => ({ mode: 'OPEN_CHAT', knowledgeBaseIds: [], documentId: '' }) }, knowledgeBases: { type: Array, default: () => [] }, documents: { type: Array, default: () => [] } })
const emit = defineEmits(['update:draft', 'scope', 'submit', 'stop'])
const scopeOpen = ref(false)
const scopeTrigger = ref(null)
watch(() => props.busy, (busy) => { if (busy) scopeOpen.value = false })
const modeOptions = computed(() => [{ value: 'OPEN_CHAT', label: '开放提问' }, { value: 'AUTO_DOCUMENT', label: '自动知识', disabled: !props.scope.knowledgeBaseIds.length }, { value: 'DOCUMENT', label: '当前文档', disabled: !props.scope.knowledgeBaseIds.length }])
const filteredDocuments = computed(() => props.documents.filter((doc) => props.scope.knowledgeBaseIds.includes(doc.knowledgeBaseId)))
const scopeSummary = computed(() => {
  const mode = modeOptions.value.find((item) => item.value === props.scope.mode)?.label
  const document = filteredDocuments.value.find((item) => item.id === props.scope.documentId)
  if (props.scope.mode === 'DOCUMENT') return `${mode} · ${document?.name || (props.scope.documentId ? '历史文档当前不可用' : '请选择文档')}`
  return `${mode} · ${props.scope.knowledgeBaseIds.length ? `${props.scope.knowledgeBaseIds.length} 个知识库` : '未限定知识库'}`
})
const canSend = computed(() => props.enabled && !props.busy && Boolean(props.draft.trim()) && (props.scope.mode !== 'DOCUMENT' || filteredDocuments.value.some((doc) => doc.id === props.scope.documentId)))
async function closeScope() { scopeOpen.value = false; await nextTick(); scopeTrigger.value?.$el?.focus() }
function handleScopeKeydown(event) {
  if (event.key !== 'Escape') return
  event.stopPropagation()
  if (event.target.getAttribute('role') === 'combobox' && event.target.getAttribute('aria-expanded') === 'true') return
  closeScope()
}
</script>

<template>
  <footer class="chat-next-composer">
    <Sender
      class="composer-sender"
      :value="draft"
      :auto-size="{ minRows: 2, maxRows: 7 }"
      :actions="false"
      :components="inputComponents"
      :send-disabled="!canSend"
      :styles="{ input: { fontSize: 'var(--text-input)', lineHeight: '1.8' }, footer: { padding: '0 12px 12px' } }"
      placeholder="给 AegisKB 发送消息"
      @change="(value) => $emit('update:draft', value)"
      @submit="() => emit('submit')"
    >
      <template #footer>
        <div class="composer-tools">
          <div class="scope-tools">
            <Popover v-model:open="scopeOpen" trigger="click" placement="topLeft">
              <template #content>
                <div class="scope-panel" @keydown="handleScopeKeydown">
                  <div class="scope-panel-title"><strong>回答范围</strong><Button type="text" size="small" aria-label="关闭回答范围设置" @click="closeScope">完成</Button></div>
                  <fieldset class="knowledge-options"><legend>知识库</legend><Checkbox.Group :value="scope.knowledgeBaseIds" :options="knowledgeBases.map((item) => ({ value: item.id, label: item.name }))" @change="(value) => emit('scope', { knowledgeBaseIds: value })" /></fieldset>
                  <Button v-if="scope.knowledgeBaseIds.length" type="link" size="small" @click="emit('scope', { knowledgeBaseIds: [] })">清空知识库</Button>
                  <label for="preview-answer-mode">回答模式</label>
                  <ChatSelect id="preview-answer-mode" :value="scope.mode" :options="modeOptions" @change="(mode) => emit('scope', { mode })" />
                  <template v-if="scope.mode === 'DOCUMENT'">
                    <label for="preview-document">提问文档</label>
                    <ChatSelect id="preview-document" :value="scope.documentId || undefined" :options="filteredDocuments.map((item) => ({ value: item.id, label: item.name }))" placeholder="请选择文档" @change="(documentId) => emit('scope', { documentId })" />
                    <p v-if="!scope.documentId" class="scope-help">选择一个文档后才可发送消息。</p>
                  </template>
                </div>
              </template>
              <Button ref="scopeTrigger" type="text" size="small" :disabled="!scopeEnabled || busy" class="scope-button" :class="{ 'scope-selected': scope.knowledgeBaseIds.length > 0 }" aria-label="设置回答范围">
                <BookOpenIcon class="chat-next-icon" aria-hidden="true" /><span class="scope-value">{{ scopeSummary }}</span><ChevronDownIcon class="small-icon" aria-hidden="true" />
              </Button>
            </Popover>
          </div>
          <Button v-if="streaming || stopping" type="primary" shape="circle" :loading="stopRequest" :disabled="stopRequest" aria-label="停止生成" :title="stopping ? '再次请求服务端停止' : '停止生成'" class="send-button" @click="emit('stop')"><StopIcon class="chat-next-icon" aria-hidden="true" /></Button>
          <Button v-else type="primary" shape="circle" :disabled="!canSend" aria-label="发送消息" :title="preview ? '只发送本地演示消息' : '发送问题'" class="send-button" @click="emit('submit')">
            <ArrowUpIcon class="chat-next-icon" aria-hidden="true" />
          </Button>
        </div>
      </template>
    </Sender>
    <span v-if="stopping" class="chat-next-sr-only" role="status">正在确认服务端停止</span>
  </footer>
</template>

<style scoped>
.chat-next-composer { width: calc(100% - 64px); max-width: 780px; margin: 0 auto; padding: 12px 0 max(24px, env(safe-area-inset-bottom)); }
.scope-value { color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.composer-sender { border: 1px solid var(--border-strong); border-radius: var(--radius-panel); box-shadow: var(--shadow-composer); background: var(--surface); }
.composer-tools { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.scope-tools { display: flex; align-items: center; min-width: 0; max-width: calc(100% - 48px); }
.scope-button { display: inline-flex; align-items: center; gap: 7px; max-width: 100%; font-size: var(--text-caption); padding: 5px 9px; height: 32px; border: 1px solid var(--border); border-radius: var(--radius-round); background: var(--surface); color: var(--muted); }
.scope-selected { border-color: var(--border-strong); background: var(--accent); color: var(--primary); }
.scope-button svg { width: 15px; height: 15px; }
.small-icon { width: 14px; height: 14px; }
.send-button { display: grid; place-items: center; flex-shrink: 0; width: 36px; height: 36px; box-shadow: none; }
.send-button:disabled { background: var(--send-disabled); border-color: var(--send-disabled); color: var(--primary-text); }
.scope-panel { display: grid; gap: 10px; width: min(320px, calc(100vw - 48px)); max-height: 55dvh; overflow-y: auto; }
.scope-panel-title { display: flex; justify-content: space-between; align-items: center; }
.scope-panel label, .scope-help { color: var(--muted); font-size: var(--text-caption); margin: 0; }
.knowledge-options { border: 0; padding: 0; margin: 0; min-width: 0; }
.knowledge-options legend { font-size: var(--text-caption); color: var(--muted); margin-bottom: 8px; }
@media (max-width: 600px) {
  .chat-next-composer { width: calc(100% - 32px); padding-bottom: max(12px, env(safe-area-inset-bottom)); }
  .scope-button { font-size: var(--text-caption); }
}
</style>
