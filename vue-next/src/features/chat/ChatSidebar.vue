<script setup>
import { computed, h } from 'vue'
import { Button, Skeleton } from 'ant-design-vue'
import { Conversations } from 'ant-design-x-vue'
import { ChatBubbleLeftRightIcon, PlusCircleIcon, ViewColumnsIcon, ArrowUpRightIcon, EllipsisHorizontalIcon } from '@heroicons/vue/24/outline'
const emit = defineEmits(['new', 'select', 'delete', 'collapse'])
const props = defineProps({ legacyChat: { type: String, required: true }, preview: Boolean, disabled: Boolean, collapsible: Boolean, sessions: { type: Array, default: () => [] }, currentId: { type: String, default: '' }, loading: Boolean })
const items = computed(() => props.sessions.map((session) => ({
  key: session.id, group: session.group,
  label: h(Button, { disabled: props.disabled, type: 'text', class: 'session-trigger', title: session.title, 'aria-label': `打开会话：${session.title}`, 'aria-current': props.currentId === session.id ? 'page' : undefined,
    onClick: (event) => { event.stopPropagation(); emit('select', session.id) }
  }, { default: () => session.title })
})))
const menu = (session) => ({
  trigger: () => h(Button, { disabled: props.disabled, type: 'text', size: 'small', class: 'session-menu', 'aria-label': `会话操作：${props.sessions.find((item) => item.id === session.key)?.title}`, onClick: (event) => event.stopPropagation() }, { default: () => h(EllipsisHorizontalIcon, { class: 'chat-next-icon', 'aria-hidden': true }) }),
  getPopupContainer: (trigger) => trigger.closest('.mobile-dialog') || trigger.closest('.chat-next-root'),
  items: [{ key: 'delete', label: props.preview ? '删除演示会话' : '删除会话', danger: true, onClick: () => emit('delete', session.key) }]
})
</script>

<template>
  <div class="chat-next-sidebar">
    <div class="brand"><span class="brand-identity"><img src="/aegis-mark.svg" alt="" width="32" height="32" /><strong>AegisKB</strong></span><Button v-if="collapsible" type="text" class="sidebar-collapse" aria-label="收起侧栏" aria-controls="desktop-sidebar" @click="emit('collapse')"><ViewColumnsIcon class="chat-next-icon" aria-hidden="true" /></Button></div>
    <Button :disabled="disabled" block size="large" class="new-chat" @click="$emit('new')">
      <template #icon><PlusCircleIcon class="chat-next-icon" aria-hidden="true" /></template>
      新建对话
    </Button>
    <Skeleton v-if="loading" active :paragraph="{ rows: 4 }" aria-label="正在加载会话" />
    <Conversations v-else :items="items" :active-key="currentId" :menu="menu" :groupable="true" :class-names="{ item: 'conversation-item' }" class="conversation-list" aria-label="历史会话" :styles="{ item: { padding: '4px 8px' } }" @active-change="(id) => emit('select', id)" />
    <div v-if="!sessions.length && !loading" class="history-empty">
      <ChatBubbleLeftRightIcon aria-hidden="true" />
      <p>还没有对话</p>
      <span>从一个问题开始</span>
    </div>
    <a class="sidebar-footer" :href="legacyChat" target="_blank" rel="noopener noreferrer" aria-label="打开原版聊天">
      <span>打开旧版工作台</span>
      <ArrowUpRightIcon class="chat-next-icon" aria-hidden="true" />
    </a>
  </div>
</template>

<style scoped>
.chat-next-sidebar { height: 100%; display: flex; flex-direction: column; padding: 16px 12px; background: var(--sidebar); }
.brand { display: flex; align-items: center; justify-content: space-between; height: 36px; padding: 0 4px; margin-bottom: 20px; }
.brand-identity { display: inline-flex; align-items: center; gap: 6px; }
.brand-identity img { flex-shrink: 0; }
.sidebar-collapse { display: grid; place-items: center; width: 30px; height: 32px; padding: 0; color: var(--muted); }
.brand strong { font-size: var(--text-brand); letter-spacing: -0.6px; font-weight: 650; color: var(--primary); }
.new-chat { display: flex; align-items: center; justify-content: center; gap: 7px; height: 42px; padding-inline: 12px; box-shadow: var(--shadow-composer); border-color: var(--border); border-radius: var(--radius-round); background: var(--surface); color: var(--text); font-size: var(--text-navigation); }
.conversation-list { min-height: 0; overflow-y: auto; padding: 0; margin-top: 26px; }
:deep(.session-trigger) { display: block; width: 100%; height: auto; min-height: 34px; padding: 5px 0; overflow: hidden; text-overflow: ellipsis; text-align: start; font-size: var(--text-navigation); font-weight: 400; }
:deep(.session-trigger[aria-current="page"]) { color: var(--primary); font-weight: 500; }
:deep(.conversation-item:has([aria-current="page"])) { background: var(--accent); }
:deep(.session-menu) { display: grid; place-items: center; min-width: 32px; min-height: 32px; padding: 0; }
.history-empty { padding: 28px 4px; text-align: center; color: var(--muted); }
.history-empty svg { width: 27px; height: 27px; margin: 0 auto 14px; color: var(--border-strong); }
.history-empty p { font-size: var(--text-caption); margin: 0 0 6px; }
.history-empty span { font-size: var(--text-micro); }
.sidebar-footer { display: flex; align-items: center; justify-content: space-between; margin-top: auto; padding: 16px 12px 0; border-top: 1px solid var(--border); color: var(--muted); font-size: var(--text-caption); }
.sidebar-footer > svg { margin-left: auto; color: var(--muted); width: 15px; height: 15px; }
</style>
