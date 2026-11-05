<script setup>
import { provide, ref } from 'vue'
import { ConfigProvider } from 'ant-design-vue'
import './admin.css'

const container = ref(null)
const popupHost = ref(null)
const popupContainer = () => popupHost.value || container.value || document.body
provide('admin-next-container', popupContainer)
const css = getComputedStyle(document.documentElement)
const value = (key) => css.getPropertyValue(`--${key}`).trim()
const theme = { token: {
  fontFamily: value('admin-font'), fontSize: parseFloat(value('admin-text-body')),
  fontSizeSM: parseFloat(value('admin-text-small')), borderRadius: parseFloat(value('admin-radius')),
  colorText: value('admin-ink'), colorTextSecondary: value('admin-muted'), colorTextDescription: value('admin-muted'), colorBorder: value('admin-border'), colorBgLayout: value('admin-canvas'), colorError: value('admin-danger')
} }
</script>

<template>
  <ConfigProvider :theme="theme" :get-popup-container="popupContainer">
    <div ref="container" class="admin-next-root"><RouterView /><div ref="popupHost" class="admin-next-overlay-host"></div></div>
  </ConfigProvider>
</template>
