<script setup>
import { computed, inject, nextTick, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Breadcrumb, BreadcrumbItem, Button, Drawer, Modal } from 'ant-design-vue'
import { ArrowLeftOnRectangleIcon, Bars3Icon, ChevronLeftIcon, ChevronRightIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { authSession } from '../../../shared/auth/session.js'
import { adminAuth } from '../../../shared/auth/adminAuth.js'
import AdminNav from './AdminNav.vue'

const route = useRoute(), popupContainer = inject('admin-next-container')
const collapsed = ref(false), navigationOpen = ref(false), logoutOpen = ref(false)
const navigationTrigger = ref(null), navigationDialog = ref(null), logoutTrigger = ref(null)
const username = computed(() => authSession.state.profile?.username || '')
async function focusMain() { await nextTick(); document.getElementById('admin-next-main')?.focus() }
async function drawerFocus(open) {
  await nextTick()
  if (open) navigationDialog.value?.querySelector('button')?.focus()
  else navigationTrigger.value?.$el?.focus()
}
function trapNavigation(event) {
  if (event.key !== 'Tab') return
  const controls = [...navigationDialog.value.querySelectorAll('button:not([disabled]), a[href]')]
  const first = controls[0], last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
function logout() { logoutOpen.value = false; adminAuth.logout() }
function resize() { if (window.innerWidth >= 1024) navigationOpen.value = false }
window.addEventListener('resize', resize)
onBeforeUnmount(() => window.removeEventListener('resize', resize))
</script>

<template>
  <div class="admin-next-shell" :class="{ 'is-collapsed': collapsed }">
    <a href="#admin-next-main" class="admin-next-skip" :inert="navigationOpen ? '' : undefined" @click="focusMain">跳到主要内容</a>
    <aside class="admin-next-sidebar" :inert="navigationOpen ? '' : undefined"><AdminNav :collapsed="collapsed" /></aside>
    <div class="admin-next-content" :inert="navigationOpen ? '' : undefined">
      <header class="admin-next-header">
        <div class="admin-next-header-start"><Button class="admin-next-desktop-toggle" type="text" :aria-label="collapsed ? '展开后台导航' : '收起后台导航'" @click="collapsed = !collapsed"><ChevronRightIcon v-if="collapsed" class="admin-next-icon" aria-hidden="true" /><ChevronLeftIcon v-else class="admin-next-icon" aria-hidden="true" /></Button><Button ref="navigationTrigger" class="admin-next-mobile-toggle" type="text" aria-label="打开后台导航" @click="navigationOpen = true"><Bars3Icon class="admin-next-icon" aria-hidden="true" /></Button>
          <Breadcrumb><BreadcrumbItem><RouterLink to="/admin">管理后台</RouterLink></BreadcrumbItem><BreadcrumbItem>{{ route.meta.title }}</BreadcrumbItem></Breadcrumb>
        </div>
        <div class="admin-next-header-end"><span class="admin-next-avatar" aria-hidden="true">{{ username.slice(0, 1).toUpperCase() }}</span><span class="admin-next-username">{{ username }}</span><Button ref="logoutTrigger" type="text" aria-label="退出登录" @click="logoutOpen = true"><ArrowLeftOnRectangleIcon class="admin-next-icon" aria-hidden="true" /></Button></div>
      </header>
      <main id="admin-next-main" class="admin-next-main" tabindex="-1"><RouterView v-if="authSession.state.status === 'authenticated'" /><p v-else role="status">正在确认登录状态</p></main>
    </div>
    <Drawer v-model:open="navigationOpen" placement="left" :closable="false" :width="264" :get-container="popupContainer" :body-style="{ padding: 0 }" destroy-on-close @after-open-change="drawerFocus">
      <section v-if="navigationOpen" ref="navigationDialog" class="admin-next-mobile-dialog" role="dialog" aria-label="后台导航" aria-modal="true" @keydown="trapNavigation"><Button type="text" class="admin-next-mobile-close" aria-label="关闭后台导航" @click="navigationOpen = false"><XMarkIcon class="admin-next-icon" aria-hidden="true" /></Button><AdminNav @navigate="navigationOpen = false" /></section>
    </Drawer>
    <Modal v-model:open="logoutOpen" title="退出登录" ok-text="退出" cancel-text="取消" :get-container="popupContainer" centered destroy-on-close @ok="logout" @after-close="logoutTrigger?.$el?.focus()"><p>退出账户 {{ username }}？</p></Modal>
  </div>
</template>
