<script setup>
import { provide, ref } from 'vue'
import { ConfigProvider, Spin } from 'ant-design-vue'
import { useRoute } from 'vue-router'
import { authSession } from './shared/auth/session.js'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { createTheme } from './theme.js'

const container = ref(null)
const route = useRoute()
const theme = createTheme()
const popupContainer = () => container.value || document.body
provide('chat-next-container', popupContainer)
</script>

<template>
  <ConfigProvider :theme="theme" :locale="zhCN" :get-popup-container="popupContainer" :auto-insert-space-in-button="false">
    <div ref="container" class="chat-next-root">
      <main v-if="!route.matched.length && authSession.state.status === 'checking'" class="admin-next-bootstrap" role="status"><Spin /><p>正在确认登录状态</p></main>
      <RouterView v-else />
    </div>
  </ConfigProvider>
</template>

<style scoped>
.admin-next-bootstrap { min-height: 100dvh; display: grid; place-content: center; justify-items: center; font-family: var(--admin-font); font-size: var(--admin-text-body); color: var(--admin-muted); background: var(--admin-canvas); }
</style>
