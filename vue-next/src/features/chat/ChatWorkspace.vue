<script setup>
import { computed, defineAsyncComponent, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Alert, Button, Drawer, Modal, Popover, Skeleton } from 'ant-design-vue'
import { Bars3Icon, ArrowTopRightOnSquareIcon, BuildingOffice2Icon, AdjustmentsHorizontalIcon, ArrowDownIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { legacyLinks } from '../../config/environment.js'
import ChatSidebar from './ChatSidebar.vue'
import ChatWelcome from './ChatWelcome.vue'
import ChatComposer from './ChatComposer.vue'
import ChatSelect from './ChatSelect.vue'
const ChatMessages = defineAsyncComponent(() => import('./ChatMessages.vue'))
import { usePreviewState } from './previewState.js'
import { useLiveState } from './liveState.js'

const props = defineProps({ preview: Boolean })
const showPreview = import.meta.env.DEV
const route = useRoute()
const links = legacyLinks()
const draft = ref('')
const navigationOpen = ref(false)
const sidebarCollapsed = ref(false)
const previewSettingsOpen = ref(false)
const navigationTrigger = ref(null)
const navigationDialog = ref(null)
let focusComposerAfterClose = false
const popupContainer = inject('chat-next-container')
const state = import.meta.env.DEV && props.preview ? usePreviewState(true) : useLiveState()
const { sessions, currentId, status, error, knowledgeBases, documents, scope, messages } = state
const scenario = ref(['history', 'welcome', 'loading', 'error'].includes(route.query.scenario) ? route.query.scenario : 'history')
const messagePanel = ref(null)
const pendingDelete = ref('')
const empty = computed(() => !messages.value.length && status.value !== 'loading')
const streamBusy = computed(() => Boolean(state.busy?.value))
const following = ref(true)
let scrollIntentUntil = 0
function markScrollIntent(event) {
  if (event.type === 'keydown' && !['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '].includes(event.key)) return
  scrollIntentUntil = performance.now() + 1000
  if (event.type === 'wheel' && event.deltaY < 0) following.value = false
}
function observeScroll() {
  const el = messagePanel.value
  // Browser scroll anchoring and async Bubble layout also emit scroll events.
  // Only user input can pause/resume following; content growth cannot.
  if (el && performance.now() <= scrollIntentUntil) following.value = el.scrollHeight - el.scrollTop - el.clientHeight <= 96
}
async function scrollLatest() { scrollIntentUntil = 0; following.value = true; await followLayout() }
async function followLayout() { await nextTick(); if (following.value && messagePanel.value) messagePanel.value.scrollTop = messagePanel.value.scrollHeight }
watch(() => messages.value.map((item) => `${item.id}:${item.content}:${item.statusText || ''}`).join('|'), followLayout, { flush: 'post' })

async function loadScenario(value) { scenario.value = value; draft.value = ''; await state.load(value) }
async function fillPrompt(text) { draft.value = text; await nextTick(); document.querySelector('.chat-next-main textarea')?.focus() }
async function selectSession(id) { if (streamBusy.value) return; following.value = true; state.select(id); draft.value = ''; focusComposerAfterClose = false; navigationOpen.value = false }
async function sendPreview() {
  if (state.send(draft.value)) {
    draft.value = ''
    await scrollLatest()
  }
}
function requestDelete(id) { if (streamBusy.value) return; navigationOpen.value = false; pendingDelete.value = id }
async function deletePreview() { const id = pendingDelete.value; await state.remove(id); pendingDelete.value = '' }
function restoreAfterDelete() { document.querySelector('.desktop-navigation button')?.focus(); if (window.innerWidth < 1024) navigationTrigger.value?.$el?.focus() }
watch(currentId, scrollLatest)
onMounted(() => { if (props.preview) loadScenario(scenario.value); else state.load() })
onBeforeUnmount(state.cancel)

async function newConversation() {
  if (streamBusy.value) return
  state.reset()
  draft.value = ''
  focusComposerAfterClose = navigationOpen.value
  navigationOpen.value = false
  await nextTick()
  if (!focusComposerAfterClose) document.querySelector('.chat-next-main textarea')?.focus()
}
function restoreNavigationFocus(open) {
  if (pendingDelete.value) return
  if (open) navigationDialog.value?.querySelector('button')?.focus()
  else if (focusComposerAfterClose) {
    focusComposerAfterClose = false
    document.querySelector('.chat-next-main textarea')?.focus()
  } else navigationTrigger.value?.$el?.focus()
}
function containNavigationFocus(event) {
  if (event.key !== 'Tab') return
  const controls = [...navigationDialog.value.querySelectorAll('button:not([disabled]), a[href]')]
  const first = controls[0]
  const last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}
function focusMain() {
  document.getElementById('chat-next-main')?.focus()
}
</script>

<template>
  <div class="chat-next-workspace" :class="{ 'is-empty': empty }">
    <a href="#chat-next-main" class="skip-link" :inert="navigationOpen ? '' : undefined" @click="focusMain">跳到主要内容</a>
    <aside v-show="!sidebarCollapsed" id="desktop-sidebar" class="desktop-navigation" aria-label="会话导航" :inert="navigationOpen ? '' : undefined">
      <ChatSidebar collapsible :disabled="streamBusy" :preview="preview" :legacy-chat="links.chat" :sessions="sessions" :current-id="currentId" :loading="status === 'loading'" @collapse="sidebarCollapsed = true" @new="newConversation" @select="selectSession" @delete="requestDelete" />
    </aside>
    <Drawer
      v-model:open="navigationOpen"
      :closable="false"
      placement="left"
      :width="264"
      :get-container="popupContainer"
      :body-style="{ padding: 0 }"
      destroy-on-close
      @after-open-change="restoreNavigationFocus"
    >
      <section v-if="navigationOpen" ref="navigationDialog" class="mobile-dialog" role="dialog" aria-label="会话导航" aria-modal="true" @keydown="containNavigationFocus">
        <Button type="text" class="mobile-close" aria-label="关闭会话导航" @click="navigationOpen = false"><XMarkIcon class="chat-next-icon" aria-hidden="true" /></Button>
        <ChatSidebar :disabled="streamBusy" :preview="preview" :legacy-chat="links.chat" :sessions="sessions" :current-id="currentId" :loading="status === 'loading'" @new="newConversation" @select="selectSession" @delete="requestDelete" />
      </section>
    </Drawer>
    <div class="workspace-body" :inert="navigationOpen ? '' : undefined">
      <header class="workspace-header">
        <div class="header-title">
          <Button ref="navigationTrigger" type="text" class="mobile-navigation" aria-label="打开会话导航" @click="navigationOpen = true">
            <Bars3Icon class="chat-next-icon" aria-hidden="true" />
          </Button>
          <Button v-if="sidebarCollapsed" type="text" class="desktop-toggle" aria-label="展开侧栏" :aria-expanded="false" aria-controls="desktop-sidebar" @click="sidebarCollapsed = false"><Bars3Icon class="chat-next-icon" aria-hidden="true" /></Button>
          <span class="workspace-label">对话</span><span v-if="preview" class="preview-label" title="本地预览，使用隔离演示数据">预览</span>
        </div>
        <nav class="header-actions" aria-label="工作台入口">
          <Popover v-if="showPreview" v-model:open="previewSettingsOpen" trigger="click" placement="bottomRight">
            <template #content><div class="preview-settings" @keydown.esc.stop="previewSettingsOpen = false">
              <template v-if="preview"><label for="preview-scenario">选择演示场景</label><ChatSelect id="preview-scenario" :value="scenario" :options="[{ value: 'history', label: '历史会话' }, { value: 'welcome', label: '欢迎空状态' }, { value: 'loading', label: '加载状态' }, { value: 'error', label: '加载失败' }]" @change="loadScenario" /><small>仅使用本地演示数据</small></template>
              <RouterLink v-else to="/preview/chat">交互演示</RouterLink>
            </div></template>
            <Button type="text" class="header-control" aria-label="预览设置" title="预览设置"><AdjustmentsHorizontalIcon class="chat-next-icon" aria-hidden="true" /></Button>
          </Popover>
          <a :href="links.chat" target="_blank" rel="noopener noreferrer" class="header-control" aria-label="原版对比" title="原版对比"><ArrowTopRightOnSquareIcon class="chat-next-icon" aria-hidden="true" /></a>
          <a :href="links.admin" target="_blank" rel="noopener noreferrer" class="header-control" aria-label="管理后台" title="管理后台"><BuildingOffice2Icon class="chat-next-icon" aria-hidden="true" /></a>
        </nav>
      </header>
      <main id="chat-next-main" class="chat-next-main" tabindex="-1">
        <div class="reading-area"><div ref="messagePanel" class="welcome-scroll" @scroll="observeScroll" @wheel.passive="markScrollIntent" @touchmove.passive="markScrollIntent" @pointerdown="markScrollIntent" @keydown="markScrollIntent">
          <Alert v-if="error" type="error" show-icon class="load-error" :message="preview ? '演示资料加载失败' : '操作未完成'" :description="error"><template #action><Button size="small" @click="preview ? loadScenario('history') : state.retry()">重试加载</Button></template></Alert>
          <div v-if="status === 'loading'" class="loading-content" role="status" aria-label="正在加载对话"><Skeleton active :paragraph="{ rows: 5 }" /><Skeleton active :paragraph="{ rows: 3 }" /></div>
          <ChatMessages v-else-if="messages.length" :messages="messages" :preview="preview" :busy="streamBusy" :conversation-id="currentId" @layout="followLayout" @prompt="fillPrompt" @retry="(id) => preview ? state.retry(id) : state.retryMessage(id)" />
          <ChatWelcome v-else />
        </div>
        <Button v-if="!following && messages.length" class="jump-latest" shape="circle" aria-label="回到最新消息" title="回到最新消息" @click="scrollLatest"><ArrowDownIcon class="chat-next-icon" aria-hidden="true" /></Button></div>
        <ChatComposer v-model:draft="draft" :preview="preview" :enabled="preview || state.scopeReady.value" :streaming="Boolean(state.generating?.value)" :stopping="Boolean(state.stopping?.value)" :stop-request="Boolean(state.stopRequest?.value)" :scope-enabled="preview || state.scopeReady.value" :busy="streamBusy || status === 'loading' || Boolean(state.deleting?.value)" :scope="scope" :knowledge-bases="knowledgeBases" :documents="documents" @scope="state.updateScope" @submit="sendPreview" @stop="state.stop" />
      </main>
    </div>
    <Modal :open="Boolean(pendingDelete)" :title="preview ? '删除演示会话' : '删除会话'" ok-text="删除" cancel-text="取消" :confirm-loading="Boolean(state.deleting?.value)" :cancel-button-props="{ disabled: Boolean(state.deleting?.value) }" :closable="!state.deleting?.value" :mask-closable="!state.deleting?.value" :keyboard="!state.deleting?.value" :ok-button-props="{ danger: true }" :get-container="popupContainer" centered destroy-on-close @ok="deletePreview" @cancel="pendingDelete = ''" @after-close="restoreAfterDelete">
      <p>{{ preview ? '只删除当前浏览器中的演示记录，刷新页面后会恢复。' : '这会重置该会话，清理聊天记录并收口运行中的任务。此操作不可撤销。' }}</p>
    </Modal>
  </div>
</template>

<style scoped>
.chat-next-workspace { display: flex; height: 100dvh; overflow: hidden; background: var(--surface); }
.skip-link { position: fixed; top: 8px; left: 12px; z-index: 2000; padding: 12px 16px; border-radius: var(--radius-small); color: var(--primary-text); background: var(--primary); transform: translateY(-150%); }
.skip-link:focus { transform: translateY(0); }
.desktop-navigation { width: 260px; flex-shrink: 0; border-right: 1px solid var(--border); }
.workspace-body { min-width: 0; min-height: 0; flex: 1; display: flex; flex-direction: column; }
.workspace-header { flex-shrink: 0; height: 56px; padding: 0 24px; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.header-title { display: flex; align-items: center; gap: 12px; min-width: 0; }
.workspace-label { color: var(--muted); font-size: var(--text-body); }
.preview-label { padding: 2px 6px; font-size: var(--text-micro); color: var(--muted); background: var(--sidebar); border-radius: var(--radius-small); }
.header-actions { display: flex; align-items: center; gap: 4px; }
.header-control, .desktop-toggle { display: inline-grid; place-items: center; width: 36px; height: 36px; padding: 0; color: var(--muted); border-radius: var(--radius-small); }
.header-control:hover { background: var(--sidebar); color: var(--text); }
.preview-settings { display: grid; gap: 10px; min-width: 200px; font-size: var(--text-small); }
.preview-settings small { color: var(--muted); }
.mobile-navigation { display: none; }
.mobile-dialog { position: relative; height: 100%; }
.mobile-close { position: absolute; right: 8px; top: 14px; z-index: 1; display: grid; place-items: center; width: 36px; height: 36px; padding: 0; }
.chat-next-main { display: flex; flex: 1; min-height: 0; min-width: 0; flex-direction: column; outline: none; }
.reading-area { position: relative; display: flex; flex: 1; min-height: 0; min-width: 0; }
.welcome-scroll { flex: 1; min-height: 0; min-width: 0; overflow-y: auto; display: flex; flex-direction: column; padding: 0 32px; scrollbar-width: thin; scrollbar-color: var(--border-strong) transparent; }
.loading-content { width: 100%; max-width: 800px; padding: 32px 0; margin: 0 auto; }
.load-error { margin: 16px auto; width: 100%; max-width: 800px; }
.jump-latest { position: absolute; bottom: 16px; left: 50%; transform: translateX(-50%); width: 36px; height: 36px; display: grid; place-items: center; padding: 0; box-shadow: var(--shadow-composer); }
.is-empty .chat-next-main { justify-content: center; padding-bottom: 10vh; }
.is-empty .reading-area { flex: 0 1 auto; }
@media (max-width: 1023px) {
  .desktop-navigation, .desktop-toggle { display: none; }
  .mobile-navigation { display: inline-grid; place-items: center; width: 36px; height: 36px; padding: 0; }
  .workspace-header { padding: 0 16px; }
}
@media (max-width: 600px) {
  .welcome-scroll { padding: 0 16px; }
  .workspace-header { height: 56px; padding: 0 10px; }
  .header-title { gap: 8px; }
  .header-control { width: 32px; height: 36px; }
  .is-empty .chat-next-main { padding-bottom: 4vh; }
}
@media (max-height: 650px) { .is-empty .chat-next-main { padding-bottom: 0; } .is-empty .reading-area { flex: 1; } }
</style>

