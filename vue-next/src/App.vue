<script setup>
import { provide, ref } from 'vue'
import { ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { createTheme } from './theme.js'

const container = ref(null)
const theme = createTheme()
const popupContainer = () => container.value || document.body
provide('chat-next-container', popupContainer)
</script>

<template>
  <ConfigProvider :theme="theme" :locale="zhCN" :get-popup-container="popupContainer" :auto-insert-space-in-button="false">
    <div ref="container" class="chat-next-root">
      <RouterView />
    </div>
  </ConfigProvider>
</template>
